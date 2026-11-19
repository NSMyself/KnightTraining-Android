package com.knightindustries.knighttraining.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.designsystem.GradientBackground
import com.knightindustries.knighttraining.feedback.FeedbackEvent
import com.knightindustries.knighttraining.feedback.rememberFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

private sealed interface Art {
    data class Picture(val drawable: Int) : Art
    data object Target : Art
}

private data class Page(val title: Int, val subtitle: Int, val detail: Int, val art: Art)

/** iOS's onboarding carousel: brand, weekly plan, commit once, the target is a floor. */
private val pages = listOf(
    Page(R.string.app_name, R.string.onboarding_brand_subtitle, R.string.onboarding_brand_detail, Art.Picture(R.drawable.onboarding_logo)),
    Page(R.string.onboarding_plan_title, R.string.onboarding_plan_body, R.string.onboarding_plan_detail, Art.Picture(R.drawable.onboarding_study)),
    Page(R.string.onboarding_commit_title, R.string.onboarding_commit_body, R.string.onboarding_commit_detail, Art.Picture(R.drawable.onboarding_treadmill)),
    Page(R.string.onboarding_floor_title, R.string.onboarding_floor_body, R.string.onboarding_floor_detail, Art.Target),
)

/**
 * The intro (iOS `OnboardingScreen`): four pages on the brand glow, each with its artwork, then a
 * Lao Tzu quote before the app. "Let's go" grows a white circle out of itself to cover the
 * carousel and reveal the quote; Skip, on the middle pages only, crossfades to the same quote.
 * [onFinished] marks onboarding complete at the very end, never the start, so quitting halfway
 * shows it again.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var showsQuote by rememberSaveable { mutableStateOf(false) }
    // Where "Let's go" was tapped: the reveal's center.
    var revealFrom by remember { mutableStateOf<Offset?>(null) }

    // A Surface, so text takes the theme's content color; outside one it defaults to black.
    Surface(color = MaterialTheme.colorScheme.background) {
        AnimatedContent(showsQuote, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "quote") { quote ->
            if (quote) QuoteScreen(onFinished) else Carousel(onSkip = { showsQuote = true }, onLetsGo = { revealFrom = it })
        }
        revealFrom?.let { origin -> CircleReveal(origin, onCovered = { showsQuote = true }, done = showsQuote) }
    }
}

@Composable
private fun Carousel(onSkip: () -> Unit, onLetsGo: (Offset) -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedback()
    val isLast = pager.currentPage == pages.lastIndex
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize()) {
        GradientBackground()
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
                if (pager.currentPage in 1 until pages.lastIndex) {
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { index -> PageContent(pages[index], stagesText = index == 0) }
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
                onClick = {
                    if (isLast) {
                        feedback(FeedbackEvent.OnboardingFinished)
                        onLetsGo(buttonCenter)
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { buttonCenter = it.boundsInRoot().center },
            ) { Text(stringResource(if (isLast) R.string.lets_go else R.string.next)) }
        }
    }
}

/**
 * Title, artwork, subtitle and detail. The first page's subtitle and detail drift in after the
 * title (iOS: 1.05 s, then 0.75 s apart), the first time only.
 */
@Composable
private fun PageContent(page: Page, stagesText: Boolean) {
    var staged by rememberSaveable { mutableStateOf(!stagesText) }
    val subtitle = remember { Animatable(if (staged) 1f else 0f) }
    val detail = remember { Animatable(if (staged) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (staged) return@LaunchedEffect
        delay(1_050)
        launch { subtitle.animateTo(1f, tween(750, easing = EaseIn)) }
        delay(750)
        detail.animateTo(1f, tween(1_000, easing = EaseIn))
        staged = true
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(0.6f))
        val art = Modifier.fillMaxWidth(0.5f)
        when (page.art) {
            is Art.Picture -> Image(painterResource(page.art.drawable), contentDescription = null, modifier = art)
            Art.Target -> Icon(Icons.Rounded.TrackChanges, contentDescription = null, modifier = art.height(160.dp), tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(page.subtitle),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = subtitle.value },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(page.detail),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.graphicsLayer { alpha = detail.value },
        )
        Spacer(Modifier.weight(0.6f))
    }
}

/**
 * A white circle growing from [origin] until it covers the screen (iOS `OverlayView`), then
 * [onCovered] swaps the quote in beneath it, and it fades once the quote is there ([done]).
 */
@Composable
private fun CircleReveal(origin: Offset, onCovered: () -> Unit, done: Boolean) {
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(600, easing = EaseInOutCubic))
        onCovered()
    }
    LaunchedEffect(done) { if (done) alpha.animateTo(0f, tween(300)) }
    Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        // Far enough to reach the farthest corner from where it starts.
        val reach = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { hypot(it.x - origin.x, it.y - origin.y) }
        drawCircle(Color.White, radius = reach * progress.value, center = origin)
    }
}

/** "The journey of a thousand miles…": fades in over 0.5 s, holds, fades out, then the app (iOS `QuoteScreen`). */
@Composable
private fun QuoteScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(500, easing = EaseIn))
        delay(2_500)
        alpha.animateTo(0f, tween(700, easing = EaseOut))
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp).graphicsLayer { this.alpha = alpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.onboarding_quote), style = MaterialTheme.typography.headlineSmall, color = Color.Black, textAlign = TextAlign.Center)
            Text(stringResource(R.string.onboarding_quote_author), style = MaterialTheme.typography.titleSmall, color = Color.Black.copy(alpha = 0.6f))
        }
    }
}
