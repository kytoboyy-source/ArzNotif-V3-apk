package com.arznotif.app.data

data class PricePoint(val time: Long, val value: Double)

data class PriceItem(
    val id: String,
    val nameFa: String,
    val nameEn: String,
    val priceToman: Double,
    val priceUsd: Double? = null,
    val changePercent: Double? = null,
    val unit: String = "تومان",
    val iconEmoji: String = "💰",
    val lastUpdate: Long = System.currentTimeMillis(),
    val source: String = "TGJU",
    val history: List<PricePoint> = emptyList()
)

data class AlertSetting(
    val assetId: String,
    val targetPrice: Double,
    val enabled: Boolean = true,
    val notifyAbove: Boolean = true,
    val notifyBelow: Boolean = true,
    val percentMove: Double? = null,
    val sound: Boolean = true,
    val vibration: Boolean = true
)

data class AppPrices(
    val items: List<PriceItem>,
    val lastFetch: Long = System.currentTimeMillis(),
    val sourceStatus: String = "TGJU"
)
