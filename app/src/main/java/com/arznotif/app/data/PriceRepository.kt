package com.arznotif.app.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/** TGJU-only market adapter. Live prices come from TGJU's live ajax feed.
 *  The feed is intentionally polled no faster than 10 seconds; the source itself
 *  updates on its own schedule, so a one-second UI timer must not be confused with
 *  one-second market data.
 */
class PriceRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val liveUrl = "https://call5.tgju.org/ajax.json"
    private val historyBase = "https://api.tgju.org/v1/market/indicator/summary-table-data/"

    private data class Meta(val id: String, val fa: String, val en: String, val emoji: String, val kind: String)

    private val catalog = listOf(
        Meta("price_dollar_rl", "دلار آمریکا", "USD", "💵", "fiat"),
        Meta("price_eur", "یورو", "EUR", "💶", "fiat"),
        Meta("price_gbp", "پوند انگلیس", "GBP", "💷", "fiat"),
        Meta("price_aed", "درهم امارات", "AED", "🇦🇪", "fiat"),
        Meta("price_cad", "دلار کانادا", "CAD", "🇨🇦", "fiat"),
        Meta("price_aud", "دلار استرالیا", "AUD", "🇦🇺", "fiat"),
        Meta("price_chf", "فرانک سوئیس", "CHF", "🇨🇭", "fiat"),
        Meta("price_jpy", "ین ژاپن", "JPY", "🇯🇵", "fiat"),
        Meta("price_try", "لیر ترکیه", "TRY", "🇹🇷", "fiat"),
        Meta("price_cny", "یوان چین", "CNY", "🇨🇳", "fiat"),
        Meta("price_inr", "روپیه هند", "INR", "🇮🇳", "fiat"),
        Meta("price_rub", "روبل روسیه", "RUB", "🇷🇺", "fiat"),
        Meta("price_iqd", "دینار عراق", "IQD", "🇮🇶", "fiat"),
        Meta("price_afn", "افغانی", "AFN", "🇦🇫", "fiat"),
        Meta("price_azn", "منات آذربایجان", "AZN", "🇦🇿", "fiat"),
        Meta("price_gel", "لاری گرجستان", "GEL", "🇬🇪", "fiat"),
        Meta("price_kwd", "دینار کویت", "KWD", "🇰🇼", "fiat"),
        Meta("price_qar", "ریال قطر", "QAR", "🇶🇦", "fiat"),
        Meta("price_sar", "ریال عربستان", "SAR", "🇸🇦", "fiat"),
        Meta("price_omr", "ریال عمان", "OMR", "🇴🇲", "fiat"),
        Meta("price_bhd", "دینار بحرین", "BHD", "🇧🇭", "fiat"),
        Meta("price_myr", "رینگیت مالزی", "MYR", "🇲🇾", "fiat"),
        Meta("price_thb", "بات تایلند", "THB", "🇹🇭", "fiat"),
        Meta("geram18", "طلای ۱۸ عیار", "Gold 18K / g", "🥇", "gold"),
        Meta("geram24", "طلای ۲۴ عیار", "Gold 24K / g", "🟨", "gold"),
        Meta("abshodeh", "آبشده نقدی", "Melted Gold", "🟨", "gold"),
        Meta("abshodeh_mam", "آبشده معاملاتی", "Melted Gold Trading", "🟨", "gold"),
        Meta("sekkeh", "سکه امامی", "Emami Coin", "🪙", "gold"),
        Meta("bahar", "سکه بهار آزادی", "Bahar Azadi", "🪙", "gold"),
        Meta("nim", "نیم سکه", "Half Coin", "🪙", "gold"),
        Meta("rob", "ربع سکه", "Quarter Coin", "🪙", "gold"),
        Meta("gerami", "سکه گرمی", "Gram Coin", "🪙", "gold"),
        Meta("ons", "اونس جهانی طلا", "Gold Ounce", "🌐", "goldusd"),
        Meta("silver_999", "گرم نقره ۹۹۹", "Silver 999 / g", "🥈", "gold"),
        Meta("ons_silver", "اونس نقره", "Silver Ounce", "🥈", "goldusd"),
        Meta("platinum", "اونس پلاتین", "Platinum Ounce", "⚪", "goldusd"),
        Meta("palladium", "اونس پالادیوم", "Palladium Ounce", "⚪", "goldusd"),
        Meta("bitcoin", "بیت‌کوین", "BTC", "₿", "crypto"),
        Meta("ethereum", "اتریوم", "ETH", "Ξ", "crypto"),
        Meta("tether", "تتر", "USDT", "₮", "crypto"),
        Meta("solana", "سولانا", "SOL", "◎", "crypto"),
        Meta("binancecoin", "بایننس کوین", "BNB", "🟡", "crypto"),
        Meta("cardano", "کاردانو", "ADA", "₳", "crypto"),
        Meta("xrp", "ریپل", "XRP", "✕", "crypto"),
        Meta("dogecoin", "دوج‌کوین", "DOGE", "Ð", "crypto"),
        Meta("tron", "ترون", "TRX", "⚡", "crypto"),
        Meta("polkadot", "پولکادات", "DOT", "●", "crypto")
    )

    suspend fun fetchAllPrices(): Result<AppPrices> = withContext(Dispatchers.IO) {
        try {
            val root = fetchJsonObject(liveUrl) ?: return@withContext Result.failure(Exception("TGJU در دسترس نیست"))
            val current = root.getAsJsonObject("current") ?: root
            val usdObj = findLiveObject(current, "price_dollar_rl")
            val usd = usdObj?.let { liveNumber(it, "p", "price", "value", "close") }
            val now = System.currentTimeMillis()
            val items = catalog.mapNotNull { meta ->
                val obj = findLiveObject(current, meta.id) ?: return@mapNotNull null
                val raw = liveNumber(obj, "p", "price", "value", "close") ?: return@mapNotNull null
                val price = if (meta.kind == "fiat" || meta.kind == "gold") raw / 10.0 else raw
                val priceUsd = when (meta.kind) {
                    "goldusd", "crypto" -> raw
                    else -> if (usd != null && usd > 0) price / (usd / 10.0) else null
                }
                val change = liveNumber(obj, "dp", "change_pct", "changePercent")
                PriceItem(meta.id, meta.fa, meta.en, price, priceUsd, change, iconEmoji = meta.emoji, lastUpdate = liveTime(obj, now), source = "TGJU")
            }.toMutableList()

            // TGJU's live feed contains many more currency instruments than the curated list above.
            // Add unknown price_* instruments too, so the app never silently hides a market returned by TGJU.
            current.entrySet().forEach { (key, element) ->
                if (key.startsWith("price_") && items.none { it.id == key } && element.isJsonObject) {
                    val obj = element.asJsonObject
                    val raw = liveNumber(obj, "p", "price", "value", "close") ?: return@forEach
                    val change = liveNumber(obj, "dp", "change_pct", "changePercent")
                    val code = key.removePrefix("price_").uppercase(Locale.US)
                    items += PriceItem(key, code, code, raw / 10.0, if (usd != null && usd > 0) (raw / 10.0) / (usd / 10.0) else null, change, iconEmoji = "💱", lastUpdate = liveTime(obj, now), source = "TGJU")
                }
            }
            if (items.isEmpty()) return@withContext Result.failure(Exception("داده زنده TGJU خالی است"))
            Result.success(AppPrices(items.sortedWith(compareBy<PriceItem> { kindOrder(it.id) }.thenBy { it.nameFa }), now, "TGJU • Live"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchHistory(profileId: String, divideByTen: Boolean = true): List<PricePoint> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$historyBase$profileId?length=30&convert_to_ad=1"
            val root = fetchJsonObject(url) ?: return@runCatching emptyList()
            val arr = root.getAsJsonArray("data") ?: return@runCatching emptyList()
            arr.mapNotNull { row ->
                if (!row.isJsonArray) return@mapNotNull null
                val cells = row.asJsonArray
                val close = cells.getOrNull(3)?.asString?.cleanNumber()?.toDoubleOrNull() ?: return@mapNotNull null
                val date = cells.getOrNull(6)?.asString ?: return@mapNotNull null
                val ts = parseDate(date) ?: return@mapNotNull null
                PricePoint(ts, if (divideByTen) close / 10.0 else close)
            }.reversed()
        }.getOrDefault(emptyList())
    }

    private fun kindOrder(id: String): Int = when {
        id.startsWith("price_") -> 0
        id in setOf("geram18", "geram24", "abshodeh", "abshodeh_mam", "sekkeh", "bahar", "nim", "rob", "gerami", "silver_999") -> 1
        id.startsWith("ons") || id == "platinum" || id == "palladium" -> 2
        else -> 3
    }

    private fun fetchJsonObject(url: String): JsonObject? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "ArzNotif/3.0 Android").header("Accept", "application/json").build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            JsonParser.parseString(text).asJsonObject
        }
    }.getOrNull()

    private fun findLiveObject(current: JsonObject, id: String): JsonObject? {
        if (current.has(id) && current.get(id).isJsonObject) return current.getAsJsonObject(id)
        val aliases = mapOf(
            "geram18" to listOf("geram18", "gold18", "price_geram18"),
            "geram24" to listOf("geram24", "price_geram24"),
            "sekkeh" to listOf("sekkeh", "sekee", "coin_emami"),
            "abshodeh_mam" to listOf("abshodeh_mam", "abshodeh_moamele"),
            "ons" to listOf("ons", "xau"),
            "bitcoin" to listOf("bitcoin", "btc"),
            "ethereum" to listOf("ethereum", "eth"),
            "tether" to listOf("tether", "usdt"),
            "binancecoin" to listOf("binancecoin", "bnb"),
            "cardano" to listOf("cardano", "ada"),
            "dogecoin" to listOf("dogecoin", "doge"),
            "polkadot" to listOf("polkadot", "dot")
        )[id].orEmpty()
        aliases.firstOrNull { current.has(it) && current.get(it).isJsonObject }?.let { return current.getAsJsonObject(it) }
        return null
    }

    private fun liveNumber(obj: JsonObject, vararg names: String): Double? = names.asSequence().mapNotNull { n ->
        if (!obj.has(n) || obj.get(n).isJsonNull) null else obj.get(n).asString.cleanNumber().toDoubleOrNull()
    }.firstOrNull()

    private fun liveTime(obj: JsonObject, fallback: Long): Long {
        val ts = obj.get("ts")?.asString ?: return fallback
        return runCatching { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(ts)?.time ?: fallback }.getOrDefault(fallback)
    }

    private fun parseDate(s: String): Long? = runCatching { java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(s.trim())?.time }.getOrNull()
    private fun String.cleanNumber(): String = replace(",", "").replace("٬", "").replace("٫", ".").trim()
    private fun JsonElement?.asStringOrNull(): String? = runCatching { this?.asString }.getOrNull()
    private fun com.google.gson.JsonArray.getOrNull(i: Int): JsonElement? = if (i in 0 until size()) get(i) else null
}
