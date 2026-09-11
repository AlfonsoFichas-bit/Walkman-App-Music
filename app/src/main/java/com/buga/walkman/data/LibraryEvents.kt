package com.buga.walkman.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object LibraryEvents {
    private val _reload = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reload: SharedFlow<Unit> = _reload.asSharedFlow()

    fun requestLibraryReload() {
        _reload.tryEmit(Unit)
    }
}