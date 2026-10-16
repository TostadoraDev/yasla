package xyz.tosdev.yetasimplelyicsapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.ui.LibraryItemUi
import xyz.tosdev.yetasimplelyicsapp.ui.LibraryUiState
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.SearchUiState
import xyz.tosdev.yetasimplelyicsapp.ui.components.SongArtwork
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalBadge
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalDesignSystem
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalEmptyState
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalSectionHeader
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.signalLiquidGlass
import xyz.tosdev.yetasimplelyicsapp.R

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenItem: (String) -> Unit,
) {
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    val items = if (showFavorites) state.favorites else state.recent

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        ScreenHeading(
            eyebrow = stringResource(R.string.ui_library_eyebrow),
            title = stringResource(R.string.ui_library_title),
            description = stringResource(R.string.ui_library_description),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = !showFavorites,
                onClick = { showFavorites = false },
                label = { Text(stringResource(R.string.ui_library_recent)) },
            )
            FilterChip(
                selected = showFavorites,
                onClick = { showFavorites = true },
                label = { Text(stringResource(R.string.ui_library_favorites)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            HonestEmptyState(
                icon = if (showFavorites) Icons.Rounded.FavoriteBorder else Icons.Rounded.LibraryMusic,
                title = if (showFavorites) stringResource(R.string.ui_library_empty_favorites_title) else stringResource(R.string.ui_library_empty_title),
                detail = if (showFavorites) {
                    stringResource(R.string.ui_library_empty_favorites_detail)
                } else {
                    stringResource(R.string.ui_library_empty_detail)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn {
                items(items, key = { it.id }) { item ->
                    LibraryRow(item = item, onClick = { onOpenItem(item.id) })
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItemUi,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongArtwork(
                artworkUri = item.artworkUri,
                title = item.title,
                modifier = Modifier.size(58.dp),
                cornerRadius = 14,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignalBadge(
                text = if (item.hasWordSync) stringResource(R.string.ui_sync_word) else stringResource(R.string.ui_sync_synced),
                emphasized = item.hasWordSync,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    }
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
) {
    var query by remember(state.query) { mutableStateOf(state.query) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        ScreenHeading(
            eyebrow = stringResource(R.string.ui_search_eyebrow),
            title = stringResource(R.string.ui_search_title),
            description = stringResource(R.string.ui_search_description),
        )
        Spacer(Modifier.height(20.dp))
        val searchShape = RoundedCornerShape(SignalDesignSystem.radii.large)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .signalLiquidGlass(
                    shape = searchShape,
                    motionLevel = motionLevel,
                    emphasized = true,
                )
                .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.ui_search_field_label)) },
            placeholder = { Text(stringResource(R.string.ui_search_field_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) onSearch(query.trim())
                    focusManager.clearFocus()
                },
            ),
            trailingIcon = {
                IconButton(
                    enabled = query.isNotBlank(),
                    onClick = {
                            onSearch(query.trim())
                            focusManager.clearFocus()
                    },
                ) { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.ui_search_action)) }
            },
            shape = searchShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            ),
        )
        Spacer(Modifier.height(18.dp))
        when {
            state.isSearching -> Column(Modifier.fillMaxSize()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                HonestEmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.ui_search_loading_title),
                    detail = stringResource(R.string.ui_search_loading_detail),
                    modifier = Modifier.weight(1f),
                )
            }
            state.results.isNotEmpty() -> LazyColumn {
                items(state.results, key = { it.id }) { result ->
                    SearchResultRow(result, onClick = { onOpenResult(result.id) })
                }
            }
            query.isBlank() -> HonestEmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.ui_search_empty_title),
                detail = stringResource(R.string.ui_search_empty_detail),
                modifier = Modifier.fillMaxSize(),
            )
            else -> HonestEmptyState(
                icon = Icons.Rounded.GraphicEq,
                title = state.message ?: stringResource(R.string.ui_search_no_results),
                detail = stringResource(R.string.ui_search_no_results_detail),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun ScreenHeading(
    eyebrow: String,
    title: String,
    description: String,
) {
    SignalSectionHeader(eyebrow = eyebrow, title = title, description = description)
}

@Composable
internal fun HonestEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.GraphicEq,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    SignalEmptyState(icon = icon, title = title, detail = detail, modifier = modifier)
}

@Composable
private fun SearchResultRow(
    result: xyz.tosdev.yetasimplelyicsapp.ui.SearchResultUi,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    result.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    result.provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignalBadge(
                text = result.syncLabel,
                emphasized = result.syncLabel.contains("word", ignoreCase = true),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    }
}
