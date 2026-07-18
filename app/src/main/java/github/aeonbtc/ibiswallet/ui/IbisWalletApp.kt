@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.data.model.SyncProgress
import github.aeonbtc.ibiswallet.data.model.WalletLayer
import github.aeonbtc.ibiswallet.data.model.WalletPolicyType
import github.aeonbtc.ibiswallet.navigation.Screen
import github.aeonbtc.ibiswallet.navigation.bottomNavItems
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.ui.components.AppLaunchLoadingScreen
import github.aeonbtc.ibiswallet.ui.components.CertificateDialog
import github.aeonbtc.ibiswallet.ui.components.DrawerContent
import github.aeonbtc.ibiswallet.ui.components.DrawerItem
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.LayerSwitcher
import github.aeonbtc.ibiswallet.ui.components.LayerSwitcherCenterMode
import github.aeonbtc.ibiswallet.ui.components.WalletSelectorDropdown
import github.aeonbtc.ibiswallet.ui.components.WalletSelectorPanel
import github.aeonbtc.ibiswallet.ui.screens.AboutScreen
import github.aeonbtc.ibiswallet.ui.screens.AllAddressesScreen
import github.aeonbtc.ibiswallet.ui.screens.AllUtxosScreen
import github.aeonbtc.ibiswallet.ui.screens.BackupRestoreScreen
import github.aeonbtc.ibiswallet.ui.screens.BackupWalletEntry
import github.aeonbtc.ibiswallet.ui.screens.BalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.BroadcastTransactionScreen
import github.aeonbtc.ibiswallet.ui.screens.CombinedServerConfigScreen
import github.aeonbtc.ibiswallet.ui.screens.CurrentServerCard
import github.aeonbtc.ibiswallet.ui.screens.ElectrumConfigScreen
import github.aeonbtc.ibiswallet.ui.screens.FullBackupPreview
import github.aeonbtc.ibiswallet.ui.screens.GenerateWalletScreen
import github.aeonbtc.ibiswallet.ui.screens.ImportWalletScreen
import github.aeonbtc.ibiswallet.ui.screens.KeyMaterialInfo
import github.aeonbtc.ibiswallet.ui.screens.Layer2OptionsScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeBalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeChannelsScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeConnectionScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeOnchainBalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeOnchainReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeOnchainSendScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.LightningNodeSendScreen
import github.aeonbtc.ibiswallet.ui.screens.LiquidBalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.LiquidCurrentServerCard
import github.aeonbtc.ibiswallet.ui.screens.LiquidReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.LiquidSendScreen
import github.aeonbtc.ibiswallet.ui.screens.LiquidServerConfigScreen
import github.aeonbtc.ibiswallet.ui.screens.LockScreen
import github.aeonbtc.ibiswallet.ui.screens.ManageWalletsScreen
import github.aeonbtc.ibiswallet.ui.screens.PsbtScreen
import github.aeonbtc.ibiswallet.ui.screens.ReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.SecurityScreen
import github.aeonbtc.ibiswallet.ui.screens.SendScreen
import github.aeonbtc.ibiswallet.ui.screens.ServerConfigSection
import github.aeonbtc.ibiswallet.ui.screens.SettingsScreen
import github.aeonbtc.ibiswallet.ui.screens.SparkBalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.SparkReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.SparkSendScreen
import github.aeonbtc.ibiswallet.ui.screens.SparkTransferScreen
import github.aeonbtc.ibiswallet.ui.screens.SwapScreen
import github.aeonbtc.ibiswallet.ui.screens.SweepPrivateKeyScreen
import github.aeonbtc.ibiswallet.ui.screens.WalletInfo
import github.aeonbtc.ibiswallet.ui.screens.WelcomeDialog
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TorPurple
import github.aeonbtc.ibiswallet.util.Bip329LabelCounts
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.WalletNotificationHelper
import github.aeonbtc.ibiswallet.util.WalletNotificationPolicy
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.layer2RecipientValidationError
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import github.aeonbtc.ibiswallet.util.resolveLayer2SendDraft
import github.aeonbtc.ibiswallet.util.resolveSendRoute
import github.aeonbtc.ibiswallet.data.model.LightningNodeEvent
import github.aeonbtc.ibiswallet.data.model.SparkEvent
import github.aeonbtc.ibiswallet.viewmodel.LightningNodeViewModel
import github.aeonbtc.ibiswallet.viewmodel.LiquidEvent
import github.aeonbtc.ibiswallet.viewmodel.LiquidViewModel
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import github.aeonbtc.ibiswallet.viewmodel.SparkViewModel
import github.aeonbtc.ibiswallet.viewmodel.WalletEvent
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToLong

private enum class WalletAuthPurpose {
    OPEN_WALLET,
    DISABLE_LOCK,
}

private data class PendingWalletUnlock(
    val walletId: String,
    val walletName: String,
    val isLocked: Boolean,
    val purpose: WalletAuthPurpose,
    val targetLayer: WalletLayer,
    val navigateToBalance: Boolean,
    val securityMethod: SecureStorage.SecurityMethod,
)

private sealed class SwipeAction {
    data class NavigateTab(val route: String) : SwipeAction()
    data class SwitchWallet(val walletId: String) : SwipeAction()
    data class SwitchLayer(val layer: WalletLayer, val exitTransferRoute: String? = null) : SwipeAction()
}

private data class PendingSwipe(
    val action: SwipeAction,
    val direction: Int,
    val screenWidth: Float,
    val id: Long = System.nanoTime(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbisWalletApp(
    viewModel: WalletViewModel = viewModel(),
    liquidViewModel: LiquidViewModel = viewModel(),
    sparkViewModel: SparkViewModel = viewModel(),
    lightningNodeViewModel: LightningNodeViewModel = viewModel(),
    onLockApp: () -> Unit = {},
    appUnlockCounter: Int = 0,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val secureStorage = remember(context) { SecureStorage.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val walletSettingsRefreshVersion by viewModel.settingsRefreshVersion.collectAsStateWithLifecycle()
    val liquidSettingsRefreshVersion by liquidViewModel.settingsRefreshVersion.collectAsStateWithLifecycle()
    var walletNotificationsEnabled by remember(walletSettingsRefreshVersion) {
        mutableStateOf(viewModel.isWalletNotificationsEnabled())
    }
    var foregroundConnectivityEnabled by remember(walletSettingsRefreshVersion) {
        mutableStateOf(viewModel.isForegroundConnectivityEnabled())
    }
    var appUpdateCheckEnabled by remember(walletSettingsRefreshVersion) {
        mutableStateOf(viewModel.isAppUpdateCheckEnabled())
    }
    val notificationPermissionGranted = WalletNotificationHelper.hasNotificationPermission(context)
    val systemNotificationsEnabled = WalletNotificationHelper.areNotificationsEnabledInSystem(context)
    val walletNotificationDeliveryState =
        WalletNotificationPolicy.resolveDeliveryState(
            appEnabled = walletNotificationsEnabled,
            permissionGranted = notificationPermissionGranted,
            systemNotificationsEnabled = systemNotificationsEnabled,
        )
    val electrumStatusTorBootstrapping = stringResource(R.string.loc_268af6fe)
    val electrumStatusConnecting = stringResource(R.string.loc_066df953)
    val electrumStatusConnected = stringResource(R.string.loc_98469a16)
    val electrumStatusDisconnected = stringResource(R.string.loc_82e9d0dd)
    val bip329ExportEmptyMessage = stringResource(R.string.loc_ba113750)
    val bip329ExportSuccessMessage = stringResource(R.string.loc_c5473c1d)
    val bip329ExportErrorMessage = stringResource(R.string.loc_45e5d14f)
    val bip329ImportEmptyMessage = stringResource(R.string.loc_9efb1540)
    val bip329ImportSuccessMessage = stringResource(R.string.loc_a70e9a3b)
    val bip329ImportErrorMessage = stringResource(R.string.loc_4be4f258)
    val duressSetupErrorMessage = stringResource(R.string.loc_e95ede4f)
    val psetCanceledMessage = stringResource(R.string.loc_b6df6876)
    val walletNotificationsAndroidBlocked = stringResource(R.string.wallet_notifications_android_blocked)
    val walletNotificationsPermissionDenied = stringResource(R.string.wallet_notifications_permission_denied)
    val initialSyncComplete by viewModel.initialSyncComplete.collectAsStateWithLifecycle()
    val initialLiquidSyncComplete by liquidViewModel.initialLiquidSyncComplete.collectAsStateWithLifecycle()

    val walletState by viewModel.walletState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serversState by viewModel.serversState.collectAsStateWithLifecycle()
    val torState by viewModel.torState.collectAsStateWithLifecycle()
    val layer1Denomination by viewModel.denominationState.collectAsStateWithLifecycle()
    val appLocale by viewModel.appLocale.collectAsStateWithLifecycle()
    val feeEstimationState by viewModel.feeEstimationState.collectAsStateWithLifecycle()
    val minFeeRate by viewModel.minFeeRate.collectAsStateWithLifecycle()
    val btcPrice by viewModel.btcPriceState.collectAsStateWithLifecycle()
    val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()
    val appUpdatePrompt by viewModel.appUpdatePrompt.collectAsStateWithLifecycle()
    val priceCurrency by viewModel.priceCurrencyState.collectAsStateWithLifecycle()
    val historicalTxFiatEnabled by viewModel.historicalTxFiatEnabledState.collectAsStateWithLifecycle()
    val historicalTxBtcPrices by viewModel.historicalTxBtcPriceState.collectAsStateWithLifecycle()
    var showHistoricalTxPrices by remember { mutableStateOf(false) }
    val autoSwitchServer by viewModel.autoSwitchServer.collectAsStateWithLifecycle()
    val syncingWalletId by viewModel.syncingWalletId.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    var showPrivacyModeHintDialog by remember { mutableStateOf(false) }
    val swipeMode by viewModel.swipeMode.collectAsStateWithLifecycle()
    val balanceDateFormat by viewModel.balanceDateFormatState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeModeState.collectAsStateWithLifecycle()
    val typeface by viewModel.typefaceState.collectAsStateWithLifecycle()
    val certDialogState by viewModel.certDialogState.collectAsStateWithLifecycle()
    val liquidCertDialogState by liquidViewModel.certDialogState.collectAsStateWithLifecycle()
    val isDuressMode by viewModel.isDuressMode.collectAsStateWithLifecycle()
    val pendingSendInput by viewModel.pendingSendInput.collectAsStateWithLifecycle()
    val walletLastFullSyncTimes by viewModel.walletLastFullSyncTimes.collectAsStateWithLifecycle()

    // Layer 2 (Liquid) state
    val isLayer2Enabled by liquidViewModel.isLayer2Enabled.collectAsStateWithLifecycle()
    val activeLayer by liquidViewModel.activeLayer.collectAsStateWithLifecycle()
    val liquidState by liquidViewModel.liquidState.collectAsStateWithLifecycle()
    val loadedLiquidWalletId by liquidViewModel.loadedWalletId.collectAsStateWithLifecycle()
    val liquidServersState by liquidViewModel.liquidServersState.collectAsStateWithLifecycle()
    val liquidEnabledWallets by liquidViewModel.liquidEnabledWallets.collectAsStateWithLifecycle()
    val liquidGapLimits by liquidViewModel.liquidGapLimits.collectAsStateWithLifecycle()
    val sparkEnabledWallets by sparkViewModel.sparkEnabledWallets.collectAsStateWithLifecycle()
    val sparkState by sparkViewModel.sparkState.collectAsStateWithLifecycle()
    val sparkSendState by sparkViewModel.sendState.collectAsStateWithLifecycle()
    val sparkReceiveState by sparkViewModel.receiveState.collectAsStateWithLifecycle()
    val sparkAddressLabels by sparkViewModel.sparkAddressLabels.collectAsStateWithLifecycle()
    val sparkSendDraft by sparkViewModel.sendDraft.collectAsStateWithLifecycle()
    val loadedSparkWalletId by sparkViewModel.loadedWalletId.collectAsStateWithLifecycle()
    val isSparkConnected by sparkViewModel.isSparkConnected.collectAsStateWithLifecycle()
    val isSparkConnecting by sparkViewModel.isSparkConnecting.collectAsStateWithLifecycle()
    val isSparkLayer2Enabled by sparkViewModel.isSparkLayer2Enabled.collectAsStateWithLifecycle()
    val lightningEnabledWallets by lightningNodeViewModel.lightningEnabledWallets.collectAsStateWithLifecycle()
    val lightningNodeState by lightningNodeViewModel.walletState.collectAsStateWithLifecycle()
    val lightningSendState by lightningNodeViewModel.sendState.collectAsStateWithLifecycle()
    val lightningReceiveState by lightningNodeViewModel.receiveState.collectAsStateWithLifecycle()
    val lightningOnchainState by lightningNodeViewModel.onchainState.collectAsStateWithLifecycle()
    val lightningSendDraft by lightningNodeViewModel.sendDraft.collectAsStateWithLifecycle()
    val loadedLightningWalletId by lightningNodeViewModel.loadedWalletId.collectAsStateWithLifecycle()
    val isLightningConnected by lightningNodeViewModel.isConnected.collectAsStateWithLifecycle()
    val isLightningConnecting by lightningNodeViewModel.isConnecting.collectAsStateWithLifecycle()
    val isLightningNodeLayer2Enabled by lightningNodeViewModel.isLightningNodeLayer2Enabled.collectAsStateWithLifecycle()
    val lightningConnectionTestResult by lightningNodeViewModel.connectionTestResult.collectAsStateWithLifecycle()
    val isLightningTestingConnection by lightningNodeViewModel.isTestingConnection.collectAsStateWithLifecycle()
    val lightningConnectionTestPhase by lightningNodeViewModel.connectionTestPhase.collectAsStateWithLifecycle()
    val lightningNodeLightningAddress by lightningNodeViewModel.lightningAddress.collectAsStateWithLifecycle()
    val lightningChannels by lightningNodeViewModel.channels.collectAsStateWithLifecycle()
    val lightningChannelsLoading by lightningNodeViewModel.channelsLoading.collectAsStateWithLifecycle()
    val lightningChannelsError by lightningNodeViewModel.channelsError.collectAsStateWithLifecycle()
    val lightningConfigRevision by lightningNodeViewModel.configRevision.collectAsStateWithLifecycle()
    val layer2Denomination by liquidViewModel.denominationState.collectAsStateWithLifecycle()
    val liquidExplorer by liquidViewModel.liquidExplorer.collectAsStateWithLifecycle()
    val isLiquidConnected by liquidViewModel.isLiquidConnected.collectAsStateWithLifecycle()
    val isLiquidConnecting by liquidViewModel.isLiquidConnecting.collectAsStateWithLifecycle()
    val isLiquidTorEnabled by liquidViewModel.isLiquidTorEnabled.collectAsStateWithLifecycle()
    val liquidAutoSwitch by liquidViewModel.liquidAutoSwitchServer.collectAsStateWithLifecycle()
    val liquidTorState by liquidViewModel.torState.collectAsStateWithLifecycle()
    val boltzApiSource by liquidViewModel.boltzApiSource.collectAsStateWithLifecycle()
    val sideSwapApiSource by liquidViewModel.sideSwapApiSource.collectAsStateWithLifecycle()
    val liquidBlockHeight by liquidViewModel.liquidBlockHeight.collectAsStateWithLifecycle()
    val liquidConnectionError by liquidViewModel.liquidConnectionError.collectAsStateWithLifecycle()
    val pendingLiquidFullSyncProgress by liquidViewModel.pendingFullSyncProgress.collectAsStateWithLifecycle()
    var holdLiquidConnectingStatus by remember { mutableStateOf(false) }
    LaunchedEffect(isLiquidConnecting, isLiquidConnected, liquidConnectionError) {
        when {
            isLiquidConnecting -> holdLiquidConnectingStatus = true
            isLiquidConnected || liquidConnectionError != null -> holdLiquidConnectingStatus = false
        }
    }
    val showLiquidConnecting = isLiquidConnecting || holdLiquidConnectingStatus
    // Liquid is available for the active wallet when:
    // - Global Layer 2 toggle is ON
    // - Per-wallet Liquid toggle is ON
    // - Wallet has a seed (BIP39) OR is a Liquid watch-only wallet (CT descriptor)
    val activeWalletObj = walletState.activeWallet
    val isActiveWalletLiquidWatchOnly = activeWalletObj?.let {
        liquidViewModel.isLiquidWatchOnly(it.id)
    } == true
    val visibleLiquidState =
        if (activeWalletObj != null && liquidState.walletId == activeWalletObj.id) {
            liquidState
        } else {
            LiquidWalletState(isInitialized = true)
        }
    val activeLayer2Provider = remember(
        activeWalletObj,
        liquidEnabledWallets,
        sparkEnabledWallets,
        lightningEnabledWallets,
        isActiveWalletLiquidWatchOnly,
    ) {
        val walletId = activeWalletObj?.id
        when {
            walletId == null -> Layer2Provider.NONE
            lightningEnabledWallets[walletId]
                ?: lightningNodeViewModel.isLightningNodeEnabledForWallet(walletId) -> Layer2Provider.LIGHTNING
            sparkEnabledWallets[walletId] ?: sparkViewModel.isSparkEnabledForWallet(walletId) -> Layer2Provider.SPARK
            isActiveWalletLiquidWatchOnly ||
                (liquidEnabledWallets[walletId] ?: liquidViewModel.isLiquidEnabledForWallet(walletId)) -> Layer2Provider.LIQUID
            else -> Layer2Provider.NONE
        }
    }
    val isLiquidAvailable = remember(
        isLayer2Enabled,
        activeWalletObj,
        liquidEnabledWallets,
        isActiveWalletLiquidWatchOnly,
        activeLayer2Provider,
    ) {
        isLayer2Enabled &&
            activeWalletObj != null &&
            (!activeWalletObj.isWatchOnly || isActiveWalletLiquidWatchOnly) &&
            activeLayer2Provider == Layer2Provider.LIQUID &&
            (liquidEnabledWallets[activeWalletObj.id]
                ?: liquidViewModel.isLiquidEnabledForWallet(activeWalletObj.id))
    }
    val isSparkAvailable = remember(isSparkLayer2Enabled, activeWalletObj, sparkEnabledWallets, activeLayer2Provider) {
        isSparkLayer2Enabled &&
            activeWalletObj != null &&
            !activeWalletObj.isWatchOnly &&
            activeWalletObj.seedFormat == github.aeonbtc.ibiswallet.data.model.SeedFormat.BIP39 &&
            activeLayer2Provider == Layer2Provider.SPARK &&
            (sparkEnabledWallets[activeWalletObj.id] ?: sparkViewModel.isSparkEnabledForWallet(activeWalletObj.id))
    }
    val isLightningAvailable =
        remember(isLightningNodeLayer2Enabled, activeWalletObj, lightningEnabledWallets, activeLayer2Provider) {
            isLightningNodeLayer2Enabled &&
                activeWalletObj != null &&
                activeLayer2Provider == Layer2Provider.LIGHTNING &&
                (
                    lightningEnabledWallets[activeWalletObj.id]
                        ?: lightningNodeViewModel.isLightningNodeEnabledForWallet(activeWalletObj.id)
                    )
        }
    val visibleSparkState =
        if (activeWalletObj != null &&
            loadedSparkWalletId == activeWalletObj.id &&
            sparkState.walletId == activeWalletObj.id
        ) {
            sparkState
        } else {
            SparkWalletState(
                walletId = activeWalletObj?.id,
                isInitialized = true,
            )
    }
    val visibleLightningState =
        if (activeWalletObj != null &&
            (loadedLightningWalletId == activeWalletObj.id || lightningNodeState.walletId == activeWalletObj.id)
        ) {
            // Prefer repository state when either loaded id or wallet-state id matches active,
            // so beginConnecting during switches is visible even if load is still in flight.
            if (lightningNodeState.walletId == activeWalletObj.id) {
                lightningNodeState
            } else {
                github.aeonbtc.ibiswallet.data.model.LightningNodeWalletState(
                    walletId = activeWalletObj.id,
                    isInitialized = true,
                    isConnecting = isLightningConnecting && loadedLightningWalletId == activeWalletObj.id,
                    connectionType = lightningNodeState.connectionType,
                )
            }
        } else {
            github.aeonbtc.ibiswallet.data.model.LightningNodeWalletState(
                walletId = activeWalletObj?.id,
                isInitialized = true,
            )
        }
    val visibleSparkConnected = isSparkConnected && loadedSparkWalletId == activeWalletObj?.id
    val visibleSparkConnecting =
        isSparkConnecting && !visibleSparkConnected && sparkState.walletId == activeWalletObj?.id
    val visibleLightningConnected =
        isLightningConnected &&
            loadedLightningWalletId == activeWalletObj?.id &&
            lightningNodeState.walletId == activeWalletObj?.id
    val visibleLightningConnecting =
        !visibleLightningConnected &&
            activeWalletObj != null &&
            (
                (isLightningConnecting && loadedLightningWalletId == activeWalletObj.id) ||
                    (lightningNodeState.isConnecting && lightningNodeState.walletId == activeWalletObj.id)
                )
    val isLayer2Available = isLiquidAvailable || isSparkAvailable || isLightningAvailable
    val isAnyLayer2Enabled = isLayer2Enabled || isSparkLayer2Enabled || isLightningNodeLayer2Enabled
    val layer2Accent =
        when (activeLayer2Provider) {
            Layer2Provider.SPARK -> SparkPurple
            Layer2Provider.LIGHTNING -> LightningYellow
            else -> LiquidTeal
        }
    val layer2Label = stringResource(R.string.loc_2f73501f)
    val openLayer2Transfer: () -> Unit = {
        when (activeLayer2Provider) {
            Layer2Provider.SPARK -> navController.navigate(Screen.SparkTransfer.route)
            Layer2Provider.LIQUID -> navController.navigate(Screen.Swap.route)
            Layer2Provider.LIGHTNING ->
                navController.navigate(Screen.LightningNodeChannels.route) {
                    launchSingleTop = true
                }
            else -> Unit
        }
    }

    // Liquid Swap remains selectable so users can choose APIs from the Swap screen.
    // Lightning Node: center control is Channels (enabled whenever LN wallet is active).
    val swapEnabledForWallet =
        when (activeLayer2Provider) {
            Layer2Provider.SPARK -> isSparkAvailable
            Layer2Provider.LIGHTNING -> isLightningAvailable
            else -> !isActiveWalletLiquidWatchOnly
        }
    val layerSwitcherCenterMode =
        if (isLightningAvailable) {
            LayerSwitcherCenterMode.CHANNELS
        } else {
            LayerSwitcherCenterMode.SWAP
        }
    val isLayer1EnabledForWallet = !isActiveWalletLiquidWatchOnly

    LaunchedEffect(privacyMode) {
        if (privacyMode && !secureStorage.hasSeenPrivacyModeHint()) {
            showPrivacyModeHintDialog = true
        }
    }

    fun dismissPrivacyModeHint() {
        secureStorage.setHasSeenPrivacyModeHint(true)
        showPrivacyModeHintDialog = false
    }

    val initialWalletId = remember(secureStorage) { secureStorage.getActiveWalletId() }
    var pendingMainWalletId by remember { mutableStateOf(initialWalletId) }
    var pendingMainLayer by remember {
        mutableStateOf(
            initialWalletId?.let { walletId ->
                runCatching { WalletLayer.valueOf(secureStorage.getActiveLayer(walletId)) }
                    .getOrDefault(WalletLayer.LAYER1)
            } ?: WalletLayer.LAYER1,
        )
    }

    // Wallet selector dropdown state
    var walletSelectorExpanded by remember { mutableStateOf(false) }
    var hasCompletedInitialMainLoad by remember { mutableStateOf(false) }

    // PIN setup active state — hides bottom bar so Next/Confirm buttons are visible
    var isPinSetupActive by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val postNotificationsPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                viewModel.setWalletNotificationsEnabled(true)
                walletNotificationsEnabled = true
                if (!WalletNotificationHelper.areNotificationsEnabledInSystem(context)) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            walletNotificationsAndroidBlocked,
                        )
                    }
                }
            } else {
                viewModel.setWalletNotificationsEnabled(false)
                walletNotificationsEnabled = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        walletNotificationsPermissionDenied,
                    )
                }
            }
        }

    fun updateWalletNotificationsEnabled(enabled: Boolean) {
        viewModel.setWalletNotificationsEnabled(enabled)
        walletNotificationsEnabled = enabled
    }

    fun updateForegroundConnectivityEnabled(enabled: Boolean) {
        viewModel.setForegroundConnectivityEnabled(enabled)
        foregroundConnectivityEnabled = enabled
    }

    fun updateAppUpdateCheckEnabled(enabled: Boolean) {
        viewModel.setAppUpdateCheckEnabled(enabled)
        appUpdateCheckEnabled = enabled
        secureStorage.setHasSeenAppUpdateOptInPrompt(true)
    }

    fun postWalletNotification(
        key: String,
        title: String,
        body: String,
    ) {
        if (!walletNotificationsEnabled) return
        WalletNotificationHelper.notifyWalletActivity(
            context = context,
            notificationId = key.hashCode(),
            title = title,
            body = body,
        )
    }

    LaunchedEffect(Unit) {
        WalletNotificationHelper.ensureChannels(context)
    }

    val handleParsedSendInput: (String) -> Unit = handleParsedSendInput@{ input ->
        val parsedInput = parseSendRecipient(input)
        if (isLayer2Available) {
            val providerMismatch =
                (parsedInput is ParsedSendRecipient.Spark && activeLayer2Provider != Layer2Provider.SPARK) ||
                    (parsedInput is ParsedSendRecipient.Liquid && activeLayer2Provider != Layer2Provider.LIQUID)
            if (providerMismatch) {
                layer2RecipientValidationError(parsedInput, activeLayer2Provider)?.let { message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                    return@handleParsedSendInput
                }
            }
        }
        val resolution =
            resolveSendRoute(
                input = input,
                layer1UseSats = layer1Denomination == SecureStorage.DENOMINATION_SATS,
                layer2UseSats = layer2Denomination == SecureStorage.DENOMINATION_SATS,
                isLiquidAvailable = isLayer2Available,
            )
        if (isLayer2Available) {
            liquidViewModel.setActiveLayer(resolution.route, walletState.activeWallet?.id)
        }
        if (resolution.route == WalletLayer.LAYER2) {
            when (activeLayer2Provider) {
                Layer2Provider.SPARK -> sparkViewModel.setSendDraft(resolution.draft)
                Layer2Provider.LIGHTNING -> lightningNodeViewModel.setSendDraft(resolution.draft)
                else -> liquidViewModel.updateSendDraft(resolution.draft)
            }
        } else {
            viewModel.updateSendScreenDraft(resolution.draft)
        }
        navController.navigate(Screen.Send.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    val handleLayer2SendInput: (String) -> Unit = { input ->
        if (isLayer2Available) {
            liquidViewModel.setActiveLayer(WalletLayer.LAYER2, walletState.activeWallet?.id)
            val parsed = parseSendRecipient(input)
            val validationError = layer2RecipientValidationError(parsed, activeLayer2Provider)
            if (validationError != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(validationError)
                }
            } else {
                val draft = resolveLayer2SendDraft(
                    input = input,
                    layer2UseSats = layer2Denomination == SecureStorage.DENOMINATION_SATS,
                    provider = activeLayer2Provider,
                )
                when (activeLayer2Provider) {
                    Layer2Provider.SPARK -> sparkViewModel.setSendDraft(draft)
                    Layer2Provider.LIGHTNING -> lightningNodeViewModel.setSendDraft(draft)
                    else -> liquidViewModel.updateSendDraft(draft)
                }
                navController.navigate(Screen.Send.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
        } else {
            handleParsedSendInput(input)
        }
    }

    // Handle incoming send payload from external intent or NFC
    LaunchedEffect(pendingSendInput) {
        val input = pendingSendInput ?: return@LaunchedEffect
        viewModel.consumePendingSendInput()
        handleParsedSendInput(input)
    }

    // Close wallet picker and reset PIN setup state when navigating to a different screen
    LaunchedEffect(currentDestination?.route) {
        walletSelectorExpanded = false
        if (currentDestination?.route != Screen.Security.route) {
            isPinSetupActive = false
        }
    }

    // Check if we're on a main screen (with bottom nav)
    val isMainScreen =
        currentDestination?.route in
            listOf(
                Screen.Receive.route,
                Screen.Balance.route,
                Screen.Send.route,
                Screen.Swap.route,
                Screen.SparkTransfer.route,
                Screen.LightningNodeChannels.route,
            )

    // Check if we're on a sub-screen that should show back button in main TopAppBar
    val isSubScreenWithTopBar =
        currentDestination?.route in
            listOf(
                Screen.AllAddresses.route,
                Screen.AllUtxos.route,
            )

    val requiresActiveWalletAuth =
        currentDestination?.route in
            listOf(
                Screen.Receive.route,
                Screen.Balance.route,
                Screen.Send.route,
                Screen.Swap.route,
                Screen.SparkTransfer.route,
                Screen.LightningNodeChannels.route,
                Screen.AllAddresses.route,
                Screen.AllUtxos.route,
            )

    // Get title for sub-screens
    val allAddressesTitle = stringResource(R.string.loc_ed3bf7b5)
    val allUtxosTitle = stringResource(R.string.loc_aefc2317)
    val subScreenTitle =
        when (currentDestination?.route) {
            Screen.AllAddresses.route -> allAddressesTitle
            Screen.AllUtxos.route -> allUtxosTitle
            else -> ""
        }

    val walletUnlockSecurityRequiredMessage = stringResource(R.string.loc_7378b4ca)
    val walletLockEnableSecurityMessage = stringResource(R.string.loc_e440bb19)
    val biometricUnavailableMessage = stringResource(R.string.loc_0039435a)

    // Security state - tracks whether app lock is enabled for the lock icon
    var isSecurityEnabled by remember { mutableStateOf(viewModel.isSecurityEnabled()) }
    val activity = remember(context) { context as? FragmentActivity }
    var pendingWalletUnlock by remember { mutableStateOf<PendingWalletUnlock?>(null) }
    var authorizedLockedWalletId by remember { mutableStateOf<String?>(null) }
    var lastProcessedAppUnlockCounter by remember { mutableIntStateOf(0) }
    var showServerStatusDialog by remember { mutableStateOf(false) }
    var hideFullSyncDialog by remember { mutableStateOf(false) }
    var hideLiquidFullSyncDialog by remember { mutableStateOf(false) }
    var showWelcomeDialog by remember {
        mutableStateOf(!secureStorage.hasSeenWelcome())
    }
    var showAppUpdateOptInDialog by remember {
        mutableStateOf(
            secureStorage.hasSeenWelcome() &&
                !secureStorage.hasSeenAppUpdateOptInPrompt() &&
                !viewModel.isAppUpdateCheckEnabled(),
        )
    }
    var showLiquidEnableInfoDialog by remember { mutableStateOf(false) }
    var showSparkEnableInfoDialog by remember { mutableStateOf(false) }
    var showLightningNodeEnableInfoDialog by remember { mutableStateOf(false) }

    fun showSparkEnableInfoDialogIfNeeded() {
        if (!secureStorage.hasSeenSparkEnableInfo()) {
            secureStorage.setHasSeenSparkEnableInfo(true)
            showSparkEnableInfoDialog = true
        }
    }

    fun showLightningNodeEnableInfoDialogIfNeeded() {
        if (!secureStorage.hasSeenLightningNodeEnableInfo()) {
            secureStorage.setHasSeenLightningNodeEnableInfo(true)
            showLightningNodeEnableInfoDialog = true
        }
    }

    val electrumConfig = viewModel.getElectrumConfig()
    val liquidElectrumConfig = liquidServersState.servers.find {
        it.id == liquidServersState.activeServerId
    }

    // Certificate TOFU dialog
    certDialogState?.let { state ->
        CertificateDialog(
            state = state,
            onAccept = { viewModel.acceptCertificate() },
            onReject = { viewModel.rejectCertificate() },
        )
    }
    if (certDialogState == null) {
        liquidCertDialogState?.let { state ->
            CertificateDialog(
                state = state,
                onAccept = { liquidViewModel.acceptCertificate() },
                onReject = { liquidViewModel.rejectCertificate() },
            )
        }
    }

    LaunchedEffect(walletState.isFullSyncing) {
        if (!walletState.isFullSyncing) {
            hideFullSyncDialog = false
        }
    }

    LaunchedEffect(liquidState.isFullSyncing) {
        if (!liquidState.isFullSyncing) {
            hideLiquidFullSyncDialog = false
        }
    }

    if (walletState.isFullSyncing && !hideFullSyncDialog) {
        FullSyncProgressDialog(
            walletName = walletState.activeWallet?.name,
            progress = walletState.syncProgress,
            onCancel = { viewModel.cancelFullSync() },
            onClose = { hideFullSyncDialog = true },
        )
    } else if ((liquidState.isFullSyncing || pendingLiquidFullSyncProgress != null) && !hideLiquidFullSyncDialog) {
        FullSyncProgressDialog(
            walletName = walletState.activeWallet?.name,
            progress = liquidState.syncProgress ?: pendingLiquidFullSyncProgress,
            accentColor = LiquidTeal,
            onCancel = { liquidViewModel.cancelFullSync() },
            onClose = { hideLiquidFullSyncDialog = true },
        )
    }

    if (showWelcomeDialog) {
        WelcomeDialog(
            currentAppLocale = appLocale,
            onAppLocaleChange = { viewModel.setAppLocale(it) },
            initialAppUpdateCheckEnabled = appUpdateCheckEnabled,
            onDismiss = { enabled ->
                updateAppUpdateCheckEnabled(enabled)
                showWelcomeDialog = false
                secureStorage.setHasSeenWelcome(true)
            },
        )
    }

    if (showAppUpdateOptInDialog && !showWelcomeDialog) {
        IbisConfirmDialog(
            onDismissRequest = {
                secureStorage.setHasSeenAppUpdateOptInPrompt(true)
                showAppUpdateOptInDialog = false
            },
            title = stringResource(R.string.app_update_opt_in_title),
            message = stringResource(R.string.app_update_opt_in_message),
            confirmText = stringResource(R.string.app_update_opt_in_enable),
            dismissText = stringResource(R.string.app_update_opt_in_not_now),
            onDismissAction = {
                secureStorage.setHasSeenAppUpdateOptInPrompt(true)
                showAppUpdateOptInDialog = false
            },
            onConfirm = {
                updateAppUpdateCheckEnabled(true)
                showAppUpdateOptInDialog = false
            },
        )
    }

    if (showLiquidEnableInfoDialog) {
        IbisConfirmDialog(
            onDismissRequest = { showLiquidEnableInfoDialog = false },
            title = stringResource(R.string.liquid_enable_info_title),
            message = stringResource(R.string.liquid_enable_info_message),
            confirmText = stringResource(R.string.liquid_enable_info_confirm),
            showDismissButton = false,
            onConfirm = { showLiquidEnableInfoDialog = false },
        )
    }

    if (showSparkEnableInfoDialog) {
        IbisConfirmDialog(
            onDismissRequest = { showSparkEnableInfoDialog = false },
            title = stringResource(R.string.spark_enable_info_title),
            message = stringResource(R.string.spark_enable_info_message),
            confirmText = stringResource(R.string.spark_enable_info_confirm),
            showDismissButton = false,
            onConfirm = { showSparkEnableInfoDialog = false },
        )
    }

    if (showLightningNodeEnableInfoDialog) {
        IbisConfirmDialog(
            onDismissRequest = { showLightningNodeEnableInfoDialog = false },
            title = stringResource(R.string.ln_node_enable_info_title),
            message = stringResource(R.string.ln_node_enable_info_message),
            confirmText = stringResource(R.string.ln_node_enable_info_confirm),
            showDismissButton = false,
            onConfirm = { showLightningNodeEnableInfoDialog = false },
        )
    }

    if (showPrivacyModeHintDialog) {
        IbisConfirmDialog(
            onDismissRequest = { dismissPrivacyModeHint() },
            title = stringResource(R.string.privacy_mode_hint_title),
            message = stringResource(R.string.privacy_mode_hint_message),
            confirmText = stringResource(R.string.liquid_enable_info_confirm),
            showDismissButton = false,
            onConfirm = { dismissPrivacyModeHint() },
        )
    }

    appUpdatePrompt?.let { prompt ->
        IbisConfirmDialog(
            onDismissRequest = { viewModel.dismissAppUpdatePrompt() },
            title = stringResource(R.string.update_popup_title),
            message = stringResource(R.string.update_popup_message, prompt.latestVersionName),
            confirmText = stringResource(R.string.update_popup_view),
            dismissText = stringResource(R.string.update_popup_close),
            onDismissAction = { viewModel.dismissAppUpdatePrompt() },
            onConfirm = {
                viewModel.dismissAppUpdatePrompt()
                context.startActivity(Intent(Intent.ACTION_VIEW, prompt.releaseUrl.toUri()))
            },
        )
    }

    // Existing wallet names for auto-naming on import/generate screens
    val existingWalletNames = remember(walletState.wallets) { walletState.wallets.map { it.name } }

    // Filter wallets based on duress mode:
    // - In duress mode: show only the duress wallet
    // - Not in duress mode: hide the duress wallet
    val duressWalletId = remember(isDuressMode) { viewModel.getDuressWalletId() }
    val filteredWallets =
        remember(walletState.wallets, isDuressMode, duressWalletId) {
            if (isDuressMode) {
                walletState.wallets.filter { it.id == duressWalletId }
            } else {
                walletState.wallets.filter { it.id != duressWalletId }
            }
        }

    // Build wallet list for ManageWallets screen from filtered wallets
    val activeWalletId = walletState.activeWallet?.id
    val effectiveWalletLastFullSyncTimes =
        remember(walletState.wallets, walletLastFullSyncTimes, walletState.lastSyncTimestamp) {
            walletState.wallets.associate { storedWallet ->
                storedWallet.id to
                    (
                        walletLastFullSyncTimes[storedWallet.id]
                            ?: viewModel.getLastFullSyncTime(storedWallet.id)
                    )
            }
        }
    val lightningNodeTitle = stringResource(R.string.ln_node_title)
    val lightningListCopy =
        github.aeonbtc.ibiswallet.data.model.LightningNodeListCopy(
            serverFormat = stringResource(R.string.ln_node_list_server_format),
            serverNotConfigured = stringResource(R.string.ln_node_list_server_not_configured),
            portFormat = stringResource(R.string.ln_node_list_port_format),
            relayFormat = stringResource(R.string.ln_node_list_relay_format),
            configured = stringResource(R.string.ln_node_list_configured),
            notConfigured = stringResource(R.string.ln_node_list_not_configured),
            modeHttp = stringResource(R.string.ln_node_list_mode_http),
            modeSsl = stringResource(R.string.ln_node_list_mode_ssl),
            modeSslPin = stringResource(R.string.ln_node_list_mode_ssl_pin),
            pubkeyFormat = stringResource(R.string.ln_node_list_pubkey_format),
            addressFormat = stringResource(R.string.ln_node_list_address_format),
        )
    val wallets =
        remember(
            filteredWallets,
            activeWalletId,
            liquidGapLimits,
            effectiveWalletLastFullSyncTimes,
            lightningNodeTitle,
            lightningListCopy,
            lightningConfigRevision,
        ) {
            filteredWallets.map { storedWallet ->
                val isWatchAddress = storedWallet.derivationPath == "single" && storedWallet.isWatchOnly
                val isPrivateKey = storedWallet.derivationPath == "single" && !storedWallet.isWatchOnly
                val isMultisig = storedWallet.policyType == WalletPolicyType.MULTISIG
                val isLnNode =
                    storedWallet.walletKind == github.aeonbtc.ibiswallet.data.model.WalletKind.LIGHTNING_NODE
                val lnConfig =
                    if (isLnNode) {
                        lightningNodeViewModel.getConfig(storedWallet.id)
                    } else {
                        null
                    }
                WalletInfo(
                    id = storedWallet.id,
                    name = storedWallet.name,
                    type =
                        if (isLnNode) {
                            "lightning_node"
                        } else if (isMultisig) {
                            "multisig"
                        } else {
                            storedWallet.addressType.name.lowercase()
                        },
                    typeDescription =
                        if (isLnNode) {
                            lnConfig?.listTypeLabel(lightningNodeTitle) ?: lightningNodeTitle
                        } else if (isMultisig) {
                            "${storedWallet.multisigThreshold ?: 0}-of-${storedWallet.multisigTotalCosigners ?: 0}"
                        } else {
                            storedWallet.addressType.displayName
                        },
                    derivationPath = storedWallet.derivationPath,
                    seedFormat = storedWallet.seedFormat,
                    isActive = storedWallet.id == activeWalletId,
                    isWatchOnly = storedWallet.isWatchOnly,
                    isLocked = storedWallet.isLocked,
                    isWatchAddress = isWatchAddress,
                    isPrivateKey = isPrivateKey,
                    isMultisig = isMultisig,
                    lastFullSyncTime = effectiveWalletLastFullSyncTimes[storedWallet.id],
                    masterFingerprint = storedWallet.masterFingerprint,
                    gapLimit = storedWallet.gapLimit,
                    liquidGapLimit = liquidViewModel.getLiquidGapLimit(storedWallet.id),
                    isLiquidWatchOnly = liquidViewModel.isLiquidWatchOnly(storedWallet.id),
                    isLightningNode = isLnNode,
                    lightningTypeLabel = lnConfig?.listTypeLabel(lightningNodeTitle),
                    lightningDetail = lnConfig?.listDetailLine(lightningListCopy),
                    lightningPort = lnConfig?.listPortLine(lightningListCopy),
                    lightningMeta = lnConfig?.listMetaLine(lightningListCopy),
                )
            }
        }

    fun completeWalletSelection(request: PendingWalletUnlock) {
        authorizedLockedWalletId = if (request.isLocked) request.walletId else null
        pendingMainWalletId = request.walletId
        pendingMainLayer = request.targetLayer
        liquidViewModel.setActiveLayer(request.targetLayer)
        if (request.walletId != activeWalletId) {
            viewModel.switchWallet(request.walletId)
        }
        if (request.navigateToBalance) {
            navController.navigate(Screen.Balance.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    fun requestWalletSelection(walletId: String, navigateToBalance: Boolean = false) {
        val wallet = filteredWallets.find { it.id == walletId } ?: return
        val isWalletLiquidWatchOnly = liquidViewModel.isLiquidWatchOnly(walletId)
        val isWalletLiquidEnabled =
            liquidEnabledWallets[walletId] ?: liquidViewModel.isLiquidEnabledForWallet(walletId)
        val targetLayer =
            if (isLayer2Enabled && isWalletLiquidWatchOnly && isWalletLiquidEnabled) {
                WalletLayer.LAYER2
            } else {
                runCatching { WalletLayer.valueOf(secureStorage.getActiveLayer(walletId)) }
                    .getOrDefault(WalletLayer.LAYER1)
            }
        val request =
            PendingWalletUnlock(
                walletId = walletId,
                walletName = wallet.name,
                isLocked = wallet.isLocked,
                purpose = WalletAuthPurpose.OPEN_WALLET,
                targetLayer = targetLayer,
                navigateToBalance = navigateToBalance,
                securityMethod = viewModel.getSecurityMethod(),
            )

        val needsAuth = wallet.isLocked && authorizedLockedWalletId != walletId
        if (!needsAuth) {
            completeWalletSelection(request)
            return
        }

        if (request.securityMethod == SecureStorage.SecurityMethod.NONE) {
            scope.launch {
                snackbarHostState.showSnackbar(walletUnlockSecurityRequiredMessage)
            }
            return
        }

        pendingWalletUnlock = request
    }

    fun requestDisableWalletLock(walletId: String) {
        val wallet = filteredWallets.find { it.id == walletId } ?: return
        val request =
            PendingWalletUnlock(
                walletId = walletId,
                walletName = wallet.name,
                isLocked = wallet.isLocked,
                purpose = WalletAuthPurpose.DISABLE_LOCK,
                targetLayer = pendingMainLayer,
                navigateToBalance = false,
                securityMethod = viewModel.getSecurityMethod(),
            )

        if (request.securityMethod == SecureStorage.SecurityMethod.NONE) {
            viewModel.setWalletLocked(walletId, false)
            if (authorizedLockedWalletId == walletId) {
                authorizedLockedWalletId = null
            }
            return
        }

        pendingWalletUnlock = request
    }

    fun enableWalletLock(walletId: String) {
        if (!isSecurityEnabled) {
            scope.launch {
                snackbarHostState.showSnackbar(walletLockEnableSecurityMessage)
            }
            return
        }

        val isActiveWallet = walletId == activeWalletId
        if (!isActiveWallet) {
            viewModel.setWalletLocked(walletId, true)
            return
        }

        authorizedLockedWalletId = null
        pendingWalletUnlock = null

        val fallbackWalletId =
            filteredWallets.firstOrNull { it.id != walletId && !it.isLocked }?.id

        if (fallbackWalletId != null) {
            requestWalletSelection(fallbackWalletId)
        } else {
            pendingMainWalletId = null
            pendingMainLayer = WalletLayer.LAYER1
            liquidViewModel.setActiveLayer(WalletLayer.LAYER1)
            navController.navigate(Screen.ManageWallets.route) {
                launchSingleTop = true
            }
        }

        viewModel.setWalletLocked(walletId, true)
    }

    fun completeWalletAuth(request: PendingWalletUnlock) {
        when (request.purpose) {
            WalletAuthPurpose.OPEN_WALLET -> completeWalletSelection(request)
            WalletAuthPurpose.DISABLE_LOCK -> {
                if (authorizedLockedWalletId == request.walletId) {
                    authorizedLockedWalletId = null
                }
                viewModel.setWalletLocked(request.walletId, false)
            }
        }
    }

    fun cancelWalletUnlock(request: PendingWalletUnlock) {
        pendingWalletUnlock = null
        if (request.purpose == WalletAuthPurpose.OPEN_WALLET &&
            request.walletId == activeWalletId &&
            currentDestination?.route != Screen.ManageWallets.route
        ) {
            navController.navigate(Screen.ManageWallets.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(appUnlockCounter, activeWalletId, walletState.activeWallet?.isLocked) {
        val activeWallet = walletState.activeWallet ?: return@LaunchedEffect
        if (appUnlockCounter == 0 || appUnlockCounter == lastProcessedAppUnlockCounter) return@LaunchedEffect
        if (activeWallet.isLocked) {
            authorizedLockedWalletId = activeWallet.id
        }
        lastProcessedAppUnlockCounter = appUnlockCounter
    }

    LaunchedEffect(activeWalletId, walletState.activeWallet?.isLocked, authorizedLockedWalletId, requiresActiveWalletAuth) {
        val activeWallet = walletState.activeWallet ?: return@LaunchedEffect
        if (!requiresActiveWalletAuth) return@LaunchedEffect
        if (!activeWallet.isLocked) return@LaunchedEffect
        if (pendingMainWalletId != null && pendingMainWalletId != activeWallet.id) return@LaunchedEffect
        if (authorizedLockedWalletId == activeWallet.id || pendingWalletUnlock?.walletId == activeWallet.id) return@LaunchedEffect
        requestWalletSelection(activeWallet.id)
    }

    pendingWalletUnlock?.let { request ->
        if (request.securityMethod == SecureStorage.SecurityMethod.PIN) {
            LockScreen(
                securityMethod = SecureStorage.SecurityMethod.PIN,
                randomizePinPad = secureStorage.getRandomizePinPad(),
                isBiometricAvailable = false,
                onBiometricRequest = {},
                onPinEntered = { pin ->
                    if (secureStorage.verifyPin(pin)) {
                        pendingWalletUnlock = null
                        completeWalletAuth(request)
                        true
                    } else {
                        false
                    }
                },
            )
            return
        }
    }

    LaunchedEffect(pendingWalletUnlock?.walletId, pendingWalletUnlock?.securityMethod) {
        val request = pendingWalletUnlock ?: return@LaunchedEffect
        if (request.securityMethod != SecureStorage.SecurityMethod.BIOMETRIC) return@LaunchedEffect
        if (activity == null) {
            pendingWalletUnlock = null
            snackbarHostState.showSnackbar(biometricUnavailableMessage)
            return@LaunchedEffect
        }
        val promptTitle =
            if (request.purpose == WalletAuthPurpose.DISABLE_LOCK) {
                activity.getString(R.string.loc_5a6ca12a)
            } else {
                activity.getString(R.string.loc_4d44286e)
            }
        val promptSubtitle =
            if (request.purpose == WalletAuthPurpose.DISABLE_LOCK) {
                activity.getString(R.string.loc_b1d09d26)
            } else {
                activity.getString(R.string.loc_b543e2d2)
            }
        val promptCancel = activity.getString(R.string.loc_51bac044)
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        if (result.cryptoObject == null) {
                            pendingWalletUnlock = null
                            scope.launch {
                                snackbarHostState.showSnackbar(biometricUnavailableMessage)
                            }
                            return
                        }
                        runCatching {
                            result.cryptoObject?.cipher?.let(secureStorage::unlockSpendSecretsWithBiometric)
                        }.onFailure {
                            pendingWalletUnlock = null
                            scope.launch {
                                snackbarHostState.showSnackbar(biometricUnavailableMessage)
                            }
                            return
                        }
                        pendingWalletUnlock = null
                        completeWalletAuth(request)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        cancelWalletUnlock(request)
                    }
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setNegativeButtonText(promptCancel)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        val cryptoObject =
            withContext(Dispatchers.Default) {
                runCatching { secureStorage.createSpendSecretBiometricCryptoObject() }.getOrNull()
            }
        if (cryptoObject == null) {
            pendingWalletUnlock = null
            snackbarHostState.showSnackbar(biometricUnavailableMessage)
            return@LaunchedEffect
        }
        prompt.authenticate(promptInfo, cryptoObject)
    }

    // Get string resources for use in event handling
    val walletAddedMessage = stringResource(R.string.wallet_added)
    val lightningNodeWalletAddedFormat = stringResource(R.string.ln_node_wallet_added_format)
    val walletOperationFailedMessage = stringResource(R.string.wallet_operation_failed)
    val transactionSentMessage = stringResource(R.string.loc_54947bc7)
    val walletExportedMessage = stringResource(R.string.loc_a10f1671)
    val bip329ExportedMessage = stringResource(R.string.loc_c5473c1d)
    val bip329ImportedMessage = stringResource(R.string.loc_a70e9a3b)
    val feeBumpedMessage = stringResource(R.string.loc_a0665acf)
    val cpfpCreatedMessage = stringResource(R.string.loc_84d585b7)
    val transactionRedirectedMessage = stringResource(R.string.loc_331c24c8)
    val liquidOperationFailedMessage = stringResource(R.string.liquid_operation_failed)
    val liquidTransactionSentMessage = stringResource(R.string.loc_38a633d4)
    val lightningReceivedTitle = stringResource(R.string.loc_4defd854)
    val lightningReceivedBody = stringResource(R.string.loc_e3dea6d5)
    val lightningSentMessage = stringResource(R.string.loc_dc9ca216)
    val swapCompletedTitle = stringResource(R.string.loc_932fd4d9)
    val swapCompletedBody = stringResource(R.string.loc_1b2f100c)
    val layer1ReceiveNotificationTitle = stringResource(R.string.loc_a11d4b84)
    val receiveNotificationBody = stringResource(R.string.loc_0ab66fae)
    val layer2ReceiveNotificationTitle = stringResource(R.string.loc_7cf79a48)
    val sparkPaymentReceivedTitle = stringResource(R.string.loc_739b859d)
    val sparkReceiveNotificationTitle = stringResource(R.string.spark_payment_received)
    val lnNodePaymentReceivedTitle = stringResource(R.string.loc_739b859d)
    val lnNodeReceiveNotificationTitle = stringResource(R.string.ln_node_payment_received)
    val suppressWalletServerSnackbar: (String) -> Boolean = { message ->
        message == "Failed to connect to server" ||
            message == "Not connected to Electrum server" ||
            message == "Connection timed out" ||
            message == "Connection lost" ||
            message == "Server connection lost" ||
            message == "Tor connection timed out" ||
            message.startsWith("Tor failed to start:") ||
            message.startsWith("Auto-switched to ") ||
            message == "Auto-switch failed: no reachable servers"
    }
    val suppressLiquidServerSnackbar: (String) -> Boolean = { message ->
        message.startsWith("Connection failed:") ||
            message == "Not connected to Electrum" ||
            message == "Tor connection timed out" ||
            message.startsWith("Tor failed to start:") ||
            message.startsWith("Switching Liquid server to ")
    }

    // Handle events - show notifications and navigate as needed
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletEvent.Error -> {
                    if (!suppressWalletServerSnackbar(event.message)) {
                        snackbarHostState.showSnackbar(
                            event.message.ifBlank {
                                walletOperationFailedMessage
                            },
                        )
                    }
                }
                is WalletEvent.WalletImported -> {
                    liquidViewModel.reloadRestoredSettings()
                    sparkViewModel.reloadRestoredSettings()
                    lightningNodeViewModel.reloadRestoredSettings()
                    Toast.makeText(context, walletAddedMessage, Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Balance.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is WalletEvent.LightningNodeWalletCreated -> {
                    liquidViewModel.reloadRestoredSettings()
                    sparkViewModel.reloadRestoredSettings()
                    lightningNodeViewModel.reloadRestoredSettings()
                    Toast.makeText(
                        context,
                        lightningNodeWalletAddedFormat.format(event.walletName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is WalletEvent.TransactionSent -> {
                    Toast.makeText(
                        context,
                        transactionSentMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                    navController.navigate(Screen.Balance.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is WalletEvent.PsbtCreated -> {
                    navController.navigate(Screen.PsbtExport.route)
                }
                is WalletEvent.WalletExported -> {
                    Toast.makeText(context, walletExportedMessage, Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.Bip329LabelsExported -> {
                    Toast.makeText(context, bip329ExportedMessage, Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.Bip329LabelsImported -> {
                    Toast.makeText(context, bip329ImportedMessage, Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.FeeBumped -> {
                    Toast.makeText(context, feeBumpedMessage, Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.CpfpCreated -> {
                    Toast.makeText(context, cpfpCreatedMessage, Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.TransactionRedirected -> {
                    Toast.makeText(context, transactionRedirectedMessage, Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.SyncCompleted,
                is WalletEvent.WalletSwitched,
                is WalletEvent.LabelsRestored,
                -> {
                    if (event is WalletEvent.LabelsRestored) {
                        liquidViewModel.refreshCachedWalletState()
                    }
                }
                // Other success events - no notification needed, UI updates reflect the change
                is WalletEvent.Connected,
                is WalletEvent.WalletDeleted,
                is WalletEvent.ServerDeleted,
                is WalletEvent.AddressGenerated,
                -> { }
            }
        }
    }

    LaunchedEffect(isLiquidAvailable) {
        liquidViewModel.setLiquidContextActive(isLiquidAvailable)
    }

    LaunchedEffect(isLiquidAvailable) {
        if (!isLiquidAvailable) return@LaunchedEffect
        liquidViewModel.events.collect { event ->
            when (event) {
                is LiquidEvent.Error -> {
                    if (!suppressLiquidServerSnackbar(event.message)) {
                        snackbarHostState.showSnackbar(liquidOperationFailedMessage)
                    }
                }
                is LiquidEvent.TransactionSent -> {
                    liquidViewModel.clearSendDraft()
                    liquidViewModel.cancelPsetFlow()
                    if (navController.currentDestination?.route == Screen.LiquidPsetExport.route) {
                        navController.popBackStack()
                    }
                    Toast.makeText(context, liquidTransactionSentMessage, Toast.LENGTH_SHORT).show()
                }
                is LiquidEvent.LightningReceived -> {
                    postWalletNotification(
                        key = "lightning-received-${event.txid}",
                        title = lightningReceivedTitle,
                        body = lightningReceivedBody,
                    )
                    Toast.makeText(context, lightningReceivedTitle, Toast.LENGTH_SHORT).show()
                }
                is LiquidEvent.LightningSent -> {
                    liquidViewModel.clearSendDraft()
                    Toast.makeText(context, lightningSentMessage, Toast.LENGTH_SHORT).show()
                }
                is LiquidEvent.SwapCompleted -> {
                    viewModel.sync()
                    postWalletNotification(
                        key = "swap-completed-${event.swapId}",
                        title = swapCompletedTitle,
                        body = swapCompletedBody,
                    )
                    Toast.makeText(context, swapCompletedTitle, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(isSparkAvailable) {
        if (!isSparkAvailable) return@LaunchedEffect
        sparkViewModel.events.collect { event ->
            when (event) {
                is SparkEvent.PaymentReceived -> {
                    postWalletNotification(
                        key = "spark-receive-${event.paymentId}",
                        title = sparkReceiveNotificationTitle,
                        body = receiveNotificationBody,
                    )
                    Toast.makeText(context, sparkPaymentReceivedTitle, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(isLightningAvailable) {
        if (!isLightningAvailable) return@LaunchedEffect
        lightningNodeViewModel.events.collect { event ->
            when (event) {
                is LightningNodeEvent.PaymentReceived -> {
                    postWalletNotification(
                        key = "ln-node-receive-${event.paymentId}",
                        title = lnNodeReceiveNotificationTitle,
                        body = receiveNotificationBody,
                    )
                    Toast.makeText(context, lnNodePaymentReceivedTitle, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(
        walletState.activeWallet?.id,
        walletState.transactions,
        walletState.isTransactionHistoryLoading,
        walletNotificationsEnabled,
        initialSyncComplete,
    ) {
        if (!initialSyncComplete) return@LaunchedEffect
        val walletId = walletState.activeWallet?.id ?: return@LaunchedEffect
        val currentTransactions = walletState.transactions

        val persistedTxids = secureStorage.getNotifiedTxids(walletId)
        val currentTxids = currentTransactions.map { it.txid }.toSet()
        if (walletState.isTransactionHistoryLoading) {
            secureStorage.saveNotifiedTxids(walletId, persistedTxids + currentTxids)
            secureStorage.setNotifiedTxidsBaseline(walletId, true)
            return@LaunchedEffect
        }
        val trackingUpdate =
            WalletNotificationPolicy.updateTrackedTransactions(
                currentTxids = currentTxids,
                trackedTxids = persistedTxids,
                baselineEstablished = secureStorage.hasNotifiedTxidsBaseline(walletId),
            )

        secureStorage.saveNotifiedTxids(walletId, trackingUpdate.trackedTxids)
        secureStorage.setNotifiedTxidsBaseline(walletId, trackingUpdate.baselineEstablished)

        if (!walletNotificationsEnabled || trackingUpdate.notifyTxids.isEmpty()) {
            return@LaunchedEffect
        }

        val newIncomingTransactions =
            currentTransactions.filter { tx ->
                tx.amountSats > 0 && tx.txid in trackingUpdate.notifyTxids
            }
        if (newIncomingTransactions.isEmpty()) return@LaunchedEffect

        newIncomingTransactions.forEach { tx ->
            postWalletNotification(
                key = "l1-receive-${walletId}-${tx.txid}",
                title = layer1ReceiveNotificationTitle,
                body = receiveNotificationBody,
            )
        }
    }

    LaunchedEffect(
        loadedLiquidWalletId,
        liquidState.transactions,
        walletNotificationsEnabled,
        initialLiquidSyncComplete,
    ) {
        if (!initialLiquidSyncComplete) return@LaunchedEffect
        val walletId = loadedLiquidWalletId ?: return@LaunchedEffect
        val currentTransactions = liquidState.transactions

        val persistedTxids = secureStorage.getNotifiedLiquidTxids(walletId)
        val currentTxids = currentTransactions.map { it.txid }.toSet()
        val trackingUpdate =
            WalletNotificationPolicy.updateTrackedTransactions(
                currentTxids = currentTxids,
                trackedTxids = persistedTxids,
                baselineEstablished = secureStorage.hasNotifiedLiquidTxidsBaseline(walletId),
            )

        secureStorage.saveNotifiedLiquidTxids(walletId, trackingUpdate.trackedTxids)
        secureStorage.setNotifiedLiquidTxidsBaseline(walletId, trackingUpdate.baselineEstablished)

        if (!walletNotificationsEnabled || trackingUpdate.notifyTxids.isEmpty()) {
            return@LaunchedEffect
        }

        val newIncomingTransactions =
            currentTransactions.filter { tx ->
                tx.balanceSatoshi > 0 &&
                    tx.type == github.aeonbtc.ibiswallet.data.model.LiquidTxType.RECEIVE &&
                    tx.source == LiquidTxSource.NATIVE &&
                    tx.txid in trackingUpdate.notifyTxids
            }
        if (newIncomingTransactions.isEmpty()) return@LaunchedEffect

        newIncomingTransactions.forEach { tx ->
            postWalletNotification(
                key = "l2-receive-${walletId}-${tx.txid}",
                title = layer2ReceiveNotificationTitle,
                body = receiveNotificationBody,
            )
        }
    }

    LaunchedEffect(
        liquidState.transactions,
        visibleSparkState.payments,
        visibleSparkState.unclaimedDeposits,
        visibleLightningState.payments,
        lightningOnchainState.transactions,
    ) {
        viewModel.setExternalHistoricalTxTimestamps(
            buildMap {
                liquidState.transactions.forEach { tx ->
                    put(tx.txid, tx.timestamp)
                }
                visibleSparkState.payments.forEach { payment ->
                    put(payment.id, payment.timestamp)
                }
                visibleSparkState.unclaimedDeposits.forEach { deposit ->
                    put(deposit.txid, deposit.timestamp)
                }
                visibleLightningState.payments.forEach { payment ->
                    put(payment.id, payment.timestamp)
                    payment.paymentHash?.takeIf { it.isNotBlank() }?.let { hash ->
                        put(hash, payment.timestamp)
                    }
                }
                lightningOnchainState.transactions.forEach { tx ->
                    put(tx.txid, tx.timestamp)
                }
            },
        )
    }

    LaunchedEffect(historicalTxFiatEnabled) {
        if (!historicalTxFiatEnabled) {
            showHistoricalTxPrices = false
        }
    }

    // ── Layer 2 (Liquid) wallet lifecycle ──
    // Load/unload the Liquid wallet when:
    // - The active Bitcoin wallet changes
    // - Layer 2 is enabled/disabled
    // - Per-wallet Liquid toggle changes
    LaunchedEffect(walletState.activeWallet?.id, isLayer2Enabled) {
        val activeWallet = walletState.activeWallet ?: return@LaunchedEffect
        val walletId = activeWallet.id

        if (!isLayer2Enabled) {
            liquidViewModel.unloadLiquidWallet()
            return@LaunchedEffect
        }

        if (!liquidViewModel.isLiquidEnabledForWallet(walletId)) {
            liquidViewModel.unloadLiquidWallet()
            return@LaunchedEffect
        }

        // Non-Liquid-watch-only wallets still require a mnemonic (BIP39 wallets only)
        if (activeWallet.isWatchOnly && !liquidViewModel.isLiquidWatchOnly(walletId)) {
            liquidViewModel.unloadLiquidWallet()
            return@LaunchedEffect
        }

        // Load (or initialize on first use) the Liquid wallet from cached state,
        // letting the repository fetch key material lazily if needed.
        liquidViewModel.loadLiquidWallet(walletId)
        if (boltzApiSource != SecureStorage.BOLTZ_API_DISABLED) {
            liquidViewModel.requestBoltzWarmupAtAppStart()
        }
    }

    LaunchedEffect(walletState.activeWallet?.id, isSparkLayer2Enabled, activeLayer2Provider) {
        val activeWallet = walletState.activeWallet ?: return@LaunchedEffect
        val walletId = activeWallet.id

        if (!isSparkLayer2Enabled || activeLayer2Provider != Layer2Provider.SPARK) {
            sparkViewModel.unloadSparkWallet()
            return@LaunchedEffect
        }

        liquidViewModel.loadActiveLayer(walletId)
        sparkViewModel.loadSparkWallet(walletId)
    }

    LaunchedEffect(walletState.activeWallet?.id, isLightningNodeLayer2Enabled, activeLayer2Provider) {
        val activeWallet = walletState.activeWallet
        if (
            activeWallet == null ||
            !isLightningNodeLayer2Enabled ||
            activeLayer2Provider != Layer2Provider.LIGHTNING
        ) {
            lightningNodeViewModel.unloadLightningWallet()
            return@LaunchedEffect
        }

        val walletId = activeWallet.id
        liquidViewModel.loadActiveLayer(walletId)
        lightningNodeViewModel.loadLightningWallet(walletId)
    }

    LaunchedEffect(activeWalletObj?.id, isLiquidAvailable, isActiveWalletLiquidWatchOnly, activeLayer) {
        val walletId = activeWalletObj?.id ?: return@LaunchedEffect
        if (isLiquidAvailable && isActiveWalletLiquidWatchOnly && activeLayer != WalletLayer.LAYER2) {
            liquidViewModel.setActiveLayer(WalletLayer.LAYER2, walletId)
        }
    }

    // On selecting an NWC wallet, start on Layer 2 once. Pills stay fully usable.
    LaunchedEffect(activeWalletObj?.id) {
        val walletId = activeWalletObj?.id ?: return@LaunchedEffect
        if (!isLightningAvailable) return@LaunchedEffect
        val type = lightningNodeViewModel.getConfig(walletId).type
        if (type == github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType.NWC) {
            liquidViewModel.setActiveLayer(WalletLayer.LAYER2, walletId)
        }
    }

    val activeMainWalletId = walletState.activeWallet?.id
    val isPendingLayerReady =
        when (pendingMainLayer) {
            WalletLayer.LAYER1 -> activeLayer == WalletLayer.LAYER1
            WalletLayer.LAYER2 ->
                if (!isLayer2Available) {
                    true
                } else if (isLightningAvailable) {
                    activeLayer == WalletLayer.LAYER2 &&
                        loadedLightningWalletId == pendingMainWalletId &&
                        (lightningNodeState.isInitialized || lightningNodeState.error != null)
                } else if (isSparkAvailable) {
                    activeLayer == WalletLayer.LAYER2 &&
                        loadedSparkWalletId == pendingMainWalletId &&
                        (sparkState.isInitialized || sparkState.error != null)
                } else {
                    activeLayer == WalletLayer.LAYER2 &&
                        loadedLiquidWalletId == pendingMainWalletId &&
                        (liquidState.isInitialized || liquidState.error != null)
                }
        }
    val shouldBlockMainContent =
        pendingMainWalletId != null &&
            uiState.error == null &&
            (
                activeMainWalletId != pendingMainWalletId ||
                    (
                        !hasCompletedInitialMainLoad ||
                            pendingMainLayer != WalletLayer.LAYER2
                    ) &&
                    !isPendingLayerReady
            )

    LaunchedEffect(pendingMainWalletId, shouldBlockMainContent) {
        if (pendingMainWalletId != null && !shouldBlockMainContent) {
            pendingMainWalletId = null
        }
    }

    LaunchedEffect(shouldBlockMainContent) {
        if (!shouldBlockMainContent) {
            hasCompletedInitialMainLoad = true
        }
    }

    if (shouldBlockMainContent && !hasCompletedInitialMainLoad) {
        AppLaunchLoadingScreen()
        return
    }

    val isElectrumTorBootstrapping =
        uiState.isConnecting &&
            electrumConfig?.isOnionAddress() == true &&
            torState.status != TorStatus.CONNECTED
    val isLiquidTorBootstrapping =
        showLiquidConnecting &&
            liquidElectrumConfig?.isOnionAddress() == true &&
            liquidTorState.status != TorStatus.CONNECTED
    val electrumStatusColor =
        when {
            isElectrumTorBootstrapping -> TorPurple
            uiState.isConnecting -> BitcoinOrange
            uiState.isConnected -> SuccessGreen
            uiState.error != null -> ErrorRed
            else -> TextSecondary
        }
    val liquidStatusColor =
        when {
            isLiquidTorBootstrapping -> TorPurple
            showLiquidConnecting -> BitcoinOrange
            isLiquidConnected -> SuccessGreen
            liquidConnectionError != null -> ErrorRed
            else -> TextSecondary
        }
    val layer2StatusColor =
        when {
            isLightningAvailable ->
                when {
                    visibleLightningConnecting -> BitcoinOrange
                    visibleLightningConnected -> SuccessGreen
                    visibleLightningState.error != null -> ErrorRed
                    else -> TextSecondary
                }
            isSparkAvailable ->
                when {
                    visibleSparkConnecting -> BitcoinOrange
                    visibleSparkConnected -> SuccessGreen
                    else -> TextSecondary
                }
            else -> liquidStatusColor
        }
    val headerStatusChromeColor =
        when {
            isLiquidAvailable && uiState.isConnected && isLiquidConnected -> SuccessGreen
            isLiquidAvailable && (showLiquidConnecting || uiState.isConnecting) -> BitcoinOrange
            isLiquidAvailable && (uiState.isConnected || isLiquidConnected) -> SuccessGreen
            // Lightning Node wallets do not use Electrum — chrome follows LN only.
            isLightningAvailable && visibleLightningConnecting -> BitcoinOrange
            isLightningAvailable && visibleLightningConnected -> SuccessGreen
            isLightningAvailable && visibleLightningState.error != null -> ErrorRed
            isLightningAvailable && !visibleLightningConnected -> TextSecondary
            isSparkAvailable && (visibleSparkConnecting || uiState.isConnecting) -> BitcoinOrange
            isSparkAvailable && uiState.isConnected && visibleSparkConnected -> SuccessGreen
            isSparkAvailable && (uiState.isConnecting || !visibleSparkConnected) -> TextSecondary
            uiState.isConnecting -> BitcoinOrange
            else -> electrumStatusColor
        }

    @Composable
    fun StatusDot(color: Color) {
        Box(
            modifier =
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color),
        )
    }

    @Composable
    fun HeaderSettingsCog(onClick: () -> Unit) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.loc_c79ab944),
            tint = TextSecondary,
            modifier =
                Modifier
                    .size(18.dp)
                    .clickable(onClick = onClick),
        )
    }

    @Composable
    fun LightningNodeConnectionStatusCard(
        state: github.aeonbtc.ibiswallet.data.model.LightningNodeWalletState,
        isConnected: Boolean,
        isConnecting: Boolean,
        onOpenSettings: () -> Unit,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onCancelConnection: () -> Unit,
    ) {
        val statusColor =
            when {
                isConnecting -> BitcoinOrange
                isConnected -> SuccessGreen
                state.error != null -> ErrorRed
                else -> TextSecondary
            }
        val borderColor =
            when {
                isConnected -> SuccessGreen
                isConnecting -> BitcoinOrange
                else -> BorderColor
            }
        val transportType =
            when (state.connectionType) {
                github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType.LND_REST -> "LND"
                github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType.NWC -> "NWC"
                github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType.CLN_REST -> "CLN"
                else -> null
            }
        val displayName =
            state.nodeAlias?.takeIf { it.isNotBlank() }
                ?: activeWalletObj?.name
                ?: stringResource(R.string.ln_node_title)
        val host = state.host?.takeIf { it.isNotBlank() }
        val hasEndpoint = host != null || transportType != null

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = LightningYellow,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.ln_node_pill_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        HeaderSettingsCog(onClick = onOpenSettings)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val canConnect = hasEndpoint && !isConnected && !isConnecting
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (canConnect) BorderColor else statusColor,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .background(
                                        if (canConnect) {
                                            DarkBackground
                                        } else {
                                            statusColor.copy(alpha = 0.15f)
                                        },
                                    )
                                    .then(
                                        if (canConnect) {
                                            Modifier.clickable(onClick = onConnect)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = statusColor,
                                        strokeWidth = 1.5.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text =
                                        when {
                                            isConnecting -> stringResource(R.string.loc_066df953)
                                            isConnected -> stringResource(R.string.loc_98469a16)
                                            else -> stringResource(R.string.loc_bb72c083)
                                        },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                )
                            }
                        }

                        if (isConnected || isConnecting) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, ErrorRed, RoundedCornerShape(6.dp))
                                        .background(ErrorRed)
                                        .clickable(
                                            onClick =
                                                if (isConnecting) {
                                                    onCancelConnection
                                                } else {
                                                    onDisconnect
                                                },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription =
                                        if (isConnecting) {
                                            stringResource(R.string.loc_51bac044)
                                        } else {
                                            stringResource(R.string.loc_4f674841)
                                        },
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onError,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (hasEndpoint) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isConnected -> SuccessGreen.copy(alpha = 0.08f)
                                        isConnecting -> BitcoinOrange.copy(alpha = 0.08f)
                                        else -> DarkSurfaceVariant
                                    },
                                )
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenSettings)
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_3cf29f70),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.width(72.dp),
                            )
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (
                                state.connectionType ==
                                github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType.LND_REST ||
                                state.connectionType ==
                                github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType.CLN_REST
                            ) {
                                github.aeonbtc.ibiswallet.ui.screens.ProtocolBadge(useSsl = state.useTls)
                            } else if (transportType != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = LightningYellow.copy(alpha = 0.15f),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            LightningYellow.copy(alpha = 0.4f),
                                        ),
                                ) {
                                    Text(
                                        text = transportType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LightningYellow,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp,
                                            ),
                                    )
                                }
                            }
                            if (state.useTor) {
                                Spacer(modifier = Modifier.width(6.dp))
                                github.aeonbtc.ibiswallet.ui.screens.TorBadge()
                            }
                        }
                        host?.let {
                            github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                                label = stringResource(R.string.loc_77caa9b0),
                                value = it,
                                monospace = true,
                            )
                        }
                        state.port?.takeIf { it > 0 }?.let { port ->
                            github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                                label = stringResource(R.string.loc_475e06fd),
                                value = port.toString(),
                                monospace = true,
                            )
                        }
                        if (isConnected) {
                            state.nodeVersion?.takeIf { it.isNotBlank() }?.let { version ->
                                github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                                    label = stringResource(R.string.loc_9d53bbd5),
                                    value = version,
                                )
                            }
                            state.nodePubkey?.takeIf { it.isNotBlank() }?.let { pubkey ->
                                github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                                    label = stringResource(R.string.ln_node_pubkey_label),
                                    value = pubkey.take(20) + "…",
                                    monospace = true,
                                )
                            }
                        }
                    }
                    if (state.error != null && !isConnecting && !isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.loc_11c5bc85),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource(R.string.ln_node_setup_connection))
                    }
                }
            }
        }
    }

    @Composable
    fun SparkConnectionCard(
        onOpenSettings: () -> Unit,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onCancelConnection: () -> Unit,
    ) {
        val isConnected = visibleSparkConnected
        val isConnecting = visibleSparkConnecting
        val statusColor =
            when {
                isConnecting -> BitcoinOrange
                isConnected -> SuccessGreen
                visibleSparkState.error != null -> ErrorRed
                else -> TextSecondary
            }
        val borderColor =
            when {
                isConnected -> SuccessGreen
                isConnecting -> BitcoinOrange
                else -> BorderColor
            }
        val displayName =
            activeWalletObj?.name?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.loc_85f5955f)
        val identity =
            visibleSparkState.identityPubkey?.takeIf { it.isNotBlank() }
        val lightningAddress =
            visibleSparkState.lightningAddress?.takeIf { it.isNotBlank() }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerRadius = size.minDimension * 0.46f
                            val innerRadius = size.minDimension * 0.18f
                            val starPath =
                                Path().apply {
                                    for (index in 0 until 12) {
                                        val angle = Math.toRadians((index * 30 - 90).toDouble())
                                        val radius = if (index % 2 == 0) outerRadius else innerRadius
                                        val x = center.x + kotlin.math.cos(angle).toFloat() * radius
                                        val y = center.y + kotlin.math.sin(angle).toFloat() * radius
                                        if (index == 0) {
                                            moveTo(x, y)
                                        } else {
                                            lineTo(x, y)
                                        }
                                    }
                                    close()
                                }
                            drawPath(
                                path = starPath,
                                color = SparkPurple,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.loc_85f5955f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        HeaderSettingsCog(onClick = onOpenSettings)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val canConnect = activeWalletObj != null && !isConnected && !isConnecting
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (canConnect) BorderColor else statusColor,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .background(
                                        if (canConnect) {
                                            DarkBackground
                                        } else {
                                            statusColor.copy(alpha = 0.15f)
                                        },
                                    )
                                    .then(
                                        if (canConnect) {
                                            Modifier.clickable(onClick = onConnect)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = statusColor,
                                        strokeWidth = 1.5.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text =
                                        when {
                                            isConnecting -> stringResource(R.string.loc_066df953)
                                            isConnected -> stringResource(R.string.loc_98469a16)
                                            else -> stringResource(R.string.loc_bb72c083)
                                        },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                )
                            }
                        }

                        if (isConnected || isConnecting) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, ErrorRed, RoundedCornerShape(6.dp))
                                        .background(ErrorRed)
                                        .clickable(
                                            onClick =
                                                if (isConnecting) {
                                                    onCancelConnection
                                                } else {
                                                    onDisconnect
                                                },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription =
                                        if (isConnecting) {
                                            stringResource(R.string.loc_51bac044)
                                        } else {
                                            stringResource(R.string.loc_4f674841)
                                        },
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onError,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isConnected -> SuccessGreen.copy(alpha = 0.08f)
                                    isConnecting -> BitcoinOrange.copy(alpha = 0.08f)
                                    else -> DarkSurfaceVariant
                                },
                            )
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenSettings)
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_3cf29f70),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.width(72.dp),
                        )
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SparkPurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SparkPurple.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = "SDK",
                                style = MaterialTheme.typography.labelSmall,
                                color = SparkPurple,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                        label = stringResource(R.string.spark_connection_backend_label) + ":",
                        value = stringResource(R.string.spark_connection_backend_value),
                    )
                    if (isConnected) {
                        identity?.let { pubkey ->
                            github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                                label = stringResource(R.string.spark_connection_identity_label) + ":",
                                value = pubkey.take(20) + "…",
                                monospace = true,
                            )
                        }
                        lightningAddress?.let { address ->
                            github.aeonbtc.ibiswallet.ui.screens.ServerDetailRow(
                                label = stringResource(R.string.loc_42429685) + ":",
                                value = address,
                            )
                        }
                    }
                }

                if (visibleSparkState.error != null && !isConnecting && !isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.loc_11c5bc85),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            }
        }
    }

    if (showServerStatusDialog) {
        Dialog(
            onDismissRequest = { showServerStatusDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, BorderColor),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    // Lightning Node wallets use the node for L1 — no Electrum Bitcoin card.
                    if (!isLightningAvailable) {
                        CurrentServerCard(
                            server = electrumConfig,
                            isConnecting = uiState.isConnecting,
                            isConnected = uiState.isConnected,
                            error = uiState.error,
                            serverVersion = uiState.serverVersion,
                            blockHeight = walletState.blockHeight,
                            torState = torState,
                            isOnionServer = electrumConfig?.isOnionAddress() == true,
                            headerTitle = stringResource(R.string.loc_197cebf2),
                            headerTrailingContent = {
                                HeaderSettingsCog(
                                    onClick = {
                                        showServerStatusDialog = false
                                        navController.navigate(Screen.ElectrumConfig.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            },
                            onServerDetailsClick = {
                                showServerStatusDialog = false
                                navController.navigate(Screen.ElectrumConfig.route) {
                                    launchSingleTop = true
                                }
                            },
                            onConnect = { serversState.activeServerId?.let(viewModel::connectToServer) },
                            onDisconnect = { viewModel.disconnect() },
                            onCancelConnection = { viewModel.cancelConnection() },
                            onAddServer = {
                                showServerStatusDialog = false
                                navController.navigate(Screen.ElectrumConfig.route) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    if (isLiquidAvailable) {
                        if (!isLightningAvailable) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        LiquidCurrentServerCard(
                            server = liquidElectrumConfig,
                            isConnecting = showLiquidConnecting,
                            isConnected = isLiquidConnected,
                            error = liquidConnectionError,
                            blockHeight = liquidBlockHeight,
                            torState = liquidTorState,
                            isOnionServer = liquidElectrumConfig?.isOnionAddress() == true,
                            headerTitle = stringResource(R.string.loc_22236665),
                            headerTrailingContent = {
                                HeaderSettingsCog(
                                    onClick = {
                                        showServerStatusDialog = false
                                        navController.navigate(Screen.LiquidServerConfig.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            },
                            onServerDetailsClick = {
                                showServerStatusDialog = false
                                navController.navigate(Screen.LiquidServerConfig.route) {
                                    launchSingleTop = true
                                }
                            },
                            onConnect = { liquidServersState.activeServerId?.let(liquidViewModel::connectToLiquidServer) },
                            onDisconnect = { liquidViewModel.disconnectLiquidServer() },
                            onCancelConnection = { liquidViewModel.cancelLiquidConnection() },
                            onAddServer = {
                                showServerStatusDialog = false
                                navController.navigate(Screen.LiquidServerConfig.route) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    if (isSparkAvailable) {
                        if (!isLightningAvailable || isLiquidAvailable) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        SparkConnectionCard(
                            onOpenSettings = {
                                showServerStatusDialog = false
                                navController.navigate(Screen.Layer2Options.route) {
                                    launchSingleTop = true
                                }
                            },
                            onConnect = {
                                activeWalletObj?.id?.let { walletId ->
                                    sparkViewModel.loadSparkWallet(walletId)
                                }
                            },
                            onDisconnect = {
                                sparkViewModel.unloadSparkWallet()
                            },
                            onCancelConnection = {
                                sparkViewModel.unloadSparkWallet()
                            },
                        )
                    }

                    if (isLightningAvailable) {
                        // Lightning-only wallet: LN card is first/only rail card.
                        if (isLiquidAvailable || isSparkAvailable) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        LightningNodeConnectionStatusCard(
                            state = visibleLightningState,
                            isConnected = visibleLightningConnected,
                            isConnecting = visibleLightningConnecting,
                            onOpenSettings = {
                                showServerStatusDialog = false
                                val editId = activeWalletObj?.id
                                navController.navigate(
                                    Screen.LightningNodeConnection.createRoute(editId),
                                ) {
                                    launchSingleTop = true
                                }
                            },
                            onConnect = {
                                activeWalletObj?.id?.let { walletId ->
                                    lightningNodeViewModel.loadLightningWallet(walletId)
                                }
                            },
                            onDisconnect = {
                                lightningNodeViewModel.disconnectLightningWallet()
                            },
                            onCancelConnection = {
                                lightningNodeViewModel.disconnectLightningWallet()
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick = { showServerStatusDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.loc_d2c0aec0))
                    }
                }
            }
        }
    }

    @Composable
    fun ServerConfigRoute(initialSection: ServerConfigSection) {
        val activeLiquidServer = liquidServersState.servers.find { it.id == liquidServersState.activeServerId }

        CombinedServerConfigScreen(
            onBack = { navController.popBackStack() },
            initialSection = initialSection,
            showLiquidSection = isLiquidAvailable,
            bitcoinContent = { contentModifier ->
                ElectrumConfigScreen(
                    modifier = contentModifier,
                    onBack = { navController.popBackStack() },
                    showHeader = false,
                    isConnecting = uiState.isConnecting,
                    isConnected = uiState.isConnected,
                    error = uiState.error,
                    savedServers = serversState.servers,
                    activeServerId = serversState.activeServerId,
                    onSaveServer = { config -> viewModel.saveServer(config) },
                    onDeleteServer = { serverId -> viewModel.deleteServer(serverId) },
                    onConnectToServer = { serverId -> viewModel.connectToServer(serverId) },
                    onDisconnect = { viewModel.disconnect() },
                    onCancelConnection = { viewModel.cancelConnection() },
                    autoSwitchServer = autoSwitchServer,
                    onAutoSwitchServerChange = { enabled -> viewModel.setAutoSwitchServer(enabled) },
                    onReorderServers = { orderedIds -> viewModel.reorderServers(orderedIds) },
                    torState = torState,
                    isTorEnabled = viewModel.isTorEnabled(),
                    isActiveServerOnion = serversState.servers
                        .find { it.id == serversState.activeServerId }
                        ?.isOnionAddress() == true,
                    serverVersion = uiState.serverVersion,
                    blockHeight = walletState.blockHeight,
                )
            },
            liquidContent = { contentModifier ->
                LiquidServerConfigScreen(
                    modifier = contentModifier,
                    onBack = { navController.popBackStack() },
                    showHeader = false,
                    isConnecting = showLiquidConnecting,
                    isConnected = isLiquidConnected,
                    error = liquidConnectionError,
                    blockHeight = liquidBlockHeight,
                    savedServers = liquidServersState.servers,
                    activeServerId = liquidServersState.activeServerId,
                    onSaveServer = { config -> liquidViewModel.saveLiquidServer(config) },
                    onDeleteServer = { id -> liquidViewModel.removeLiquidServer(id) },
                    onConnectToServer = { id -> liquidViewModel.connectToLiquidServer(id) },
                    torState = liquidTorState,
                    isTorEnabled = isLiquidTorEnabled,
                    isActiveServerOnion = activeLiquidServer?.isOnionAddress() == true,
                    onDisconnect = { liquidViewModel.disconnectLiquidServer() },
                    onCancelConnection = { liquidViewModel.cancelLiquidConnection() },
                    autoSwitchServer = liquidAutoSwitch,
                    onAutoSwitchServerChange = { liquidViewModel.setLiquidAutoSwitchServer(it) },
                    onReorderServers = { orderedIds -> liquidViewModel.reorderLiquidServers(orderedIds) },
                )
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onItemClick = { item ->
                    scope.launch {
                        drawerState.close()
                    }
                    when (item) {
                        DrawerItem.ManageWallets -> {
                            navController.navigate(Screen.ManageWallets.route)
                        }
                        DrawerItem.ElectrumServer -> {
                            navController.navigate(Screen.ElectrumConfig.route)
                        }
                        DrawerItem.Settings -> {
                            navController.navigate(Screen.Settings.route)
                        }
                        DrawerItem.Layer2Options -> {
                            navController.navigate(Screen.Layer2Options.route)
                        }
                        DrawerItem.Security -> {
                            navController.navigate(Screen.Security.route)
                        }
                        DrawerItem.BackupRestore -> {
                            navController.navigate(Screen.BackupRestore.route)
                        }
                        DrawerItem.About -> {
                            navController.navigate(Screen.About.route)
                        }
                    }
                },
                appUpdateStatus = appUpdateStatus,
                onDownloadUpdateClick = { releaseUrl ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, releaseUrl.toUri()))
                },
            )
        },
        gesturesEnabled = isMainScreen,
    ) {
        Scaffold(
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isMainScreen || isSubScreenWithTopBar) {
                    TopAppBar(
                        expandedHeight = if (isMainScreen) 40.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isMainScreen && isSecurityEnabled) {
                                    IconButton(
                                        onClick = onLockApp,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.loc_9071098c),
                                            tint = BitcoinOrange,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (isSubScreenWithTopBar) {
                                    Text(
                                        text = subScreenTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                } else {
                                    WalletSelectorDropdown(
                                        activeWallet = walletState.activeWallet,
                                        expanded = walletSelectorExpanded,
                                        onToggle = { walletSelectorExpanded = !walletSelectorExpanded },
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (isSubScreenWithTopBar) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.loc_cdfc6e09),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.loc_dd3795ad),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (isMainScreen) {
                                Row(
                                    modifier =
                                        Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = 1.dp,
                                                color = headerStatusChromeColor,
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .background(headerStatusChromeColor.copy(alpha = 0.15f))
                                            .clickable(onClick = { showServerStatusDialog = true })
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isLayer2Available) {
                                        if (isLightningAvailable) {
                                            // Lightning Node: L1 is also the node — no Electrum Bitcoin half.
                                            if (visibleLightningConnecting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(8.dp),
                                                    color = layer2StatusColor,
                                                    strokeWidth = 1.5.dp,
                                                )
                                            } else {
                                                StatusDot(color = layer2StatusColor)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text =
                                                    when {
                                                        visibleLightningConnecting ->
                                                            stringResource(R.string.loc_066df953)
                                                        else ->
                                                            stringResource(R.string.ln_node_pill_label)
                                                    },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = layer2StatusColor,
                                                maxLines = 1,
                                            )
                                        } else {
                                            // Electrum Bitcoin + L2 provider (Liquid / Spark)
                                            if (uiState.isConnecting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(8.dp),
                                                    color = electrumStatusColor,
                                                    strokeWidth = 1.5.dp,
                                                )
                                            } else {
                                                StatusDot(color = electrumStatusColor)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text =
                                                    when {
                                                        isElectrumTorBootstrapping -> stringResource(R.string.loc_268af6fe)
                                                        uiState.isConnecting -> stringResource(R.string.loc_066df953)
                                                        uiState.isConnected -> stringResource(R.string.loc_197cebf2)
                                                        else -> stringResource(R.string.loc_197cebf2)
                                                    },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = electrumStatusColor,
                                                maxLines = 1,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "|",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextSecondary,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (
                                                (isLiquidAvailable && showLiquidConnecting) ||
                                                (isSparkAvailable && visibleSparkConnecting)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(8.dp),
                                                    color = layer2StatusColor,
                                                    strokeWidth = 1.5.dp,
                                                )
                                            } else {
                                                StatusDot(color = layer2StatusColor)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text =
                                                    when {
                                                        isSparkAvailable ->
                                                            when {
                                                                visibleSparkConnecting ->
                                                                    stringResource(R.string.loc_066df953)
                                                                else ->
                                                                    stringResource(R.string.loc_85f5955f)
                                                            }
                                                        isLiquidTorBootstrapping ->
                                                            stringResource(R.string.loc_268af6fe)
                                                        showLiquidConnecting ->
                                                            stringResource(R.string.loc_066df953)
                                                        else -> stringResource(R.string.loc_22236665)
                                                    },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = layer2StatusColor,
                                                maxLines = 1,
                                            )
                                        }
                                    } else {
                                        if (uiState.isConnecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(8.dp),
                                                color = electrumStatusColor,
                                                strokeWidth = 1.5.dp,
                                            )
                                        } else {
                                            StatusDot(color = electrumStatusColor)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text =
                                                when {
                                                    isElectrumTorBootstrapping -> electrumStatusTorBootstrapping
                                                    uiState.isConnecting -> electrumStatusConnecting
                                                    uiState.isConnected -> electrumStatusConnected
                                                    else -> electrumStatusDisconnected
                                                },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = electrumStatusColor,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = DarkBackground,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                    )
                }
            },
            bottomBar = {
                if (!isPinSetupActive) {
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        bottomNavItems.forEach { item ->
                            val itemTitle = stringResource(item.titleRes)
                            val selected =
                                currentDestination?.hierarchy?.any {
                                    it.route == item.screen.route
                                } == true

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = itemTitle,
                                    )
                                },
                                label = {
                                    Text(
                                        text = itemTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    // Only navigate if not already on this screen
                                    if (currentDestination?.route != item.screen.route) {
                                        navController.navigate(item.screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                inclusive = false
                                            }
                                        }
                                    }
                                },
                                colors = run {
                                    val accent = if (isLayer2Available && activeLayer == WalletLayer.LAYER2) layer2Accent else BitcoinOrange
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = accent,
                                        selectedTextColor = accent,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = Color.Transparent,
                                    )
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            val swipeOffset = remember { Animatable(0f) }
            var suppressNavTransition by remember { mutableStateOf(false) }
            var pendingSwipe by remember { mutableStateOf<PendingSwipe?>(null) }

            LaunchedEffect(pendingSwipe) {
                val swipe = pendingSwipe ?: return@LaunchedEffect
                val action = swipe.action

                swipeOffset.snapTo(-swipe.direction * swipe.screenWidth)
                if (action is SwipeAction.NavigateTab) suppressNavTransition = true

                when (action) {
                    is SwipeAction.NavigateTab -> {
                        navController.navigate(action.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                        }
                        delay(50)
                    }
                    is SwipeAction.SwitchWallet -> {
                        requestWalletSelection(action.walletId, navigateToBalance = false)
                        withTimeoutOrNull(1500) {
                            viewModel.walletState.first { it.activeWallet?.id == action.walletId }
                        }
                        delay(50)
                    }
                    is SwipeAction.SwitchLayer -> {
                        liquidViewModel.setActiveLayer(action.layer, walletState.activeWallet?.id)
                        withTimeoutOrNull(500) {
                            liquidViewModel.activeLayer.first { it == action.layer }
                        }
                        action.exitTransferRoute?.let { route ->
                            val isCenterRoute =
                                route == Screen.Swap.route ||
                                    route == Screen.LightningNodeChannels.route
                            val popped = if (isCenterRoute) navController.popBackStack() else false
                            if (!popped) {
                                val fallbackRoute =
                                    if (isCenterRoute) {
                                        Screen.Receive.route
                                    } else {
                                        Screen.Balance.route
                                    }
                                navController.navigate(fallbackRoute) {
                                    popUpTo(route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                        delay(50)
                    }
                }

                if (action is SwipeAction.NavigateTab) suppressNavTransition = false
                swipeOffset.animateTo(0f, tween(150))
            }

            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .graphicsLayer {
                        translationX = swipeOffset.value
                        val progress = if (size.width > 0) abs(swipeOffset.value) / size.width else 0f
                        alpha = 1f - progress * 0.25f
                    }
                    .then(
                        if (swipeMode != SecureStorage.SWIPE_MODE_DISABLED && isMainScreen) {
                            Modifier.pointerInput(
                                swipeMode,
                                currentDestination?.route,
                                activeLayer,
                                filteredWallets,
                                activeWalletId,
                                isAnyLayer2Enabled,
                                isActiveWalletLiquidWatchOnly,
                                isLayer1EnabledForWallet,
                            ) {
                                val screenWidth = size.width.toFloat()
                                val threshold = screenWidth * 0.15f
                                val velocityThreshold = 600f
                                val route = currentDestination?.route
                                val isTransferRoute =
                                    route == Screen.Swap.route ||
                                        route == Screen.SparkTransfer.route ||
                                        route == Screen.LightningNodeChannels.route
                                val walletIds = filteredWallets.map { it.id }
                                val walletIdx = walletIds.indexOf(activeWalletId)

                                fun canSwipe(dragPositive: Boolean): Boolean = when (swipeMode) {
                                    SecureStorage.SWIPE_MODE_SEND_RECEIVE -> {
                                        val screens = listOf(Screen.Receive.route, Screen.Balance.route, Screen.Send.route)
                                        val idx = screens.indexOf(route)
                                        if (dragPositive) idx > 0 else idx in 0 until screens.lastIndex
                                    }
                                    SecureStorage.SWIPE_MODE_WALLETS ->
                                        walletIds.size > 1 && walletIdx >= 0
                                    SecureStorage.SWIPE_MODE_LAYERS ->
                                        isAnyLayer2Enabled &&
                                            if (isTransferRoute) {
                                                if (dragPositive) isLayer1EnabledForWallet else true
                                            } else {
                                                !isActiveWalletLiquidWatchOnly &&
                                                    if (dragPositive) {
                                                        activeLayer == WalletLayer.LAYER2
                                                    } else {
                                                        activeLayer == WalletLayer.LAYER1
                                                    }
                                            }
                                    else -> false
                                }

                                fun resolveAction(direction: Int): SwipeAction? {
                                    val fwd = direction > 0
                                    return when (swipeMode) {
                                        SecureStorage.SWIPE_MODE_SEND_RECEIVE -> {
                                            val screens = listOf(Screen.Receive.route, Screen.Balance.route, Screen.Send.route)
                                            val idx = screens.indexOf(route)
                                            val t = if (fwd && idx > 0) screens[idx - 1] else if (!fwd && idx < screens.lastIndex) screens[idx + 1] else null
                                            t?.let { SwipeAction.NavigateTab(it) }
                                        }
                                        SecureStorage.SWIPE_MODE_WALLETS -> {
                                            if (walletIds.size <= 1 || walletIdx < 0) return null
                                            val targetIdx =
                                                if (fwd) {
                                                    if (walletIdx > 0) walletIdx - 1 else walletIds.lastIndex
                                                } else {
                                                    if (walletIdx < walletIds.lastIndex) walletIdx + 1 else 0
                                                }
                                            SwipeAction.SwitchWallet(walletIds[targetIdx])
                                        }
                                        SecureStorage.SWIPE_MODE_LAYERS -> {
                                            if (!isAnyLayer2Enabled) return null
                                            val t =
                                                if (isTransferRoute) {
                                                    if (fwd && isLayer1EnabledForWallet) {
                                                        WalletLayer.LAYER1
                                                    } else if (!fwd) {
                                                        WalletLayer.LAYER2
                                                    } else {
                                                        null
                                                    }
                                                } else {
                                                    if (isActiveWalletLiquidWatchOnly) return null
                                                    if (fwd && activeLayer == WalletLayer.LAYER2) {
                                                        WalletLayer.LAYER1
                                                    } else if (!fwd && activeLayer == WalletLayer.LAYER1) {
                                                        WalletLayer.LAYER2
                                                    } else {
                                                        null
                                                    }
                                                }
                                            t?.let { SwipeAction.SwitchLayer(it, exitTransferRoute = route.takeIf { isTransferRoute }) }
                                        }
                                        else -> null
                                    }
                                }

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    var total = 0f

                                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                        change.consume()
                                        total += over
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        scope.launch { swipeOffset.snapTo(total) }
                                    }

                                    if (drag != null) {
                                        val ok = horizontalDrag(drag.id) { change ->
                                            val delta = change.positionChange().x
                                            val moving = total + delta
                                            total += if (!canSwipe(moving > 0) && abs(moving) > abs(total)) delta * 0.15f else delta
                                            change.consume()
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            scope.launch { swipeOffset.snapTo(total) }
                                        }

                                        val velocity = tracker.calculateVelocity().x
                                        val positive = total > 0
                                        val committed = ok && canSwipe(positive) &&
                                            (abs(total) > threshold || (abs(velocity) > velocityThreshold && velocity * total > 0))
                                        val direction = if (positive) 1 else -1
                                        val action = if (committed) resolveAction(direction) else null

                                        if (action != null) {
                                            scope.launch {
                                                swipeOffset.animateTo(direction * screenWidth, tween(150))
                                                pendingSwipe = PendingSwipe(action, direction, screenWidth)
                                            }
                                        } else {
                                            scope.launch { swipeOffset.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 400f)) }
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Balance.route,
                    enterTransition = {
                        if (suppressNavTransition) fadeIn(tween(0)) else fadeIn(tween(200))
                    },
                    exitTransition = {
                        if (suppressNavTransition) fadeOut(tween(0)) else fadeOut(tween(200))
                    },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) },
                ) {
                    val toggleLayer1Denomination = {
                        val next = if (layer1Denomination == SecureStorage.DENOMINATION_SATS) {
                            SecureStorage.DENOMINATION_BTC
                        } else {
                            SecureStorage.DENOMINATION_SATS
                        }
                        viewModel.setDenomination(next)
                    }
                    val toggleLayer2Denomination = {
                        val next = if (layer2Denomination == SecureStorage.DENOMINATION_SATS) {
                            SecureStorage.DENOMINATION_BTC
                        } else {
                            SecureStorage.DENOMINATION_SATS
                        }
                        liquidViewModel.setDenomination(next)
                    }

                    composable(Screen.Receive.route) {
                        // Fetch price when entering Receive screen
                        LaunchedEffect(Unit) {
                            viewModel.fetchBtcPrice()
                        }
                        val lightningInvoiceState by liquidViewModel.lightningInvoiceState.collectAsStateWithLifecycle()
                        val pendingLightningInvoices by liquidViewModel.pendingLightningInvoices.collectAsStateWithLifecycle()
                        val lightningInvoiceLimits by liquidViewModel.lightningInvoiceLimits.collectAsStateWithLifecycle()

                        if (isLayer2Available) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LayerSwitcher(
                                    activeLayer = activeLayer,
                                    onLayerSelected = { layer ->
                                        liquidViewModel.setActiveLayer(layer, walletState.activeWallet?.id)
                                    },
                                    isSwapEnabled = swapEnabledForWallet,
                                    isLayer1Enabled = isLayer1EnabledForWallet,
                                    layer2Color = layer2Accent,
                                    layer2Label = layer2Label,
                                    centerMode = layerSwitcherCenterMode,
                                    onSwap = openLayer2Transfer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (activeLayer == WalletLayer.LAYER2) {
                                        if (isLightningAvailable) {
                                            LightningNodeReceiveScreen(
                                                receiveState = lightningReceiveState,
                                                isConnected = visibleLightningConnected,
                                                isConnecting = visibleLightningConnecting,
                                                connectionTarget = visibleLightningState.displayTarget(),
                                                denomination = layer2Denomination,
                                                privacyMode = privacyMode,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                lightningAddress = lightningNodeLightningAddress,
                                                onSaveLightningAddress = { address ->
                                                    val walletId = activeWalletObj?.id
                                                        ?: return@LightningNodeReceiveScreen false
                                                    lightningNodeViewModel.setLightningAddress(
                                                        walletId,
                                                        address,
                                                    )
                                                },
                                                onClearLightningAddress = {
                                                    activeWalletObj?.id?.let {
                                                        lightningNodeViewModel.clearLightningAddress(it)
                                                    }
                                                },
                                                onCreateInvoice = { amount, description ->
                                                    lightningNodeViewModel.createInvoice(
                                                        amount,
                                                        description.orEmpty(),
                                                    )
                                                },
                                                onReset = { lightningNodeViewModel.resetReceiveState() },
                                                onOpenConnectionSettings = {
                                                    navController.navigate(
                                                        Screen.LightningNodeConnection.createRoute(
                                                            activeWalletObj?.id,
                                                        ),
                                                    )
                                                },
                                                onToggleDenomination = toggleLayer2Denomination,
                                            )
                                        } else if (isSparkAvailable) {
                                            SparkReceiveScreen(
                                                receiveState = sparkReceiveState,
                                                sparkAddressLabels = sparkAddressLabels,
                                                denomination = layer2Denomination,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                privacyMode = privacyMode,
                                                onReceive = { kind, amount, description, forceNew ->
                                                    sparkViewModel.receive(kind, amount, description, forceNew)
                                                },
                                                onSaveAddressLabel = { addressOrRequest, label ->
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.saveSparkAddressLabel(walletId, addressOrRequest, label)
                                                    }
                                                },
                                                onResetReceive = { sparkViewModel.resetReceiveState() },
                                                onToggleDenomination = toggleLayer2Denomination,
                                            )
                                        } else {
                                            LiquidReceiveScreen(
                                                liquidAddress = liquidState.currentAddress,
                                                currentAddressLabel = liquidState.currentAddressLabel,
                                                denomination = layer2Denomination,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                privacyMode = privacyMode,
                                                boltzEnabled = liquidViewModel.isBoltzEnabled(),
                                                lightningInvoiceState = lightningInvoiceState,
                                                pendingLightningInvoices = pendingLightningInvoices,
                                                lightningInvoiceLimits = lightningInvoiceLimits,
                                                onEnsureLiquidAddress = { liquidViewModel.ensureLiquidAddress() },
                                                onGenerateLiquidAddress = { liquidViewModel.generateFreshLiquidAddress() },
                                                onSaveLiquidAddressLabel = { address, label ->
                                                    activeWalletId?.let { walletId ->
                                                        liquidViewModel.saveLiquidAddressLabel(walletId, address, label)
                                                    }
                                                },
                                                onShowAllAddresses = { navController.navigate(Screen.AllAddresses.route) },
                                                onShowAllUtxos = { navController.navigate(Screen.AllUtxos.route) },
                                                onCreateLightningInvoice = { sats, label, embedLabelInInvoice ->
                                                    liquidViewModel.createLightningInvoice(sats, label, embedLabelInInvoice)
                                                },
                                                onClaimPendingLightningInvoice = { swapId ->
                                                    liquidViewModel.claimPendingLightningInvoice(swapId)
                                                },
                                                onDeletePendingLightningInvoice = { swapId ->
                                                    liquidViewModel.deletePendingLightningInvoice(swapId)
                                                },
                                                onWarmLightningInvoice = {
                                                    liquidViewModel.requestBoltzLightningWarmup()
                                                },
                                                onFetchLightningLimits = { liquidViewModel.fetchLightningInvoiceLimits() },
                                                onResetLightningInvoice = { liquidViewModel.resetLightningInvoice() },
                                                onToggleDenomination = toggleLayer2Denomination,
                                                onOpenLayer2Options = {
                                                    navController.navigate(Screen.Layer2Options.route)
                                                },
                                                isLiquidConnected = isLiquidConnected,
                                                isLiquidConnecting = showLiquidConnecting,
                                                hasLiquidServerConfigured =
                                                    liquidServersState.hasUserSelectedServer &&
                                                        liquidServersState.activeServerId != null,
                                                onConnectLiquidServer = {
                                                    liquidServersState.activeServerId?.let(liquidViewModel::connectToLiquidServer)
                                                },
                                                onOpenLiquidServerSettings = {
                                                    navController.navigate(Screen.LiquidServerConfig.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                    } else if (isLightningAvailable) {
                                        LightningNodeOnchainReceiveScreen(
                                            state = lightningOnchainState,
                                            denomination = layer1Denomination,
                                            btcPrice = btcPrice,
                                            fiatCurrency = priceCurrency,
                                            privacyMode = privacyMode,
                                            isNodeConnected = visibleLightningConnected,
                                            isNodeConnecting = visibleLightningConnecting,
                                            connectionTarget = visibleLightningState.displayTarget(),
                                            onGenerateAddress = { lightningNodeViewModel.generateOnchainAddress() },
                                            onShowAllAddresses = {
                                                navController.navigate(Screen.AllAddresses.route)
                                            },
                                            onShowAllUtxos = {
                                                navController.navigate(Screen.AllUtxos.route)
                                            },
                                            onToggleDenomination = toggleLayer1Denomination,
                                        )
                                    } else {
                                        ReceiveScreen(
                                            walletState = walletState,
                                            denomination = layer1Denomination,
                                            btcPrice = btcPrice,
                                            fiatCurrency = priceCurrency,
                                            privacyMode = privacyMode,
                                            onGenerateAddress = { viewModel.getNewAddress() },
                                            onSaveLabel = { address, label -> viewModel.saveAddressLabel(address, label) },
                                            onShowAllAddresses = { navController.navigate(Screen.AllAddresses.route) },
                                            onShowAllUtxos = { navController.navigate(Screen.AllUtxos.route) },
                                            onToggleDenomination = toggleLayer1Denomination,
                                            isElectrumConnected = uiState.isConnected,
                                            isElectrumConnecting = uiState.isConnecting,
                                            hasElectrumServerConfigured =
                                                serversState.hasUserSelectedServer &&
                                                    serversState.activeServerId != null,
                                            onConnectElectrumServer = {
                                                serversState.activeServerId?.let(viewModel::connectToServer)
                                            },
                                            onOpenElectrumServerSettings = {
                                                navController.navigate(Screen.ElectrumConfig.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            ReceiveScreen(
                                walletState = walletState,
                                denomination = layer1Denomination,
                                btcPrice = btcPrice,
                                fiatCurrency = priceCurrency,
                                privacyMode = privacyMode,
                                onGenerateAddress = { viewModel.getNewAddress() },
                                onSaveLabel = { address, label -> viewModel.saveAddressLabel(address, label) },
                                onShowAllAddresses = { navController.navigate(Screen.AllAddresses.route) },
                                onShowAllUtxos = { navController.navigate(Screen.AllUtxos.route) },
                                onToggleDenomination = toggleLayer1Denomination,
                                isElectrumConnected = uiState.isConnected,
                                isElectrumConnecting = uiState.isConnecting,
                                hasElectrumServerConfigured =
                                    serversState.hasUserSelectedServer &&
                                        serversState.activeServerId != null,
                                onConnectElectrumServer = {
                                    serversState.activeServerId?.let(viewModel::connectToServer)
                                },
                                onOpenElectrumServerSettings = {
                                    navController.navigate(Screen.ElectrumConfig.route) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    }
                    composable(Screen.Balance.route) {
                        // Fetch price when entering Balance screen
                        LaunchedEffect(Unit) {
                            viewModel.fetchBtcPrice()
                        }
                        val addressLabels by viewModel.addressLabels.collectAsStateWithLifecycle()
                        val transactionLabels by viewModel.transactionLabels.collectAsStateWithLifecycle()
                        val liquidAddressLabels by liquidViewModel.liquidAddressLabels.collectAsStateWithLifecycle()
                        val liquidTransactionLabels by liquidViewModel.liquidTransactionLabels.collectAsStateWithLifecycle()
                        val sparkAddressLabels by sparkViewModel.sparkAddressLabels.collectAsStateWithLifecycle()
                        val sparkTransactionLabels by sparkViewModel.sparkTransactionLabels.collectAsStateWithLifecycle()
                        val boltzRescueMnemonic by liquidViewModel.boltzRescueMnemonic.collectAsStateWithLifecycle()

                        val handleScanQrResult: (String) -> Unit = { code ->
                            handleParsedSendInput(code.trim())
                        }
                        val handleLayer2ScanQrResult: (String) -> Unit = { code ->
                            handleLayer2SendInput(code.trim())
                        }

                        if (isLayer2Available) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LayerSwitcher(
                                    activeLayer = activeLayer,
                                    onLayerSelected = { layer ->
                                        liquidViewModel.setActiveLayer(layer, walletState.activeWallet?.id)
                                    },
                                    isSwapEnabled = swapEnabledForWallet,
                                    isLayer1Enabled = isLayer1EnabledForWallet,
                                    layer2Color = layer2Accent,
                                    layer2Label = layer2Label,
                                    centerMode = layerSwitcherCenterMode,
                                    onSwap = openLayer2Transfer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (activeLayer == WalletLayer.LAYER2) {
                                        if (isLightningAvailable) {
                                            LightningNodeBalanceScreen(
                                                state = visibleLightningState,
                                                denomination = layer2Denomination,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                historicalBtcPrices = historicalTxBtcPrices,
                                                showHistoricalTxPrices = showHistoricalTxPrices,
                                                onShowHistoricalTxPricesChange = {
                                                    showHistoricalTxPrices = it
                                                },
                                                privacyMode = privacyMode,
                                                dateFormat = balanceDateFormat,
                                                onTogglePrivacy = { viewModel.togglePrivacyMode() },
                                                onRefresh = { lightningNodeViewModel.refresh() },
                                                onToggleDenomination = toggleLayer2Denomination,
                                                onOpenConnectionSettings = {
                                                    navController.navigate(
                                                        Screen.LightningNodeConnection.createRoute(
                                                            activeWalletObj?.id,
                                                        ),
                                                    )
                                                },
                                                onQuickReceive = {
                                                    navController.navigate(Screen.Receive.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onScanQrResult = { code ->
                                                    handleParsedSendInput(code)
                                                },
                                            )
                                        } else if (isSparkAvailable) {
                                            SparkBalanceScreen(
                                                sparkState = visibleSparkState,
                                                receiveState = sparkReceiveState,
                                                layer1Transactions = walletState.transactions,
                                                layer1BlockHeight = walletState.blockHeight,
                                                mempoolUrl = viewModel.getMempoolUrl(),
                                                mempoolServer = viewModel.getMempoolServer(),
                                                denomination = layer2Denomination,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                historicalBtcPrices = historicalTxBtcPrices,
                                                showHistoricalTxPrices = showHistoricalTxPrices,
                                                dateFormat = balanceDateFormat,
                                                onShowHistoricalTxPricesChange = { showHistoricalTxPrices = it },
                                                privacyMode = privacyMode,
                                                sparkAddressLabels = sparkAddressLabels,
                                                sparkTransactionLabels = sparkTransactionLabels,
                                                onTogglePrivacy = { viewModel.togglePrivacyMode() },
                                                onRefresh = { sparkViewModel.refresh() },
                                                onToggleDenomination = toggleLayer2Denomination,
                                                onQuickReceive = {
                                                    sparkViewModel.receive(SparkReceiveKind.SPARK_ADDRESS)
                                                },
                                                onScanQrResult = handleLayer2ScanQrResult,
                                                onSaveSparkTransactionLabel = { paymentId, label ->
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.saveSparkTransactionLabel(walletId, paymentId, label)
                                                    }
                                                },
                                                onSaveSparkAddressLabel = { addressOrRequest, label ->
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.saveSparkAddressLabel(walletId, addressOrRequest, label)
                                                    }
                                                },
                                                onDeleteSparkTransactionLabel = { paymentId ->
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.deleteSparkTransactionLabel(walletId, paymentId)
                                                    }
                                                },
                                                onDeleteSparkAddressLabel = { addressOrRequest ->
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.deleteSparkAddressLabel(walletId, addressOrRequest)
                                                    }
                                                },
                                                onDeleteSparkHistoryItem = { itemId ->
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.deleteSparkHistoryItem(walletId, itemId)
                                                    }
                                                },
                                                onDeleteAllSparkHistory = {
                                                    activeWalletId?.let { walletId ->
                                                        sparkViewModel.deleteAllSparkHistory(walletId)
                                                    }
                                                },
                                            )
                                        } else {
                                            LiquidBalanceScreen(
                                                denomination = layer2Denomination,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                historicalBtcPrices = historicalTxBtcPrices,
                                                showHistoricalTxPrices = showHistoricalTxPrices,
                                                dateFormat = balanceDateFormat,
                                                onShowHistoricalTxPricesChange = { showHistoricalTxPrices = it },
                                                privacyMode = privacyMode,
                                                liquidExplorer = liquidExplorer,
                                                liquidExplorerUrl = liquidViewModel.getLiquidExplorerUrl(),
                                                onTogglePrivacy = { viewModel.togglePrivacyMode() },
                                                liquidAddressLabels = liquidAddressLabels,
                                                liquidTransactionLabels = liquidTransactionLabels,
                                                lookupPendingLightningPayment = { txid ->
                                                    liquidViewModel.getPendingLightningPaymentSessionForTxid(txid)
                                                },
                                                onSaveLiquidTransactionLabel = { txid, label ->
                                                    activeWalletId?.let { walletId ->
                                                        liquidViewModel.saveLiquidTransactionLabel(walletId, txid, label)
                                                    }
                                                },
                                                onDeleteLiquidTransactionLabel = { txid ->
                                                    activeWalletId?.let { walletId ->
                                                        liquidViewModel.deleteLiquidTransactionLabel(walletId, txid)
                                                    }
                                                },
                                                onDeleteLiquidTransactionFromHistory = { txid ->
                                                    liquidViewModel.deleteLiquidTransactionFromHistory(txid)
                                                },
                                                onDeleteAllLiquidTransactionsFromHistory = {
                                                    liquidViewModel.deleteAllLiquidTransactionsFromHistory()
                                                },
                                                onSaveLiquidAddressLabelFromTransaction = { address, label ->
                                                    activeWalletId?.let { walletId ->
                                                        liquidViewModel.saveLiquidAddressLabel(walletId, address, label)
                                                    }
                                                },
                                                onDeleteLiquidAddressLabelFromTransaction = { address ->
                                                    activeWalletId?.let { walletId ->
                                                        liquidViewModel.deleteLiquidAddressLabel(walletId, address)
                                                    }
                                                },
                                                searchTransactions = { query, includeSwap, includeLightning, includeNative, includeUsdt, limit ->
                                                    liquidViewModel.searchTransactions(
                                                        query = query,
                                                        includeSwap = includeSwap,
                                                        includeLightning = includeLightning,
                                                        includeNative = includeNative,
                                                        includeUsdt = includeUsdt,
                                                        limit = limit,
                                                    )
                                                },
                                                onToggleDenomination = toggleLayer2Denomination,
                                                onScanQrResult = handleLayer2ScanQrResult,
                                                boltzRescueMnemonic = boltzRescueMnemonic,
                                                liquidState = visibleLiquidState,
                                                onSyncLiquid = { liquidViewModel.syncLiquidWallet() },
                                                isLiquidConnected = isLiquidConnected,
                                                isLiquidConnecting = showLiquidConnecting,
                                                hasLiquidServerConfigured =
                                                    liquidServersState.hasUserSelectedServer &&
                                                        liquidServersState.activeServerId != null,
                                                onConnectLiquidServer = {
                                                    liquidServersState.activeServerId?.let(liquidViewModel::connectToLiquidServer)
                                                },
                                                onOpenLiquidServerSettings = {
                                                    navController.navigate(Screen.LiquidServerConfig.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                    } else if (isLightningAvailable) {
                                        LightningNodeOnchainBalanceScreen(
                                            state = lightningOnchainState,
                                            denomination = layer1Denomination,
                                            privacyMode = privacyMode,
                                            btcPrice = btcPrice,
                                            fiatCurrency = priceCurrency,
                                            historicalBtcPrices = historicalTxBtcPrices,
                                            showHistoricalTxPrices = showHistoricalTxPrices,
                                            onShowHistoricalTxPricesChange = {
                                                showHistoricalTxPrices = it
                                            },
                                            dateFormat = balanceDateFormat,
                                            mempoolUrl = viewModel.getMempoolUrl(),
                                            mempoolServer = viewModel.getMempoolServer(),
                                            isNodeConnected = visibleLightningConnected,
                                            isNodeConnecting = visibleLightningConnecting,
                                            connectionTarget = visibleLightningState.displayTarget(),
                                            onRefresh = { lightningNodeViewModel.refresh() },
                                            onTogglePrivacy = { viewModel.togglePrivacyMode() },
                                            onToggleDenomination = toggleLayer1Denomination,
                                            onScanQrResult = { code ->
                                                handleParsedSendInput(code)
                                            },
                                            onQuickReceive = {
                                                navController.navigate(Screen.Receive.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            onOpenConnectionSettings = {
                                                navController.navigate(
                                                    Screen.LightningNodeConnection.createRoute(
                                                        activeWalletId,
                                                    ),
                                                )
                                            },
                                            feeEstimationState = feeEstimationState,
                                            minFeeRate = minFeeRate,
                                            onRefreshFees = { viewModel.fetchFeeEstimates() },
                                            onBumpFee = { txid, feeRate ->
                                                lightningNodeViewModel.bumpOnchainFee(
                                                    parentTxid = txid,
                                                    satPerVbyte = feeRate.roundToLong().coerceAtLeast(1L),
                                                )
                                            },
                                            canBumpFee = { txid, confirmations ->
                                                lightningNodeViewModel.canBumpOnchainFee(
                                                    txid,
                                                    confirmations,
                                                )
                                            },
                                        )
                                    } else {
                                        BalanceScreen(
                                            walletState = walletState,
                                            denomination = layer1Denomination,
                                            mempoolUrl = viewModel.getMempoolUrl(),
                                            mempoolServer = viewModel.getMempoolServer(),
                                            btcPrice = btcPrice,
                                            fiatCurrency = priceCurrency,
                                            historicalBtcPrices = historicalTxBtcPrices,
                                            showHistoricalTxPrices = showHistoricalTxPrices,
                                            dateFormat = balanceDateFormat,
                                            onShowHistoricalTxPricesChange = { showHistoricalTxPrices = it },
                                            privacyMode = privacyMode,
                                            onTogglePrivacy = { viewModel.togglePrivacyMode() },
                                            onToggleDenomination = toggleLayer1Denomination,
                                            addressLabels = addressLabels,
                                            transactionLabels = transactionLabels,
                                            feeEstimationState = feeEstimationState,
                                            minFeeRate = minFeeRate,
                                            onBumpFee = { txid, feeRate -> viewModel.bumpFee(txid, feeRate) },
                                            onCpfp = { txid, feeRate -> viewModel.cpfp(txid, feeRate) },
                                            onRedirectTransaction = { txid, feeRate, destinationAddress ->
                                                viewModel.redirectTransaction(txid, feeRate, destinationAddress)
                                            },
                                            onSaveTransactionLabel = { txid, label ->
                                                viewModel.saveTransactionLabel(txid, label)
                                            },
                                            onDeleteTransactionLabel = { txid ->
                                                viewModel.deleteTransactionLabel(txid)
                                            },
                                            onDeleteTransactionFromHistory = { txid ->
                                                viewModel.deleteTransactionFromHistory(txid)
                                            },
                                            onDeleteAllTransactionsFromHistory = {
                                                viewModel.deleteAllTransactionsFromHistory()
                                            },
                                            onSaveAddressLabelFromTransaction = { address, label ->
                                                viewModel.saveAddressLabel(address, label)
                                            },
                                            onDeleteAddressLabelFromTransaction = { address ->
                                                viewModel.deleteAddressLabel(address)
                                            },
                                            searchTransactions = { query, showSwapTransactions, limit ->
                                                viewModel.searchTransactions(
                                                    query = query,
                                                    showSwapTransactions = showSwapTransactions,
                                                    limit = limit,
                                                )
                                            },
                                            onFetchTxVsize = { txid -> viewModel.fetchTransactionVsize(txid) },
                                            onRefreshFees = { viewModel.fetchFeeEstimates() },
                                            onSync = { viewModel.sync() },
                                            onManageWallets = { navController.navigate(Screen.ManageWallets.route) },
                                            onScanQrResult = handleScanQrResult,
                                            boltzRescueMnemonic = boltzRescueMnemonic,
                                            showLayer2RequiredPlaceholder = !isLayer2Enabled && isActiveWalletLiquidWatchOnly,
                                            onOpenSettings = { navController.navigate(Screen.Layer2Options.route) },
                                            isElectrumConnected = uiState.isConnected,
                                            isElectrumConnecting = uiState.isConnecting,
                                            hasElectrumServerConfigured =
                                                serversState.hasUserSelectedServer &&
                                                    serversState.activeServerId != null,
                                            onConnectElectrumServer = {
                                                serversState.activeServerId?.let(viewModel::connectToServer)
                                            },
                                            onOpenElectrumServerSettings = {
                                                navController.navigate(Screen.ElectrumConfig.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            BalanceScreen(
                                walletState = walletState,
                                denomination = layer1Denomination,
                                mempoolUrl = viewModel.getMempoolUrl(),
                                mempoolServer = viewModel.getMempoolServer(),
                                btcPrice = btcPrice,
                                fiatCurrency = priceCurrency,
                                historicalBtcPrices = historicalTxBtcPrices,
                                showHistoricalTxPrices = showHistoricalTxPrices,
                                dateFormat = balanceDateFormat,
                                onShowHistoricalTxPricesChange = { showHistoricalTxPrices = it },
                                privacyMode = privacyMode,
                                onTogglePrivacy = { viewModel.togglePrivacyMode() },
                                onToggleDenomination = toggleLayer1Denomination,
                                addressLabels = addressLabels,
                                transactionLabels = transactionLabels,
                                feeEstimationState = feeEstimationState,
                                minFeeRate = minFeeRate,
                                onBumpFee = { txid, feeRate -> viewModel.bumpFee(txid, feeRate) },
                                onCpfp = { txid, feeRate -> viewModel.cpfp(txid, feeRate) },
                                onRedirectTransaction = { txid, feeRate, destinationAddress ->
                                    viewModel.redirectTransaction(txid, feeRate, destinationAddress)
                                },
                                onSaveTransactionLabel = { txid, label ->
                                    viewModel.saveTransactionLabel(txid, label)
                                },
                                onDeleteTransactionLabel = { txid ->
                                    viewModel.deleteTransactionLabel(txid)
                                },
                                onDeleteTransactionFromHistory = { txid ->
                                    viewModel.deleteTransactionFromHistory(txid)
                                },
                                onDeleteAllTransactionsFromHistory = {
                                    viewModel.deleteAllTransactionsFromHistory()
                                },
                                onSaveAddressLabelFromTransaction = { address, label ->
                                    viewModel.saveAddressLabel(address, label)
                                },
                                onDeleteAddressLabelFromTransaction = { address ->
                                    viewModel.deleteAddressLabel(address)
                                },
                                searchTransactions = { query, showSwapTransactions, limit ->
                                    viewModel.searchTransactions(
                                        query = query,
                                        showSwapTransactions = showSwapTransactions,
                                        limit = limit,
                                    )
                                },
                                onFetchTxVsize = { txid -> viewModel.fetchTransactionVsize(txid) },
                                onRefreshFees = { viewModel.fetchFeeEstimates() },
                                onSync = { viewModel.sync() },
                                onManageWallets = { navController.navigate(Screen.ManageWallets.route) },
                                onScanQrResult = handleScanQrResult,
                                boltzRescueMnemonic = boltzRescueMnemonic,
                                showLayer2RequiredPlaceholder = !isLayer2Enabled && isActiveWalletLiquidWatchOnly,
                                onOpenSettings = { navController.navigate(Screen.Layer2Options.route) },
                                isElectrumConnected = uiState.isConnected,
                                isElectrumConnecting = uiState.isConnecting,
                                hasElectrumServerConfigured =
                                    serversState.hasUserSelectedServer &&
                                        serversState.activeServerId != null,
                                onConnectElectrumServer = {
                                    serversState.activeServerId?.let(viewModel::connectToServer)
                                },
                                onOpenElectrumServerSettings = {
                                    navController.navigate(Screen.ElectrumConfig.route) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    }
                    composable(Screen.Send.route) {
                        val utxos by viewModel.allUtxos.collectAsStateWithLifecycle()
                        val layer1SendDraft by viewModel.sendScreenDraft.collectAsStateWithLifecycle()
                        val dryRunResult by viewModel.dryRunResult.collectAsStateWithLifecycle()
                        val isDryRunInProgress by viewModel.isDryRunInProgress.collectAsStateWithLifecycle()
                        val sendRecipientIsSelfTransfer by viewModel.sendRecipientIsSelfTransfer.collectAsStateWithLifecycle()
                        val layer2SendDraft by liquidViewModel.sendDraft.collectAsStateWithLifecycle()
                        val liquidSendState by liquidViewModel.sendState.collectAsStateWithLifecycle()
                        val liquidUtxos by liquidViewModel.allLiquidUtxos.collectAsStateWithLifecycle()
                        val pendingSubmarineSwap by liquidViewModel.pendingSubmarineSwap.collectAsStateWithLifecycle()
                        val sendBoltzRescueMnemonic by liquidViewModel.boltzRescueMnemonic.collectAsStateWithLifecycle()

                        // Collect preSelectedUtxo directly here to ensure fresh value
                        val currentPreSelectedUtxo by viewModel.preSelectedUtxo.collectAsStateWithLifecycle()
                        val currentPreSelectedLiquidUtxo by liquidViewModel.preSelectedLiquidUtxo.collectAsStateWithLifecycle()

                        // Incremented each time a liquid UTXO is pre-selected, forces LiquidSendScreen re-creation
                        var liquidSendScreenKey by remember { mutableIntStateOf(0) }
                        LaunchedEffect(currentPreSelectedLiquidUtxo) {
                            if (currentPreSelectedLiquidUtxo != null) liquidSendScreenKey++
                        }

                        // Fetch fee estimates and price when entering Send screen
                        LaunchedEffect(Unit) {
                            viewModel.fetchFeeEstimates()
                            viewModel.fetchBtcPrice()
                        }

                        val handleLayer1RecipientInput: (String) -> Boolean = { input ->
                            if (!isLayer2Available || input.isBlank()) {
                                false
                            } else {
                                val resolution =
                                    resolveSendRoute(
                                        input = input,
                                        layer1UseSats = layer1Denomination == SecureStorage.DENOMINATION_SATS,
                                        layer2UseSats = layer2Denomination == SecureStorage.DENOMINATION_SATS,
                                        isLiquidAvailable = true,
                                    )
                                if (resolution.route == WalletLayer.LAYER2) {
                                    handleParsedSendInput(input)
                                    true
                                } else {
                                    false
                                }
                            }
                        }

                        if (isLayer2Available) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LayerSwitcher(
                                    activeLayer = activeLayer,
                                    onLayerSelected = { layer ->
                                        liquidViewModel.setActiveLayer(layer, walletState.activeWallet?.id)
                                    },
                                    isSwapEnabled = swapEnabledForWallet,
                                    isLayer1Enabled = isLayer1EnabledForWallet,
                                    layer2Color = layer2Accent,
                                    layer2Label = layer2Label,
                                    centerMode = layerSwitcherCenterMode,
                                    onSwap = openLayer2Transfer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    key(activeLayer, liquidSendScreenKey) {
                                        if (activeLayer == WalletLayer.LAYER2) {
                                            if (isLightningAvailable) {
                                                LightningNodeSendScreen(
                                                    sendState = lightningSendState,
                                                    sendDraft = lightningSendDraft,
                                                    isConnected = visibleLightningConnected,
                                                    isConnecting = visibleLightningConnecting,
                                                    connectionType = visibleLightningState.connectionType,
                                                    connectionTarget = visibleLightningState.displayTarget(),
                                                    availableBalanceSats = visibleLightningState.balanceSats,
                                                    denomination = layer2Denomination,
                                                    privacyMode = privacyMode,
                                                    btcPrice = btcPrice,
                                                    fiatCurrency = priceCurrency,
                                                    onUpdateDraft = { draft -> lightningNodeViewModel.setSendDraft(draft) },
                                                    onPrepareSend = { paymentRequest, amountSats ->
                                                        lightningNodeViewModel.prepareSend(
                                                            paymentRequest,
                                                            amountSats,
                                                        )
                                                    },
                                                    onSendPrepared = { lightningNodeViewModel.sendPrepared() },
                                                    onUpdateMaxFeePercent = { percent ->
                                                        lightningNodeViewModel.updatePreparedMaxFeePercent(percent)
                                                    },
                                                    onResetSend = { lightningNodeViewModel.resetSendState() },
                                                    onToggleDenomination = toggleLayer2Denomination,
                                                    onOpenConnectionSettings = {
                                                        navController.navigate(
                                                            Screen.LightningNodeConnection.createRoute(
                                                                activeWalletObj?.id,
                                                            ),
                                                        )
                                                    },
                                                )
                                            } else if (isSparkAvailable) {
                                                SparkSendScreen(
                                                    draft = sparkSendDraft,
                                                    sendState = sparkSendState,
                                                    denomination = layer2Denomination,
                                                    btcPrice = btcPrice,
                                                    fiatCurrency = priceCurrency,
                                                    privacyMode = privacyMode,
                                                    availableSats = visibleSparkState.balanceSats,
                                                    onUpdateDraft = { draft -> sparkViewModel.setSendDraft(draft) },
                                                    onLoadOnchainFeeQuotes = { paymentRequest, amountSats, useAllFunds ->
                                                        sparkViewModel.getOnchainFeeQuotes(paymentRequest, amountSats, useAllFunds)
                                                    },
                                                    onPrepareSend = { paymentRequest, amountSats, onchainFeeSpeed, useAllFunds ->
                                                        sparkViewModel.prepareSend(paymentRequest, amountSats, onchainFeeSpeed, useAllFunds)
                                                    },
                                                    onSendPrepared = { sparkViewModel.sendPrepared() },
                                                    onResetSend = { sparkViewModel.resetSendState() },
                                                    onToggleDenomination = toggleLayer2Denomination,
                                                )
                                            } else {
                                                LiquidSendScreen(
                                                denomination = layer2Denomination,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                privacyMode = privacyMode,
                                                boltzEnabled = liquidViewModel.isBoltzEnabled() && !isActiveWalletLiquidWatchOnly,
                                                isLiquidWatchOnly = walletState.activeWallet?.let {
                                                    liquidViewModel.isLiquidWatchOnly(it.id)
                                                } == true,
                                                liquidState = visibleLiquidState,
                                                liquidUtxos = liquidUtxos,
                                                spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                                                draft = layer2SendDraft,
                                                liquidSendState = liquidSendState,
                                                onUpdateDraft = { draft -> liquidViewModel.updateSendDraft(draft) },
                                                onPreviewLiquidSend = { address, amountSats, feeRate, selectedUtxos, isMaxSend, label ->
                                                    liquidViewModel.previewLiquidSend(
                                                        address = address,
                                                        amountSats = amountSats,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        isMaxSend = isMaxSend,
                                                        label = label,
                                                    )
                                                },
                                                onPreviewLiquidSendMulti = { recipients, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.previewLiquidSendMulti(
                                                        recipients = recipients,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onPreviewLightningPayment = { paymentInput, kind, amountSats, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.previewLightningPayment(
                                                        paymentInput = paymentInput,
                                                        kind = kind,
                                                        amountSats = amountSats,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onSendLBTC = { address, amountSats, feeRate, selectedUtxos, isMaxSend, label ->
                                                    liquidViewModel.sendLBTC(
                                                        address = address,
                                                        amountSats = amountSats,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        isMaxSend = isMaxSend,
                                                        label = label,
                                                    )
                                                },
                                                onSendLBTCMulti = { recipients, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.sendLBTCMulti(
                                                        recipients = recipients,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onResolveLightningPayment = { paymentInput, kind, amountSats, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.resolveLightningPaymentReview(
                                                        paymentInput = paymentInput,
                                                        kind = kind,
                                                        amountSats = amountSats,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onConfirmLightningPayment = { selectedUtxos, label ->
                                                    liquidViewModel.confirmLightningPayment(
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onCreatePset = { address, amountSats, feeRate, selectedUtxos, isMaxSend, label ->
                                                    liquidViewModel.createUnsignedPset(
                                                        address = address,
                                                        amountSats = amountSats,
                                                        feeRateSatPerVb = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        isMaxSend = isMaxSend,
                                                        label = label,
                                                    )
                                                    navController.navigate(Screen.LiquidPsetExport.route)
                                                },
                                                onPreviewAssetSend = { address, amount, assetId, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.previewAssetSend(
                                                        address = address,
                                                        amount = amount,
                                                        assetId = assetId,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onSendAsset = { address, amount, assetId, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.sendAsset(
                                                        address = address,
                                                        amount = amount,
                                                        assetId = assetId,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                },
                                                onCreateAssetPset = { address, amount, assetId, feeRate, selectedUtxos, label ->
                                                    liquidViewModel.createUnsignedAssetPset(
                                                        address = address,
                                                        amount = amount,
                                                        assetId = assetId,
                                                        feeRate = feeRate,
                                                        selectedUtxos = selectedUtxos,
                                                        label = label,
                                                    )
                                                    navController.navigate(Screen.LiquidPsetExport.route)
                                                },
                                                pendingSubmarineSwap = pendingSubmarineSwap,
                                                boltzRescueMnemonic = sendBoltzRescueMnemonic,
                                                onRetryPendingLightningRefund = { swapId ->
                                                    liquidViewModel.retryPendingLightningRefund(swapId)
                                                },
                                                preSelectedUtxo = currentPreSelectedLiquidUtxo,
                                                onClearPreSelectedUtxo = { liquidViewModel.clearPreSelectedLiquidUtxo() },
                                                onClearDraft = { liquidViewModel.clearSendDraft() },
                                                onResetSend = { liquidViewModel.resetSendState() },
                                                onToggleDenomination = toggleLayer2Denomination,
                                                isLiquidConnected = isLiquidConnected,
                                                isLiquidConnecting = showLiquidConnecting,
                                                hasLiquidServerConfigured =
                                                    liquidServersState.hasUserSelectedServer &&
                                                        liquidServersState.activeServerId != null,
                                                onConnectLiquidServer = {
                                                    liquidServersState.activeServerId?.let(liquidViewModel::connectToLiquidServer)
                                                },
                                                onOpenLiquidServerSettings = {
                                                    navController.navigate(Screen.LiquidServerConfig.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                            }
                                        } else if (isLightningAvailable) {
                                            val lightningOnchainSendState by
                                                lightningNodeViewModel.onchainSendState.collectAsStateWithLifecycle()
                                            val lightningPreSelectedUtxo by
                                                lightningNodeViewModel.preSelectedOnchainUtxo
                                                    .collectAsStateWithLifecycle()
                                            LightningNodeOnchainSendScreen(
                                                state = lightningOnchainState,
                                                sendState = lightningOnchainSendState,
                                                denomination = layer1Denomination,
                                                feeEstimationState = feeEstimationState,
                                                minFeeRate = minFeeRate,
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                privacyMode = privacyMode,
                                                preSelectedUtxo = lightningPreSelectedUtxo,
                                                spendUnconfirmed = true,
                                                isNodeConnected = visibleLightningConnected,
                                                isNodeConnecting = visibleLightningConnecting,
                                                connectionTarget = visibleLightningState.displayTarget(),
                                                onRefreshFees = { viewModel.fetchFeeEstimates() },
                                                onClearPreSelectedUtxo = {
                                                    lightningNodeViewModel.clearPreSelectedOnchainUtxo()
                                                },
                                                onSend = {
                                                        address,
                                                        amountSats,
                                                        satPerVbyte,
                                                        sendAll,
                                                        label,
                                                        selectedOutpoints,
                                                    ->
                                                    lightningNodeViewModel.sendOnchain(
                                                        address = address,
                                                        amountSats = amountSats,
                                                        satPerVbyte = satPerVbyte,
                                                        sendAll = sendAll,
                                                        label = label,
                                                        selectedOutpoints = selectedOutpoints,
                                                        spendUnconfirmed = true,
                                                    )
                                                },
                                                onSendMany = {
                                                        addrToAmount,
                                                        satPerVbyte,
                                                        label,
                                                        selectedOutpoints,
                                                    ->
                                                    lightningNodeViewModel.sendOnchainMany(
                                                        addrToAmountSats = addrToAmount,
                                                        satPerVbyte = satPerVbyte,
                                                        label = label,
                                                        selectedOutpoints = selectedOutpoints,
                                                        spendUnconfirmed = true,
                                                    )
                                                },
                                                onResetSend = {
                                                    lightningNodeViewModel.resetOnchainSendState()
                                                },
                                                onToggleDenomination = toggleLayer1Denomination,
                                            )
                                        } else {
                                            SendScreen(
                                                walletState = walletState,
                                                uiState = uiState,
                                                denomination = layer1Denomination,
                                                utxos = utxos,
                                                feeEstimationState = feeEstimationState,
                                                minFeeRate = minFeeRate,
                                                preSelectedUtxo = currentPreSelectedUtxo,
                                                spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                                                btcPrice = btcPrice,
                                                fiatCurrency = priceCurrency,
                                                privacyMode = privacyMode,
                                                isWatchOnly =
                                                    walletState.activeWallet?.isWatchOnly == true ||
                                                        walletState.activeWallet?.policyType == WalletPolicyType.MULTISIG,
                                                draft = layer1SendDraft,
                                                dryRunResult = dryRunResult,
                                                isDryRunInProgress = isDryRunInProgress,
                                                isRecipientSelfTransfer = sendRecipientIsSelfTransfer,
                                                onEstimateFee = { address, amount, feeRate, selectedUtxos, isMaxSend ->
                                                    viewModel.estimateFee(address, amount, feeRate, selectedUtxos, isMaxSend)
                                                },
                                                onEstimateFeeMulti = { recipients, feeRate, selectedUtxos ->
                                                    viewModel.estimateFeeMulti(recipients, feeRate, selectedUtxos)
                                                },
                                                onClearDryRun = { viewModel.clearDryRunResult() },
                                                onRefreshFees = { viewModel.fetchFeeEstimates() },
                                                onClearPreSelectedUtxo = { viewModel.clearPreSelectedUtxo() },
                                                onUpdateDraft = { draft -> viewModel.updateSendScreenDraft(draft) },
                                                onCheckSelfTransferAddress = { address ->
                                                    viewModel.checkSendRecipientIsSelfTransfer(address)
                                                },
                                                onHandleScannedInput = { code ->
                                                    handleParsedSendInput(code)
                                                    true
                                                },
                                                onHandleRecipientInput = handleLayer1RecipientInput,
                                                onSend = { address, amount, feeRate, selectedUtxos, label, isMaxSend, precomputedFeeSats ->
                                                    viewModel.sendBitcoin(
                                                        address,
                                                        amount,
                                                        feeRate,
                                                        selectedUtxos,
                                                        label,
                                                        isMaxSend,
                                                        precomputedFeeSats,
                                                    )
                                                },
                                            onSendMulti = { recipients, feeRate, selectedUtxos, label, precomputedFeeSats ->
                                                viewModel.sendBitcoinMulti(
                                                    recipients,
                                                    feeRate,
                                                    selectedUtxos,
                                                    label,
                                                    precomputedFeeSats,
                                                )
                                            },
                                            onCreatePsbt = {
                                                    address,
                                                    amount,
                                                    feeRate,
                                                    selectedUtxos,
                                                    label,
                                                    isMaxSend,
                                                    precomputedFeeSats,
                                                ->
                                                viewModel.createPsbt(
                                                    address,
                                                    amount,
                                                    feeRate,
                                                    selectedUtxos,
                                                    label,
                                                    isMaxSend,
                                                    precomputedFeeSats,
                                                )
                                            },
                                            onCreatePsbtMulti = { recipients, feeRate, selectedUtxos, label, precomputedFeeSats ->
                                                viewModel.createPsbtMulti(recipients, feeRate, selectedUtxos, label, precomputedFeeSats)
                                            },
                                            onNavigateToBroadcast = {
                                                navController.navigate(Screen.BroadcastTransaction.route)
                                            },
                                            onToggleDenomination = toggleLayer1Denomination,
                                            hasElectrumServerConfigured =
                                                serversState.hasUserSelectedServer &&
                                                    serversState.activeServerId != null,
                                            onConnectElectrumServer = {
                                                serversState.activeServerId?.let(viewModel::connectToServer)
                                            },
                                            onOpenElectrumServerSettings = {
                                                navController.navigate(Screen.ElectrumConfig.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        } else {
                            SendScreen(
                                walletState = walletState,
                                uiState = uiState,
                                denomination = layer1Denomination,
                                utxos = utxos,
                                feeEstimationState = feeEstimationState,
                                minFeeRate = minFeeRate,
                                preSelectedUtxo = currentPreSelectedUtxo,
                                spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                                btcPrice = btcPrice,
                                fiatCurrency = priceCurrency,
                                privacyMode = privacyMode,
                                isWatchOnly =
                                    walletState.activeWallet?.isWatchOnly == true ||
                                        walletState.activeWallet?.policyType == WalletPolicyType.MULTISIG,
                                draft = layer1SendDraft,
                                dryRunResult = dryRunResult,
                                isDryRunInProgress = isDryRunInProgress,
                                isRecipientSelfTransfer = sendRecipientIsSelfTransfer,
                                onEstimateFee = { address, amount, feeRate, selectedUtxos, isMaxSend ->
                                    viewModel.estimateFee(address, amount, feeRate, selectedUtxos, isMaxSend)
                                },
                                onEstimateFeeMulti = { recipients, feeRate, selectedUtxos ->
                                    viewModel.estimateFeeMulti(recipients, feeRate, selectedUtxos)
                                },
                                onClearDryRun = { viewModel.clearDryRunResult() },
                                onRefreshFees = { viewModel.fetchFeeEstimates() },
                                onClearPreSelectedUtxo = { viewModel.clearPreSelectedUtxo() },
                                onUpdateDraft = { draft -> viewModel.updateSendScreenDraft(draft) },
                                onCheckSelfTransferAddress = { address ->
                                    viewModel.checkSendRecipientIsSelfTransfer(address)
                                },
                                onHandleScannedInput = { code ->
                                    handleParsedSendInput(code)
                                    true
                                },
                                onHandleRecipientInput = handleLayer1RecipientInput,
                                onSend = { address, amount, feeRate, selectedUtxos, label, isMaxSend, precomputedFeeSats ->
                                    viewModel.sendBitcoin(
                                        address,
                                        amount,
                                        feeRate,
                                        selectedUtxos,
                                        label,
                                        isMaxSend,
                                        precomputedFeeSats,
                                    )
                                },
                                onSendMulti = { recipients, feeRate, selectedUtxos, label, precomputedFeeSats ->
                                    viewModel.sendBitcoinMulti(
                                        recipients,
                                        feeRate,
                                        selectedUtxos,
                                        label,
                                        precomputedFeeSats,
                                    )
                                },
                                onCreatePsbt = {
                                        address,
                                        amount,
                                        feeRate,
                                        selectedUtxos,
                                        label,
                                        isMaxSend,
                                        precomputedFeeSats,
                                    ->
                                    viewModel.createPsbt(
                                        address,
                                        amount,
                                        feeRate,
                                        selectedUtxos,
                                        label,
                                        isMaxSend,
                                        precomputedFeeSats,
                                    )
                                },
                                onCreatePsbtMulti = { recipients, feeRate, selectedUtxos, label, precomputedFeeSats ->
                                    viewModel.createPsbtMulti(recipients, feeRate, selectedUtxos, label, precomputedFeeSats)
                                },
                                onNavigateToBroadcast = {
                                    navController.navigate(Screen.BroadcastTransaction.route)
                                },
                                onToggleDenomination = toggleLayer1Denomination,
                                hasElectrumServerConfigured =
                                    serversState.hasUserSelectedServer &&
                                        serversState.activeServerId != null,
                                onConnectElectrumServer = {
                                    serversState.activeServerId?.let(viewModel::connectToServer)
                                },
                                onOpenElectrumServerSettings = {
                                    navController.navigate(Screen.ElectrumConfig.route) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    }
                    composable(
                        route = Screen.ManageWallets.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        ManageWalletsScreen(
                            wallets = wallets,
                            onBack = { navController.popBackStack() },
                            onImportWallet = { navController.navigate(Screen.ImportWallet.route) },
                            onGenerateWallet = { navController.navigate(Screen.GenerateWallet.route) },
                            onEditLightningNodeConnection = { walletId ->
                                navController.navigate(
                                    Screen.LightningNodeConnection.createRoute(walletId),
                                ) {
                                    launchSingleTop = true
                                }
                            },
                            onViewWallet = { wallet ->
                                val keyMaterial = viewModel.getKeyMaterial(wallet.id)
                                val liquidDescriptor = viewModel.getLiquidDescriptor(wallet.id)
                                if (keyMaterial == null && liquidDescriptor == null) {
                                    null
                                } else {
                                    KeyMaterialInfo(
                                        walletName = wallet.name,
                                        mnemonic = keyMaterial?.mnemonic,
                                        extendedPublicKey = keyMaterial?.extendedPublicKey,
                                        isWatchOnly = keyMaterial?.isWatchOnly ?: wallet.isWatchOnly,
                                        masterFingerprint = wallet.masterFingerprint,
                                        privateKey = keyMaterial?.privateKey,
                                        watchAddress = keyMaterial?.watchAddress,
                                        liquidDescriptor = liquidDescriptor,
                                        multisigConfig = keyMaterial?.multisigConfig,
                                        localCosignerKeyMaterial = keyMaterial?.localCosignerKeyMaterial,
                                    )
                                }
                            },
                            onDeleteWallet = { wallet ->
                                scope.launch {
                                    viewModel.deleteWallet(wallet.id)
                                    liquidViewModel.deleteWalletData(wallet.id)
                                    sparkViewModel.deleteWalletData(wallet.id)
                                    lightningNodeViewModel.deleteWalletData(wallet.id)
                                }
                            },
                            onSelectWallet = { wallet ->
                                requestWalletSelection(wallet.id, navigateToBalance = true)
                            },
                            onSignMessage = { walletId, address, message ->
                                viewModel.signMessage(walletId, address, message)
                            },
                            onVerifyMessage = { address, message, signature ->
                                viewModel.verifyMessage(address, message, signature)
                            },
                            onExportBip329Labels = { walletId, uri, labelScope ->
                                scope.launch {
                                    try {
                                        val content =
                                            when (labelScope) {
                                                Bip329LabelScope.BITCOIN ->
                                                    viewModel.getBitcoinBip329LabelsContent(walletId)
                                                Bip329LabelScope.LIQUID ->
                                                    liquidViewModel.getLiquidBip329LabelsContent(walletId)
                                                Bip329LabelScope.SPARK ->
                                                    sparkViewModel.getSparkBip329LabelsContent(walletId)
                                                Bip329LabelScope.BOTH ->
                                                    listOf(
                                                        viewModel.getBitcoinBip329LabelsContent(
                                                            walletId,
                                                            includeNetworkTag = true,
                                                        ),
                                                        liquidViewModel.getLiquidBip329LabelsContent(walletId),
                                                        sparkViewModel.getSparkBip329LabelsContent(walletId),
                                                    ).filter { it.isNotBlank() }.joinToString("\n")
                                            }
                                        if (content.isBlank()) {
                                            snackbarHostState.showSnackbar(bip329ExportEmptyMessage)
                                            return@launch
                                        }

                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                                stream.write(content.toByteArray(Charsets.UTF_8))
                                            } ?: throw IllegalStateException("Could not open output stream")
                                        }

                                        snackbarHostState.showSnackbar(bip329ExportSuccessMessage)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        snackbarHostState.showSnackbar(bip329ExportErrorMessage)
                                    }
                                }
                            },
                            onImportBip329Labels = { walletId, uri, labelScope ->
                                scope.launch {
                                    try {
                                        val content =
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                context.contentResolver.openInputStream(uri)?.use {
                                                    it.readBytesWithLimit(InputLimits.BACKUP_FILE_BYTES).toString(Charsets.UTF_8)
                                                } ?: throw IllegalStateException("Could not read file")
                                            }
                                        val imported =
                                            when (labelScope) {
                                                Bip329LabelScope.BITCOIN ->
                                                    viewModel.importBitcoinBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.BITCOIN,
                                                    )
                                                Bip329LabelScope.LIQUID ->
                                                    liquidViewModel.importLiquidBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.LIQUID,
                                                    )
                                                Bip329LabelScope.SPARK ->
                                                    sparkViewModel.importSparkBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.SPARK,
                                                    )
                                                Bip329LabelScope.BOTH ->
                                                    viewModel.importBitcoinBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.BOTH,
                                                    ) +
                                                        liquidViewModel.importLiquidBip329LabelsFromContent(
                                                            walletId,
                                                            content,
                                                            Bip329LabelScope.BOTH,
                                                        ) +
                                                        sparkViewModel.importSparkBip329LabelsFromContent(
                                                            walletId,
                                                            content,
                                                            Bip329LabelScope.BOTH,
                                                        )
                                            }
                                        if (imported == 0) {
                                            snackbarHostState.showSnackbar(bip329ImportEmptyMessage)
                                            return@launch
                                        }
                                        snackbarHostState.showSnackbar(bip329ImportSuccessMessage)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        snackbarHostState.showSnackbar(bip329ImportErrorMessage)
                                    }
                                }
                            },
                            onImportBip329LabelsFromContent = { walletId, content, labelScope ->
                                scope.launch {
                                    try {
                                        val imported =
                                            when (labelScope) {
                                                Bip329LabelScope.BITCOIN ->
                                                    viewModel.importBitcoinBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.BITCOIN,
                                                    )
                                                Bip329LabelScope.LIQUID ->
                                                    liquidViewModel.importLiquidBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.LIQUID,
                                                    )
                                                Bip329LabelScope.SPARK ->
                                                    sparkViewModel.importSparkBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.SPARK,
                                                    )
                                                Bip329LabelScope.BOTH ->
                                                    viewModel.importBitcoinBip329LabelsFromContent(
                                                        walletId,
                                                        content,
                                                        Bip329LabelScope.BOTH,
                                                    ) +
                                                        liquidViewModel.importLiquidBip329LabelsFromContent(
                                                            walletId,
                                                            content,
                                                            Bip329LabelScope.BOTH,
                                                        ) +
                                                        sparkViewModel.importSparkBip329LabelsFromContent(
                                                            walletId,
                                                            content,
                                                            Bip329LabelScope.BOTH,
                                                        )
                                            }
                                        if (imported == 0) {
                                            snackbarHostState.showSnackbar(bip329ImportEmptyMessage)
                                            return@launch
                                        }
                                        snackbarHostState.showSnackbar(bip329ImportSuccessMessage)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        snackbarHostState.showSnackbar(bip329ImportErrorMessage)
                                    }
                                }
                            },
                            onGetBip329LabelsContent = { walletId, labelScope ->
                                when (labelScope) {
                                    Bip329LabelScope.BITCOIN ->
                                        viewModel.getBitcoinBip329LabelsContent(walletId)
                                    Bip329LabelScope.LIQUID ->
                                        liquidViewModel.getLiquidBip329LabelsContent(walletId)
                                    Bip329LabelScope.SPARK ->
                                        sparkViewModel.getSparkBip329LabelsContent(walletId)
                                    Bip329LabelScope.BOTH ->
                                        listOf(
                                            viewModel.getBitcoinBip329LabelsContent(
                                                walletId,
                                                includeNetworkTag = true,
                                            ),
                                            liquidViewModel.getLiquidBip329LabelsContent(walletId),
                                            sparkViewModel.getSparkBip329LabelsContent(walletId),
                                        ).filter { it.isNotBlank() }.joinToString("\n")
                                }
                            },
                            onGetLabelCounts = { walletId ->
                                val (bitcoinAddressCount, bitcoinTransactionCount) = viewModel.getLabelCounts(walletId)
                                val (liquidAddressCount, liquidTransactionCount) =
                                    liquidViewModel.getLiquidLabelCounts(walletId)
                                val sparkLabels = sparkViewModel.getSparkLabelCounts(walletId)
                                Bip329LabelCounts(
                                    bitcoinAddressCount = bitcoinAddressCount,
                                    bitcoinTransactionCount = bitcoinTransactionCount,
                                    liquidAddressCount = liquidAddressCount,
                                    liquidTransactionCount = liquidTransactionCount,
                                    sparkAddressCount = sparkLabels.first,
                                    sparkTransactionCount = sparkLabels.second,
                                )
                            },
                            onEditWallet = { walletId, newName, newGapLimit, newFingerprint ->
                                viewModel.editWallet(walletId, newName, newGapLimit, newFingerprint)
                            },
                            onReorderWallets = { orderedIds ->
                                viewModel.reorderWallets(orderedIds)
                            },
                            onFullSync = { wallet ->
                                if (liquidViewModel.isLiquidEnabledForWallet(wallet.id)) {
                                    liquidViewModel.requestFullSync(wallet.id)
                                }
                                if (sparkViewModel.isSparkEnabledForWallet(wallet.id)) {
                                    sparkViewModel.syncWallet(wallet.id)
                                }
                                if (!wallet.isLiquidWatchOnly && !wallet.isLightningNode) {
                                    viewModel.fullSync(wallet.id)
                                }
                            },
                            syncingWalletId = syncingWalletId,
                            // Layer 2
                            layer2Enabled = isAnyLayer2Enabled,
                            liquidLayer2Enabled = isLayer2Enabled,
                            sparkLayer2Enabled = isSparkLayer2Enabled,
                            lightningNodeLayer2Enabled = isLightningNodeLayer2Enabled,
                            isLiquidEnabledForWallet = { walletId ->
                                // Read from the reactive map (triggers recomposition on change)
                                liquidEnabledWallets[walletId]
                                    ?: liquidViewModel.isLiquidEnabledForWallet(walletId)
                            },
                            isSparkEnabledForWallet = { walletId ->
                                sparkEnabledWallets[walletId]
                                    ?: sparkViewModel.isSparkEnabledForWallet(walletId)
                            },
                            isLightningNodeEnabledForWallet = { walletId ->
                                lightningEnabledWallets[walletId]
                                    ?: lightningNodeViewModel.isLightningNodeEnabledForWallet(walletId)
                            },
                            onSetLiquidEnabledForWallet = { walletId, enabled ->
                                liquidViewModel.setLiquidEnabledForWallet(walletId, enabled)
                                if (enabled) {
                                    sparkViewModel.setSparkEnabledForWallet(walletId, false)
                                    lightningNodeViewModel.setLightningNodeEnabledForWallet(walletId, false)
                                }
                                if (walletId == walletState.activeWallet?.id && isLayer2Enabled) {
                                    if (enabled) {
                                        liquidViewModel.loadLiquidWallet(walletId)
                                        sparkViewModel.unloadSparkWallet()
                                        lightningNodeViewModel.unloadLightningWallet()
                                    } else {
                                        liquidViewModel.unloadLiquidWallet()
                                    }
                                }
                            },
                            onSetSparkEnabledForWallet = { walletId, enabled ->
                                sparkViewModel.setSparkEnabledForWallet(walletId, enabled)
                                if (enabled) {
                                    liquidViewModel.setLiquidEnabledForWallet(walletId, false)
                                    lightningNodeViewModel.setLightningNodeEnabledForWallet(walletId, false)
                                }
                                if (walletId == walletState.activeWallet?.id && enabled) {
                                    liquidViewModel.unloadLiquidWallet()
                                    lightningNodeViewModel.unloadLightningWallet()
                                    sparkViewModel.loadSparkWallet(walletId)
                                }
                            },
                            onSetLightningNodeEnabledForWallet = { walletId, enabled ->
                                lightningNodeViewModel.setLightningNodeEnabledForWallet(walletId, enabled)
                                if (enabled) {
                                    liquidViewModel.setLiquidEnabledForWallet(walletId, false)
                                    sparkViewModel.setSparkEnabledForWallet(walletId, false)
                                }
                                if (walletId == walletState.activeWallet?.id && enabled) {
                                    liquidViewModel.unloadLiquidWallet()
                                    sparkViewModel.unloadSparkWallet()
                                    lightningNodeViewModel.loadLightningWallet(walletId)
                                } else if (walletId == walletState.activeWallet?.id && !enabled) {
                                    lightningNodeViewModel.unloadLightningWallet()
                                }
                            },
                            onEditLiquidGapLimit = { walletId, gap ->
                                liquidViewModel.setLiquidGapLimit(walletId, gap)
                            },
                            isWalletLockAvailable = isSecurityEnabled,
                            onSetWalletLocked = { walletId, locked ->
                                if (!locked) {
                                    requestDisableWalletLock(walletId)
                                } else {
                                    enableWalletLock(walletId)
                                }
                            },
                        )
                    }
                    composable(
                        route = Screen.ImportWallet.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        ImportWalletScreen(
                            onImport = { config ->
                                viewModel.importWallet(config)
                            },
                            onImportLiquidWatchOnly = { name, ctDescriptor, gapLimit ->
                                viewModel.importLiquidWatchOnlyWallet(name, ctDescriptor, gapLimit)
                            },
                            onImportFromBackup = { backupJson, importServerSettings ->
                                viewModel.importFromBackup(backupJson, importServerSettings)
                            },
                            onParseBackupFile = { uri, password ->
                                viewModel.parseBackupFile(uri, password)
                            },
                            onBack = { navController.popBackStack() },
                            onSweepPrivateKey = { navController.navigate(Screen.SweepPrivateKey.route) },
                            existingWalletNames = existingWalletNames,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                        )
                    }
                    composable(
                        route = Screen.GenerateWallet.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        GenerateWalletScreen(
                            onGenerate = { config ->
                                viewModel.generateWallet(config)
                            },
                            onBack = { navController.popBackStack() },
                            existingWalletNames = existingWalletNames,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                        )
                    }
                    composable(
                        route = Screen.SweepPrivateKey.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val sweepState by viewModel.sweepState.collectAsStateWithLifecycle()
                        SweepPrivateKeyScreen(
                            sweepState = sweepState,
                            isConnected = uiState.isConnected,
                            onScanBalances = { wif -> viewModel.scanWifBalances(wif) },
                            onSweep = { wif, dest, rate -> viewModel.sweepPrivateKey(wif, dest, rate) },
                            onReset = { viewModel.resetSweepState() },
                            onBack = { navController.popBackStack() },
                            isWifValid = { viewModel.isWifPrivateKey(it) },
                            feeEstimationState = feeEstimationState,
                            minFeeRate = minFeeRate,
                            onRefreshFees = { viewModel.fetchFeeEstimates() },
                        )
                    }
                    composable(
                        route = Screen.ElectrumConfig.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        ServerConfigRoute(initialSection = ServerConfigSection.BITCOIN)
                    }
                    composable(
                        route = Screen.Settings.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val feeSource by viewModel.feeSourceState.collectAsStateWithLifecycle()
                        val priceSource by viewModel.priceSourceState.collectAsStateWithLifecycle()
                        val mempoolServer by viewModel.mempoolServerState.collectAsStateWithLifecycle()
                        var customMempoolUrl by remember(walletSettingsRefreshVersion) {
                            mutableStateOf(viewModel.getCustomMempoolUrl())
                        }
                        var customFeeSourceUrl by remember(walletSettingsRefreshVersion) {
                            mutableStateOf(viewModel.getCustomFeeSourceUrl())
                        }
                        var spendUnconfirmed by remember(walletSettingsRefreshVersion) {
                            mutableStateOf(viewModel.getSpendUnconfirmed())
                        }
                        var nfcEnabled by remember(walletSettingsRefreshVersion) {
                            mutableStateOf(viewModel.isNfcEnabled())
                        }
                        val nfcAvailability = context.getNfcAvailability(nfcEnabled)

                        SettingsScreen(
                            currentDenomination = layer1Denomination,
                            onDenominationChange = { newDenomination ->
                                viewModel.setDenomination(newDenomination)
                            },
                            currentAppLocale = appLocale,
                            onAppLocaleChange = { locale ->
                                viewModel.setAppLocale(locale)
                            },
                            spendUnconfirmed = spendUnconfirmed,
                            onSpendUnconfirmedChange = { enabled ->
                                viewModel.setSpendUnconfirmed(enabled)
                                spendUnconfirmed = enabled
                            },
                            walletNotificationsEnabled = walletNotificationsEnabled,
                            walletNotificationDeliveryState = walletNotificationDeliveryState,
                            onWalletNotificationsEnabledChange = { enabled ->
                                if (!enabled) {
                                    updateWalletNotificationsEnabled(false)
                                } else if (!notificationPermissionGranted) {
                                    postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    updateWalletNotificationsEnabled(true)
                                    if (!systemNotificationsEnabled) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                walletNotificationsAndroidBlocked,
                                            )
                                        }
                                    }
                                }
                            },
                            foregroundConnectivityEnabled = foregroundConnectivityEnabled,
                            onForegroundConnectivityEnabledChange = { enabled ->
                                updateForegroundConnectivityEnabled(enabled)
                            },
                            nfcEnabled = nfcEnabled,
                            onNfcEnabledChange = { enabled ->
                                viewModel.setNfcEnabled(enabled)
                                nfcEnabled = enabled
                            },
                            hasNfcHardware = nfcAvailability.hasHardware,
                            isSystemNfcEnabled = nfcAvailability.isSystemEnabled,
                            supportsNfcBroadcast = nfcAvailability.supportsHce,
                            currentFeeSource = feeSource,
                            onFeeSourceChange = { newSource ->
                                val wasOnion = viewModel.isFeeSourceOnion()
                                viewModel.setFeeSource(newSource)
                                val isNowOnion = viewModel.isFeeSourceOnion()
                                if (isNowOnion && !viewModel.isTorReady()) {
                                    viewModel.startTor()
                                }
                                if (wasOnion && !isNowOnion) {
                                    viewModel.stopTor()
                                }
                            },
                            customFeeSourceUrl = customFeeSourceUrl,
                            onCustomFeeSourceUrlSave = { newUrl ->
                                val wasOnion = viewModel.isFeeSourceOnion()
                                customFeeSourceUrl = newUrl
                                viewModel.setCustomFeeSourceUrl(newUrl)
                                val isNewUrlOnion =
                                    try {
                                        java.net.URI(newUrl).host?.endsWith(".onion") == true
                                    } catch (_: Exception) {
                                        newUrl.endsWith(".onion")
                                    }
                                if (isNewUrlOnion && !viewModel.isTorReady()) {
                                    // Start Tor for .onion fee source
                                    viewModel.startTor()
                                } else if (wasOnion && !isNewUrlOnion) {
                                    // Switched from .onion to clearnet — stop Tor if nothing else needs it
                                    viewModel.stopTor()
                                }
                            },
                            currentPriceSource = priceSource,
                            onPriceSourceChange = { newSource ->
                                val wasOnion = viewModel.isPriceSourceOnion()
                                viewModel.setPriceSource(newSource)
                                val isNowOnion = viewModel.isPriceSourceOnion()
                                if (isNowOnion && !viewModel.isTorReady()) {
                                    viewModel.startTor()
                                } else if (wasOnion && !isNowOnion) {
                                    viewModel.stopTor()
                                }
                            },
                            currentPriceCurrency = priceCurrency,
                            onPriceCurrencyChange = { newCurrency ->
                                viewModel.setPriceCurrency(newCurrency)
                            },
                            historicalTxFiatEnabled = historicalTxFiatEnabled,
                            onHistoricalTxFiatEnabledChange = { enabled ->
                                viewModel.setHistoricalTxFiatEnabled(enabled)
                            },
                            currentMempoolServer = mempoolServer,
                            onMempoolServerChange = { newServer ->
                                viewModel.setMempoolServer(newServer)
                            },
                            customMempoolUrl = customMempoolUrl,
                            onCustomMempoolUrlSave = { newUrl ->
                                customMempoolUrl = newUrl
                                viewModel.setCustomMempoolUrl(newUrl)
                            },
                            currentSwipeMode = swipeMode,
                            onSwipeModeChange = { mode ->
                                viewModel.setSwipeMode(mode)
                            },
                            currentBalanceDateFormat = balanceDateFormat,
                            onBalanceDateFormatChange = { format ->
                                viewModel.setBalanceDateFormat(format)
                            },
                            currentThemeMode = themeMode,
                            onThemeModeChange = { mode ->
                                viewModel.setThemeMode(mode)
                            },
                            currentTypeface = typeface,
                            onTypefaceChange = { selectedTypeface ->
                                viewModel.setTypeface(selectedTypeface)
                            },
                            isLiquidAvailable = isAnyLayer2Enabled,
                            torStatus = torState.status,
                            onOpenBitcoinElectrum = {
                                navController.navigate(Screen.ElectrumConfig.route) {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = Screen.Layer2Options.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        var customLiquidExplorerUrl by remember(liquidSettingsRefreshVersion) {
                            mutableStateOf(liquidViewModel.getCustomLiquidExplorerUrl())
                        }
                        Layer2OptionsScreen(
                            liquidEnabled = isLayer2Enabled,
                            onLiquidEnabledChange = { enabled ->
                                liquidViewModel.setLayer2Enabled(enabled)
                                if (enabled && !isLayer2Enabled && !secureStorage.hasSeenLiquidEnableInfo()) {
                                    secureStorage.setHasSeenLiquidEnableInfo(true)
                                    showLiquidEnableInfoDialog = true
                                }
                            },
                            sparkEnabled = isSparkLayer2Enabled,
                            onSparkEnabledChange = { enabled ->
                                sparkViewModel.setSparkLayer2Enabled(enabled)
                                if (enabled && !isSparkLayer2Enabled && !secureStorage.hasSeenSparkEnableInfo()) {
                                    showSparkEnableInfoDialogIfNeeded()
                                }
                            },
                            lightningNodeEnabled = isLightningNodeLayer2Enabled,
                            onLightningNodeEnabledChange = { enabled ->
                                lightningNodeViewModel.setLightningNodeLayer2Enabled(enabled)
                                if (enabled && !isLightningNodeLayer2Enabled) {
                                    showLightningNodeEnableInfoDialogIfNeeded()
                                }
                            },
                            onOpenLightningNodeConnection = {
                                // Layer 2 Options: add a new Lightning Node wallet
                                navController.navigate(Screen.LightningNodeConnection.createRoute())
                            },
                            currentDenomination = layer2Denomination,
                            onDenominationChange = { newDenomination ->
                                liquidViewModel.setDenomination(newDenomination)
                            },
                            currentBoltzApiSource = boltzApiSource,
                            onBoltzApiSourceChange = { newSource ->
                                liquidViewModel.setBoltzApiSource(newSource)
                            },
                            currentSideSwapApiSource = sideSwapApiSource,
                            onSideSwapApiSourceChange = { newSource ->
                                liquidViewModel.setSideSwapApiSource(newSource)
                            },
                            currentLiquidExplorer = liquidExplorer,
                            onLiquidExplorerChange = { newExplorer ->
                                liquidViewModel.setLiquidExplorer(newExplorer)
                            },
                            customLiquidExplorerUrl = customLiquidExplorerUrl,
                            onCustomLiquidExplorerUrlSave = { newUrl ->
                                customLiquidExplorerUrl = newUrl
                                liquidViewModel.setCustomLiquidExplorerUrl(newUrl)
                            },
                            layer2TorStatus = liquidTorState.status,
                            onOpenLiquidElectrum = {
                                navController.navigate(Screen.LiquidServerConfig.route) {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = Screen.LightningNodeConnection.route,
                        arguments =
                            listOf(
                                navArgument(Screen.LightningNodeConnection.WALLET_ID_ARG) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) { entry ->
                        val editWalletId =
                            entry.arguments?.getString(Screen.LightningNodeConnection.WALLET_ID_ARG)
                        // Create mode (null walletId): blank form for a new node wallet, reset after save.
                        // Edit mode (walletId set): load & mutate the current node config.
                        var formKey by remember(editWalletId) { mutableIntStateOf(0) }
                        val formConfig =
                            remember(formKey, editWalletId) {
                                if (editWalletId.isNullOrBlank()) {
                                    github.aeonbtc.ibiswallet.data.model.LightningNodeConfig()
                                } else {
                                    lightningNodeViewModel.getConfig(editWalletId)
                                }
                            }
                        key(formKey, editWalletId) {
                            LightningNodeConnectionScreen(
                                walletId = editWalletId,
                                initialConfig = formConfig,
                                isTesting = isLightningTestingConnection,
                                testPhase = lightningConnectionTestPhase,
                                testResult = lightningConnectionTestResult,
                                onTest = { config -> lightningNodeViewModel.testConnection(config) },
                                onSave = { name, config ->
                                    if (!editWalletId.isNullOrBlank()) {
                                        lightningNodeViewModel.saveConfig(editWalletId, config)
                                        lightningNodeViewModel.clearConnectionTestResult()
                                        navController.popBackStack()
                                    } else {
                                        scope.launch {
                                            val result =
                                                viewModel.createLightningNodeWalletNow(
                                                    name = name,
                                                    config = config,
                                                    navigateToWallet = false,
                                                )
                                            if (result is github.aeonbtc.ibiswallet.data.model.WalletResult.Success) {
                                                // Fresh node configs are written outside saveConfig;
                                                // bump revision so Manage Wallets cards refresh.
                                                lightningNodeViewModel.notifyConfigChanged()
                                                lightningNodeViewModel.clearConnectionTestResult()
                                                formKey += 1
                                            }
                                        }
                                    }
                                },
                                onClearTest = { lightningNodeViewModel.clearConnectionTestResult() },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(
                        route = Screen.Security.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        // Use mutableState so UI recomposes when settings change
                        var securityMethod by remember { mutableStateOf(viewModel.getSecurityMethod()) }
                        var lockTiming by remember { mutableStateOf(viewModel.getLockTiming()) }
                        var screenshotsDisabled by remember { mutableStateOf(viewModel.getDisableScreenshots()) }
                        var randomizePinPad by remember { mutableStateOf(viewModel.getRandomizePinPad()) }
                        var duressEnabled by remember { mutableStateOf(viewModel.isDuressEnabled()) }
                        var autoWipeThreshold by remember { mutableStateOf(viewModel.getAutoWipeThreshold()) }
                        var cloakModeEnabled by remember { mutableStateOf(viewModel.isCloakModeEnabled()) }
                        var pendingBiometricEnrollment by remember { mutableStateOf(false) }
                        val isDuressMode by viewModel.isDuressMode.collectAsStateWithLifecycle()
                        // Check if device has biometric hardware
                        val biometricManager = BiometricManager.from(context)
                        val isBiometricAvailable =
                            biometricManager.canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
                            ) == BiometricManager.BIOMETRIC_SUCCESS

                        LaunchedEffect(pendingBiometricEnrollment) {
                            if (!pendingBiometricEnrollment) return@LaunchedEffect
                            val hostActivity = activity
                            if (hostActivity == null) {
                                pendingBiometricEnrollment = false
                                snackbarHostState.showSnackbar(biometricUnavailableMessage)
                                return@LaunchedEffect
                            }
                            val cryptoObject =
                                withContext(Dispatchers.Default) {
                                    runCatching {
                                        secureStorage.createSpendSecretBiometricEnrollmentCryptoObject()
                                    }.getOrNull()
                                }
                            if (cryptoObject == null) {
                                pendingBiometricEnrollment = false
                                snackbarHostState.showSnackbar(biometricUnavailableMessage)
                                return@LaunchedEffect
                            }
                            val enrollmentPrompt =
                                BiometricPrompt(
                                    hostActivity,
                                    ContextCompat.getMainExecutor(hostActivity),
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(
                                            result: BiometricPrompt.AuthenticationResult,
                                        ) {
                                            super.onAuthenticationSucceeded(result)
                                            val cipher = result.cryptoObject?.cipher
                                            if (cipher == null) {
                                                pendingBiometricEnrollment = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(biometricUnavailableMessage)
                                                }
                                                return
                                            }
                                            runCatching {
                                                viewModel.enrollBiometricLock(cipher)
                                            }.onSuccess {
                                                viewModel.clearPin()
                                                viewModel.setSecurityMethod(
                                                    SecureStorage.SecurityMethod.BIOMETRIC,
                                                )
                                                securityMethod = SecureStorage.SecurityMethod.BIOMETRIC
                                                isSecurityEnabled = true
                                            }.onFailure {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        biometricUnavailableMessage,
                                                    )
                                                }
                                            }
                                            pendingBiometricEnrollment = false
                                        }

                                        override fun onAuthenticationError(
                                            errorCode: Int,
                                            errString: CharSequence,
                                        ) {
                                            super.onAuthenticationError(errorCode, errString)
                                            pendingBiometricEnrollment = false
                                        }
                                    },
                                )
                            val promptInfo =
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle(
                                        hostActivity.getString(R.string.biometric_prompt_unlock_ibis_wallet),
                                    )
                                    .setSubtitle(
                                        hostActivity.getString(R.string.biometric_prompt_access_wallet),
                                    )
                                    .setNegativeButtonText(hostActivity.getString(R.string.loc_51bac044))
                                    .setAllowedAuthenticators(
                                        BiometricManager.Authenticators.BIOMETRIC_STRONG,
                                    )
                                    .build()
                            enrollmentPrompt.authenticate(promptInfo, cryptoObject)
                        }

                        SecurityScreen(
                            currentSecurityMethod = securityMethod,
                            currentLockTiming = lockTiming,
                            isBiometricAvailable = isBiometricAvailable,
                            screenshotsDisabled = screenshotsDisabled,
                            randomizePinPad = randomizePinPad,
                            isDuressEnabled = duressEnabled,
                            isDuressMode = isDuressMode,
                            hasWallet = walletState.wallets.isNotEmpty(),
                            autoWipeThreshold = autoWipeThreshold,
                            isCloakModeEnabled = cloakModeEnabled,
                            onSetPinCode = { pin ->
                                viewModel.savePin(pin)
                                viewModel.setSecurityMethod(SecureStorage.SecurityMethod.PIN)
                                securityMethod = SecureStorage.SecurityMethod.PIN
                                isSecurityEnabled = true
                            },
                            onEnableBiometric = {
                                pendingBiometricEnrollment = true
                            },
                            onDisableSecurity = {
                                // Disabling security also disables duress
                                if (duressEnabled) {
                                    viewModel.disableDuress {
                                        duressEnabled = false
                                    }
                                }
                                // Disabling security also disables auto-wipe
                                viewModel.setAutoWipeThreshold(SecureStorage.AutoWipeThreshold.DISABLED)
                                autoWipeThreshold = SecureStorage.AutoWipeThreshold.DISABLED
                                viewModel.clearPin()
                                viewModel.setSecurityMethod(SecureStorage.SecurityMethod.NONE)
                                securityMethod = SecureStorage.SecurityMethod.NONE
                                isSecurityEnabled = false
                            },
                            onLockTimingChange = { timing ->
                                viewModel.setLockTiming(timing)
                                lockTiming = timing
                            },
                            onScreenshotsDisabledChange = { disabled ->
                                viewModel.setDisableScreenshots(disabled)
                                screenshotsDisabled = disabled
                                val activity = context as? android.app.Activity
                                if (disabled) {
                                    activity?.window?.setFlags(
                                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                    )
                                } else {
                                    activity?.window?.clearFlags(
                                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                    )
                                }
                            },
                            onRandomizePinPadChange = { enabled ->
                                viewModel.setRandomizePinPad(enabled)
                                randomizePinPad = enabled
                            },
                            onSetupDuress = { pin, config ->
                                viewModel.setupDuress(
                                    pin = pin,
                                    config = config,
                                    onSuccess = { duressEnabled = true },
                                    onError = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = duressSetupErrorMessage,
                                            )
                                        }
                                    },
                                )
                            },
                            onDisableDuress = {
                                viewModel.disableDuress {
                                    duressEnabled = false
                                }
                            },
                            onAutoWipeThresholdChange = { threshold ->
                                viewModel.setAutoWipeThreshold(threshold)
                                autoWipeThreshold = threshold
                            },
                            onEnableCloakMode = { code ->
                                viewModel.enableCloakMode(code)
                                cloakModeEnabled = true
                            },
                            onDisableCloakMode = {
                                viewModel.disableCloakMode()
                                cloakModeEnabled = false
                            },
                            onRestartApp = {
                                viewModel.stopTor()
                                val ctx = navController.context
                                val restartIntent = Intent(
                                    ctx,
                                    github.aeonbtc.ibiswallet.MainActivity::class.java,
                                ).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                )
                                ctx.startActivity(restartIntent)
                                kotlin.system.exitProcess(0)
                            },
                            onPinSetupActiveChange = { active -> isPinSetupActive = active },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = Screen.About.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        AboutScreen(
                            appUpdateStatus = appUpdateStatus,
                            appUpdateCheckEnabled = appUpdateCheckEnabled,
                            onAppUpdateCheckEnabledChange = ::updateAppUpdateCheckEnabled,
                            onDownloadUpdateClick = { releaseUrl ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, releaseUrl.toUri()))
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = Screen.BackupRestore.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val backupWallets = remember { viewModel.getBackupWalletEntries() }
                        val backupLoading by viewModel.uiState.collectAsStateWithLifecycle()
                        val resultMessage by viewModel.fullBackupResultMessage.collectAsStateWithLifecycle()

                        BackupRestoreScreen(
                            wallets = backupWallets,
                            onBack = { navController.popBackStack() },
                            onExportFullBackup = { uri, walletIds, labelWalletIds, includeServers, includeAppSettings, password ->
                                viewModel.exportFullBackup(uri, walletIds, labelWalletIds, includeServers, includeAppSettings, password)
                            },
                            onParseFullBackup = { uri, password ->
                                val json = viewModel.parseFullBackup(uri, password)
                                val walletsArr = json.optJSONArray("wallets")
                                val previewWallets = List(walletsArr?.length() ?: 0) { i ->
                                    val entry = walletsArr!!.getJSONObject(i)
                                    val walletObj = entry.optJSONObject("wallet")
                                    val rawType = walletObj?.optString("addressType", "").orEmpty()
                                    val rawPolicy = walletObj?.optString("policyType", "").orEmpty()
                                    val displayType =
                                        if (rawPolicy == WalletPolicyType.MULTISIG.name) {
                                            "${walletObj?.optInt("multisigThreshold", 0)}-of-${walletObj?.optInt("multisigTotalCosigners", 0)} Multisig"
                                        } else {
                                            runCatching {
                                                github.aeonbtc.ibiswallet.data.model.AddressType.valueOf(rawType).displayName
                                            }.getOrElse {
                                                rawType.ifBlank { "Unknown" }
                                            }
                                        }

                                    BackupWalletEntry(
                                        id = i.toString(),
                                        name = walletObj?.optString("name", "Unnamed") ?: "Unnamed",
                                        type = displayType,
                                        isWatchOnly = walletObj?.optBoolean("isWatchOnly", false) == true,
                                        hasLabels = entry.has("labels"),
                                    )
                                }
                                FullBackupPreview(
                                    wallets = previewWallets,
                                    hasServers = json.has("serverSettings") || json.has("electrumServers"),
                                    hasLiquidServers =
                                        json.has("liquidServers") ||
                                            json.optJSONObject("serverSettings")?.has("liquidServers") == true,
                                    hasAppSettings = json.has("appSettings"),
                                    exportedAt = json.optString("exportedAt", "Unknown"),
                                )
                            },
                            onImportFullBackup = { uri, password, walletIds, labelWalletIds, importServers, importAppSettings ->
                                scope.launch {
                                    try {
                                        val json = viewModel.parseFullBackup(uri, password)
                                        val restored =
                                            viewModel.importFullBackup(
                                                json,
                                                walletIds,
                                                labelWalletIds,
                                                importServers,
                                                importAppSettings,
                                            )
                                        if (restored && (importServers || importAppSettings)) {
                                            viewModel.reloadRestoredAppSettings()
                                            liquidViewModel.reloadRestoredSettings()
                                            sparkViewModel.reloadRestoredSettings()
                                            lightningNodeViewModel.reloadRestoredSettings()
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        viewModel.setFullBackupResult("Restore failed")
                                    }
                                }
                            },
                            isLoading = backupLoading.isLoading,
                            resultMessage = resultMessage,
                            onClearResult = { viewModel.clearFullBackupResult() },
                        )
                    }
                    composable(
                        route = Screen.AllAddresses.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val bitcoinAddressBook by viewModel.allAddresses.collectAsStateWithLifecycle()
                        val liquidAddressBook by liquidViewModel.allLiquidAddresses.collectAsStateWithLifecycle()
                        val liquidUtxosForAddresses by liquidViewModel.allLiquidUtxos.collectAsStateWithLifecycle()
                        if (isLiquidAvailable && activeLayer == WalletLayer.LAYER2) {
                            AllAddressesScreen(
                                receiveAddresses = liquidAddressBook.first,
                                changeAddresses = liquidAddressBook.second,
                                usedAddresses = liquidAddressBook.third,
                                denomination = layer2Denomination,
                                privacyMode = privacyMode,
                                accentColor = LiquidTeal,
                                labelAccentColor = LiquidTeal,
                                addressEdgeCharacters = 25,
                                addressMaxLines = 2,
                                useMultilineTruncatedAddress = false,
                                assetUtxos = liquidUtxosForAddresses,
                                onSaveLabel = { address, label ->
                                    activeWalletId?.let { walletId ->
                                        liquidViewModel.saveLiquidAddressLabel(walletId, address, label)
                                    }
                                },
                                onDeleteLabel = { address ->
                                    activeWalletId?.let { walletId ->
                                        liquidViewModel.deleteLiquidAddressLabel(walletId, address)
                                    }
                                },
                            )
                        } else if (isLightningAvailable) {
                            AllAddressesScreen(
                                receiveAddresses = lightningOnchainState.receiveAddresses,
                                changeAddresses = lightningOnchainState.changeAddresses,
                                usedAddresses = lightningOnchainState.usedAddresses,
                                denomination = layer1Denomination,
                                privacyMode = privacyMode,
                                accentColor = BitcoinOrange,
                                changeTabHelperText =
                                    stringResource(R.string.ln_node_onchain_change_unavailable),
                                emptyChangeMessage =
                                    stringResource(R.string.ln_node_onchain_change_unavailable),
                            )
                        } else {
                            AllAddressesScreen(
                                receiveAddresses = bitcoinAddressBook.first,
                                changeAddresses = bitcoinAddressBook.second,
                                usedAddresses = bitcoinAddressBook.third,
                                denomination = layer1Denomination,
                                privacyMode = privacyMode,
                                onSaveLabel = { address, label ->
                                    viewModel.saveAddressLabel(address, label)
                                },
                                onDeleteLabel = { address ->
                                    viewModel.deleteAddressLabel(address)
                                },
                            )
                        }
                    }
                    composable(
                        route = Screen.AllUtxos.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val liquidUtxos by liquidViewModel.allLiquidUtxos.collectAsStateWithLifecycle()
                        if (isLiquidAvailable && activeLayer == WalletLayer.LAYER2) {
                            AllUtxosScreen(
                                utxos = liquidUtxos,
                                denomination = layer2Denomination,
                                btcPrice = btcPrice,
                                fiatCurrency = priceCurrency,
                                privacyMode = privacyMode,
                                spendUnconfirmed = true,
                                addressEdgeCharacters = 10,
                                onFreezeUtxo = { outpoint, frozen ->
                                    liquidViewModel.setLiquidUtxoFrozen(outpoint, frozen)
                                },
                                onSendFromUtxo = { utxo ->
                                    liquidViewModel.setPreSelectedLiquidUtxo(utxo)
                                    liquidViewModel.updateSendDraft(
                                        SendScreenDraft(
                                            assetId = utxo.assetId,
                                            selectedUtxoOutpoints = listOf(utxo.outpoint),
                                        ),
                                    )
                                    liquidViewModel.setActiveLayer(WalletLayer.LAYER2, walletState.activeWallet?.id)
                                    navController.navigate(Screen.Send.route)
                                },
                                onSaveLabel = { address, label ->
                                    activeWalletId?.let { walletId ->
                                        liquidViewModel.saveLiquidAddressLabel(walletId, address, label)
                                    }
                                },
                                onDeleteLabel = { address ->
                                    activeWalletId?.let { walletId ->
                                        liquidViewModel.deleteLiquidAddressLabel(walletId, address)
                                    }
                                },
                            )
                        } else if (isLightningAvailable) {
                            AllUtxosScreen(
                                utxos = lightningOnchainState.utxos,
                                denomination = layer1Denomination,
                                btcPrice = btcPrice,
                                fiatCurrency = priceCurrency,
                                privacyMode = privacyMode,
                                spendUnconfirmed = true,
                                onSendFromUtxo = { utxo ->
                                    lightningNodeViewModel.setPreSelectedOnchainUtxo(utxo)
                                    navController.navigate(Screen.Send.route)
                                },
                            )
                        } else {
                            val utxos by viewModel.allUtxos.collectAsStateWithLifecycle()

                            AllUtxosScreen(
                                utxos = utxos,
                                denomination = layer1Denomination,
                                btcPrice = btcPrice,
                                fiatCurrency = priceCurrency,
                                privacyMode = privacyMode,
                                spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                                onFreezeUtxo = { outpoint, frozen ->
                                    viewModel.setUtxoFrozen(outpoint, frozen)
                                },
                                onSendFromUtxo = { utxo ->
                                    viewModel.setPreSelectedUtxo(utxo)
                                    navController.navigate(Screen.Send.route)
                                },
                                onSaveLabel = { address, label ->
                                    viewModel.saveAddressLabel(address, label)
                                },
                                onDeleteLabel = { address ->
                                    viewModel.deleteAddressLabel(address)
                                },
                            )
                        }
                    }
                    composable(
                        route = Screen.PsbtExport.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val psbtState by viewModel.psbtState.collectAsStateWithLifecycle()
                        val psbtQrDensity by viewModel.psbtQrDensityState.collectAsStateWithLifecycle()
                        val psbtQrBrightness by viewModel.psbtQrBrightnessState.collectAsStateWithLifecycle()
                        PsbtScreen(
                            psbtState = psbtState,
                            uiState = uiState,
                            qrDensity = psbtQrDensity,
                            onQrDensityChange = { density ->
                                viewModel.setPsbtQrDensity(density)
                            },
                            qrBrightness = psbtQrBrightness,
                            onQrBrightnessChange = { brightness ->
                                viewModel.setPsbtQrBrightness(brightness)
                            },
                            onSignedDataReceived = { data ->
                                viewModel.setSignedTransactionData(data)
                            },
                            onConfirmBroadcast = {
                                viewModel.confirmBroadcast()
                            },
                            onCancelBroadcast = {
                                viewModel.cancelBroadcast()
                            },
                            onBack = {
                                viewModel.clearPsbtState()
                                navController.popBackStack()
                            },
                        )
                    }
                    composable(
                        route = Screen.LiquidPsetExport.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        LaunchedEffect(Unit) {
                            snackbarHostState.showSnackbar(psetCanceledMessage)
                            liquidViewModel.cancelPsetFlow()
                            navController.popBackStack()
                        }
                    }
                    composable(
                        route = Screen.BroadcastTransaction.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        val manualBroadcastState by viewModel.manualBroadcastState.collectAsStateWithLifecycle()

                        BroadcastTransactionScreen(
                            broadcastState = manualBroadcastState,
                            isConnected = uiState.isConnected,
                            onPreview = { data ->
                                viewModel.previewManualBroadcast(data)
                            },
                            onBroadcast = { data ->
                                viewModel.broadcastManualTransaction(data)
                            },
                            onClear = {
                                viewModel.clearManualBroadcastState()
                            },
                            onBack = {
                                navController.popBackStack()
                            },
                        )
                    }

                    // ── Layer 2 routes ──
                    composable(
                        route = Screen.LiquidServerConfig.route,
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        ServerConfigRoute(initialSection = ServerConfigSection.LIQUID)
                    }

                    composable(
                        route = Screen.Swap.route,
                    ) {
                        DisposableEffect(Unit) {
                            liquidViewModel.setSwapScreenActive(true)
                            onDispose {
                                liquidViewModel.setSwapScreenActive(false)
                            }
                        }
                        val liquidSwapState by liquidViewModel.swapState.collectAsStateWithLifecycle()
                        val pendingLiquidSwaps by liquidViewModel.pendingSwaps.collectAsStateWithLifecycle()
                        val boltzRescueMnemonic by liquidViewModel.boltzRescueMnemonic.collectAsStateWithLifecycle()
                        val liquidSwapLimits by liquidViewModel.swapLimits.collectAsStateWithLifecycle()
                        val preferredSwapService by liquidViewModel.preferredSwapService.collectAsStateWithLifecycle()
                        val liquidUtxos by liquidViewModel.allLiquidUtxos.collectAsStateWithLifecycle()
                        if (!isLiquidAvailable) {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Balance.route) {
                                    popUpTo(Screen.Swap.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                            return@composable
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            val bitcoinSwapUtxos by viewModel.allUtxos.collectAsStateWithLifecycle()
                            LayerSwitcher(
                                activeLayer = activeLayer,
                                onLayerSelected = { layer ->
                                    liquidViewModel.setActiveLayer(layer, walletState.activeWallet?.id)
                                    if (!navController.popBackStack()) {
                                        navController.navigate(Screen.Receive.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                inclusive = false
                                            }
                                        }
                                    }
                                },
                                isSwapSelected = true,
                                isSwapEnabled = swapEnabledForWallet,
                                isLayer1Enabled = isLayer1EnabledForWallet,
                                onSwap = { },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                SwapScreen(
                                    swapState = liquidSwapState,
                                    pendingSwaps = pendingLiquidSwaps,
                                    boltzRescueMnemonic = boltzRescueMnemonic,
                                    swapLimitsByService = liquidSwapLimits,
                                    boltzEnabled = boltzApiSource != SecureStorage.BOLTZ_API_DISABLED,
                                    sideSwapEnabled = sideSwapApiSource != SecureStorage.SIDESWAP_API_DISABLED,
                                    btcBalanceSats = walletState.balanceSats.toLong(),
                                    lbtcBalanceSats = liquidState.balanceSats,
                                    btcUtxos = bitcoinSwapUtxos,
                                    liquidUtxos = liquidUtxos,
                                    spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                                    btcPrice = btcPrice,
                                    fiatCurrency = priceCurrency,
                                    privacyMode = privacyMode,
                                    denomination = layer2Denomination,
                                    feeEstimationState = feeEstimationState,
                                    minFeeRate = minFeeRate,
                                    preferredService = preferredSwapService,
                                    onFetchLimits = { direction, service ->
                                        liquidViewModel.fetchSwapLimits(direction, service)
                                    },
                                    onPreferredServiceChange = { service ->
                                        liquidViewModel.setPreferredSwapService(service)
                                    },
                                    onRefreshBitcoinFees = { viewModel.fetchFeeEstimates() },
                                    onPrepareSwapReview = { direction, amount, service, selectedUtxos, destinationAddress, label, usesMaxAmount, fundingFeeRateOverride ->
                                        liquidViewModel.prepareSwapReview(
                                            direction = direction,
                                            amountSats = amount,
                                            service = service,
                                            selectedUtxos = selectedUtxos,
                                            bitcoinWalletAddress = walletState.currentAddress,
                                            destinationAddress = destinationAddress,
                                            label = label,
                                            usesMaxAmount = usesMaxAmount,
                                            fundingFeeRateOverride = fundingFeeRateOverride,
                                            resolveBitcoinMaxSend = { address, feeRate, selectedFundingUtxos ->
                                                viewModel.getMaxBitcoinSpendableForSwap(
                                                    recipientAddress = address,
                                                    feeRate = feeRate,
                                                    selectedUtxos = selectedFundingUtxos,
                                                )
                                            },
                                            previewBitcoinFunding = { address, amount, feeRate, selectedFundingUtxos, isMaxSend ->
                                                viewModel.previewBitcoinFundingForSwap(
                                                    recipientAddress = address,
                                                    amountSats = amount,
                                                    feeRate = feeRate,
                                                    selectedUtxos = selectedFundingUtxos,
                                                    isMaxSend = isMaxSend,
                                                )
                                            },
                                        )
                                    },
                                    onExecuteSwap = { pendingSwap, selectedUtxos ->
                                        liquidViewModel.executeSwap(pendingSwap, selectedUtxos) { address, amountSats, feeRate, fundingUtxos, isMaxSend ->
                                            viewModel.sendBitcoinForSwap(
                                                recipientAddress = address,
                                                amountSats = amountSats,
                                                feeRate = feeRate,
                                                selectedUtxos = fundingUtxos,
                                                isMaxSend = isMaxSend,
                                            )
                                        }
                                    },
                                    onCancelPreparedReview = {
                                        liquidViewModel.discardPreparedSwapReview()
                                    },
                                    onResetSwap = { liquidViewModel.resetSwapState() },
                                    onDismissFailedSwap = { liquidViewModel.dismissFailedSwap() },
                                    onToggleDenomination = toggleLayer2Denomination,
                                    onOpenLayer2Options = {
                                        navController.navigate(Screen.Layer2Options.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    isLiquidConnected = isLiquidConnected,
                                    isLiquidConnecting = showLiquidConnecting,
                                    hasLiquidServerConfigured =
                                        liquidServersState.hasUserSelectedServer &&
                                            liquidServersState.activeServerId != null,
                                    onConnectLiquidServer = {
                                        liquidServersState.activeServerId?.let(liquidViewModel::connectToLiquidServer)
                                    },
                                    onOpenLiquidServerSettings = {
                                        navController.navigate(Screen.LiquidServerConfig.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }
                        }
                    }

                    composable(route = Screen.LightningNodeChannels.route) {
                        if (!isLightningAvailable) {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Balance.route) {
                                    popUpTo(Screen.LightningNodeChannels.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                            return@composable
                        }
                        val channelsRoute =
                            currentDestination?.route == Screen.LightningNodeChannels.route
                        Column(modifier = Modifier.fillMaxSize()) {
                            LayerSwitcher(
                                activeLayer = activeLayer,
                                onLayerSelected = { layer ->
                                    liquidViewModel.setActiveLayer(layer, walletState.activeWallet?.id)
                                    if (!navController.popBackStack()) {
                                        navController.navigate(Screen.Balance.route) {
                                            popUpTo(Screen.LightningNodeChannels.route) {
                                                inclusive = true
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                isSwapSelected = channelsRoute,
                                isSwapEnabled = swapEnabledForWallet,
                                isLayer1Enabled = isLayer1EnabledForWallet,
                                layer2Color = layer2Accent,
                                layer2Label = layer2Label,
                                centerMode = LayerSwitcherCenterMode.CHANNELS,
                                onSwap = { },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                LightningNodeChannelsScreen(
                                    channels = lightningChannels,
                                    isLoading = lightningChannelsLoading,
                                    error = lightningChannelsError,
                                    isConnected = visibleLightningConnected,
                                    isConnecting = visibleLightningConnecting,
                                    connectionType = visibleLightningState.connectionType,
                                    denomination = layer2Denomination,
                                    privacyMode = privacyMode,
                                    onRefresh = { lightningNodeViewModel.refreshChannels() },
                                )
                            }
                        }
                    }

                    composable(
                        route = Screen.SparkTransfer.route,
                    ) {
                        if (!isSparkAvailable) {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Balance.route) {
                                    popUpTo(Screen.SparkTransfer.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                            return@composable
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            LayerSwitcher(
                                activeLayer = activeLayer,
                                onLayerSelected = { layer ->
                                    liquidViewModel.setActiveLayer(layer, walletState.activeWallet?.id)
                                    navController.navigate(Screen.Balance.route) {
                                        popUpTo(Screen.SparkTransfer.route) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                isSwapSelected = true,
                                isSwapEnabled = true,
                                layer2Color = layer2Accent,
                                layer2Label = layer2Label,
                                onSwap = { },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                SparkTransferScreen(
                                    sparkState = visibleSparkState,
                                    receiveState = sparkReceiveState,
                                    layer1Address = walletState.currentAddress,
                                    denomination = layer2Denomination,
                                    btcPrice = btcPrice,
                                    fiatCurrency = priceCurrency,
                                    privacyMode = privacyMode,
                                    layer1BalanceSats = walletState.balanceSats.toLong(),
                                    feeEstimationState = feeEstimationState,
                                    minFeeRate = minFeeRate,
                                    onRefreshBitcoinFees = { viewModel.fetchFeeEstimates() },
                                    onLoadSparkRecommendedFeeEstimates = {
                                        sparkViewModel.getRecommendedFeeEstimates()
                                    },
                                    onGenerateSparkDeposit = {
                                        sparkViewModel.receive(SparkReceiveKind.BITCOIN_ADDRESS)
                                    },
                                    onGenerateLayer1Address = { viewModel.getNewAddress() },
                                    onPreviewLayer1ToSpark = { address, amount, feeRate, isMaxSend ->
                                        viewModel.previewBitcoinFundingForSwap(
                                            recipientAddress = address,
                                            amountSats = amount,
                                            feeRate = feeRate,
                                            selectedUtxos = null,
                                            isMaxSend = isMaxSend,
                                        )
                                    },
                                    onExecuteLayer1ToSpark = { address, amount, feeRate, isMaxSend ->
                                        val (txid, depositedAmountSats) = viewModel.sendBitcoinForSwap(
                                            recipientAddress = address,
                                            amountSats = amount,
                                            feeRate = feeRate,
                                            selectedUtxos = null,
                                            isMaxSend = isMaxSend,
                                        )
                                        sparkViewModel.addLocalPendingDeposit(txid, depositedAmountSats, address)
                                    },
                                    onPreviewSparkToLayer1 = { address, amount, feeSpeed, isMaxSend ->
                                        sparkViewModel.prepareSendPreview(
                                            paymentRequest = address,
                                            amountSats = amount,
                                            onchainFeeSpeed = feeSpeed,
                                            useAllFunds = isMaxSend,
                                        )
                                    },
                                    onLoadSparkWithdrawalFeeQuotes = { address, amount, isMaxSend ->
                                        sparkViewModel.getOnchainFeeQuotes(address, amount, isMaxSend)
                                    },
                                    onExecuteSparkToLayer1 = {
                                        sparkViewModel.sendPreparedNow()
                                    },
                                    onResetSparkSend = {
                                        sparkViewModel.resetSendState()
                                    },
                                    onToggleDenomination = toggleLayer2Denomination,
                                )
                            }
                        }
                    }
                }

                // Wallet selector overlay panel
                if (isMainScreen) {
                    WalletSelectorPanel(
                        activeWallet = walletState.activeWallet,
                        wallets = filteredWallets,
                        expanded = walletSelectorExpanded,
                        onDismiss = { walletSelectorExpanded = false },
                        onSelectWallet = { walletId ->
                            requestWalletSelection(walletId)
                        },
                        onManageWallets = {
                            navController.navigate(Screen.ManageWallets.route)
                        },
                        onFullSync = { wallet ->
                            if (liquidViewModel.isLiquidEnabledForWallet(wallet.id)) {
                                liquidViewModel.requestFullSync(wallet.id)
                            }
                            if (sparkViewModel.isSparkEnabledForWallet(wallet.id)) {
                                sparkViewModel.refresh()
                            }
                            if (
                                wallet.derivationPath != "liquid_ct" &&
                                wallet.walletKind != github.aeonbtc.ibiswallet.data.model.WalletKind.LIGHTNING_NODE
                            ) {
                                viewModel.fullSync(wallet.id)
                            }
                        },
                        syncingWalletId = syncingWalletId,
                        lastFullSyncTimes = effectiveWalletLastFullSyncTimes,
                        layer2Enabled = isAnyLayer2Enabled,
                        liquidLayer2Enabled = isLayer2Enabled,
                        sparkLayer2Enabled = isSparkLayer2Enabled,
                        isLiquidEnabledForWallet = { walletId ->
                            liquidEnabledWallets[walletId]
                                ?: liquidViewModel.isLiquidEnabledForWallet(walletId)
                        },
                        isSparkEnabledForWallet = { walletId ->
                            sparkEnabledWallets[walletId]
                                ?: sparkViewModel.isSparkEnabledForWallet(walletId)
                        },
                        isLiquidWatchOnlyForWallet = { walletId ->
                            liquidViewModel.isLiquidWatchOnly(walletId)
                        },
                        lightningConfigForWallet = { walletId ->
                            lightningNodeViewModel.getConfig(walletId)
                        },
                        lightningConfigRevision = lightningConfigRevision,
                        onSetLiquidEnabledForWallet = { walletId, enabled ->
                            liquidViewModel.setLiquidEnabledForWallet(walletId, enabled)
                            if (enabled) {
                                sparkViewModel.setSparkEnabledForWallet(walletId, false)
                                lightningNodeViewModel.setLightningNodeEnabledForWallet(walletId, false)
                            }
                            if (walletId == walletState.activeWallet?.id && isLayer2Enabled) {
                                if (enabled) {
                                    liquidViewModel.loadLiquidWallet(walletId)
                                    sparkViewModel.unloadSparkWallet()
                                    lightningNodeViewModel.unloadLightningWallet()
                                } else {
                                    liquidViewModel.unloadLiquidWallet()
                                }
                            }
                        },
                        onSetSparkEnabledForWallet = { walletId, enabled ->
                            sparkViewModel.setSparkEnabledForWallet(walletId, enabled)
                            if (enabled) {
                                liquidViewModel.setLiquidEnabledForWallet(walletId, false)
                                lightningNodeViewModel.setLightningNodeEnabledForWallet(walletId, false)
                            }
                            if (walletId == walletState.activeWallet?.id && isLayer2Enabled) {
                                if (enabled) {
                                    liquidViewModel.unloadLiquidWallet()
                                    lightningNodeViewModel.unloadLightningWallet()
                                    sparkViewModel.loadSparkWallet(walletId)
                                } else {
                                    sparkViewModel.unloadSparkWallet()
                                }
                            }
                        },
                        isWalletLockAvailable = isSecurityEnabled,
                        onSetWalletLocked = { walletId, locked ->
                            if (!locked) {
                                requestDisableWalletLock(walletId)
                            } else {
                                enableWalletLock(walletId)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FullSyncProgressDialog(
    walletName: String?,
    progress: SyncProgress?,
    title: String = "",
    accentColor: Color = BitcoinOrange,
    onCancel: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val current = progress?.current ?: 0UL
    val total = progress?.total ?: 0UL
    val status = progress?.status
    val dialogTitle = title.ifBlank { stringResource(R.string.loc_b2ef417b) }
    val statusText =
        when {
            current > 0UL && status?.startsWith("Scanned") == true -> stringResource(R.string.loc_fe7aca52)
            !status.isNullOrBlank() -> status
            else -> stringResource(R.string.loc_a999bfe2)
        }
    val countText =
        when {
            total > 0UL -> stringResource(R.string.wallet_full_sync_count_format, current.toString(), total.toString())
            current > 0UL -> stringResource(R.string.wallet_full_sync_scanned_format, current.toString())
            else -> null
        }

    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 3.dp,
                )
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!walletName.isNullOrBlank()) {
                    Text(
                        text = walletName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                countText?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    onCancel?.let { cancel ->
                        TextButton(onClick = cancel) {
                            Text(
                                text = stringResource(R.string.loc_51bac044),
                                color = ErrorRed,
                            )
                        }
                    }
                    TextButton(onClick = onClose) {
                        Text(
                            text = stringResource(R.string.loc_d2c0aec0),
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
