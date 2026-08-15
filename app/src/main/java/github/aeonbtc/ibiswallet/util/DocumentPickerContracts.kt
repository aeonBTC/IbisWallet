package github.aeonbtc.ibiswallet.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Document pickers that prefer the public Downloads tree as the initial location.
 *
 * Default [ActivityResultContracts.OpenDocument] / [CreateDocument] reuse the system’s last
 * browsed SAF folder — often the Ark auto-backup tree after choosing that folder — which is
 * wrong for full Ibis JSON backup export/restore.
 */
object DocumentPickerContracts {
    /**
     * Best-effort URI for primary volume Downloads (DocumentsUI [EXTRA_INITIAL_URI]).
     * Providers may ignore this; still better than inheriting the last Ark folder.
     */
    fun downloadsInitialUri(): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOWNLOADS}",
        )

    private fun Intent.applyDownloadsInitialUri(): Intent {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsInitialUri())
        return this
    }

    class CreateJsonInDownloads : ActivityResultContracts.CreateDocument("application/json") {
        override fun createIntent(
            context: Context,
            input: String,
        ): Intent = super.createIntent(context, input).applyDownloadsInitialUri()
    }

    class OpenJsonFromDownloads : ActivityResultContracts.OpenDocument() {
        override fun createIntent(
            context: Context,
            input: Array<String>,
        ): Intent = super.createIntent(context, input).applyDownloadsInitialUri()
    }
}
