package com.buga.walkman.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buga.walkman.data.db.AppDatabase
import com.buga.walkman.data.db.FolderResolver
import com.buga.walkman.data.db.FolderState
import com.buga.walkman.data.db.SelectedFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(getApplication())

    private val _folder = MutableStateFlow<SelectedFolder?>(null)
    val folder: StateFlow<SelectedFolder?> = _folder.asStateFlow()

    private val _folderReady = MutableStateFlow(false)
    val folderReady: StateFlow<Boolean> = _folderReady.asStateFlow()

    init {
        viewModelScope.launch {
            database.folderDao().observeFolder().collect { saved ->
                _folder.value = saved
                FolderState.set(saved)
                _folderReady.value = true
            }
        }
    }

    fun pickFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val folder = FolderResolver.resolve(context, uri) ?: return
        viewModelScope.launch {
            database.folderDao().save(folder)
        }
        FolderState.set(folder)
    }

    fun clearFolder() {
        viewModelScope.launch {
            database.folderDao().clear()
        }
        FolderState.set(null)
    }
}