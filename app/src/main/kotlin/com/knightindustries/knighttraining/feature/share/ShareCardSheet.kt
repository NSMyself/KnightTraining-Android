package com.knightindustries.knighttraining.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.knightindustries.knighttraining.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ShareToggle(val title: String, val isOn: Boolean, val onChange: (Boolean) -> Unit)

/**
 * A share card's sheet (iOS `ShareCardSheet`): what it's for, the toggles, a live preview, and
 * Share, which renders exactly the preview to a PNG and hands it to the system share sheet. Nothing
 * leaves the device until the user picks a target there.
 *
 * @param card `null` while loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(
    title: String,
    body: String,
    toggles: List<ShareToggle>,
    note: String? = null,
    failed: Boolean = false,
    onDismiss: () -> Unit,
    card: (@Composable () -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            toggles.forEach { toggle ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(toggle.title, modifier = Modifier.weight(1f))
                    Switch(checked = toggle.isOn, onCheckedChange = toggle.onChange)
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp)) }
            when {
                failed -> Text(stringResource(R.string.share_failed), color = MaterialTheme.colorScheme.error)
                card == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            },
                        ) { card() }
                    }
                    Button(
                        onClick = { scope.launch { share(context, layer.toImageBitmap().asAndroidBitmap()) } },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Text(stringResource(R.string.share), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Writes the card to the share cache (one file, replaced each time) and opens the system share sheet. */
private suspend fun share(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        // Written aside and renamed, so the share target never reads a half-written image.
        val partial = dir.resolve("knight-training.png.partial")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.resolve("knight-training.png").also { partial.renameTo(it) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
