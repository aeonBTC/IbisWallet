package github.aeonbtc.ibiswallet.util

import android.app.Activity
import android.content.Context
import android.content.Intent

fun Context.startActivityWithTaskFallback(intent: Intent) {
    val launchIntent =
        if (this is Activity) {
            intent
        } else {
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    startActivity(launchIntent)
}
