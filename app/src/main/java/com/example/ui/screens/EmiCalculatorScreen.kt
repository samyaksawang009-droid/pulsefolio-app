package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AppCurrency
import com.example.data.model.EmiCalculationResult
import com.example.data.model.EmiPlan
import com.example.ui.components.AllocationDonutChart
import com.example.ui.components.AllocationSlice
import com.example.ui.components.AmortizationBalanceChart
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenSoft
import com.example.ui.viewmodel.UiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EmiCalculatorScreen(
    currency: AppCurrency,
    selectedLoanType: String,
    principal: Double,
    annualRate: Double,
    tenureMonths: Int,
    monthlyPrepayment: Double,
    calculation: EmiCalculationResult,
    savedPlansState: UiState<List<EmiPlan>>,
    onSelectPreset: (String) -> Unit,
    onPrincipalChange: (Double) -> Unit,
    onAnnualRateChange: (Double) -> Unit,
    onTenureMonthsChange: (Int) -> Unit,
    onMonthlyPrepaymentChange: (Double) -> Unit,
    onSavePlan: (String) -> Unit,
    onLoadPlan: (EmiPlan) -> Unit,
    onDeletePlan: (String) -> Unit
) {
    var showAmortizationSchedule by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExactNumericDialog by remember { mutableStateOf(false) }

    val loanCategories = remember {
        listOf("Home Loan", "Auto Loan", "Personal Loan", "Education Loan", "Investment Loan")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("emi_calculator_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                loanCategories.forEach { category ->
                    FilterChip(
                        selected = selectedLoanType == category,
                        onClick = { onSelectPreset(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("emi_preset_${category.lowercase().replace(" ", "_")}")
                    )
                }
            }
        }

        item {
            EmiResultHeroCard(
                currency = currency,
                calculation = calculation,
                loanType = selectedLoanType,
                onSaveClicked = { showSaveDialog = true }
            )
        }

        item {
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
                            text = "Loan Parameters (${currency.code})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showExactNumericDialog = true },
                            modifier = Modifier.testTag("emi_exact_input_button")
                        ) {
                            Text("Enter Exact Values")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ParameterSliderBlock(
                        label = "Loan Principal Amount",
                        formattedValue = currency.format(principal, 0),
                        value = principal.toFloat(),
                        valueRange = 5000f..1500000f,
                        onValueChange = { onPrincipalChange((it / 1000f).roundToInt() * 1000.0) },
                        testTag = "emi_principal_slider"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderBlock(
                        label = "Annual Interest Rate (%)",
                        formattedValue = String.format(Locale.US, "%.2f%% p.a.", annualRate),
                        value = annualRate.toFloat(),
                        valueRange = 1.0f..24.0f,
                        onValueChange = { onAnnualRateChange((it * 20f).roundToInt() / 20.0) },
                        testTag = "emi_rate_slider"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val years = tenureMonths / 12
                    val remMonths = tenureMonths % 12
                    val tenureLabel = if (years > 0) {
                        "${years}y ${if (remMonths > 0) "${remMonths}m " else ""}($tenureMonths mos)"
                    } else {
                        "$tenureMonths mos"
                    }
                    ParameterSliderBlock(
                        label = "Repayment Tenure",
                        formattedValue = tenureLabel,
                        value = tenureMonths.toFloat(),
                        valueRange = 12f..360f,
                        onValueChange = { onTenureMonthsChange((it / 6f).roundToInt() * 6) },
                        testTag = "emi_tenure_slider"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ParameterSliderBlock(
                        label = "Extra Monthly Prepayment (Principal Booster)",
                        formattedValue = "+${currency.format(monthlyPrepayment, 0)} / mo",
                        value = monthlyPrepayment.toFloat(),
                        valueRange = 0f..5000f,
                        onValueChange = { onMonthlyPrepaymentChange((it / 50f).roundToInt() * 50.0) },
                        testTag = "emi_prepayment_slider"
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Principal vs. Total Interest Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AllocationDonutChart(
                        slices = listOf(
                            AllocationSlice("Principal Loan", calculation.principal, BullishGreen),
                            AllocationSlice("Total Interest", calculation.totalInterestWithPrepay, AmberTertiary)
                        ),
                        centerTopLabel = "Total Payable",
                        centerValueLabel = currency.format(calculation.totalPaymentWithPrepay, 0)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Balance & Interest Payoff Trajectory",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AmortizationBalanceChart(schedule = calculation.schedule)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
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
                        Column {
                            Text(
                                text = "Amortization Repayment Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Payoff in ${calculation.actualMonthsToPayOff} months (${String.format(Locale.US, "%.1f", calculation.actualMonthsToPayOff / 12.0)} years)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showAmortizationSchedule = !showAmortizationSchedule },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("toggle_amortization_button")
                        ) {
                            Text(if (showAmortizationSchedule) "Hide Table" else "View Schedule")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showAmortizationSchedule) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAmortizationSchedule) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("YEAR", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f))
                                Text("PRINCIPAL", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                Text("INTEREST", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                Text("BALANCE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.1f))
                            }

                            val yearlyGroups = calculation.schedule.groupBy { it.year }
                            yearlyGroups.forEach { (yr, rows) ->
                                val yrPrincipal = rows.sumOf { it.principalPaid + it.extraPrepayment }
                                val yrInterest = rows.sumOf { it.interestPaid }
                                val endBal = rows.last().endingBalance
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Yr $yr",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(0.6f)
                                    )
                                    Text(
                                        text = currency.format(yrPrincipal, 0),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BullishGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = currency.format(yrInterest, 0),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberTertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = currency.format(endBal, 0),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        val savedPlans = (savedPlansState as? UiState.Success)?.data ?: emptyList()
        if (savedPlans.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Cloud EMI Scenarios (${savedPlans.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(savedPlans, key = { it.id }) { plan ->
                SavedEmiPlanCard(
                    currency = currency,
                    plan = plan,
                    onLoad = { onLoadPlan(plan) },
                    onDelete = { onDeletePlan(plan.id) }
                )
            }
        }
    }

    if (showSaveDialog) {
        SaveEmiScenarioDialog(
            defaultTitle = "$selectedLoanType (${currency.format(calculation.principal, 0)})",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title ->
                onSavePlan(title)
                showSaveDialog = false
            }
        )
    }

    if (showExactNumericDialog) {
        ExactEmiInputDialog(
            currency = currency,
            initialPrincipalUsd = principal,
            initialRate = annualRate,
            initialMonths = tenureMonths,
            initialPrepaymentUsd = monthlyPrepayment,
            onDismiss = { showExactNumericDialog = false },
            onApply = { pUsd, r, m, prepayUsd ->
                onPrincipalChange(pUsd)
                onAnnualRateChange(r)
                onTenureMonthsChange(m)
                onMonthlyPrepaymentChange(prepayUsd)
                showExactNumericDialog = false
            }
        )
    }
}

@Composable
private fun EmiResultHeroCard(
    currency: AppCurrency,
    calculation: EmiCalculationResult,
    loanType: String,
    onSaveClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("emi_result_hero_card"),
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0A192F),
                            Color(0xFF164E63),
                            Color(0xFF0E1B31)
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
                        text = "${loanType.uppercase()} • MONTHLY EMI",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8)
                    )
                    Button(
                        onClick = onSaveClicked,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_emi_plan_button")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Scenario", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${currency.format(calculation.monthlyEmi)} / mo",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    modifier = Modifier.testTag("emi_monthly_value_text")
                )

                if (calculation.monthlyPrepayment > 0 && calculation.interestSaved > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = BullishGreenSoft,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = BullishGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Prepayment saves ${currency.format(calculation.interestSaved, 0)} in interest & ${calculation.monthsSaved} months!",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF6EE7B7)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("PRINCIPAL", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(
                            text = currency.format(calculation.principal, 0),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                    Column {
                        Text("TOTAL INTEREST", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(
                            text = currency.format(calculation.totalInterestWithPrepay, 0),
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberTertiary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL PAYABLE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(
                            text = currency.format(calculation.totalPaymentWithPrepay, 0),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF34D399)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterSliderBlock(
    label: String,
    formattedValue: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
private fun SavedEmiPlanCard(
    currency: AppCurrency,
    plan: EmiPlan,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_emi_card_${plan.id}"),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${plan.loanType} • ${currency.format(plan.principal, 0)} @ ${String.format(Locale.US, "%.2f%%", plan.annualRate)} for ${plan.tenureMonths} mos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "EMI: ${currency.format(plan.monthlyEmi)}/mo • Total Interest: ${currency.format(plan.totalInterest, 0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLoad) {
                    Text("Load")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete plan",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveEmiScenarioDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var titleText by remember { mutableStateOf(defaultTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save EMI Scenario to Cloud") },
        text = {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Scenario Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_emi_title_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(titleText) },
                modifier = Modifier.testTag("save_emi_confirm_button")
            ) {
                Text("Save")
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
private fun ExactEmiInputDialog(
    currency: AppCurrency,
    initialPrincipalUsd: Double,
    initialRate: Double,
    initialMonths: Int,
    initialPrepaymentUsd: Double,
    onDismiss: () -> Unit,
    onApply: (principalUsd: Double, rate: Double, months: Int, prepaymentUsd: Double) -> Unit
) {
    var principalText by remember(currency, initialPrincipalUsd) {
        mutableStateOf((initialPrincipalUsd * currency.rateFromUsd).roundToInt().toString())
    }
    var rateText by remember { mutableStateOf(initialRate.toString()) }
    var monthsText by remember { mutableStateOf(initialMonths.toString()) }
    var prepayText by remember(currency, initialPrepaymentUsd) {
        mutableStateOf((initialPrepaymentUsd * currency.rateFromUsd).roundToInt().toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Exact Loan Parameters (${currency.code})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text("Loan Principal (${currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Annual Interest Rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monthsText,
                    onValueChange = { monthsText = it },
                    label = { Text("Tenure (Months)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prepayText,
                    onValueChange = { prepayText = it },
                    label = { Text("Extra Monthly Prepayment (${currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pInCurrency = principalText.toDoubleOrNull() ?: (initialPrincipalUsd * currency.rateFromUsd)
                    val pUsd = pInCurrency / currency.rateFromUsd
                    val r = rateText.toDoubleOrNull() ?: initialRate
                    val m = monthsText.toIntOrNull() ?: initialMonths
                    val preInCurrency = prepayText.toDoubleOrNull() ?: (initialPrepaymentUsd * currency.rateFromUsd)
                    val preUsd = preInCurrency / currency.rateFromUsd
                    onApply(pUsd, r, m, preUsd)
                }
            ) {
                Text("Calculate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
