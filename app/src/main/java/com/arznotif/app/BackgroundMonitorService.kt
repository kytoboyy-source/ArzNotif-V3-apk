package com.arznotif.app

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arznotif.app.data.AlertStore
import com.arznotif.app.data.PriceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BackgroundMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = PriceRepository()
    private lateinit var store: AlertStore

    override fun onCreate() {
        super.onCreate()
        store = AlertStore(this)
        NotificationHelper.createChannel(this)
        startForeground(9001, monitorNotification())
        scope.launch {
            while (isActive) {
                checkAlerts()
                delay(30_000)
            }
        }
    }

    private suspend fun checkAlerts() {
        val alerts = store.alertsFlow.first().filter { it.enabled }
        if (alerts.isEmpty()) { stopSelf(); return }
        val prices = repo.fetchAllPrices().getOrNull()?.items ?: return
        alerts.forEach { alert ->
            val item = prices.firstOrNull { it.id == alert.assetId } ?: return@forEach
            val hitAbove = alert.notifyAbove && item.priceToman >= alert.targetPrice
            val hitBelow = alert.notifyBelow && item.priceToman <= alert.targetPrice
            val hitPercent = alert.percentMove?.let { kotlin.math.abs(item.changePercent ?: 0.0) >= it } ?: false
            if (hitAbove || hitBelow || hitPercent) {
                val msg = if (hitPercent) "${item.nameFa}: نوسان ${String.format("%.2f", item.changePercent ?: 0.0)}% • ${formatToman(item.priceToman)}" else "${item.nameFa}: ${formatToman(item.priceToman)}"
                NotificationHelper.showAlert(this, "ArzNotif • هشدار هوشمند", msg, item.id.hashCode())
            }
        }
    }

    private fun monitorNotification(): Notification = NotificationCompat.Builder(this, NotificationHelper.MONITOR_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setContentTitle("ArzNotif")
        .setContentText("پایش هوشمند هشدارهای قیمت فعال است")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BackgroundMonitorService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, BackgroundMonitorService::class.java)) }
    }
}
