package app.cristal.cover.ui.widgets

import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.calendar.AgendaEvent
import app.cristal.cover.data.calendar.AgendaState
import app.cristal.cover.data.weather.DayForecast
import app.cristal.cover.data.weather.WeatherData
import app.cristal.cover.data.weather.WeatherState
import app.cristal.cover.data.weather.WeatherStatus
import java.time.LocalDate

/**
 * Dados de exemplo, só para as prévias da personalização e os testes de tela (a página avisa que
 * são de exemplo). Os widgets da tela inicial sempre usam os dados reais do aparelho.
 */
object WidgetSamples {
    fun data(now: Long = System.currentTimeMillis()): WidgetData {
        val today = LocalDate.now()
        val days = (0 until 8).map { i ->
            DayForecast(today.plusDays(i.toLong()).toString(), listOf(1, 2, 61, 3, 0, 80, 2, 1)[i], 17.0 + i % 3, 26.0 + (i * 2) % 5)
        }
        return WidgetData(
            battery = BatteryState(percent = 76, charging = true),
            weather = WeatherState(
                WeatherStatus.READY,
                WeatherData("Recife", -8.05, -34.9, 27.4, 29.0, 2, true, 71, 14.0, days, now),
            ),
            agenda = AgendaState(
                hasPermission = true,
                events = listOf(
                    AgendaEvent(1, "Reunião do projeto", now + 50 * 60_000L, now + 110 * 60_000L, false, 0xFF0A84FF.toInt(), ""),
                    AgendaEvent(2, "Academia", now + 4 * 3_600_000L, now + 5 * 3_600_000L, false, 0xFF30D158.toInt(), ""),
                    AgendaEvent(3, "Jantar com a família", now + 7 * 3_600_000L, now + 9 * 3_600_000L, false, 0xFFFF9F0A.toInt(), ""),
                ),
            ),
            notificationAccess = true,
            lily = true,
            brief = BriefSummary(
                items = listOf(BriefItem("Levar o notebook", now + 30 * 60_000L, true), BriefItem("Pagar a conta de luz", now + 26 * 3_600_000L, true)),
                suggestions = 2,
            ),
        )
    }
}
