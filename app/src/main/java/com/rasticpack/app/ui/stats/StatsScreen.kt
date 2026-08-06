package com.rasticpack.app.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.Gold
import com.rasticpack.app.ui.theme.GreenDark
import com.rasticpack.app.ui.theme.Blue
import com.rasticpack.app.ui.theme.BlueDark
import com.rasticpack.app.ui.theme.Green
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary

/**
 * معادل «TAB — آمار سود» در 4.html: تاگل روزانه/هفته/ماه/سال، سه نمودار میله‌ای
 * تاشو (گردش حساب، سود، تعداد فاکتور) و سه کارت رتبه (پرفروش‌ترین کارتن،
 * مشتری با بیشترین خرید، مشتری با بیشترین سود).
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val data = remember(state.period, state.invoices) { viewModel.data() }
    val leaders = remember(state.period, state.invoices) { viewModel.leaders() }
    val hasInvoices = state.invoices.isNotEmpty()
    val periodLabel = StatsEngine.periodLabel(state.period)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊 آمار سود", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onBack) { Text("‹ بازگشت") }
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PeriodChip("روزانه", state.period == StatsPeriod.DAY) { viewModel.setPeriod(StatsPeriod.DAY) }
                Spacer(Modifier.width(6.dp))
                PeriodChip("هفته", state.period == StatsPeriod.WEEK) { viewModel.setPeriod(StatsPeriod.WEEK) }
                Spacer(Modifier.width(6.dp))
                PeriodChip("ماه", state.period == StatsPeriod.MONTH) { viewModel.setPeriod(StatsPeriod.MONTH) }
                Spacer(Modifier.width(6.dp))
                PeriodChip("سال", state.period == StatsPeriod.YEAR) { viewModel.setPeriod(StatsPeriod.YEAR) }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            StatsChartCard(
                dotColor = Blue,
                title = "گردش حساب",
                totalText = fmtNumStats(data.sumOf { it.turnover }) + " ریال · $periodLabel",
                totalColor = Blue,
                open = state.turnoverChartOpen,
                onToggle = { viewModel.toggleTurnoverChart() },
                hasData = hasInvoices
            ) {
                StatsBarChart(data = data, valueOf = { it.turnover }, barColor = Blue, labelColor = BlueDark)
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            StatsChartCard(
                dotColor = Green,
                title = "سود",
                totalText = fmtNumStats(data.sumOf { it.profit }) + " ریال · $periodLabel",
                totalColor = GreenDark,
                open = state.profitChartOpen,
                onToggle = { viewModel.toggleProfitChart() },
                hasData = hasInvoices
            ) {
                StatsBarChart(data = data, valueOf = { it.profit }, barColor = Green, labelColor = GreenDark)
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            StatsChartCard(
                dotColor = Gold,
                title = "تعداد فاکتور",
                totalText = fmtNumStats(data.sumOf { it.count.toDouble() }) + " فاکتور · $periodLabel",
                totalColor = Gold,
                open = state.countChartOpen,
                onToggle = { viewModel.toggleCountChart() },
                hasData = hasInvoices
            ) {
                StatsBarChart(data = data, valueOf = { it.count.toDouble() }, barColor = Gold, labelColor = Color(0xFF92400E))
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            LeaderCard(
                icon = "📦",
                title = "پرفروش‌ترین کارتن",
                name = leaders.topCarton?.label,
                valueLabel = "عدد فروخته‌شده",
                valueText = leaders.topCarton?.let { fmtNumStats(it.qty.toDouble()) } ?: "",
                valueColor = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
        }
        item {
            LeaderCard(
                icon = "👤",
                title = "مشتری با بیشترین خرید",
                name = leaders.topBuyer?.name,
                valueLabel = "ریال خرید",
                valueText = leaders.topBuyer?.let { fmtNumStats(it.turnover) } ?: "",
                valueColor = Blue
            )
            Spacer(Modifier.height(10.dp))
        }
        item {
            LeaderCard(
                icon = "💎",
                title = "مشتری با بیشترین سود",
                name = leaders.topProfitCustomer?.name,
                valueLabel = "ریال سود",
                valueText = leaders.topProfitCustomer?.let { fmtNumStats(it.profit) } ?: "",
                valueColor = GreenDark
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

/** یک کارت تاشوی نمودار — معادل هر یک از سه بلاک گردش‌حساب/سود/تعداد در وب */
@Composable
private fun StatsChartCard(
    dotColor: Color,
    title: String,
    totalText: String,
    totalColor: Color,
    open: Boolean,
    onToggle: () -> Unit,
    hasData: Boolean,
    chart: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(11.dp).height(11.dp)
                            .background(dotColor, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(totalText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = totalColor)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "▾",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.rotate(if (open) 180f else 0f)
                    )
                }
            }
            AnimatedVisibility(visible = open) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Divider(color = BorderColor)
                    Spacer(Modifier.height(10.dp))
                    if (!hasData) {
                        Text(
                            "داده‌ای برای نمایش نمودار وجود ندارد.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        chart()
                    }
                }
            }
        }
    }
}

/** نمودار میله‌ای ساده با Canvas — معادل renderStatsBarChart در وب */
@Composable
private fun StatsBarChart(
    data: List<StatsPoint>,
    valueOf: (StatsPoint) -> Double,
    barColor: Color,
    labelColor: Color
) {
    val maxVal = (data.maxOfOrNull { valueOf(it) } ?: 0.0).let { if (it <= 0.0) 1.0 else it }
    val barWidthDp = if (data.size <= 7) 40.dp else if (data.size <= 8) 34.dp else 26.dp
    val gapDp = if (data.size <= 7) 12.dp else 8.dp
    val chartHeightDp = 110.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(gapDp)
    ) {
        data.forEach { point ->
            val v = valueOf(point)
            val isZero = v <= 0.0
            val fraction = (v / maxVal).toFloat().coerceIn(0f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(barWidthDp)
            ) {
                Text(
                    text = if (v != 0.0) StatsEngine.formatShort(v) else "۰",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isZero) TextMuted else labelColor,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(barWidthDp)
                        .height((chartHeightDp.value * fraction).coerceAtLeast(4f).dp)
                        .background(
                            color = if (isZero) BorderColor else barColor,
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(point.label, fontSize = 10.sp, color = TextMuted, maxLines = 1)
            }
        }
    }
}

/** کارت رتبه (پرفروش‌ترین/بیشترین خرید/بیشترین سود) — معادل statsLeaderCardHtml در وب */
@Composable
private fun LeaderCard(
    icon: String,
    title: String,
    name: String?,
    valueLabel: String,
    valueText: String,
    valueColor: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp, if (name != null) MaterialTheme.colorScheme.primary else BorderColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(30.dp).height(30.dp)
                    .background(SurfaceAlt, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 14.sp) }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    name ?: "داده‌ای در این بازه ثبت نشده",
                    fontSize = if (name != null) 15.sp else 12.sp,
                    fontWeight = if (name != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (name != null) MaterialTheme.colorScheme.onSurface else TextMuted
                )
            }
            if (name != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
                    Text(valueLabel, fontSize = 10.sp, color = TextMuted)
                }
            }
        }
    }
}
