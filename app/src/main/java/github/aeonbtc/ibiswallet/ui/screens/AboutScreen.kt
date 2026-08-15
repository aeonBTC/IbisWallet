package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.viewmodel.AppUpdateStatus
import kotlinx.coroutines.delay

const val DONATE_BITCOIN_ADDRESS = "bc1qk54j45l8s20z6glxnt5zuk7efq2qsjj9n44wc8"

@Composable
fun AboutScreen(
    appUpdateStatus: AppUpdateStatus,
    appUpdateCheckEnabled: Boolean,
    onAppUpdateCheckEnabledChange: (Boolean) -> Unit,
    onDownloadUpdateClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val updateAvailable = appUpdateStatus as? AppUpdateStatus.UpdateAvailable
    var showDonateDialog by remember { mutableStateOf(false) }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onDonateClick = {
                showDonateDialog = false
                onDonateClick()
            },
        )
    }

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

                    Spacer(modifier = Modifier.height(20.dp))

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

                Button(
                    onClick = { showDonateDialog = true },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.loc_7a3f1c9e),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DonateDialog(
    onDismiss: () -> Unit,
    onDonateClick: () -> Unit,
) {
    val context = LocalContext.current
    val qrBitmap = remember { generateQrBitmap(DONATE_BITCOIN_ADDRESS) }
    var showCopied by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(3000)
            showCopied = false
        }
    }

    if (showEnlargedQr && qrBitmap != null) {
        Dialog(
            onDismissRequest = { showEnlargedQr = false },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { showEnlargedQr = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(16.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.loc_7a3f1c9e),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.loc_e1041b50),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    ScrollableDialogSurface(
        onDismissRequest = onDismiss,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier.widthIn(min = 84.dp),
                    activeColor = TextSecondary,
                ) {
                    Text(
                        text = stringResource(R.string.loc_d2c0aec0),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.widthIn(min = 12.dp))

                Button(
                    onClick = onDonateClick,
                    modifier = Modifier.widthIn(min = 84.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.loc_7a3f1c9e),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.loc_7a3f1c9e),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )

            if (qrBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier =
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { showEnlargedQr = true }
                            .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.loc_7a3f1c9e),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = formatChunkedAddress(DONATE_BITCOIN_ADDRESS),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            SecureClipboard.copyAndScheduleClear(
                                context,
                                DONATE_BITCOIN_ADDRESS,
                            )
                            showCopied = true
                        }
                        .padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.loc_3c19e32e),
                    tint = if (showCopied) BitcoinOrange else TextSecondary,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clickable {
                                SecureClipboard.copyAndScheduleClear(
                                    context,
                                    DONATE_BITCOIN_ADDRESS,
                                )
                                showCopied = true
                            },
                )
            }

            if (showCopied) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loc_e287255d),
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange,
                )
            }
        }
    }
}
