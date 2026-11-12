package com.knightindustries.knighttraining.feature.resources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.net.EnrichedLink
import com.knightindustries.knighttraining.core.store.addCatalogCourse
import com.knightindustries.knighttraining.core.store.catalogCourses
import com.knightindustries.knighttraining.core.store.importedCatalogCourseIds
import com.knightindustries.knighttraining.core.store.saveResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Adding a resource (iOS `AddResourceSheet`): by hand, with a pasted link filling in what it can
 * (never overwriting what the user typed), or from the catalog. Nothing here ever blocks a manual
 * add: a failed lookup just leaves the fields as they are.
 */
class AddResourceViewModel(private val container: AppContainer) : ViewModel() {
    var url by mutableStateOf("")
        private set
    var kind by mutableStateOf(ResourceKind.BOOK)
    var title by mutableStateOf("")
    var author by mutableStateOf("")
    var isLookingUp by mutableStateOf(false)
        private set
    private var kindPickedByUser = false
    private var enriched: EnrichedLink? = null
    private var lookup: Job? = null

    var courses by mutableStateOf<List<CatalogCourse>?>(null)
        private set

    fun pickKind(code: String) {
        kind = code
        kindPickedByUser = true
    }

    /** Looks the link up once typing pauses. */
    fun updateUrl(value: String) {
        url = value
        lookup?.cancel()
        enriched = null
        val trimmed = value.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return
        lookup = viewModelScope.launch {
            delay(600)
            isLookingUp = true
            try {
                val result = container.enricher.enrich(trimmed)
                enriched = result
                if (!kindPickedByUser) kind = result.kindCode
                if (title.isBlank()) result.title?.let { title = it }
                if (author.isBlank()) result.author?.let { author = it }
            } finally {
                isLookingUp = false
            }
        }
    }

    val canSave: Boolean get() = title.isNotBlank()

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        val extra = enriched
        viewModelScope.launch {
            val now = Instant.now()
            container.store.saveResource(
                Resource(
                    kind = container.store.resourceKind(kind)!!,
                    title = title.trim(),
                    author = author.trim().ifEmpty { null },
                    url = url.trim().ifEmpty { null },
                    // The lookup's extras ride along silently; the full edit sheet shows them later.
                    imageUrl = extra?.imageUrl,
                    description = extra?.description,
                    publisher = extra?.publisher,
                    publicationDate = extra?.publicationDate,
                    createdAt = now,
                    updatedAt = now,
                    lastAccessedAt = null,
                ),
            )
            onSaved()
        }
    }

    /** The catalog minus what's already in the library; synced first in case launch hasn't yet. */
    fun loadCatalog() {
        viewModelScope.launch {
            courses = available()
            if (container.catalogSync.syncIfNeeded()) courses = available()
        }
    }

    private suspend fun available(): List<CatalogCourse> {
        val imported = container.store.importedCatalogCourseIds()
        return container.store.catalogCourses().filterNot { it.id in imported }
    }

    fun addCourse(course: CatalogCourse, onAdded: () -> Unit) {
        viewModelScope.launch {
            container.store.addCatalogCourse(course)
            onAdded()
        }
    }
}
