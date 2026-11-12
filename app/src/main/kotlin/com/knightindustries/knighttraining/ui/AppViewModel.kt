package com.knightindustries.knighttraining.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightindustries.knighttraining.AppContainer

/** A screen's ViewModel, built from the app container. Scoped to its navigation entry. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(key = key) { create(AppContainer.from(application)) }
}
