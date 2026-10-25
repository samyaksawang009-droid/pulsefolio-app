package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppCurrency
import com.example.data.model.StockQuote
import com.example.data.model.WatchlistItem
import com.example.ui.components.AllocationDonutChart
import com.example.ui.components.AllocationSlice
import com.example.ui.components.SectorPalette
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BearishRedSoft
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenSoft
import com.example.ui.viewmodel.EnrichedHolding
import com.example.ui.viewmodel.PortfolioSummary
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun PortfolioScreen(
    currency: AppCurrency,
    portfolioState: UiState<PortfolioSummary>,
    watchlistState: UiState<List<WatchlistItem>>,
    liveQuotes: List<StockQuote>,
    onAddCustomPosition: (symbol: String, companyName: String, shares: Double, avgBuyPrice: Double, sector: String, notes: String) -> Unit,
    onUpdateHolding: (holdingId: String, shares: Double, avgBuyPrice: Double, notes: String) -> Unit,
    onDeleteHolding: (holdingId: String, symbol: String) -> Unit,
    onSeedStarterPortfolio: () -> Unit,
    onRemoveWatchlistItem: (StockQuote) -> Unit
) {
    var showAddPositionModal by remember { mutableStateOf(false) }
    var editingHolding by remember { mutableStateOf<EnrichedHolding?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("portfolio_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (portfolioState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = portfolioState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val summary = portfolioState.data

                item {
                    PortfolioHeroSummaryCard(
                        currency = currency,
                        summary = summary,
                        onAddPositionClicked = { showAddPositionModal = true }
                    )
                }

                if (summary.holdings.isEmpty()) {
                    item {
                        EmptyPortfolioStateCard(
                            onSeedStarter = onSeedStarterPortfolio,
                            onAddCustom = { showAddPositionModal = true }
                        )
                    }
                } else {
                    item {
                        val sectorGroups = summary.holdings
                            .groupBy { it.holding.sector }
                            .entries
                            .mapIndexed { idx, entry ->
                                AllocationSlice(
                                    label = entry.key,
                                    amount = entry.value.sumOf { it.marketValue },
                                    color = SectorPalette[idx % SectorPalette.size]
                                )
                            }
                            .sortedByDescending { it.amount }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sector & Asset Allocation",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = BullishGreenSoft,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Score: ${summary.healthMetrics.diversificationScore}/100",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BullishGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                AllocationDonutChart(
                                    slices = sectorGroups,
                                    centerTopLabel = "Positions",
                                    centerValueLabel = "${summary.holdings.size} Assets"
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cloud Portfolio Positions (${summary.holdings.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = { showAddPositionModal = true },
                                modifier = Modifier.testTag("portfolio_header_add_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.add_holding_button))
                            }
                        }
                    }

                    items(summary.holdings, key = { it.holding.id }) { enriched ->
                        HoldingPositionCard(
                            currency = currency,
                            enriched = enriched,
                            onEdit = { editingHolding = enriched },
                            onDelete = { onDeleteHolding(enriched.holding.id, enriched.holding.symbol) }
                        )
                    }
                }
            }
        }

        val watchedItems = (watchlistState as? UiState.Success)?.data ?: emptyList()
        if (watchedItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cloud Watchlist & Target Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(watchedItems, key = { it.id }) { item ->
                val quote = liveQuotes.find { it.symbol.equals(item.symbol, ignoreCase = true) }
                WatchlistAlertCard(
                    currency = currency,
                    item = item,
                    liveQuote = quote,
                    onRemove = {
                        if (quote != null) {
                            onRemoveWatchlistItem(quote)
                        }
                    }
                )
            }
        }
    }

    if (showAddPositionModal) {
        CustomAddPositionDialog(
            currency = currency,
            liveQuotes = liveQuotes,
            onDismiss = { showAddPositionModal = false },
            onConfirm = { sym, name, shares, priceUsd, sector, notes ->
                onAddCustomPosition(sym, name, shares, priceUsd, sector, notes)
                showAddPositionModal = false
            }
        )
    }

    val currentEdit = editingHolding
    if (currentEdit != null) {
        EditPositionDialog(
            currency = currency,
            enriched = currentEdit,
            onDismiss = { editingHolding = null },
            onConfirm = { newShares, newAvgPriceUsd, newNotes ->
                onUpdateHolding(currentEdit.holding.id, newShares, newAvgPriceUsd, newNotes)
                editingHolding = null
            }
        )
    }
}

@Composable
private fun PortfolioHeroSummaryCard(
    currency: AppCurrency,
    summary: PortfolioSummary,
    onAddPositionClicked: () -> Unit
) {
    val isTotalPos = summary.totalUnrealizedPnL >= 0
    val isDayPos = summary.totalDayPnL >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_summary_card"),
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0A192F),
                            Color(0xFF064E3B),
                            Color(0xFF112240)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NET PORTFOLIO VALUE (${currency.code})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8)
                    )
                    FilledTonalButton(
                        onClick = onAddPositionClicked,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("hero_add_position_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Asset", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currency.format(summary.totalMarketValue),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TOTAL UNREALIZED P&L",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currency.formatSigned(summary.totalUnrealizedPnL)} (${String.format(Locale.US, "%+.2f%%", summary.totalUnrealizedPnLPercent)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isTotalPos) Color(0xFF34D399) else Color(0xFFFB7185)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TODAY'S P&L",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currency.formatSigned(summary.totalDayPnL)} (${String.format(Locale.US, "%+.2f%%", summary.totalDayPnLPercent)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isDayPos) Color(0xFF34D399) else Color(0xFFFB7185)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Invested Principal Cost: ${currency.format(summary.totalInvestedCost)} • Est. Dividends: ${currency.format(summary.healthMetrics.estimatedAnnualDividendUsd)}/yr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
private fun EmptyPortfolioStateCard(
    onSeedStarter: () -> Unit,
    onAddCustom: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_portfolio_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PieChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.empty_portfolio_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empty_portfolio_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onSeedStarter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("seed_starter_portfolio_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.seed_starter_portfolio))
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAddCustom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_add_custom_position_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Custom Stock Position")
            }
        }
    }
}

@Composable
private fun HoldingPositionCard(
    currency: AppCurrency,
    enriched: EnrichedHolding,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val h = enriched.holding
    val isPos = enriched.totalPnL >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("holding_card_${h.symbol}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = h.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = h.sector,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = h.companyName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currency.format(enriched.marketValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = if (isPos) BullishGreenSoft else BearishRedSoft,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${currency.formatSigned(enriched.totalPnL)} (${String.format(Locale.US, "%+.2f%%", enriched.totalPnLPercent)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPos) BullishGreen else BearishRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = String.format(Locale.US, "Shares: %.2f • Avg Cost: %s", h.shares, currency.format(h.avgBuyPrice)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "Live Price: %s (%+.2f%% today)", currency.format(enriched.livePrice), enriched.dayChangePercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (h.notes.isNotBlank()) {
                        Text(
                            text = "Note: ${h.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_holding_${h.symbol}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${h.symbol}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_holding_${h.symbol}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${h.symbol}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistAlertCard(
    currency: AppCurrency,
    item: WatchlistItem,
    liveQuote: StockQuote?,
    onRemove: () -> Unit
) {
    val currentPrice = liveQuote?.price ?: item.targetAlertPrice
    val alertTriggered = item.targetAlertPrice > 0 && currentPrice >= item.targetAlertPrice

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = if (alertTriggered) BullishGreen else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${item.symbol} • ${item.companyName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live: ${currency.format(currentPrice)} • Target Alert: ${currency.format(item.targetAlertPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove watchlist item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomAddPositionDialog(
    currency: AppCurrency,
    liveQuotes: List<StockQuote>,
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, companyName: String, shares: Double, avgBuyPriceUsd: Double, sector: String, notes: String) -> Unit
) {
    var symbol by remember { mutableStateOf("AAPL") }
    var shares by remember { mutableStateOf("10") }
    var avgPriceDisplay by remember(currency) {
        mutableStateOf(String.format(Locale.US, "%.2f", 225.00 * currency.rateFromUsd))
    }
    var sector by remember { mutableStateOf("Technology") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Position to Cloud Portfolio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { input ->
                        symbol = input.uppercase()
                        val match = liveQuotes.find { it.symbol.equals(input.trim(), ignoreCase = true) }
                        if (match != null) {
                            avgPriceDisplay = String.format(Locale.US, "%.2f", match.price * currency.rateFromUsd)
                            sector = match.sector
                        }
                    },
                    label = { Text("Ticker Symbol (e.g. AAPL, NVDA, TCS.NS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_add_symbol_input")
                )
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it },
                    label = { Text("Number of Shares") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_add_shares_input")
                )
                OutlinedTextField(
                    value = avgPriceDisplay,
                    onValueChange = { avgPriceDisplay = it },
                    label = { Text("Average Buy Price (${currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_add_price_input")
                )
                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it },
                    label = { Text("Sector (e.g. Technology, Global / India)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanSym = symbol.trim().uppercase()
                    val sh = shares.toDoubleOrNull() ?: 0.0
                    val prInCurrency = avgPriceDisplay.toDoubleOrNull() ?: 0.0
                    val prUsd = prInCurrency / currency.rateFromUsd
                    val matchedQuote = liveQuotes.find { it.symbol == cleanSym }
                    if (cleanSym.isNotEmpty() && sh > 0 && prUsd > 0) {
                        onConfirm(
                            cleanSym,
                            matchedQuote?.companyName ?: "$cleanSym Holdings",
                            sh,
                            prUsd,
                            sector.ifBlank { "Technology" },
                            notes
                        )
                    }
                },
                modifier = Modifier.testTag("custom_add_confirm_button")
            ) {
                Text("Add Position")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditPositionDialog(
    currency: AppCurrency,
    enriched: EnrichedHolding,
    onDismiss: () -> Unit,
    onConfirm: (shares: Double, avgBuyPriceUsd: Double, notes: String) -> Unit
) {
    var sharesText by remember { mutableStateOf(enriched.holding.shares.toString()) }
    var priceDisplayText by remember(currency, enriched) {
        mutableStateOf(String.format(Locale.US, "%.2f", enriched.holding.avgBuyPrice * currency.rateFromUsd))
    }
    var notesText by remember { mutableStateOf(enriched.holding.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update ${enriched.holding.symbol} Position") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sharesText,
                    onValueChange = { sharesText = it },
                    label = { Text("Total Shares") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceDisplayText,
                    onValueChange = { priceDisplayText = it },
                    label = { Text("Average Buy Price (${currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sh = sharesText.toDoubleOrNull() ?: enriched.holding.shares
                    val prInCurrency = priceDisplayText.toDoubleOrNull() ?: (enriched.holding.avgBuyPrice * currency.rateFromUsd)
                    val prUsd = prInCurrency / currency.rateFromUsd
                    if (sh > 0 && prUsd > 0) {
                        onConfirm(sh, prUsd, notesText)
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
