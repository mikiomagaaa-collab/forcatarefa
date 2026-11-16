package app.lily.keyboard.brief

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.serialization.Serializable

/** O que o Info Brief sabe reconhecer. [wire] é o nome combinado com o Cristal. */
@Serializable
enum class BriefKind(val wire: String, val label: String, val example: String) {
    EVENT("event", "Compromissos e horários", "“Vou ao show às 7 da noite”"),
    TASK("task", "Tarefas", "“Não posso esquecer de entregar o trabalho”"),
    PLACE("place", "Lugares", "“Preciso passar no shopping amanhã”"),
    RECURRING("recurring", "Rotinas", "“Tenho aula toda segunda-feira”"),
    TRIP("trip", "Viagens", "“Vou viajar no próximo mês”"),
}

/**
 * Um compromisso reconhecido. [start] e [end] marcam a expressão no texto analisado (para o
 * destaque azul); [at] é o momento resolvido (null numa tarefa sem data).
 */
@Immutable
data class BriefCandidate(
    val kind: BriefKind,
    val title: String,
    val at: LocalDateTime?,
    val dateConfirmed: Boolean,
    val timeConfirmed: Boolean,
    val weekly: Boolean,
    val place: String?,
    val expression: String,
    val start: Int,
    val end: Int,
) {
    /** Chave para não sugerir duas vezes a mesma coisa (pelo sentido, não pelo texto). */
    val key: String get() = "${kind.wire}|${title.lowercase(Locale.ROOT)}|${at?.toLocalDate()}|${if (timeConfirmed) at?.toLocalTime() else ""}"
}

/**
 * O reconhecedor do Info Brief. Roda no teclado, no aparelho, sem rede: lê só a última frase do
 * campo em uso e procura um horário, uma data, uma tarefa, um lugar, uma rotina ou uma viagem.
 * Frases hipotéticas ("se eu for…", "talvez"), perguntas, negações e coisas no passado ficam de
 * fora: a ideia é sugerir pouco e acertar.
 */
object BriefDetector {
    private const val NUMBER = "(\\d{1,2}|uma|um|duas|dois|tr[êe]s|quatro|cinco|seis|sete|oito|nove|dez|onze|doze)"
    private val numberWords = mapOf(
        "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "três" to 3, "tres" to 3, "quatro" to 4, "cinco" to 5,
        "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10, "onze" to 11, "doze" to 12,
    )

    // --- Horários ---------------------------------------------------------------------------------
    /** "às 7 horas da noite", "às 19h", "7h30", "às 8 e meia", "lá pelas 9 da noite". */
    private val ptTime = Regex(
        "(?:\\b(às|as|pelas|umas|por volta das|lá pelas|la pelas|a partir das|até as|até às)\\s+)?" +
            "\\b$NUMBER(?:\\s*([:h])\\s*(\\d{2}))?(?:\\s*(hs|h|horas|hora)\\b)?" +
            "(?:\\s+e\\s+(meia|\\d{1,2}))?" +
            "(?:\\s+(?:da|de)\\s+(manh[ãa]|tarde|noite|madrugada))?",
    )
    private val ptNoonMidnight = Regex("\\b(?:ao\\s+|à\\s+|a\\s+)?(meio[- ]dia|meia[- ]noite)\\b")

    /** "at 7pm", "at 7:30 pm", "at 19:00", "7 o'clock". */
    private val enTime = Regex(
        "(?:\\b(at|around|by)\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.|o'?clock)?\\b",
    )

    /** Período solto: "hoje à noite", "amanhã de manhã". */
    private val ptPeriod = Regex("\\b(?:à|a|de|pela|na)\\s+(manh[ãa]|tarde|noite)\\b")
    private val enPeriod = Regex("\\b(?:in the|this)\\s+(morning|afternoon|evening)\\b|\\b(tonight)\\b")

    // --- Datas -----------------------------------------------------------------------------------
    private val ptToday = Regex("\\bhoje\\b")
    private val ptAfterTomorrow = Regex("\\bdepois de amanh[ãa]\\b")
    private val ptTomorrow = Regex("\\bamanh[ãa]\\b")
    private val ptWeekday = Regex("\\b(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)(s)?(?:[- ]feiras?)?\\b")
    private val ptDayOfMonth = Regex("\\bdia\\s+(\\d{1,2})\\b(?:\\s+de\\s+(janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro))?")
    private val ptMonthDate = Regex("\\b(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\b")
    private val numericDate = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
    private val ptNextWeek = Regex("\\b(?:semana que vem|pr[óo]xima semana)\\b")
    private val ptNextMonth = Regex("\\b(?:m[êe]s que vem|pr[óo]ximo m[êe]s)\\b")
    private val ptWeekend = Regex("\\b(?:fim|final) de semana\\b")
    private val enToday = Regex("\\b(today|tonight)\\b")
    private val enTomorrow = Regex("\\btomorrow\\b")
    private val enWeekday = Regex("\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(s)?\\b")
    private val enNextWeek = Regex("\\bnext week\\b")
    private val enNextMonth = Regex("\\bnext month\\b")
    private val enWeekend = Regex("\\b(?:this|next)? ?weekend\\b")

    private val months = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    // --- Filtros ---------------------------------------------------------------------------------
    private val hypothetical = Regex(
        "\\b(?:talvez|quem sabe|seria|será que|sera que|e se|imagina|imagine|hipoteticamente|supondo|caso|maybe|perhaps|would|could|if i|if we|if you|in case|suppose)\\b|" +
            "\\bse\\s+(?:eu|a gente|você|voce|vc|der|puder|não|nao|tiver|for|fosse|chover|sobrar|rolar|ele|ela|eles|elas|vocês)\\b",
    )
    private val past = Regex("\\b(?:ontem|anteontem|semana passada|m[êe]s passado|ano passado|fui|fomos|foi|teve|tivemos|aconteceu|viajei|viajamos|yesterday|last week|last month|went|was|were)\\b")
    private val rescheduled = Regex("\\b(?:remarcad[ao]s?|adiad[ao]s?|mudou para|passou para|ficou para|moved to|rescheduled)\\b")
    private val negated = Regex("\\b(?:não|nao|nunca|jamais)\\s+(?:vou|vamos|irei|iremos|vai|vão|tenho|temos|posso|podemos|consigo|dá|da|rola|marquei)\\b|\\b(?:won't|can't|cannot|not going)\\b")

    // --- Tarefas, lugares, viagens --------------------------------------------------------------
    /** Pistas fortes: viram tarefa mesmo sem data. */
    private val strongTask = Regex(
        "\\b(?:não posso (?:me )?esquecer de|nao posso (?:me )?esquecer de|não (?:posso )?esquecer de|nao esquecer de|não esqueça de|nao esqueca de|" +
            "preciso lembrar de|tenho que lembrar de|lembrar de|me lembra de|não deixar de|nao deixar de|" +
            "don't forget to|dont forget to|remember to|must not forget to)\\s+",
    )

    /** Pistas fracas: só valem junto com uma data ou um horário. */
    private val weakTask = Regex("\\b(?:preciso|tenho que|tenho de|vou ter que|devo|need to|have to|gotta)\\s+(?:de\\s+)?")
    private val placeVerb = Regex(
        "\\b(?:passar|ir|dar uma passada|dar um pulo)\\s+(?:lá\\s+)?(?:no|na|em|ao|à|a|pelo|pela|pro|pra|para o|para a)\\s+([a-zà-ú]+(?:\\s+[a-zà-ú]+)?)|" +
            "\\b(?:go|stop by|drop by|pass by)\\s+(?:to\\s+)?(?:the\\s+)?([a-z]+(?:\\s+[a-z]+)?)",
    )
    private val places = setOf(
        "shopping", "mercado", "supermercado", "farmácia", "farmacia", "banco", "padaria", "academia", "médico", "medico",
        "dentista", "escola", "faculdade", "trabalho", "correios", "correio", "posto", "hospital", "clínica", "clinica",
        "igreja", "parque", "praia", "cinema", "restaurante", "loja", "lotérica", "loterica", "cartório", "cartorio",
        "oficina", "salão", "salao", "barbearia", "aeroporto", "rodoviária", "rodoviaria", "feira", "açougue", "acougue",
        "pet shop", "petshop", "biblioteca", "prefeitura", "detran", "consultório", "consultorio", "laboratório", "laboratorio",
        "mall", "supermarket", "pharmacy", "bank", "bakery", "gym", "post office", "hospital", "school", "office", "airport", "library",
    )
    private val trip = Regex("\\b(?:vou|vamos|irei|iremos|minha|nossa|a)\\s+(?:viajar|viagem)\\b|\\bviajar\\b|\\b(?:going to travel|trip)\\b")
    private val tripTo = Regex("\\b(?:para|pra|to)\\s+([A-ZÀ-Ú][\\wÀ-ú]+(?:\\s+[A-ZÀ-Ú][\\wÀ-ú]+)?)")

    /** Verbos de compromisso: sem um evento nomeado, só eles fazem um horário virar sugestão. */
    private val commitment = Regex(
        "\\b(?:vou|vamos|irei|iremos|marquei|marcamos|combinei|combinamos|tenho|temos|te encontro|nos encontramos|começa|começo|" +
            "i'll|i will|we'll|we will|i have|we have|meet|meeting)\\b",
    )

    /** Eventos com nome próprio (título da sugestão). */
    private val events = linkedMapOf(
        "happy hour" to "Happy hour", "show" to "Show", "aula" to "Aula", "reunião" to "Reunião", "reuniao" to "Reunião",
        "consulta" to "Consulta", "prova" to "Prova", "apresentação" to "Apresentação", "apresentacao" to "Apresentação",
        "jantar" to "Jantar", "almoço" to "Almoço", "almoco" to "Almoço", "festa" to "Festa", "cinema" to "Cinema",
        "academia" to "Academia", "dentista" to "Dentista", "médico" to "Médico", "medico" to "Médico", "médica" to "Médica",
        "voo" to "Voo", "entrevista" to "Entrevista", "aniversário" to "Aniversário", "aniversario" to "Aniversário",
        "jogo" to "Jogo", "treino" to "Treino", "culto" to "Culto", "missa" to "Missa", "encontro" to "Encontro",
        "churrasco" to "Churrasco", "palestra" to "Palestra", "curso" to "Curso", "exame" to "Exame",
        "compromisso" to "Compromisso", "evento" to "Evento", "partida" to "Partida", "ensaio" to "Ensaio",
        "formatura" to "Formatura", "casamento" to "Casamento", "balada" to "Balada", "plantão" to "Plantão",
        "plantao" to "Plantão", "trabalho" to "Trabalho", "call" to "Call", "live" to "Live", "estreia" to "Estreia",
        "meeting" to "Meeting", "class" to "Class", "appointment" to "Appointment", "dinner" to "Dinner", "lunch" to "Lunch",
        "party" to "Party", "concert" to "Concert", "game" to "Game", "interview" to "Interview", "flight" to "Flight",
        "exam" to "Exam", "practice" to "Practice", "movie" to "Movie",
    )
    private val evening = setOf("show", "jantar", "festa", "cinema", "balada", "happy hour", "churrasco", "estreia", "dinner", "party", "concert", "movie")
    private val morning = setOf("aula", "escola", "trabalho", "prova", "exame", "plantão", "plantao", "class", "exam")

    /** Palavras que deixam a frase "pela metade" (não analisa no gatilho de pausa). */
    private val dangling = setOf("de", "da", "do", "das", "dos", "às", "as", "a", "ao", "no", "na", "para", "pra", "com", "e", "ou", "em", "por", "pelas", "umas", "at", "on", "the", "to", "in", "and")

    /** A frase parece terminada (não termina em "…às", "…da"). Usado pelo gatilho de pausa. */
    fun looksFinished(text: String): Boolean {
        val last = text.trimEnd().substringAfterLast(' ').lowercase(Locale.ROOT).trim(',', ';')
        return last.isNotEmpty() && last !in dangling
    }

    /**
     * Procura um compromisso na última frase de [text] (o texto antes do cursor). [now] é o
     * momento atual (para resolver "amanhã", "sexta", "às 7").
     */
    fun detect(text: String, now: LocalDateTime, kinds: Set<BriefKind> = BriefKind.entries.toSet()): BriefCandidate? {
        if (kinds.isEmpty() || text.isBlank()) return null
        val (offset, sentence) = lastSentence(text)
        if (sentence.length < 6) return null
        val lower = sentence.lowercase(Locale.ROOT)
        // Índices precisam coincidir com o texto original (para o destaque).
        if (lower.length != sentence.length) return null
        if (sentence.trimEnd().endsWith("?")) return null
        if (hypothetical.containsMatchIn(lower)) return null
        if (past.containsMatchIn(lower) && !rescheduled.containsMatchIn(lower)) return null
        val strong = strongTask.find(lower)
        if (strong == null && negated.containsMatchIn(lower)) return null

        val time = findTime(lower)
        val date = findDate(lower, now)
        val noun = findEvent(lower)
        val period = ptPeriod.find(lower)?.groupValues?.get(1) ?: enPeriod.find(lower)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        // Momento resolvido.
        val resolved = resolve(time, date, period, noun, now)

        // 1) Rotina: "toda segunda".
        if (date?.weekly == true && BriefKind.RECURRING in kinds) {
            val title = noun?.second ?: taskPhrase(sentence, lower, strong ?: weakTask.find(lower), time, date)?.let(::capitalize) ?: "Compromisso semanal"
            return build(BriefKind.RECURRING, title, resolved, true, time != null && !time.ambiguous, true, null, sentence, offset, listOfNotNull(time?.range, date.range))
        }
        // 2) Viagem.
        val tripMatch = trip.find(lower)
        if (tripMatch != null && (date != null || tripTo.containsMatchIn(sentence)) && BriefKind.TRIP in kinds) {
            val dest = tripTo.find(sentence, tripMatch.range.first)?.groupValues?.get(1)
            val title = if (dest != null) "Viagem para $dest" else "Viagem"
            return build(BriefKind.TRIP, title, resolved, date?.confirmed == true, time != null && !time.ambiguous, false, null, sentence, offset, listOfNotNull(date?.range, time?.range) .ifEmpty { listOf(tripMatch.range) })
        }
        // 3) Tarefa ou lugar ("não posso esquecer de…", "preciso passar no shopping amanhã").
        val cue = strong ?: weakTask.find(lower)?.takeIf { date != null || time != null }
        if (cue != null) {
            val phrase = taskPhrase(sentence, lower, cue, time, date)
            if (phrase != null) {
                val placeWord = findPlace(phrase.lowercase(Locale.ROOT))
                val kind = when {
                    time != null && noun != null -> BriefKind.EVENT
                    time != null -> BriefKind.EVENT
                    placeWord != null -> BriefKind.PLACE
                    else -> BriefKind.TASK
                }
                if (kind in kinds || (kind == BriefKind.PLACE && BriefKind.TASK in kinds)) {
                    val finalKind = if (kind in kinds) kind else BriefKind.TASK
                    val phraseStart = sentence.indexOf(phrase, cue.range.first).coerceAtLeast(0)
                    val spans = listOfNotNull(phraseStart until phraseStart + phrase.length, date?.range, time?.range)
                    return build(finalKind, capitalize(phrase), resolved, date?.confirmed == true, time != null && !time.ambiguous, false, placeWord, sentence, offset, spans)
                }
            }
        }
        // 4) Compromisso com horário ou data.
        if ((time != null || date != null) && BriefKind.EVENT in kinds) {
            val title = when {
                noun != null -> withCompanion(noun.second, sentence, noun.first)
                commitment.containsMatchIn(lower) && time != null -> "Compromisso"
                else -> return null
            }
            if (resolved == null) return null
            return build(BriefKind.EVENT, title, resolved, date?.confirmed == true, time != null && !time.ambiguous, false, findPlace(lower)?.takeIf { noun == null }, sentence, offset, listOfNotNull(time?.range, date?.range))
        }
        // 5) Lugar sem data, com intenção ("vou passar na farmácia").
        if (BriefKind.PLACE in kinds && (strong != null || commitment.containsMatchIn(lower) || weakTask.containsMatchIn(lower))) {
            val m = placeVerb.find(lower) ?: return null
            val place = findPlace(m.value) ?: return null
            val text = sentence.substring(m.range.first, m.range.last + 1)
            return build(BriefKind.PLACE, capitalize(text), null, false, false, false, place, sentence, offset, listOf(m.range))
        }
        return null
    }

    // --- Peças -----------------------------------------------------------------------------------

    private data class TimeHit(val hour: Int, val minute: Int, val ambiguous: Boolean, val range: IntRange, val period: String?)
    private data class DateHit(val date: LocalDate, val confirmed: Boolean, val weekly: Boolean, val range: IntRange)

    /** A última frase (do último ponto final, exclamação, interrogação ou quebra de linha em diante). */
    private fun lastSentence(text: String): Pair<Int, String> {
        val trimmed = text.trimEnd()
        // Ignora a pontuação final da própria frase ao procurar o começo dela.
        var end = trimmed.length
        while (end > 0 && trimmed[end - 1] in ".!…") end--
        val start = (trimmed.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', '…'), end - 1) + 1).coerceAtLeast(0)
        val raw = trimmed.substring(start)
        val lead = raw.length - raw.trimStart().length
        return (start + lead) to raw.trimStart()
    }

    private fun findTime(s: String): TimeHit? {
        ptNoonMidnight.find(s)?.let { m ->
            val noon = m.groupValues[1].startsWith("meio")
            return TimeHit(if (noon) 12 else 0, 0, false, m.range, null)
        }
        for (m in ptTime.findAll(s)) {
            val prefix = m.groups[1]?.value
            val hour = numberWords[m.groupValues[2]] ?: m.groupValues[2].toIntOrNull() ?: continue
            val sep = m.groups[3]?.value
            val mm = m.groups[4]?.value?.toIntOrNull()
            val unit = m.groups[5]?.value
            val extra = m.groups[6]?.value
            val period = m.groups[7]?.value
            val hasMinutes = sep == ":" || (sep == "h" && mm != null)
            val explicit = hasMinutes || unit != null || period != null || sep == "h"
            // "as 3 camisas": o artigo "as" só vale com algo que confirme horário.
            if (prefix == null && !explicit) continue
            if (prefix == "as" && !explicit && !endsAsTime(s, m.range.last + 1)) continue
            if (prefix == "às" && !explicit && !endsAsTime(s, m.range.last + 1)) continue
            if (hour > 23) continue
            val minute = when {
                mm != null -> mm
                extra == "meia" -> 30
                extra != null -> extra.toIntOrNull() ?: 0
                else -> 0
            }
            if (minute > 59) continue
            // "19h", "7h30", "19:00": formato de 24 h, sem dúvida. "às 7", "7 horas": sem período, ambíguo.
            val twentyFour = sep != null
            val ambiguous = period == null && hour in 1..12 && !twentyFour
            val adjusted = when {
                period == null -> hour
                period.startsWith("manh") -> if (hour == 12) 0 else hour
                period == "madrugada" -> hour % 12
                else -> if (hour in 1..11) hour + 12 else hour
            }
            return TimeHit(adjusted, minute, ambiguous, m.range, period)
        }
        for (m in enTime.findAll(s)) {
            val prefix = m.groups[1]?.value
            val hour = m.groupValues[2].toIntOrNull() ?: continue
            val mm = m.groups[3]?.value?.toIntOrNull()
            val suffix = m.groups[4]?.value
            if (prefix == null && mm == null && suffix == null) continue
            if (prefix != null && mm == null && suffix == null && !endsAsTime(s, m.range.last + 1)) continue
            if (hour > 23 || (mm ?: 0) > 59) continue
            val pm = suffix?.startsWith("p") == true
            val am = suffix?.startsWith("a") == true
            val adjusted = when {
                pm && hour in 1..11 -> hour + 12
                am && hour == 12 -> 0
                else -> hour
            }
            val ambiguous = !pm && !am && mm == null && hour in 1..12
            return TimeHit(adjusted, mm ?: 0, ambiguous, m.range, if (pm) "pm" else if (am) "am" else null)
        }
        return null
    }

    /** Depois de "às 7" vem fim de frase, pontuação, ou uma palavra de data/período? */
    private fun endsAsTime(s: String, from: Int): Boolean {
        val rest = s.substring(from.coerceAtMost(s.length)).trimStart()
        if (rest.isEmpty() || rest[0] in ".,!;:)") return true
        val word = rest.takeWhile { it.isLetter() || it == 'ã' || it == 'á' }
        return word in setOf("hoje", "amanhã", "amanha", "e", "da", "de", "no", "na", "em", "pra", "para", "com", "today", "tomorrow", "tonight", "on", "with")
    }

    private fun findDate(s: String, now: LocalDateTime): DateHit? {
        val today = now.toLocalDate()
        ptAfterTomorrow.find(s)?.let { return DateHit(today.plusDays(2), true, false, it.range) }
        ptTomorrow.find(s)?.let { return DateHit(today.plusDays(1), true, false, it.range) }
        enTomorrow.find(s)?.let { return DateHit(today.plusDays(1), true, false, it.range) }
        ptToday.find(s)?.let { return DateHit(today, true, false, it.range) }
        enToday.find(s)?.let { return DateHit(today, true, false, it.range) }
        weekdayHit(s, today)?.let { return it }
        ptDayOfMonth.find(s)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groups[2]?.value?.let(::monthIndex)
            return dayDate(today, day, month)?.let { DateHit(it, true, false, m.range) }
        }
        ptMonthDate.find(s)?.let { m ->
            return dayDate(today, m.groupValues[1].toInt(), monthIndex(m.groupValues[2]))?.let { DateHit(it, true, false, m.range) }
        }
        numericDate.find(s)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            if (mo in 1..12) {
                val y = m.groups[3]?.value?.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                val date = runCatching { LocalDate.of(y ?: today.year, mo, d) }.getOrNull()
                if (date != null) {
                    val fixed = if (y == null && date.isBefore(today)) date.plusYears(1) else date
                    return DateHit(fixed, true, false, m.range)
                }
            }
        }
        (ptNextWeek.find(s) ?: enNextWeek.find(s))?.let {
            return DateHit(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), false, false, it.range)
        }
        (ptNextMonth.find(s) ?: enNextMonth.find(s))?.let {
            return DateHit(today.plusMonths(1).withDayOfMonth(1), false, false, it.range)
        }
        (ptWeekend.find(s) ?: enWeekend.find(s))?.let {
            return DateHit(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), false, false, it.range)
        }
        return null
    }

    private fun weekdayHit(s: String, today: LocalDate): DateHit? {
        val pt = ptWeekday.find(s)
        val en = if (pt == null) enWeekday.find(s) else null
        val m = pt ?: en ?: return null
        val day = when (m.groupValues[1].replace('á', 'a').replace('ç', 'c')) {
            "segunda", "monday" -> DayOfWeek.MONDAY
            "terca", "tuesday" -> DayOfWeek.TUESDAY
            "quarta", "wednesday" -> DayOfWeek.WEDNESDAY
            "quinta", "thursday" -> DayOfWeek.THURSDAY
            "sexta", "friday" -> DayOfWeek.FRIDAY
            "sabado", "saturday" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        val before = s.substring(0, m.range.first).takeLast(18)
        val after = s.substring(m.range.last + 1).take(10)
        val weekly = Regex("\\b(?:toda|todo|todas|todos|every)\\b(?:\\s+(?:as|os|a|o))?\\s*$").containsMatchIn(before) || m.groups[2] != null && Regex("\\b(?:às|as|aos|nas|nos|on)\\s*$").containsMatchIn(before)
        val next = Regex("\\bpr[óo]xim[ao]\\s*$|\\bnext\\s*$").containsMatchIn(before) || after.trimStart().startsWith("que vem")
        val base = today.with(TemporalAdjusters.nextOrSame(day))
        val date = when {
            next && base == today -> today.plusWeeks(1)
            next && day.value <= today.dayOfWeek.value -> base
            else -> base
        }
        val end = if (after.trimStart().startsWith("que vem")) m.range.last + 1 + after.indexOf("que vem") + "que vem".length - 1 else m.range.last
        return DateHit(date, true, weekly, m.range.first..end)
    }

    private fun monthIndex(name: String): Int = months.indexOfFirst { it.replace('ç', 'c') == name.replace('ç', 'c') } + 1

    private fun dayDate(today: LocalDate, day: Int, month: Int?): LocalDate? {
        if (day !in 1..31) return null
        if (month != null && month in 1..12) {
            val d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            return if (d.isBefore(today)) d.plusYears(1) else d
        }
        val thisMonth = runCatching { today.withDayOfMonth(day) }.getOrNull()
        if (thisMonth != null && !thisMonth.isBefore(today)) return thisMonth
        return runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
    }

    private fun findEvent(s: String): Pair<Int, String>? {
        var best: Pair<Int, String>? = null
        for ((word, title) in events) {
            val m = Regex("\\b${Regex.escape(word)}\\b").find(s) ?: continue
            if (best == null || m.range.first < best.first) best = m.range.first to title
        }
        // "trabalho" só é evento se não houver outro (em "entregar o trabalho" é tarefa).
        return best
    }

    private fun findPlace(s: String): String? {
        val m = placeVerb.find(s) ?: return places.firstOrNull { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(s) && Regex("\\b(?:no|na|ao|à|pro|pra)\\s+${Regex.escape(it)}\\b").containsMatchIn(s) }
        val words = (m.groups[1]?.value ?: m.groups[2]?.value ?: return null).split(' ')
        return when {
            words.size >= 2 && "${words[0]} ${words[1]}" in places -> "${words[0]} ${words[1]}"
            words[0] in places -> words[0]
            else -> null
        }
    }

    /** "Jantar com Ana" quando a frase diz "jantar com a Ana". */
    private fun withCompanion(title: String, sentence: String, at: Int): String {
        val m = Regex("\\bcom\\s+(?:a\\s+|o\\s+)?([A-ZÀ-Ú][\\wÀ-ú]+)").find(sentence, at) ?: return title
        return if (m.range.first - at < 40) "$title com ${m.groupValues[1]}" else title
    }

    /** O que vem depois da pista ("entregar o trabalho"), sem a data, o horário e o resto da frase. */
    private fun taskPhrase(sentence: String, lower: String, cue: MatchResult?, time: TimeHit?, date: DateHit?): String? {
        cue ?: return null
        val from = cue.range.last + 1
        var to = sentence.length
        for (r in listOfNotNull(time?.range, date?.range)) if (r.first in from until to) to = r.first
        val stop = Regex("[,;.!?]|\\s(?:e depois|mas|porque|pois|que é|and|but|because)\\s").find(lower, from)
        if (stop != null && stop.range.first < to) to = stop.range.first
        val words = sentence.substring(from, to).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Tira preposições soltas no fim ("…o trabalho na").
        val cleaned = words.take(7).dropLastWhile { it.lowercase(Locale.ROOT) in dangling || it.lowercase(Locale.ROOT) in setOf("hoje", "amanhã", "amanha") }
        if (cleaned.isEmpty() || cleaned.joinToString(" ").length < 3) return null
        return cleaned.joinToString(" ")
    }

    private fun resolve(time: TimeHit?, date: DateHit?, period: String?, noun: Pair<Int, String>?, now: LocalDateTime): LocalDateTime? {
        val nounWord = noun?.second?.lowercase(Locale.ROOT)
        if (time != null) {
            var hour = time.hour
            if (time.ambiguous) {
                val evening = (period != null && !period.startsWith("manh") && !period.startsWith("morn")) ||
                    nounWord in this.evening || nounWord == null && time.hour in 1..6
                val morningNoun = nounWord in morning
                hour = when {
                    period != null && (period.startsWith("manh") || period.startsWith("morn")) -> if (time.hour == 12) 0 else time.hour
                    evening && time.hour in 1..11 -> time.hour + 12
                    morningNoun -> if (time.hour in 1..5) time.hour + 12 else time.hour
                    time.hour in 1..6 -> time.hour + 12
                    else -> time.hour
                }
            }
            val t = LocalTime.of(hour.coerceIn(0, 23), time.minute)
            if (date != null) return date.date.atTime(t)
            val today = now.toLocalDate().atTime(t)
            return if (today.isAfter(now.plusMinutes(5))) today else today.plusDays(1)
        }
        if (date != null) {
            val h = when {
                period == null -> 9
                period.startsWith("manh") || period.startsWith("morn") -> 9
                period == "tarde" || period == "afternoon" -> 15
                else -> 20
            }
            return date.date.atTime(h, 0)
        }
        return null
    }

    private fun build(
        kind: BriefKind,
        title: String,
        at: LocalDateTime?,
        dateConfirmed: Boolean,
        timeConfirmed: Boolean,
        weekly: Boolean,
        place: String?,
        sentence: String,
        offset: Int,
        spans: List<IntRange>,
    ): BriefCandidate {
        // A expressão destacada: as partes reconhecidas, juntas quando estão lado a lado.
        val sorted = spans.filter { !it.isEmpty() }.sortedBy { it.first }
        var start = sorted.firstOrNull()?.first ?: 0
        var end = (sorted.firstOrNull()?.last ?: (sentence.length - 1)) + 1
        for (r in sorted.drop(1)) {
            val gap = sentence.substring(end.coerceAtMost(sentence.length), r.first.coerceAtLeast(end).coerceAtMost(sentence.length))
            if (gap.length <= 4 && gap.isBlank() || gap.trim() in setOf("às", "as", "de", "da", "no", "na", "e", "at", "on")) end = maxOf(end, r.last + 1) else break
        }
        start = start.coerceIn(0, sentence.length)
        end = end.coerceIn(start, sentence.length)
        return BriefCandidate(
            kind = kind,
            title = title.trim().take(60),
            at = at,
            dateConfirmed = dateConfirmed && at != null,
            timeConfirmed = timeConfirmed && at != null,
            weekly = weekly,
            place = place,
            expression = sentence.substring(start, end).trim(),
            start = offset + start,
            end = offset + end,
        )
    }

    private fun capitalize(s: String): String = s.trim().replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
}
