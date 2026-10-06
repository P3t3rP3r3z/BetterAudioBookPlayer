package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikipunk.betteraudiobookplayer.domain.model.AudioOutput
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.*

internal fun audioOutputIcon(kind: AudioOutput.Kind): ImageVector = when (kind) {
    AudioOutput.Kind.PHONE_SPEAKER -> Icons.Outlined.PhoneAndroid
    AudioOutput.Kind.WIRED -> Icons.Outlined.Headset
    AudioOutput.Kind.USB -> Icons.Outlined.Usb
    AudioOutput.Kind.BLUETOOTH -> Icons.Outlined.Bluetooth
    AudioOutput.Kind.HEARING_AID -> Icons.Outlined.Hearing
    AudioOutput.Kind.AUX -> Icons.Outlined.Cable
    AudioOutput.Kind.HDMI -> Icons.Outlined.Tv
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioOutputSheet(
    outputs: List<AudioOutput>,
    current: AudioOutput?,
    onSelect: (AudioOutput) -> Unit,
    onMoreDevices: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = "Play audio on",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            Column(modifier = Modifier.selectableGroup()) {
                outputs.forEach { output ->
                    val selected = output.id == current?.id
                    OutputRow(
                        icon = audioOutputIcon(output.kind),
                        title = output.name,
                        highlighted = selected,
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(output) },
                        ),
                    ) {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = TerracottaPrimary)
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceContainer, modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            OutputRow(
                icon = Icons.AutoMirrored.Outlined.BluetoothSearching,
                title = "Connect a Bluetooth device…",
                modifier = Modifier.clickable(role = Role.Button) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        onDismiss()
                    } catch (_: ActivityNotFoundException) {
                        // No Bluetooth settings screen on this device; leave the sheet open
                    }
                },
            )
            OutputRow(
                icon = Icons.Outlined.Devices,
                title = "More devices…",
                modifier = Modifier.clickable(role = Role.Button, onClick = onMoreDevices),
            )
        }
    }
}

@Composable
private fun OutputRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (highlighted) TerracottaPrimary else TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = if (highlighted) TerracottaPrimary else TextPrimary,
            fontSize = 16.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
