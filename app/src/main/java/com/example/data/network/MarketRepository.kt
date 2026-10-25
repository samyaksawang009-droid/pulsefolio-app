package com.example.data.network

import com.example.data.model.ChartTimeframe
import com.example.data.model.StockQuote
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

data class YahooChartResponse(
    @param:Json(name = "chart") val chart: YahooChartContainer?
)

data class YahooChartContainer(
    @param:Json(name = "result") val result: List<YahooChartResult>?
)

data class YahooChartResult(
    @param:Json(name = "meta") val meta: YahooChartMeta?,
    @param:Json(name = "indicators") val indicators: YahooIndicators?
)

data class YahooChartMeta(
    @param:Json(name = "symbol") val symbol: String?,
    @param:Json(name = "shortName") val shortName: String?,
    @param:Json(name = "longName") val longName: String?,
    @param:Json(name = "regularMarketPrice") val regularMarketPrice: Double?,
    @param:Json(name = "chartPreviousClose") val chartPreviousClose: Double?,
    @param:Json(name = "previousClose") val previousClose: Double?,
    @param:Json(name = "regularMarketDayHigh") val regularMarketDayHigh: Double?,
    @param:Json(name = "regularMarketDayLow") val regularMarketDayLow: Double?,
    @param:Json(name = "regularMarketVolume") val regularMarketVolume: Long?
)

data class YahooIndicators(
    @param:Json(name = "quote") val quote: List<YahooQuoteIndicator>?
)

data class YahooQuoteIndicator(
    @param:Json(name = "close") val close: List<Double?>?,
    @param:Json(name = "open") val open: List<Double?>?,
    @param:Json(name = "high") val high: List<Double?>?,
    @param:Json(name = "low") val low: List<Double?>?
)

interface YahooFinanceApi {
    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        "Accept: application/json"
    )
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "1d",
        @Query("interval") interval: String = "5m"
    ): YahooChartResponse
}

private data class StockMeta(
    val companyName: String,
    val sector: String,
    val marketCapB: Double,
    val peRatio: Double,
    val dividendYieldPct: Double
)

class MarketRepository {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val api: YahooFinanceApi = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YahooFinanceApi::class.java)

    private val catalogMetadata = mapOf(
        "AAPL" to StockMeta("Apple Inc.", "Technology", 3420.0, 31.4, 0.52),
        "NVDA" to StockMeta("NVIDIA Corporation", "Semiconductors", 3290.0, 52.8, 0.04),
        "MSFT" to StockMeta("Microsoft Corporation", "Technology", 3180.0, 35.6, 0.72),
        "GOOGL" to StockMeta("Alphabet Inc.", "Technology", 2240.0, 24.9, 0.45),
        "AMZN" to StockMeta("Amazon.com Inc.", "Consumer", 2110.0, 41.2, 0.0),
        "META" to StockMeta("Meta Platforms Inc.", "Communication", 1480.0, 27.5, 0.38),
        "TSLA" to StockMeta("Tesla Inc.", "Automotive", 785.0, 62.1, 0.0),
        "BRK-B" to StockMeta("Berkshire Hathaway", "Financials", 980.0, 21.3, 0.0),
        "JPM" to StockMeta("JPMorgan Chase & Co.", "Financials", 645.0, 12.6, 2.15),
        "V" to StockMeta("Visa Inc.", "Financials", 590.0, 30.1, 0.78),
        "LLY" to StockMeta("Eli Lilly & Co.", "Healthcare", 840.0, 58.4, 0.65),
        "SPY" to StockMeta("SPDR S&P 500 ETF", "Index ETF", 610.0, 26.8, 1.32),
        "QQQ" to StockMeta("Invesco QQQ Trust", "Index ETF", 310.0, 33.4, 0.60),
        "BTC-USD" to StockMeta("Bitcoin USD", "Crypto", 1340.0, 0.0, 0.0),
        "RELIANCE.NS" to StockMeta("Reliance Industries (NSE)", "Global / India", 245.0, 26.4, 0.42),
        "TCS.NS" to StockMeta("Tata Consultancy Services", "Global / India", 182.0, 29.8, 1.45),
        "HDFCBANK.NS" to StockMeta("HDFC Bank Ltd (NSE)", "Global / India", 158.0, 19.2, 1.15),
        "INFY.NS" to StockMeta("Infosys Limited (NSE)", "Global / India", 92.0, 27.1, 2.10)
    )

    // Baseline prices in USD equivalent so portfolio aggregation is unified across currencies
    private val baselinePrices = mutableMapOf(
        "AAPL" to 228.45,
        "NVDA" to 138.60,
        "MSFT" to 428.90,
        "GOOGL" to 184.20,
        "AMZN" to 210.35,
        "META" to 592.40,
        "TSLA" to 254.80,
        "BRK-B" to 456.10,
        "JPM" to 232.75,
        "V" to 294.50,
        "LLY" to 896.20,
        "SPY" to 586.40,
        "QQQ" to 504.80,
        "BTC-USD" to 68450.00,
        "RELIANCE.NS" to 34.80,
        "TCS.NS" to 48.90,
        "HDFCBANK.NS" to 20.60,
        "INFY.NS" to 22.40
    )

    private val currentQuotes = mutableMapOf<String, StockQuote>()

    init {
        baselinePrices.forEach { (sym, basePrice) ->
            currentQuotes[sym] = buildInitialQuote(sym, basePrice)
        }
    }

    private fun buildInitialQuote(symbol: String, basePrice: Double): StockQuote {
        val meta = catalogMetadata[symbol] ?: StockMeta(symbol, "Equities", 120.0, 22.0, 1.1)
        val seed = symbol.hashCode()
        val rand = Random(seed)
        val driftPct = rand.nextDouble(-2.4, 3.2)
        val prevClose = (basePrice / (1.0 + driftPct / 100.0)).round2()
        val change = (basePrice - prevClose).round2()
        val changePct = ((change / prevClose) * 100.0).round2()

        val points = mutableListOf<Double>()
        var cursor = prevClose
        for (i in 0 until 28) {
            val step = (basePrice - cursor) / (28 - i) + rand.nextDouble(-basePrice * 0.004, basePrice * 0.004)
            cursor = (cursor + step).coerceAtLeast(1.0).round2()
            points.add(cursor)
        }
        points[points.lastIndex] = basePrice

        val high = (points.maxOrNull() ?: basePrice).coerceAtLeast(basePrice * 1.006).round2()
        val low = (points.minOrNull() ?: basePrice).coerceAtMost(basePrice * 0.994).round2()
        val open = points.firstOrNull() ?: prevClose

        return StockQuote(
            symbol = symbol,
            companyName = meta.companyName,
            sector = meta.sector,
            price = basePrice,
            previousClose = prevClose,
            change = change,
            changePercent = changePct,
            dayHigh = high,
            dayLow = low,
            openPrice = open,
            volume = abs(seed.toLong() % 45_000_000L) + 12_500_000L,
            marketCapB = meta.marketCapB,
            peRatio = meta.peRatio,
            dividendYieldPct = meta.dividendYieldPct,
            sparkline = points,
            isLiveNetwork = false
        )
    }

    suspend fun fetchLiveQuote(
        symbol: String,
        timeframe: ChartTimeframe = ChartTimeframe.ONE_DAY
    ): StockQuote = withContext(Dispatchers.IO) {
        val cleanSymbol = symbol.trim().uppercase()
        val existing = currentQuotes[cleanSymbol]
            ?: buildInitialQuote(cleanSymbol, baselinePrices[cleanSymbol] ?: 150.0).also {
                currentQuotes[cleanSymbol] = it
            }

        try {
            val response = api.getStockChart(
                symbol = cleanSymbol,
                range = timeframe.rangeParam,
                interval = timeframe.intervalParam
            )
            val result = response.chart?.result?.firstOrNull()
            val meta = result?.meta
            val closes = result?.indicators?.quote?.firstOrNull()?.close?.filterNotNull() ?: emptyList()

            if (meta?.regularMarketPrice != null && meta.regularMarketPrice > 0) {
                // Convert NSE INR prices to base USD if ticker ends with .NS so multi-currency converter works uniformly
                val fxFactor = if (cleanSymbol.endsWith(".NS") || cleanSymbol.endsWith(".BO")) (1.0 / 84.10) else 1.0
                val price = (meta.regularMarketPrice * fxFactor).round2()
                val rawPrev = meta.chartPreviousClose ?: meta.previousClose
                val prevClose = if (rawPrev != null && rawPrev > 0) (rawPrev * fxFactor).round2() else existing.previousClose
                val change = (price - prevClose).round2()
                val changePct = if (prevClose > 0) ((change / prevClose) * 100.0).round2() else 0.0
                val spark = if (closes.size >= 4) closes.takeLast(40).map { (it * fxFactor).round2() } else existing.sparkline
                val catalogMeta = catalogMetadata[cleanSymbol]
                val updated = existing.copy(
                    companyName = catalogMeta?.companyName ?: meta.longName ?: meta.shortName ?: existing.companyName,
                    price = price,
                    previousClose = prevClose,
                    change = change,
                    changePercent = changePct,
                    dayHigh = ((meta.regularMarketDayHigh?.let { it * fxFactor }) ?: spark.maxOrNull() ?: price).round2(),
                    dayLow = ((meta.regularMarketDayLow?.let { it * fxFactor }) ?: spark.minOrNull() ?: price).round2(),
                    openPrice = (spark.firstOrNull() ?: prevClose).round2(),
                    volume = meta.regularMarketVolume ?: existing.volume,
                    sparkline = spark,
                    isLiveNetwork = true,
                    lastUpdatedMs = System.currentTimeMillis()
                )
                currentQuotes[cleanSymbol] = updated
                return@withContext updated
            }
        } catch (_: Exception) {
            // Fall through to real-time tick update if offline or throttled
        }

        val updated = applyMicroTick(existing, timeframe)
        currentQuotes[cleanSymbol] = updated
        updated
    }

    private fun applyMicroTick(quote: StockQuote, timeframe: ChartTimeframe): StockQuote {
        val deltaFactor = Random.nextDouble(-0.0022, 0.0024)
        val newPrice = (quote.price * (1.0 + deltaFactor)).coerceAtLeast(1.0).round2()
        val change = (newPrice - quote.previousClose).round2()
        val changePct = if (quote.previousClose > 0) ((change / quote.previousClose) * 100.0).round2() else 0.0

        val updatedSpark = when (timeframe) {
            ChartTimeframe.ONE_DAY -> (quote.sparkline.drop(1) + newPrice)
            ChartTimeframe.ONE_WEEK -> buildTimeframeCurve(newPrice, 32, 0.025, quote.symbol.hashCode() + 1)
            ChartTimeframe.ONE_MONTH -> buildTimeframeCurve(newPrice, 30, 0.055, quote.symbol.hashCode() + 2)
            ChartTimeframe.THREE_MONTHS -> buildTimeframeCurve(newPrice, 36, 0.11, quote.symbol.hashCode() + 3)
            ChartTimeframe.ONE_YEAR -> buildTimeframeCurve(newPrice, 48, 0.24, quote.symbol.hashCode() + 4)
        }

        return quote.copy(
            price = newPrice,
            change = change,
            changePercent = changePct,
            dayHigh = maxOf(quote.dayHigh, newPrice),
            dayLow = minOf(quote.dayLow, newPrice),
            volume = quote.volume + Random.nextLong(1_200L, 28_000L),
            sparkline = updatedSpark,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    private fun buildTimeframeCurve(currentPrice: Double, pointsCount: Int, volatility: Double, seed: Int): List<Double> {
        val r = Random(seed)
        val startPrice = (currentPrice * (1.0 - r.nextDouble(-volatility * 0.5, volatility))).coerceAtLeast(1.0)
        val list = mutableListOf<Double>()
        var cursor = startPrice
        for (i in 0 until pointsCount) {
            val pull = (currentPrice - cursor) / (pointsCount - i)
            val noise = r.nextDouble(-currentPrice * (volatility / 12.0), currentPrice * (volatility / 12.0))
            cursor = (cursor + pull + noise).coerceAtLeast(1.0).round2()
            list.add(cursor)
        }
        list[list.lastIndex] = currentPrice
        return list
    }

    fun streamRealTimeQuotes(extraSymbols: Set<String> = emptySet()): Flow<List<StockQuote>> = flow {
        val allSymbols = (baselinePrices.keys + extraSymbols.map { it.uppercase() }).distinct()
        allSymbols.forEach { sym ->
            if (!currentQuotes.containsKey(sym)) {
                currentQuotes[sym] = buildInitialQuote(sym, 125.0)
            }
        }
        emit(allSymbols.mapNotNull { currentQuotes[it] })

        var cycle = 0
        while (true) {
            val symbolsToRefresh = (baselinePrices.keys + extraSymbols.map { it.uppercase() }).distinct()
            if (cycle % 4 == 0) {
                coroutineScope {
                    symbolsToRefresh.take(8).map { sym ->
                        async { fetchLiveQuote(sym, ChartTimeframe.ONE_DAY) }
                    }.awaitAll()
                }
            } else {
                symbolsToRefresh.forEach { sym ->
                    val existing = currentQuotes[sym] ?: buildInitialQuote(sym, 125.0)
                    currentQuotes[sym] = applyMicroTick(existing, ChartTimeframe.ONE_DAY)
                }
            }
            emit(symbolsToRefresh.mapNotNull { currentQuotes[it] })
            cycle++
            delay(3500L)
        }
    }

    fun getCachedQuotes(): List<StockQuote> = currentQuotes.values.toList()

    private fun Double.round2(): Double = (this * 100.0).roundToInt() / 100.0
}
