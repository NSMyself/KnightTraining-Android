package com.knightindustries.knighttraining.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import kotlinx.coroutines.launch

private data class Page(val title: Int, val body: Int, val icon: ImageVector)

/** iOS's onboarding carousel: brand, weekly plan, commit once, the target is a floor. */
private val pages = listOf(
    Page(R.string.app_name, R.string.onboarding_brand_subtitle, Icons.Rounded.Flag),
    Page(R.string.onboarding_plan_title, R.string.onboarding_plan_body, Icons.Rounded.Extension),
    Page(R.string.onboarding_commit_title, R.string.onboarding_commit_body, Icons.Rounded.CalendarMonth),
    Page(R.string.onboarding_floor_title, R.string.onboarding_floor_body, Icons.Rounded.TrendingUp),
)

/**
 * Skip appears on the middle pages only. [onFinished] marks onboarding complete, at the end and
 * never the start, so quitting halfway shows it again.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex

    // A Surface, so text takes the theme's content color; outside one it defaults to black.
    Surface(color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
            if (pager.currentPage in 1 until pages.lastIndex) {
                TextButton(onClick = onFinished) { Text(stringResource(R.string.skip)) }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { index ->
                Box(
                    Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(
                        if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
        }
        Button(
            onClick = { if (isLast) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (isLast) R.string.get_started else R.string.next)) }
    }
    }
}
