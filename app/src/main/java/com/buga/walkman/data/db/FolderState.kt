package com.buga.walkman.data.db

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FolderState {

    private val _folder = MutableStateFlow<SelectedFolder?>(null)
    val folder: StateFlow<SelectedFolder?> = _folder.asStateFlow()

    val current: SelectedFolder?
        get() = _folder.value

    val absolutePath: String?
        get() = _folder.value?.absolutePath

    fun set(folder: SelectedFolder?) {
        _folder.value = folder
    }
}