package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

@Immutable
data class SignalNavigationItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun SignalSectionHeader(
    eyebrow: String,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = SignalDesignSystem.spacing
    Column(modifier.fillMaxWidth()) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(spacing.tiny))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        description?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(spacing.tiny))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = SignalDesignSystem.colors.textMuted,
                modifier = Modifier.widthIn(max = 620.dp),
            )
        }
    }
}

@Composable
fun SignalBadge(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val radii = SignalDesignSystem.radii
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radii.pill),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        border = null,
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.7.sp,
            color = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SignalSurface(
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    interactive: Boolean = false,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = SignalDesignSystem.colors
    val radii = SignalDesignSystem.radii
    val shape = RoundedCornerShape(radii.large)
    if (glass) {
        SignalLiquidGlassSurface(
            modifier = modifier,
            shape = shape,
            motionLevel = motionLevel,
            emphasized = interactive,
            selected = interactive,
            contentPadding = contentPadding,
            content = content,
        )
        return
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = when {
            interactive -> colors.surfaceInteractive
            else -> colors.surfaceRaised
        },
        border = if (interactive) BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ) else null,
        shadowElevation = SignalDesignSystem.elevation.flat,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SignalEmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val spacing = SignalDesignSystem.spacing
    Column(
        modifier = modifier.padding(horizontal = spacing.medium),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(spacing.small).size(28.dp),
            )
        }
        Spacer(Modifier.height(spacing.medium))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.tiny))
        Text(
            detail,
            style = MaterialTheme.typography.bodyLarge,
            color = SignalDesignSystem.colors.textMuted,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}

@Composable
fun SignalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    enabled: Boolean = true,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
) {
    val motion = rememberSignalMotion(motionLevel)
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled && motion.enabled) 0.92f else 1f,
        animationSpec = SignalMotionSpecs.microSpring(motion),
        label = "button press scale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.76f else if (enabled) 1f else 0.38f,
        animationSpec = SignalMotionSpecs.fast(motion),
        label = "button press alpha",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            prominent && pressed && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
            prominent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.94f)
            pressed && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = SignalMotionSpecs.fast(motion),
        label = "button liquid highlight",
    )
    Surface(
        modifier = modifier
            .size(if (prominent) 64.dp else 48.dp)
            .semantics { this.contentDescription = contentDescription },
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = containerColor,
        contentColor = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = null,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = icon,
                animationSpec = SignalMotionSpecs.fast(motion),
                label = "transport icon",
            ) { targetIcon ->
                Icon(
                    targetIcon,
                    contentDescription = null,
                    modifier = Modifier.size(if (prominent) 29.dp else 25.dp),
                )
            }
        }
    }
}

@Composable
fun SignalNavigationBar(
    items: List<SignalNavigationItem>,
    selectedIndex: Int,
    motionLevel: MotionLevel,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    transparentContainer: Boolean = false,
) {
    val motion = rememberSignalMotion(motionLevel)
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .background(if (transparentContainer) Color.Transparent else MaterialTheme.colorScheme.background),
    ) {
        val itemWidth = maxWidth / items.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            animationSpec = SignalMotionSpecs.navigation(motion),
            label = "navigation indicator offset",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset + itemWidth * 0.28f)
                .width(itemWidth * 0.44f)
                .height(2.dp)
                .align(Alignment.BottomStart)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    CircleShape,
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    SignalMotionSpecs.micro(motion),
                    label = "navigation tint",
                )
                val scale by animateFloatAsState(
                    if (selected) 1f else 0.94f,
                    SignalMotionSpecs.microSpring(motion),
                    label = "navigation scale",
                )
                val labelAlpha by animateFloatAsState(
                    if (selected) 1f else 0.68f,
                    SignalMotionSpecs.fast(motion),
                    label = "navigation label alpha",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                        ) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelected(index)
                        }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(21.dp).graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha },
                    )
                }
            }
        }
    }
}

@Composable
fun SignalNavigationRail(
    items: List<SignalNavigationItem>,
    selectedIndex: Int,
    motionLevel: MotionLevel,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    transparentContainer: Boolean = false,
) {
    val motion = rememberSignalMotion(motionLevel)
    val colors = SignalDesignSystem.colors
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.width(96.dp).fillMaxHeight(),
        color = if (transparentContainer) Color.Transparent else colors.surfaceLow,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    SignalMotionSpecs.micro(motion),
                    label = "rail tint",
                )
                val indicatorColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else Color.Transparent,
                    SignalMotionSpecs.navigation(motion),
                    label = "rail indicator color",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                        ) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelected(index)
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 32.dp)
                            .background(indicatorColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, null, tint = tint, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}
