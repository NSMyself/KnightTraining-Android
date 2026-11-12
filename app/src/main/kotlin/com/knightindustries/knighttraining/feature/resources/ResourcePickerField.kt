package com.knightindustries.knighttraining.feature.resources

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.store.allResources
import com.knightindustries.knighttraining.ui.kindIcon
import com.knightindustries.knighttraining.ui.kindOrder
import java.util.UUID

/**
 * "Link a resource to track your time on it": the plan ↔ resource link that's the app's reason to
 * exist (iOS AGENTS.md §2). Always optional: Puzzles or Playing often have no single resource.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcePickerField(selectedId: UUID?, onSelect: (Resource?) -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val resources by produceState<List<Resource>>(emptyList()) { value = AppContainer.from(application).store.allResources() }
    val selected = resources.firstOrNull { it.id == selectedId }
    var choosing by rememberSaveable { mutableStateOf(false) }

    OutlinedCard(Modifier.fillMaxWidth().clickable { choosing = true }) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            overlineContent = { Text(stringResource(R.string.link_resource)) },
            headlineContent = { Text(selected?.title ?: stringResource(R.string.link_resource_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = selected?.let { { Icon(kindIcon(it.kind.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary) } },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        )
    }

    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding()) {
                Text(stringResource(R.string.choose_resource), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
                LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                    item {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Rounded.Close, contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.resource_none)) },
                            modifier = Modifier.clickable { onSelect(null); choosing = false },
                        )
                    }
                    items(resources.sortedBy { kindOrder.indexOf(it.kind.code) }, key = { it.id }) { resource ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { ResourceCover(resource, Modifier.width(40.dp)) },
                            headlineContent = { Text(resource.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = resource.author?.let { { Text(it) } },
                            modifier = Modifier.clickable { onSelect(resource); choosing = false },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
