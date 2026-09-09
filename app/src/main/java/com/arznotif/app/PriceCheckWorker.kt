package com.arznotif.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arznotif.app.data.AlertStore
import com.arznotif.app.data.PriceRepository
import kotlinx.coroutines.flow.first
import java.util.Locale

class PriceCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = PriceRepository()
            val store = AlertStore(applicationContext)

            val pricesResult = repo.fetchAllPrices()
            if (pricesResult.isFailure) return Result.retry()

            val prices = pricesResult.getOrNull()?.items ?: return Result.success()
            val alerts = store.alertsFlow.first().filter { it.enabled }

            for (alert in alerts) {
                val item = prices.find { it.id == alert.assetId } ?: continue
                val current = item.priceToman

                val above = alert.notifyAbove && current >= alert.targetPrice
                val below = alert.notifyBelow && current <= alert.targetPrice
                val percentHit = alert.percentMove?.let { pct ->
                    val change = item.changePercent ?: 0.0
                    kotlin.math.abs(change) >= pct
                } ?: false

                if (above || below || percentHit) {
                    val direction = if (above) "بالا رفت" else "پایین آمد"
                    val formatted = String.format("%,.0f", current)
                    NotificationHelper.showAlert(
                        applicationContext,
                        title = "هشدار ${item.nameFa}",
                        message = if (percentHit) "نوسان ${item.nameFa} به ${String.format(Locale.US, "%.2f", item.changePercent ?: 0.0)}% رسید • قیمت ${formatted} تومان" else "قیمت ${item.nameFa} $direction و به $formatted تومان رسید (هدف: ${String.format("%,.0f", alert.targetPrice)})",
                        notificationId = alert.assetId.hashCode()
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
