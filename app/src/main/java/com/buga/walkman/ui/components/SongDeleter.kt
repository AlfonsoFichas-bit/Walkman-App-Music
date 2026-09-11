package com.buga.walkman.ui.components

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buga.walkman.model.Song
import com.buga.walkman.viewmodel.PlayerControllerViewModel

@Composable
fun rememberSongDeleter(playerViewModel: PlayerControllerViewModel): (Song) -> Unit {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Song?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = pendingDelete
        pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK && song != null) {
            playerViewModel.onSongDeleted(song)
        }
    }
    return remember(context, playerViewModel) {
        { song: Song ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(song.uri)
                    )
                    pendingDelete = song
                    launcher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                } catch (_: Exception) {
                    playerViewModel.deleteSong(song)
                }
            } else {
                playerViewModel.deleteSong(song)
            }
        }
    }
}