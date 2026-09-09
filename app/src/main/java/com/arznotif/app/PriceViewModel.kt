package com.arznotif.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arznotif.app.data.AlertSetting
import com.arznotif.app.data.AlertStore
import com.arznotif.app.data.AppPrices
import com.arznotif.app.data.PriceItem
import com.arznotif.app.data.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.TimeUnit

class PriceViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PriceRepository()
    private val alertStore = AlertStore(application)

    private val _prices = MutableStateFlow<AppPrices?>(null)
    val prices: StateFlow<AppPrices?> = _prices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val alerts: StateFlow<List<AlertSetting>> = alertStore.alertsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
        scheduleWorker()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repo.fetchAllPrices()
            result.onSuccess { app ->
                _prices.value = app
                val targets = app.items.take(12)
                viewModelScope.launch {
                    val enriched = targets.map { item ->
                        async {
                            val divide = item.id.startsWith("price_") || item.id in setOf("geram18","geram24","abshodeh","abshodeh_mam","sekkeh","bahar","nim","rob","gerami","silver_999")
                            item.id to repo.fetchHistory(item.id, divide)
                        }
                    }.awaitAll().toMap()
                    _prices.value = _prices.value?.copy(items = _prices.value!!.items.map { it.copy(history = (enriched[it.id] ?: it.history).takeLast(60)) })
                }
            }
                .onFailure { _error.value = it.message ?: "خطا در دریافت قیمت‌ها" }
            _isLoading.value = false
        }
    }

    fun saveAlert(alert: AlertSetting) {
        viewModelScope.launch {
            alertStore.updateAlert(alert)
            BackgroundMonitorService.start(getApplication())
        }
    }

    fun removeAlert(assetId: String) {
        viewModelScope.launch {
            val current = alerts.value.filter { it.assetId != assetId }
            alertStore.saveAlerts(current)
            if (current.isEmpty()) BackgroundMonitorService.stop(getApplication())
        }
    }

    private fun scheduleWorker() {
        val work = PeriodicWorkRequestBuilder<PriceCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "price_check",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    fun getAlertFor(assetId: String): AlertSetting? {
        return alerts.value.find { it.assetId == assetId }
    }
}
