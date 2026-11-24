package com.family.inventory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.inventory.data.SummarySegment
import com.family.inventory.data.TopItem

/** 与网页端 PIE_COLORS 一致的配色，保证同一位置/标签在两端颜色相近。 */
private val PieColors = listOf(
    Color(0xFF2563EB), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF64748B),
    Color(0xFF84CC16), Color(0xFFF97316)
)

private fun colorAt(i: Int): Color = PieColors[i % PieColors.size]

/**
 * 仪表盘：对齐服务端 GET /api/summary。
 * 概览数字 + 按位置/按标签环形饼图（图例可点，点后回列表按该维度筛选）+ 各位置件数柱状图 + 数量 Top10（点开详情）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) {
        if (vm.summary == null) vm.loadSummary()
    }
    val summary = vm.summary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("仪表盘", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${vm.stats.items} 件物品 · ${vm.stats.locations} 个位置 · ${vm.stats.withImage} 张图片",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.goToItems() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回物品列表")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadSummary() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (summary == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (vm.summaryLoading) CircularProgressIndicator()
                else Text("暂无数据，点右上角刷新", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OverviewCard(vm)
                PieCard(
                    title = "按位置分布",
                    hint = "点图例 → 列表按该位置筛选",
                    segments = summary.byLocation,
                    onClick = { vm.applyDashboardLocation(it) }
                )
                PieCard(
                    title = "按标签分布",
                    hint = "点图例 → 列表按该标签筛选",
                    segments = summary.byTag,
                    onClick = { vm.applyDashboardTag(it) }
                )
                LocationBarCard(
                    segments = summary.byLocation,
                    onClick = { vm.applyDashboardLocation(it) }
                )
                TopItemCard(
                    items = summary.topItems,
                    onClick = { vm.openItemDetail(it) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DashCard(
    title: String,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (hint != null) {
                Text(
                    hint,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun OverviewCard(vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("物品", vm.stats.items.toString(), Modifier.weight(1f))
        StatTile("位置", vm.stats.locations.toString(), Modifier.weight(1f))
        StatTile("图片", vm.stats.withImage.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PieCard(
    title: String,
    hint: String,
    segments: List<SummarySegment>,
    onClick: (String) -> Unit
) {
    DashCard(title = title, hint = hint) {
        if (segments.isEmpty()) {
            Text("暂无数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        val total = segments.sumOf { it.count }.coerceAtLeast(1)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(segments)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "件物品",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        segments.forEachIndexed { index, seg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(seg.name) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(colorAt(index), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    seg.name,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${seg.count} 件 / ${seg.quantity}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DonutChart(segments: List<SummarySegment>) {
    val total = segments.sumOf { it.count }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = Modifier.size(170.dp).padding(6.dp)) {
        val strokeWidth = 20.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val topLeft = Offset(
            x = (size.width - radius * 2f) / 2f,
            y = (size.height - radius * 2f) / 2f
        )
        val arcSize = Size(radius * 2f, radius * 2f)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        var start = -90f
        val gap = if (segments.size > 1) 2.5f else 0f
        segments.forEachIndexed { index, seg ->
            val sweep = 360f * seg.count / total
            drawArc(
                color = colorAt(index),
                startAngle = start + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            start += sweep
        }
    }
}

@Composable
private fun LocationBarCard(segments: List<SummarySegment>, onClick: (String) -> Unit) {
    DashCard(title = "各位置件数", hint = "点柱条 → 列表按该位置筛选") {
        if (segments.isEmpty()) {
            Text("暂无数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        val max = segments.maxOf { it.count }.coerceAtLeast(1)
        segments.forEachIndexed { index, seg ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(seg.name) }
                    .padding(vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        seg.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${seg.count} 件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                BarTrack(fraction = seg.count.toFloat() / max, color = colorAt(index))
            }
        }
    }
}

@Composable
private fun TopItemCard(items: List<TopItem>, onClick: (String) -> Unit) {
    DashCard(title = "数量 Top 10", hint = "点条目 → 打开该物品详情") {
        if (items.isEmpty()) {
            Text("暂无数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        val max = items.maxOf { it.quantity }.coerceAtLeast(1)
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(item.id) }
                    .padding(vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "×${item.quantity}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (item.location.isNotBlank()) {
                    Text(
                        item.location,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                }
                Spacer(Modifier.height(4.dp))
                BarTrack(fraction = item.quantity.toFloat() / max, color = colorAt(index))
            }
        }
    }
}

@Composable
private fun BarTrack(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
    }
}
