package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AppCurrency
import com.example.data.model.PortfolioHealthMetrics
import com.example.data.network.MarketRepository
import com.example.data.repository.PortfolioRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.EmiCalculatorScreen
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.WealthLabScreen
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenSoft
import com.example.ui.theme.PulseFolioTheme
import com.example.ui.viewmodel.PulseFolioViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            PulseFolioTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

enum class PulseDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MARKETS("markets", R.string.nav_markets, Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
    PORTFOLIO("portfolio", R.string.nav_portfolio, Icons.Filled.PieChart, Icons.Outlined.PieChart),
    EMI_LAB("emi_lab", R.string.nav_emi_lab, Icons.Filled.Calculate, Icons.Outlined.Calculate),
    WEALTH_LAB("wealth_lab", R.string.nav_wealth_lab, Icons.Filled.AutoGraph, Icons.Outlined.AutoGraph)
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        PulseFolioMainShell(
            currentUserId = user.uid,
            userDisplayName = user.displayName ?: user.email ?: "Investor",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseFolioMainShell(
    currentUserId: String,
    userDisplayName: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: PulseFolioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PulseFolioViewModel(
                    portfolioRepository = PortfolioRepository(db),
                    marketRepository = MarketRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    var currentDestination by rememberSaveable { mutableStateOf(PulseDestination.MARKETS) }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    if (currentDestination != PulseDestination.MARKETS) {
        BackHandler {
            currentDestination = PulseDestination.MARKETS
        }
    }

    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val allQuotes by viewModel.liveQuotes.collectAsStateWithLifecycle()
    val filteredQuotes by viewModel.filteredQuotes.collectAsStateWithLifecycle()
    val selectedQuote by viewModel.selectedQuote.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSector by viewModel.selectedSectorFilter.collectAsStateWithLifecycle()
    val portfolioState by viewModel.portfolioSummaryState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val emiLoanType by viewModel.emiLoanType.collectAsStateWithLifecycle()
    val emiPrincipal by viewModel.emiPrincipal.collectAsStateWithLifecycle()
    val emiAnnualRate by viewModel.emiAnnualRate.collectAsStateWithLifecycle()
    val emiTenureMonths by viewModel.emiTenureMonths.collectAsStateWithLifecycle()
    val emiPrepayment by viewModel.emiMonthlyPrepayment.collectAsStateWithLifecycle()
    val emiCalculation by viewModel.emiCalculation.collectAsStateWithLifecycle()
    val emiPlansState by viewModel.emiPlansState.collectAsStateWithLifecycle()
    val sipCalculation by viewModel.sipCalculation.collectAsStateWithLifecycle()
    val fireCalculation by viewModel.fireCalculation.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PulseFolio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = BullishGreenSoft,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BullishGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        // Global Currency Switcher Pill
                        Box {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showCurrencyMenu = true }
                                    .testTag("currency_selector_button")
                            ) {
                                Text(
                                    text = "${selectedCurrency.flagEmoji} ${selectedCurrency.code}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                AppCurrency.entries.forEach { curr ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${curr.flagEmoji} ${curr.code} (${curr.symbol.trim()})")
                                        },
                                        onClick = {
                                            viewModel.selectCurrency(curr)
                                            showCurrencyMenu = false
                                        },
                                        modifier = Modifier.testTag("currency_option_${curr.code}")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                        IconButton(
                            onClick = {
                                signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {},
                                    scope = coroutineScope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.sign_out)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        PulseDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            PulseDestination.entries.forEach { dest ->
                                val selected = currentDestination == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_rail_${dest.route}")
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentDestination) {
                        PulseDestination.MARKETS -> {
                            val watched = (watchlistState as? UiState.Success)?.data ?: emptyList()
                            MarketsScreen(
                                currency = selectedCurrency,
                                allQuotes = allQuotes,
                                filteredQuotes = filteredQuotes,
                                selectedQuote = selectedQuote,
                                selectedTimeframe = selectedTimeframe,
                                searchQuery = searchQuery,
                                selectedSector = selectedSector,
                                watchlist = watched,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSectorSelected = viewModel::selectSectorFilter,
                                onStockSelected = viewModel::selectStock,
                                onTimeframeSelected = viewModel::selectTimeframe,
                                onAddHolding = viewModel::addOrUpdatePortfolioPosition,
                                onToggleWatchlist = viewModel::toggleWatchlist
                            )
                        }
                        PulseDestination.PORTFOLIO -> {
                            PortfolioScreen(
                                currency = selectedCurrency,
                                portfolioState = portfolioState,
                                watchlistState = watchlistState,
                                liveQuotes = allQuotes,
                                onAddCustomPosition = viewModel::addOrUpdatePortfolioPosition,
                                onUpdateHolding = viewModel::updateExistingHolding,
                                onDeleteHolding = viewModel::deleteHolding,
                                onSeedStarterPortfolio = viewModel::seedStarterPortfolio,
                                onRemoveWatchlistItem = viewModel::toggleWatchlist
                            )
                        }
                        PulseDestination.EMI_LAB -> {
                            EmiCalculatorScreen(
                                currency = selectedCurrency,
                                selectedLoanType = emiLoanType,
                                principal = emiPrincipal,
                                annualRate = emiAnnualRate,
                                tenureMonths = emiTenureMonths,
                                monthlyPrepayment = emiPrepayment,
                                calculation = emiCalculation,
                                savedPlansState = emiPlansState,
                                onSelectPreset = viewModel::selectEmiPreset,
                                onPrincipalChange = viewModel::updateEmiPrincipal,
                                onAnnualRateChange = viewModel::updateEmiAnnualRate,
                                onTenureMonthsChange = viewModel::updateEmiTenureMonths,
                                onMonthlyPrepaymentChange = viewModel::updateEmiMonthlyPrepayment,
                                onSavePlan = viewModel::saveCurrentEmiPlan,
                                onLoadPlan = viewModel::loadSavedEmiPlan,
                                onDeletePlan = viewModel::deleteSavedEmiPlan
                            )
                        }
                        PulseDestination.WEALTH_LAB -> {
                            val health = (portfolioState as? UiState.Success)?.data?.healthMetrics
                                ?: PortfolioHealthMetrics(
                                    diversificationScore = 0,
                                    riskGrade = "Unallocated",
                                    topHoldingSymbol = "-",
                                    topHoldingWeightPct = 0.0,
                                    sectorCount = 0,
                                    estimatedAnnualDividendUsd = 0.0,
                                    averageYieldPct = 0.0,
                                    insightTip = "Add positions in the Portfolio tab to unlock your Diversification Score and Dividend Forecast."
                                )
                            WealthLabScreen(
                                currency = selectedCurrency,
                                sipResult = sipCalculation,
                                fireResult = fireCalculation,
                                healthMetrics = health,
                                onSipMonthlyChange = viewModel::updateSipMonthlyUsd,
                                onSipLumpSumChange = viewModel::updateSipInitialLumpSumUsd,
                                onSipReturnRateChange = viewModel::updateSipAnnualReturnPct,
                                onSipStepUpChange = viewModel::updateSipStepUpPct,
                                onSipYearsChange = viewModel::updateSipDurationYears,
                                onFireMonthlyExpensesChange = viewModel::updateFireMonthlyExpensesUsd
                            )
                        }
                    }
                }
            }
        }
    }
}
