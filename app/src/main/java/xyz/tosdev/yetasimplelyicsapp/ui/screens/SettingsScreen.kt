package xyz.tosdev.yetasimplelyicsapp.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import xyz.tosdev.yetasimplelyicsapp.ui.AccessStatus
import xyz.tosdev.yetasimplelyicsapp.ui.AppThemeMode
import xyz.tosdev.yetasimplelyicsapp.ui.HighlightStyle
import xyz.tosdev.yetasimplelyicsapp.ui.InactiveStyle
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppActions
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsDisplayMode
import xyz.tosdev.yetasimplelyicsapp.ui.MAX_LYRICS_TEXT_SCALE
import xyz.tosdev.yetasimplelyicsapp.ui.MIN_LYRICS_TEXT_SCALE
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState
import xyz.tosdev.yetasimplelyicsapp.ui.SystemAccessUiState
import xyz.tosdev.yetasimplelyicsapp.ui.normalizeLyricsTextScale
import xyz.tosdev.yetasimplelyicsapp.ui.scaledLyricsSp
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionSpecs
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion

private const val PROJECT_URL = "https://github.com/TostadoraDev/yasla"
private const val LICENSE_URL = "https://github.com/TostadoraDev/yasla/blob/main/LICENSE"

private enum class SettingsPicker {
    THEME,
    MOTION,
    PLAYBACK_VIEW,
    ALIGNMENT,
    HIGHLIGHT,
    INACTIVE,
    TRANSLATION_LANGUAGE,
    APP_LANGUAGE,
}

private data class PickerOption(val id: String, val label: String)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    systemAccess: SystemAccessUiState,
    actions: LyricsAppActions,
    onStateChange: (SettingsUiState) -> Unit,
) {
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    var lyricsAppearanceExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) { applicationVersionName(context.packageManager, context.packageName) }
    val motion = rememberSignalMotion(state.motionLevel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeading(
            eyebrow = stringResource(R.string.ui_settings_eyebrow),
            title = stringResource(R.string.ui_settings_title),
            description = stringResource(R.string.ui_settings_description),
        )
        Spacer(Modifier.height(24.dp))

        SettingsSection(stringResource(R.string.ui_settings_system_access)) {
            PermissionSettingRow(
                title = stringResource(R.string.ui_settings_media_sessions),
                detail = stringResource(R.string.ui_settings_media_sessions_detail),
                status = systemAccess.mediaSession,
                icon = Icons.Default.Lock,
                onAction = actions.onRequestMediaAccess,
            )
            PermissionSettingRow(
                title = stringResource(R.string.ui_settings_overlay_access),
                detail = stringResource(R.string.ui_settings_overlay_access_detail),
                status = systemAccess.overlay,
                icon = Icons.Default.Visibility,
                onAction = actions.onRequestOverlayAccess,
            )
            ToggleSetting(
                title = stringResource(R.string.ui_settings_notification),
                detail = stringResource(R.string.ui_settings_notification_detail),
                checked = systemAccess.lyricsNotificationEnabled,
                onCheckedChange = actions.onToggleLyricsNotification,
                icon = Icons.Default.Notifications,
                status = systemAccess.notifications,
            )
        }

        SettingsSection(stringResource(R.string.ui_settings_appearance)) {
            SelectionSettingRow(
                title = stringResource(R.string.ui_settings_theme),
                value = themeLabel(state.themeMode),
                onClick = { picker = SettingsPicker.THEME },
            )
            ToggleSetting(
                title = stringResource(R.string.ui_settings_dynamic_color),
                detail = stringResource(R.string.ui_settings_dynamic_color_detail),
                checked = state.dynamicColor,
                onCheckedChange = { onStateChange(state.copy(dynamicColor = it)) },
            )
            ToggleSetting(
                title = stringResource(R.string.ui_settings_high_contrast),
                detail = stringResource(R.string.ui_settings_high_contrast_detail),
                checked = state.highContrastLyrics,
                onCheckedChange = { onStateChange(state.copy(highContrastLyrics = it)) },
            )
            SelectionSettingRow(
                title = stringResource(R.string.ui_settings_motion),
                detail = stringResource(R.string.ui_settings_motion_detail),
                value = motionLabel(state.motionLevel),
                onClick = { picker = SettingsPicker.MOTION },
            )
        }

        SettingsSection(stringResource(R.string.ui_settings_lyrics)) {
            LyricsSizeSetting(state, onStateChange)
            ToggleSetting(
                title = stringResource(R.string.ui_settings_prefer_word_sync),
                detail = stringResource(R.string.ui_settings_prefer_word_sync_detail),
                checked = state.preferWordSync,
                onCheckedChange = { onStateChange(state.copy(preferWordSync = it)) },
            )
            SelectionSettingRow(
                title = stringResource(R.string.ui_settings_playback_view),
                detail = stringResource(R.string.ui_settings_current_word_explanation),
                value = lyricsDisplayLabel(state.lyricsDisplayMode),
                onClick = { picker = SettingsPicker.PLAYBACK_VIEW },
            )
            if (state.lyricsDisplayMode == LyricsDisplayMode.CURRENT_WORD) {
                ToggleSetting(
                    title = stringResource(R.string.ui_settings_fallback_line),
                    detail = stringResource(R.string.ui_settings_fallback_line_detail),
                    checked = state.currentWordFallbackToLine,
                    onCheckedChange = { onStateChange(state.copy(currentWordFallbackToLine = it)) },
                )
            }
            SelectionSettingRow(
                title = stringResource(R.string.ui_settings_lyrics_alignment),
                detail = stringResource(R.string.ui_settings_alignment_detail),
                value = alignmentLabel(state.lyricsAlignment),
                onClick = { picker = SettingsPicker.ALIGNMENT },
            )
            SelectionSettingRow(
                title = stringResource(R.string.ui_translation_language),
                value = translationLanguageLabel(state.translationLanguage),
                onClick = { picker = SettingsPicker.TRANSLATION_LANGUAGE },
            )
            ExpandableSettingRow(
                title = stringResource(R.string.ux_settings_karaoke_appearance),
                detail = stringResource(R.string.ux_settings_karaoke_appearance_detail),
                expanded = lyricsAppearanceExpanded,
                onClick = { lyricsAppearanceExpanded = !lyricsAppearanceExpanded },
            )
            AnimatedVisibility(
                visible = lyricsAppearanceExpanded,
                enter = when {
                    !motion.enabled -> EnterTransition.None
                    motion.reduced -> fadeIn(SignalMotionSpecs.content(motion))
                    else -> fadeIn(SignalMotionSpecs.content(motion)) +
                        expandVertically(animationSpec = SignalMotionSpecs.content(motion))
                },
                exit = when {
                    !motion.enabled -> ExitTransition.None
                    motion.reduced -> fadeOut(SignalMotionSpecs.exit(motion))
                    else -> fadeOut(SignalMotionSpecs.exit(motion)) +
                        shrinkVertically(animationSpec = SignalMotionSpecs.exit(motion))
                },
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectionSettingRow(
                        title = stringResource(R.string.ui_settings_karaoke_progress),
                        value = highlightLabel(state.karaokeStyle.highlight),
                        onClick = { picker = SettingsPicker.HIGHLIGHT },
                    )
                    if (state.karaokeStyle.highlight == HighlightStyle.CUSTOM_GRADIENT) {
                        ColorHexField(
                            label = stringResource(R.string.ui_gradient_start),
                            color = state.karaokeStyle.customStartArgb,
                            onColorChange = { color ->
                                onStateChange(state.copy(
                                    karaokeStyle = state.karaokeStyle.copy(customStartArgb = color),
                                ))
                            },
                        )
                        ColorHexField(
                            label = stringResource(R.string.ui_gradient_end),
                            color = state.karaokeStyle.customEndArgb,
                            onColorChange = { color ->
                                onStateChange(state.copy(
                                    karaokeStyle = state.karaokeStyle.copy(customEndArgb = color),
                                ))
                            },
                        )
                    }
                    SelectionSettingRow(
                        title = stringResource(R.string.ui_pending_text),
                        value = inactiveLabel(state.karaokeStyle.inactive),
                        onClick = { picker = SettingsPicker.INACTIVE },
                    )
                    if (state.karaokeStyle.inactive == InactiveStyle.CUSTOM) {
                        ColorHexField(
                            label = stringResource(R.string.ui_pending_color),
                            color = state.karaokeStyle.inactiveArgb,
                            onColorChange = { color ->
                                onStateChange(state.copy(
                                    karaokeStyle = state.karaokeStyle.copy(inactiveArgb = color),
                                ))
                            },
                        )
                    }
                    if (state.karaokeStyle.inactive == InactiveStyle.OPACITY) {
                        SettingSlider(
                            label = stringResource(R.string.ui_pending_opacity),
                            value = state.karaokeStyle.inactiveOpacity,
                            valueRange = 0.1f..0.9f,
                            display = "${(state.karaokeStyle.inactiveOpacity * 100).toInt()}%",
                            onValueChange = { value ->
                                onStateChange(state.copy(
                                    karaokeStyle = state.karaokeStyle.copy(inactiveOpacity = value),
                                ))
                            },
                        )
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.ux_settings_playback)) {
            ToggleSetting(
                title = stringResource(R.string.ui_settings_keep_awake),
                detail = stringResource(R.string.ui_settings_keep_awake_detail),
                checked = state.keepScreenAwake,
                onCheckedChange = { onStateChange(state.copy(keepScreenAwake = it)) },
            )
        }

        SettingsSection(stringResource(R.string.ux_settings_language)) {
            SelectionSettingRow(
                title = stringResource(R.string.ui_settings_language),
                detail = stringResource(R.string.ui_settings_language_detail),
                value = appLanguageLabel(state.languageTag),
                onClick = { picker = SettingsPicker.APP_LANGUAGE },
            )
        }

        SettingsSection(stringResource(R.string.ux_settings_about)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            ReadOnlySettingRow(
                title = stringResource(R.string.ux_settings_version),
                value = versionName,
            )
            ActionSettingRow(
                title = stringResource(R.string.ux_settings_github),
                detail = stringResource(R.string.ux_settings_github_detail),
                onClick = { runCatching { uriHandler.openUri(PROJECT_URL) } },
            )
            ActionSettingRow(
                title = stringResource(R.string.ux_settings_license),
                detail = stringResource(R.string.ux_settings_license_detail),
                onClick = { runCatching { uriHandler.openUri(LICENSE_URL) } },
            )
            SettingsRow(
                title = stringResource(R.string.ux_settings_credits),
                detail = stringResource(R.string.ux_settings_credits_detail),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    picker?.let { activePicker ->
        val model = pickerModel(activePicker, state)
        SettingsSelectionDialog(
            title = model.title,
            options = model.options,
            selectedId = model.selectedId,
            onDismiss = { picker = null },
            onSelect = { id ->
                when (activePicker) {
                    SettingsPicker.THEME -> onStateChange(state.copy(themeMode = AppThemeMode.valueOf(id)))
                    SettingsPicker.MOTION -> onStateChange(state.copy(motionLevel = MotionLevel.valueOf(id)))
                    SettingsPicker.PLAYBACK_VIEW -> onStateChange(
                        state.copy(lyricsDisplayMode = LyricsDisplayMode.valueOf(id)),
                    )
                    SettingsPicker.ALIGNMENT -> onStateChange(state.copy(lyricsAlignment = id))
                    SettingsPicker.HIGHLIGHT -> onStateChange(state.copy(
                        karaokeStyle = state.karaokeStyle.copy(highlight = HighlightStyle.valueOf(id)),
                    ))
                    SettingsPicker.INACTIVE -> onStateChange(state.copy(
                        karaokeStyle = state.karaokeStyle.copy(inactive = InactiveStyle.valueOf(id)),
                    ))
                    SettingsPicker.TRANSLATION_LANGUAGE -> onStateChange(
                        state.copy(translationLanguage = id),
                    )
                    SettingsPicker.APP_LANGUAGE -> actions.onLanguageChange(id)
                }
                picker = null
            },
        )
    }
}

private data class PickerModel(
    val title: String,
    val options: List<PickerOption>,
    val selectedId: String,
)

@Composable
private fun pickerModel(picker: SettingsPicker, state: SettingsUiState): PickerModel = when (picker) {
    SettingsPicker.THEME -> PickerModel(
        stringResource(R.string.ui_settings_theme),
        AppThemeMode.entries.map { PickerOption(it.name, themeLabel(it)) },
        state.themeMode.name,
    )
    SettingsPicker.MOTION -> PickerModel(
        stringResource(R.string.ui_settings_motion),
        MotionLevel.entries.map { PickerOption(it.name, motionLabel(it)) },
        state.motionLevel.name,
    )
    SettingsPicker.PLAYBACK_VIEW -> PickerModel(
        stringResource(R.string.ui_settings_playback_view),
        LyricsDisplayMode.entries.map { PickerOption(it.name, lyricsDisplayLabel(it)) },
        state.lyricsDisplayMode.name,
    )
    SettingsPicker.ALIGNMENT -> PickerModel(
        stringResource(R.string.ui_settings_lyrics_alignment),
        listOf("AUTO", "START", "CENTER", "END").map { PickerOption(it, alignmentLabel(it)) },
        state.lyricsAlignment,
    )
    SettingsPicker.HIGHLIGHT -> PickerModel(
        stringResource(R.string.ui_settings_karaoke_progress),
        HighlightStyle.entries.map { PickerOption(it.name, highlightLabel(it)) },
        state.karaokeStyle.highlight.name,
    )
    SettingsPicker.INACTIVE -> PickerModel(
        stringResource(R.string.ui_pending_text),
        InactiveStyle.entries.map { PickerOption(it.name, inactiveLabel(it)) },
        state.karaokeStyle.inactive.name,
    )
    SettingsPicker.TRANSLATION_LANGUAGE -> PickerModel(
        stringResource(R.string.ui_translation_language),
        listOf(
            PickerOption("Español", stringResource(R.string.ui_spanish)),
            PickerOption("English", stringResource(R.string.ui_english)),
            PickerOption("Sistema", stringResource(R.string.ui_system)),
        ),
        state.translationLanguage,
    )
    SettingsPicker.APP_LANGUAGE -> PickerModel(
        stringResource(R.string.ui_settings_language),
        listOf(
            PickerOption(AppLanguageManager.TAG_SYSTEM, stringResource(R.string.ui_system)),
            PickerOption(AppLanguageManager.TAG_SPANISH, stringResource(R.string.ui_spanish)),
            PickerOption(AppLanguageManager.TAG_ENGLISH, stringResource(R.string.ui_english)),
        ),
        state.languageTag,
    )
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.1.sp,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun LyricsSizeSetting(
    state: SettingsUiState,
    onStateChange: (SettingsUiState) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        SettingSlider(
            label = stringResource(R.string.ux_lyrics_size_title),
            value = state.lyricsTextScale,
            valueRange = MIN_LYRICS_TEXT_SCALE..MAX_LYRICS_TEXT_SCALE,
            display = stringResource(lyricsTextScaleLabelResource(state.lyricsTextScale)),
            steps = 2,
            onValueChange = { value ->
                onStateChange(state.copy(lyricsTextScale = normalizeLyricsTextScale(value)))
            },
        )
        Text(
            text = stringResource(R.string.ux_lyrics_size_preview),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = scaledLyricsSp(
                    MaterialTheme.typography.titleLarge.fontSize.value,
                    state.lyricsTextScale,
                ).sp,
                lineHeight = scaledLyricsSp(
                    MaterialTheme.typography.titleLarge.lineHeight.value,
                    state.lyricsTextScale,
                ).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.ux_lyrics_size_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(display, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = "$label: $display" },
        )
    }
}

@Composable
private fun SelectionSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    SettingsRow(
        title = title,
        detail = detail,
        trailing = value,
        onClick = onClick,
        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
    )
}

@Composable
private fun ExpandableSettingRow(
    title: String,
    detail: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        detail = detail,
        trailing = stringResource(
            if (expanded) R.string.ux_settings_hide else R.string.ux_settings_show,
        ),
        onClick = onClick,
        trailingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
    )
}

@Composable
private fun ActionSettingRow(title: String, detail: String, onClick: () -> Unit) {
    SettingsRow(
        title = title,
        detail = detail,
        onClick = onClick,
        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
    )
}

@Composable
private fun ReadOnlySettingRow(title: String, value: String) {
    SettingsRow(title = title, trailing = value)
}

@Composable
private fun SettingsRow(
    title: String,
    detail: String? = null,
    trailing: String? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun SettingsSelectionDialog(
    title: String,
    options: List<PickerOption>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.id == selectedId,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.id) },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.id == selectedId, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ux_settings_cancel)) }
        },
    )
}

@Composable
private fun PermissionSettingRow(
    title: String,
    detail: String,
    status: AccessStatus,
    icon: ImageVector,
    onAction: () -> Unit,
) {
    val statusLabel = accessLabel(status)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (status) {
                AccessStatus.GRANTED -> Icons.Default.CheckCircle
                AccessStatus.DENIED -> Icons.Default.Warning
                else -> icon
            },
            contentDescription = null,
            tint = when (status) {
                AccessStatus.GRANTED -> MaterialTheme.colorScheme.primary
                AccessStatus.DENIED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (status == AccessStatus.DENIED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier.semantics {
                contentDescription = "$title, $statusLabel"
            },
        ) {
            Icon(
                imageVector = if (status == AccessStatus.GRANTED) Icons.Default.Info
                else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun ToggleSetting(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    status: AccessStatus? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp).size(24.dp),
            )
        }
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status != null && status != AccessStatus.GRANTED) {
                Text(
                    text = stringResource(R.string.ui_permission_required_format, accessLabel(status)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun ColorHexField(label: String, color: Long, onColorChange: (Long) -> Unit) {
    val canonical = remember(color) { color.toArgbHex() }
    var text by remember(color) { mutableStateOf(canonical) }
    val parsed = parseArgbHex(text)
    OutlinedTextField(
        value = text,
        onValueChange = { candidate ->
            text = candidate.take(9).uppercase()
            parseArgbHex(text)?.let(onColorChange)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = {
            Text(if (parsed == null) stringResource(R.string.ui_hex_hint) else stringResource(R.string.ui_valid_color))
        },
        isError = parsed == null,
        singleLine = true,
    )
}

private fun Long.toArgbHex(): String = "#%08X".format(this and 0xFFFFFFFFL)

private fun parseArgbHex(value: String): Long? {
    val digits = value.trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    val parsed = digits.toLongOrNull(16) ?: return null
    return if (digits.length == 6) parsed or 0xFF000000 else parsed
}

@Composable
private fun themeLabel(value: AppThemeMode): String = when (value) {
    AppThemeMode.SYSTEM -> stringResource(R.string.ui_system)
    AppThemeMode.LIGHT -> stringResource(R.string.ui_light)
    AppThemeMode.DARK -> stringResource(R.string.ui_dark)
}

@Composable
private fun motionLabel(value: MotionLevel): String = when (value) {
    MotionLevel.OFF -> stringResource(R.string.ui_motion_off)
    MotionLevel.REDUCED -> stringResource(R.string.ui_motion_reduced)
    MotionLevel.NORMAL -> stringResource(R.string.ui_motion_normal)
    MotionLevel.ENHANCED -> stringResource(R.string.ui_motion_enhanced)
}

@Composable
private fun lyricsDisplayLabel(value: LyricsDisplayMode): String = when (value) {
    LyricsDisplayMode.FULL_LYRICS -> stringResource(R.string.ui_full_lyrics)
    LyricsDisplayMode.CURRENT_WORD -> stringResource(R.string.ui_current_word)
}

@Composable
private fun alignmentLabel(value: String): String = when (value) {
    "AUTO" -> stringResource(R.string.ui_automatic)
    "START" -> stringResource(R.string.ui_start)
    "CENTER" -> stringResource(R.string.ui_center)
    "END" -> stringResource(R.string.ui_end)
    else -> value
}

@Composable
private fun highlightLabel(value: HighlightStyle): String = when (value) {
    HighlightStyle.SOLID -> stringResource(R.string.ui_highlight_solid)
    HighlightStyle.WHITE_GRADIENT -> stringResource(R.string.ui_highlight_white)
    HighlightStyle.ACCENT_GRADIENT -> stringResource(R.string.ui_highlight_accent)
    HighlightStyle.ARTWORK_GRADIENT -> stringResource(R.string.ui_highlight_artwork)
    HighlightStyle.CUSTOM_GRADIENT -> stringResource(R.string.ui_highlight_custom)
}

@Composable
private fun inactiveLabel(value: InactiveStyle): String = when (value) {
    InactiveStyle.SOLID -> stringResource(R.string.ui_highlight_solid)
    InactiveStyle.OPACITY -> stringResource(R.string.ui_inactive_opacity)
    InactiveStyle.CUSTOM -> stringResource(R.string.ui_highlight_custom)
}

@Composable
private fun appLanguageLabel(value: String): String = when (value) {
    AppLanguageManager.TAG_SPANISH -> stringResource(R.string.ui_spanish)
    AppLanguageManager.TAG_ENGLISH -> stringResource(R.string.ui_english)
    else -> stringResource(R.string.ui_system)
}

@Composable
private fun translationLanguageLabel(value: String): String = when (value) {
    "Español" -> stringResource(R.string.ui_spanish)
    "English" -> stringResource(R.string.ui_english)
    else -> stringResource(R.string.ui_system)
}

@Composable
private fun accessLabel(status: AccessStatus): String = when (status) {
    AccessStatus.NOT_REQUESTED -> stringResource(R.string.ui_access_pending)
    AccessStatus.GRANTED -> stringResource(R.string.ui_access_active)
    AccessStatus.DENIED -> stringResource(R.string.ui_access_denied)
    AccessStatus.UNAVAILABLE -> stringResource(R.string.ui_access_unavailable)
}

private fun lyricsTextScaleLabelResource(value: Float): Int = when {
    value < 0.875f -> R.string.ux_lyrics_size_small
    value < 1.125f -> R.string.ux_lyrics_size_default
    value < 1.375f -> R.string.ux_lyrics_size_large
    else -> R.string.ux_lyrics_size_extra_large
}

internal fun applicationVersionName(packageManager: PackageManager, packageName: String): String =
    runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull().orEmpty().ifBlank { "—" }
