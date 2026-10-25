package com.example.domain

import com.example.data.model.AmortizationRow
import com.example.data.model.EmiCalculationResult
import com.example.data.model.FireCalculationResult
import com.example.data.model.PortfolioHealthMetrics
import com.example.data.model.SipCalculationResult
import com.example.data.model.SipYearPoint
import com.example.data.model.StockQuote
import com.example.ui.viewmodel.EnrichedHolding
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object EmiCalculatorEngine {

    fun calculate(
        principal: Double,
        annualRatePercent: Double,
        tenureMonths: Int,
        monthlyPrepayment: Double = 0.0
    ): EmiCalculationResult {
        val safePrincipal = principal.coerceAtLeast(100.0)
        val safeTenure = tenureMonths.coerceIn(1, 600)
        val safeRate = annualRatePercent.coerceIn(0.1, 60.0)
        val safePrepay = monthlyPrepayment.coerceAtLeast(0.0)

        val monthlyRate = safeRate / 12.0 / 100.0
        val factor = (1.0 + monthlyRate).pow(safeTenure.toDouble())
        val baseEmi = if (monthlyRate > 0.0 && factor > 1.0) {
            safePrincipal * monthlyRate * factor / (factor - 1.0)
        } else {
            safePrincipal / safeTenure
        }

        val totalInterestWithoutPrepay = ((baseEmi * safeTenure) - safePrincipal).coerceAtLeast(0.0)

        val schedule = mutableListOf<AmortizationRow>()
        var balance = safePrincipal
        var cumulativeInterest = 0.0
        var cumulativePrincipal = 0.0
        var month = 0

        while (balance > 0.005 && month < safeTenure) {
            month++
            val interestForMonth = balance * monthlyRate
            val scheduledPrincipal = (baseEmi - interestForMonth).coerceAtLeast(0.0)
            val actualRegularPrincipal = minOf(balance, scheduledPrincipal)
            val remainingAfterRegular = (balance - actualRegularPrincipal).coerceAtLeast(0.0)
            val actualExtraPrepay = minOf(remainingAfterRegular, safePrepay)

            val totalPrincipalThisMonth = actualRegularPrincipal + actualExtraPrepay
            val totalPaymentThisMonth = interestForMonth + totalPrincipalThisMonth
            balance = (balance - totalPrincipalThisMonth).coerceAtLeast(0.0)

            cumulativeInterest += interestForMonth
            cumulativePrincipal += totalPrincipalThisMonth

            schedule.add(
                AmortizationRow(
                    month = month,
                    year = ((month - 1) / 12) + 1,
                    payment = totalPaymentThisMonth.round2(),
                    principalPaid = actualRegularPrincipal.round2(),
                    interestPaid = interestForMonth.round2(),
                    extraPrepayment = actualExtraPrepay.round2(),
                    endingBalance = balance.round2(),
                    cumulativeInterest = cumulativeInterest.round2(),
                    cumulativePrincipal = cumulativePrincipal.round2()
                )
            )
        }

        val interestSaved = (totalInterestWithoutPrepay - cumulativeInterest).coerceAtLeast(0.0)
        val monthsSaved = (safeTenure - month).coerceAtLeast(0)

        return EmiCalculationResult(
            principal = safePrincipal.round2(),
            annualRate = safeRate.round2(),
            tenureMonths = safeTenure,
            monthlyPrepayment = safePrepay.round2(),
            monthlyEmi = baseEmi.round2(),
            totalInterestWithoutPrepay = totalInterestWithoutPrepay.round2(),
            totalInterestWithPrepay = cumulativeInterest.round2(),
            totalPaymentWithPrepay = (safePrincipal + cumulativeInterest).round2(),
            interestSaved = interestSaved.round2(),
            monthsSaved = monthsSaved,
            actualMonthsToPayOff = month,
            schedule = schedule
        )
    }

    /**
     * Global SIP / Dollar-Cost Averaging & Step-Up Compounding Engine
     */
    fun calculateSip(
        monthlyInvestment: Double,
        initialLumpSum: Double,
        expectedAnnualReturnPct: Double,
        annualStepUpPct: Double,
        durationYears: Int
    ): SipCalculationResult {
        val safeMonthly = monthlyInvestment.coerceAtLeast(0.0)
        val safeLump = initialLumpSum.coerceAtLeast(0.0)
        val safeReturn = expectedAnnualReturnPct.coerceIn(1.0, 40.0)
        val safeStepUp = annualStepUpPct.coerceIn(0.0, 30.0)
        val safeYears = durationYears.coerceIn(1, 50)

        val monthlyRate = safeReturn / 12.0 / 100.0
        var corpus = safeLump
        var totalInvested = safeLump
        var currentMonthlySip = safeMonthly
        val yearlyPoints = mutableListOf<SipYearPoint>()

        for (year in 1..safeYears) {
            for (m in 1..12) {
                corpus = (corpus + currentMonthlySip) * (1.0 + monthlyRate)
                totalInvested += currentMonthlySip
            }
            yearlyPoints.add(
                SipYearPoint(
                    year = year,
                    totalInvested = totalInvested.round2(),
                    totalCorpus = corpus.round2(),
                    wealthGained = (corpus - totalInvested).coerceAtLeast(0.0).round2()
                )
            )
            currentMonthlySip *= (1.0 + safeStepUp / 100.0)
        }

        val wealthGained = (corpus - totalInvested).coerceAtLeast(0.0)
        val multiplier = if (totalInvested > 0) (corpus / totalInvested).round2() else 1.0

        return SipCalculationResult(
            monthlyInvestment = safeMonthly.round2(),
            initialLumpSum = safeLump.round2(),
            expectedAnnualReturnPct = safeReturn.round2(),
            annualStepUpPct = safeStepUp.round2(),
            durationYears = safeYears,
            totalInvested = totalInvested.round2(),
            totalWealthGained = wealthGained.round2(),
            maturityCorpus = corpus.round2(),
            wealthMultiplier = multiplier,
            yearlyPoints = yearlyPoints
        )
    }

    /**
     * FIRE (Financial Independence, Retire Early) & Passive Income Engine
     */
    fun calculateFire(
        monthlyExpensesUsd: Double,
        safeWithdrawalRatePct: Double,
        currentPortfolioValueUsd: Double,
        monthlySipUsd: Double,
        expectedAnnualReturnPct: Double,
        estimatedAnnualDividendUsd: Double
    ): FireCalculationResult {
        val safeExpenses = monthlyExpensesUsd.coerceAtLeast(100.0)
        val safeSwr = safeWithdrawalRatePct.coerceIn(2.0, 6.0)
        val annualExpenses = safeExpenses * 12.0
        val targetCorpus = annualExpenses / (safeSwr / 100.0)
        val progress = if (targetCorpus > 0) ((currentPortfolioValueUsd / targetCorpus) * 100.0).coerceIn(0.0, 100.0) else 0.0

        // Estimate years to reach targetCorpus given currentPortfolioValueUsd + monthlySipUsd
        val r = (expectedAnnualReturnPct.coerceIn(1.0, 30.0) / 100.0) / 12.0
        var simCorpus = currentPortfolioValueUsd
        var months = 0
        while (simCorpus < targetCorpus && months < 720) {
            simCorpus = (simCorpus + monthlySipUsd.coerceAtLeast(50.0)) * (1.0 + r)
            months++
        }
        val yearsToFire = (months / 12.0).round2()
        val passiveIncomeNow = ((currentPortfolioValueUsd * (safeSwr / 100.0)) / 12.0).round2()

        return FireCalculationResult(
            monthlyExpenses = safeExpenses.round2(),
            safeWithdrawalRatePct = safeSwr.round2(),
            targetFireCorpus = targetCorpus.round2(),
            currentPortfolioValue = currentPortfolioValueUsd.round2(),
            progressPercent = progress.round2(),
            estimatedYearsToFire = yearsToFire,
            projectedMonthlyPassiveIncome = passiveIncomeNow,
            estimatedAnnualDividendUsd = estimatedAnnualDividendUsd.round2()
        )
    }

    /**
     * Institutional Portfolio Health, Diversification & Dividend Yield Analyzer
     */
    fun analyzePortfolioHealth(
        holdings: List<EnrichedHolding>,
        quotes: List<StockQuote>
    ): PortfolioHealthMetrics {
        if (holdings.isEmpty()) {
            return PortfolioHealthMetrics(
                diversificationScore = 0,
                riskGrade = "Unallocated",
                topHoldingSymbol = "-",
                topHoldingWeightPct = 0.0,
                sectorCount = 0,
                estimatedAnnualDividendUsd = 0.0,
                averageYieldPct = 0.0,
                insightTip = "Add at least 3–5 positions across Technology, Index ETFs, and Financials to unlock your portfolio diversification score."
            )
        }

        val totalVal = holdings.sumOf { it.marketValue }.coerceAtLeast(1.0)
        val sortedByWeight = holdings.sortedByDescending { it.marketValue }
        val topHolding = sortedByWeight.first()
        val topWeightPct = ((topHolding.marketValue / totalVal) * 100.0).round2()
        val sectorCount = holdings.map { it.holding.sector }.distinct().size

        val quoteMap = quotes.associateBy { it.symbol.uppercase() }
        var totalAnnualDividends = 0.0
        holdings.forEach { item ->
            val q = quoteMap[item.holding.symbol.uppercase()]
            val divYield = q?.dividendYieldPct ?: 0.85
            totalAnnualDividends += item.marketValue * (divYield / 100.0)
        }
        val avgYield = ((totalAnnualDividends / totalVal) * 100.0).round2()

        // Score calculation (0..100)
        val countScore = (holdings.size * 12).coerceAtMost(40)
        val sectorScore = (sectorCount * 12).coerceAtMost(36)
        val concentrationPenalty = if (topWeightPct > 50.0) ((topWeightPct - 50.0) * 0.6).toInt() else 0
        val rawScore = (24 + countScore + sectorScore - concentrationPenalty).coerceIn(15, 98)

        val grade = when {
            rawScore >= 80 -> "Institutional Grade (Well Diversified)"
            rawScore >= 60 -> "Balanced Growth"
            else -> "Concentrated Risk"
        }

        val tip = when {
            topWeightPct > 45.0 -> "High concentration in ${topHolding.holding.symbol} (${topWeightPct}%). Consider adding broad Index ETFs (SPY/QQQ) to reduce single-stock volatility."
            sectorCount < 3 -> "Your portfolio spans $sectorCount sector(s). Expanding into Healthcare, Financials, or Global equities improves downside resilience."
            else -> "Strong multi-sector diversification across $sectorCount sectors with an estimated ${avgYield}% annual dividend yield."
        }

        return PortfolioHealthMetrics(
            diversificationScore = rawScore,
            riskGrade = grade,
            topHoldingSymbol = topHolding.holding.symbol,
            topHoldingWeightPct = topWeightPct,
            sectorCount = sectorCount,
            estimatedAnnualDividendUsd = totalAnnualDividends.round2(),
            averageYieldPct = avgYield,
            insightTip = tip
        )
    }

    private fun Double.round2(): Double = (this * 100.0).roundToInt() / 100.0
}
