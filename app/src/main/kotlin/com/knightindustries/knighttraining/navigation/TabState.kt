package com.knightindustries.knighttraining.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.navigation3.runtime.NavEntryDecorator

/** Entry metadata marking a tab's root, whose state outlives leaving the back stack. */
val TabRootMetadata: Map<String, Any> = mapOf("tabRoot" to true)

/**
 * Nav 3's saveable-state decorator, except a tab's root keeps its state when it leaves the back
 * stack (iOS keeps every tab alive): switching away from Resources and back finds its search,
 * filters and scroll where they were. Everything above a root still forgets on pop, as usual.
 * Tabs can only be switched from a root (the bar hides elsewhere), so a root is all a tab has.
 */
@Composable
fun <T : Any> rememberTabKeepingStateDecorator(): NavEntryDecorator<T> {
    val holder = rememberSaveableStateHolder()
    return remember(holder) {
        // Content keys of tab roots, learned as they're shown (an entry is shown before it's popped).
        val kept = mutableSetOf<Any>()
        NavEntryDecorator(
            onPop = { contentKey -> if (contentKey !in kept) holder.removeState(contentKey) },
            decorate = { entry ->
                if (entry.metadata["tabRoot"] == true) kept += entry.contentKey
                holder.SaveableStateProvider(entry.contentKey) { entry.Content() }
            },
        )
    }
}
