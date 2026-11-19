package com.knightindustries.knighttraining.feature.home

import com.knightindustries.knighttraining.feedback.FeedbackEvent
import com.knightindustries.knighttraining.feedback.rememberFeedback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Edit Home: the pinned plan card first (locked), then the chosen tiles with drag handles and
 * remove buttons, then every hidden tile to add. Each change saves at once, so Done only closes.
 * The Android counterpart of iOS's `EditHomeSheet`; iOS's in-place jiggle mode has no Android
 * convention, so it isn't ported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHomeSheet(layout: HomeLayout, onChange: (HomeLayout) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    // A local copy while dragging, so the list doesn't wait on DataStore between frames.
    var tiles by remember(layout) { mutableStateOf(layout.tiles) }
    val feedback = rememberFeedback()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = tiles.indexOfFirst { it.id == from.key }
        val toIndex = tiles.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            tiles = tiles.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            feedback(FeedbackEvent.HomeTileSlotChanged)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding().testTag("editHomeList")) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.edit_home), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                }
            }
            item(key = "pinned") {
                Column {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.home_pinned_plan)) },
                        supportingContent = { Text(stringResource(R.string.home_pinned_plan_detail)) },
                        trailingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    )
                    Text(
                        stringResource(R.string.home_pinned_note),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item(key = "onHome") { Header(stringResource(R.string.home_on_home)) }
            if (tiles.isEmpty()) {
                item(key = "empty") { Note(stringResource(R.string.home_only_plan)) }
            }
            items(tiles, key = { it.id }) { tile ->
                ReorderableItem(reorderState, key = tile.id) { isDragging ->
                    val title = tile.title()
                    Surface(tonalElevation = if (isDragging) 6.dp else 0.dp, color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                IconButton(onClick = { tiles = tiles - tile; onChange(HomeLayout(tiles)) }) {
                                    Icon(Icons.Rounded.RemoveCircle, contentDescription = stringResource(R.string.home_remove_tile, title), tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            headlineContent = { Text(title) },
                            supportingContent = tile.detail()?.let { { Text(it) } },
                            trailingContent = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { feedback(FeedbackEvent.HomeTileLifted) },
                                        onDragStopped = { onChange(HomeLayout(tiles)) },
                                    ),
                                ) { Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.home_reorder_tile, title)) }
                            },
                        )
                    }
                }
            }
            item(key = "moreHeader") { Header(stringResource(R.string.home_more_tiles)) }
            val hidden = HomeTile.entries.filter { it !in tiles }
            if (hidden.isEmpty()) {
                item(key = "allAdded") { Note(stringResource(R.string.home_all_tiles_added)) }
            }
            items(hidden, key = { "hidden-" + it.id }) { tile ->
                val title = tile.title()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        IconButton(onClick = { tiles = tiles + tile; onChange(HomeLayout(tiles)) }) {
                            Icon(Icons.Rounded.AddCircle, contentDescription = stringResource(R.string.home_add_tile, title), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    headlineContent = { Text(title) },
                    supportingContent = tile.detail()?.let { { Text(it) } },
                    trailingContent = { Icon(tile.icon(), contentDescription = null) },
                )
            }
            item(key = "reset") {
                Column {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.home_reset)) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
)

@Composable
private fun Note(text: String) = Text(
    text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
