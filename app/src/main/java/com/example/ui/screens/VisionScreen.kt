package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssistantLanguage
import com.example.data.model.MemoryCategory
import com.example.ui.theme.MyraCyan
import com.example.ui.theme.MyraViolet
import com.example.ui.viewmodel.MyraViewModel
import com.example.ui.viewmodel.StreamUiState
import com.example.util.VoiceAssistantManager
import java.io.File

private enum class VisionPreset(val label: String, val defaultPrompt: String) {
    ANALYZE("Analyze Scene", "Describe what is in this image, key details, and actionable insights."),
    OCR("Extract Text (OCR)", "Extract all readable text accurately from this image and organize it cleanly."),
    TRANSLATE("Translate Text", "Extract the text in this image and translate it clearly into my preferred language."),
    CODE("Explain Screen / Code", "Read the code, UI, or technical diagram in this screenshot and explain what it does or how to improve it.")
}

@Composable
fun VisionScreen(
    viewModel: MyraViewModel,
    voiceManager: VoiceAssistantManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val visionState by viewModel.visionStreamState.collectAsStateWithLifecycle()
    val language = AssistantLanguage.fromCode(prefs.language)

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedPreset by remember { mutableStateOf(VisionPreset.ANALYZE) }
    var customPrompt by remember { mutableStateOf(VisionPreset.ANALYZE.defaultPrompt) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            loadError = null
            selectedBitmap = decodeScaledBitmap(context, uri)
            if (selectedBitmap == null) {
                loadError = "Unable to decode the selected image."
            } else {
                viewModel.clearVisionState()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            loadError = null
            selectedBitmap = decodeScaledBitmap(context, uri)
            if (selectedBitmap == null) {
                loadError = "Unable to load captured photo."
            } else {
                viewModel.clearVisionState()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "MYRA Visual Lens",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Multimodal OCR, screen understanding & photo analysis",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Image source buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("pick_image_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gallery / Shot")
            }

            OutlinedButton(
                onClick = {
                    val uri = createTempImageUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("camera_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Capture")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = selectedBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Selected image for MYRA Visual Lens",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusWeak,
                        contentDescription = null,
                        tint = MyraCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pick a screenshot or snap a photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!loadError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = loadError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Mode Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VisionPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = {
                        selectedPreset = preset
                        customPrompt = preset.defaultPrompt
                    },
                    label = { Text(preset.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = customPrompt,
            onValueChange = { customPrompt = it },
            label = { Text("Vision prompt") },
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vision_prompt_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                selectedBitmap?.let { bmp ->
                    viewModel.analyzeImage(bmp, customPrompt)
                }
            },
            enabled = selectedBitmap != null &&
                visionState !is StreamUiState.Thinking &&
                visionState !is StreamUiState.Streaming,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("analyze_vision_button")
        ) {
            Text("Analyze with MYRA Lens")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Result Display
        when (val state = visionState) {
            is StreamUiState.Thinking -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scanning image…", style = MaterialTheme.typography.labelMedium, color = MyraCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MyraCyan)
                    }
                }
            }
            is StreamUiState.Streaming -> {
                VisionResultCard(
                    text = state.text,
                    isFinal = false,
                    onSpeak = {},
                    onSaveMemory = {}
                )
            }
            is StreamUiState.Completed -> {
                VisionResultCard(
                    text = state.text,
                    isFinal = true,
                    onSpeak = { voiceManager.speak(state.text, language) },
                    onSaveMemory = {
                        viewModel.addMemory(state.text.take(450), MemoryCategory.GENERAL.key)
                    }
                )
            }
            is StreamUiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            StreamUiState.Idle -> Unit
        }
    }
}

@Composable
private fun VisionResultCard(
    text: String,
    isFinal: Boolean,
    onSpeak: () -> Unit,
    onSaveMemory: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MyraViolet.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MYRA Lens Output",
                    style = MaterialTheme.typography.labelMedium,
                    color = MyraCyan
                )
                if (isFinal) {
                    Row {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Speak analysis",
                                tint = MyraCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onSaveMemory, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = "Save insight to memory",
                                tint = MyraViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "vision").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Suppress("DEPRECATION")
private fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val width = raw.width
        val height = raw.height
        val scale = minOf(1f, maxDim.toFloat() / maxOf(width, height).toFloat())
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
                raw,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            raw
        }
    } catch (_: Exception) {
        null
    }
}
