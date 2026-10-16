package xyz.tosdev.yetasimplelyicsapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.os.Bundle
import android.annotation.SuppressLint
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import xyz.tosdev.yetasimplelyicsapp.LyricsApplication
import xyz.tosdev.yetasimplelyicsapp.MainActivity
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import java.util.Locale

internal val CURRENT_WORD_BLOCK_ALIGNMENT: Alignment = Alignment.Center
internal val CURRENT_WORD_TEXT_ALIGNMENT: TextAlign = TextAlign.Center

class LyricsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent {
            val state = LyricsWidgetState.fromPreferences(currentState<Preferences>())
            LyricsWidgetContent(
                context = AppLanguageManager.localizedContext(context),
                config = state.config,
                snapshot = state.snapshot,
            )
        }
    }

    override suspend fun onDelete(context: Context, glanceId: androidx.glance.GlanceId) {
        WidgetIdleImageStore(context).delete(LyricsWidgetState.read(context, glanceId).config.idleImagePath)
        LyricsWidgetState.clear(context, glanceId)
    }
}

class LyricsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LyricsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ensurePlaybackPipeline(context)
    }

    @SuppressLint("MissingSuperCall") // Publish directly; super enqueues a second Glance worker.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        ensurePlaybackPipeline(context)
        requestRefresh(context, appWidgetIds.toSet())
    }

    @SuppressLint("MissingSuperCall")
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        ensurePlaybackPipeline(context)
        requestRefresh(context, setOf(appWidgetId))
    }

    private fun requestRefresh(context: Context, appWidgetIds: Set<Int>) {
        (context.applicationContext as? LyricsApplication)?.container?.requestWidgetRefresh(appWidgetIds)
    }

    private fun ensurePlaybackPipeline(context: Context) {
        val application = context.applicationContext as? LyricsApplication ?: return
        application.container.start()
        application.container.media.startListening()
        application.container.media.traceLiveness("widget event")
    }
}

@Composable
private fun LyricsWidgetContent(
    context: Context,
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
) {
    val hostSize = LocalSize.current
    val systemFontScale = context.resources.configuration.fontScale.coerceAtLeast(0.5f)
    val policy = config.renderPolicy(hostSize.width.value, hostSize.height.value)
    val renderConfig = config.copy(
        layout = policy.layout,
        showTranslation = policy.showTranslation,
        showPlaybackControls = policy.showPlaybackControls,
    )
    val resolvedContent = renderConfig.resolveContent(snapshot, policy.sizeClass)
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val basePadding = when {
        snapshot.resolvedPresentationState == WidgetPresentationState.NO_PLAYBACK -> 0.dp
        resolvedContent == ResolvedWidgetContent.CURRENT_WORD -> 4.dp
        resolvedContent == ResolvedWidgetContent.DESKTOP -> 4.dp
        policy.sizeClass == WidgetSizeClass.COMPACT -> 8.dp
        else -> 12.dp
    }
    val paddingDp = basePadding.value
    val availableWidthDp = (hostSize.width.value - paddingDp * 2f).coerceAtLeast(1f)
    val availableHeightDp = (hostSize.height.value - paddingDp * 2f).coerceAtLeast(1f)
    val transparent = resolvedContent == ResolvedWidgetContent.CURRENT_WORD ||
        resolvedContent == ResolvedWidgetContent.DESKTOP ||
        renderConfig.layout == LyricsWidgetLayout.TRANSPARENT ||
        renderConfig.layout == LyricsWidgetLayout.WORD_FOCUS ||
        snapshot.resolvedPresentationState == WidgetPresentationState.NO_PLAYBACK

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                if (transparent) Color.Transparent else configuredBackground(renderConfig),
            )
            .cornerRadius(renderConfig.cornerRadius.dp)
            .clickable(openApp)
            .padding(
                start = basePadding,
                top = basePadding,
                end = basePadding,
                bottom = basePadding,
            ),
        contentAlignment = contentAlignment(renderConfig, resolvedContent),
    ) {
        when (snapshot.resolvedPresentationState) {
            WidgetPresentationState.NO_PLAYBACK -> IdleImageWidget(context, renderConfig)
            WidgetPresentationState.SEARCHING_LYRICS -> WidgetStatus(
                context.getString(R.string.widget_state_searching),
                renderConfig,
            )
            WidgetPresentationState.LYRICS_UNAVAILABLE -> WidgetStatus(
                context.getString(R.string.widget_state_unavailable),
                renderConfig,
            )
            WidgetPresentationState.ERROR -> WidgetStatus(
                context.getString(R.string.widget_state_error),
                renderConfig,
            )
            WidgetPresentationState.LYRICS_AVAILABLE -> LyricsAvailableContent(
                context = context,
                config = renderConfig,
                snapshot = snapshot,
                resolvedContent = resolvedContent,
                sizeClass = policy.sizeClass,
                fontScale = systemFontScale,
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            )
        }
    }
}

@Composable
private fun LyricsAvailableContent(
    context: Context,
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    resolvedContent: ResolvedWidgetContent,
    sizeClass: WidgetSizeClass,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    when (resolvedContent) {
            ResolvedWidgetContent.CURRENT_WORD -> if (snapshot.hasTrack) CurrentWordWidget(
                context = context,
                config = config,
                snapshot = snapshot,
                locale = context.resources.configuration.locales[0],
                fontScale = fontScale,
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            ) else IdleImageWidget(context, config)

            ResolvedWidgetContent.CURRENT_LINE -> if (!snapshot.hasTrack) {
                IdleImageWidget(context, config)
            } else when (config.layout) {
                LyricsWidgetLayout.MINIMAL -> MinimalWidget(
                    config, snapshot, context.resources.configuration.locales[0],
                    fontScale,
                    availableWidthDp, availableHeightDp,
                )
                LyricsWidgetLayout.COMPACT_PLAYER -> CompactPlayerWidget(
                    config, snapshot, context.resources.configuration.locales[0],
                    fontScale,
                    availableWidthDp, availableHeightDp,
                )
                else -> CurrentLineWidget(
                    config = config,
                    snapshot = snapshot,
                    locale = context.resources.configuration.locales[0],
                    fontScale = fontScale,
                    availableWidthDp = availableWidthDp,
                    availableHeightDp = availableHeightDp,
                )
            }

            ResolvedWidgetContent.CURRENT_NEXT -> if (snapshot.hasTrack) TwoLinesWidget(
                config, snapshot, context.resources.configuration.locales[0],
                fontScale,
                availableWidthDp, availableHeightDp,
            ) else IdleImageWidget(context, config)

            ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT -> if (snapshot.hasTrack) LargeWidget(
                config, snapshot, context.resources.configuration.locales[0],
                fontScale,
                availableWidthDp, availableHeightDp,
            ) else IdleImageWidget(context, config)

            ResolvedWidgetContent.KARAOKE -> if (snapshot.hasTrack) KaraokeCardWidget(
                config, snapshot, context.resources.configuration.locales[0],
                fontScale,
                availableWidthDp, availableHeightDp,
            ) else IdleImageWidget(context, config)

            ResolvedWidgetContent.DESKTOP -> if (snapshot.hasTrack) TransparentWidget(
                config, snapshot, context.resources.configuration.locales[0],
                fontScale,
                availableWidthDp, availableHeightDp,
            ) else IdleImageWidget(context, config)
        }
}

@Composable
private fun WidgetStatus(message: String, config: LyricsWidgetConfig) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = secondaryStyle(config),
            maxLines = 2,
        )
    }
}

@Composable
private fun IdleImageWidget(context: Context, config: LyricsWidgetConfig) {
    val customBitmap = if (config.idleImageSource == WidgetIdleImageSource.CUSTOM) {
        WidgetIdleImageStore(context).load(config.idleImagePath)
    } else {
        null
    }
    val provider = customBitmap?.let(::ImageProvider) ?: ImageProvider(R.drawable.widget_idle_default)
    Image(
        provider = provider,
        contentDescription = context.getString(R.string.widget_idle_image_description),
        modifier = GlanceModifier.fillMaxSize().cornerRadius(config.cornerRadius.dp),
        contentScale = when (config.idleImageScale) {
            WidgetIdleImageScale.COVER -> ContentScale.Crop
            WidgetIdleImageScale.FIT -> ContentScale.Fit
        },
    )
}

@Composable
private fun MinimalWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    AdaptiveActiveLine(
        config = config,
        snapshot = snapshot,
        locale = locale,
        fontScale = fontScale,
        availableWidthDp = availableWidthDp,
        availableHeightDp = availableHeightDp,
        preferredFontSizeSp = 18f * config.textScale,
        minFontSizeSp = (12f * config.textScale).coerceAtLeast(10f),
        maxLines = 3,
        centered = true,
    )
}

@Composable
private fun TwoLinesWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    Column {
        val supporting = supportingLine(config, snapshot)
        val reservedHeight = 10f * config.textScale * fontScale * 1.2f + 8f +
            if (supporting.isNotBlank()) {
                6f + 13f * config.textScale * fontScale * 1.2f
            } else {
                0f
            }
        NowPlayingEyebrow(config, snapshot)
        Spacer(GlanceModifier.height(8.dp))
        AdaptiveActiveLine(
            config = config,
            snapshot = snapshot,
            locale = locale,
            fontScale = fontScale,
            availableWidthDp = availableWidthDp,
            availableHeightDp = (availableHeightDp - reservedHeight).coerceAtLeast(1f),
            preferredFontSizeSp = 20f * config.textScale,
            minFontSizeSp = (12f * config.textScale).coerceAtLeast(10f),
            maxLines = 3,
        )
        if (supporting.isNotBlank()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(text = supporting, style = secondaryStyle(config), maxLines = 1)
        }
    }
}

@Composable
private fun TransparentWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    Column {
        val supporting = supportingLine(config, snapshot)
        val reservedHeight = if (supporting.isNotBlank()) {
            4f + 13f * config.textScale * fontScale * 1.2f
        } else {
            0f
        }
        AdaptiveActiveLine(
            config = config,
            snapshot = snapshot,
            locale = locale,
            fontScale = fontScale,
            availableWidthDp = availableWidthDp,
            availableHeightDp = (availableHeightDp - reservedHeight).coerceAtLeast(1f),
            preferredFontSizeSp = 20f * config.textScale,
            minFontSizeSp = (12f * config.textScale).coerceAtLeast(10f),
            maxLines = 4,
            style = activeLineStyle(config).copy(
                color = fixedColor(Color(0xFFFFFFFF)),
            ),
        )
        if (supporting.isNotBlank()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = supporting,
                style = secondaryStyle(config).copy(color = fixedColor(Color(0xFFD7D7D7))),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KaraokeCardWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    Column {
        val translationHeight = if (config.showTranslation && snapshot.translation.isNotBlank()) {
            8f + 2f * 12f * config.textScale * fontScale * 1.2f
        } else {
            0f
        }
        val reservedHeight = 10f * config.textScale * fontScale * 1.2f + 8f +
            translationHeight
        NowPlayingEyebrow(config, snapshot)
        Spacer(GlanceModifier.height(8.dp))
        AdaptiveActiveLine(
            config = config,
            snapshot = snapshot,
            locale = locale,
            fontScale = fontScale,
            availableWidthDp = availableWidthDp,
            availableHeightDp = (availableHeightDp - reservedHeight).coerceAtLeast(1f),
            preferredFontSizeSp = 22f * config.textScale,
            minFontSizeSp = (12f * config.textScale).coerceAtLeast(10f),
            maxLines = 4,
        )
        if (config.showTranslation && snapshot.translation.isNotBlank()) {
            Spacer(GlanceModifier.height(8.dp))
            Text(text = snapshot.translation, style = translationStyle(config), maxLines = 2)
        }
    }
}

@Composable
private fun LargeWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    Column {
        val metadataHeight = 15f * config.textScale * fontScale * 1.2f +
            if (snapshot.artist.isNotBlank()) {
                2f + 13f * config.textScale * fontScale * 1.2f
            } else {
                0f
            }
        val previousHeight = if (snapshot.previousLine.isNotBlank()) {
            13f * config.textScale * fontScale * 1.2f + 7f
        } else {
            0f
        }
        val nextHeight = if (snapshot.nextLine.isNotBlank()) {
            7f + 2f * 13f * config.textScale * fontScale * 1.2f
        } else {
            0f
        }
        val translationHeight = if (config.showTranslation && snapshot.translation.isNotBlank()) {
            9f + 2f * 12f * config.textScale * fontScale * 1.2f
        } else {
            0f
        }
        val controlsHeight = if (config.showPlaybackControls) 12f + 31f else 0f
        val reservedHeight = metadataHeight + 12f + previousHeight + nextHeight +
            translationHeight + controlsHeight
        Metadata(config, snapshot)
        Spacer(GlanceModifier.height(12.dp))
        if (snapshot.previousLine.isNotBlank()) {
            Text(text = snapshot.previousLine, style = mutedLineStyle(config), maxLines = 1)
            Spacer(GlanceModifier.height(7.dp))
        }
        AdaptiveActiveLine(
            config = config,
            snapshot = snapshot,
            locale = locale,
            fontScale = fontScale,
            availableWidthDp = availableWidthDp,
            availableHeightDp = (availableHeightDp - reservedHeight).coerceAtLeast(1f),
            preferredFontSizeSp = 22f * config.textScale,
            minFontSizeSp = (12f * config.textScale).coerceAtLeast(10f),
            maxLines = 4,
        )
        if (snapshot.nextLine.isNotBlank()) {
            Spacer(GlanceModifier.height(7.dp))
            Text(text = snapshot.nextLine, style = secondaryStyle(config), maxLines = 2)
        }
        if (config.showTranslation && snapshot.translation.isNotBlank()) {
            Spacer(GlanceModifier.height(9.dp))
            Text(text = snapshot.translation, style = translationStyle(config), maxLines = 2)
        }
        if (config.showPlaybackControls) {
            Spacer(GlanceModifier.height(12.dp))
            PlaybackControls(snapshot)
        }
    }
}

@Composable
private fun CompactPlayerWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    Column {
        val metadataHeight = 15f * config.textScale * fontScale * 1.2f +
            if (snapshot.artist.isNotBlank()) {
                2f + 13f * config.textScale * fontScale * 1.2f
            } else {
                0f
            }
        val translationHeight = if (config.showTranslation && snapshot.translation.isNotBlank()) {
            3f + 12f * config.textScale * fontScale * 1.2f
        } else {
            0f
        }
        val controlsHeight = if (config.showPlaybackControls) 12f + 31f else 0f
        val reservedHeight = metadataHeight + 8f + translationHeight + controlsHeight
        Metadata(config, snapshot)
        Spacer(GlanceModifier.height(8.dp))
        AdaptiveActiveLine(
            config = config,
            snapshot = snapshot,
            locale = locale,
            fontScale = fontScale,
            availableWidthDp = availableWidthDp,
            availableHeightDp = (availableHeightDp - reservedHeight).coerceAtLeast(1f),
            preferredFontSizeSp = 17f * config.textScale,
            minFontSizeSp = (11f * config.textScale).coerceAtLeast(9f),
            maxLines = 2,
        )
        if (config.showTranslation && snapshot.translation.isNotBlank()) {
            Spacer(GlanceModifier.height(3.dp))
            Text(text = snapshot.translation, style = translationStyle(config), maxLines = 1)
        }
        if (config.showPlaybackControls) {
            Spacer(GlanceModifier.height(12.dp))
            PlaybackControls(snapshot)
        }
    }
}

@Composable
private fun CurrentWordWidget(
    context: Context,
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    val color = runCatching { config.primaryColorHex?.let(android.graphics.Color::parseColor) }
        .getOrNull() ?: context.getColor(R.color.widget_primary_text)
    val rendered = renderWidgetWord(context, snapshot.activeWord, snapshot.lyricsLanguageTag,
        availableWidthDp, availableHeightDp, config.textScale, color)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = CURRENT_WORD_BLOCK_ALIGNMENT) {
        Image(provider = ImageProvider(rendered.bitmap), contentDescription = snapshot.activeWord
            .uppercase(snapshot.lyricsLanguageTag.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag) ?: locale),
            modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

internal fun currentWordTextMeasurer(context: Context): WidgetTextMeasurer {
    val metrics = context.resources.displayMetrics
    val density = metrics.density.coerceAtLeast(0.5f)
    val scaledDensity = metrics.scaledDensity.coerceAtLeast(0.5f)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    return object : WidgetTextMeasurer {
        override fun measureWidthDp(text: String, fontSizeSp: Float): Float {
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp, metrics)
            return paint.measureText(text) / density
        }

        override fun measureLineHeightDp(fontSizeSp: Float): Float {
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp, metrics)
            val metrics = paint.fontMetrics
            return (metrics.bottom - metrics.top) / density
        }
    }
}

@Composable
private fun CurrentLineWidget(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
) {
    val source = snapshot.activeLine.ifBlank {
        if (config.useTitleAsFallback) snapshot.fallbackLabel() else ""
    }
    val preferredFontSize = 22f * config.textScale
    val minimumFontSize = (14f * config.textScale).coerceAtLeast(12f)
    val maxLines = (availableHeightDp / (minimumFontSize * fontScale * 1.25f))
        .toInt().coerceIn(1, 4)
    val layout = WordLineBreaker.layoutLine(
        line = source,
        locale = locale,
        availableWidthDp = availableWidthDp,
        availableHeightDp = availableHeightDp,
        fontScaleFactor = fontScale,
        preferredFontSizeSp = preferredFontSize,
        minFontSizeSp = minimumFontSize,
        maxLines = maxLines,
    )
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = layout.text,
            style = activeLineStyle(config).copy(
                fontSize = layout.fontSizeSp.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = layout.lineCount,
        )
    }
}

@Composable
private fun AdaptiveActiveLine(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    locale: Locale,
    fontScale: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
    preferredFontSizeSp: Float,
    minFontSizeSp: Float,
    maxLines: Int,
    centered: Boolean = false,
    style: TextStyle = activeLineStyle(config),
) {
    val source = snapshot.activeLine.ifBlank {
        if (config.useTitleAsFallback) snapshot.fallbackLabel() else ""
    }
    val layout = WordLineBreaker.layoutLine(
        line = source,
        locale = locale,
        availableWidthDp = availableWidthDp,
        availableHeightDp = availableHeightDp,
        fontScaleFactor = fontScale,
        preferredFontSizeSp = preferredFontSizeSp,
        minFontSizeSp = minFontSizeSp,
        maxLines = maxLines,
        fontWeightWidthFactor = 1.06f,
        lineHeightRatio = 1.20f,
    )
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = layout.text,
        style = if (centered) {
            style.copy(fontSize = layout.fontSizeSp.sp, textAlign = TextAlign.Center)
        } else {
            style.copy(fontSize = layout.fontSizeSp.sp)
        },
        maxLines = layout.lineCount,
    )
}

@Composable
private fun NowPlayingEyebrow(config: LyricsWidgetConfig, snapshot: LyricsWidgetSnapshot) {
    Text(
        text = AppLanguageManager.localizedContext(androidx.glance.LocalContext.current).getString(
            if (snapshot.isPlaying) R.string.widget_now_playing else R.string.widget_paused),
        style = eyebrowStyle(config),
        maxLines = 1,
    )
}

@Composable
private fun Metadata(config: LyricsWidgetConfig, snapshot: LyricsWidgetSnapshot) {
    Column {
        Text(
            text = snapshot.title,
            style = titleStyle(config),
            maxLines = 1,
        )
        if (snapshot.artist.isNotBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(text = snapshot.artist, style = secondaryStyle(config), maxLines = 1)
        }
    }
}

@Composable
private fun PlaybackControls(snapshot: LyricsWidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Control("‹", WidgetPlaybackCommand.PREVIOUS)
        Spacer(GlanceModifier.width(24.dp))
        Control(if (snapshot.isPlaying) "Ⅱ" else "▶", WidgetPlaybackCommand.PLAY_PAUSE)
        Spacer(GlanceModifier.width(24.dp))
        Control("›", WidgetPlaybackCommand.NEXT)
    }
}

@Composable
private fun Control(label: String, command: WidgetPlaybackCommand) {
    Box(
        modifier = GlanceModifier
            .background(Color(0x26FFFFFF))
            .cornerRadius(18.dp)
            .clickable(
                actionRunCallback<WidgetPlaybackAction>(
                    actionParametersOf(WidgetPlaybackAction.commandKey to command.wireValue),
                ),
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = fixedColor(Color(0xFFFFFFFF)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

private fun supportingLine(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
): String = when {
    config.showTranslation && snapshot.translation.isNotBlank() -> snapshot.translation
    snapshot.nextLine.isNotBlank() -> snapshot.nextLine
    else -> snapshot.artist
}

private fun eyebrowStyle(config: LyricsWidgetConfig) = TextStyle(
    color = fixedColor(configuredAccent(config)),
    fontSize = (10 * config.textScale).sp,
    fontWeight = FontWeight.Bold,
)

private fun titleStyle(config: LyricsWidgetConfig) = TextStyle(
    color = fixedColor(Color(0xFFFFFFFF)),
    fontSize = (15 * config.textScale).sp,
    fontWeight = FontWeight.Bold,
)

private fun activeLineStyle(config: LyricsWidgetConfig) = TextStyle(
    color = fixedColor(Color(0xFFFFF7EF)),
    fontSize = (20 * config.textScale).sp,
    fontWeight = FontWeight.Bold,
)

private fun secondaryStyle(config: LyricsWidgetConfig) = TextStyle(
    color = fixedColor(Color(0xFFB7B8BC)),
    fontSize = (13 * config.textScale).sp,
)

private fun mutedLineStyle(config: LyricsWidgetConfig) = TextStyle(
    color = fixedColor(Color(0xFF777A81)),
    fontSize = (13 * config.textScale).sp,
)

private fun translationStyle(config: LyricsWidgetConfig) = TextStyle(
    color = fixedColor(Color(0xFFFFB6A8)),
    fontSize = (12 * config.textScale).sp,
)

private fun configuredBackground(config: LyricsWidgetConfig): Color =
    Color(0xFF111217).copy(alpha = config.backgroundOpacityPercent / 100f)

private fun configuredAccent(config: LyricsWidgetConfig): Color = config.primaryColorHex
    ?.let { value -> runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull() }
    ?: Color(0xFFFF846F)

private fun contentAlignment(
    config: LyricsWidgetConfig,
    content: ResolvedWidgetContent,
): Alignment = when {
    content == ResolvedWidgetContent.CURRENT_WORD ||
        content == ResolvedWidgetContent.CURRENT_LINE -> Alignment.Center
    config.verticalBias < 0.34f -> Alignment.TopCenter
    config.verticalBias > 0.66f -> Alignment.BottomCenter
    else -> Alignment.Center
}

@Suppress("RestrictedApi")
private fun fixedColor(color: Color): ColorProvider = ColorProvider(color)
