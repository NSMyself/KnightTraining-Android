package com.knightindustries.knighttraining.feature.resources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceCompletion
import com.knightindustries.knighttraining.core.store.completions
import com.knightindustries.knighttraining.core.store.deleteResource
import com.knightindustries.knighttraining.core.store.markResourceFinished
import com.knightindustries.knighttraining.core.store.observeResource
import com.knightindustries.knighttraining.core.store.restartResource
import com.knightindustries.knighttraining.core.store.saveResource
import com.knightindustries.knighttraining.core.store.setResourceProgress
import com.knightindustries.knighttraining.core.store.timeInvested
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** One resource, observed, with its finished history and tracked time (iOS `ResourceProgressViewModel`). */
class ResourceDetailViewModel(private val container: AppContainer, private val id: UUID) : ViewModel() {
    private val store = container.store
    var resource by mutableStateOf<Resource?>(null)
        private set
    var completions by mutableStateOf<List<ResourceCompletion>>(emptyList())
        private set
    var timeInvestedSeconds by mutableIntStateOf(0)
        private set
    var isDeleted by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            store.observeResource(id).collect { latest ->
                resource = latest
                if (latest == null) return@collect
                completions = store.completions(id)
                timeInvestedSeconds = store.timeInvested(id)
            }
        }
    }

    /** The slider: clamps, refreshes recency, never counts a finish however often it hits 100. */
    fun setProgress(percentage: Int) = launch { store.setResourceProgress(id, percentage) }

    fun markFinished(notes: String?, rating: Int?) = launch { store.markResourceFinished(id, notes, rating) }

    fun restart() = launch { store.restartResource(id) }

    /** Notes are metadata: recency stays where it was. */
    fun saveNotes(notes: String) {
        val current = resource ?: return
        val cleaned = notes.trim().ifEmpty { null }
        if (cleaned != current.notes) launch { store.saveResource(current.withNotes(cleaned)) }
    }

    /** Details are metadata too; changing the type keeps progress and history. */
    fun saveDetails(updated: Resource) = launch { store.saveResource(updated.copy(updatedAt = Instant.now(), lastAccessedAt = resource?.lastAccessedAt)) }

    fun delete() = launch {
        store.deleteResource(id)
        isDeleted = true
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
