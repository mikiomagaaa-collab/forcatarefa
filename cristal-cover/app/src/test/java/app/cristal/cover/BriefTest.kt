package app.cristal.cover

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.cristal.cover.data.brief.BriefContract
import app.cristal.cover.data.brief.ReminderRepository
import app.cristal.cover.data.calendar.AgendaEvent
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.brief.BriefConfirmSheet
import app.cristal.cover.ui.brief.BriefDraft
import app.cristal.cover.ui.brief.BriefSheetActions
import app.cristal.cover.ui.brief.BriefSheetLogic
import app.cristal.cover.ui.launcher.NowBriefData
import app.cristal.cover.ui.launcher.NowBriefLogic
import app.cristal.cover.data.brief.Reminder
import app.cristal.cover.ui.theme.CristalTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class BriefTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val zone = ZoneId.systemDefault()

    private fun millis(date: LocalDate, time: LocalTime) = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `contrato valida o que chega do teclado`() {
        val now = System.currentTimeMillis()
        val s = BriefSuggestion(7, "Show", now + 3_600_000, dateConfirmed = false, kind = "event", sourceText = "às 7 horas da noite")
        val back = BriefContract.parse(BriefContract.fill(Intent(BriefContract.ACTION_SUGGEST), s), now)!!
        assertEquals(s, back)
        // Sem título, tipo desconhecido ou data absurda: recusado ou sem data.
        assertNull(BriefContract.parse(Intent().putExtra(BriefContract.EXTRA_KIND, "event"), now))
        assertNull(BriefContract.parse(Intent().putExtra(BriefContract.EXTRA_TITLE, "Oi").putExtra(BriefContract.EXTRA_KIND, "hack"), now))
        val far = BriefContract.parse(
            Intent().putExtra(BriefContract.EXTRA_TITLE, "x".repeat(200)).putExtra(BriefContract.EXTRA_KIND, "task").putExtra(BriefContract.EXTRA_WHEN, now + 900L * 86_400_000L),
            now,
        )!!
        assertNull(far.whenMillis)
        assertEquals(60, far.title.length)
    }

    @Test
    fun `lembretes avisam uma vez repetem e adiam`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ReminderRepository(context, json, scope)
        val now = System.currentTimeMillis()
        val later = repo.create("Show", now + 3_600_000, alarm = true)
        assertNull("cedo demais", repo.fire(later.id))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue("agendou o aviso", alarms.any { it.triggerAtMs == now + 3_600_000 })

        val due = repo.create("Remédio", now - 30_000)
        assertNotNull(repo.fire(due.id))
        assertNull("não avisa duas vezes", repo.fire(due.id))

        val weekly = repo.create("Aula", now - 30_000, weekly = true)
        assertNotNull(repo.fire(weekly.id))
        val next = repo.reminders.value.first { it.id == weekly.id }
        assertTrue("passou para a próxima semana", next.at!! > now + 6 * 86_400_000L)

        repo.snooze(due.id, 10)
        val snoozed = repo.reminders.value.first { it.id == due.id }
        assertTrue(snoozed.at!! >= now + 9 * 60_000L && snoozed.firedAt == null)

        val task = repo.create("Entregar o trabalho", null, kind = "task")
        repo.complete(task.id)
        assertTrue(repo.reminders.value.first { it.id == task.id }.done)

        // Fica guardado: outra instância lê o mesmo arquivo.
        val again = ReminderRepository(context, json, scope)
        again.rescheduleAll()
        assertEquals(repo.reminders.value.map { it.id }.toSet(), again.reminders.value.map { it.id }.toSet())
        assertEquals(
            millis(LocalDate.of(2026, 11, 23), LocalTime.of(8, 0)),
            ReminderRepository.nextWeek(millis(LocalDate.of(2026, 11, 9), LocalTime.of(8, 0)), millis(LocalDate.of(2026, 11, 16), LocalTime.of(10, 0))),
        )
    }

    @Test
    fun `linha do tempo e resumo do now brief`() {
        val now = millis(LocalDate.of(2026, 11, 16), LocalTime.of(10, 0))
        val events = listOf(
            AgendaEvent(1, "Reunião", now + 3_600_000, now + 7_200_000, false, 0, "Sala 12"),
            AgendaEvent(2, "Feriado", millis(LocalDate.of(2026, 11, 17), LocalTime.MIDNIGHT), millis(LocalDate.of(2026, 11, 18), LocalTime.MIDNIGHT), true, 0, ""),
            AgendaEvent(3, "Longe", now + 10 * 86_400_000L, now + 10 * 86_400_000L + 1, false, 0, ""),
        )
        val reminders = listOf(
            Reminder(10, "Show", now + 9 * 3_600_000L, now),
            Reminder(11, "Entregar o trabalho", null, now, kind = "task"),
        )
        val timeline = NowBriefLogic.timeline(events, reminders, now)
        assertEquals(listOf("Feriado", "Reunião", "Show"), timeline.map { it.title })
        assertNotNull(timeline.last().reminder)
        assertEquals(listOf("Entregar o trabalho"), NowBriefLogic.tasks(reminders).map { it.title })
        assertEquals("2 compromissos hoje · 1 tarefa", NowBriefLogic.summary(NowBriefData(reminders = reminders), events, now, zone))
        assertEquals("Nada marcado por enquanto.", NowBriefLogic.summary(NowBriefData(), emptyList(), now, zone))
    }

    @Test
    fun `folha de confirmacao`() {
        val today = LocalDate.now()
        val at = millis(today.plusDays(1), LocalTime.of(19, 0))
        val suggestion = BriefSuggestion(5, "Show", at, dateConfirmed = false, kind = "event", sourceText = "às 7 horas da noite", timeConfirmed = true)
        var draft: BriefDraft? = null
        rule.setContent {
            CristalTheme(settings = Settings()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF2B3550))) {
                    BriefConfirmSheet(
                        suggestion = suggestion,
                        alarmDefault = false,
                        notificationsOk = false,
                        exactOk = false,
                        created = false,
                        actions = BriefSheetActions(
                            onCreate = { draft = it },
                            onLater = {},
                            onPickDate = { _, _ -> },
                            onPickTime = { _, _ -> },
                            onAllowNotifications = {},
                            onAllowExact = {},
                            onOpenMap = {},
                        ),
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Compromisso identificado").assertExists()
        rule.onNodeWithText("Confira o dia: a mensagem não dizia com certeza.").assertExists()
        rule.onNodeWithText("Permitir").assertExists()
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "52-info-brief-confirmacao.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Criar lembrete").performClick()
        val d = draft!!
        assertEquals(at, d.at)
        assertEquals("Show", d.title)
        assertFalse(d.alarm)
        assertEquals(millis(today, LocalTime.of(9, 0)), BriefSheetLogic.at(today, null, zone))
        assertTrue(BriefSheetLogic.allowsNoDate("task") && !BriefSheetLogic.allowsNoDate("event"))
    }
}
