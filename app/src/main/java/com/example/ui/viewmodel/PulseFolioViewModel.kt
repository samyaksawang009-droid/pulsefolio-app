package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppCurrency
import com.example.data.model.ChartTimeframe
import com.example.data.model.EmiCalculationResult
import com.example.data.model.EmiPlan
import com.example.data.model.FireCalculationResult
import com.example.data.model.PortfolioHealthMetrics
import com.example.data.model.PortfolioHolding
import com.example.data.model.SipCalculationResult
import com.example.data.model.StockQuote
import com.example.data.model.WatchlistItem
import com.example.data.network.MarketRepository
import com.example.data.repository.PortfolioRepository
import com.example.domain.EmiCalculatorEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class EnrichedHolding(
    val holding: PortfolioHolding,
    val livePrice: Double,
    val dayChangePercent: Double,
    val marketValue: Double,
    val investedCost: Double,
    val totalPnL: Double,
    val totalPnLPercent: Double,
    val dayPnL: Double
)

data class PortfolioSummary(
    val holdings: List<EnrichedHolding>,
    val totalMarketValue: Double,
    val totalInvestedCost: Double,
    val totalUnrealizedPnL: Double,
    val totalUnrealizedPnLPercent: Double,
    val totalDayPnL: Double,
    val totalDayPnLPercent: Double,
    val healthMetrics: PortfolioHealthMetrics
)

@OptIn(ExperimentalCoroutinesApi::class)
class PulseFolioViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val marketRepository: MarketRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedCurrency = MutableStateFlow(AppCurrency.USD)
    val selectedCurrency: StateFlow<AppCurrency> = _selectedCurrency.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSectorFilter = MutableStateFlow("All")
    val selectedSectorFilter: StateFlow<String> = _selectedSectorFilter.asStateFlow()

    private val _selectedSymbol = MutableStateFlow("NVDA")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(ChartTimeframe.ONE_DAY)
    val selectedTimeframe: StateFlow<ChartTimeframe> = _selectedTimeframe.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // EMI Calculator reactive inputs (stored in base USD)
    private val _emiLoanType = MutableStateFlow("Home Loan")
    val emiLoanType: StateFlow<String> = _emiLoanType.asStateFlow()

    private val _emiPrincipal = MutableStateFlow(350000.0)
    val emiPrincipal: StateFlow<Double> = _emiPrincipal.asStateFlow()

    private val _emiAnnualRate = MutableStateFlow(6.65)
    val emiAnnualRate: StateFlow<Double> = _emiAnnualRate.asStateFlow()

    private val _emiTenureMonths = MutableStateFlow(240)
    val emiTenureMonths: StateFlow<Int> = _emiTenureMonths.asStateFlow()

    private val _emiMonthlyPrepayment = MutableStateFlow(250.0)
    val emiMonthlyPrepayment: StateFlow<Double> = _emiMonthlyPrepayment.asStateFlow()

    val emiCalculation: StateFlow<EmiCalculationResult> = combine(
        _emiPrincipal,
        _emiAnnualRate,
        _emiTenureMonths,
        _emiMonthlyPrepayment
    ) { principal, rate, months, prepay ->
        EmiCalculatorEngine.calculate(
            principal = principal,
            annualRatePercent = rate,
            tenureMonths = months,
            monthlyPrepayment = prepay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = EmiCalculatorEngine.calculate(350000.0, 6.65, 240, 250.0)
    )

    // SIP / Compounding & FIRE Planner state (stored in base USD)
    private val _sipMonthlyUsd = MutableStateFlow(1000.0)
    val sipMonthlyUsd: StateFlow<Double> = _sipMonthlyUsd.asStateFlow()

    private val _sipInitialLumpSumUsd = MutableStateFlow(10000.0)
    val sipInitialLumpSumUsd: StateFlow<Double> = _sipInitialLumpSumUsd.asStateFlow()

    private val _sipAnnualReturnPct = MutableStateFlow(12.0)
    val sipAnnualReturnPct: StateFlow<Double> = _sipAnnualReturnPct.asStateFlow()

    private val _sipStepUpPct = MutableStateFlow(10.0)
    val sipStepUpPct: StateFlow<Double> = _sipStepUpPct.asStateFlow()

    private val _sipDurationYears = MutableStateFlow(15)
    val sipDurationYears: StateFlow<Int> = _sipDurationYears.asStateFlow()

    private val _fireMonthlyExpensesUsd = MutableStateFlow(3500.0)
    val fireMonthlyExpensesUsd: StateFlow<Double> = _fireMonthlyExpensesUsd.asStateFlow()

    val sipCalculation: StateFlow<SipCalculationResult> = combine(
        _sipMonthlyUsd,
        _sipInitialLumpSumUsd,
        _sipAnnualReturnPct,
        _sipStepUpPct,
        _sipDurationYears
    ) { monthly, lump, ret, stepUp, yrs ->
        EmiCalculatorEngine.calculateSip(
            monthlyInvestment = monthly,
            initialLumpSum = lump,
            expectedAnnualReturnPct = ret,
            annualStepUpPct = stepUp,
            durationYears = yrs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = EmiCalculatorEngine.calculateSip(1000.0, 10000.0, 12.0, 10.0, 15)
    )

    // Two-Tier Kotlin Flow Error Handling for Firestore Streams
    val holdingsState: StateFlow<UiState<List<PortfolioHolding>>> =
        portfolioRepository.observeHoldings(currentUserId)
            .map<List<PortfolioHolding>, UiState<List<PortfolioHolding>>> { UiState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing holdings", error)
                emit(UiState.Error(error.message ?: "Failed to load portfolio holdings"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val watchlistState: StateFlow<UiState<List<WatchlistItem>>> =
        portfolioRepository.observeWatchlist(currentUserId)
            .map<List<WatchlistItem>, UiState<List<WatchlistItem>>> { UiState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing watchlist", error)
                emit(UiState.Error(error.message ?: "Failed to load watchlist"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val emiPlansState: StateFlow<UiState<List<EmiPlan>>> =
        portfolioRepository.observeEmiPlans(currentUserId)
            .map<List<EmiPlan>, UiState<List<EmiPlan>>> { UiState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing EMI plans", error)
                emit(UiState.Error(error.message ?: "Failed to load saved EMI plans"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val customTrackedSymbols = combine(holdingsState, watchlistState) { hState, wState ->
        val hSyms = (hState as? UiState.Success)?.data?.map { it.symbol } ?: emptyList()
        val wSyms = (wState as? UiState.Success)?.data?.map { it.symbol } ?: emptyList()
        (hSyms + wSyms).toSet()
    }

    val liveQuotes: StateFlow<List<StockQuote>> = customTrackedSymbols
        .flatMapLatest { extraSyms ->
            marketRepository.streamRealTimeQuotes(extraSyms)
        }
        .catch { e ->
            Log.w(TAG, "Error in market stream", e)
            emit(marketRepository.getCachedQuotes())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = marketRepository.getCachedQuotes()
        )

    val filteredQuotes: StateFlow<List<StockQuote>> = combine(
        liveQuotes,
        _searchQuery,
        _selectedSectorFilter
    ) { quotes, query, sector ->
        quotes.filter { q ->
            val matchesSearch = query.isBlank() ||
                q.symbol.contains(query.trim(), ignoreCase = true) ||
                q.companyName.contains(query.trim(), ignoreCase = true)
            val matchesSector = sector == "All" || q.sector.equals(sector, ignoreCase = true)
            matchesSearch && matchesSector
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = marketRepository.getCachedQuotes()
    )

    val selectedQuote: StateFlow<StockQuote?> = combine(
        liveQuotes,
        _selectedSymbol
    ) { quotes, symbol ->
        quotes.find { it.symbol.equals(symbol, ignoreCase = true) } ?: quotes.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = marketRepository.getCachedQuotes().firstOrNull()
    )

    val portfolioSummaryState: StateFlow<UiState<PortfolioSummary>> = combine(
        holdingsState,
        liveQuotes
    ) { hState, quotes ->
        when (hState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(hState.message)
            is UiState.Success -> {
                val quoteMap = quotes.associateBy { it.symbol.uppercase() }
                val enriched = hState.data.map { holding ->
                    val quote = quoteMap[holding.symbol.uppercase()]
                    val livePrice = quote?.price ?: holding.avgBuyPrice
                    val dayChangePct = quote?.changePercent ?: 0.0
                    val marketVal = holding.shares * livePrice
                    val cost = holding.shares * holding.avgBuyPrice
                    val pnl = marketVal - cost
                    val pnlPct = if (cost > 0) (pnl / cost) * 100.0 else 0.0
                    val prevClose = quote?.previousClose ?: livePrice
                    val dayPnl = holding.shares * (livePrice - prevClose)
                    EnrichedHolding(
                        holding = holding,
                        livePrice = livePrice,
                        dayChangePercent = dayChangePct,
                        marketValue = marketVal,
                        investedCost = cost,
                        totalPnL = pnl,
                        totalPnLPercent = pnlPct,
                        dayPnL = dayPnl
                    )
                }
                val totalVal = enriched.sumOf { it.marketValue }
                val totalCost = enriched.sumOf { it.investedCost }
                val totalPnl = totalVal - totalCost
                val totalPnlPct = if (totalCost > 0) (totalPnl / totalCost) * 100.0 else 0.0
                val totalDayPnl = enriched.sumOf { it.dayPnL }
                val prevPortfolioVal = (totalVal - totalDayPnl).coerceAtLeast(1.0)
                val totalDayPct = if (enriched.isNotEmpty()) (totalDayPnl / prevPortfolioVal) * 100.0 else 0.0
                val health = EmiCalculatorEngine.analyzePortfolioHealth(enriched, quotes)

                UiState.Success(
                    PortfolioSummary(
                        holdings = enriched,
                        totalMarketValue = totalVal,
                        totalInvestedCost = totalCost,
                        totalUnrealizedPnL = totalPnl,
                        totalUnrealizedPnLPercent = totalPnlPct,
                        totalDayPnL = totalDayPnl,
                        totalDayPnLPercent = totalDayPct,
                        healthMetrics = health
                    )
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val fireCalculation: StateFlow<FireCalculationResult> = combine(
        _fireMonthlyExpensesUsd,
        portfolioSummaryState,
        _sipMonthlyUsd,
        _sipAnnualReturnPct
    ) { monthlyExp, pState, sipMonthly, annualReturn ->
        val summary = (pState as? UiState.Success)?.data
        val currentVal = summary?.totalMarketValue ?: 0.0
        val annualDiv = summary?.healthMetrics?.estimatedAnnualDividendUsd ?: 0.0
        EmiCalculatorEngine.calculateFire(
            monthlyExpensesUsd = monthlyExp,
            safeWithdrawalRatePct = 4.0,
            currentPortfolioValueUsd = currentVal,
            monthlySipUsd = sipMonthly,
            expectedAnnualReturnPct = annualReturn,
            estimatedAnnualDividendUsd = annualDiv
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = EmiCalculatorEngine.calculateFire(3500.0, 4.0, 0.0, 1000.0, 12.0, 0.0)
    )

    // --- UI ACTIONS ---

    fun selectCurrency(currency: AppCurrency) {
        _selectedCurrency.value = currency
        _statusBannerMessage.value = "Switched display currency to ${currency.flagEmoji} ${currency.code} (${currency.symbol})"
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectSectorFilter(sector: String) {
        _selectedSectorFilter.value = sector
    }

    fun selectStock(symbol: String) {
        _selectedSymbol.value = symbol.uppercase()
        refreshSelectedStockChart()
    }

    fun selectTimeframe(timeframe: ChartTimeframe) {
        _selectedTimeframe.value = timeframe
        refreshSelectedStockChart()
    }

    private fun refreshSelectedStockChart() {
        val sym = _selectedSymbol.value
        val tf = _selectedTimeframe.value
        viewModelScope.launch {
            marketRepository.fetchLiveQuote(sym, tf)
        }
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun addOrUpdatePortfolioPosition(
        symbol: String,
        companyName: String,
        shares: Double,
        avgBuyPrice: Double,
        sector: String,
        notes: String = ""
    ) {
        if (shares <= 0.0 || avgBuyPrice <= 0.0) {
            _statusBannerMessage.value = "Shares and buy price must be greater than zero."
            return
        }
        viewModelScope.launch {
            val result = portfolioRepository.addHolding(
                symbol = symbol,
                companyName = companyName,
                shares = shares,
                avgBuyPrice = avgBuyPrice,
                sector = sector,
                notes = notes
            )
            result.onSuccess {
                _statusBannerMessage.value = "Added $shares shares of ${symbol.uppercase()} to your Cloud Portfolio."
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Failed to add holding"
            }
        }
    }

    fun updateExistingHolding(
        holdingId: String,
        shares: Double,
        avgBuyPrice: Double,
        notes: String
    ) {
        viewModelScope.launch {
            val result = portfolioRepository.updateHolding(holdingId, shares, avgBuyPrice, notes)
            result.onSuccess {
                _statusBannerMessage.value = "Updated position in Cloud Portfolio."
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Failed to update holding"
            }
        }
    }

    fun deleteHolding(holdingId: String, symbol: String) {
        viewModelScope.launch {
            val result = portfolioRepository.deleteHolding(holdingId)
            result.onSuccess {
                _statusBannerMessage.value = "Removed $symbol from Cloud Portfolio."
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Failed to remove holding"
            }
        }
    }

    fun seedStarterPortfolio() {
        viewModelScope.launch {
            val starters = listOf(
                PortfolioHolding(symbol = "NVDA", companyName = "NVIDIA Corporation", shares = 25.0, avgBuyPrice = 118.50, sector = "Semiconductors", notes = "AI Data Center Leader"),
                PortfolioHolding(symbol = "AAPL", companyName = "Apple Inc.", shares = 30.0, avgBuyPrice = 198.00, sector = "Technology", notes = "Ecosystem Core"),
                PortfolioHolding(symbol = "MSFT", companyName = "Microsoft Corporation", shares = 12.0, avgBuyPrice = 395.00, sector = "Technology", notes = "Enterprise Cloud"),
                PortfolioHolding(symbol = "SPY", companyName = "SPDR S&P 500 ETF", shares = 15.0, avgBuyPrice = 535.00, sector = "Index ETF", notes = "Core Index Anchor"),
                PortfolioHolding(symbol = "TCS.NS", companyName = "Tata Consultancy Services", shares = 40.0, avgBuyPrice = 44.50, sector = "Global / India", notes = "Global IT Dividend Anchor")
            )
            starters.forEach { item ->
                portfolioRepository.addHolding(
                    symbol = item.symbol,
                    companyName = item.companyName,
                    shares = item.shares,
                    avgBuyPrice = item.avgBuyPrice,
                    sector = item.sector,
                    notes = item.notes
                )
            }
            _statusBannerMessage.value = "Global Blue-Chip Portfolio synced to Cloud Firestore."
        }
    }

    fun toggleWatchlist(quote: StockQuote, targetAlertPrice: Double = (quote.price * 1.05)) {
        viewModelScope.launch {
            val currentList = (watchlistState.value as? UiState.Success)?.data ?: emptyList()
            val existing = currentList.find { it.symbol.equals(quote.symbol, ignoreCase = true) }
            if (existing != null) {
                portfolioRepository.removeWatchlistItem(existing.id)
                _statusBannerMessage.value = "Removed ${quote.symbol} from Watchlist."
            } else {
                portfolioRepository.addWatchlistItem(
                    symbol = quote.symbol,
                    companyName = quote.companyName,
                    targetAlertPrice = targetAlertPrice
                )
                val formattedTarget = _selectedCurrency.value.format(targetAlertPrice)
                _statusBannerMessage.value = "Added ${quote.symbol} to Watchlist (Alert @ $formattedTarget)."
            }
        }
    }

    // --- EMI ACTIONS ---

    fun selectEmiPreset(loanType: String) {
        _emiLoanType.value = loanType
        when (loanType) {
            "Home Loan" -> {
                _emiPrincipal.value = 400000.0
                _emiAnnualRate.value = 6.5
                _emiTenureMonths.value = 300
                _emiMonthlyPrepayment.value = 300.0
            }
            "Auto Loan" -> {
                _emiPrincipal.value = 42000.0
                _emiAnnualRate.value = 7.2
                _emiTenureMonths.value = 60
                _emiMonthlyPrepayment.value = 100.0
            }
            "Personal Loan" -> {
                _emiPrincipal.value = 20000.0
                _emiAnnualRate.value = 11.5
                _emiTenureMonths.value = 36
                _emiMonthlyPrepayment.value = 50.0
            }
            "Education Loan" -> {
                _emiPrincipal.value = 65000.0
                _emiAnnualRate.value = 5.9
                _emiTenureMonths.value = 120
                _emiMonthlyPrepayment.value = 150.0
            }
            "Investment Loan" -> {
                _emiPrincipal.value = 100000.0
                _emiAnnualRate.value = 8.25
                _emiTenureMonths.value = 84
                _emiMonthlyPrepayment.value = 200.0
            }
        }
    }

    fun updateEmiPrincipal(amount: Double) {
        _emiPrincipal.value = amount.coerceIn(1000.0, 10_000_000.0)
    }

    fun updateEmiAnnualRate(rate: Double) {
        _emiAnnualRate.value = rate.coerceIn(0.5, 36.0)
    }

    fun updateEmiTenureMonths(months: Int) {
        _emiTenureMonths.value = months.coerceIn(6, 480)
    }

    fun updateEmiMonthlyPrepayment(prepayment: Double) {
        _emiMonthlyPrepayment.value = prepayment.coerceIn(0.0, 100_000.0)
    }

    fun saveCurrentEmiPlan(customTitle: String) {
        val calc = emiCalculation.value
        val type = _emiLoanType.value
        val curr = _selectedCurrency.value
        viewModelScope.launch {
            val result = portfolioRepository.saveEmiPlan(
                title = customTitle.ifBlank { "$type (${curr.format(calc.principal, 0)})" },
                loanType = type,
                principal = calc.principal,
                annualRate = calc.annualRate,
                tenureMonths = calc.tenureMonths,
                monthlyPrepayment = calc.monthlyPrepayment,
                monthlyEmi = calc.monthlyEmi,
                totalInterest = calc.totalInterestWithPrepay
            )
            result.onSuccess {
                _statusBannerMessage.value = "Saved EMI Plan to Cloud Firestore."
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Failed to save EMI plan"
            }
        }
    }

    fun loadSavedEmiPlan(plan: EmiPlan) {
        _emiLoanType.value = plan.loanType
        _emiPrincipal.value = plan.principal
        _emiAnnualRate.value = plan.annualRate
        _emiTenureMonths.value = plan.tenureMonths.toInt()
        _emiMonthlyPrepayment.value = plan.monthlyPrepayment
        _statusBannerMessage.value = "Loaded '${plan.title}' into EMI Lab."
    }

    fun deleteSavedEmiPlan(planId: String) {
        viewModelScope.launch {
            portfolioRepository.deleteEmiPlan(planId)
            _statusBannerMessage.value = "Deleted saved EMI plan."
        }
    }

    // --- SIP & FIRE ACTIONS ---

    fun updateSipMonthlyUsd(amountUsd: Double) {
        _sipMonthlyUsd.value = amountUsd.coerceIn(50.0, 50_000.0)
    }

    fun updateSipInitialLumpSumUsd(amountUsd: Double) {
        _sipInitialLumpSumUsd.value = amountUsd.coerceIn(0.0, 500_000.0)
    }

    fun updateSipAnnualReturnPct(ratePct: Double) {
        _sipAnnualReturnPct.value = ratePct.coerceIn(2.0, 30.0)
    }

    fun updateSipStepUpPct(stepUpPct: Double) {
        _sipStepUpPct.value = stepUpPct.coerceIn(0.0, 25.0)
    }

    fun updateSipDurationYears(years: Int) {
        _sipDurationYears.value = years.coerceIn(1, 45)
    }

    fun updateFireMonthlyExpensesUsd(expensesUsd: Double) {
        _fireMonthlyExpensesUsd.value = expensesUsd.coerceIn(200.0, 50_000.0)
    }

    private companion object {
        const val TAG = "PulseFolioVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
