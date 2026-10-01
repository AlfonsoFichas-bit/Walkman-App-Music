package com.buga.walkman.ui.components

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buga.walkman.data.CoverStore
import com.buga.walkman.model.Song
import com.buga.walkman.viewmodel.PlayerControllerViewModel

/**
 * Handles the "edit song metadata + cover" flow as a full-screen view. On API 30+
 * requests a per-item write grant from the system via [MediaStore.createWriteRequest]
 * before applying metadata changes. Custom covers are stored in app-private storage
 * via [CoverStore] and need no extra permission.
 */
@Stable
class SongMetadataEditorState(
    private val context: Context,
    private val playerViewModel: PlayerControllerViewModel
) {
    internal var writeLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    internal var pickLauncher: ActivityResultLauncher<String>? = null

    var song by mutableStateOf<Song?>(null)
        private set
    var coverCandidate by mutableStateOf<Uri?>(null)
        private set

    private var pendingEdit: PendingEdit? = null
    private var pendingRestore: Song? = null

    fun requestEdit(current: Song) {
        song = current
        coverCandidate = null
    }

    fun dismiss() {
        song = null
        coverCandidate = null
        pendingEdit = null
        pendingRestore = null
    }

    fun pickCover() {
        pickLauncher?.launch("image/*")
    }

    internal fun onCoverPicked(uri: Uri?) {
        coverCandidate = uri
    }

    fun hasCustomCover(current: Song): Boolean = CoverStore.has(context, current.id)

    fun save(title: String, artist: String, album: String) {
        val current = song ?: return
        val cover = coverCandidate
        val metadataChanged = title.trim() != current.title ||
            artist.trim() != current.artist ||
            album.trim() != current.album
        song = null
        coverCandidate = null
        if (!metadataChanged && cover == null) return
        if ((metadataChanged || cover != null) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingEdit = PendingEdit(current, title, artist, album, cover)
                val intentSender = MediaStore.createWriteRequest(
                    context.contentResolver,
                    listOf(current.uri)
                )
                writeLauncher?.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (_: Exception) {
                pendingEdit = null
                playerViewModel.updateSongMetadata(current, title, artist, album, cover)
            }
        } else {
            playerViewModel.updateSongMetadata(current, title, artist, album, cover)
        }
    }

    fun restoreCover(current: Song) {
        song = null
        coverCandidate = null
        if (playerViewModel.hasOriginalCover(current) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            try {
                pendingRestore = current
                val intentSender = MediaStore.createWriteRequest(
                    context.contentResolver,
                    listOf(current.uri)
                )
                writeLauncher?.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (_: Exception) {
                pendingRestore = null
                playerViewModel.restoreSongCover(current)
            }
        } else {
            playerViewModel.restoreSongCover(current)
        }
    }

    internal fun onWriteResult(resultCode: Int) {
        val edit = pendingEdit
        pendingEdit = null
        if (edit != null) {
            if (resultCode == Activity.RESULT_OK) {
                playerViewModel.updateSongMetadata(
                    edit.song,
                    edit.title,
                    edit.artist,
                    edit.album,
                    edit.cover
                )
            }
            return
        }
        val restore = pendingRestore
        pendingRestore = null
        if (resultCode == Activity.RESULT_OK && restore != null) {
            playerViewModel.restoreSongCover(restore)
        }
    }

    private data class PendingEdit(
        val song: Song,
        val title: String,
        val artist: String,
        val album: String,
        val cover: Uri? = null
    )
}

@Composable
fun rememberSongMetadataEditor(playerViewModel: PlayerControllerViewModel): SongMetadataEditorState {
    val context = LocalContext.current
    val editor = remember(context, playerViewModel) {
        SongMetadataEditorState(context, playerViewModel)
    }
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        editor.onWriteResult(result.resultCode)
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        editor.onCoverPicked(uri)
    }
    SideEffect {
        editor.writeLauncher = writeLauncher
        editor.pickLauncher = pickLauncher
    }
    return editor
}