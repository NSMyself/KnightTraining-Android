package com.knightindustries.knighttraining.feature.resources

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.ui.kindIcon
import java.io.File
import java.util.UUID

/**
 * A resource's cover: books and courses portrait, everything else landscape, the kind's icon
 * while loading or when there's no image.
 */
@Composable
fun ResourceCover(resource: Resource, modifier: Modifier = Modifier) {
    val portrait = resource.kind.code == ResourceKind.BOOK || resource.kind.code == ResourceKind.COURSE
    Box(
        modifier
            .aspectRatio(if (portrait) 2f / 3f else 16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder: @Composable () -> Unit = {
            Icon(kindIcon(resource.kind.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        if (resource.imageUrl == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = resource.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Opens a link in a Custom Tab: the user's own browser session, so a paywalled or signed-in page
 * stays signed in (the reason iOS uses `SFSafariViewController`).
 */
fun openLink(context: Context, url: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
}

/**
 * Copies a photo the user picked into app storage and returns its `file://` URI: the picker's own
 * URI only grants access for this session.
 */
fun saveCoverPhoto(context: Context, picked: Uri): String? = runCatching {
    val dir = File(context.filesDir, "covers").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(picked)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
    Uri.fromFile(file).toString()
}.getOrNull()
