package app.lily.shared.brief

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Os textos do Info Brief, iguais no teclado, na L.I.L.Y AI e no Cristal. */
object BriefText {
    private val pt = Locale.forLanguageTag("pt-BR")

    /** "Show · Hoje 19:00" (com "?" quando a data não foi dita com certeza). */
    fun summary(c: BriefCandidate, today: LocalDate = LocalDate.now()): String {
        val at = c.at ?: return c.title
        val day = when (at.toLocalDate()) {
            today -> "Hoje"
            today.plusDays(1) -> "Amanhã"
            else -> DateTimeFormatter.ofPattern("EEE d/M", pt).format(at).replaceFirstChar { it.uppercase() }
        }
        val time = if (c.timeConfirmed || c.kind == BriefKind.EVENT) " " + DateTimeFormatter.ofPattern("HH:mm", pt).format(at) else ""
        return "${c.title} · $day$time" + if (c.dateConfirmed) "" else "?"
    }

    fun found(kind: BriefKind): String = when (kind) {
        BriefKind.TASK -> "Tarefa identificada"
        BriefKind.PLACE -> "Lugar identificado"
        BriefKind.RECURRING -> "Rotina identificada"
        BriefKind.TRIP -> "Viagem identificada"
        BriefKind.EVENT -> "Compromisso identificado"
    }

    fun action(kind: BriefKind): String = when (kind) {
        BriefKind.TASK -> "Adicionar tarefa"
        BriefKind.TRIP -> "Organizar viagem"
        else -> "Criar lembrete"
    }
}
