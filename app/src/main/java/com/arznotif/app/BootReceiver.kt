package com.arznotif.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arznotif.app.data.AlertStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val work = PeriodicWorkRequestBuilder<PriceCheckWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "price_check",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
            val alerts = runBlocking { AlertStore(context).alertsFlow.first() }
            if (alerts.any { it.enabled }) BackgroundMonitorService.start(context)
        }
    }
}
