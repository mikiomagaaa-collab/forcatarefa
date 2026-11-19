package app.cristal.cover.ui.brief

import android.Manifest
import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.cristal.cover.core.Logx
import app.cristal.cover.data.brief.BriefContract
import app.cristal.cover.data.brief.BriefRepository
import app.cristal.cover.data.brief.ReminderNotifier
import app.cristal.cover.data.brief.ReminderScheduler
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.theme.CristalTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Confirmação do Info Brief: uma folha sobre o app em uso para conferir título, data, hora e ação
 * antes de agendar. É também onde o Cristal pede, só quando preciso, a permissão de notificações e
 * mostra o atalho para "Alarmes e lembretes".
 */
@AndroidEntryPoint
class BriefConfirmActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var brief: BriefRepository

    private var notificationsOk by mutableStateOf(true)
    private var exactOk by mutableStateOf(true)
    private var created by mutableStateOf(false)
    private var pending: BriefDraft? = null

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOk = granted
        // Cria de qualquer jeito: sem notificações, o lembrete fica no Now Brief.
        pending?.let(::create)
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val suggestion = BriefContract.parse(intent, System.currentTimeMillis())
        if (suggestion == null) {
            finish()
            return
        }
        refreshPermissions()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            CristalTheme(settings = settings) {
                BackHandler { later(suggestion) }
                BriefConfirmSheet(
                    suggestion = suggestion,
                    alarmDefault = settings.briefAlarm,
                    notificationsOk = notificationsOk,
                    exactOk = exactOk,
                    created = created,
                    actions = BriefSheetActions(
                        onCreate = ::confirm,
                        onLater = { later(suggestion) },
                        onPickDate = ::pickDate,
                        onPickTime = ::pickTime,
                        onAllowNotifications = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onAllowExact = ::openExactAlarms,
                        onOpenMap = ::openMap,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        notificationsOk = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            ReminderNotifier.enabled(this)
        exactOk = ReminderScheduler.canExact(this)
    }

    private fun confirm(draft: BriefDraft) {
        if (draft.at != null && !notificationsOk && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending = draft
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        create(draft)
    }

    private fun create(draft: BriefDraft) {
        lifecycleScope.launch {
            brief.create(draft.suggestion, draft.title, draft.at, draft.alarm, draft.weekly)
            created = true
            delay(900)
            finish()
        }
    }

    private fun later(suggestion: BriefSuggestion) {
        if (!created) brief.later(suggestion)
        finish()
    }

    private fun pickDate(current: LocalDate?, onPicked: (LocalDate) -> Unit) {
        val d = current ?: LocalDate.now()
        DatePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth)
            .apply { datePicker.minDate = System.currentTimeMillis() - 1_000 }
            .show()
    }

    private fun pickTime(current: LocalTime?, onPicked: (LocalTime) -> Unit) {
        val t = current ?: LocalTime.of(9, 0)
        TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h, m -> onPicked(LocalTime.of(h, m)) }, t.hour, t.minute, true).show()
    }

    private fun openExactAlarms() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }.onFailure { Logx.w("Sem a tela de Alarmes e lembretes", it) }
    }

    private fun openMap(place: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Logx.w("Nenhum app de mapas", it) }
    }

    companion object {
        /** Abre a confirmação na tela em que a pessoa está (interna ou externa). */
        fun open(context: Context, suggestion: BriefSuggestion, displayId: Int) {
            val intent = BriefContract.fill(Intent(context, BriefConfirmActivity::class.java), suggestion)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val display = if (displayId >= 0) displayId else Display.DEFAULT_DISPLAY
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display)
            runCatching { context.startActivity(intent, options.toBundle()) }
                .onFailure {
                    Logx.w("Não deu para abrir a confirmação na tela $display", it)
                    runCatching { context.startActivity(intent) }
                }
        }
    }
}
