package com.arznotif.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arznotif.app.data.AlertSetting
import com.arznotif.app.data.PriceItem
import com.arznotif.app.data.PricePoint
import com.arznotif.app.ui.theme.ArzNotifTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: PriceViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationHelper.createChannel(this)
        setContent { ArzNotifTheme { ArzNotifApp(viewModel) } }
    }
}

enum class MarketTab(val title: String) { ALL("همه"), FIAT("ارزها"), GOLD("طلا و سکه"), CRYPTO("رمزارز") }

enum class AppPage { HOME, MARKETS, ALERTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArzNotifApp(viewModel: PriceViewModel) {
    val prices by viewModel.prices.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    var page by remember { mutableStateOf(AppPage.HOME) }
    var tab by remember { mutableStateOf(MarketTab.ALL) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PriceItem?>(null) }
    var alertTarget by remember { mutableStateOf<PriceItem?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            viewModel.refresh()
        }
    }

    val marketItems = prices?.items.orEmpty()
    val filtered = marketItems.filter { item ->
        val category = when {
            item.id.startsWith("price_") -> MarketTab.FIAT
            item.id in setOf("geram18","geram24","abshodeh","abshodeh_mam","sekkeh","bahar","nim","rob","gerami","ons","silver_999","ons_silver","platinum","palladium") -> MarketTab.GOLD
            else -> MarketTab.CRYPTO
        }
        val categoryOk = tab == MarketTab.ALL || category == tab
        categoryOk && (query.isBlank() || item.nameFa.contains(query, true) || item.nameEn.contains(query, true) || item.id.contains(query, true))
    }

    Scaffold(
        containerColor = Color(0xFFF4F7FB),
        topBar = { ModernTopBar(prices?.lastFetch, loading, onRefresh = viewModel::refresh) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavigationBarItem(page == AppPage.HOME, { page = AppPage.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("خانه") })
                NavigationBarItem(page == AppPage.MARKETS, { page = AppPage.MARKETS }, icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("بازار") })
                NavigationBarItem(page == AppPage.ALERTS, { page = AppPage.ALERTS }, icon = { Icon(Icons.Default.NotificationsActive, null) }, label = { Text("هشدارها") })
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (page) {
                AppPage.HOME -> {
                    item { HeroCard(marketItems, loading, prices?.lastFetch, viewModel::refresh) }
                    item { QuickMarkets(marketItems, onSelect = { selected = it }) }
                    item { SectionHeader("بازارهای محبوب", "نمایش همه", { page = AppPage.MARKETS }) }
                    items(marketItems.take(8), key = { it.id }) { item ->
                        AnimatedMarketCard(item, alerts.any { a -> a.assetId == item.id && a.enabled }, { selected = item }, { alertTarget = item })
                    }
                }
                AppPage.MARKETS -> {
                    item { SearchBox(query) { query = it } }
                    item { CategoryTabs(tab) { tab = it } }
                    if (error != null) item { StatusPill("TGJU: اتصال ناپایدار • تلاش مجدد خودکار", Color(0xFFF59E0B)) }
                    if (loading && marketItems.isEmpty()) item { LoadingCards() }
                    if (filtered.isEmpty() && !loading) item { EmptyState(query) }
                    items(filtered, key = { it.id }) { item ->
                        AnimatedMarketCard(item, alerts.any { a -> a.assetId == item.id && a.enabled }, { selected = item }, { alertTarget = item })
                    }
                    item { Footer(prices?.lastFetch) }
                }
                AppPage.ALERTS -> {
                    item { AlertsHeader(alerts.size) }
                    if (alerts.isEmpty()) item { EmptyAlerts() }
                    items(alerts, key = { it.assetId }) { alert ->
                        val item = marketItems.firstOrNull { it.id == alert.assetId }
                        if (item != null) AlertRow(item, alert) { alertTarget = item }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        AssetDetailsDialog(item, onDismiss = { selected = null }, onAlert = { selected = null; alertTarget = item })
    }
    alertTarget?.let { item ->
        AlertDialogContent(item, viewModel.getAlertFor(item.id), { alertTarget = null }, { viewModel.saveAlert(it); alertTarget = null }, { viewModel.removeAlert(item.id); alertTarget = null })
    }
}

@Composable
private fun ModernTopBar(lastFetch: Long?, loading: Boolean, onRefresh: () -> Unit) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { pulse = loading }
    val scale by animateFloatAsState(if (pulse) 1.12f else 1f, tween(450, easing = FastOutSlowInEasing), label = "pulse")
    Surface(color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.arznotif_logo), "ArzNotif", Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ArzNotif", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(Modifier.width(5.dp))
                    Text(if (loading) "در حال دریافت از TGJU…" else "قیمت زنده • TGJU", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size((44 * scale).dp)) { Icon(Icons.Default.Refresh, "بروزرسانی") }
        }
    }
}

@Composable
private fun HeroCard(items: List<PriceItem>, loading: Boolean, lastFetch: Long?, onRefresh: () -> Unit) {
    val usd = items.firstOrNull { it.id == "price_dollar_rl" }
    val gold = items.firstOrNull { it.id == "geram18" }
    val btc = items.firstOrNull { it.id == "bitcoin" }
    val rotation by rememberInfinitePulse()
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF0879F9), Color(0xFF064BC2), Color(0xFF122B86))))) {
        Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.radialGradient(listOf(Color.White.copy(.14f), Color.Transparent), radius = 600f)))
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("نبض بازار", color = Color.White.copy(.7f), fontSize = 12.sp)
                    Text("بازار، همیشه جلوی چشم شما", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("داده مستقیم از TGJU", color = Color.White.copy(.65f), fontSize = 10.sp)
                }
                Box(Modifier.size((54 + rotation * 2).dp).clip(CircleShape).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) { Image(painterResource(R.drawable.arznotif_logo), "ArzNotif", Modifier.size(44.dp).clip(CircleShape)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HeroMetric("دلار", usd?.let { formatToman(it.priceToman) } ?: "—", usd?.changePercent, Modifier.weight(1f))
                HeroMetric("طلای ۱۸", gold?.let { formatToman(it.priceToman) } ?: "—", gold?.changePercent, Modifier.weight(1f))
                HeroMetric("BTC", btc?.priceUsd?.let(::formatUsd) ?: "—", btc?.changePercent, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF34D399)))
                Spacer(Modifier.width(6.dp))
                Text(if (loading) "در حال بروزرسانی…" else "بروزرسانی خودکار هر ۱۰ ثانیه", color = Color.White.copy(.7f), fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text(lastFetch?.let { formatClock(it) } ?: "—", color = Color.White.copy(.65f), fontSize = 9.sp)
                TextButton(onClick = onRefresh) { Text("اکنون", color = Color.White, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun HeroMetric(title: String, value: String, change: Double?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(Color.White.copy(.11f)).padding(10.dp)) {
        Text(title, color = Color.White.copy(.65f), fontSize = 9.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        change?.let { Text(changeText(it), color = if (it >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 9.sp) }
    }
}

@Composable
private fun QuickMarkets(items: List<PriceItem>, onSelect: (PriceItem) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items.take(6).forEach { item ->
            Surface(Modifier.width(138.dp).clickable { onSelect(item) }, RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text("${item.iconEmoji}  ${item.nameFa}", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (item.priceUsd != null && item.id !in setOf("price_dollar_rl","geram18","abshodeh","sekkeh")) formatUsd(item.priceUsd) else formatToman(item.priceToman), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(changeText(item.changePercent ?: 0.0), color = if ((item.changePercent ?: 0.0) >= 0) Color(0xFF059669) else Color(0xFFDC2626), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(19.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("جستجو: دلار، یورو، آبشده، BTC…", fontSize = 12.sp) }, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFDCE4EF), focusedBorderColor = Color(0xFF0879F9)))
}

@Composable
private fun CategoryTabs(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarketTab.entries.forEach { tab -> FilterChip(selected == tab, { onSelect(tab) }, label = { Text(tab.title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(14.dp)) }
    }
}

@Composable
private fun AnimatedMarketCard(item: PriceItem, hasAlert: Boolean, onDetails: () -> Unit, onAlert: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(350)) + expandVertically(tween(350)), exit = fadeOut() + shrinkVertically()) {
        MarketCard(item, hasAlert, onDetails, onAlert)
    }
}

@Composable
private fun MarketCard(item: PriceItem, hasAlert: Boolean, onDetails: () -> Unit, onAlert: () -> Unit) {
    val change = item.changePercent ?: 0.0
    val positive = change >= 0
    val accent by animateColorAsState(if (positive) Color(0xFF10B981) else Color(0xFFEF4444), tween(400), label = "accent")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onDetails), RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0F6FF)), contentAlignment = Alignment.Center) { Text(item.iconEmoji, fontSize = 24.sp) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.nameFa, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(item.nameEn, color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatToman(item.priceToman), fontWeight = FontWeight.Black, fontSize = 15.sp)
                    item.priceUsd?.let { Text(formatUsd(it), color = Color(0xFF64748B), fontSize = 9.sp) }
                }
                IconButton(onClick = onAlert) { Icon(if (hasAlert) Icons.Default.NotificationsActive else Icons.Outlined.NotificationsNone, null, tint = if (hasAlert) Color(0xFF0879F9) else Color(0xFF94A3B8)) }
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(item.history, accent, Modifier.fillMaxWidth().height(44.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(changeText(change), color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("TGJU • ${formatClock(item.lastUpdate)}", color = Color(0xFF94A3B8), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun Sparkline(points: List<PricePoint>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val values = points.map { it.value }.ifEmpty { listOf(0.0, 0.0) }
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            val y = size.height - ((v - min) / range).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun AssetDetailsDialog(item: PriceItem, onDismiss: () -> Unit, onAlert: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.nameFa, fontWeight = FontWeight.Black) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatToman(item.priceToman), fontSize = 22.sp, fontWeight = FontWeight.Black)
            item.priceUsd?.let { Text(formatUsd(it), color = Color(0xFF64748B)) }
            Text(changeText(item.changePercent ?: 0.0), color = if ((item.changePercent ?: 0.0) >= 0) Color(0xFF059669) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
            Sparkline(item.history, if ((item.changePercent ?: 0.0) >= 0) Color(0xFF10B981) else Color(0xFFEF4444), Modifier.fillMaxWidth().height(150.dp))
            Text("منبع: TGJU • آخرین بروزرسانی ${formatClock(item.lastUpdate)}", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }, confirmButton = { Button(onClick = onAlert) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(5.dp)); Text("ساخت هشدار") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } })
}

@Composable
private fun AlertsHeader(count: Int) { Column(Modifier.padding(horizontal = 18.dp)) { Text("مرکز هشدار ArzNotif", fontSize = 23.sp, fontWeight = FontWeight.Black); Text("$count هشدار فعال و قابل مدیریت", color = Color(0xFF64748B), fontSize = 11.sp) } }

@Composable
private fun AlertRow(item: PriceItem, alert: AlertSetting, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEAF3FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF0879F9)) }
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.nameFa, fontWeight = FontWeight.Bold); Text("هدف: ${formatToman(alert.targetPrice)}", fontSize = 10.sp, color = Color(0xFF64748B)) }
            Text(if (alert.enabled) "فعال" else "خاموش", color = if (alert.enabled) Color(0xFF059669) else Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun EmptyAlerts() { Text("هنوز هشداری نساخته‌اید. روی زنگ کنار هر دارایی بزنید.", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Color(0xFF64748B)) }
@Composable private fun EmptyState(query: String) { Text(if (query.isBlank()) "داده‌ای موجود نیست" else "برای «$query» نتیجه‌ای پیدا نشد", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Color(0xFF64748B)) }
@Composable private fun LoadingCards() { repeat(5) { Box(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) } }
@Composable private fun StatusPill(text: String, color: Color) { Text(text, color = color, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
@Composable private fun Footer(lastFetch: Long?) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("منبع قیمت: TGJU", fontWeight = FontWeight.Bold, fontSize = 11.sp); Text("آخرین دریافت: ${lastFetch?.let(::formatDateTime) ?: "—"}", color = Color(0xFF94A3B8), fontSize = 9.sp) } }

@Composable
fun AlertDialogContent(item: PriceItem, existing: AlertSetting?, onDismiss: () -> Unit, onSave: (AlertSetting) -> Unit, onRemove: () -> Unit) {
    var targetText by remember { mutableStateOf(existing?.targetPrice?.let { "%.0f".format(Locale.US, it) } ?: "") }
    var above by remember { mutableStateOf(existing?.notifyAbove ?: true) }
    var below by remember { mutableStateOf(existing?.notifyBelow ?: true) }
    var percentText by remember { mutableStateOf(existing?.percentMove?.toString() ?: "") }
    var sound by remember { mutableStateOf(existing?.sound ?: true) }
    var vibration by remember { mutableStateOf(existing?.vibration ?: true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("هشدار هوشمند ${item.nameFa}", fontWeight = FontWeight.Black) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("قیمت فعلی: ${formatToman(item.priceToman)}", fontSize = 12.sp)
            OutlinedTextField(targetText, { targetText = it }, label = { Text("قیمت هدف (تومان)") }, singleLine = true)
            OutlinedTextField(percentText, { percentText = it }, label = { Text("هشدار نوسان ± درصد (اختیاری)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(above, { above = it }); Text("عبور رو به بالا") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(below, { below = it }); Text("عبور رو به پایین") }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(sound, { sound = it }); Spacer(Modifier.width(6.dp)); Text("صدای هشدار") }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(vibration, { vibration = it }); Spacer(Modifier.width(6.dp)); Text("ویبره") }
        }
    }, confirmButton = { Button(onClick = { targetText.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(AlertSetting(item.id, it, true, above, below, percentText.toDoubleOrNull()?.takeIf { p -> p > 0 }, sound, vibration)) } }) { Text("فعال‌سازی هشدار") } }, dismissButton = { Row { if (existing != null) TextButton(onClick = onRemove) { Text("حذف", color = Color(0xFFDC2626)) }; TextButton(onClick = onDismiss) { Text("انصراف") } } })
}

@Composable
private fun rememberInfinitePulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "logo")
    return transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
}

fun formatToman(value: Double): String = NumberFormat.getNumberInstance(Locale("fa", "IR")).apply { maximumFractionDigits = 0 }.format(value) + " تومان"
fun formatUsd(value: Double): String = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = if (value < 100) 2 else 0 }.format(value) + " $"
fun changeText(value: Double): String = "${if (value >= 0) "+" else ""}${String.format(Locale.US, "%.2f", value)}%"
fun formatClock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale("fa", "IR")).format(Date(millis))
fun formatDateTime(millis: Long): String = SimpleDateFormat("yyyy/MM/dd • HH:mm:ss", Locale("fa", "IR")).format(Date(millis))
