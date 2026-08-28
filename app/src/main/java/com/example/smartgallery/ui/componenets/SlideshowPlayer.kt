package com.example.smartgallery.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smartgallery.data.PhotoEntity
import kotlinx.coroutines.delay

@Composable
fun SlideshowPlayer(
    photos: List<PhotoEntity>,
    onClose: () -> Unit,
    initialShuffle: Boolean = false,
    initialIntervalSec: Float = 3.5f
) {
    var isShuffle by remember { mutableStateOf(initialShuffle) }
    var intervalSec by remember { mutableFloatStateOf(initialIntervalSec) }
    var isPlaying by remember { mutableStateOf(true) }
    var isLoop by remember { mutableStateOf(true) }

    val playList = remember(photos, isShuffle) {
        if (isShuffle) photos.shuffled() else photos
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, currentIndex, intervalSec, playList.size) {
        if (isPlaying && playList.isNotEmpty()) {
            val stepMs = 50L
            val totalSteps = (intervalSec * 1000 / stepMs).toInt()
            for (i in 0..totalSteps) {
                progress = i.toFloat() / totalSteps
                delay(stepMs)
            }
            progress = 0f
            if (currentIndex < playList.size - 1) {
                currentIndex++
            } else if (isLoop) {
                currentIndex = 0
            } else {
                isPlaying = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val currentPhoto = playList.getOrNull(currentIndex)

        Crossfade(
            targetState = currentPhoto,
            animationSpec = tween(700),
            label = "slideshow_crossfade"
        ) { photo ->
            photo?.let {
                AsyncImage(
                    model = it.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Верхний прогресс-бар
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
            color = Color(0xFF22D3EE),
            trackColor = Color(0x33FFFFFF),
        )

        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.clip(CircleShape).background(Color(0x66000000))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Text(
                text = "${currentIndex + 1} / ${playList.size}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            IconButton(
                onClick = { isShuffle = !isShuffle; currentIndex = 0 },
                modifier = Modifier.clip(CircleShape).background(if (isShuffle) Color(0x9922D3EE) else Color(0x66000000))
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White)
            }
        }

        // Нижняя панель управления
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xCC090D1C))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = { currentIndex = (currentIndex - 1 + playList.size) % playList.size; progress = 0f }
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
            }

            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF22D3EE))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black
                )
            }

            IconButton(
                onClick = { currentIndex = (currentIndex + 1) % playList.size; progress = 0f }
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
            }
        }
    }
}