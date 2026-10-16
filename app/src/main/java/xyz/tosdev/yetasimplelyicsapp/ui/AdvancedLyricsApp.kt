package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import android.content.res.Configuration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import xyz.tosdev.yetasimplelyicsapp.domain.sync.SyncState
import xyz.tosdev.yetasimplelyicsapp.ui.screens.EditorScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.LibraryScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.NowPlayingScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.OverlayConfiguratorScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.SearchScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.SettingsScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.StudioScreen
import xyz.tosdev.yetasimplelyicsapp.ui.screens.WidgetConfiguratorScreen
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalDesignSystem
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.DynamicProceduralBackground
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionSpecs
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalNavigationBar
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalNavigationItem
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalNavigationRail
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberArtworkPalette
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsTheme
import xyz.tosdev.yetasimplelyicsapp.R

private data class NavItem(
    val destination: AppDestination,
    val icon: ImageVector,
    val labelRes: Int,
)

private val primaryNavigation = listOf(
    NavItem(AppDestination.NOW_PLAYING, Icons.Outlined.GraphicEq, R.string.ui_nav_now_playing),
    NavItem(AppDestination.LIBRARY, Icons.Outlined.LibraryMusic, R.string.ui_nav_library),
    NavItem(AppDestination.SEARCH, Icons.Outlined.Search, R.string.ui_nav_search),
    NavItem(AppDestination.STUDIO, Icons.Outlined.Tune, R.string.ui_nav_studio),
    NavItem(AppDestination.SETTINGS, Icons.Outlined.Settings, R.string.ui_nav_settings),
)

@Composable
fun AdvancedLyricsApp(
    state: LyricsAppUiState = LyricsAppUiState(),
    actions: LyricsAppActions = LyricsAppActions(),
    sync: StateFlow<SyncState>? = null,
    snackbarHostState: SnackbarHostState? = null,
    onDarkSurfaceChanged: (Boolean) -> Unit = {},
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.NOW_PLAYING) }
    var fullscreenLyrics by rememberSaveable { mutableStateOf(false) }
    var settings by remember(state.settings) { mutableStateOf(state.settings) }
    val immersiveNowPlaying = isImmersiveNowPlaying(destination, fullscreenLyrics)
    KeepScreenOnEffect(
        preferenceEnabled = settings.keepScreenAwake,
        destination = destination,
        onKeepScreenOnChanged = onKeepScreenOnChanged,
    )
    val systemDark = isSystemInDarkTheme()
    val showsArtworkBackground = usesDynamicArtworkBackground(destination)
    val darkTheme = showsArtworkBackground || when (settings.themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    androidx.compose.runtime.SideEffect { onDarkSurfaceChanged(darkTheme) }
    // Do not carry light-theme foregrounds into the dark ambient surface during a tab change.
    // Track changes keep this identity, so their palette transition remains continuous.
    val artworkPalette = androidx.compose.runtime.key(darkTheme) {
        rememberArtworkPalette(
            artworkUri = state.nowPlaying.artworkUri,
            darkTheme = darkTheme,
            highContrast = settings.highContrastLyrics,
            motionLevel = settings.motionLevel,
        )
    }

    LyricsTheme(
        darkTheme = darkTheme,
        useDynamicColor = settings.dynamicColor,
        palette = artworkPalette.takeUnless { settings.dynamicColor },
    ) {
        val resolvedSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
        val motion = rememberSignalMotion(settings.motionLevel)
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val entranceAlpha by animateFloatAsState(
            targetValue = if (entered || !motion.enabled) 1f else 0f,
            animationSpec = SignalMotionSpecs.content(motion),
            label = "app entrance alpha",
        )
        val entranceScale by animateFloatAsState(
            targetValue = if (entered || !motion.enabled || motion.reduced) 1f else 0.985f,
            animationSpec = SignalMotionSpecs.content(motion),
            label = "app entrance scale",
        )
        Box(Modifier.fillMaxSize()) {
            if (showsArtworkBackground) {
                DynamicProceduralBackground(
                    palette = artworkPalette,
                    motionLevel = settings.motionLevel,
                    modifier = Modifier.matchParentSize(),
                )
            }
            AppFrame(
                destination = destination,
                onDestination = { destination = it },
                immersiveNowPlaying = immersiveNowPlaying,
                onImmersiveNowPlayingChange = { fullscreenLyrics = it },
                state = state.copy(settings = settings),
                actions = actions,
                sync = sync,
                onSettingsChange = {
                    settings = it
                    actions.onSettingsChange(it)
                },
                transparentRoot = showsArtworkBackground,
                modifier = Modifier.graphicsLayer {
                    alpha = entranceAlpha
                    scaleX = entranceScale
                    scaleY = entranceScale
                },
            )
            SnackbarHost(
                hostState = resolvedSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 520.dp)
                    .padding(horizontal = SignalDesignSystem.spacing.large)
                    .padding(bottom = 84.dp),
            )
        }
    }
}

@Composable
private fun AppFrame(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    immersiveNowPlaying: Boolean,
    onImmersiveNowPlayingChange: (Boolean) -> Unit,
    state: LyricsAppUiState,
    actions: LyricsAppActions,
    sync: StateFlow<SyncState>?,
    onSettingsChange: (SettingsUiState) -> Unit,
    transparentRoot: Boolean,
    modifier: Modifier = Modifier,
) {
    val primarySignalNavigation = primaryNavigation.map {
        SignalNavigationItem(stringResource(it.labelRes), it.icon)
    }
    BackHandler(
        enabled = immersiveNowPlaying || destination == AppDestination.EDITOR ||
            destination == AppDestination.OVERLAY || destination == AppDestination.WIDGET,
    ) {
        if (immersiveNowPlaying) onImmersiveNowPlayingChange(false)
        else onDestination(AppDestination.STUDIO)
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (transparentRoot) androidx.compose.ui.graphics.Color.Transparent
                else MaterialTheme.colorScheme.background,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        val useRail = maxWidth >= 840.dp
        val motion = rememberSignalMotion(state.settings.motionLevel)
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !immersiveNowPlaying,
                    enter = immersiveChromeEnter(motion, horizontal = true),
                    exit = immersiveChromeExit(motion, horizontal = true),
                ) {
                    AppNavigationRail(
                        destination,
                        state.settings.motionLevel,
                        transparentRoot,
                        primarySignalNavigation,
                        onDestination,
                    )
                }
                AppContent(
                    destination = destination,
                    onDestination = onDestination,
                    immersiveNowPlaying = immersiveNowPlaying,
                    onImmersiveNowPlayingChange = onImmersiveNowPlayingChange,
                    state = state,
                    actions = actions,
                    sync = sync,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    val selected = primaryNavigation.indexOfFirst {
                        it.destination == primaryDestination(destination)
                    }.coerceAtLeast(0)
                    AnimatedVisibility(
                        visible = !immersiveNowPlaying,
                        enter = immersiveChromeEnter(motion, horizontal = false),
                        exit = immersiveChromeExit(motion, horizontal = false),
                    ) {
                        SignalNavigationBar(
                            items = primarySignalNavigation,
                            selectedIndex = selected,
                            motionLevel = state.settings.motionLevel,
                            transparentContainer = transparentRoot,
                            onSelected = { onDestination(primaryNavigation[it].destination) },
                        )
                    }
                },
            ) { padding ->
                AppContent(
                    destination = destination,
                    onDestination = onDestination,
                    immersiveNowPlaying = immersiveNowPlaying,
                    onImmersiveNowPlayingChange = onImmersiveNowPlayingChange,
                    state = state,
                    actions = actions,
                    sync = sync,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    destination: AppDestination,
    motionLevel: MotionLevel,
    transparentContainer: Boolean,
    items: List<SignalNavigationItem>,
    onDestination: (AppDestination) -> Unit,
) {
    val selected = primaryNavigation.indexOfFirst {
        it.destination == primaryDestination(destination)
    }.coerceAtLeast(0)
    SignalNavigationRail(
        items = items,
        selectedIndex = selected,
        motionLevel = motionLevel,
        transparentContainer = transparentContainer,
        onSelected = { onDestination(primaryNavigation[it].destination) },
    )
}

internal fun usesDynamicArtworkBackground(destination: AppDestination): Boolean =
    destination == AppDestination.NOW_PLAYING

private fun primaryDestination(destination: AppDestination): AppDestination = when (destination) {
    AppDestination.EDITOR,
    AppDestination.OVERLAY,
    AppDestination.WIDGET,
    -> AppDestination.STUDIO
    else -> destination
}

internal fun isImmersiveNowPlaying(
    destination: AppDestination,
    fullscreenRequested: Boolean,
): Boolean = fullscreenRequested && destination == AppDestination.NOW_PLAYING

private fun immersiveChromeEnter(
    motion: xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionScheme,
    horizontal: Boolean,
): EnterTransition = when {
    !motion.enabled -> EnterTransition.None
    motion.reduced -> fadeIn(SignalMotionSpecs.content(motion))
    horizontal -> fadeIn(SignalMotionSpecs.immersive(motion)) +
        expandHorizontally(animationSpec = SignalMotionSpecs.immersive(motion))
    else -> fadeIn(SignalMotionSpecs.immersive(motion)) +
        expandVertically(animationSpec = SignalMotionSpecs.immersive(motion))
}

private fun immersiveChromeExit(
    motion: xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionScheme,
    horizontal: Boolean,
): ExitTransition = when {
    !motion.enabled -> ExitTransition.None
    motion.reduced -> fadeOut(SignalMotionSpecs.exit(motion))
    horizontal -> fadeOut(SignalMotionSpecs.exit(motion)) +
        shrinkHorizontally(animationSpec = SignalMotionSpecs.immersive(motion))
    else -> fadeOut(SignalMotionSpecs.exit(motion)) +
        shrinkVertically(animationSpec = SignalMotionSpecs.immersive(motion))
}

@Composable
private fun AppContent(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    immersiveNowPlaying: Boolean,
    onImmersiveNowPlayingChange: (Boolean) -> Unit,
    state: LyricsAppUiState,
    actions: LyricsAppActions,
    sync: StateFlow<SyncState>?,
    onSettingsChange: (SettingsUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTab = destination.isPrimaryTab()
    val motion = rememberSignalMotion(state.settings.motionLevel)
    val horizontalPadding by animateDpAsState(
        targetValue = if (immersiveNowPlaying) 0.dp else SignalDesignSystem.spacing.large,
        animationSpec = if (motion.enabled) SignalMotionSpecs.immersive(motion) else snap(),
        label = "immersive horizontal padding",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (immersiveNowPlaying || !primaryTab) 0.dp else SignalDesignSystem.spacing.medium,
        animationSpec = if (motion.enabled) SignalMotionSpecs.immersive(motion) else snap(),
        label = "immersive vertical padding",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        AnimatedContent(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (immersiveNowPlaying) Modifier
                    else Modifier.widthIn(
                        max = if (destination == AppDestination.NOW_PLAYING) 1200.dp else 760.dp,
                    )
                )
                .fillMaxWidth(),
            targetState = destination,
            transitionSpec = {
                when {
                    !motion.enabled -> EnterTransition.None togetherWith ExitTransition.None
                    motion.reduced -> fadeIn(SignalMotionSpecs.navigation(motion)) togetherWith
                        fadeOut(SignalMotionSpecs.exit(motion))
                    else -> fadeIn(SignalMotionSpecs.navigation(motion)) togetherWith
                        fadeOut(SignalMotionSpecs.exit(motion))
                }.using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }))
            },
            label = "ScreenTransition"
        ) { targetDestination ->
            when (targetDestination) {
                AppDestination.NOW_PLAYING -> NowPlayingScreen(
                    state = state.nowPlaying,
                    settings = state.settings,
                    actions = actions,
                    fullscreen = immersiveNowPlaying,
                    onFullscreenChange = onImmersiveNowPlayingChange,
                )
                AppDestination.LIBRARY -> LibraryScreen(
                    state = state.library,
                    onOpenItem = { id ->
                        actions.onOpenLibraryItem(id)
                        onDestination(AppDestination.EDITOR)
                    },
                )
                AppDestination.SEARCH -> SearchScreen(
                    state = state.search,
                    onSearch = actions.onSearch,
                    onOpenResult = actions.onOpenSearchResult,
                    motionLevel = state.settings.motionLevel,
                )
                AppDestination.STUDIO -> StudioScreen(
                    onDestination = onDestination,
                    actions = actions,
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    state = state.settings,
                    systemAccess = state.systemAccess,
                    actions = actions,
                    onStateChange = onSettingsChange,
                )
                AppDestination.EDITOR -> EditorScreen(
                    state = state.editor,
                    onBack = { onDestination(AppDestination.STUDIO) },
                    onSave = actions.onEditorSave,
                    onImport = actions.onImportLyrics,
                    onExport = actions.onExportLyrics,
                )
                AppDestination.OVERLAY -> OverlayConfiguratorScreen(
                    state = state.overlay,
                    accessStatus = state.systemAccess.overlay,
                    onRequestAccess = actions.onRequestOverlayAccess,
                    onBack = { onDestination(AppDestination.STUDIO) },
                    onStateChange = actions.onOverlayChange,
                )
                AppDestination.WIDGET -> WidgetConfiguratorScreen(
                    state = state.widget,
                    snapshot = state.widgetSnapshot,
                    artworkUri = state.nowPlaying.artworkUri,
                    pickerAvailable = state.systemAccess.widgetPickerAvailable,
                    onOpenWidgetPicker = actions.onOpenWidgetPicker,
                    onBack = { onDestination(AppDestination.STUDIO) },
                    onStateChange = actions.onWidgetChange,
                )
            }
        }
    }
}

private fun AppDestination.isPrimaryTab(): Boolean = when (this) {
    AppDestination.NOW_PLAYING,
    AppDestination.LIBRARY,
    AppDestination.SEARCH,
    AppDestination.STUDIO,
    AppDestination.SETTINGS,
    -> true
    else -> false
}

private fun previewState(): LyricsAppUiState = LyricsAppUiState(
    nowPlaying = NowPlayingUiState(
        connectionLabel = "com.spotify.music",
        title = "Afterlight",
        artist = "Northbound",
        album = "Signal Bloom",
        positionMs = 72_000L,
        durationMs = 214_000L,
        hasActiveSession = true,
        isPlaying = true,
        canTogglePlayback = true,
        canSeek = true,
        canSkipPrevious = true,
        canSkipNext = true,
        sourceLabel = "TTML · palabra por palabra",
        activeLineId = "line-2",
        isPreview = true,
        lyrics = listOf(
            LyricLineUi(
                id = "line-1",
                words = listOf(
                    KaraokeWordUi("1-1", "We", 1f),
                    KaraokeWordUi("1-2", "left", 1f),
                    KaraokeWordUi("1-3", "the", 1f),
                    KaraokeWordUi("1-4", "city", 1f),
                    KaraokeWordUi("1-5", "glowing", 1f),
                ),
                translation = "Dejamos la ciudad brillando",
                isPast = true,
            ),
            LyricLineUi(
                id = "line-2",
                words = listOf(
                    KaraokeWordUi("2-1", "Chasing", 1f),
                    KaraokeWordUi("2-2", "the", 1f),
                    KaraokeWordUi("2-3", "afterlight", 0.58f),
                ),
                translation = "Persiguiendo la última luz",
                isActive = true,
            ),
            LyricLineUi(
                id = "line-3",
                words = listOf(
                    KaraokeWordUi("3-1", "Nothing", 0f),
                    KaraokeWordUi("3-2", "but", 0f),
                    KaraokeWordUi("3-3", "open", 0f),
                    KaraokeWordUi("3-4", "sky", 0f),
                ),
                translation = "Nada salvo el cielo abierto",
            ),
        ),
    ),
)

@Preview(
    name = "Now Playing · dark phone",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AdvancedLyricsAppPreview() {
    AdvancedLyricsApp(
        state = previewState().copy(
            settings = SettingsUiState(themeMode = AppThemeMode.DARK),
        ),
    )
}

@Preview(
    name = "Now Playing · light tablet",
    showBackground = true,
    widthDp = 900,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun AdvancedLyricsAppWidePreview() {
    AdvancedLyricsApp(
        state = previewState().copy(
            settings = SettingsUiState(themeMode = AppThemeMode.LIGHT),
        ),
    )
}
