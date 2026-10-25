package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.model.AmortizationRow
import com.example.data.model.SipYearPoint
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.CyanSecondary
import java.util.Locale

@Composable
fun InteractiveStockChart(
    points: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    onScrubPrice: (Double?) -> Unit = {}
) {
    val lineColor = if (isPositive) BullishGreen else BearishRed
    var scrubbedIndex by remember(points) { mutableStateOf<Int?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (points.isNotEmpty() && size.width > 0) {
                                val idx = ((offset.x / size.width) * (points.size - 1))
                                    .toInt()
                                    .coerceIn(0, points.lastIndex)
                                scrubbedIndex = idx
                                onScrubPrice(points[idx])
                                tryAwaitRelease()
                                scrubbedIndex = null
                                onScrubPrice(null)
                            }
                        }
                    )
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scrubbedIndex = null
                            onScrubPrice(null)
                        },
                        onDragCancel = {
                            scrubbedIndex = null
                            onScrubPrice(null)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (points.isNotEmpty() && size.width > 0) {
                                val idx = ((change.position.x / size.width) * (points.size - 1))
                                    .toInt()
                                    .coerceIn(0, points.lastIndex)
                                scrubbedIndex = idx
                                onScrubPrice(points[idx])
                            }
                        }
                    )
                }
        ) {
            if (points.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val minVal = points.minOrNull() ?: 0.0
            val maxVal = points.maxOrNull() ?: 1.0
            val range = (maxVal - minVal).coerceAtLeast(0.01)
            val verticalPad = h * 0.12f
            val chartHeight = (h - verticalPad * 2f).coerceAtLeast(1f)

            for (i in 0..3) {
                val y = verticalPad + (chartHeight / 3f) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f
                )
            }

            val linePath = Path()
            val fillPath = Path()

            points.forEachIndexed { index, value ->
                val x = (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * w
                val normalized = ((value - minVal) / range).toFloat()
                val y = verticalPad + chartHeight * (1f - normalized)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.34f),
                        lineColor.copy(alpha = 0.02f)
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val activeIdx = scrubbedIndex ?: points.lastIndex
            val activeVal = points[activeIdx]
            val activeX = (activeIdx.toFloat() / (points.size - 1).coerceAtLeast(1)) * w
            val activeNorm = ((activeVal - minVal) / range).toFloat()
            val activeY = verticalPad + chartHeight * (1f - activeNorm)

            if (scrubbedIndex != null) {
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(activeX, 0f),
                    end = Offset(activeX, h),
                    strokeWidth = 2f
                )
            }

            drawCircle(
                color = lineColor.copy(alpha = 0.3f),
                radius = 16f,
                center = Offset(activeX, activeY)
            )
            drawCircle(
                color = lineColor,
                radius = 7f,
                center = Offset(activeX, activeY)
            )
        }
    }
}

@Composable
fun MiniSparkline(
    points: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isPositive) BullishGreen else BearishRed
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).coerceAtLeast(0.01)

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = (index.toFloat() / (points.size - 1)) * w
            val norm = ((value - minVal) / range).toFloat()
            val y = h * 0.1f + (h * 0.8f) * (1f - norm)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

data class AllocationSlice(
    val label: String,
    val amount: Double,
    val color: Color
)

@Composable
fun AllocationDonutChart(
    slices: List<AllocationSlice>,
    centerTopLabel: String,
    centerValueLabel: String,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.amount }.coerceAtLeast(0.01)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val strokeWidth = 28f
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = ((slice.amount / total) * 360.0).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerTopLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = centerValueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.take(5).forEach { slice ->
                val pct = (slice.amount / total) * 100.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = slice.color
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f%%", pct),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AmortizationBalanceChart(
    schedule: List<AmortizationRow>,
    modifier: Modifier = Modifier
) {
    if (schedule.isEmpty()) return
    val maxBalance = schedule.first().endingBalance.coerceAtLeast(1.0)
    val maxTotalPaid = (schedule.last().cumulativePrincipal + schedule.last().cumulativeInterest).coerceAtLeast(maxBalance)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val w = size.width
            val h = size.height
            val step = (schedule.size - 1).coerceAtLeast(1)

            val balancePath = Path()
            val interestPath = Path()

            schedule.forEachIndexed { idx, row ->
                val x = (idx.toFloat() / step) * w
                val yBal = h - ((row.endingBalance / maxTotalPaid).toFloat() * (h * 0.88f))
                val yInt = h - ((row.cumulativeInterest / maxTotalPaid).toFloat() * (h * 0.88f))
                if (idx == 0) {
                    balancePath.moveTo(x, yBal)
                    interestPath.moveTo(x, yInt)
                } else {
                    balancePath.lineTo(x, yBal)
                    interestPath.lineTo(x, yInt)
                }
            }

            for (i in 0..2) {
                val y = (h / 3f) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f
                )
            }

            drawPath(
                path = balancePath,
                color = BullishGreen,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawPath(
                path = interestPath,
                color = AmberTertiary,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = BullishGreen) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Remaining Principal Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = AmberTertiary) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Cumulative Interest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SipGrowthCurveChart(
    points: List<SipYearPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val maxVal = points.last().totalCorpus.coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val w = size.width
            val h = size.height
            val step = (points.size - 1).coerceAtLeast(1)

            val corpusPath = Path()
            val fillCorpusPath = Path()
            val investedPath = Path()

            points.forEachIndexed { idx, pt ->
                val x = (idx.toFloat() / step) * w
                val yCorpus = h - ((pt.totalCorpus / maxVal).toFloat() * (h * 0.88f))
                val yInv = h - ((pt.totalInvested / maxVal).toFloat() * (h * 0.88f))
                if (idx == 0) {
                    corpusPath.moveTo(x, yCorpus)
                    fillCorpusPath.moveTo(x, h)
                    fillCorpusPath.lineTo(x, yCorpus)
                    investedPath.moveTo(x, yInv)
                } else {
                    corpusPath.lineTo(x, yCorpus)
                    fillCorpusPath.lineTo(x, yCorpus)
                    investedPath.lineTo(x, yInv)
                }
            }
            fillCorpusPath.lineTo(w, h)
            fillCorpusPath.close()

            drawPath(
                path = fillCorpusPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BullishGreen.copy(alpha = 0.30f),
                        BullishGreen.copy(alpha = 0.02f)
                    )
                )
            )
            drawPath(
                path = corpusPath,
                color = BullishGreen,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawPath(
                path = investedPath,
                color = CyanSecondary,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = BullishGreen) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Compounded Maturity Corpus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = CyanSecondary) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Total Capital Invested",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

val SectorPalette = listOf(
    BullishGreen,
    CyanSecondary,
    AmberTertiary,
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF3B82F6)
)
