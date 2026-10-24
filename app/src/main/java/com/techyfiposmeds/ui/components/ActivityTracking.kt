package com.techyfiposmeds.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.techyfiposmeds.ui.viewmodel.AuthViewModel

/**
 * Shared modifier to record user interaction in dialogs and bottom sheets so that long form entries
 * in separate window hierarchies do not get prematurely locked by the idle-lock timer.
 */
fun Modifier.trackUserActivity(): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                AuthViewModel.recordUserActivity()
            }
        }
    }
