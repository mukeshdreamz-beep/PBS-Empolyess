package com.example.ui.components

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AttendanceRecordItem
import com.example.domain.AttendanceCalculator
import com.example.domain.SelfieLocationHelper
import com.example.ui.theme.JetBrainsMono

@Composable
fun AttendanceProofDialog(
    record: AttendanceRecordItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val inBitmap = remember(record.checkInPhotoBase64) {
        SelfieLocationHelper.decodeBase64ToImageBitmap(record.checkInPhotoBase64)
    }
    val outBitmap = remember(record.checkOutPhotoBase64) {
        SelfieLocationHelper.decodeBase64ToImageBitmap(record.checkOutPhotoBase64)
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
                .widthIn(max = 560.dp)
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
                            text = stringResource(R.string.proof_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${record.employeeName} · ${record.date} · ${record.employeeSite}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_action))
                    }
                }

                // Check-In Proof
                ProofPunchSectionCard(
                    title = "CHECK IN · ${AttendanceCalculator.formatTime12h(record.inMinutes)}",
                    bitmap = inBitmap,
                    lat = record.checkInLat,
                    lng = record.checkInLng,
                    address = record.checkInAddress,
                    emptyText = if (record.markedByRole == "ADMIN") {
                        stringResource(R.string.proof_marked_by_admin)
                    } else {
                        stringResource(R.string.proof_not_captured_yet)
                    },
                    onOpenMap = { lat, lng ->
                        SelfieLocationHelper.openLocationInMaps(
                            context,
                            lat,
                            lng,
                            "${record.employeeName} Check-In"
                        )
                    }
                )

                // Check-Out Proof
                ProofPunchSectionCard(
                    title = if (record.hasCheckedOut) {
                        "CHECK OUT · ${AttendanceCalculator.formatTime12h(record.outMinutes)}"
                    } else {
                        "CHECK OUT · Pending"
                    },
                    bitmap = outBitmap,
                    lat = record.checkOutLat,
                    lng = record.checkOutLng,
                    address = record.checkOutAddress,
                    emptyText = if (!record.hasCheckedOut) {
                        stringResource(R.string.proof_checkout_pending)
                    } else if (record.markedByRole == "ADMIN") {
                        stringResource(R.string.proof_marked_by_admin)
                    } else {
                        stringResource(R.string.proof_not_captured_yet)
                    },
                    onOpenMap = { lat, lng ->
                        SelfieLocationHelper.openLocationInMaps(
                            context,
                            lat,
                            lng,
                            "${record.employeeName} Check-Out"
                        )
                    }
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("close_proof_dialog_button")
                ) {
                    Text(stringResource(R.string.confirm_action))
                }
            }
        }
    }
}

@Composable
private fun ProofPunchSectionCard(
    title: String,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    lat: Double?,
    lng: Double?,
    address: String,
    emptyText: String,
    onOpenMap: (Double, Double) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NoPhotography,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (lat != null && lng != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = address.ifBlank { SelfieLocationHelper.formatCoords(lat, lng) },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = SelfieLocationHelper.formatCoords(lat, lng),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenMap(lat, lng) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Map, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.open_in_maps_button))
                    }
                }
            } else if (address.isNotBlank()) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
