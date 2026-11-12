package com.knightindustries.knighttraining.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * An extended FAB built with the content slot. The `icon`/`text` overload left the label out of
 * the semantics tree (found by `CoreLoopTest`): TalkBack read an unlabelled button.
 */
@Composable
fun LabeledFab(icon: ImageVector, label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
