package xyz.tosdev.yetasimplelyicsapp.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import xyz.tosdev.yetasimplelyicsapp.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigurationScreen(
    initialConfig: LyricsWidgetConfig,
    onSave: (LyricsWidgetConfig) -> Unit,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
    onBack: () -> Unit = {},
    snapshot: LyricsWidgetSnapshot = LyricsWidgetSnapshot(),
    pickedImagePath: String? = null,
    onChooseImage: () -> Unit = {},
) {
    var config by remember(initialConfig) { mutableStateOf(initialConfig) }
    LaunchedEffect(pickedImagePath) {
        if (pickedImagePath != null) config = config.copy(idleImageSource = WidgetIdleImageSource.CUSTOM, idleImagePath = pickedImagePath)
    }
    val spacing = SignalDesignSystem.spacing
    val motion = rememberSignalMotion(motionLevel)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SignalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.studio_back_2), onBack,
                    motionLevel = motionLevel)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.studio_yasla_widget), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.studio_make_it_yours), style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
        bottomBar = {
            Button(onClick = { onSave(config) }, shape = RoundedCornerShape(SignalDesignSystem.radii.medium),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.studio_save_widget), modifier = Modifier.padding(vertical = 5.dp))
            }
        },
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            stickyHeader {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 12.dp)) {
                    WidgetConfigurationPreview(config, snapshot)
                }
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_content), stringResource(R.string.studio_choose_how_much_context_to_show))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsWidgetContentMode.entries.forEach { mode ->
                        FilterChip(config.contentMode == mode, { config = config.copy(contentMode = mode) },
                            label = { Text(mode.displayName()) })
                    }
                }
                AnimatedContent(config.contentMode, transitionSpec = {
                    fadeIn(SignalMotionSpecs.fast(motion)) togetherWith fadeOut(SignalMotionSpecs.fast(motion))
                }, label = "widget mode description") { mode ->
                    Text(mode.description(), style = MaterialTheme.typography.bodySmall,
                        color = SignalDesignSystem.colors.textMuted)
                }
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_appearance), stringResource(R.string.studio_content_mode_and_layout_are_saved_separately))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsWidgetLayout.entries.forEach { layout ->
                        FilterChip(config.layout == layout, { config = config.copy(layout = layout) },
                            label = { Text(layout.displayName()) })
                    }
                }
                Text(config.layout.description(), style = MaterialTheme.typography.bodySmall,
                    color = SignalDesignSystem.colors.textMuted)
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_typography), stringResource(R.string.studio_complete_text_fitted_to_widget_space))
                WidgetEditorSlider(stringResource(R.string.studio_text_scale), "${"%.1f".format(config.textScale)}×",
                    config.textScale, 0.5f..2f, 14) { config = config.copy(textScale = it) }
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_color), stringResource(R.string.studio_choose_an_accent_for_your_composition))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.studio_default_coral to null, R.string.studio_green to "#37E789", R.string.studio_blue to "#63A7FF",
                        R.string.studio_purple to "#B89CFF", R.string.studio_white to "#FFFFFF").forEach { (label, value) ->
                        FilterChip(config.primaryColorHex == value, { config = config.copy(primaryColorHex = value) },
                            label = { Text(stringResource(label)) })
                    }
                }
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_background_and_shape), stringResource(R.string.studio_from_a_subtle_surface_to_full_transparency))
                WidgetEditorSlider(stringResource(R.string.studio_opacity), "${config.backgroundOpacityPercent}%",
                    config.backgroundOpacityPercent.toFloat(), 0f..100f, 19) {
                    config = config.copy(backgroundOpacityPercent = it.roundToInt())
                }
                WidgetEditorSlider(stringResource(R.string.studio_corners), "${config.cornerRadius} dp", config.cornerRadius.toFloat(),
                    0f..64f, 15) { config = config.copy(cornerRadius = it.roundToInt()) }
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_position), stringResource(R.string.studio_balance_content_within_the_widget))
                WidgetEditorSlider(stringResource(R.string.studio_vertical_position), "${(config.verticalBias * 100).roundToInt()}%",
                    config.verticalBias, 0f..1f, 9) { config = config.copy(verticalBias = it) }
                Text(stringResource(R.string.studio_current_word_stays_horizontally_and_vertically_centered),
                    style = MaterialTheme.typography.bodySmall, color = SignalDesignSystem.colors.textMuted)
            }
            item {
                WidgetEditorHeading(stringResource(R.string.widget_idle_image_title),
                    stringResource(R.string.widget_idle_image_detail))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChooseImage) { Text(stringResource(R.string.widget_choose_image)) }
                    TextButton(onClick = { config = config.copy(idleImageSource = WidgetIdleImageSource.YASLA_DEFAULT,
                        idleImagePath = null) }) { Text(stringResource(if (config.idleImagePath != null)
                            R.string.widget_remove_image else R.string.widget_default_image)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetIdleImageScale.entries.forEach { scale ->
                        FilterChip(config.idleImageScale == scale, { config = config.copy(idleImageScale = scale) },
                            label = { Text(stringResource(if (scale == WidgetIdleImageScale.COVER)
                                R.string.widget_image_cover else R.string.widget_image_fit)) })
                    }
                }
            }
            item {
                WidgetEditorHeading(stringResource(R.string.studio_behavior), stringResource(R.string.studio_choose_information_to_accompany_lyrics))
                SignalSurface(contentPadding = PaddingValues(16.dp)) {
                    Column {
                        WidgetEditorToggle(stringResource(R.string.studio_show_translation), config.showTranslation) {
                            config = config.copy(showTranslation = it)
                        }
                        WidgetEditorToggle(stringResource(R.string.studio_playback_controls), config.showPlaybackControls) {
                            config = config.copy(showPlaybackControls = it)
                        }
                        WidgetEditorToggle(stringResource(R.string.studio_use_title_as_fallback), config.useTitleAsFallback) {
                            config = config.copy(useTitleAsFallback = it)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.studio_preview_follows_playback_final_size_depends_on_your_home_screen_a),
                    style = MaterialTheme.typography.bodySmall, color = SignalDesignSystem.colors.textMuted)
            }
        }
    }
}

@Composable
private fun WidgetEditorHeading(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(detail, style = MaterialTheme.typography.bodySmall, color = SignalDesignSystem.colors.textMuted,
        modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
}

@Composable
private fun WidgetEditorSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value, onChange, valueRange = range, steps = steps,
        modifier = Modifier.semantics { contentDescription = label })
}

@Composable
private fun WidgetEditorToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
internal fun WidgetConfigurationPreview(config: LyricsWidgetConfig, snapshot: LyricsWidgetSnapshot) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val accent = config.primaryColorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.primary
    val image by produceState<android.graphics.Bitmap?>(null, config.idleImagePath, config.idleImageSource) {
        value = withContext(Dispatchers.IO) {
            if (config.idleImageSource == WidgetIdleImageSource.CUSTOM) WidgetIdleImageStore(context).load(config.idleImagePath)
            else null
        }
    }
    SignalSurface(contentPadding = PaddingValues(12.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.studio_preview), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(if (snapshot.hasTrack) R.string.widget_preview_live else R.string.widget_preview_idle),
                    style = MaterialTheme.typography.labelSmall, color = SignalDesignSystem.colors.textMuted)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(142.dp * density.fontScale.coerceAtLeast(1f)).padding(top = 8.dp)) {
                val policy = config.renderPolicy(maxWidth.value, maxHeight.value)
                val renderConfig = config.copy(layout = policy.layout, showTranslation = policy.showTranslation,
                    showPlaybackControls = policy.showPlaybackControls)
                val resolved = renderConfig.resolveContent(snapshot, policy.sizeClass)
                val word = resolved == ResolvedWidgetContent.CURRENT_WORD
                val transparent = word || resolved == ResolvedWidgetContent.DESKTOP ||
                    renderConfig.layout == LyricsWidgetLayout.TRANSPARENT ||
                    renderConfig.layout == LyricsWidgetLayout.WORD_FOCUS ||
                    snapshot.resolvedPresentationState == WidgetPresentationState.NO_PLAYBACK
                val padding = if (word || resolved == ResolvedWidgetContent.DESKTOP) 4.dp else 12.dp
                val width = (maxWidth - padding * 2).value.coerceAtLeast(1f)
                val height = (maxHeight - padding * 2).value.coerceAtLeast(1f)
                val wordColor = config.primaryColorHex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                    ?: colorResource(R.color.widget_primary_text).toArgb()
                val renderedWord = remember(snapshot.activeWord, snapshot.lyricsLanguageTag, config.textScale,
                    width, height, density.fontScale, wordColor, word, snapshot.resolvedPresentationState) {
                    if (word && snapshot.resolvedPresentationState == WidgetPresentationState.LYRICS_AVAILABLE) renderWidgetWord(context, snapshot.activeWord, snapshot.lyricsLanguageTag, width, height,
                        config.textScale, wordColor) else null
                }
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(config.cornerRadius.dp)).background(
                    if (transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                        alpha = config.backgroundOpacityPercent / 100f)).padding(padding),
                    contentAlignment = if (word) Alignment.Center else BiasAlignment(0f, config.verticalBias * 2f - 1f)) {
                    when (snapshot.resolvedPresentationState) {
                        WidgetPresentationState.NO_PLAYBACK -> {
                            val contentScale = if (config.idleImageScale == WidgetIdleImageScale.COVER) ContentScale.Crop else ContentScale.Fit
                            if (image != null) Image(checkNotNull(image).asImageBitmap(),
                                stringResource(R.string.widget_idle_image_description), Modifier.fillMaxSize(), contentScale = contentScale)
                            else Image(painterResource(R.drawable.widget_idle_default),
                                stringResource(R.string.widget_idle_image_description), Modifier.fillMaxSize(), contentScale = contentScale)
                        }
                        WidgetPresentationState.SEARCHING_LYRICS -> Text(stringResource(R.string.widget_state_searching))
                        WidgetPresentationState.LYRICS_UNAVAILABLE -> Text(stringResource(R.string.widget_state_unavailable))
                        WidgetPresentationState.ERROR -> Text(stringResource(R.string.widget_state_error))
                        WidgetPresentationState.LYRICS_AVAILABLE -> if (word) Image(checkNotNull(renderedWord).bitmap.asImageBitmap(),
                            renderedWord.textLayout.text.replace("\n", ""), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (resolved == ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT && snapshot.previousLine.isNotBlank())
                                Text(snapshot.previousLine, style = MaterialTheme.typography.bodySmall, color = SignalDesignSystem.colors.textMuted)
                            Text(snapshot.activeLine.ifBlank { if (config.useTitleAsFallback) snapshot.fallbackLabel() else "" },
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = (18f * config.textScale).coerceAtMost(26f).sp,
                                    lineHeight = (22f * config.textScale).coerceAtMost(30f).sp), color = accent, textAlign = TextAlign.Center)
                            if (resolved != ResolvedWidgetContent.CURRENT_LINE && resolved != ResolvedWidgetContent.KARAOKE && snapshot.nextLine.isNotBlank())
                                Text(snapshot.nextLine, style = MaterialTheme.typography.bodySmall, color = SignalDesignSystem.colors.textMuted)
                            if (renderConfig.showTranslation && snapshot.translation.isNotBlank()) Text(snapshot.translation,
                                style = MaterialTheme.typography.labelSmall, color = SignalDesignSystem.colors.textMuted)
                            if (renderConfig.showPlaybackControls) Row(horizontalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.padding(top = 8.dp)) {
                                listOf(Icons.Rounded.SkipPrevious, if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    Icons.Rounded.SkipNext).forEach {
                                    Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
