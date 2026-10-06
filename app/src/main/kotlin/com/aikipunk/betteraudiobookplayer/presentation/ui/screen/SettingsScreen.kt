package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.domain.about.AppInfo
import com.aikipunk.betteraudiobookplayer.domain.settings.ThemeMode
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.*
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

private val SLEEP_DEFAULT_OPTIONS = listOf(10, 15, 20, 30, 45, 60, 90)
private val APP_VOLUME_MARKERS = listOf(50, 100, 150)
private const val MIN_APP_VOLUME_PERCENT = 0f
private const val MAX_APP_VOLUME_PERCENT = 150f
private const val WARNING_APP_VOLUME_PERCENT = 100

private enum class SettingsDialog { SKIP_BACK, SKIP_FORWARD, SPEED, SLEEP_DEFAULT, APP_LICENSE }

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val skipBack by viewModel.skipBackSeconds.collectAsStateWithLifecycle()
    val skipForward by viewModel.skipForwardSeconds.collectAsStateWithLifecycle()
    val rewindOnResume by viewModel.rewindOnResume.collectAsStateWithLifecycle()
    val defaultSpeed by viewModel.defaultSpeed.collectAsStateWithLifecycle()
    val appVolume by viewModel.appVolume.collectAsStateWithLifecycle()
    val gradualVolumeFade by viewModel.gradualVolumeFade.collectAsStateWithLifecycle()
    val pauseOnDisconnect by viewModel.pauseOnHeadphoneDisconnect.collectAsStateWithLifecycle()
    val sleepDefault by viewModel.sleepTimerDefaultMinutes.collectAsStateWithLifecycle()
    val shakeToExtend by viewModel.shakeToExtend.collectAsStateWithLifecycle()

    var openDialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    var sliderVolume by rememberSaveable { mutableStateOf(appVolume.toFloat()) }
    var pendingHighVolume by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val versionName = remember { appVersionName(context) }
    // Held outside the settings column so returning from the licenses screen keeps the scroll position
    val scrollState = rememberScrollState()

    LaunchedEffect(appVolume, pendingHighVolume) {
        if (pendingHighVolume == null) sliderVolume = appVolume.toFloat()
    }

    if (showOpenSourceLicenses) {
        OpenSourceLicensesContent(onBack = { showOpenSourceLicenses = false }, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .semantics { heading() },
        )

        SettingsSection("Appearance") {
            ThemeModeRow(selected = themeMode, onSelected = viewModel::setThemeMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsDivider()
                SwitchRow(
                    title = "Dynamic color",
                    subtitle = "Use colors from your wallpaper",
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        }

        SettingsSection("Playback") {
            ValueRow("Skip back", "$skipBack s") { openDialog = SettingsDialog.SKIP_BACK }
            SettingsDivider()
            ValueRow("Skip forward", "$skipForward s") { openDialog = SettingsDialog.SKIP_FORWARD }
            SettingsDivider()
            ValueRow("Default speed", formatSpeed(defaultSpeed), subtitle = "For books you haven't started") {
                openDialog = SettingsDialog.SPEED
            }
            SettingsDivider()
            SwitchRow(
                title = "Rewind on resume",
                subtitle = "Go back a few seconds after a pause (more after long pauses)",
                checked = rewindOnResume,
                onCheckedChange = viewModel::setRewindOnResume,
            )
            SettingsDivider()
            SwitchRow(
                title = "Pause when headphones disconnect",
                subtitle = "Wired or Bluetooth",
                checked = pauseOnDisconnect,
                onCheckedChange = viewModel::setPauseOnHeadphoneDisconnect,
            )
        }

        SettingsSection("Volume control") {
            AppVolumeRow(
                currentVolume = sliderVolume.roundToInt(),
                onVolumeChange = { sliderVolume = it },
                onVolumeChangeFinished = {
                    val selected = sliderVolume.roundToInt().coerceIn(
                        MIN_APP_VOLUME_PERCENT.roundToInt(),
                        MAX_APP_VOLUME_PERCENT.roundToInt(),
                    )
                    when {
                        selected == appVolume -> sliderVolume = appVolume.toFloat()
                        selected > WARNING_APP_VOLUME_PERCENT -> pendingHighVolume = selected
                        else -> viewModel.setAppVolume(selected)
                    }
                },
            )
        }

        SettingsSection("Gradual fade") {
            SwitchRow(
                title = "Gradual Volume Fade (Sleep timer required)",
                subtitle = "When a sleep timer is active, smoothly fade from current volume to the final app volume setting over the timer's duration.",
                checked = gradualVolumeFade,
                onCheckedChange = viewModel::setGradualVolumeFade,
            )
        }

        SettingsSection("Sleep timer") {
            ValueRow("Default length", "$sleepDefault min") { openDialog = SettingsDialog.SLEEP_DEFAULT }
            SettingsDivider()
            SwitchRow(
                title = "Shake to extend",
                subtitle = "Shake your phone near the end of the sleep timer to add 10 minutes",
                checked = shakeToExtend,
                onCheckedChange = viewModel::setShakeToExtend,
            )
        }

        SettingsSection("About") {
            ValueRow("Version", versionName, onClick = null)
            SettingsDivider()
            ValueRow("Made by", AppInfo.DEVELOPER, onClick = null)
            SettingsDivider()
            ValueRow("License", "", subtitle = AppInfo.COPYRIGHT) { openDialog = SettingsDialog.APP_LICENSE }
            SettingsDivider()
            ValueRow("Open-source licenses", "") {
                showOpenSourceLicenses = true
            }
        }
        Spacer(modifier = Modifier.padding(bottom = 16.dp))
    }

    when (openDialog) {
        SettingsDialog.SKIP_BACK -> ChoiceDialog(
            title = "Skip back",
            options = UserPreferences.SKIP_BACK_OPTIONS,
            selected = skipBack,
            label = { "$it seconds" },
            onSelected = viewModel::setSkipBackSeconds,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.SKIP_FORWARD -> ChoiceDialog(
            title = "Skip forward",
            options = UserPreferences.SKIP_FORWARD_OPTIONS,
            selected = skipForward,
            label = { "$it seconds" },
            onSelected = viewModel::setSkipForwardSeconds,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.SPEED -> ChoiceDialog(
            title = "Default speed",
            options = SPEED_OPTIONS,
            selected = defaultSpeed,
            label = { formatSpeed(it) },
            onSelected = viewModel::setDefaultSpeed,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.SLEEP_DEFAULT -> ChoiceDialog(
            title = "Default sleep timer",
            options = SLEEP_DEFAULT_OPTIONS,
            selected = sleepDefault,
            label = { "$it minutes" },
            onSelected = viewModel::setSleepTimerDefaultMinutes,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.APP_LICENSE -> AppLicenseDialog(onDismiss = { openDialog = null })
        null -> Unit
    }

    pendingHighVolume?.let { pendingVolume ->
        AlertDialog(
            onDismissRequest = {
                pendingHighVolume = null
                sliderVolume = appVolume.toFloat()
            },
            containerColor = SurfaceContainerLow,
            title = { Text("⚠️ Hearing Damage Risk", color = TextPrimary) },
            text = {
                Text(
                    "Volume above 100% is louder than system maximum and may cause hearing damage with prolonged use. Use cautiously.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setAppVolume(pendingVolume)
                        sliderVolume = pendingVolume.toFloat()
                        pendingHighVolume = null
                    },
                ) {
                    Text("Continue", color = TerracottaPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingHighVolume = null
                        sliderVolume = appVolume.toFloat()
                    },
                ) {
                    Text("Cancel", color = TerracottaPrimary)
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TerracottaPrimary,
        modifier = Modifier
            .padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
    Surface(
        color = SurfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = SurfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun AppVolumeRow(
    currentVolume: Int,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("App Volume", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("$currentVolume%", color = TerracottaPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Adjust playback volume independent of system volume. 100% = full system volume. Values above 100% may cause hearing damage.",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Slider(
            value = currentVolume.toFloat(),
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChangeFinished,
            valueRange = MIN_APP_VOLUME_PERCENT..MAX_APP_VOLUME_PERCENT,
            colors = SliderDefaults.colors(
                thumbColor = TerracottaPrimary,
                activeTrackColor = TerracottaPrimary,
                inactiveTrackColor = SurfaceContainer,
            ),
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            APP_VOLUME_MARKERS.forEach { marker ->
                Text("$marker%", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Theme", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Follows your phone's setting unless you choose Light or Dark",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.padding(top = 12.dp))
        val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = TerracottaContainer,
                        activeContentColor = TextPrimary,
                        inactiveContainerColor = SurfaceContainerLow,
                        inactiveContentColor = TextSecondary,
                        activeBorderColor = TextMuted,
                        inactiveBorderColor = TextMuted,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = TerracottaPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceContainer,
                uncheckedBorderColor = TextMuted,
            ),
        )
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        if (value.isNotEmpty()) {
            Text(value, color = if (onClick != null) TerracottaPrimary else TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelected(option)
                                    onDismiss()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = TerracottaPrimary,
                                unselectedColor = TextSecondary,
                            ),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label(option), color = TextPrimary, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TerracottaPrimary) }
        },
    )
}

private fun appVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
