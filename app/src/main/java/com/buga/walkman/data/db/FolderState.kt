package com.buga.walkman.data.db

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FolderState {

    private val _folder = MutableStateFlow<List<SelectedFolder>>(emptyList())
    val folder: StateFlow<List<SelectedFolder>> = _folder.asStateFlow()

    val current: List<SelectedFolder>
        get() = _folder.value

    val absolutePaths: List<String>
        get() = _folder.value.mapNotNull { it.absolutePath }

    fun set(folders: List<SelectedFolder>) {
        _folder.value = folders
    }
}