package github.aeonbtc.ibiswallet

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class LaunchActivity : ComponentActivity() {
    private var hasForwardedToMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.post(::launchMainActivity)
    }

    private fun launchMainActivity() {
        if (hasForwardedToMain) {
            return
        }
        hasForwardedToMain = true

        val sourceIntent = intent
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = sourceIntent?.action
                data = sourceIntent?.data
                sourceIntent?.categories?.forEach(::addCategory)
                sourceIntent?.extras?.let(::putExtras)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle(),
        )
        finish()
    }
}
