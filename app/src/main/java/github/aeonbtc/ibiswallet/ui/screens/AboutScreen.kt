package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

@Composable
fun AboutScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Centered content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ibis icon
            Icon(
                painter = painterResource(id = R.drawable.ic_ibis),
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ibis Wallet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BitcoinOrange
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // GitHub link
            Row(
                modifier = Modifier
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/aeonbtc/IbisWallet")
                        )
                        context.startActivity(intent)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    tint = BitcoinOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Source Code",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BitcoinOrange
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Report bugs and request features on GitHub",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
