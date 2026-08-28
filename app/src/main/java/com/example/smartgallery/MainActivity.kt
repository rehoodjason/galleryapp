package com.example.smartgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.smartgallery.data.PhotoEntity
import com.example.smartgallery.ui.components.SlideshowPlayer
import com.example.smartgallery.ui.theme.SmartGalleryTheme
import com.example.smartgallery.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartGalleryTheme {
                MainGalleryApp()
            }
        }
    }
}

@Composable
fun MainGalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val photos by viewModel.photos.collectAsState(initial = emptyList())
    val persons by viewModel.persons.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var isSlideshowActive by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070913))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Smart Gallery AI",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        "On-Device Neural Engine",
                        color = Color(0xFF22D3EE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { isSlideshowActive = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3322D3EE)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6622D3EE))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Слайд-шоу", color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Body
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> PhotosGrid(photos)
                    1 -> PersonsGrid(persons)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Раздел в разработке", color = Color.Gray)
                    }
                }
            }

            // Fixed Bottom Nav Bar
            Surface(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                color = Color(0xF2090D1C),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(0, "Фото", Icons.Default.Image),
                        Triple(1, "Лица", Icons.Default.People),
                        Triple(2, "Поиск", Icons.Default.Search),
                        Triple(3, "Сейф", Icons.Default.Shield)
                    )
                    tabs.forEach { (index, title, icon) ->
                        val isSelected = selectedTab == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedTab = index },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = title,
                                tint = if (isSelected) Color(0xFF22D3EE) else Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                title,
                                color = if (isSelected) Color(0xFF22D3EE) else Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Slideshow Overlay
        if (isSlideshowActive && photos.isNotEmpty()) {
            SlideshowPlayer(
                photos = photos,
                onClose = { isSlideshowActive = false }
            )
        }
    }
}

@Composable
fun PhotosGrid(photos: List<PhotoEntity>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(photos) { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
fun PersonsGrid(persons: List<com.example.smartgallery.data.PersonEntity>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(persons) { person ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x26FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = person.coverFaceUri,
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF22D3EE), CircleShape)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(person.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}