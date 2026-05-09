package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.core.net.toUri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.viewmodel.AppUpdateStatus
import androidx.compose.material3.Text

@Composable
fun AboutScreen(
    appUpdateStatus: AppUpdateStatus,
    appUpdateCheckEnabled: Boolean,
    onAppUpdateCheckEnabledChange: (Boolean) -> Unit,
    onDownloadUpdateClick: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val updateAvailable = appUpdateStatus as? AppUpdateStatus.UpdateAvailable

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.loc_74350de7),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ibis),
                        contentDescription = stringResource(R.string.welcome_logo_content_description),
                        modifier = Modifier.size(132.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.loc_c434ec51),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BitcoinOrange,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (updateAvailable != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.drawer_update_available,
                                    updateAvailable.latestVersionName,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BitcoinOrange,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onDownloadUpdateClick(updateAvailable.releaseUrl) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier =
                            Modifier
                                .clickable {
                                    val intent =
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://github.com/aeonbtc/IbisWallet".toUri(),
                                        )
                                    context.startActivity(intent)
                                }
                                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub",
                            tint = BitcoinOrange,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.loc_2bb3e26c),
                            style = MaterialTheme.typography.bodyLarge,
                            color = BitcoinOrange,
                        )
                    }
                    Text(
                        text = stringResource(R.string.loc_98b3744d),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAppUpdateCheckEnabledChange(!appUpdateCheckEnabled) }
                            .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = appUpdateCheckEnabled,
                        onCheckedChange = { onAppUpdateCheckEnabledChange(it) },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = BitcoinOrange,
                            ),
                    )

                    Text(
                        text = stringResource(R.string.app_update_check_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}
