package com.buga.walkman.data.db

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object FolderResolver {

    fun resolve(context: Context, treeUri: Uri): SelectedFolder? {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val documentId = doc.uri.lastPathSegment ?: return null
        val displayName = doc.name?.takeIf { it.isNotBlank() }
            ?: documentId.substringAfterLast('/').substringAfter(':')

        return when {
            documentId.startsWith("primary:") -> {
                val relative = documentId.removePrefix("primary:")
                SelectedFolder(
                    treeUri = treeUri.toString(),
                    displayName = displayName,
                    relativePath = relative,
                    absolutePath = "/storage/emulated/0/$relative"
                )
            }
            documentId.contains(':') -> {
                SelectedFolder(
                    treeUri = treeUri.toString(),
                    displayName = displayName,
                    relativePath = null,
                    absolutePath = "/storage/${documentId.replace(':', '/')}"
                )
            }
            else -> null
        }
    }
}