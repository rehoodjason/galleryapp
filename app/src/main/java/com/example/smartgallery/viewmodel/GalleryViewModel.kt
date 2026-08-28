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
import com.example.smartgallery.data.*
import com.example.smartgallery.ml.FaceClusteringService
import com.example.smartgallery.ml.FaceRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SortField { DATE, NAME, SIZE }
enum class SortDirection { ASC, DESC }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GalleryDatabase.getInstance(application)
    private val dao = db.galleryDao()

    private val recognitionEngine = FaceRecognitionEngine(application)
    private val clusteringService = FaceClusteringService(recognitionEngine)

    val rawPhotos = dao.getAllPhotos()
    val persons = dao.getAllPersons()
    val vaultPhotos = dao.getVaultPhotos()

    private val _sortField = MutableStateFlow(SortField.DATE)
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.DESC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _gridColumns = MutableStateFlow(3)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedPersonId = MutableStateFlow<String?>(null)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    val photos: StateFlow<List<PhotoEntity>> = combine(
        rawPhotos, _sortField, _sortDirection, _searchQuery, _selectedCategory, _selectedPersonId
    ) { photoList, field, direction, query, category, personId ->
        var list = photoList

        if (category != null) {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        if (personId != null) {
            val personPhotos = dao.getPhotosForPerson(personId).firstOrNull() ?: emptyList()
            val personPhotoIds = personPhotos.map { it.id }.toSet()
            list = list.filter { it.id in personPhotoIds }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                (it.locationName?.lowercase()?.contains(q) == true) ||
                        it.category.lowercase().contains(q) ||
                        it.filePath.lowercase().contains(q)
            }
        }

        when (field) {
            SortField.DATE -> {
                if (direction == SortDirection.DESC) list.sortedByDescending { it.dateAdded }
                else list.sortedBy { it.dateAdded }
            }
            SortField.NAME -> {
                if (direction == SortDirection.DESC) list.sortedByDescending { it.locationName ?: it.id }
                else list.sortedBy { it.locationName ?: it.id }
            }
            SortField.SIZE -> {
                if (direction == SortDirection.DESC) list.sortedByDescending { it.sizeBytes }
                else list.sortedBy { it.sizeBytes }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        syncDevicePhotos()
    }

    fun syncDevicePhotos() {
        viewModelScope.launch {
            _isScanning.value = true
            val context = getApplication<Application>()
            val localPhotos = withContext(Dispatchers.IO) {
                queryMediaStoreImages(context)
            }

            withContext(Dispatchers.IO) {
                localPhotos.forEach { dao.insertPhoto(it) }
            }
            _isScanning.value = false
            scanFacesOnPhotos()
        }
    }

    private fun queryMediaStoreImages(context: Context): List<PhotoEntity> {
        val photoList = mutableListOf<PhotoEntity>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext() && photoList.size < 800) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L
                    val name = cursor.getString(nameColumn) ?: "Photo"
                    val sizeBytes = cursor.getLong(sizeColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val contentUri = ContentUris.withAppendedId(queryUri, id).toString()

                    val category = when {
                        name.contains("PORTRAIT", true) || name.contains("IMG", true) -> "Портрет"
                        name.contains("PANO", true) || name.contains("LANDSCAPE", true) -> "Природа"
                        name.contains("DOC", true) || name.contains("SCAN", true) -> "Документы"
                        else -> "Галерея"
                    }

                    photoList.add(
                        PhotoEntity(
                            id = id.toString(), uri = contentUri, dateAdded = dateAdded,
                            locationName = name, category = category, sizeBytes = sizeBytes,
                            width = if (width > 0) width else 1920, height = if (height > 0) height else 1080,
                            filePath = filePath
                        )
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return photoList
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

                for (photo in allPhotos) {
                    if (allFaces.any { it.photoId == photo.id }) continue

                    try {
                        val bitmap = decodeSampledBitmap(context, Uri.parse(photo.uri), 720, 720) ?: continue
                        val detectedFaces = recognitionEngine.detectFaces(bitmap)

                        for (face in detectedFaces) {
                            val box = face.boundingBox
                            val embedding = recognitionEngine.extractFaceEmbedding(bitmap, box)
                            val matchedPersonId = clusteringService.assignFaceToPerson(embedding, personFacesMap)
                            
                            val isNewPerson = existingPersons.none { it.id == matchedPersonId }
                            val finalPersonId = if (isNewPerson) {
                                val newId = UUID.randomUUID().toString()
                                val newPerson = PersonEntity(newId, "Человек ${personFacesMap.size + 1}", photo.uri, false)
                                dao.insertPerson(newPerson)
                                existingPersons.add(newPerson)
                                personFacesMap.add(newPerson to emptyList())
                                newId
                            } else matchedPersonId

                            val faceEntity = FaceEntity(
                                id = UUID.randomUUID().toString(), photoId = photo.id, personId = finalPersonId,
                                boundingBoxJson = "{\"x\":${box.left},\"y\":${box.top},\"w\":${box.width()},\"h\":${box.height()}}",
                                embedding = embedding
                            )
                            dao.insertFace(faceEntity)
                            allFaces.add(faceEntity)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            _isScanning.value = false
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                var inSampleSize = 1
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2; val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2
                    }
                }
                val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    BitmapFactory.decodeStream(stream2, null, decodeOptions)
                }
            }
        } catch (e: Exception) { null }
    }

    fun setSort(field: SortField, direction: SortDirection) {
        _sortField.value = field
        _sortDirection.value = direction
    }

    fun setGridColumns(cols: Int) { _gridColumns.value = cols }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    
    fun unlockVault(pin: String): Boolean {
        return if (pin == "1234") { _isVaultUnlocked.value = true; true } else false
    }
    fun lockVault() { _isVaultUnlocked.value = false }
    
    fun moveToVault(photo: PhotoEntity) { viewModelScope.launch(Dispatchers.IO) { dao.insertPhoto(photo.copy(isVault = true)) } }
    fun toggleFavorite(photo: PhotoEntity) { viewModelScope.launch(Dispatchers.IO) { dao.insertPhoto(photo.copy(isFavorite = !photo.isFavorite)) } }
    fun deletePhoto(photo: PhotoEntity) { viewModelScope.launch(Dispatchers.IO) { dao.deletePhoto(photo.id) } }
}
