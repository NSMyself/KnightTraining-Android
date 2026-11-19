package com.knightindustries.knighttraining.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Taps on the bottom-bar item of the tab already showing. Provided to each tab's root screen; every
 * other screen gets an empty flow.
 */
val LocalTabReselections = compositionLocalOf<Flow<Unit>> { emptyFlow() }

/**
 * Re-tapping the current tab scrolls it back to the top and expands its large title, as
 * first-party Android apps do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollToTopOnReselect(list: LazyListState? = null, scroll: ScrollState? = null, topBar: TopAppBarScrollBehavior? = null) {
    val reselections = LocalTabReselections.current
    val target by rememberUpdatedState(Triple(list, scroll, topBar))
    LaunchedEffect(reselections) {
        reselections.collect {
            val (l, s, bar) = target
            bar?.state?.let { it.heightOffset = 0f; it.contentOffset = 0f }
            l?.animateScrollToItem(0)
            s?.animateScrollTo(0)
        }
    }
}
