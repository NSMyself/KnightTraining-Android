package com.knightindustries.knighttraining.feature.resources

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import com.knightindustries.knighttraining.designsystem.glowTopAppBarColors
import androidx.compose.foundation.lazy.rememberLazyListState
import com.knightindustries.knighttraining.ui.ScrollToTopOnReselect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.feature.share.ShareLibrarySheet
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.store.observeAllResources
import com.knightindustries.knighttraining.ui.LabeledFab
import com.knightindustries.knighttraining.ui.kindOrder
import com.knightindustries.knighttraining.ui.kindPluralRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** Not started / In progress / Completed, the library's second filter facet. */
enum class ResourceStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

fun Resource.status(): ResourceStatus = when {
    isCompleted -> ResourceStatus.COMPLETED
    progressPercentage > 0 -> ResourceStatus.IN_PROGRESS
    else -> ResourceStatus.NOT_STARTED
}

/** The library, observed: a share import or a background sync shows up without a reload. */
class ResourcesViewModel(container: AppContainer) : ViewModel() {
    val resources: StateFlow<List<Resource>?> = container.store.observeAllResources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The Resources tab (iOS `ResourcesScreen`): search, two filter facets in one chip row (kind,
 * then status; each single-select with toggle-off), and the library grouped by kind as horizontal
 * rows, except Lichess studies, which have no covers and show as a list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesScreen(viewModel: ResourcesViewModel, onOpen: (UUID) -> Unit, onAdd: () -> Unit, contentPadding: PaddingValues) {
    val resources by viewModel.resources.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf<ResourceStatus?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(list = listState, topBar = scrollBehavior)
    var sharing by rememberSaveable { mutableStateOf(false) }
    if (sharing) ShareLibrarySheet(onDismiss = { sharing = false })

    val all = resources.orEmpty()
    val visible = all.filter { resource ->
        (kindFilter == null || resource.kind.code == kindFilter) &&
            (statusFilter == null || resource.status() == statusFilter) &&
            (query.isBlank() || listOfNotNull(resource.title, resource.author, resource.notes).any { it.contains(query.trim(), ignoreCase = true) })
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_resources)) },
                actions = {
                    if (!resources.isNullOrEmpty()) {
                        IconButton(onClick = { sharing = true }) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_library_title)) }
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = stringResource(R.string.search_resources))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = glowTopAppBarColors(),
            )
        },
        floatingActionButton = { LabeledFab(Icons.Rounded.Add, stringResource(R.string.add_resource), onAdd) },
        // The tab's glow (`GradientBackground`) is drawn behind it.
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(Modifier.padding(top = padding.calculateTopPadding()), state = listState, contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searching) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_resources)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            if (all.isNotEmpty()) {
                item { FilterRow(kindFilter, statusFilter, { kindFilter = it }, { statusFilter = it }) }
            }
            when {
                resources == null -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                all.isEmpty() -> item {
                    EmptyState(Icons.AutoMirrored.Rounded.LibraryBooks, stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_body), stringResource(R.string.library_empty_link_hint))
                }
                visible.isEmpty() -> item {
                    EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.no_matches), stringResource(R.string.no_matches_body))
                }
                else -> kindOrder.forEach { code ->
                    val ofKind = visible.filter { it.kind.code == code }
                    if (ofKind.isEmpty()) return@forEach
                    item(key = "header-$code") {
                        Text(stringResource(kindPluralRes(code)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    }
                    if (code == ResourceKind.LICHESS_STUDY) {
                        // Studies have no covers: a row of identical placeholders said nothing.
                        items(ofKind.sortedWith(studyOrder), key = { it.id }) { StudyRow(it) { onOpen(it.id) } }
                    } else {
                        item(key = "row-$code") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(ofKind, key = { it.id }) { ResourceCard(it) { onOpen(it.id) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** In progress by recency, then not started, then finished (iOS `LichessStudyList`). */
private val studyOrder = compareBy<Resource>({ it.status().let { s -> listOf(ResourceStatus.IN_PROGRESS, ResourceStatus.NOT_STARTED, ResourceStatus.COMPLETED).indexOf(s) } })
    .thenByDescending { it.lastAccessedAt }

@Composable
private fun FilterRow(kind: String?, status: ResourceStatus?, onKind: (String?) -> Unit, onStatus: (ResourceStatus?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item { FilterChip(selected = kind == null && status == null, onClick = { onKind(null); onStatus(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
        items(kindOrder) { code ->
            FilterChip(selected = kind == code, onClick = { onKind(if (kind == code) null else code) }, label = { Text(stringResource(kindPluralRes(code))) })
        }
        item { VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp)) }
        items(ResourceStatus.entries) { value ->
            FilterChip(selected = status == value, onClick = { onStatus(if (status == value) null else value) }, label = { Text(stringResource(statusName(value))) })
        }
    }
}

fun statusName(status: ResourceStatus): Int = when (status) {
    ResourceStatus.NOT_STARTED -> R.string.status_not_started
    ResourceStatus.IN_PROGRESS -> R.string.status_in_progress
    ResourceStatus.COMPLETED -> R.string.status_completed
}

@Composable
private fun ResourceCard(resource: Resource, onClick: () -> Unit) {
    val portrait = resource.kind.code == ResourceKind.BOOK || resource.kind.code == ResourceKind.COURSE
    Column(Modifier.width(if (portrait) 120.dp else 200.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.TopStart) {
            ResourceCover(resource, Modifier.fillMaxWidth())
            ResourceBadges(resource, Modifier.padding(6.dp))
        }
        Text(resource.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        resource.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (resource.isInProgress) LinearProgressIndicator(progress = { resource.progressPercentage / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StudyRow(resource: Resource, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(resource.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (resource.isInProgress) ({ LinearProgressIndicator(progress = { resource.progressPercentage / 100f }, modifier = Modifier.fillMaxWidth()) }) else null,
        trailingContent = if (resource.isCompleted) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.status_completed), tint = MaterialTheme.colorScheme.primary) }) else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** iOS's library states: an icon, a title and what to do next. */
@Composable
private fun EmptyState(icon: ImageVector, title: String, vararg body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

/**
 * The cover's corner labels (iOS `ResourceBadge`): automatically imported, and Reading (Studying
 * for courses and studies) while in progress, or Completed.
 */
@Composable
fun ResourceBadges(resource: Resource, modifier: Modifier = Modifier) {
    val studied = resource.kind.code == ResourceKind.COURSE || resource.kind.code == ResourceKind.LICHESS_STUDY
    val badges = buildList {
        if (resource.isAutoImported) add(R.string.auto_imported to Color(0xFF6B6B76))
        when {
            resource.isInProgress -> add((if (studied) R.string.badge_studying else R.string.badge_reading) to Color(0xFF5C7CAF))
            resource.isCompleted -> add(R.string.status_completed to Color(0xFF5C9A6E))
        }
    }
    if (badges.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        badges.forEach { (label, color) ->
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}
