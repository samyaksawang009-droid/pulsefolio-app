package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

enum class AppCurrency(
    val code: String,
    val symbol: String,
    val rateFromUsd: Double,
    val flagEmoji: String
) {
    USD("USD", "$", 1.0, "🇺🇸"),
    INR("INR", "₹", 84.10, "🇮🇳"),
    EUR("EUR", "€", 0.92, "🇪🇺"),
    GBP("GBP", "£", 0.77, "🇬🇧"),
    AED("AED", "AED ", 3.67, "🇦🇪"),
    JPY("JPY", "¥", 152.40, "🇯🇵");

    fun format(amountUsd: Double, decimals: Int = 2): String {
        val converted = amountUsd * rateFromUsd
        val effectiveDecimals = if (this == JPY && decimals > 0) 0 else decimals
        return String.format(Locale.US, "%s%,.${effectiveDecimals}f", symbol, converted)
    }

    fun formatSigned(amountUsd: Double, decimals: Int = 2): String {
        val converted = amountUsd * rateFromUsd
        val sign = if (converted >= 0) "+" else "-"
        val effectiveDecimals = if (this == JPY && decimals > 0) 0 else decimals
        return String.format(Locale.US, "%s%s%,.${effectiveDecimals}f", sign, symbol, kotlin.math.abs(converted))
    }
}

@IgnoreExtraProperties
data class PortfolioHolding(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val companyName: String = "",
    val shares: Double = 0.0,
    val avgBuyPrice: Double = 0.0,
    val sector: String = "Technology",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WatchlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val companyName: String = "",
    val targetAlertPrice: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EmiPlan(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val loanType: String = "Home Loan",
    val principal: Double = 0.0,
    val annualRate: Double = 0.0,
    val tenureMonths: Long = 12L,
    val monthlyPrepayment: Double = 0.0,
    val monthlyEmi: Double = 0.0,
    val totalInterest: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class StockQuote(
    val symbol: String,
    val companyName: String,
    val sector: String,
    val price: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val openPrice: Double,
    val volume: Long,
    val marketCapB: Double,
    val peRatio: Double,
    val dividendYieldPct: Double = 0.8,
    val sparkline: List<Double>,
    val isLiveNetwork: Boolean = false,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

enum class ChartTimeframe(val label: String, val rangeParam: String, val intervalParam: String) {
    ONE_DAY("1D", "1d", "5m"),
    ONE_WEEK("1W", "5d", "15m"),
    ONE_MONTH("1M", "1mo", "1d"),
    THREE_MONTHS("3M", "3mo", "1d"),
    ONE_YEAR("1Y", "1y", "1wk")
}

data class AmortizationRow(
    val month: Int,
    val year: Int,
    val payment: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val extraPrepayment: Double,
    val endingBalance: Double,
    val cumulativeInterest: Double,
    val cumulativePrincipal: Double
)

data class EmiCalculationResult(
    val principal: Double,
    val annualRate: Double,
    val tenureMonths: Int,
    val monthlyPrepayment: Double,
    val monthlyEmi: Double,
    val totalInterestWithoutPrepay: Double,
    val totalInterestWithPrepay: Double,
    val totalPaymentWithPrepay: Double,
    val interestSaved: Double,
    val monthsSaved: Int,
    val actualMonthsToPayOff: Int,
    val schedule: List<AmortizationRow>
)

data class SipYearPoint(
    val year: Int,
    val totalInvested: Double,
    val totalCorpus: Double,
    val wealthGained: Double
)

data class SipCalculationResult(
    val monthlyInvestment: Double,
    val initialLumpSum: Double,
    val expectedAnnualReturnPct: Double,
    val annualStepUpPct: Double,
    val durationYears: Int,
    val totalInvested: Double,
    val totalWealthGained: Double,
    val maturityCorpus: Double,
    val wealthMultiplier: Double,
    val yearlyPoints: List<SipYearPoint>
)

data class FireCalculationResult(
    val monthlyExpenses: Double,
    val safeWithdrawalRatePct: Double,
    val targetFireCorpus: Double,
    val currentPortfolioValue: Double,
    val progressPercent: Double,
    val estimatedYearsToFire: Double,
    val projectedMonthlyPassiveIncome: Double,
    val estimatedAnnualDividendUsd: Double
)

data class PortfolioHealthMetrics(
    val diversificationScore: Int,
    val riskGrade: String,
    val topHoldingSymbol: String,
    val topHoldingWeightPct: Double,
    val sectorCount: Int,
    val estimatedAnnualDividendUsd: Double,
    val averageYieldPct: Double,
    val insightTip: String
)
