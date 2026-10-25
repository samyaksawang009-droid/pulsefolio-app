package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppCurrency
import com.example.data.model.ChartTimeframe
import com.example.data.model.StockQuote
import com.example.data.model.WatchlistItem
import com.example.ui.components.InteractiveStockChart
import com.example.ui.components.MiniSparkline
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BearishRedSoft
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenSoft
import java.util.Locale

@Composable
fun MarketsScreen(
    currency: AppCurrency,
    allQuotes: List<StockQuote>,
    filteredQuotes: List<StockQuote>,
    selectedQuote: StockQuote?,
    selectedTimeframe: ChartTimeframe,
    searchQuery: String,
    selectedSector: String,
    watchlist: List<WatchlistItem>,
    onSearchQueryChange: (String) -> Unit,
    onSectorSelected: (String) -> Unit,
    onStockSelected: (String) -> Unit,
    onTimeframeSelected: (ChartTimeframe) -> Unit,
    onAddHolding: (symbol: String, companyName: String, shares: Double, avgBuyPrice: Double, sector: String, notes: String) -> Unit,
    onToggleWatchlist: (StockQuote) -> Unit
) {
    var showBuyModalForQuote by remember { mutableStateOf<StockQuote?>(null) }
    val sectors = remember {
        listOf("All", "Global / India", "Technology", "Semiconductors", "Financials", "Consumer", "Index ETF", "Crypto", "Healthcare")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("markets_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Index Bar (SPY, QQQ, BTC-USD, RELIANCE.NS, NVDA)
        item {
            MarketIndexRibbon(
                currency = currency,
                quotes = allQuotes.filter { it.symbol in listOf("SPY", "QQQ", "BTC-USD", "RELIANCE.NS", "NVDA") },
                onSelectSymbol = onStockSelected
            )
        }

        // 2. Featured Interactive Stock Chart & Detail Panel
        if (selectedQuote != null) {
            item {
                val isWatched = watchlist.any { it.symbol.equals(selectedQuote.symbol, ignoreCase = true) }
                SelectedStockChartCard(
                    currency = currency,
                    quote = selectedQuote,
                    selectedTimeframe = selectedTimeframe,
                    isWatched = isWatched,
                    onTimeframeSelected = onTimeframeSelected,
                    onOpenBuyDialog = { showBuyModalForQuote = selectedQuote },
                    onToggleWatchlist = { onToggleWatchlist(selectedQuote) }
                )
            }
        }

        // 3. Search & Sector Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_search_input"),
                    placeholder = { Text(stringResource(R.string.search_stocks_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search stocks"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sectors.forEach { sector ->
                        FilterChip(
                            selected = selectedSector == sector,
                            onClick = { onSectorSelected(sector) },
                            label = { Text(sector) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("sector_chip_${sector.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }
        }

        // 4. Live Market Rate Rows
        items(filteredQuotes, key = { it.symbol }) { quote ->
            val isSelected = selectedQuote?.symbol == quote.symbol
            StockQuoteRowCard(
                currency = currency,
                quote = quote,
                isSelected = isSelected,
                onClick = { onStockSelected(quote.symbol) },
                onQuickBuy = { showBuyModalForQuote = quote }
            )
        }
    }

    val quoteForModal = showBuyModalForQuote
    if (quoteForModal != null) {
        BuyPositionDialog(
            currency = currency,
            quote = quoteForModal,
            onDismiss = { showBuyModalForQuote = null },
            onConfirm = { shares, buyPriceUsd, notes ->
                onAddHolding(
                    quoteForModal.symbol,
                    quoteForModal.companyName,
                    shares,
                    buyPriceUsd,
                    quoteForModal.sector,
                    notes
                )
                showBuyModalForQuote = null
            }
        )
    }
}

@Composable
private fun MarketIndexRibbon(
    currency: AppCurrency,
    quotes: List<StockQuote>,
    onSelectSymbol: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        quotes.forEach { quote ->
            val isPos = quote.change >= 0
            Card(
                modifier = Modifier
                    .width(156.dp)
                    .clickable { onSelectSymbol(quote.symbol) }
                    .testTag("index_card_${quote.symbol}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = quote.symbol,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = if (isPos) BullishGreenSoft else BearishRedSoft,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%+.2f%%", quote.changePercent),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPos) BullishGreen else BearishRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currency.format(quote.price),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = quote.companyName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedStockChartCard(
    currency: AppCurrency,
    quote: StockQuote,
    selectedTimeframe: ChartTimeframe,
    isWatched: Boolean,
    onTimeframeSelected: (ChartTimeframe) -> Unit,
    onOpenBuyDialog: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val isPositive = quote.change >= 0
    var scrubbedPrice by remember(quote.symbol, selectedTimeframe) { mutableStateOf<Double?>(null) }
    val displayPrice = scrubbedPrice ?: quote.price

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_stock_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (quote.isLiveNetwork) "YAHOO FINANCE LIVE" else stringResource(R.string.market_live_feed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${quote.sector}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${quote.symbol} — ${quote.companyName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = currency.format(displayPrice),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            color = if (isPositive) BullishGreenSoft else BearishRedSoft,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isPositive) BullishGreen else BearishRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currency.formatSigned(quote.change)} (${String.format(Locale.US, "%+.2f%%", quote.changePercent)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isPositive) BullishGreen else BearishRed
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier.testTag("toggle_watchlist_button")
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Toggle Watchlist",
                        tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            InteractiveStockChart(
                points = quote.sparkline,
                isPositive = isPositive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .testTag("interactive_stock_canvas"),
                onScrubPrice = { scrubbedPrice = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartTimeframe.entries.forEach { tf ->
                    val selected = tf == selectedTimeframe
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onTimeframeSelected(tf) }
                            .testTag("timeframe_button_${tf.label}"),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = tf.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryStatItem("OPEN", currency.format(quote.openPrice))
                TelemetryStatItem("DAY HIGH", currency.format(quote.dayHigh))
                TelemetryStatItem("DAY LOW", currency.format(quote.dayLow))
                TelemetryStatItem("DIV YIELD", String.format(Locale.US, "%.2f%%", quote.dividendYieldPct))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenBuyDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_selected_stock_to_portfolio_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add ${quote.symbol} to Portfolio @ ${currency.format(quote.price)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StockQuoteRowCard(
    currency: AppCurrency,
    quote: StockQuote,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQuickBuy: () -> Unit
) {
    val isPos = quote.change >= 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("stock_row_${quote.symbol}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quote.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = quote.sector,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = quote.companyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MiniSparkline(
                points = quote.sparkline,
                isPositive = isPos,
                modifier = Modifier
                    .width(68.dp)
                    .height(34.dp)
                    .padding(horizontal = 4.dp)
            )

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1.05f)
            ) {
                Text(
                    text = currency.format(quote.price),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = if (isPos) BullishGreenSoft else BearishRedSoft,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%+.2f%%", quote.changePercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPos) BullishGreen else BearishRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onQuickBuy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("quick_buy_${quote.symbol}")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add ${quote.symbol}",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BuyPositionDialog(
    currency: AppCurrency,
    quote: StockQuote,
    onDismiss: () -> Unit,
    onConfirm: (shares: Double, avgBuyPriceUsd: Double, notes: String) -> Unit
) {
    var sharesText by remember { mutableStateOf("10") }
    var priceDisplayText by remember(currency, quote) {
        mutableStateOf(String.format(Locale.US, "%.2f", quote.price * currency.rateFromUsd))
    }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add ${quote.symbol} to Portfolio",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${quote.companyName} • Live Rate: ${currency.format(quote.price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = sharesText,
                    onValueChange = { sharesText = it },
                    label = { Text("Quantity (Shares)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("buy_dialog_shares_input")
                )
                OutlinedTextField(
                    value = priceDisplayText,
                    onValueChange = { priceDisplayText = it },
                    label = { Text("Buy Price per Share (${currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("buy_dialog_price_input")
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Investment Thesis / Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("buy_dialog_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sh = sharesText.toDoubleOrNull() ?: 0.0
                    val enteredInCurrency = priceDisplayText.toDoubleOrNull() ?: (quote.price * currency.rateFromUsd)
                    val baseUsdPrice = enteredInCurrency / currency.rateFromUsd
                    if (sh > 0 && baseUsdPrice > 0) {
                        onConfirm(sh, baseUsdPrice, notesText)
                    }
                },
                modifier = Modifier.testTag("buy_dialog_confirm_button")
            ) {
                Text("Save to Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
