package com.knightindustries.knighttraining.feature.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.designsystem.GradientBackground
import com.knightindustries.knighttraining.designsystem.Palette
import com.knightindustries.knighttraining.ui.kindPluralRes
import java.util.UUID

/** Which kinds open a "see all" screen from their header, and how many studies the library shows. */
object ResourceShelf {
    val kinds = setOf(ResourceKind.BOOK, ResourceKind.COURSE, ResourceKind.LICHESS_STUDY)
    const val STUDY_ROW_LIMIT = 4
}

/**
 * Every resource of one kind (iOS `BookShelfScreen` and `LichessStudiesScreen`), opened from its
 * header on the Resources tab, ignoring the tab's search and filters as iOS does. Books and courses
 * sit three to a row on shelf ledges over the brand glow; studies, which have no covers, are the
 * full list in the library's order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceShelfScreen(viewModel: ResourcesViewModel, kindCode: String, onOpen: (UUID) -> Unit, onBack: () -> Unit) {
    val resources by viewModel.resources.collectAsStateWithLifecycle()
    val ofKind = resources.orEmpty().filter { it.kind.code == kindCode }
    val isStudies = kindCode == ResourceKind.LICHESS_STUDY
    Box(Modifier.fillMaxSize()) {
        if (isStudies) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) else GradientBackground()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(kindPluralRes(kindCode))) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            if (isStudies) {
                LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
                    items(ofKind.sortedWith(studyOrder), key = { it.id }) { StudyRow(it) { onOpen(it.id) } }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 24.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    items(ofKind, key = { it.id }) { ShelfBook(it) { onOpen(it.id) } }
                }
            }
        }
    }
}

/** A cover standing on its ledge, with the library's badges; the title is read out, not shown. */
@Composable
private fun ShelfBook(resource: Resource, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).semantics { contentDescription = resource.title }) {
        Box {
            ResourceCover(
                resource,
                Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
            )
            ResourceBadges(resource, Modifier.padding(4.dp))
        }
        // iOS's ledge: a soft accent bar rather than a wood texture.
        val accent = Palette.accent
        Box(
            Modifier.fillMaxWidth().height(5.dp).shadow(4.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.4f), accent.copy(alpha = 0.05f)))),
        )
    }
}
