package xyz.tosdev.yetasimplelyicsapp.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionSpecs
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion
import xyz.tosdev.yetasimplelyicsapp.R

@Composable
fun SongArtwork(
    artworkUri: String?,
    title: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    emptyLabel: String? = null,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
    showBorder: Boolean = true,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, artworkUri) {
        value = withContext(Dispatchers.IO) {
            decodeArtworkBitmap(context, artworkUri)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(cornerRadius.dp)
    val colors = MaterialTheme.colorScheme
    val motion = rememberSignalMotion(motionLevel)

    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.surfaceVariant,
        border = if (showBorder) {
            BorderStroke(1.dp, colors.outline.copy(alpha = 0.16f))
        } else {
            null
        },
    ) {
        Crossfade(
            targetState = bitmap,
            animationSpec = SignalMotionSpecs.immersive(motion),
            label = "song artwork",
        ) { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = title?.let { stringResource(R.string.ui_artwork_description, it) },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (emptyLabel != null) {
                        Text(
                            text = emptyLabel,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            letterSpacing = 1.4.sp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxSize(0.38f),
                        )
                    }
                }
            }
        }
    }
}
