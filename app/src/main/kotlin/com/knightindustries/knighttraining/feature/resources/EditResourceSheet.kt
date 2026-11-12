package com.knightindustries.knighttraining.feature.resources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.persistence.Presets
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.ui.kindNameRes
import com.knightindustries.knighttraining.ui.kindOrder

/**
 * Everything editable on a resource (iOS `EditResourceDetailsSheet`), the type included: a wrong
 * guess on a shared link is fixable here. Changing the type keeps progress and history.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditResourceSheet(resource: Resource, onDismiss: () -> Unit, onSave: (Resource) -> Unit) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(resource.kind.code) }
    var title by rememberSaveable { mutableStateOf(resource.title) }
    var author by rememberSaveable { mutableStateOf(resource.author.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(resource.url.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(resource.description.orEmpty()) }
    var edition by rememberSaveable { mutableStateOf(resource.edition.orEmpty()) }
    var publisher by rememberSaveable { mutableStateOf(resource.publisher.orEmpty()) }
    var owned by rememberSaveable { mutableStateOf(resource.isOwned) }
    var imageUrl by rememberSaveable { mutableStateOf(resource.imageUrl) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        picked?.let { saveCoverPhoto(context, it) }?.let { imageUrl = it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.edit), style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ResourceCover(resource.copy(kind = ResourceKind(resource.kind.id, kind), imageUrl = imageUrl), Modifier.width(80.dp))
                Column {
                    OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.choose_photo))
                    }
                    if (imageUrl != null) TextButton(onClick = { imageUrl = null }) { Text(stringResource(R.string.remove_cover)) }
                }
            }
            Text(stringResource(R.string.resource_kind), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kindOrder.forEach { code -> FilterChip(selected = kind == code, onClick = { kind = code }, label = { Text(stringResource(kindNameRes(code))) }) }
            }
            Field(title, R.string.resource_title) { title = it }
            Field(author, R.string.resource_author) { author = it }
            Field(url, R.string.resource_link) { url = it }
            Field(description, R.string.resource_description, singleLine = false) { description = it }
            if (kind == ResourceKind.BOOK) {
                Field(edition, R.string.resource_edition) { edition = it }
                Field(publisher, R.string.resource_publisher) { publisher = it }
            }
            if (kind == ResourceKind.BOOK || kind == ResourceKind.COURSE) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.resource_owned), modifier = Modifier.weight(1f))
                    Switch(checked = owned, onCheckedChange = { owned = it })
                }
            }
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val kindId = Presets.resourceKinds.first { it.code == kind }.id
                    onSave(
                        resource.copy(
                            kind = ResourceKind(kindId, kind), title = title.trim(), author = author.trim().ifEmpty { null },
                            url = url.trim().ifEmpty { null }, description = description.trim().ifEmpty { null },
                            edition = edition.trim().ifEmpty { null }, publisher = publisher.trim().ifEmpty { null },
                            isOwned = owned, imageUrl = imageUrl,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun Field(value: String, label: Int, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(stringResource(label)) }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
}
