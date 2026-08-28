package com.example.smartgallery.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

/**
 * Модуль распознавания лиц: Google ML Kit + TensorFlow Lite MobileFaceNet (512D)
 */
class FaceRecognitionEngine(private val context: Context) {

    private val tfliteInterpreter: Interpreter? by lazy {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "mobile_facenet.tflite")
            val options = Interpreter.Options().apply { setNumThreads(4) }
            Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(image)
            .addOnSuccessListener { faces -> continuation.resume(faces) }
            .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    }

    suspend fun extractFaceEmbedding(originalBitmap: Bitmap, boundingBox: Rect): FloatArray = withContext(Dispatchers.Default) {
        val left = boundingBox.left.coerceAtLeast(0)
        val top = boundingBox.top.coerceAtLeast(0)
        val width = boundingBox.width().coerceAtMost(originalBitmap.width - left)
        val height = boundingBox.height().coerceAtMost(originalBitmap.height - top)

        if (width <= 0 || height <= 0 || tfliteInterpreter == null) return@withContext FloatArray(512)

        val croppedFace = Bitmap.createBitmap(originalBitmap, left, top, width, height)
        val scaledFace = Bitmap.createScaledBitmap(croppedFace, 112, 112, true)

        val inputBuffer = convertBitmapToByteBuffer(scaledFace)
        val outputEmbeddings = Array(1) { FloatArray(512) }

        tfliteInterpreter?.run(inputBuffer, outputEmbeddings)
        normalizeL2(outputEmbeddings[0])
    }

    fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            normA += embedding1[i] * embedding1[i]
            normB += embedding2[i] * embedding2[i]
        }

        if (normA == 0f || normB == 0f) return 0f
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(112 * 112)
        bitmap.getPixels(intValues, 0, 112, 0, 0, 112, 112)

        var pixel = 0
        for (i in 0 until 112) {
            for (j in 0 until 112) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 128.0f)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 128.0f)
                byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 128.0f)
            }
        }
        return byteBuffer
    }

    private fun normalizeL2(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares).coerceAtLeast(1e-10f)
        for (i in vector.indices) vector[i] /= norm
        return vector
    }
}