package com.example.smartgallery

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.smartgallery.data.PersonEntity
import com.example.smartgallery.data.PhotoEntity
import com.example.smartgallery.ui.components.SlideshowPlayer
import com.example.smartgallery.ui.theme.SmartGalleryTheme
import com.example.smartgallery.viewmodel.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
WindowCompat.setDecorFitsSystemWindows(window, false)

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
val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
val isScanning by viewModel.isScanning.collectAsState()
val scanProgress by viewModel.scanProgress.collectAsState()

var selectedTab by remember { mutableIntStateOf(0) }
var selectedPhotoForDetail by remember { mutableStateOf<PhotoEntity?>(null) }
var isSlideshowActive by remember { mutableStateOf(false) }

val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted ->
    viewModel.onPermissionResult(isGranted)
}

LaunchedEffect(Unit) {
    if (!isPermissionGranted) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perm)
    }
}

Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF070913))
        .statusBarsPadding()
) {
    Column(modifier = Modifier.fillMaxSize()) {
        
        AppHeaderBar(
            isScanning = isScanning,
            onStartSlideshow = { isSlideshowActive = true },
            onRefresh = { viewModel.syncDevicePhotos() }
        )

        if (isScanning && scanProgress > 0f) {
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFF22D3EE),
                trackColor = Color(0x33FFFFFF)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!isPermissionGranted && photos.isEmpty()) {
                PermissionRequestView(onRequest = {
                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    permissionLauncher.launch(perm)
                })
            } else {
                when (selectedTab) {
                    0 -> PhotosTimelineScreen(
                        photos = photos,
                        onPhotoClick = { selectedPhotoForDetail = it }
                    )
                    1 -> PersonsScreen(
                        persons = persons,
                        isScanning = isScanning,
                        onScanFaces = { viewModel.scanFacesOnPhotos() },
                        onRenamePerson = { id, name -> viewModel.renamePerson(id, name) }
                    )
                    2 -> SearchScreen(
                        photos = photos,
                        viewModel = viewModel,
                        onPhotoClick = { selectedPhotoForDetail = it }
                    )
                    3 -> VaultScreen(
                        viewModel = viewModel,
                        onPhotoClick = { selectedPhotoForDetail = it }
                    )
                }
            }
        }

        FixedBottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }

    selectedPhotoForDetail?.let { photo ->
        PhotoDetailViewer(
            photo = photo,
            onClose = { selectedPhotoForDetail = null },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onMoveToVault = { 
                viewModel.moveToVault(it)
                selectedPhotoForDetail = null
            },
            onStartSlideshow = { isSlideshowActive = true }
        )
    }

    if (isSlideshowActive && photos.isNotEmpty()) {
        SlideshowPlayer(
            photos = photos,
            onClose = { isSlideshowActive = false }
        )
    }
}


}

@Composable
fun AppHeaderBar(
isScanning: Boolean,
onStartSlideshow: () -> Unit,
onRefresh: () -> Unit
) {
Row(
modifier = Modifier
.fillMaxWidth()
.padding(horizontal = 16.dp, vertical = 10.dp),
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
if (isScanning) "⚡ Сканирование медиатеки..." else "On-Device Face Neural Engine",
color = Color(0xFF22D3EE),
fontSize = 11.sp,
fontWeight = FontWeight.SemiBold
)
}

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
        }

        Button(
            onClick = onStartSlideshow,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3322D3EE)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0x6622D3EE)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Слайд-шоу", color = Color(0xFF22D3EE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}


}

@Composable
fun PhotosTimelineScreen(
photos: List,
onPhotoClick: (PhotoEntity) -> Unit
) {
if (photos.isEmpty()) {
Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
Column(horizontalAlignment = Alignment.CenterHorizontally) {
Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
Spacer(Modifier.height(8.dp))
Text("Фотографий не найдено", color = Color.White, fontWeight = FontWeight.Bold)
Text("Нажмите кнопку обновить вверху", color = Color.Gray, fontSize = 12.sp)
}
}
} else {
LazyVerticalGrid(
columns = GridCells.Fixed(3),
contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
horizontalArrangement = Arrangement.spacedBy(6.dp),
verticalArrangement = Arrangement.spacedBy(6.dp)
) {
items(photos) { photo ->
Box(
modifier = Modifier
.aspectRatio(1f)
.clip(RoundedCornerShape(16.dp))
.border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
.clickable { onPhotoClick(photo) }
) {
AsyncImage(
model = photo.uri,
contentDescription = null,
contentScale = ContentScale.Crop,
modifier = Modifier.fillMaxSize()
)
if (photo.isFavorite) {
Icon(
Icons.Default.Favorite,
contentDescription = null,
tint = Color(0xFFF43F5E),
modifier = Modifier
.align(Alignment.TopEnd)
.padding(6.dp)
.size(16.dp)
)
}
}
}
}
}
}

@Composable
fun PersonsScreen(
persons: List,
isScanning: Boolean,
onScanFaces: () -> Unit,
onRenamePerson: (String, String) -> Unit
) {
var editingPersonId by remember { mutableStateOf<String?>(null) }
var editingNameText by remember { mutableStateOf("") }

Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("AI-Кластеризация лиц", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if (persons.isEmpty()) "Лица ещё не обнаружены" else "Найдено персон: ${persons.size}",
                color = Color(0xFF22D3EE),
                fontSize = 12.sp
            )
        }

        Button(
            onClick = onScanFaces,
            enabled = !isScanning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3322D3EE)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0x6622D3EE)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isScanning) "Анализ..." else "Скан лиц", color = Color(0xFF22D3EE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(Modifier.height(14.dp))

    if (persons.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x0AFFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Группы лиц пусты", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Нажмите кнопку «Скан лиц», чтобы локальная нейросеть MobileFaceNet проанализировала медиатеку и автоматически сгруппировала людей.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onScanFaces,
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isScanning) "Идет сканирование..." else "Запустить поиск лиц", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(persons) { person ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
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
                        
                        if (editingPersonId == person.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = editingNameText,
                                    onValueChange = { editingNameText = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                IconButton(onClick = {
                                    if (editingNameText.isNotBlank()) {
                                        onRenamePerson(person.id, editingNameText.trim())
                                    }
                                    editingPersonId = null
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF10B981))
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    person.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                IconButton(
                                    onClick = {
                                        editingPersonId = person.id
                                        editingNameText = person.name
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Text("Кластер активен", color = Color(0xFF22D3EE), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}


}

@Composable
fun PhotoDetailViewer(
photo: PhotoEntity,
onClose: () -> Unit,
onToggleFavorite: (PhotoEntity) -> Unit,
onMoveToVault: (PhotoEntity) -> Unit,
onStartSlideshow: () -> Unit
) {
val dateString = remember(photo.dateAdded) {
SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(photo.dateAdded))
}

Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color(0xF5050711))
        .clickable(onClick = {})
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(photo.category, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(dateString, color = Color(0xFF22D3EE), fontSize = 11.sp)
            }

            IconButton(
                onClick = { onToggleFavorite(photo) },
                modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF))
            ) {
                Icon(
                    if (photo.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (photo.isFavorite) Color(0xFFF43F5E) else Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x1FFFFFFF))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(24.dp))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onStartSlideshow() }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF22D3EE))
                Text("Слайд-шоу", color = Color.White, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onMoveToVault(photo) }) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981))
                Text("В Сейф", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}


}

@Composable
fun SearchScreen(
photos: List,
viewModel: GalleryViewModel,
onPhotoClick: (PhotoEntity) -> Unit
) {
val query by viewModel.searchQuery.collectAsState()
val selectedCategory by viewModel.selectedCategory.collectAsState()

val filteredPhotos = remember(photos, query, selectedCategory) {
    photos.filter { photo ->
        val matchQuery = query.isEmpty() ||
                photo.locationName?.contains(query, ignoreCase = true) == true ||
                photo.category.contains(query, ignoreCase = true)
        val matchCat = selectedCategory == null || photo.category.equals(selectedCategory, ignoreCase = true)
        matchQuery && matchCat
    }
}

Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    OutlinedTextField(
        value = query,
        onValueChange = { viewModel.setSearchQuery(it) },
        placeholder = { Text("Поиск лиц, мест, категорий...", color = Color.Gray, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF22D3EE)) },
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF22D3EE),
            unfocusedBorderColor = Color(0x33FFFFFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )

    Text("Категории", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    val categories = listOf("Портрет", "Природа", "Путешествия", "Документы", "Галерея")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(categories) { cat ->
            val isSelected = selectedCategory == cat
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Color(0x4D22D3EE) else Color(0x14FFFFFF),
                border = BorderStroke(1.dp, if (isSelected) Color(0xFF22D3EE) else Color(0x26FFFFFF)),
                modifier = Modifier.clickable { viewModel.selectCategory(cat) }
            ) {
                Text(
                    cat,
                    color = if (isSelected) Color(0xFF22D3EE) else Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }

    Text("Найдено: ${filteredPhotos.size}", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(filteredPhotos) { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                    .clickable { onPhotoClick(photo) }
            )
        }
    }
}


}

@Composable
fun VaultScreen(
viewModel: GalleryViewModel,
onPhotoClick: (PhotoEntity) -> Unit
) {
val isUnlocked by viewModel.isVaultUnlocked.collectAsState()
var pinInput by remember { mutableStateOf("") }
var isError by remember { mutableStateOf(false) }

Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    if (!isUnlocked) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Личный Сейф (AES-256)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Введите PIN для доступа к скрытым фото", color = Color.Gray, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pinInput,
            onValueChange = { 
                if (it.length <= 4) {
                    pinInput = it
                    isError = false
                }
            },
            placeholder = { Text("PIN (1234)", color = Color.Gray) },
            shape = RoundedCornerShape(16.dp),
            isError = isError,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        if (isError) {
            Text("Неверный PIN-код (по умолчанию 1234)", color = Color(0xFFF43F5E), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (!viewModel.unlockVault(pinInput)) {
                    isError = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Разблокировать", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Сейф разблокирован 🔓", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
            Button(
                onClick = { viewModel.lockVault() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Заблокировать", color = Color.White, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Скрытые фотографии отсутствуют. Добавьте их через просмотрщик фото кнопкой «В Сейф».", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}


}

@Composable
fun PermissionRequestView(onRequest: () -> Unit) {
Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
Column(
horizontalAlignment = Alignment.CenterHorizontally,
modifier = Modifier.padding(24.dp)
) {
Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(56.dp))
Spacer(Modifier.height(16.dp))
Text("Требуется доступ к фото", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
Spacer(Modifier.height(8.dp))
Text(
"Предоставьте доступ к медиатеке, чтобы галерея считала ваши фотографии и запустила локальное распознавание лиц.",
color = Color.Gray,
fontSize = 13.sp,
textAlign = TextAlign.Center
)
Spacer(Modifier.height(20.dp))
Button(
onClick = onRequest,
colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
shape = RoundedCornerShape(16.dp)
) {
Text("Разрешить доступ", color = Color.Black, fontWeight = FontWeight.Bold)
}
}
}
}

@Composable
fun FixedBottomNavBar(
selectedTab: Int,
onTabSelected: (Int) -> Unit
) {
Surface(
modifier = Modifier
.fillMaxWidth()
.height(64.dp)
.navigationBarsPadding(),
color = Color(0xF2090D1C),
border = BorderStroke(1.dp, Color(0x1AFFFFFF))
) {
Row(
modifier = Modifier.fillMaxSize(),
horizontalArrangement = Arrangement.SpaceAround,
verticalAlignment = Alignment.CenterVertically
) {
val tabs = listOf(
Triple(0, "Фото", Icons.Default.Image),
Triple(1, "Лица", Icons.Default.Face),
Triple(2, "Поиск", Icons.Default.Search),
Triple(3, "Сейф", Icons.Default.Lock)
)
tabs.forEach { (index, title, icon) ->
val isSelected = selectedTab == index
Column(
modifier = Modifier
.weight(1f)
.fillMaxHeight()
.clickable { onTabSelected(index) },
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center
) {
Icon(
icon,
contentDescription = title,
tint = if (isSelected) Color(0xFF22D3EE) else Color(0xFF64748B),
modifier = Modifier.size(20.dp)
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
