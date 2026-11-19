package app.cristal.cover.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
@Immutable
data class DayForecast(val date: String, val code: Int, val minC: Double, val maxC: Double)

@Serializable
@Immutable
data class WeatherData(
    val place: String,
    val lat: Double,
    val lon: Double,
    val tempC: Double,
    val feelsLikeC: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Double,
    val days: List<DayForecast>,
    val fetchedAt: Long,
)

@Immutable
data class CityResult(val name: String, val region: String, val lat: Double, val lon: Double) {
    val display: String get() = if (region.isBlank()) name else "$name, $region"
}

enum class WeatherStatus { NO_PLACE, LOADING, READY, ERROR }

@Immutable
data class WeatherState(
    val status: WeatherStatus = WeatherStatus.NO_PLACE,
    val data: WeatherData? = null,
)

/**
 * Clima via Open-Meteo (gratuito, sem chave e sem cadastro). A única coisa enviada é a
 * latitude/longitude arredondada do lugar escolhido. O resultado fica em cache por 30 minutos.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
    @AppScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var cacheLoaded = false

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Atualiza se o cache estiver velho (ou sempre, com [force]). Nunca lança. */
    fun refresh(force: Boolean = false) {
        scope.launch {
            mutex.withLock { refreshLocked(force) }
        }
    }

    private suspend fun refreshLocked(force: Boolean) {
        val settings = settingsRepository.current()
        if (!cacheLoaded) {
            cacheLoaded = true
            val cached = safely("ler cache do clima") {
                settingsRepository.getString(PrefKeys.WEATHER_CACHE)?.let { json.decodeFromString<WeatherData>(it) }
            }
            if (cached != null) _state.value = WeatherState(WeatherStatus.READY, cached)
        }

        val place: Triple<String, Double, Double>? = when {
            settings.weatherUseLocation && hasLocationPermission() -> lastKnownLocation()?.let {
                Triple("", it.first, it.second)
            } ?: manualPlace(settings.weatherCity, settings.weatherLat, settings.weatherLon)

            else -> manualPlace(settings.weatherCity, settings.weatherLat, settings.weatherLon)
        }
        if (place == null) {
            if (_state.value.data == null) _state.value = WeatherState(WeatherStatus.NO_PLACE)
            return
        }

        val current = _state.value.data
        val samePlace = current != null &&
            kotlin.math.abs(current.lat - place.second) < 0.05 && kotlin.math.abs(current.lon - place.third) < 0.05
        val fresh = current != null && System.currentTimeMillis() - current.fetchedAt < CACHE_MS
        if (!force && samePlace && fresh) return

        if (!samePlace) _state.value = WeatherState(WeatherStatus.LOADING, null)
        val fetched = withContext(io) { fetch(place.first, place.second, place.third) }
        if (fetched != null) {
            _state.value = WeatherState(WeatherStatus.READY, fetched)
            safely("gravar cache do clima") {
                settingsRepository.set(PrefKeys.WEATHER_CACHE, json.encodeToString(WeatherData.serializer(), fetched))
            }
        } else if (_state.value.data == null || !samePlace) {
            _state.value = WeatherState(WeatherStatus.ERROR, null)
        }
    }

    private fun manualPlace(city: String, lat: Double, lon: Double): Triple<String, Double, Double>? =
        if (lat.isNaN() || lon.isNaN()) null else Triple(city, lat, lon)

    @Suppress("MissingPermission")
    private fun lastKnownLocation(): Pair<Double, Double>? = safely("ler última localização") {
        val lm = context.getSystemService(LocationManager::class.java) ?: return@safely null
        var best: android.location.Location? = null
        for (provider in lm.getProviders(true)) {
            val loc = safely("provedor de localização $provider") { lm.getLastKnownLocation(provider) } ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        // Duas casas decimais (~1 km) são suficientes para o clima e preservam a privacidade.
        best?.let { Pair(Math.round(it.latitude * 100) / 100.0, Math.round(it.longitude * 100) / 100.0) }
    }

    private fun fetch(place: String, lat: Double, lon: Double): WeatherData? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4"
        val root = json.parseToJsonElement(httpGet(url)).jsonObject
        val current = root["current"]!!.jsonObject
        val daily = root["daily"]?.jsonObject
        val days = ArrayList<DayForecast>()
        if (daily != null) {
            val dates = daily.array("time")
            val codes = daily.array("weather_code")
            val max = daily.array("temperature_2m_max")
            val min = daily.array("temperature_2m_min")
            for (i in 0 until (dates?.size ?: 0)) {
                days += DayForecast(
                    date = dates!![i].jsonPrimitive.content,
                    code = codes?.getOrNull(i)?.jsonPrimitive?.intOrNull ?: 0,
                    minC = min?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    maxC = max?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                )
            }
        }
        WeatherData(
            place = place,
            lat = lat,
            lon = lon,
            tempC = current.double("temperature_2m"),
            feelsLikeC = current.double("apparent_temperature"),
            code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
            isDay = (current["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1,
            humidity = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull ?: 0,
            windKmh = current.double("wind_speed_10m"),
            days = days,
            fetchedAt = System.currentTimeMillis(),
        )
    } catch (t: Throwable) {
        Logx.w("Falha ao buscar o clima", t)
        null
    }

    /** Busca cidades pelo nome. Devolve lista vazia em caso de erro ou sem internet. */
    suspend fun searchCity(query: String): List<CityResult> = withContext(io) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=pt&format=json&name=" +
                URLEncoder.encode(q, "UTF-8")
            val root = json.parseToJsonElement(httpGet(url)).jsonObject
            val results = root["results"] as? JsonArray ?: return@withContext emptyList()
            results.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val lat = o["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lon = o["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val region = listOfNotNull(
                    o["admin1"]?.jsonPrimitive?.content,
                    o["country_code"]?.jsonPrimitive?.content,
                ).joinToString(" · ")
                CityResult(name, region, lat, lon)
            }
        } catch (t: Throwable) {
            Logx.w("Falha ao buscar cidades", t)
            emptyList()
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.double(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun JsonObject.array(key: String): JsonArray? = safely("ler lista $key") { this[key]?.jsonArray }

    private companion object {
        const val CACHE_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 8_000
    }
}
