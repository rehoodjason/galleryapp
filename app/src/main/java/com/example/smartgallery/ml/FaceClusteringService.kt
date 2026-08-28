package com.example.smartgallery.ml

import com.example.smartgallery.data.FaceEntity
import com.example.smartgallery.data.PersonEntity
import java.util.UUID

class FaceClusteringService(private val recognitionEngine: FaceRecognitionEngine) {

    private val similarityThreshold = 0.82f

    fun assignFaceToPerson(
        newFaceEmbedding: FloatArray,
        existingPersons: List<Pair<PersonEntity, List<FaceEntity>>>
    ): String {
        var bestMatchPersonId: String? = null
        var maxSimilarity = 0.0f

        for ((person, faces) in existingPersons) {
            for (face in faces) {
                val similarity = recognitionEngine.calculateCosineSimilarity(newFaceEmbedding, face.embedding)
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity
                    if (similarity >= similarityThreshold) {
                        bestMatchPersonId = person.id
                    }
                }
            }
        }
        return bestMatchPersonId ?: UUID.randomUUID().toString()
    }
}