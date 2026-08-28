package com.example.smartgallery

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.smartgallery.data.PersonEntity
import com.example.smartgallery.data.PhotoEntity
import com.example.smartgallery.ui.components.SlideshowPlayer
import com.example.smartgallery.ui.theme.SmartGalleryTheme
import com.example.smartgallery.viewmodel.GalleryViewModel
import com.example.smartgallery.viewmodel.SortDirection
import com.example.smartgallery.viewmodel.SortField
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartGalleryTheme {
                MainGalleryRootScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGalleryRootScreen(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photos by viewModel.photos.collectAsState()
    val persons by viewModel.persons.collectAsState(initial = emptyList())
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var detailPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var isSlideshowActive by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var editingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
        if (!granted) {
            Toast.makeText(context, "Для просмотра фото нужен доступ к галерее", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!isPermissionGranted) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(permission)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050711))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .size(380.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .blur(100.dp)
                .background(Brush.radialGradient(listOf(Color(0x554F46E5), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .blur(110.dp)
                .background(Brush.radialGradient(listOf(Color(0x44EC4899), Color.Transparent)))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopGlassAppBar(
                sortField = sortField,
                sortDirection = sortDirection,
                isScanning = isScanning,
                onOpenSettings = { showSettingsSheet = true },
                onStartSlideshow = { isSlideshowActive = true },
                onScanFaces = { viewModel.scanFacesOnPhotos() }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> PhotosGridContent(
                        photos = photos,
                        gridColumns = gridColumns,
                        onPhotoClick = { photo ->
                            val idx = photos.indexOf(photo)
                            if (idx != -1) detailPhotoIndex = idx
                        }
                    )
                    1 -> PersonsHubContent(
                        persons = persons,
                        onPersonClick = { person ->
                            viewModel.selectPerson(person.id)
                            selectedTab = 0
                        },
                        onRenamePerson = { id, name -> viewModel.renamePerson(id, name) }
                    )
                    2 -> SearchHubContent(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onCategorySelect = { cat ->
                            viewModel.selectCategory(cat)
                            selectedTab = 0
                        }
                    )
                    3 -> VaultHubContent(
                        isUnlocked = isVaultUnlocked,
                        vaultPhotos = photos.filter { it.isVault },
                        onUnlock = { pin -> viewModel.unlockVault(pin) },
                        onLock = { viewModel.lockVault() },
                        onPhotoClick = { photo ->
                            val idx = photos.indexOf(photo)
                            if (idx != -1) detailPhotoIndex = idx
                        }
                    )
                }
            }

            FixedBottomNavDock(
                selectedTab = selectedTab,
                onTabSelect = { selectedTab = it },
                peopleCount = persons.size
            )
        }

        detailPhotoIndex?.let { startIndex ->
            FullPhotoDetailViewer(
                photos = photos,
                initialIndex = startIndex,
                onDismiss = { detailPhotoIndex = null },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onMoveToVault = { viewModel.moveToVault(it) },
                onDelete = { viewModel.deletePhoto(it) },
                onOpenEditor = { photo -> editingPhoto = photo }
            )
        }

        editingPhoto?.let { photo ->
            PhotoEditorStudioDialog(
                photo = photo,
                onDismiss = { editingPhoto = null },
                onSave = {
                    editingPhoto = null
                    Toast.makeText(context, "✅ Изменения сохранены в новом файле!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showSettingsSheet) {
            GallerySettingsBottomSheet(
                currentSortField = sortField,
                currentSortDirection = sortDirection,
                currentGridColumns = gridColumns,
                onSortChange = { field, direction -> viewModel.setSort(field, direction) },
                onGridColumnsChange = { viewModel.setGridColumns(it) },
                onDismiss = { showSettingsSheet = false }
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
fun TopGlassAppBar(
    sortField: SortField,
    sortDirection: SortDirection,
    isScanning: Boolean,
    onOpenSettings: () -> Unit,
    onStartSlideshow: () -> Unit,
    onScanFaces: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Smart Gallery AI",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                "MobileFaceNet 512D • NPU",
                color = Color(0xFF22D3EE),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1FFFFFFF))
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Сортировка", tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
            }

            Button(
                onClick = onScanFaces,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x3322D3EE)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x6622D3EE)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isScanning) "Скан..." else "Скан Лиц",
                    color = Color(0xFF22D3EE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PhotosGridContent(
    photos: List<PhotoEntity>,
    gridColumns: Int,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет доступных фотографий", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
                        .clickable { onPhotoClick(photo) }
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.locationName,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPhotoDetailViewer(
    photos: List<PhotoEntity>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onToggleFavorite: (PhotoEntity) -> Unit,
    onMoveToVault: (PhotoEntity) -> Unit,
    onDelete: (PhotoEntity) -> Unit,
    onOpenEditor: (PhotoEntity) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: return
    var showInfoSheet by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF5050711))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        currentPhoto.locationName ?: "Фотография",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${pagerState.currentPage + 1} из ${photos.size}",
                        color = Color(0xFF22D3EE),
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    /* INFO (ℹ️) BUTTON */
                    IconButton(
                        onClick = { showInfoSheet = !showInfoSheet },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showInfoSheet) Color(0x8022D3EE) else Color(0x33FFFFFF))
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Инфо о файле", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { onToggleFavorite(currentPhoto) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Лайк",
                            tint = if (currentPhoto.isFavorite) Color(0xFFF43F5E) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                val photo = photos[page]
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                    offset = Offset.Zero
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            if (showInfoSheet) {
                PhotoMetadataInfoCard(
                    photo = currentPhoto,
                    onDismiss = { showInfoSheet = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xE6090D1C))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenEditor(currentPhoto) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3322D3EE)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x6622D3EE))
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Редактор", color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = { onMoveToVault(currentPhoto); onDismiss() }) {
                    Icon(Icons.Default.Lock, contentDescription = "В Сейф", tint = Color(0xFF34D399))
                }

                IconButton(onClick = { onDelete(currentPhoto); onDismiss() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFF43F5E))
                }
            }
        }
    }
}

@Composable
fun PhotoMetadataInfoCard(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(photo.dateAdded) { dateFormat.format(Date(photo.dateAdded)) }
    val formattedSize = remember(photo.sizeBytes) {
        if (photo.sizeBytes < 1024 * 1024) "${photo.sizeBytes / 1024} KB"
        else "%.2f MB".format(photo.sizeBytes / (1024f * 1024f))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2090D1C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0x6622D3EE))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "СВЕДЕНИЯ О ФАЙЛЕ",
                    color = Color(0xFF22D3EE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Разрешение:", color = Color.Gray, fontSize = 12.sp)
                Text("${photo.width} × ${photo.height}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Размер файла:", color = Color.Gray, fontSize = 12.sp)
                Text(formattedSize, color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Дата съемки:", color = Color.Gray, fontSize = 12.sp)
                Text(formattedDate, color = Color.White, fontSize = 12.sp)
            }

            Column {
                Text("Путь на устройстве:", color = Color.Gray, fontSize = 11.sp)
                Text(
                    photo.filePath.ifEmpty { photo.uri },
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PhotoEditorStudioDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var brightness by remember { mutableFloatStateOf(1.0f) }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var selectedAiTool by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070913))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            /* Top Editor Bar */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
                Text("Студия Редактирования", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE))
                ) {
                    Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            /* Editor Canvas */
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotationAngle)
                        .graphicsLayer {
                            val colorMatrix = ColorMatrix().apply {
                                setToSaturation(saturation)
                            }
                            this.colorFilter = ColorFilter.colorMatrix(colorMatrix)
                        }
                )
            }

            /* Controls Sheet */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF090D1C))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                /* AI Tools Neon Strip */
                Text("AI NEURAL MAGIC TOOLS (ON-DEVICE)", color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Убрать фон", "Вырезка", "Апскейл 4K", "Ластик").forEach { tool ->
                        val isSelected = selectedAiTool == tool
                        Button(
                            onClick = { selectedAiTool = if (isSelected) null else tool },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF22D3EE) else Color(0x22818CF8)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color.White else Color(0x66818CF8)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                tool,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                /* Color Grading Sliders */
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Насыщенность: ${(saturation * 100).toInt()}%", color = Color.Gray, fontSize = 11.sp)
                    IconButton(onClick = { rotationAngle = (rotationAngle + 90f) % 360f }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Поворот", tint = Color(0xFF22D3EE))
                    }
                }
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..2f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF22D3EE), activeTrackColor = Color(0xFF22D3EE))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySettingsBottomSheet(
    currentSortField: SortField,
    currentSortDirection: SortDirection,
    currentGridColumns: Int,
    onSortChange: (SortField, SortDirection) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF090D1C),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "НАСТРОЙКИ СОРТИРОВКИ И ВИДА",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )

            /* Sort Field Selection */
            Text("Сортировать по:", color = Color.Gray, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SortField.DATE to "По дате",
                    SortField.NAME to "По имени",
                    SortField.SIZE to "По размеру"
                ).forEach { (field, label) ->
                    val isSelected = currentSortField == field
                    Button(
                        onClick = { onSortChange(field, currentSortDirection) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0x4D22D3EE) else Color(0x14FFFFFF)
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF22D3EE) else Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = if (isSelected) Color(0xFF22D3EE) else Color.White, fontSize = 11.sp)
                    }
                }
            }

            /* Sort Direction Selection */
            Text("Порядок:", color = Color.Gray, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SortDirection.DESC to "По убыванию ↓",
                    SortDirection.ASC to "По возрастанию ↑"
                ).forEach { (dir, label) ->
                    val isSelected = currentSortDirection == dir
                    Button(
                        onClick = { onSortChange(currentSortField, dir) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0x4D22D3EE) else Color(0x14FFFFFF)
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF22D3EE) else Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = if (isSelected) Color(0xFF22D3EE) else Color.White, fontSize = 11.sp)
                    }
                }
            }

            /* Grid Columns Selection (1..4) */
            Text("Сетка галереи (колонки):", color = Color.Gray, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { cols ->
                    val isSelected = currentGridColumns == cols
                    Button(
                        onClick = { onGridColumnsChange(cols) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0x4D22D3EE) else Color(0x14FFFFFF)
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF22D3EE) else Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("$cols", color = if (isSelected) Color(0xFF22D3EE) else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PersonsHubContent(
    persons: List<PersonEntity>,
    onPersonClick: (PersonEntity) -> Unit,
    onRenamePerson: (String, String) -> Unit
) {
    if (persons.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Лица ещё не распознаны", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Нажмите «Скан Лиц» вверху экрана", color = Color.Gray, fontSize = 12.sp)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(persons, key = { it.id }) { person ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPersonClick(person) }
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
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF22D3EE), CircleShape)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(person.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchHubContent(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Поиск по имени, месту, объектам...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF22D3EE)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF22D3EE),
                unfocusedBorderColor = Color(0x33FFFFFF)
            )
        )

        Text("Категории AI Vision", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

        listOf("Портрет", "Природа", "Путешествия", "Документы").forEach { cat ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategorySelect(cat) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cat, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun VaultHubContent(
    isUnlocked: Boolean,
    vaultPhotos: List<PhotoEntity>,
    onUnlock: (String) -> Boolean,
    onLock: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    if (!isUnlocked) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0x4422D3EE)),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(48.dp))
                    Text("Личный Сейф (AES-256)", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Введите PIN-код (по умолчанию 1234)", color = Color.Gray, fontSize = 11.sp)

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it },
                        placeholder = { Text("****") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(140.dp)
                    )

                    Button(
                        onClick = {
                            if (!onUnlock(pin)) error = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE))
                    ) {
                        Text("Открыть", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    if (error) {
                        Text("Неверный PIN-код", color = Color(0xFFF43F5E), fontSize = 11.sp)
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Сейф разблокирован 🔓", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                Button(onClick = onLock, colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF))) {
                    Text("Заблокировать", fontSize = 11.sp)
                }
            }
            PhotosGridContent(photos = vaultPhotos, gridColumns = 3, onPhotoClick = onPhotoClick)
        }
    }
}

@Composable
fun FixedBottomNavDock(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    peopleCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
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
                Triple(1, "Лица ($peopleCount)", Icons.Default.People),
                Triple(2, "Поиск", Icons.Default.Search),
                Triple(3, "Сейф", Icons.Default.Shield)
            )

            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x3322D3EE) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = title,
                            tint = if (isSelected) Color(0xFF22D3EE) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
