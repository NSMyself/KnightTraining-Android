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
import coil3.request.allowHardware
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

/**
 * A cover's accent (iOS `CoverAccent`): its average color, saturation lifted to at least 0.45 and
 * brightness held to 0.4–0.75 so a washed-out or near-black cover still tints. `null` without a
 * cover or when it fails to load.
 */
suspend fun coverAccent(context: Context, url: String?): androidx.compose.ui.graphics.Color? {
    if (url == null) return null
    val request = coil3.request.ImageRequest.Builder(context).data(url).size(64).allowHardware(false).build()
    val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult ?: return null
    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap ?: return null
    val average = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(average, hsv)
    hsv[1] = maxOf(hsv[1], 0.45f)
    hsv[2] = hsv[2].coerceIn(0.4f, 0.75f)
    return androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(hsv))
}
