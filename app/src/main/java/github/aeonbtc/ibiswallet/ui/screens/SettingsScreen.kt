@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.BtcPriceService
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkEsploraPreset
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.ui.components.BalanceDateFormatDropdown
import github.aeonbtc.ibiswallet.ui.components.CompactDropdownField
import github.aeonbtc.ibiswallet.ui.components.DropdownOptionText
import github.aeonbtc.ibiswallet.ui.components.LanguageDropdown
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.TypefaceDropdown
import github.aeonbtc.ibiswallet.ui.theme.ArkRust
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TorPurple
import github.aeonbtc.ibiswallet.util.ServerUrlValidator
import github.aeonbtc.ibiswallet.util.WalletNotificationDeliveryState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDenomination: String = SecureStorage.DENOMINATION_BTC,
    onDenominationChange: (String) -> Unit = {},
    currentAppLocale: AppLocale = AppLocale.ENGLISH,
    onAppLocaleChange: (AppLocale) -> Unit = {},
    spendUnconfirmed: Boolean = true,
    onSpendUnconfirmedChange: (Boolean) -> Unit = {},
    rbfEnabled: Boolean = true,
    onRbfEnabledChange: (Boolean) -> Unit = {},
    requireCoinControl: Boolean = false,
    onRequireCoinControlChange: (Boolean) -> Unit = {},
    consolidateChange: Boolean = false,
    onConsolidateChangeChange: (Boolean) -> Unit = {},
    walletNotificationsEnabled: Boolean = false,
    walletNotificationDeliveryState: WalletNotificationDeliveryState =
        WalletNotificationDeliveryState.APP_DISABLED,
    onWalletNotificationsEnabledChange: (Boolean) -> Unit = {},
    foregroundConnectivityEnabled: Boolean = false,
    onForegroundConnectivityEnabledChange: (Boolean) -> Unit = {},
    nfcEnabled: Boolean = true,
    onNfcEnabledChange: (Boolean) -> Unit = {},
    hasNfcHardware: Boolean = false,
    isSystemNfcEnabled: Boolean = false,
    supportsNfcBroadcast: Boolean = false,
    currentFeeSource: String = SecureStorage.FEE_SOURCE_OFF,
    onFeeSourceChange: (String) -> Unit = {},
    customFeeSourceUrl: String = "",
    onCustomFeeSourceUrlSave: (String) -> Unit = {},
    currentPriceSource: String = SecureStorage.PRICE_SOURCE_OFF,
    onPriceSourceChange: (String) -> Unit = {},
    currentPriceCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    onPriceCurrencyChange: (String) -> Unit = {},
    historicalTxFiatEnabled: Boolean = false,
    onHistoricalTxFiatEnabledChange: (Boolean) -> Unit = {},
    currentMempoolServer: String = SecureStorage.MEMPOOL_SPACE,
    onMempoolServerChange: (String) -> Unit = {},
    customMempoolUrl: String = "",
    onCustomMempoolUrlSave: (String) -> Unit = {},
    currentSwipeMode: String = SecureStorage.SWIPE_MODE_DISABLED,
    onSwipeModeChange: (String) -> Unit = {},
    currentBalanceDateFormat: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
    onBalanceDateFormatChange: (String) -> Unit = {},
    currentThemeMode: String = SecureStorage.THEME_MODE_DARK,
    onThemeModeChange: (String) -> Unit = {},
    currentTypeface: String = SecureStorage.TYPEFACE_SYSTEM,
    onTypefaceChange: (String) -> Unit = {},
    isLiquidAvailable: Boolean = false,
    torStatus: TorStatus = TorStatus.DISCONNECTED,
    onOpenBitcoinElectrum: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.loc_1c33c293),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isSats = currentDenomination == SecureStorage.DENOMINATION_SATS
        val walletNotificationsSubtitle =
            when (walletNotificationDeliveryState) {
                WalletNotificationDeliveryState.PERMISSION_REQUIRED ->
                    stringResource(R.string.wallet_notifications_subtitle_permission_required)
                WalletNotificationDeliveryState.SYSTEM_DISABLED ->
                    stringResource(R.string.wallet_notifications_subtitle_android_blocked)
                else ->
                    stringResource(R.string.wallet_notifications_subtitle_default)
            }
        val walletNotificationsSubtitleColor =
            when (walletNotificationDeliveryState) {
                WalletNotificationDeliveryState.PERMISSION_REQUIRED,
                WalletNotificationDeliveryState.SYSTEM_DISABLED,
                -> ErrorRed
                else -> TextSecondary
            }
        val nfcSubtitle =
            when {
                !hasNfcHardware -> stringResource(R.string.loc_3e2ca137)
                !isSystemNfcEnabled -> stringResource(R.string.loc_e762ab0b)
                !supportsNfcBroadcast -> stringResource(R.string.loc_03cc7c45)
                else -> stringResource(R.string.loc_7e8f0b30)
            }

        // ── Display ──
        SettingsSectionCard(title = stringResource(R.string.settings_general_section_display)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onDenominationChange(
                                if (!isSats) {
                                    SecureStorage.DENOMINATION_SATS
                                } else {
                                    SecureStorage.DENOMINATION_BTC
                                },
                            )
                        },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.CurrencyBitcoin,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = if (isSats) stringResource(R.string.loc_33b64233) else "BTC",
                        subtitle = if (isSats) {
                            stringResource(R.string.loc_d654b827)
                        } else {
                            stringResource(R.string.loc_781eebac)
                        },
                    )
                }
                SquareToggle(
                    checked = isSats,
                    onCheckedChange = { useSats ->
                        onDenominationChange(
                            if (useSats) {
                                SecureStorage.DENOMINATION_SATS
                            } else {
                                SecureStorage.DENOMINATION_BTC
                            },
                        )
                    },
                    checkedColor = BitcoinOrange,
                    uncheckedColor = BitcoinOrange.copy(alpha = 0.18f),
                    uncheckedBorderColor = BitcoinOrange,
                    uncheckedThumbColor = BitcoinOrange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.SwapHoriz,
                label = stringResource(R.string.loc_db88d4ce),
            ) {
                SwipeModeDropdown(
                    currentMode = currentSwipeMode,
                    onModeSelected = onSwipeModeChange,
                    isLiquidAvailable = isLiquidAvailable,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.Palette,
                label = stringResource(R.string.settings_theme_title),
            ) {
                ThemeModeDropdown(
                    currentThemeMode = currentThemeMode,
                    onThemeModeSelected = onThemeModeChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.Schedule,
                label = stringResource(R.string.settings_balance_date_format_title),
            ) {
                BalanceDateFormatDropdown(
                    currentFormat = currentBalanceDateFormat,
                    onFormatSelected = onBalanceDateFormatChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.TextFields,
                label = stringResource(R.string.settings_typeface_title),
            ) {
                TypefaceDropdown(
                    currentTypeface = currentTypeface,
                    onTypefaceSelected = onTypefaceChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.Language,
                label = stringResource(R.string.settings_language_title),
            ) {
                LanguageDropdown(
                    currentLocale = currentAppLocale,
                    onLocaleSelected = onAppLocaleChange,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Transactions ──
        SettingsSectionCard(title = stringResource(R.string.settings_general_section_transactions)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSpendUnconfirmedChange(!spendUnconfirmed) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.loc_0708218f),
                        subtitle = stringResource(R.string.loc_35cb0c66),
                    )
                }
                SquareToggle(
                    checked = spendUnconfirmed,
                    onCheckedChange = onSpendUnconfirmedChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRbfEnabledChange(!rbfEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.settings_rbf_title),
                        subtitle = stringResource(R.string.settings_rbf_subtitle),
                    )
                }
                SquareToggle(
                    checked = rbfEnabled,
                    onCheckedChange = onRbfEnabledChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRequireCoinControlChange(!requireCoinControl) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.settings_require_coin_control_title),
                        subtitle = stringResource(R.string.settings_require_coin_control_subtitle),
                    )
                }
                SquareToggle(
                    checked = requireCoinControl,
                    onCheckedChange = onRequireCoinControlChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onConsolidateChangeChange(!consolidateChange) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallMerge,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.settings_consolidate_change_title),
                        subtitle = stringResource(R.string.settings_consolidate_change_subtitle),
                    )
                }
                SquareToggle(
                    checked = consolidateChange,
                    onCheckedChange = onConsolidateChangeChange,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Connectivity ──
        SettingsSectionCard(title = stringResource(R.string.settings_general_section_connectivity)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onWalletNotificationsEnabledChange(!walletNotificationsEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.wallet_notifications_title),
                        subtitle = walletNotificationsSubtitle,
                        subtitleColor = walletNotificationsSubtitleColor,
                    )
                }
                SquareToggle(
                    checked = walletNotificationsEnabled,
                    onCheckedChange = onWalletNotificationsEnabledChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onForegroundConnectivityEnabledChange(!foregroundConnectivityEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.foreground_connectivity_title),
                        subtitle = stringResource(R.string.foreground_connectivity_subtitle),
                    )
                }
                SquareToggle(
                    checked = foregroundConnectivityEnabled,
                    onCheckedChange = onForegroundConnectivityEnabledChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (hasNfcHardware) {
                                Modifier.clickable { onNfcEnabledChange(!nfcEnabled) }
                            } else {
                                Modifier
                            },
                        ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (hasNfcHardware) BitcoinOrange else TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToggleOptionText(
                        title = stringResource(R.string.loc_ccb38171),
                        subtitle = nfcSubtitle,
                        titleColor = if (hasNfcHardware) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            TextSecondary.copy(alpha = 0.4f)
                        },
                        subtitleColor = if (hasNfcHardware) TextSecondary else TextSecondary.copy(alpha = 0.4f),
                    )
                }
                SquareToggle(
                    checked = if (hasNfcHardware) nfcEnabled else false,
                    onCheckedChange = if (hasNfcHardware) onNfcEnabledChange else { _ -> },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── External Services ──
        SettingsSectionCard(title = stringResource(R.string.loc_23c9f3ad)) {
            SettingsLabeledControl(
                icon = Icons.Default.Speed,
                label = stringResource(R.string.loc_31ab2a4e),
            ) {
                FeeSourceDropdown(
                    currentSource = currentFeeSource,
                    onSourceSelected = onFeeSourceChange,
                )
            }

            if (currentFeeSource == SecureStorage.FEE_SOURCE_MEMPOOL_ONION) {
                TorStatusIndicator(
                    torStatus = torStatus,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                )
            }

            if (currentFeeSource == SecureStorage.FEE_SOURCE_CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))

                var feeUrlDraft by remember(customFeeSourceUrl) {
                    mutableStateOf(customFeeSourceUrl)
                }
                var feeUrlError by remember { mutableStateOf<String?>(null) }
                var feeUrlSaved by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(feeUrlSaved) {
                    if (feeUrlSaved != null) {
                        delay(3000)
                        feeUrlSaved = null
                    }
                }

                val isOnionUrl =
                    try {
                        java.net.URI(feeUrlDraft).host?.endsWith(".onion") == true
                    } catch (_: Exception) {
                        feeUrlDraft.endsWith(".onion")
                    }
                val torStatusColor =
                    when (torStatus) {
                        TorStatus.CONNECTED -> TorPurple
                        TorStatus.CONNECTING, TorStatus.STARTING -> TorPurple.copy(alpha = 0.6f)
                        TorStatus.ERROR -> ErrorRed
                        TorStatus.DISCONNECTED, TorStatus.STOPPING -> TextSecondary
                    }
                val torStatusText =
                    when (torStatus) {
                        TorStatus.CONNECTED -> stringResource(R.string.loc_892c0ce5)
                        TorStatus.CONNECTING -> stringResource(R.string.loc_1a2bbf31)
                        TorStatus.STARTING -> stringResource(R.string.loc_a4c47a71)
                        TorStatus.ERROR -> stringResource(R.string.loc_27d8399b)
                        TorStatus.DISCONNECTED, TorStatus.STOPPING -> stringResource(R.string.loc_d6353c61)
                    }

                CompactTextFieldWithSave(
                    value = feeUrlDraft,
                    onValueChange = {
                        feeUrlDraft = it
                        feeUrlError = null
                        feeUrlSaved = null
                    },
                    onSave = {
                        val error = ServerUrlValidator.validate(feeUrlDraft)
                        if (error != null) {
                            feeUrlError = error
                            feeUrlSaved = null
                        } else {
                            val normalizedUrl = ServerUrlValidator.normalize(feeUrlDraft)
                            feeUrlError = null
                            feeUrlDraft = normalizedUrl
                            onCustomFeeSourceUrlSave(normalizedUrl)
                            feeUrlSaved = "saved"
                        }
                    },
                    placeholder = stringResource(R.string.settings_custom_server_placeholder),
                    errorMessage = feeUrlError,
                    successMessage = feeUrlSaved,
                    torStatusText = if (isOnionUrl) torStatusText else null,
                    torStatusColor = if (isOnionUrl) torStatusColor else null,
                    modifier = Modifier.padding(start = 28.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.Language,
                label = stringResource(R.string.loc_a688468b),
            ) {
                MempoolServerDropdown(
                    currentServer = currentMempoolServer,
                    onServerSelected = onMempoolServerChange,
                )
            }

            val mempoolNeedsTor =
                currentMempoolServer == SecureStorage.MEMPOOL_ONION ||
                    (
                        currentMempoolServer == SecureStorage.MEMPOOL_CUSTOM &&
                            settingsIsOnionUrl(customMempoolUrl)
                    )
            if (mempoolNeedsTor) {
                TorStatusIndicator(
                    torStatus = torStatus,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (currentMempoolServer == SecureStorage.MEMPOOL_CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))

                var mempoolUrlDraft by remember(customMempoolUrl) {
                    mutableStateOf(customMempoolUrl)
                }
                var mempoolUrlError by remember { mutableStateOf<String?>(null) }
                var mempoolUrlSaved by remember { mutableStateOf<String?>(null) }
                val isCustomMempoolOnion = settingsIsOnionUrl(mempoolUrlDraft)
                val mempoolTorStatusColor =
                    when (torStatus) {
                        TorStatus.CONNECTED -> TorPurple
                        TorStatus.CONNECTING, TorStatus.STARTING -> TorPurple.copy(alpha = 0.6f)
                        TorStatus.ERROR -> ErrorRed
                        TorStatus.DISCONNECTED, TorStatus.STOPPING -> TextSecondary
                    }
                val mempoolTorStatusText =
                    when (torStatus) {
                        TorStatus.CONNECTED -> stringResource(R.string.loc_892c0ce5)
                        TorStatus.CONNECTING -> stringResource(R.string.loc_1a2bbf31)
                        TorStatus.STARTING -> stringResource(R.string.loc_a4c47a71)
                        TorStatus.ERROR -> stringResource(R.string.loc_27d8399b)
                        TorStatus.DISCONNECTED, TorStatus.STOPPING -> stringResource(R.string.loc_d6353c61)
                    }

                CompactTextFieldWithSave(
                    value = mempoolUrlDraft,
                    onValueChange = {
                        mempoolUrlDraft = it
                        mempoolUrlError = null
                        mempoolUrlSaved = null
                    },
                    onSave = {
                        val error = ServerUrlValidator.validate(mempoolUrlDraft)
                        if (error != null) {
                            mempoolUrlError = error
                            mempoolUrlSaved = null
                        } else {
                            val normalizedUrl = ServerUrlValidator.normalize(mempoolUrlDraft)
                            mempoolUrlError = null
                            mempoolUrlDraft = normalizedUrl
                            onCustomMempoolUrlSave(normalizedUrl)
                            mempoolUrlSaved = "saved"
                        }
                    },
                    placeholder = stringResource(R.string.settings_custom_server_placeholder),
                    errorMessage = mempoolUrlError,
                    successMessage = mempoolUrlSaved,
                    torStatusText = if (isCustomMempoolOnion) mempoolTorStatusText else null,
                    torStatusColor = if (isCustomMempoolOnion) mempoolTorStatusColor else null,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsLabeledControl(
                icon = Icons.Default.AttachMoney,
                label = stringResource(R.string.loc_00a426f8),
            ) {
                PriceSourceDropdown(
                    currentSource = currentPriceSource,
                    onSourceSelected = onPriceSourceChange,
                )
            }

            val historicalTxFiatSupported = SecureStorage.supportsHistoricalTxFiatPricing(currentPriceSource)

            if (currentPriceSource != SecureStorage.PRICE_SOURCE_OFF) {
                Spacer(modifier = Modifier.height(6.dp))

                PriceCurrencyDropdown(
                    currentSource = currentPriceSource,
                    currentCurrency = currentPriceCurrency,
                    onCurrencySelected = onPriceCurrencyChange,
                )

                if (historicalTxFiatSupported) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onHistoricalTxFiatEnabledChange(!historicalTxFiatEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToggleOptionText(
                            title = stringResource(R.string.historical_tx_fiat_title),
                            subtitle = stringResource(R.string.historical_tx_fiat_subtitle),
                            modifier = Modifier.weight(1f),
                        )
                        SquareToggle(
                            checked = historicalTxFiatEnabled,
                            onCheckedChange = onHistoricalTxFiatEnabledChange,
                        )
                    }

                    if (currentPriceSource == SecureStorage.PRICE_SOURCE_YADIO) {
                        Text(
                            text = stringResource(R.string.historical_tx_fiat_yadio_limit_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = BitcoinOrange,
                            textAlign = TextAlign.Start,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                        )
                    }
                }
            }

            if (currentPriceSource == SecureStorage.PRICE_SOURCE_MEMPOOL_ONION) {
                TorStatusIndicator(
                    torStatus = torStatus,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = BorderColor.copy(alpha = 0.45f))

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenBitcoinElectrum),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.loc_a39dd5c6),
                    style = TextStyle(fontSize = 15.sp),
                    color = TextPrimary,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.loc_2a54f889),
                    tint = BitcoinOrange,
                    modifier =
                        Modifier
                            .padding(end = 8.dp)
                            .size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Layer2OptionsScreen(
    liquidEnabled: Boolean = false,
    onLiquidEnabledChange: (Boolean) -> Unit = {},
    sparkEnabled: Boolean = false,
    onSparkEnabledChange: (Boolean) -> Unit = {},
    arkEnabled: Boolean = false,
    onArkEnabledChange: (Boolean) -> Unit = {},
    lightningNodeEnabled: Boolean = false,
    onLightningNodeEnabledChange: (Boolean) -> Unit = {},
    onOpenLightningNodeConnection: () -> Unit = {},
    currentDenomination: String = SecureStorage.DENOMINATION_BTC,
    onDenominationChange: (String) -> Unit = {},
    currentBoltzApiSource: String = SecureStorage.BOLTZ_API_DISABLED,
    onBoltzApiSourceChange: (String) -> Unit = {},
    currentSideSwapApiSource: String = SecureStorage.SIDESWAP_API_DISABLED,
    onSideSwapApiSourceChange: (String) -> Unit = {},
    currentLiquidExplorer: String = SecureStorage.LIQUID_EXPLORER_DISABLED,
    onLiquidExplorerChange: (String) -> Unit = {},
    customLiquidExplorerUrl: String = "",
    onCustomLiquidExplorerUrlSave: (String) -> Unit = {},
    currentArkEsploraUrl: String = "",
    onArkEsploraUrlChange: (String) -> Unit = {},
    layer2TorStatus: TorStatus = TorStatus.DISCONNECTED,
    onOpenLiquidElectrum: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val layer2Enabled = liquidEnabled || sparkEnabled || arkEnabled || lightningNodeEnabled
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.loc_2f73501f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Layer2OptionsCard(
            liquidEnabled = liquidEnabled,
            onLiquidEnabledChange = onLiquidEnabledChange,
            sparkEnabled = sparkEnabled,
            onSparkEnabledChange = onSparkEnabledChange,
            lightningNodeEnabled = lightningNodeEnabled,
            onLightningNodeEnabledChange = onLightningNodeEnabledChange,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (lightningNodeEnabled) {
            LightningNodeConnectionCard(onOpenLightningNodeConnection)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (arkEnabled) {
            ArkExternalServicesCard(
                currentArkEsploraUrl = currentArkEsploraUrl,
                onArkEsploraUrlChange = onArkEsploraUrlChange,
                layer2TorStatus = layer2TorStatus,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (liquidEnabled) {
            Layer2ExternalServicesCard(
                currentBoltzApiSource = currentBoltzApiSource,
                onBoltzApiSourceChange = onBoltzApiSourceChange,
                currentSideSwapApiSource = currentSideSwapApiSource,
                onSideSwapApiSourceChange = onSideSwapApiSourceChange,
                currentLiquidExplorer = currentLiquidExplorer,
                onLiquidExplorerChange = onLiquidExplorerChange,
                customLiquidExplorerUrl = customLiquidExplorerUrl,
                onCustomLiquidExplorerUrlSave = onCustomLiquidExplorerUrlSave,
                layer2TorStatus = layer2TorStatus,
                onOpenLiquidElectrum = onOpenLiquidElectrum,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Layer2DisplayCard(
            layer2Enabled = layer2Enabled,
            currentDenomination = currentDenomination,
            onDenominationChange = onDenominationChange,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LightningNodeConnectionCard(onOpenConnection: () -> Unit) {
    SettingsSectionCard(title = stringResource(R.string.ln_node_connection_title)) {
        Button(
            onClick = onOpenConnection,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BitcoinOrange),
        ) {
            Text(stringResource(R.string.ln_node_setup_connection))
        }
    }
}

@Composable
private fun Layer2OptionsCard(
    liquidEnabled: Boolean,
    onLiquidEnabledChange: (Boolean) -> Unit,
    sparkEnabled: Boolean,
    onSparkEnabledChange: (Boolean) -> Unit,
    lightningNodeEnabled: Boolean,
    onLightningNodeEnabledChange: (Boolean) -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.protocols_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleOptionText(
                title = stringResource(R.string.ln_node_pill_label),
                subtitle = stringResource(R.string.settings_lightning_subtitle),
                titleColor = TextPrimary,
                subtitleColor = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            SquareToggle(
                checked = lightningNodeEnabled,
                onCheckedChange = onLightningNodeEnabledChange,
                checkedColor = LightningYellow,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleOptionText(
                title = stringResource(R.string.loc_85f5955f),
                subtitle = stringResource(R.string.settings_spark_subtitle),
                titleColor = TextPrimary,
                subtitleColor = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            SquareToggle(
                checked = sparkEnabled,
                onCheckedChange = onSparkEnabledChange,
                checkedColor = SparkPurple,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleOptionText(
                title = stringResource(R.string.loc_22236665),
                subtitle = stringResource(R.string.loc_f1af1b9c),
                titleColor = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            SquareToggle(
                checked = liquidEnabled,
                onCheckedChange = onLiquidEnabledChange,
                checkedColor = LiquidTeal,
            )
        }
    }
}

@Composable
private fun Layer2DisplayCard(
    layer2Enabled: Boolean,
    currentDenomination: String,
    onDenominationChange: (String) -> Unit,
) {
    val isSats = currentDenomination == SecureStorage.DENOMINATION_SATS

    SettingsSectionCard(title = stringResource(R.string.settings_general_section_display)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (layer2Enabled) {
                            Modifier.clickable {
                                onDenominationChange(
                                    if (!isSats) {
                                        SecureStorage.DENOMINATION_SATS
                                    } else {
                                        SecureStorage.DENOMINATION_BTC
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.CurrencyBitcoin,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                ToggleOptionText(
                    title = if (isSats) stringResource(R.string.loc_33b64233) else "BTC",
                    subtitle = if (isSats) {
                        stringResource(R.string.loc_eda2a508)
                    } else {
                        stringResource(R.string.loc_e0230c8c)
                    },
                )
            }
            SquareToggle(
                checked = isSats,
                onCheckedChange = { useSats ->
                    onDenominationChange(
                        if (useSats) {
                            SecureStorage.DENOMINATION_SATS
                        } else {
                            SecureStorage.DENOMINATION_BTC
                        },
                    )
                },
                enabled = layer2Enabled,
                checkedColor = BitcoinOrange,
                uncheckedColor = BitcoinOrange.copy(alpha = 0.18f),
                uncheckedBorderColor = BitcoinOrange,
                uncheckedThumbColor = BitcoinOrange,
            )
        }
    }
}

@Composable
private fun ArkExternalServicesCard(
    currentArkEsploraUrl: String,
    onArkEsploraUrlChange: (String) -> Unit,
    layer2TorStatus: TorStatus,
) {
    val selectedPreset =
        ArkEsploraPreset.match(currentArkEsploraUrl) ?: ArkEsploraPreset.MEMPOOL_SPACE
    SettingsSectionCard(title = stringResource(R.string.settings_ark_external_services_title)) {
        SettingsLabeledControl(
            icon = Icons.Default.Language,
            label = stringResource(R.string.ark_connection_esplora_label),
        ) {
            ArkEsploraDropdown(
                currentUrl = currentArkEsploraUrl,
                onUrlSelected = onArkEsploraUrlChange,
            )
        }
        if (selectedPreset.requiresTor) {
            TorStatusIndicator(
                torStatus = layer2TorStatus,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ark_esplora_picker_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun Layer2ExternalServicesCard(
    currentBoltzApiSource: String,
    onBoltzApiSourceChange: (String) -> Unit,
    currentSideSwapApiSource: String,
    onSideSwapApiSourceChange: (String) -> Unit,
    currentLiquidExplorer: String,
    onLiquidExplorerChange: (String) -> Unit,
    customLiquidExplorerUrl: String,
    onCustomLiquidExplorerUrlSave: (String) -> Unit,
    layer2TorStatus: TorStatus,
    onOpenLiquidElectrum: () -> Unit,
) {
    SettingsSectionCard(title = stringResource(R.string.loc_64f10f32)) {
        SettingsLabeledControl(
            icon = Icons.Default.Api,
            label = stringResource(R.string.loc_14b0f0b9),
        ) {
            BoltzApiSourceDropdown(
                currentSource = currentBoltzApiSource,
                onSourceSelected = onBoltzApiSourceChange,
            )
        }

        if (currentBoltzApiSource == SecureStorage.BOLTZ_API_TOR) {
            TorStatusIndicator(
                torStatus = layer2TorStatus,
                modifier = Modifier.padding(start = 28.dp, top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsLabeledControl(
            icon = Icons.Default.Api,
            label = stringResource(R.string.loc_799c4cd6),
        ) {
            SideSwapApiSourceDropdown(
                currentSource = currentSideSwapApiSource,
                onSourceSelected = onSideSwapApiSourceChange,
            )
        }

        if (currentSideSwapApiSource == SecureStorage.SIDESWAP_API_TOR) {
            TorStatusIndicator(
                torStatus = layer2TorStatus,
                modifier = Modifier.padding(start = 28.dp, top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsLabeledControl(
            icon = Icons.Default.Language,
            label = stringResource(R.string.loc_a688468b),
        ) {
            LiquidExplorerDropdown(
                currentExplorer = currentLiquidExplorer,
                onExplorerSelected = onLiquidExplorerChange,
            )
        }

        if (currentLiquidExplorer == SecureStorage.LIQUID_EXPLORER_CUSTOM) {
            Spacer(modifier = Modifier.height(6.dp))

            var liquidExplorerUrlDraft by remember(customLiquidExplorerUrl) {
                mutableStateOf(customLiquidExplorerUrl)
            }
            var liquidExplorerUrlError by remember { mutableStateOf<String?>(null) }
            var liquidExplorerUrlSaved by remember { mutableStateOf<String?>(null) }

            CompactTextFieldWithSave(
                value = liquidExplorerUrlDraft,
                onValueChange = {
                    liquidExplorerUrlDraft = it
                    liquidExplorerUrlError = null
                    liquidExplorerUrlSaved = null
                },
                onSave = {
                    val error = ServerUrlValidator.validate(liquidExplorerUrlDraft)
                    if (error != null) {
                        liquidExplorerUrlError = error
                        liquidExplorerUrlSaved = null
                    } else {
                        val normalizedUrl = ServerUrlValidator.normalize(liquidExplorerUrlDraft)
                        liquidExplorerUrlError = null
                        liquidExplorerUrlDraft = normalizedUrl
                        onCustomLiquidExplorerUrlSave(normalizedUrl)
                        liquidExplorerUrlSaved = "saved"
                    }
                },
                placeholder = stringResource(R.string.settings_custom_server_placeholder),
                errorMessage = liquidExplorerUrlError,
                successMessage = liquidExplorerUrlSaved,
                modifier = Modifier.padding(start = 28.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(color = BorderColor.copy(alpha = 0.45f))

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenLiquidElectrum),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.loc_c7185189),
                style = TextStyle(fontSize = 15.sp),
                color = TextPrimary,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.loc_1c1151e0),
                tint = BitcoinOrange,
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
            )
        }
    }
}

/**
 * Data class for mempool server options
 */
private data class MempoolServerOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MempoolServerDropdown(
    currentServer: String,
    onServerSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val serverOptions =
        listOf(
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_DISABLED,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_64d3e427),
            ),
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_SPACE,
                name = "mempool.space",
                description = stringResource(R.string.loc_bed05818),
            ),
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_ONION,
                name = stringResource(R.string.loc_e70effb6),
                description = stringResource(R.string.loc_c49a3480),
            ),
            MempoolServerOption(
                id = SecureStorage.MEMPOOL_CUSTOM,
                name = stringResource(R.string.loc_c032eff3),
                description = stringResource(R.string.loc_b3694f7f),
            ),
        )

    val selectedOption = serverOptions.find { it.id == currentServer } ?: serverOptions.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            serverOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentServer,
                        )
                    },
                    onClick = {
                        onServerSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentServer) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

private data class SwipeModeOption(
    val id: String,
    val name: String,
    val description: String,
)

private data class ThemeModeOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeModeDropdown(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    isLiquidAvailable: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    val options = buildList {
        add(
            SwipeModeOption(
                id = SecureStorage.SWIPE_MODE_DISABLED,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_1ddabd37),
            ),
        )
        add(
            SwipeModeOption(
                id = SecureStorage.SWIPE_MODE_WALLETS,
                name = stringResource(R.string.loc_59c793f0),
                description = stringResource(R.string.loc_ff9f65eb),
            ),
        )
        add(
            SwipeModeOption(
                id = SecureStorage.SWIPE_MODE_SEND_RECEIVE,
                name = stringResource(R.string.loc_6ea25f47),
                description = stringResource(R.string.loc_6e8be5a6),
            ),
        )
        if (isLiquidAvailable) {
            add(
                SwipeModeOption(
                    id = SecureStorage.SWIPE_MODE_LAYERS,
                    name = stringResource(R.string.loc_e124e866),
                    description = stringResource(R.string.loc_62840d26),
                ),
            )
        }
    }

    val selectedOption = options.find { it.id == currentMode } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentMode,
                        )
                    },
                    onClick = {
                        onModeSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeDropdown(
    currentThemeMode: String,
    onThemeModeSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            ThemeModeOption(
                id = SecureStorage.THEME_MODE_DARK,
                name = stringResource(R.string.settings_theme_dark),
                description = stringResource(R.string.settings_theme_dark_description),
            ),
            ThemeModeOption(
                id = SecureStorage.THEME_MODE_AMOLED,
                name = stringResource(R.string.settings_theme_amoled),
                description = stringResource(R.string.settings_theme_amoled_description),
            ),
        )
    val selectedOption = options.find { it.id == currentThemeMode } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentThemeMode,
                        )
                    },
                    onClick = {
                        onThemeModeSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentThemeMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Data class for fee source options
 */
private data class FeeSourceOption(
    val id: String,
    val name: String,
    val description: String,
)

/**
 * Dropdown for selecting fee estimation source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val sourceOptions =
        listOf(
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_OFF,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_ff1867f2),
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_MEMPOOL,
                name = "mempool.space",
                description = stringResource(R.string.loc_bed05818),
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_MEMPOOL_ONION,
                name = stringResource(R.string.loc_e70effb6),
                description = stringResource(R.string.loc_a1b0d97e),
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_BITVIEW,
                name = "bitview.space",
                description = stringResource(R.string.loc_bed05818),
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_ELECTRUM,
                name = stringResource(R.string.loc_aab4007b),
                description = stringResource(R.string.loc_aeca6ac4),
            ),
            FeeSourceOption(
                id = SecureStorage.FEE_SOURCE_CUSTOM,
                name = stringResource(R.string.loc_c032eff3),
                description = stringResource(R.string.loc_b3694f7f),
            ),
        )

    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            sourceOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Data class for price source options
 */
private data class PriceSourceOption(
    val id: String,
    val name: String,
    val description: String,
)

/**
 * Dropdown for selecting BTC/fiat price source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val sourceOptions =
        listOf(
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_OFF,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_060e830f),
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_MEMPOOL,
                name = "mempool.space",
                description = stringResource(R.string.loc_bed05818),
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_MEMPOOL_ONION,
                name = stringResource(R.string.loc_e70effb6),
                description = stringResource(R.string.loc_a1b0d97e),
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_COINGECKO,
                name = "CoinGecko",
                description = stringResource(R.string.loc_bed05818),
            ),
            PriceSourceOption(
                id = SecureStorage.PRICE_SOURCE_YADIO,
                name = "Yadio.io",
                description = stringResource(R.string.loc_bed05818),
            ),
        )

    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            sourceOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceCurrencyDropdown(
    currentSource: String,
    currentCurrency: String,
    onCurrencySelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val currencyOptions = remember(currentSource) { BtcPriceService.getSupportedFiatCurrencies(currentSource) }
    val selectedOption =
        currencyOptions.find { it.code == currentCurrency } ?: currencyOptions.firstOrNull()

    if (selectedOption == null) return

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = "${selectedOption.code} · ${selectedOption.name}",
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            currencyOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = "${option.code} · ${option.name}",
                            subtitle = "",
                            selected = option.code == currentCurrency,
                        )
                    },
                    onClick = {
                        onCurrencySelected(option.code)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.code == currentCurrency) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

private data class Layer2ApiSourceOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoltzApiSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            Layer2ApiSourceOption(
                id = SecureStorage.BOLTZ_API_DISABLED,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_c519cf9f),
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.BOLTZ_API_CLEARNET,
                name = "Boltz",
                description = stringResource(R.string.loc_bed05818),
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.BOLTZ_API_TOR,
                name = stringResource(R.string.loc_29c377a4),
                description = stringResource(R.string.loc_a1b0d97e),
            ),
        )
    val selectedOption = options.find { it.id == currentSource } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideSwapApiSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            Layer2ApiSourceOption(
                id = SecureStorage.SIDESWAP_API_DISABLED,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_e4e0f733),
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.SIDESWAP_API_CLEARNET,
                name = "SideSwap",
                description = stringResource(R.string.loc_bed05818),
            ),
            Layer2ApiSourceOption(
                id = SecureStorage.SIDESWAP_API_TOR,
                name = stringResource(R.string.loc_91ea3f60),
                description = stringResource(R.string.loc_0d01317f),
            ),
        )
    val selectedOption = options.find { it.id == currentSource } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentSource,
                        )
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

private data class LiquidExplorerOption(
    val id: String,
    val name: String,
    val description: String,
)

private fun truncateOnionEsploraUrl(url: String): String {
    val marker = ".onion"
    val lower = url.lowercase()
    val onionAt = lower.indexOf(marker)
    if (onionAt < 0) return url
    val schemeEnd = url.indexOf("://").let { if (it >= 0) it + 3 else 0 }
    val host = url.substring(schemeEnd, onionAt + marker.length)
    if (host.length <= 22) return url
    val prefix = host.take(12)
    val suffix = host.takeLast(10)
    val path = url.substring(onionAt + marker.length)
    val scheme = url.take(schemeEnd)
    return "$scheme$prefix…$suffix$path"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArkEsploraDropdown(
    currentUrl: String,
    onUrlSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected =
        ArkEsploraPreset.match(currentUrl) ?: ArkEsploraPreset.MEMPOOL_SPACE
    val selectedLabel =
        if (selected.requiresTor) {
            "${selected.label} · Tor"
        } else {
            selected.label
        }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedLabel,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            ArkEsploraPreset.entries.forEach { preset ->
                val isSelected =
                    selected.url.trimEnd('/').equals(preset.url.trimEnd('/'), ignoreCase = true)
                val displayUrl =
                    if (preset.requiresTor) {
                        truncateOnionEsploraUrl(preset.url)
                    } else {
                        preset.url
                    }
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = preset.label,
                            subtitle = displayUrl,
                            selected = isSelected,
                        )
                    },
                    onClick = {
                        onUrlSelected(preset.url)
                        expanded = false
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = ArkRust,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidExplorerDropdown(
    currentExplorer: String,
    onExplorerSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_DISABLED,
                name = stringResource(R.string.loc_7d880cb5),
                description = stringResource(R.string.loc_bcc5378e),
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_LIQUID_NETWORK,
                name = "liquid.network",
                description = stringResource(R.string.loc_bed05818),
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_LIQUID_NETWORK_ONION,
                name = stringResource(R.string.loc_1129520d),
                description = stringResource(R.string.loc_c49a3480),
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_BLOCKSTREAM,
                name = "Blockstream",
                description = stringResource(R.string.loc_bed05818),
            ),
            LiquidExplorerOption(
                id = SecureStorage.LIQUID_EXPLORER_CUSTOM,
                name = stringResource(R.string.loc_c032eff3),
                description = stringResource(R.string.loc_b3694f7f),
            ),
        )
    val selectedOption = options.find { it.id == currentExplorer } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(true).background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.id == currentExplorer,
                        )
                    },
                    onClick = {
                        onExplorerSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentExplorer) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BitcoinOrange,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsLabeledControl(
    icon: ImageVector,
    label: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BitcoinOrange,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ToggleOptionText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: androidx.compose.ui.graphics.Color = TextSecondary,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp),
            color = titleColor,
        )
        Text(
            text = subtitle,
            style = TextStyle(fontSize = 13.sp),
            color = subtitleColor,
        )
    }
}

/**
 * Compact editable text field with a right-aligned Save button and optional error message.
 */
private fun settingsIsOnionUrl(url: String): Boolean =
    try {
        java.net.URI(url.trim()).host?.endsWith(".onion", ignoreCase = true) == true
    } catch (_: Exception) {
        url.contains(".onion", ignoreCase = true)
    }

/**
 * Tor connection status indicator — colored dot + status text.
 * Reused for any onion-based source (fee, price, custom URL).
 */
@Composable
private fun TorStatusIndicator(
    torStatus: TorStatus,
    modifier: Modifier = Modifier,
) {
    val torStatusColor =
        when (torStatus) {
            TorStatus.CONNECTED -> TorPurple
            TorStatus.CONNECTING, TorStatus.STARTING -> TorPurple.copy(alpha = 0.6f)
            TorStatus.ERROR -> ErrorRed
            TorStatus.DISCONNECTED, TorStatus.STOPPING -> TextSecondary
        }
    val torStatusText =
        when (torStatus) {
            TorStatus.CONNECTED -> stringResource(R.string.loc_892c0ce5)
            TorStatus.CONNECTING -> stringResource(R.string.loc_1a2bbf31)
            TorStatus.STARTING -> stringResource(R.string.loc_a4c47a71)
            TorStatus.ERROR -> stringResource(R.string.loc_27d8399b)
            TorStatus.DISCONNECTED, TorStatus.STOPPING -> stringResource(R.string.loc_d6353c61)
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(torStatusColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = torStatusText,
            style = MaterialTheme.typography.bodySmall,
            color = torStatusColor,
        )
    }
}

@Composable
private fun CompactTextFieldWithSave(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    successMessage: String? = null,
    torStatusText: String? = null,
    torStatusColor: androidx.compose.ui.graphics.Color? = null,
) {
    val borderColor =
        when {
            errorMessage != null -> ErrorRed
            successMessage != null -> SuccessGreen
            else -> BorderColor
        }

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle =
                    TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                cursorBrush = SolidColor(BitcoinOrange),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(fontSize = 13.sp),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Text(
                text = if (successMessage != null) stringResource(R.string.loc_3822fc21) else stringResource(R.string.loc_f55495e0),
                style = TextStyle(fontSize = 13.sp),
                color = if (successMessage != null) SuccessGreen else BitcoinOrange,
                modifier =
                    Modifier
                        .clickable(onClick = onSave)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }

        // Error and/or Tor status on the same line
        if (errorMessage != null || torStatusText != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (torStatusText != null && torStatusColor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(torStatusColor),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = torStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = torStatusColor,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
