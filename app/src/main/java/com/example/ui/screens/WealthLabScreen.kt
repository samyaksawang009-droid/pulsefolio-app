package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppCurrency
import com.example.data.model.FireCalculationResult
import com.example.data.model.PortfolioHealthMetrics
import com.example.data.model.SipCalculationResult
import com.example.ui.components.SipGrowthCurveChart
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenSoft
import com.example.ui.theme.CyanSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WealthLabScreen(
    currency: AppCurrency,
    sipResult: SipCalculationResult,
    fireResult: FireCalculationResult,
    healthMetrics: PortfolioHealthMetrics,
    onSipMonthlyChange: (Double) -> Unit,
    onSipLumpSumChange: (Double) -> Unit,
    onSipReturnRateChange: (Double) -> Unit,
    onSipStepUpChange: (Double) -> Unit,
    onSipYearsChange: (Int) -> Unit,
    onFireMonthlyExpensesChange: (Double) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wealth_lab_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Portfolio Health, Risk Grade & Passive Dividend Income Card
        item {
            PortfolioHealthAndDividendCard(
                currency = currency,
                health = healthMetrics,
                onShareReport = {
                    val shareText = buildString {
                        appendLine("📊 PulseFolio Global Wealth Report (${currency.code})")
                        appendLine("• Diversification Score: ${healthMetrics.diversificationScore}/100 (${healthMetrics.riskGrade})")
                        appendLine("• Est. Annual Dividend Income: ${currency.format(healthMetrics.estimatedAnnualDividendUsd)} (${healthMetrics.averageYieldPct}% yield)")
                        appendLine("• SIP ${sipResult.durationYears}-Yr Target Corpus: ${currency.format(sipResult.maturityCorpus, 0)} (${sipResult.wealthMultiplier}x multiplier)")
                        appendLine("• FIRE Freedom Target: ${currency.format(fireResult.targetFireCorpus, 0)} (${fireResult.progressPercent}% achieved)")
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "My PulseFolio Wealth & FIRE Report")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Wealth Report"))
                }
            )
        }

        // 2. Global SIP / DCA & Step-Up Wealth Compounding Simulator
        item {
            SipCompoundingSimulatorCard(
                currency = currency,
                sipResult = sipResult,
                onSipMonthlyChange = onSipMonthlyChange,
                onSipLumpSumChange = onSipLumpSumChange,
                onSipReturnRateChange = onSipReturnRateChange,
                onSipStepUpChange = onSipStepUpChange,
                onSipYearsChange = onSipYearsChange
            )
        }

        // 3. FIRE (Financial Independence, Retire Early) Freedom Planner
        item {
            FireFreedomPlannerCard(
                currency = currency,
                fireResult = fireResult,
                onFireMonthlyExpensesChange = onFireMonthlyExpensesChange
            )
        }
    }
}

@Composable
private fun PortfolioHealthAndDividendCard(
    currency: AppCurrency,
    health: PortfolioHealthMetrics,
    onShareReport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_health_score_card"),
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
                            Color(0xFF1E1B4B)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = BullishGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PORTFOLIO HEALTH & DIVIDEND TRACKER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                    FilledTonalButton(
                        onClick = onShareReport,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("share_wealth_report_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Report", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${health.diversificationScore} / 100",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White
                        )
                        Text(
                            text = health.riskGrade,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF6EE7B7)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "EST. ANNUAL DIVIDENDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = currency.format(health.estimatedAnnualDividendUsd),
                            style = MaterialTheme.typography.titleLarge,
                            color = AmberTertiary
                        )
                        Text(
                            text = String.format(Locale.US, "Avg Yield: %.2f%% p.a.", health.averageYieldPct),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (health.diversificationScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = BullishGreen,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = health.insightTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
private fun SipCompoundingSimulatorCard(
    currency: AppCurrency,
    sipResult: SipCalculationResult,
    onSipMonthlyChange: (Double) -> Unit,
    onSipLumpSumChange: (Double) -> Unit,
    onSipReturnRateChange: (Double) -> Unit,
    onSipStepUpChange: (Double) -> Unit,
    onSipYearsChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sip_simulator_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoGraph,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SIP / DCA & Step-Up Wealth Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Model recurring monthly investments with annual step-up compounding",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Maturity Highlight Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROJECTED ${sipResult.durationYears}-YR CORPUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = BullishGreenSoft,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${sipResult.wealthMultiplier}x Wealth Multiplier",
                                style = MaterialTheme.typography.labelSmall,
                                color = BullishGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currency.format(sipResult.maturityCorpus, 0),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL INVESTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = currency.format(sipResult.totalInvested, 0),
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("WEALTH GAINED (RETURNS)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = currency.formatSigned(sipResult.totalWealthGained, 0),
                                style = MaterialTheme.typography.labelLarge,
                                color = BullishGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SipGrowthCurveChart(points = sipResult.yearlyPoints)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Monthly SIP Slider
            WealthSliderItem(
                label = "Monthly SIP / DCA Contribution",
                valueLabel = "${currency.format(sipResult.monthlyInvestment, 0)} / mo",
                value = sipResult.monthlyInvestment.toFloat(),
                range = 100f..10000f,
                onValueChange = { onSipMonthlyChange((it / 50f).roundToInt() * 50.0) },
                testTag = "sip_monthly_slider"
            )

            // Initial Lump Sum Slider
            WealthSliderItem(
                label = "Initial Lump-Sum Capital",
                valueLabel = currency.format(sipResult.initialLumpSum, 0),
                value = sipResult.initialLumpSum.toFloat(),
                range = 0f..100000f,
                onValueChange = { onSipLumpSumChange((it / 1000f).roundToInt() * 1000.0) },
                testTag = "sip_lumpsum_slider"
            )

            // Expected Annual Return Slider
            WealthSliderItem(
                label = "Expected Annual Return (CAGR %)",
                valueLabel = String.format(Locale.US, "%.1f%% p.a.", sipResult.expectedAnnualReturnPct),
                value = sipResult.expectedAnnualReturnPct.toFloat(),
                range = 4f..24f,
                onValueChange = { onSipReturnRateChange((it * 2f).roundToInt() / 2.0) },
                testTag = "sip_return_slider"
            )

            // Annual Step-Up % Slider
            WealthSliderItem(
                label = "Annual SIP Step-Up % (Yearly Increase)",
                valueLabel = String.format(Locale.US, "+%.0f%% / yr", sipResult.annualStepUpPct),
                value = sipResult.annualStepUpPct.toFloat(),
                range = 0f..25f,
                onValueChange = { onSipStepUpChange(it.roundToInt().toDouble()) },
                testTag = "sip_stepup_slider"
            )

            // Investment Horizon Years Slider
            WealthSliderItem(
                label = "Investment Horizon (Years)",
                valueLabel = "${sipResult.durationYears} Years",
                value = sipResult.durationYears.toFloat(),
                range = 3f..40f,
                onValueChange = { onSipYearsChange(it.roundToInt()) },
                testTag = "sip_years_slider"
            )
        }
    }
}

@Composable
private fun FireFreedomPlannerCard(
    currency: AppCurrency,
    fireResult: FireCalculationResult,
    onFireMonthlyExpensesChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fire_planner_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = AmberTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "FIRE Financial Independence Planner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "25x Annual Expense Rule (4% Safe Withdrawal Rate)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TARGET FIRE FREEDOM NUMBER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currency.format(fireResult.targetFireCorpus, 0),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmberTertiary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EST. TIME TO FIRE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${fireResult.estimatedYearsToFire} Years",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = BullishGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { (fireResult.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = AmberTertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current Portfolio Progress: ${fireResult.progressPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Safe Monthly Draw: ${currency.format(fireResult.projectedMonthlyPassiveIncome)}/mo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            WealthSliderItem(
                label = "Target Monthly Retirement Lifestyle Expense",
                valueLabel = "${currency.format(fireResult.monthlyExpenses, 0)} / mo",
                value = fireResult.monthlyExpenses.toFloat(),
                range = 500f..15000f,
                onValueChange = { onFireMonthlyExpensesChange((it / 100f).roundToInt() * 100.0) },
                testTag = "fire_expenses_slider"
            )
        }
    }
}

@Composable
private fun WealthSliderItem(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
