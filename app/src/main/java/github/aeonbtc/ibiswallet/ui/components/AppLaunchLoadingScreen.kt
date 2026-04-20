package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import kotlin.random.Random

private object LaunchArtworkPool {
    private val shuffledArtworks by lazy {
        listOf(
            R.drawable.loading1,
            R.drawable.loading2,
        ).shuffled(Random(System.currentTimeMillis()))
    }

    val currentArtworkResId: Int
        get() = shuffledArtworks.first()
}

@Composable
fun AppLaunchLoadingScreen(
    modifier: Modifier = Modifier,
    showVersion: Boolean = true,
) {
    val artworkResId = remember { LaunchArtworkPool.currentArtworkResId }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = artworkResId),
                contentDescription = stringResource(R.string.startup_loading_artwork),
                modifier = Modifier.fillMaxWidth(0.78f),
            )

            if (showVersion) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
        }
    }
}
