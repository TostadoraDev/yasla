package xyz.tosdev.yetasimplelyicsapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import xyz.tosdev.yetasimplelyicsapp.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.ui.AccessStatus
import xyz.tosdev.yetasimplelyicsapp.ui.AppDestination
import xyz.tosdev.yetasimplelyicsapp.ui.EditorUiState
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeWordUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppActions
import xyz.tosdev.yetasimplelyicsapp.ui.OverlayUiState
import xyz.tosdev.yetasimplelyicsapp.ui.WidgetStyle
import xyz.tosdev.yetasimplelyicsapp.ui.WidgetUiState
import xyz.tosdev.yetasimplelyicsapp.ui.components.KaraokeLine
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalBadge
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalDesignSystem
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    onDestination: (AppDestination) -> Unit,
    actions: LyricsAppActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeading(
            eyebrow = stringResource(R.string.studio_create_and_display),
            title = stringResource(R.string.studio_studio),
            description = stringResource(R.string.studio_edit_timing_and_prepare_surfaces_using_the_shared_lyrics_engine),
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = actions.onImportLyrics) {
                Text(stringResource(R.string.studio_import_ttml_lrc))
            }
            TextButton(onClick = actions.onExportLyrics) {
                Text(stringResource(R.string.studio_export_lrc))
            }
            TextButton(onClick = actions.onExportTtml) {
                Text(stringResource(R.string.studio_export_ttml))
            }
        }
        Spacer(Modifier.height(18.dp))
        StudioCard(
            marker = "01",
            title = stringResource(R.string.studio_lyrics_editor),
            detail = stringResource(R.string.studio_text_timing_translations_and_global_offset),
            action = stringResource(R.string.studio_open_editor),
            onClick = { onDestination(AppDestination.EDITOR) },
        )
        Spacer(Modifier.height(12.dp))
        StudioCard(
            marker = "02",
            title = stringResource(R.string.studio_floating_lyrics),
            detail = stringResource(R.string.studio_preview_size_alignment_opacity_and_translations),
            action = stringResource(R.string.studio_configure_overlay),
            onClick = { onDestination(AppDestination.OVERLAY) },
        )
        Spacer(Modifier.height(12.dp))
        StudioCard(
            marker = "03",
            title = stringResource(R.string.studio_widget),
            detail = stringResource(R.string.studio_design_a_passive_view_for_your_home_screen),
            action = stringResource(R.string.studio_configure_widget),
            onClick = { onDestination(AppDestination.WIDGET) },
        )
    }
}

@Composable
private fun StudioCard(
    marker: String,
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    Column {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = action },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 22.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(
                    imageVector = when (marker) {
                        "01" -> Icons.Outlined.EditNote
                        "02" -> Icons.Outlined.Layers
                        else -> Icons.Outlined.Widgets
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SignalBadge(marker)
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun EditorScreen(
    state: EditorUiState,
    onBack: () -> Unit,
    onSave: (EditorUiState) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val offsetDescription = stringResource(R.string.studio_global_lyrics_timing_offset)
    var working by remember(state) { mutableStateOf(state) }
    val previewLines = remember(working.rawLyrics) {
        working.rawLyrics
            .lineSequence()
            .filter { it.isNotBlank() }
            .take(4)
            .mapIndexed { index, line ->
                LyricLineUi(
                    id = "editor-" + index,
                    words = line.trim().split(Regex("\\s+")).mapIndexed { wordIndex, word ->
                        KaraokeWordUi(
                            id = "editor-" + index + "-" + wordIndex,
                            text = word,
                            progress = if (index == 0) {
                                ((wordIndex + 1f) / maxOf(1, line.split(Regex("\\s+")).size))
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                        )
                    },
                    isActive = index == 0,
                )
            }
            .toList()
    }
    val visiblePreview = if (previewLines.isEmpty()) editorPlaceholderPreview() else previewLines

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 36.dp),
    ) {
        SubpageBar(
            label = stringResource(R.string.studio_editor),
            onBack = onBack,
            action = stringResource(R.string.studio_save),
            actionEnabled = working.unsavedChanges,
            onAction = {
                onSave(working.copy(unsavedChanges = false))
                working = working.copy(unsavedChanges = false)
            },
        )
        ScreenHeading(
            eyebrow = stringResource(R.string.studio_local_lyrics),
            title = working.title,
            description = stringResource(R.string.studio_paste_text_to_create_a_draft_timing_connects_to_the_synchronizati),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onImport) { Text(stringResource(R.string.studio_import_lrc_ttml)) }
            TextButton(
                enabled = working.rawLyrics.isNotBlank(),
                onClick = onExport,
            ) {
                Text(stringResource(R.string.studio_export))
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = working.title,
            onValueChange = {
                working = working.copy(title = it, unsavedChanges = true)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.studio_title)) },
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = working.artist,
            onValueChange = {
                working = working.copy(artist = it, unsavedChanges = true)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.studio_artist)) },
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = working.rawLyrics,
            onValueChange = {
                working = working.copy(rawLyrics = it, unsavedChanges = true)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            label = { Text(stringResource(R.string.studio_lyrics_one_verse_per_line)) },
            placeholder = { Text(stringResource(R.string.studio_write_or_paste_lyrics_here)) },
            minLines = 8,
        )
        Spacer(Modifier.height(16.dp))
        SignalSurface(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.studio_editor_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (previewLines.isEmpty()) stringResource(R.string.studio_demo_content) else stringResource(R.string.studio_local_draft),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(18.dp))
                visiblePreview.forEach { line ->
                    KaraokeLine(
                        line = line,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.studio_global_offset, working.offsetMs),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = working.offsetMs.toFloat(),
            onValueChange = {
                working = working.copy(offsetMs = it.toLong(), unsavedChanges = true)
            },
            valueRange = -5_000f..5_000f,
            steps = 39,
            modifier = Modifier.semantics {
                contentDescription = offsetDescription
            },
        )
    }
}

@Composable
fun OverlayConfiguratorScreen(
    state: OverlayUiState,
    accessStatus: AccessStatus,
    onRequestAccess: () -> Unit,
    onBack: () -> Unit,
    onStateChange: (OverlayUiState) -> Unit,
) {
    var working by remember(state) { mutableStateOf(state) }

    ConfiguratorLayout(
        eyebrow = stringResource(R.string.studio_overlay),
        title = stringResource(R.string.studio_floating_lyrics),
        description = stringResource(R.string.studio_the_preview_shows_appearance_actual_availability_depends_on_overl),
        onBack = onBack,
        preview = {
            OverlayPreview(working)
        },
    ) {
        AccessBanner(
            status = accessStatus,
            title = stringResource(R.string.studio_overlay_permission),
            action = stringResource(R.string.studio_configure_permission),
            onAction = onRequestAccess,
        )
        ToggleControl(
            title = stringResource(R.string.studio_enable_overlay),
            detail = stringResource(R.string.studio_system_permission_is_required_to_display_over_other_apps),
            checked = working.enabled,
            onChange = {
                working = working.copy(enabled = it)
                onStateChange(working)
            },
        )
        FloatSlider(
            label = stringResource(R.string.studio_opacity),
            value = working.opacity,
            range = 0.45f..1f,
            display = (working.opacity * 100).toInt().toString() + "%",
            onChange = {
                working = working.copy(opacity = it)
                onStateChange(working)
            },
        )
        FloatSlider(
            label = stringResource(R.string.studio_scale),
            value = working.scale,
            range = 0.75f..1.4f,
            display = String.format("%.2f×", working.scale),
            onChange = {
                working = working.copy(scale = it)
                onStateChange(working)
            },
        )
        Text(stringResource(R.string.studio_display_mode), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(
            choices = listOf("COMPACT", "EXPANDED"),
            selected = working.mode,
            label = { if (it == "COMPACT") stringResource(R.string.studio_compact) else stringResource(R.string.studio_expanded) },
            onSelect = {
                working = working.copy(mode = it)
                onStateChange(working)
            }
        )
        Text(stringResource(R.string.studio_floating_lyrics_content), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(
            choices = listOf(
                "CURRENT_LINE",
                "CURRENT_AND_NEXT",
                "PREVIOUS_CURRENT_NEXT",
                "CURRENT_WORD",
                "KARAOKE",
                "DESKTOP_LYRICS",
            ),
            selected = working.layout,
            label = {
                when (it) {
                    "CURRENT_LINE" -> stringResource(R.string.studio_current_line)
                    "CURRENT_AND_NEXT" -> stringResource(R.string.studio_current_next)
                    "PREVIOUS_CURRENT_NEXT" -> stringResource(R.string.studio_previous_current_next)
                    "CURRENT_WORD" -> stringResource(R.string.studio_current_word)
                    "KARAOKE" -> stringResource(R.string.studio_karaoke)
                    "DESKTOP_LYRICS" -> stringResource(R.string.studio_desktop_lyrics)
                    else -> it
                }
            },
            onSelect = {
                working = if (it == "CURRENT_WORD") {
                    working.copy(
                        layout = it,
                        currentWordAlignment = "CENTER",
                        background = "TRANSPARENT",
                        showBorder = false,
                        softShadow = false,
                    )
                } else {
                    working.copy(layout = it)
                }
                onStateChange(working)
            },
        )
        Text(stringResource(R.string.studio_content_alignment), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(
            choices = listOf("START", "CENTER", "END"),
            selected = working.alignment,
            label = {
                when (it) {
                    "START" -> stringResource(R.string.studio_start)
                    "CENTER" -> stringResource(R.string.studio_center)
                    "END" -> stringResource(R.string.studio_end)
                    else -> it
                }
            },
            onSelect = {
                working = working.copy(alignment = it)
                onStateChange(working)
            }
        )
        Text(stringResource(R.string.studio_visual_style), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(
            choices = listOf(
                "MINIMAL",
                "DESKTOP",
                "APPLE_INSPIRED",
                "KARAOKE",
                "DUET",
                "TRANSPARENT",
                "GLASS",
                "HIGH_CONTRAST",
            ),
            selected = working.preset,
            label = {
                when (it) {
                    "MINIMAL" -> stringResource(R.string.studio_minimal)
                    "DESKTOP" -> stringResource(R.string.studio_desktop)
                    "APPLE_INSPIRED" -> stringResource(R.string.studio_apple)
                    "KARAOKE" -> stringResource(R.string.studio_karaoke)
                    "DUET" -> stringResource(R.string.studio_duet)
                    "TRANSPARENT" -> stringResource(R.string.studio_transparent)
                    "GLASS" -> stringResource(R.string.studio_glass)
                    "HIGH_CONTRAST" -> stringResource(R.string.studio_contrast)
                    else -> it
                }
            },
            onSelect = {
                working = working.copy(preset = it)
                onStateChange(working)
            }
        )
        Text(stringResource(R.string.studio_background), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(
            choices = listOf(
                "TEXT_ONLY",
                "FROSTED",
                "TRANSPARENT",
                "SUBTLE_DARK",
                "ARTWORK",
                "PILL",
                "CARD",
            ),
            selected = working.background,
            label = {
                when (it) {
                    "TEXT_ONLY" -> stringResource(R.string.studio_text_only)
                    "FROSTED" -> stringResource(R.string.studio_frosted)
                    "TRANSPARENT" -> stringResource(R.string.studio_transparent)
                    "SUBTLE_DARK" -> stringResource(R.string.studio_subtle_dark)
                    "ARTWORK" -> stringResource(R.string.studio_artwork)
                    "PILL" -> stringResource(R.string.studio_pill)
                    "CARD" -> stringResource(R.string.studio_card)
                    else -> it
                }
            },
            onSelect = {
                working = working.copy(background = it)
                onStateChange(working)
            },
        )
        FloatSlider(
            label = stringResource(R.string.studio_corner_radius),
            value = working.cornerRadius,
            range = 0f..36f,
            display = working.cornerRadius.toInt().toString() + " dp",
            onChange = {
                working = working.copy(cornerRadius = it)
                onStateChange(working)
            },
        )
        FloatSlider(
            label = stringResource(R.string.studio_inner_spacing),
            value = working.paddingDp,
            range = 8f..28f,
            display = working.paddingDp.toInt().toString() + " dp",
            onChange = {
                working = working.copy(paddingDp = it)
                onStateChange(working)
            },
        )
        ToggleControl(
            title = stringResource(R.string.studio_subtle_border),
            detail = stringResource(R.string.studio_define_the_overlay_against_low_contrast_backgrounds),
            checked = working.showBorder,
            onChange = {
                working = working.copy(showBorder = it)
                onStateChange(working)
            },
        )
        ToggleControl(
            title = stringResource(R.string.studio_soft_shadow),
            detail = stringResource(R.string.studio_separate_text_from_content_underneath),
            checked = working.softShadow,
            onChange = {
                working = working.copy(softShadow = it)
                onStateChange(working)
            },
        )
        ToggleControl(
            title = stringResource(R.string.studio_pass_touches_through),
            detail = stringResource(R.string.studio_interact_with_content_underneath_the_overlay),
            checked = working.clickThrough,
            onChange = {
                working = working.copy(clickThrough = it)
                onStateChange(working)
            },
        )
        ToggleControl(
            title = stringResource(R.string.studio_hide_when_paused),
            detail = stringResource(R.string.studio_hide_automatically_when_playback_pauses),
            checked = working.hideWhenPaused,
            onChange = {
                working = working.copy(hideWhenPaused = it)
                onStateChange(working)
            },
        )
        Text(stringResource(R.string.studio_floating_lyrics_on_lock_screen), style = MaterialTheme.typography.titleMedium)
        ChoiceChips(
            choices = listOf("OFF", "MINIMAL", "ALWAYS"),
            selected = working.lockScreenMode,
            label = {
                when (it) {
                    "OFF" -> stringResource(R.string.studio_off)
                    "MINIMAL" -> stringResource(R.string.studio_minimal)
                    "ALWAYS" -> stringResource(R.string.studio_always)
                    else -> it
                }
            },
            onSelect = {
                working = working.copy(lockScreenMode = it)
                onStateChange(working)
            },
        )
        Text(
            stringResource(R.string.studio_minimal_limits_the_overlay_to_one_line_it_can_still_overlap_notif),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleControl(
            title = stringResource(R.string.studio_use_notification_on_lock_screen),
            detail = stringResource(R.string.studio_show_current_and_next_lyrics_in_system_ui_without_guessing_notifi),
            checked = working.useLockScreenNotification,
            onChange = {
                working = working.copy(useLockScreenNotification = it)
                onStateChange(working)
            },
        )
        ToggleControl(
            title = stringResource(R.string.studio_show_translation),
            detail = stringResource(R.string.studio_show_below_the_main_line_when_the_document_includes_it),
            checked = working.showTranslation,
            onChange = {
                working = working.copy(showTranslation = it)
                onStateChange(working)
            },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                working = working.copy(alignment = "CENTER")
                onStateChange(working)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.studio_reset_to_screen_center))
        }
    }
}

@Composable
fun WidgetConfiguratorScreen(
    state: WidgetUiState,
    pickerAvailable: Boolean,
    onOpenWidgetPicker: () -> Unit,
    onBack: () -> Unit,
    onStateChange: (WidgetUiState) -> Unit,
    snapshot: xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot = xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot(),
    artworkUri: String? = null,
) {
    var working by remember(state) { mutableStateOf(state) }

    ConfiguratorLayout(
        eyebrow = stringResource(R.string.studio_widget_2),
        title = stringResource(R.string.studio_home_screen),
        description = stringResource(R.string.studio_customize_content_and_style_for_your_home_screen),
        onBack = onBack,
        controlsGlass = false,
        preview = {
            WidgetPreview(working, snapshot, artworkUri)
        },
    ) {
        Button(
            enabled = pickerAvailable,
            onClick = onOpenWidgetPicker,
        ) {
            Text(if (pickerAvailable) stringResource(R.string.studio_add_to_home_screen) else stringResource(R.string.studio_picker_unavailable))
        }
        StudioControlHeading(stringResource(R.string.studio_layout), stringResource(R.string.studio_choose_what_to_highlight))
        ChoiceChips(
            choices = WidgetStyle.entries,
            selected = working.style,
            label = {
                when (it) {
                    WidgetStyle.COMPACT -> stringResource(R.string.studio_compact)
                    WidgetStyle.LYRICS -> stringResource(R.string.studio_lyrics)
                    WidgetStyle.ARTWORK -> stringResource(R.string.studio_artwork)
                }
            },
            onSelect = {
                working = working.copy(style = it)
                onStateChange(working)
            },
        )
        StudioControlHeading(stringResource(R.string.studio_content), stringResource(R.string.studio_show_useful_content_that_fits_the_available_space))
        ToggleControl(
            title = stringResource(R.string.studio_show_artwork),
            detail = stringResource(R.string.studio_hide_for_a_more_typographic_composition),
            checked = working.showArtwork,
            onChange = {
                working = working.copy(showArtwork = it)
                onStateChange(working)
            },
        )
        ToggleControl(
            title = stringResource(R.string.studio_show_translation),
            detail = stringResource(R.string.studio_use_a_second_line_only_when_it_fits),
            checked = working.showTranslation,
            onChange = {
                working = working.copy(showTranslation = it)
                onStateChange(working)
            },
        )
        StudioControlHeading(stringResource(R.string.studio_background), stringResource(R.string.studio_adjust_the_shape_while_preserving_widget_behavior))
        FloatSlider(
            label = stringResource(R.string.studio_corner_radius),
            value = working.cornerRadius,
            range = 0f..36f,
            display = working.cornerRadius.toInt().toString() + " dp",
            onChange = {
                working = working.copy(cornerRadius = it)
                onStateChange(working)
            },
        )
    }
}

@Composable
private fun AccessBanner(
    status: AccessStatus,
    title: String,
    action: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (status == AccessStatus.GRANTED) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (status) {
                        AccessStatus.NOT_REQUESTED -> stringResource(R.string.studio_not_requested_yet)
                        AccessStatus.GRANTED -> stringResource(R.string.studio_access_granted)
                        AccessStatus.DENIED -> stringResource(R.string.studio_access_denied)
                        AccessStatus.UNAVAILABLE -> stringResource(R.string.studio_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                enabled = status != AccessStatus.UNAVAILABLE,
                onClick = onAction,
            ) {
                Text(if (status == AccessStatus.GRANTED) stringResource(R.string.studio_review) else action)
            }
        }
    }
}

@Composable
private fun ConfiguratorLayout(
    eyebrow: String,
    title: String,
    description: String,
    onBack: () -> Unit,
    controlsGlass: Boolean = true,
    preview: @Composable () -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 36.dp),
    ) {
        SubpageBar(eyebrow, onBack)
        ScreenHeading(eyebrow, title, description)
        Spacer(Modifier.height(20.dp))
        preview()
        Spacer(Modifier.height(20.dp))
        SignalSurface(
            glass = controlsGlass,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                controls()
            }
        }
    }
}

@Composable
private fun OverlayPreview(state: OverlayUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF001515), Color(0xFF0C0C0C), Color(0xFF1A001A)),
                ),
                RoundedCornerShape(32.dp),
            )
            .padding(24.dp),
        contentAlignment = when (state.alignment) {
            "START" -> Alignment.CenterStart
            "END" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
    ) {
        val (text, accent) = when (state.preset) {
            "HIGH_CONTRAST" -> Color.White to Color.Yellow
            "APPLE_INSPIRED" -> Color.White to Color(0xFFA8C7FA)
            "DUET" -> Color.White to Color(0xFFFFA8D8)
            else -> Color.White to MaterialTheme.colorScheme.primary
        }
        val effectiveBackground = if (state.layout == "CURRENT_WORD") {
            "TRANSPARENT"
        } else {
            state.background
        }
        val bg = when (effectiveBackground) {
            "TEXT_ONLY", "TRANSPARENT" -> Color.Transparent
            "FROSTED" -> Color.White.copy(alpha = 0.13f)
            "ARTWORK" -> Color(0xFF43325E).copy(alpha = 0.82f)
            "PILL" -> Color.Black.copy(alpha = 0.72f)
            "CARD" -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            else -> Color.Black.copy(alpha = 0.64f)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                },
            shape = RoundedCornerShape(state.cornerRadius.dp),
            color = bg.copy(alpha = bg.alpha * state.opacity),
            border = if (
                state.layout != "CURRENT_WORD" &&
                (state.showBorder || effectiveBackground == "FROSTED")
            ) {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            } else {
                null
            },
            shadowElevation = if (
                state.layout != "CURRENT_WORD" &&
                state.softShadow && bg != Color.Transparent
            ) {
                10.dp
            } else {
                0.dp
            },
        ) {
            Column(
                modifier = Modifier.padding(
                    if (effectiveBackground == "TEXT_ONLY" || effectiveBackground == "TRANSPARENT") {
                        0.dp
                    } else {
                        state.paddingDp.dp
                    },
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.layout == "DESKTOP_LYRICS") {
                    Text(
                        stringResource(R.string.studio_metadata_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        letterSpacing = 2.sp
                    )
                }
                val previewAlignment = when (state.alignment) {
                    "START" -> TextAlign.Start
                    "END" -> TextAlign.End
                    else -> TextAlign.Center
                }
                when (state.layout) {
                    "CURRENT_WORD" -> Text(
                        "DEJAS",
                        style = MaterialTheme.typography.displaySmall,
                        color = accent,
                        textAlign = when (state.currentWordAlignment) {
                            "LEFT" -> TextAlign.Start
                            "RIGHT" -> TextAlign.End
                            else -> previewAlignment
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    "PREVIOUS_CURRENT_NEXT" -> {
                        PreviewLyricLine("Ven, acércate", text.copy(alpha = 0.5f), previewAlignment)
                        PreviewLyricLine(
                            "Yeah, si tú lo dejas...",
                            text,
                            previewAlignment,
                            active = true
                        )
                        PreviewLyricLine(
                            "Todo puede cambiar",
                            text.copy(alpha = 0.5f),
                            previewAlignment
                        )
                    }

                    "CURRENT_AND_NEXT", "DESKTOP_LYRICS" -> {
                        PreviewLyricLine(
                            "Yeah, si tú lo dejas...",
                            text,
                            previewAlignment,
                            active = true
                        )
                        PreviewLyricLine(
                            "Todo puede cambiar",
                            text.copy(alpha = 0.5f),
                            previewAlignment
                        )
                    }

                    "KARAOKE" -> PreviewLyricLine(
                        "Yeah, si tú lo dejas...",
                        accent,
                        previewAlignment,
                        active = true,
                    )

                    else -> PreviewLyricLine(
                        "Yeah, si tú lo dejas...",
                        text,
                        previewAlignment,
                        active = true,
                    )
                }
                if (state.showTranslation) {
                    Text(
                        "Yeah, if you let it...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = text.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewLyricLine(
    text: String,
    color: Color,
    textAlign: TextAlign,
    active: Boolean = false,
) {
    Text(
        text = text,
        style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WidgetPreview(state: WidgetUiState, snapshot: xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot,
    artworkUri: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.showArtwork && snapshot.hasTrack && state.style != WidgetStyle.LYRICS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                xyz.tosdev.yetasimplelyicsapp.ui.components.SongArtwork(artworkUri = artworkUri, title = snapshot.title,
                    modifier = Modifier.size(if (state.style == WidgetStyle.ARTWORK) 96.dp else 64.dp))
                Column {
                    Text(snapshot.title, style = MaterialTheme.typography.titleMedium)
                    Text(snapshot.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        xyz.tosdev.yetasimplelyicsapp.widget.WidgetConfigurationPreview(
            config = xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetConfig(
                layout = if (state.style == WidgetStyle.COMPACT) xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetLayout.COMPACT_PLAYER
                    else xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetLayout.TWO_LINES,
                showTranslation = state.showTranslation, cornerRadius = state.cornerRadius.toInt().coerceIn(0,64)),
            snapshot = snapshot,
        )
    }
}

@Composable
private fun SubpageBar(
    label: String,
    onBack: () -> Unit,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.studio_back))
        }
        Spacer(Modifier.weight(1f))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.weight(1f))
            Button(
                enabled = actionEnabled,
                onClick = onAction,
            ) {
                Text(action)
            }
        }
    }
}

@Composable
private fun ToggleControl(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StudioControlHeading(title: String, detail: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun <T> ChoiceChips(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                label = { Text(label(choice)) },
            )
        }
    }
}

@Composable
private fun editorPlaceholderPreview(): List<LyricLineUi> = listOf(
    stringResource(R.string.studio_demo_karaoke), stringResource(R.string.studio_demo_your_text),
).mapIndexed { lineIndex, text ->
    LyricLineUi(id = "placeholder-$lineIndex", words = text.split(' ').mapIndexed { wordIndex, word ->
        KaraokeWordUi("placeholder-$lineIndex-$wordIndex", word,
            if (lineIndex == 0) (1f - wordIndex * 0.25f).coerceAtLeast(0f) else 0f)
    }, isActive = lineIndex == 0)
}
