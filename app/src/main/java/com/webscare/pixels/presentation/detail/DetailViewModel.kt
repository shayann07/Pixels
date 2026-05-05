package com.webscare.pixels.presentation.detail

import androidx.lifecycle.ViewModel

class DetailViewModel : ViewModel() {
    var isImmersiveMode = false
        private set

    fun toggleImmersiveMode() {
        isImmersiveMode = !isImmersiveMode
    }
}

