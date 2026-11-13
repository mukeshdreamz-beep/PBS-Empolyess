package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.CapturedLocation
import com.example.domain.SelfieLocationHelper
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors
import kotlinx.coroutines.launch

@Composable
fun SelfieAttendanceDialog(
    isCheckOut: Boolean,
    employeeName: String,
    onDismiss: () -> Unit,
    onConfirmPunch: (photoBase64: String, lat: Double, lng: Double, address: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val semantic = LocalAttendanceColors.current

    fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(hasCameraPermission()) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission()) }
    var permissionRequested by remember { mutableStateOf(false) }

    var capturedPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var capturedLocation by remember { mutableStateOf<CapturedLocation?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        cameraGranted = grants[Manifest.permission.CAMERA] == true || hasCameraPermission()
        locationGranted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) ||
            hasLocationPermission()
        permissionRequested = true
    }

    fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun loadLocation() {
        if (!locationGranted) return
        coroutineScope.launch {
            isFetchingLocation = true
            locationError = null
            val res = SelfieLocationHelper.fetchCurrentLocation(context)
            res.onSuccess { loc ->
                capturedLocation = loc
            }.onFailure { err ->
                locationError = err.message ?: context.getString(R.string.location_fetch_failed)
            }
            isFetchingLocation = false
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted || !locationGranted) {
            requestPermissions()
        }
    }

    LaunchedEffect(locationGranted) {
        if (locationGranted && capturedLocation == null && !isFetchingLocation) {
            loadLocation()
        }
    }

    val previewBitmap = remember(capturedPhotoBase64) {
        capturedPhotoBase64?.let { SelfieLocationHelper.decodeBase64ToImageBitmap(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (isCheckOut) R.string.selfie_checkout_title else R.string.selfie_checkin_title
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$employeeName · ${stringResource(R.string.selfie_gps_required_note)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cancel_action)
                        )
                    }
                }

                if (!cameraGranted || !locationGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = stringResource(R.string.camera_location_permission_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = stringResource(R.string.camera_location_permission_body),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { requestPermissions() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("grant_camera_location_button")
                            ) {
                                Text(stringResource(R.string.grant_permissions_button))
                            }
                        }
                    }
                } else {
                    // Camera Preview OR Captured Selfie Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = stringResource(R.string.captured_selfie_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = semantic.fullDayContainer,
                                contentColor = semantic.onFullDayContainer,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, semantic.fullDayBorder),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = stringResource(R.string.selfie_ready_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            val previewView = remember {
                                PreviewView(context).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            }
                            DisposableEffect(lifecycleOwner) {
                                val providerFuture = ProcessCameraProvider.getInstance(context)
                                val executor = ContextCompat.getMainExecutor(context)
                                var boundProvider: ProcessCameraProvider? = null
                                providerFuture.addListener({
                                    try {
                                        val provider = providerFuture.get()
                                        boundProvider = provider
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        imageCaptureRef = capture
                                        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        }
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                        cameraError = null
                                    } catch (e: Exception) {
                                        cameraError = e.localizedMessage ?: "Camera unavailable"
                                    }
                                }, executor)

                                onDispose {
                                    runCatching { boundProvider?.unbindAll() }
                                }
                            }
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (cameraError != null) {
                        Text(
                            text = cameraError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Capture / Retake Selfie Button
                    if (capturedPhotoBase64 == null) {
                        Button(
                            onClick = {
                                val capture = imageCaptureRef ?: return@Button
                                isCapturingPhoto = true
                                cameraError = null
                                val executor = ContextCompat.getMainExecutor(context)
                                capture.takePicture(
                                    executor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val encoded = try {
                                                SelfieLocationHelper.encodeImageProxyToBase64(image)
                                            } finally {
                                                image.close()
                                            }
                                            if (encoded.isNotBlank()) {
                                                capturedPhotoBase64 = encoded
                                            } else {
                                                cameraError = "Could not process selfie image. Please retry."
                                            }
                                            isCapturingPhoto = false
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            cameraError = exception.localizedMessage ?: "Selfie capture failed"
                                            isCapturingPhoto = false
                                        }
                                    }
                                )
                            },
                            enabled = !isCapturingPhoto && imageCaptureRef != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("capture_selfie_button")
                        ) {
                            if (isCapturingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.capture_selfie_button))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { capturedPhotoBase64 = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("retake_selfie_button")
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.retake_selfie_button))
                        }
                    }

                    // GPS Location Verification Card
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (capturedLocation != null) semantic.fullDayBorder else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = if (capturedLocation != null) semantic.fullDayAccent else MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.gps_location_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                when {
                                    isFetchingLocation -> {
                                        Text(
                                            text = stringResource(R.string.detecting_gps_location),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    capturedLocation != null -> {
                                        val loc = capturedLocation!!
                                        Text(
                                            text = loc.address,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = SelfieLocationHelper.formatCoords(loc.latitude, loc.longitude),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    locationError != null -> {
                                        Text(
                                            text = locationError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = stringResource(R.string.tap_refresh_gps),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { loadLocation() },
                                enabled = !isFetchingLocation,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_gps_button")
                            ) {
                                if (isFetchingLocation) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.refresh_gps_cd)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Confirm / Cancel actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                    Button(
                        onClick = {
                            val photo = capturedPhotoBase64 ?: return@Button
                            val loc = capturedLocation ?: return@Button
                            onConfirmPunch(photo, loc.latitude, loc.longitude, loc.address)
                        },
                        enabled = capturedPhotoBase64 != null && capturedLocation != null,
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 48.dp)
                            .testTag("confirm_selfie_punch_button")
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (isCheckOut) R.string.confirm_checkout_button else R.string.confirm_checkin_button
                            )
                        )
                    }
                }
            }
        }
    }
}
