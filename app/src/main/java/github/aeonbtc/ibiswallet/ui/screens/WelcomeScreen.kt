package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.localization.ProvideLocalizedResources
import github.aeonbtc.ibiswallet.ui.components.BalanceDateFormatDropdown
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.LanguageDropdown
import github.aeonbtc.ibiswallet.ui.components.TypefaceDropdown
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

@Composable
fun WelcomeDialog(
    currentAppLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit,
    currentBalanceDateFormat: String,
    onBalanceDateFormatChange: (String) -> Unit,
    currentTypeface: String,
    onTypefaceChange: (String) -> Unit,
    initialAppUpdateCheckEnabled: Boolean = false,
    onDismiss: (Boolean) -> Unit,
) {
    var appUpdateCheckEnabled by rememberSaveable {
        mutableStateOf(initialAppUpdateCheckEnabled)
    }

    Dialog(
        onDismissRequest = { onDismiss(appUpdateCheckEnabled) },
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        ProvideLocalizedResources {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ibis),
                        contentDescription = stringResource(R.string.welcome_logo_content_description),
                        modifier = Modifier.size(96.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BitcoinOrange,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.welcome_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.welcome_menu_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, BorderColor),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WelcomePreferenceRow(label = stringResource(R.string.settings_language_title)) {
                                LanguageDropdown(
                                    currentLocale = currentAppLocale,
                                    onLocaleSelected = onAppLocaleChange,
                                    dense = true,
                                )
                            }
                            WelcomePreferenceRow(label = stringResource(R.string.settings_balance_date_format_title)) {
                                BalanceDateFormatDropdown(
                                    currentFormat = currentBalanceDateFormat,
                                    onFormatSelected = onBalanceDateFormatChange,
                                    dense = true,
                                )
                            }
                            WelcomePreferenceRow(label = stringResource(R.string.settings_typeface_title)) {
                                TypefaceDropdown(
                                    currentTypeface = currentTypeface,
                                    onTypefaceSelected = onTypefaceChange,
                                    dense = true,
                                )
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { appUpdateCheckEnabled = !appUpdateCheckEnabled },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = appUpdateCheckEnabled,
                                    onCheckedChange = { appUpdateCheckEnabled = it },
                                    modifier = Modifier.size(32.dp),
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = BitcoinOrange,
                                        ),
                                )
                                Text(
                                    text = stringResource(R.string.app_update_check_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    IbisButton(
                        onClick = { onDismiss(appUpdateCheckEnabled) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_continue),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePreferenceRow(
    label: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.30f),
        )
        Column(modifier = Modifier.weight(0.70f)) {
            control()
        }
    }
}
