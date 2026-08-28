package com.example.smartgallery.viewmodel

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartgallery.data.FaceEntity
import com.example.smartgallery.data.GalleryDatabase
import com.example.smartgallery.data.PersonEntity
import com.example.smartgallery.data.PhotoEntity
import com.example.smartgallery.ml.FaceClusteringService
import com.example.smartgallery.ml.FaceRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GalleryDatabase.getInstance(application)
    private val dao = db.galleryDao()

    private val recognitionEngine = FaceRecognitionEngine(application)
    private val clusteringService = FaceClusteringService(recognitionEngine)

    val photos = dao.getAllPhotos()
    val persons = dao.getAllPersons()

    private val _isPermissionGranted = MutableStateFlow(checkMediaPermission(application))
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    // Поиск
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedPersonId = MutableStateFlow<String?>(null)
    val selectedPersonId: StateFlow<String?> = _selectedPersonId.asStateFlow()

    // Сейф
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _vaultPhotos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val vaultPhotos: StateFlow<List<PhotoEntity>> = _vaultPhotos.asStateFlow()

    init {
        if (_isPermissionGranted.value) {
            syncDevicePhotos()
        }
    }

    private fun checkMediaPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(granted: Boolean) {
        _isPermissionGranted.value = granted
        if (granted) {
            syncDevicePhotos()
        }
    }

    fun syncDevicePhotos() {
        viewModelScope.launch {
            _isScanning.value = true
            val context = getApplication<Application>()
            val localPhotos = withContext(Dispatchers.IO) {
                queryMediaStoreImages(context)
            }

            withContext(Dispatchers.IO) {
                localPhotos.forEach { photo ->
                    dao.insertPhoto(photo)
                }
                refreshVaultPhotos()
            }
            _isScanning.value = false
            
            // Запуск фонового обнаружения лиц на реальных фото
            scanFacesOnPhotos()
        }
    }

    fun scanFacesOnPhotos() {
        viewModelScope.launch {
            _isScanning.value = true
            val context = getApplication<Application>()

            withContext(Dispatchers.IO) {
                val allPhotos = dao.getAllPhotos().firstOrNull() ?: emptyList()
                val existingPersons = (dao.getAllPersons().firstOrNull() ?: emptyList()).toMutableList()
                val allFaces = dao.getAllFaces().toMutableList()

                val personFacesMap = existingPersons.map { person ->
                    person to allFaces.filter { it.personId == person.id }
                }.toMutableList()

                val total = allPhotos.size
                for ((idx, photo) in allPhotos.withIndex()) {
                    _scanProgress.value = if (total > 0) (idx + 1).toFloat() / total else 1f

                    // Пропускаем фото, если оно уже проиндексировано
                    val isAlreadyScanned = allFaces.any { it.photoId == photo.id }
                    if (isAlreadyScanned) continue

                    try {
                        val bitmap = decodeSampledBitmapFromUri(context, Uri.parse(photo.uri), 640, 640) ?: continue
                        val detectedFaces = recognitionEngine.detectFaces(bitmap)

                        for (face in detectedFaces) {
                            val boundingBox = face.boundingBox
                            val embedding = recognitionEngine.extractFaceEmbedding(bitmap, boundingBox)
                            if (embedding.all { it == 0f }) continue

                            val matchedPersonId = clusteringService.assignFaceToPerson(embedding, personFacesMap)
                            val isNewPerson = existingPersons.none { it.id == matchedPersonId } &&
                                    personFacesMap.none { it.first.id == matchedPersonId }

                            val finalPersonId = if (isNewPerson) {
                                val newPersonId = UUID.randomUUID().toString()
                                val newPerson = PersonEntity(
                                    id = newPersonId,
                                    name = "Человек ${personFacesMap.size + 1}",
                                    coverFaceUri = photo.uri,
                                    isConfirmed = false
                                )
                                dao.insertPerson(newPerson)
                                existingPersons.add(newPerson)
                                personFacesMap.add(newPerson to emptyList())
                                newPersonId
                            } else {
                                matchedPersonId
                            }

                            val faceEntity = FaceEntity(
                                id = UUID.randomUUID().toString(),
                                photoId = photo.id,
                                personId = finalPersonId,
                                boundingBoxJson = "{\"x\":${boundingBox.left},\"y\":${boundingBox.top},\"w\":${boundingBox.width()},\"h\":${boundingBox.height()}}",
                                embedding = embedding
                            )
                            dao.insertFace(faceEntity)
                            allFaces.add(faceEntity)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _isScanning.value = false
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)

                var inSampleSize = 1
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    BitmapFactory.decodeStream(stream2, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryMediaStoreImages(context: Context): List<PhotoEntity> {
        val photoList = mutableListOf<PhotoEntity>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                var count = 0
                while (cursor.moveToNext() && count < 300) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L
                    val name = cursor.getString(nameColumn) ?: "Photo"
                    val contentUri = ContentUris.withAppendedId(queryUri, id).toString()

                    val category = when {
                        name.contains("IMG", true) -> "Портрет"
                        name.contains("PANO", true) -> "Природа"
                        name.contains("DOC", true) -> "Документы"
                        else -> "Галерея"
                    }

                    photoList.add(
                        PhotoEntity(
                            id = id.toString(),
                            uri = contentUri,
                            dateAdded = dateAdded,
                            locationName = "Медиатека устройства",
                            category = category,
                            isFavorite = false,
                            isVault = false
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return photoList
    }

    fun renamePerson(personId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.renamePerson(personId, newName)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun selectPerson(personId: String?) {
        _selectedPersonId.value = if (_selectedPersonId.value == personId) null else personId
    }

    fun unlockVault(pin: String): Boolean {
        return if (pin == "1234") {
            _isVaultUnlocked.value = true
            refreshVaultPhotos()
            true
        } else {
            false
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun moveToVault(photo: PhotoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPhoto(photo.copy(isVault = true))
            refreshVaultPhotos()
        }
    }

    fun restoreFromVault(photo: PhotoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPhoto(photo.copy(isVault = false))
            refreshVaultPhotos()
        }
    }

    private fun refreshVaultPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            _vaultPhotos.value = emptyList()
        }
    }

    fun toggleFavorite(photo: PhotoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPhoto(photo.copy(isFavorite = !photo.isFavorite))
        }
    }
}
