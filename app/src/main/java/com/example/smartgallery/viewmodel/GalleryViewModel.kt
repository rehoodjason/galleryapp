package com.example.smartgallery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartgallery.data.GalleryDatabase
import com.example.smartgallery.data.PersonEntity
import com.example.smartgallery.data.PhotoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GalleryDatabase.getInstance(application)
    private val dao = db.galleryDao()

    val photos = dao.getAllPhotos()
    val persons = dao.getAllPersons()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        seedInitialDemoDataIfEmpty()
    }

    private fun seedInitialDemoDataIfEmpty() {
        viewModelScope.launch {
            dao.insertPerson(
                PersonEntity(
                    id = "p1",
                    name = "Алексей (Вы)",
                    coverFaceUri = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
                    isConfirmed = true
                )
            )
            dao.insertPerson(
                PersonEntity(
                    id = "p2",
                    name = "Мария",
                    coverFaceUri = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400",
                    isConfirmed = true
                )
            )
            dao.insertPhoto(
                PhotoEntity(
                    id = "1",
                    uri = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=900",
                    dateAdded = System.currentTimeMillis(),
                    locationName = "Казань, Кремль",
                    category = "Портрет",
                    isFavorite = true
                )
            )
            dao.insertPhoto(
                PhotoEntity(
                    id = "2",
                    uri = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=900",
                    dateAdded = System.currentTimeMillis() - 86400000,
                    locationName = "Сочи, Красная Поляна",
                    category = "Путешествия",
                    isFavorite = true
                )
            )
        }
    }

    fun renamePerson(personId: String, newName: String) {
        viewModelScope.launch {
            dao.renamePerson(personId, newName)
        }
    }
}