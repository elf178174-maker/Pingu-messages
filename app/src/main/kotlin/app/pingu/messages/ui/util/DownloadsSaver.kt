package app.pingu.messages.ui.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.platform.permission.AppPermissions
import app.pingu.messages.platform.permission.PermissionGroup

/**
 * Saving attachments into the public Downloads folder, asking for the permission that needs on the
 * versions that need one.
 *
 * Android 10 replaced the storage permission with MediaStore, so on anything modern the returned
 * lambda calls straight through and the user never sees a dialog. Only Android 9 and below prompt,
 * and only at the moment the user asks for the file to be saved.
 */
@Composable
fun rememberDownloadsSaver(onSave: (List<Attachment>) -> Unit): (List<Attachment>) -> Unit {
    val context = LocalContext.current
    val permissions = remember(context) { AppPermissions(context) }
    val pending = remember { mutableStateOf<List<Attachment>>(emptyList()) }
    val currentOnSave by rememberUpdatedState(onSave)

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val attachments = pending.value
        pending.value = emptyList()
        if (attachments.isNotEmpty() && granted.values.all { it }) currentOnSave(attachments)
    }

    return { attachments ->
        if (attachments.isNotEmpty()) {
            val missing = permissions.missing(PermissionGroup.SAVE_TO_DOWNLOADS)
            if (missing.isEmpty()) {
                currentOnSave(attachments)
            } else {
                pending.value = attachments
                request.launch(missing.toTypedArray())
            }
        }
    }
}
