package com.example.techyfipos.utils

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RepositoryErrorBus {
    private val _errors = MutableSharedFlow<Throwable>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errors: SharedFlow<Throwable> = _errors.asSharedFlow()

    fun report(e: Throwable) {
        try {
            Log.e("RepositoryErrorBus", "Repository write error: ${e.message}", e)
        } catch (_: Throwable) {
            System.err.println("RepositoryErrorBus: ${e.message}")
        }
        _errors.tryEmit(e)
    }

    val handler = CoroutineExceptionHandler { _, throwable ->
        report(throwable)
    }
}
