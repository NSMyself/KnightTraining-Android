package com.knightindustries.knighttraining.feature.resources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.CatalogCourse.LanguageSection
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.kindNameRes
import com.knightindustries.knighttraining.ui.kindOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddResourceSheet(viewModel: AddResourceViewModel, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Text(stringResource(R.string.add_resource), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.add_manually)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.browse_catalog)) })
            }
            if (tab == 0) ManualForm(viewModel, onDismiss) else CatalogBrowser(viewModel, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualForm(viewModel: AddResourceViewModel, onDone: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = viewModel.url, onValueChange = viewModel::updateUrl, singleLine = true,
            label = { Text(stringResource(R.string.resource_link)) }, supportingText = { Text(stringResource(R.string.resource_link_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (viewModel.isLookingUp) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.looking_up_link), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.resource_kind), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kindOrder.forEach { code -> FilterChip(selected = viewModel.kind == code, onClick = { viewModel.pickKind(code) }, label = { Text(stringResource(kindNameRes(code))) }) }
        }
        Field(viewModel.title, R.string.resource_title) { viewModel.title = it }
        Field(viewModel.author, R.string.resource_author) { viewModel.author = it }
        Button(enabled = viewModel.canSave, onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add)) }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The catalog by platform. Chessable splits by language (the user's own first, then English,
 * then the rest), with headers only when there's more than one section. ChessMood carries the
 * maintainer's affiliate link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogBrowser(viewModel: AddResourceViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadCatalog() }
    var platform by rememberSaveable { mutableStateOf(CatalogCourse.CHESSMOOD) }
    var query by rememberSaveable { mutableStateOf("") }
    val courses = viewModel.courses

    Column(Modifier.padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            listOf(CatalogCourse.CHESSMOOD to "ChessMood", CatalogCourse.CHESSABLE to "Chessable").forEachIndexed { index, (code, name) ->
                SegmentedButton(selected = platform == code, onClick = { platform = code }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(name) }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search_catalog)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth(),
        )
        when {
            courses == null -> CircularProgressIndicator(Modifier.padding(24.dp))
            courses.isEmpty() -> Text(stringResource(R.string.catalog_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            else -> {
                val shown = courses.filter { it.platform == platform && (query.isBlank() || it.title.contains(query.trim(), true) || it.author?.contains(query.trim(), true) == true) }
                val language = currentLocale().toLanguageTag()
                val sections = if (platform == CatalogCourse.CHESSABLE) CatalogCourse.languageSections(shown, language) else listOf(LanguageSection.Other to shown)
                LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                    if (platform == CatalogCourse.CHESSMOOD) {
                        item {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(stringResource(R.string.chessmood_learn_more), color = MaterialTheme.colorScheme.primary) },
                                trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                                modifier = Modifier.clickable { openLink(context, CatalogCourse.CHESSMOOD_REFERRAL_URL) },
                            )
                        }
                    }
                    sections.forEach { (section, list) ->
                        if (sections.size > 1) {
                            item { Text(sectionTitle(section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp)) }
                        }
                        items(list, key = { it.id }) { course ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(course.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = course.author?.let { { Text(it) } },
                                modifier = Modifier.clickable { viewModel.addCourse(course, onDone) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(section: LanguageSection): String = when (section) {
    is LanguageSection.Preferred -> stringResource(R.string.language_section_preferred)
    LanguageSection.English -> stringResource(R.string.language_section_english)
    LanguageSection.Other -> stringResource(R.string.language_section_other)
}
