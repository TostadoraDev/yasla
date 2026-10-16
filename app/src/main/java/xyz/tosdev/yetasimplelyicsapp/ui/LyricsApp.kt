package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsTheme
import xyz.tosdev.yetasimplelyicsapp.R

@Composable
fun LyricsApp() {
    AdvancedLyricsApp()
}

@Composable
private fun LyricsHomeScreen() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.16f),
                        colors.background.copy(alpha = 0.96f),
                        colors.background,
                    ),
                    center = Offset(140f, 120f),
                    radius = 1_250f,
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 1_180.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            AppHeader()
            Spacer(Modifier.height(40.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 720.dp
                if (isWide) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkPanel(
                            modifier = Modifier
                                .weight(0.43f)
                                .aspectRatio(1f),
                        )
                        LyricsWaitingPanel(
                            modifier = Modifier.weight(0.57f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
                        ArtworkPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                        LyricsWaitingPanel(Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            FoundationStatus()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                        CircleShape
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.ui_home_brand),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 2.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Text(
                text = stringResource(R.string.ui_legacy_active_engine),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ArtworkPanel(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val artworkDescription = stringResource(R.string.ui_legacy_artwork_placeholder)
    Surface(
        modifier = modifier.semantics {
            contentDescription = artworkDescription
        },
        shape = RoundedCornerShape(38.dp),
        color = colors.surface,
        tonalElevation = 2.dp,
        shadowElevation = 18.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.primary,
                            colors.secondary,
                            colors.surface,
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f),
                    radius = size.minDimension * 0.32f,
                    center = center,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = size.minDimension * 0.20f,
                    center = center,
                )
                drawCircle(
                    color = colors.primary,
                    radius = size.minDimension * 0.045f,
                    center = center,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(size.width * 0.2f, size.height * 0.80f),
                    end = Offset(size.width * 0.8f, size.height * 0.25f),
                    strokeWidth = size.minDimension * 0.015f,
                    cap = StrokeCap.Round,
                )
            }
            Text(
                text = stringResource(R.string.ui_legacy_system_ready),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
private fun LyricsWaitingPanel(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.ui_legacy_waiting),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.ui_legacy_sync_active),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.ui_legacy_connect_player),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(34.dp))
        PreviewLine(widthFraction = 0.92f, active = true)
        Spacer(Modifier.height(14.dp))
        PreviewLine(widthFraction = 0.72f)
        Spacer(Modifier.height(14.dp))
        PreviewLine(widthFraction = 0.84f)
    }
}

@Composable
private fun PreviewLine(
    widthFraction: Float,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(if (active) 14.dp else 10.dp)
            .background(
                color = if (active) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                },
                shape = CircleShape,
            ),
    )
}

@Composable
private fun FoundationStatus() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StatusItem(
            label = stringResource(R.string.ui_legacy_engine),
            value = stringResource(R.string.ui_legacy_ready),
            modifier = Modifier.weight(1f),
        )
        StatusItem(
            label = stringResource(R.string.ui_legacy_source),
            value = stringResource(R.string.ui_legacy_disconnected),
            modifier = Modifier.weight(1f),
        )
        StatusItem(
            label = stringResource(R.string.ui_legacy_mode),
            value = stringResource(R.string.ui_legacy_local),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LyricsAppPreview() {
    LyricsTheme(darkTheme = true) {
        LyricsHomeScreen()
    }
}
