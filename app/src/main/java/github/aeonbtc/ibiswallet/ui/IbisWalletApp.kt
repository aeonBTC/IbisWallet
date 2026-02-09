package github.aeonbtc.ibiswallet.ui

import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.SyncProgress
import github.aeonbtc.ibiswallet.navigation.Screen
import github.aeonbtc.ibiswallet.navigation.bottomNavItems
import github.aeonbtc.ibiswallet.ui.components.CertificateDialog
import github.aeonbtc.ibiswallet.ui.components.DrawerContent
import github.aeonbtc.ibiswallet.ui.components.DrawerItem
import github.aeonbtc.ibiswallet.ui.screens.AboutScreen
import github.aeonbtc.ibiswallet.ui.screens.AllAddressesScreen
import github.aeonbtc.ibiswallet.ui.screens.AllUtxosScreen
import github.aeonbtc.ibiswallet.ui.screens.BalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.ElectrumConfigScreen
import github.aeonbtc.ibiswallet.ui.screens.GenerateWalletScreen
import github.aeonbtc.ibiswallet.ui.screens.ImportWalletScreen
import github.aeonbtc.ibiswallet.ui.screens.KeyMaterialInfo
import github.aeonbtc.ibiswallet.ui.screens.ManageWalletsScreen
import github.aeonbtc.ibiswallet.ui.screens.ReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.SecurityScreen
import github.aeonbtc.ibiswallet.ui.screens.PsbtScreen
import github.aeonbtc.ibiswallet.ui.screens.SendScreen
import github.aeonbtc.ibiswallet.ui.screens.SettingsScreen
import github.aeonbtc.ibiswallet.ui.screens.WalletInfo
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.viewmodel.WalletEvent
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbisWalletApp(
    viewModel: WalletViewModel = viewModel(),
    onLockApp: () -> Unit = {}
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    val walletState by viewModel.walletState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val serversState by viewModel.serversState.collectAsState()
    val torState by viewModel.torState.collectAsState()
    val denomination by viewModel.denominationState.collectAsState()
    val mempoolServer by viewModel.mempoolServerState.collectAsState()
    val feeSource by viewModel.feeSourceState.collectAsState()
    val feeEstimationState by viewModel.feeEstimationState.collectAsState()
    val minFeeRate by viewModel.minFeeRate.collectAsState()
    val preSelectedUtxo by viewModel.preSelectedUtxo.collectAsState()
    val priceSource by viewModel.priceSourceState.collectAsState()
    val btcPrice by viewModel.btcPriceState.collectAsState()
    val syncingWalletId by viewModel.syncingWalletId.collectAsState()
    val sendScreenDraft by viewModel.sendScreenDraft.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val psbtState by viewModel.psbtState.collectAsState()
    val certDialogState by viewModel.certDialogState.collectAsState()
    val dryRunResult by viewModel.dryRunResult.collectAsState()
    
    // Global labels version counter - bumped when labels may have changed
    // (wallet imported with backup labels, wallet switched, sync completed)
    var labelsVersion by remember { mutableIntStateOf(0) }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Check if we're on a main screen (with bottom nav)
    val isMainScreen = currentDestination?.route in listOf(
        Screen.Receive.route,
        Screen.Balance.route,
        Screen.Send.route
    )
    
    // Check if we're on a drawer screen (should show bottom nav but not main top bar)
    val isDrawerScreen = currentDestination?.route in listOf(
        Screen.ManageWallets.route,
        Screen.ElectrumConfig.route,
        Screen.Settings.route,
        Screen.Security.route,
        Screen.About.route
    )

    // Check if we're on a sub-screen that should show back button in main TopAppBar
    val isSubScreenWithTopBar = currentDestination?.route in listOf(
        Screen.AllAddresses.route,
        Screen.AllUtxos.route
    )
    
    // Get title for sub-screens
    val subScreenTitle = when (currentDestination?.route) {
        Screen.AllAddresses.route -> "Addresses"
        Screen.AllUtxos.route -> "UTXOs"
        else -> ""
    }
    
    // Security state - tracks whether app lock is enabled for the lock icon
    var isSecurityEnabled by remember { mutableStateOf(viewModel.isSecurityEnabled()) }
    
    // Connection status dialog state
    var showConnectionStatusDialog by remember { mutableStateOf(false) }
    val electrumConfig = viewModel.getElectrumConfig()
    
    // Connection status dialog
    if (showConnectionStatusDialog) {
        ConnectionStatusDialog(
            isConnected = uiState.isConnected,
            isConnecting = uiState.isConnecting,
            serverName = electrumConfig?.name,
            serverUrl = electrumConfig?.url,
            serverPort = electrumConfig?.port,
            useSsl = electrumConfig?.useSsl ?: false,
            isOnion = electrumConfig?.isOnionAddress() ?: false,
            serverVersion = uiState.serverVersion,
            isTorActive = torState.status == TorStatus.CONNECTED,
            lastSyncTimestamp = walletState.lastSyncTimestamp,
            blockHeight = walletState.blockHeight,
            onDismiss = { showConnectionStatusDialog = false },
            onConnect = {
                serversState.activeServerId?.let { viewModel.connectToServer(it) }
            },
            onDisconnect = {
                viewModel.disconnect()
            },
            onConfigureServer = {
                showConnectionStatusDialog = false
                navController.navigate(Screen.ElectrumConfig.route)
            }
        )
    }
    
    // Full sync progress dialog - shows automatically during full scan
    if (walletState.isFullSyncing) {
        FullSyncProgressDialog(
            syncProgress = walletState.syncProgress
        )
    }
    
    
    // Certificate TOFU dialog
    certDialogState?.let { state ->
        CertificateDialog(
            state = state,
            onAccept = { viewModel.acceptCertificate() },
            onReject = { viewModel.rejectCertificate() }
        )
    }
    
    // Build wallet list for ManageWallets screen from all wallets in state
    val activeWalletId = walletState.activeWallet?.id
    val wallets = walletState.wallets.map { storedWallet ->
        WalletInfo(
            id = storedWallet.id,
            name = storedWallet.name,
            type = storedWallet.addressType.name.lowercase(),
            typeDescription = storedWallet.addressType.displayName,
            derivationPath = storedWallet.derivationPath,
            isActive = storedWallet.id == activeWalletId,
            isWatchOnly = storedWallet.isWatchOnly,
            lastFullSyncTime = viewModel.getLastFullSyncTime(storedWallet.id),
            masterFingerprint = storedWallet.masterFingerprint
        )
    }
    
    // Get string resources for use in event handling
    val walletAddedMessage = stringResource(R.string.wallet_added)

    // Handle events - show notifications and navigate as needed
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletEvent.Error -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
                is WalletEvent.WalletImported -> {
                    labelsVersion++
                    Toast.makeText(context, walletAddedMessage, Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Balance.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is WalletEvent.TransactionSent -> {
                    Toast.makeText(context, "Transaction sent successfully", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Wallet \"${event.walletName}\" exported", Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.SyncCompleted,
                is WalletEvent.WalletSwitched,
                is WalletEvent.LabelsRestored -> {
                    // Refresh labels - labels may have been restored from backup,
                    // changed due to wallet switch, or updated after sync
                    labelsVersion++
                }
                // Other success events - no notification needed, UI updates reflect the change
                is WalletEvent.Connected,
                is WalletEvent.FeeBumped,
                is WalletEvent.CpfpCreated,
                is WalletEvent.WalletDeleted,
                is WalletEvent.ServerDeleted,
                is WalletEvent.AddressGenerated -> { }
            }
        }
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
                        DrawerItem.Security -> {
                            navController.navigate(Screen.Security.route)
                        }
                        DrawerItem.About -> {
                            navController.navigate(Screen.About.route)
                        }
                    }
                }
            )
        },
        gesturesEnabled = isMainScreen
    ) {
        Scaffold(
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isMainScreen || isSubScreenWithTopBar) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSubScreenWithTopBar) subScreenTitle 
                                           else walletState.activeWallet?.name ?: "Ibis Wallet",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (isMainScreen && isSecurityEnabled) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = onLockApp,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock app",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            if (isSubScreenWithTopBar) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        },
                        actions = {
                            if (isMainScreen) {
                                // Connection status indicator only on main screens
                                ConnectionStatusIndicator(
                                    isConnected = uiState.isConnected,
                                    isConnecting = uiState.isConnecting,
                                    onClick = { showConnectionStatusDialog = true }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkBackground,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (isMainScreen || isDrawerScreen) {
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { 
                                it.route == item.screen.route 
                            } == true
                            
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium
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
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BitcoinOrange,
                                    selectedTextColor = BitcoinOrange,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Balance.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) }
            ) {
                composable(Screen.Receive.route) {
                    // Fetch price when entering Receive screen
                    LaunchedEffect(Unit) {
                        viewModel.fetchBtcPrice()
                    }
                    
                    ReceiveScreen(
                        walletState = walletState,
                        denomination = denomination,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        onGenerateAddress = { viewModel.getNewAddress() },
                        onSaveLabel = { address, label -> viewModel.saveAddressLabel(address, label) },
                        onShowAllAddresses = { navController.navigate(Screen.AllAddresses.route) },
                        onShowAllUtxos = { navController.navigate(Screen.AllUtxos.route) }
                    )
                }
                composable(Screen.Balance.route) {
                    // Fetch price when entering Balance screen
                    LaunchedEffect(Unit) {
                        viewModel.fetchBtcPrice()
                    }
                    
                    // Labels are refreshed when labelsVersion changes (global counter
                    // bumped on wallet import, wallet switch, sync, or manual label save)
                    val transactionLabels = remember(labelsVersion) { viewModel.getAllTransactionLabels() }
                    val addressLabels = remember(labelsVersion) { viewModel.getAllAddressLabels() }
                    
                    BalanceScreen(
                        walletState = walletState,
                        uiState = uiState,
                        denomination = denomination,
                        mempoolUrl = viewModel.getMempoolUrl(),
                        mempoolServer = viewModel.getMempoolServer(),
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        onTogglePrivacy = { viewModel.togglePrivacyMode() },
                        addressLabels = addressLabels,
                        transactionLabels = transactionLabels,
                        feeEstimationState = feeEstimationState,
                        canBumpFee = { txid -> viewModel.canBumpFee(txid) },
                        canCpfp = { txid -> viewModel.canCpfp(txid) },
                        onBumpFee = { txid, feeRate -> viewModel.bumpFee(txid, feeRate) },
                        onCpfp = { txid, feeRate -> viewModel.cpfp(txid, feeRate) },
                        onSaveTransactionLabel = { txid, label -> 
                            viewModel.saveTransactionLabel(txid, label)
                            labelsVersion++
                        },
                        onFetchTxVsize = { txid -> viewModel.fetchTransactionVsize(txid) },
                        onRefreshFees = { viewModel.fetchFeeEstimates() },
                        onSync = { viewModel.sync() },
                        onManageWallets = { navController.navigate(Screen.ManageWallets.route) }
                    )
                }
                composable(Screen.Send.route) {
                    val utxos = viewModel.getAllUtxos()
                    
                    // Collect preSelectedUtxo directly here to ensure fresh value
                    val currentPreSelectedUtxo by viewModel.preSelectedUtxo.collectAsState()
                    
                    // Get all wallet addresses for self-transfer detection
                    val allWalletAddresses = remember(walletState) {
                        val (receive, change, used) = viewModel.getAllAddresses()
                        (receive.map { it.address } + change.map { it.address } + used.map { it.address }).toSet()
                    }
                    
                    // Fetch fee estimates and price when entering Send screen
                    LaunchedEffect(Unit) {
                        viewModel.fetchFeeEstimates()
                        viewModel.fetchBtcPrice()
                    }
                    
                    SendScreen(
                        walletState = walletState,
                        uiState = uiState,
                        denomination = denomination,
                        utxos = utxos,
                        walletAddresses = allWalletAddresses,
                        feeEstimationState = feeEstimationState,
                        minFeeRate = minFeeRate,
                        preSelectedUtxo = currentPreSelectedUtxo,
                        spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        isWatchOnly = walletState.activeWallet?.isWatchOnly == true,
                        draft = sendScreenDraft,
                        dryRunResult = dryRunResult,
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
                        onSend = { address, amount, feeRate, selectedUtxos, label, isMaxSend, precomputedFeeSats ->
                            viewModel.sendBitcoin(address, amount, feeRate, selectedUtxos, label, isMaxSend, precomputedFeeSats)
                        },
                        onSendMulti = { recipients, feeRate, selectedUtxos, label, precomputedFeeSats ->
                            viewModel.sendBitcoinMulti(recipients, feeRate, selectedUtxos, label, precomputedFeeSats)
                        },
                        onCreatePsbt = { address, amount, feeRate, selectedUtxos, label, isMaxSend, precomputedFeeSats ->
                            viewModel.createPsbt(address, amount, feeRate, selectedUtxos, label, isMaxSend, precomputedFeeSats)
                        },
                        onCreatePsbtMulti = { recipients, feeRate, selectedUtxos, label, precomputedFeeSats ->
                            viewModel.createPsbtMulti(recipients, feeRate, selectedUtxos, label, precomputedFeeSats)
                        }
                    )
                }
                composable(
                    route = Screen.ManageWallets.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    ManageWalletsScreen(
                        wallets = wallets,
                        onBack = { navController.popBackStack() },
                        onImportWallet = { navController.navigate(Screen.ImportWallet.route) },
                        onGenerateWallet = { navController.navigate(Screen.GenerateWallet.route) },
                        onViewWallet = { wallet ->
                            // Get key material from ViewModel
                            viewModel.getKeyMaterial(wallet.id)?.let { keyMaterial ->
                                KeyMaterialInfo(
                                    walletName = wallet.name,
                                    mnemonic = keyMaterial.mnemonic,
                                    extendedPublicKey = keyMaterial.extendedPublicKey,
                                    isWatchOnly = keyMaterial.isWatchOnly,
                                    masterFingerprint = wallet.masterFingerprint
                                )
                            }
                        },
                        onDeleteWallet = { wallet ->
                            viewModel.deleteWallet(wallet.id)
                        },
                        onSelectWallet = { wallet ->
                            viewModel.switchWallet(wallet.id)
                        },
                        onExportWallet = { walletId, uri, includeLabels, password ->
                            viewModel.exportWallet(walletId, uri, includeLabels, password)
                        },
                        onEditWallet = { walletId, newName, newFingerprint ->
                            viewModel.editWallet(walletId, newName, newFingerprint)
                        },
                        onFullSync = { wallet ->
                            viewModel.fullSync(wallet.id)
                        },
                        syncingWalletId = syncingWalletId
                    )
                }
                composable(
                    route = Screen.ImportWallet.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    ImportWalletScreen(
                        onImport = { config ->
                            viewModel.importWallet(config)
                        },
                        onImportFromBackup = { backupJson ->
                            viewModel.importFromBackup(backupJson)
                        },
                        onParseBackupFile = { uri, password ->
                            viewModel.parseBackupFile(uri, password)
                        },
                        onBack = { navController.popBackStack() },
                        isLoading = uiState.isLoading,
                        error = uiState.error
                    )
                }
                composable(
                    route = Screen.GenerateWallet.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    GenerateWalletScreen(
                        onGenerate = { config ->
                            viewModel.generateWallet(config)
                        },
                        onBack = { navController.popBackStack() },
                        isLoading = uiState.isLoading,
                        error = uiState.error
                    )
                }
                composable(
                    route = Screen.ElectrumConfig.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    ElectrumConfigScreen(
                        onConnect = { config ->
                            viewModel.connectToElectrum(config)
                        },
                        onBack = { navController.popBackStack() },
                        currentConfig = viewModel.getElectrumConfig(),
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
                        torState = torState,
                        isTorEnabled = viewModel.isTorEnabled(),
                        onTorEnabledChange = { enabled -> viewModel.setTorEnabled(enabled) },
                        serverVersion = uiState.serverVersion,
                        blockHeight = walletState.blockHeight
                    )
                }
                composable(
                    route = Screen.Settings.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    var customMempoolUrl by remember { mutableStateOf(viewModel.getCustomMempoolUrl()) }
                    var customFeeSourceUrl by remember { mutableStateOf(viewModel.getCustomFeeSourceUrl()) }
                    var spendUnconfirmed by remember { mutableStateOf(viewModel.getSpendUnconfirmed()) }

                    
                    SettingsScreen(
                        currentDenomination = denomination,
                        onDenominationChange = { newDenomination ->
                            viewModel.setDenomination(newDenomination)
                        },
                        spendUnconfirmed = spendUnconfirmed,
                        onSpendUnconfirmedChange = { enabled ->
                            viewModel.setSpendUnconfirmed(enabled)
                            spendUnconfirmed = enabled
                        },
                        currentFeeSource = feeSource,
                        onFeeSourceChange = { newSource ->
                            viewModel.setFeeSource(newSource)
                        },
                        customFeeSourceUrl = customFeeSourceUrl,
                        onCustomFeeSourceUrlSave = { newUrl ->
                            customFeeSourceUrl = newUrl
                            viewModel.setCustomFeeSourceUrl(newUrl)
                            // Auto-enable and start Tor for .onion addresses
                            if (newUrl.contains(".onion")) {
                                if (!viewModel.isTorEnabled()) {
                                    viewModel.setTorEnabled(true)
                                } else if (!viewModel.isTorReady()) {
                                    viewModel.startTor()
                                }
                            }
                        },
                        currentPriceSource = priceSource,
                        onPriceSourceChange = { newSource ->
                            viewModel.setPriceSource(newSource)
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
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.Security.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    // Use mutableState so UI recomposes when settings change
                    var securityMethod by remember { mutableStateOf(viewModel.getSecurityMethod()) }
                    var lockTiming by remember { mutableStateOf(viewModel.getLockTiming()) }
                    var screenshotsDisabled by remember { mutableStateOf(viewModel.getDisableScreenshots()) }
                    
                    // Check if device has biometric hardware
                    val biometricManager = androidx.biometric.BiometricManager.from(context)
                    val isBiometricAvailable = biometricManager.canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                    
                    SecurityScreen(
                        currentSecurityMethod = securityMethod,
                        currentLockTiming = lockTiming,
                        isBiometricAvailable = isBiometricAvailable,
                        screenshotsDisabled = screenshotsDisabled,
                        onSetPinCode = { pin ->
                            viewModel.savePin(pin)
                            viewModel.setSecurityMethod(SecureStorage.SecurityMethod.PIN)
                            securityMethod = SecureStorage.SecurityMethod.PIN
                            isSecurityEnabled = true
                        },
                        onEnableBiometric = {
                            viewModel.setSecurityMethod(SecureStorage.SecurityMethod.BIOMETRIC)
                            securityMethod = SecureStorage.SecurityMethod.BIOMETRIC
                            isSecurityEnabled = true
                        },
                        onDisableSecurity = {
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
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                )
                            } else {
                                activity?.window?.clearFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                )
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.About.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.AllAddresses.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    // Trigger recomposition after generating new address
                    var addressListVersion by remember { mutableIntStateOf(0) }
                    
                    // Fetch addresses - uses addressListVersion as key to refresh after generating
                    val addresses = remember(addressListVersion) { viewModel.getAllAddresses() }
                    
                    AllAddressesScreen(
                        receiveAddresses = addresses.first,
                        changeAddresses = addresses.second,
                        usedAddresses = addresses.third,
                        denomination = denomination,
                        privacyMode = privacyMode,
                        onGenerateReceiveAddress = {
                            val newAddress = viewModel.getNewAddressSuspend()
                            addressListVersion++
                            newAddress
                        },
                        onSaveLabel = { address, label ->
                            viewModel.saveAddressLabel(address, label)
                            addressListVersion++
                        },
                        onDeleteLabel = { address ->
                            viewModel.deleteAddressLabel(address)
                            addressListVersion++
                        }
                    )
                }
                composable(
                    route = Screen.AllUtxos.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    val utxos = viewModel.getAllUtxos()
                    
                    AllUtxosScreen(
                        utxos = utxos,
                        denomination = denomination,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        spendUnconfirmed = viewModel.getSpendUnconfirmed(),
                        onFreezeUtxo = { outpoint, frozen ->
                            viewModel.setUtxoFrozen(outpoint, frozen)
                        },
                        onSendFromUtxo = { utxo ->
                            viewModel.setPreSelectedUtxo(utxo)
                            // Simple navigation to Send screen
                            navController.navigate(Screen.Send.route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.PsbtExport.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    PsbtScreen(
                        psbtState = psbtState,
                        uiState = uiState,
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
                        }
                    )
                }
            }
        }
    }
}

/**
 * Dialog showing full sync progress.
 * Non-dismissable - displayed while BDK full address discovery scan is running.
 * Shows the current keychain being scanned and address count.
 */
@Composable
private fun FullSyncProgressDialog(
    syncProgress: SyncProgress?
) {
    Dialog(
        onDismissRequest = { /* non-dismissable during full sync */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Full Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = BitcoinOrange,
                    strokeWidth = 4.dp,
                    trackColor = DarkCard
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status text
                Text(
                    text = "Scanning addresses...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                // Progress details
                if (syncProgress != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Info card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (syncProgress.keychain != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Keychain",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                                Text(
                                    text = syncProgress.keychain!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Addresses checked",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                            Text(
                                text = "${syncProgress.current}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This may take a while for large wallets",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * Connection status indicator shown in the app bar
 */
@Composable
private fun ConnectionStatusIndicator(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when {
        isConnecting -> BitcoinOrange
        isConnected -> SuccessGreen
        else -> ErrorRed
    }

    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .offset(y = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = statusColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(statusColor.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = BitcoinOrange,
                    strokeWidth = 1.5.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    isConnecting -> "Connecting"
                    isConnected -> "Connected"
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

/**
 * Dialog showing connection status details
 */
@Composable
private fun ConnectionStatusDialog(
    isConnected: Boolean,
    isConnecting: Boolean,
    serverName: String?,
    serverUrl: String?,
    serverPort: Int?,
    useSsl: Boolean,
    isOnion: Boolean,
    serverVersion: String?,
    isTorActive: Boolean,
    lastSyncTimestamp: Long?,
    blockHeight: UInt? = null,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onConfigureServer: () -> Unit
) {
    val statusColor = when {
        isConnecting -> BitcoinOrange
        isConnected -> SuccessGreen
        else -> ErrorRed
    }
    val statusText = when {
        isConnecting -> "Connecting"
        isConnected -> "Connected"
        else -> "Disconnected"
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Status header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                    // Badges
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (serverUrl != null) {
                            val sslColor = if (useSsl) SuccessGreen else TextSecondary
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = sslColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, sslColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (useSsl) "SSL" else "TCP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sslColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Tor badge - only for onion servers
                        if (isOnion) {
                            val purple = Color(0xFF9B59B6)
                            val torColor = if (isTorActive) purple else TextSecondary
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = torColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, torColor.copy(alpha = if (isTorActive) 0.4f else 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isTorActive) purple else TextSecondary.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tor",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = torColor
                                    )
                            }
                        }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (serverUrl != null) {
                    // Server info card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (serverName != null) {
                            DialogDetailRow("Name:", serverName)
                        }
                        DialogDetailRow("Address:", serverUrl, monospace = true)
                        DialogDetailRow("Port:", "${serverPort ?: 50001}", monospace = true)
                        if (isConnected && serverVersion != null) {
                            DialogDetailRow("Software:", serverVersion)
                        }
                        if (isConnected && blockHeight != null && blockHeight > 0u) {
                            DialogDetailRow("Block:", "%,d".format(blockHeight.toInt()))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No server configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (serverUrl != null) {
                        val isDisconnectAction = isConnected || isConnecting
                        val actionColor = if (isDisconnectAction) ErrorRed else SuccessGreen
                        OutlinedButton(
                            onClick = if (isDisconnectAction) onDisconnect else onConnect,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, actionColor.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = actionColor,
                                containerColor = actionColor.copy(alpha = 0.08f)
                            )
                        ) {
                            Text(
                                text = if (isDisconnectAction) "Disconnect" else "Connect",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onConfigureServer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text(
                            text = if (serverUrl != null) "Change Server" else "Configure Sever",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Labeled detail row for the connection status dialog
 */
@Composable
private fun DialogDetailRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = 1
        )
    }
}
