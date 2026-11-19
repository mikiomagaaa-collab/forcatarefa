package app.cristal.cover.ui.fold

import kotlin.math.abs

/**
 * Para onde a transição de dobra deve ir agora: 1 = tela assentada e nítida; 0 = no auge da
 * transformação (a tela acabou de acender ao abrir, ou está prestes a apagar ao fechar).
 *
 * Regras (ver docs/GUIA.md):
 * - **Chegada**: ao trocar de tela física (abrir ou fechar o aparelho), começa em 0.
 * - **Abrindo** (tela interna): segue a dobradiça do ângulo em que a tela acendeu até [OPEN_DONE].
 * - **Fechando** (tela interna): segue a dobradiça do ângulo em que começou a fechar até [CLOSE_END],
 *   sempre começando em 1 (sem salto) e só depois de [CLOSE_START_DELTA] graus de movimento.
 * - **Parou no meio** (modo flex, hesitação): depois de [STALL_MS] sem mexer, volta a ficar nítida.
 *   Para a interface nunca ficar desfocada parada.
 * - **Tela externa**: chega com o tempo (sem ângulo útil enquanto fechado) e, ao começar a abrir,
 *   desfaz em direção à dobradiça antes de apagar.
 * - **Sem sensor** de dobradiça: só a chegada, com o tempo.
 * - **Movimento reduzido** (animações desligadas no Android): sempre 1.
 */
class FoldMotionModel(private val reduceMotion: Boolean = false) {
    private enum class Session { NONE, ARRIVING, OPENING, CLOSING, COVER_OPENING }

    private var session = Session.NONE
    private var start = 0f
    private var lastAngle: Float? = null
    private var lastMoveAt = 0L
    private var settledAngle: Float? = null
    private var onCover = false
    private var lowest = 180f

    /** O alvo atual (a interface anima até ele com uma mola). */
    var target: Float = 1f
        private set

    /** Começo do efeito numa chegada: a mola parte daqui. */
    val arrivalStart: Float get() = if (reduceMotion) 1f else 0f

    /** Trocou de tela física. [angle] = ângulo da dobradiça agora (null sem sensor). */
    fun onDisplay(cover: Boolean, angle: Float?, now: Long) {
        onCover = cover
        lastAngle = angle
        lastMoveAt = now
        settledAngle = null
        if (reduceMotion) return settle()
        if (!cover && angle != null && angle < OPEN_DONE - 5f) {
            // Abriu: a tela interna acendeu no meio do movimento; segue a dobradiça daqui.
            session = Session.OPENING
            start = angle.coerceAtMost(OPEN_DONE - 20f)
            target = progressBetween(start, OPEN_DONE, angle)
        } else {
            session = Session.ARRIVING
            target = 1f
        }
    }

    /** Novo ângulo da dobradiça (graus; 180 = aberto). */
    fun onAngle(angle: Float, now: Long) {
        val previous = lastAngle
        lastAngle = angle
        if (previous != null && abs(angle - previous) >= 0.5f) lastMoveAt = now
        if (reduceMotion) return settle()
        when (session) {
            Session.OPENING -> {
                target = progressBetween(start, OPEN_DONE, angle)
                if (angle >= OPEN_DONE) settle(angle)
            }
            Session.CLOSING -> {
                lowest = minOf(lowest, angle)
                if (angle > lowest + REOPEN_DELTA) {
                    // Reabriu antes de fechar: volta a ficar nítida.
                    settle(angle)
                } else {
                    target = progressBetween(CLOSE_END, start, angle)
                }
            }
            Session.COVER_OPENING -> target = 1f - progressBetween(COVER_OPEN_START, COVER_OPEN_END, angle)
            Session.ARRIVING, Session.NONE -> {
                val base = settledAngle ?: previous ?: angle
                if (settledAngle == null) settledAngle = base
                if (!onCover && angle <= base - CLOSE_START_DELTA && angle in CLOSE_END..OPEN_DONE + 15f) {
                    session = Session.CLOSING
                    lowest = angle
                    start = base.coerceIn(CLOSE_END + 30f, 180f)
                    target = progressBetween(CLOSE_END, start, angle)
                } else if (onCover && angle >= COVER_OPEN_START) {
                    session = Session.COVER_OPENING
                    target = 1f - progressBetween(COVER_OPEN_START, COVER_OPEN_END, angle)
                } else if (angle > base) {
                    settledAngle = angle
                }
            }
        }
    }

    /** Relógio: depois de [STALL_MS] parada no meio, volta a ficar nítida. Devolve true se mudou. */
    fun tick(now: Long): Boolean {
        if (session == Session.OPENING || session == Session.CLOSING || session == Session.COVER_OPENING) {
            if (now - lastMoveAt >= STALL_MS) {
                settle(lastAngle)
                return true
            }
        }
        return false
    }

    /** A chegada terminou de animar: fica assentada. */
    fun arrived() {
        if (session == Session.ARRIVING) session = Session.NONE
    }

    private fun settle(angle: Float? = lastAngle) {
        session = Session.NONE
        settledAngle = angle
        target = 1f
    }

    companion object {
        /** Daqui para cima a tela interna está assentada. */
        const val OPEN_DONE = 165f

        /** Ao fechar, a transformação chega ao máximo aqui (a tela apaga logo depois). */
        const val CLOSE_END = 60f

        /** Graus de fechamento antes de começar a reagir (evita tremer com a mão). */
        const val CLOSE_START_DELTA = 6f

        /** Graus de volta (abrindo) que desfazem um fechamento em andamento. */
        const val REOPEN_DELTA = 5f

        /** Na tela externa, começa a desfazer ao abrir daqui... */
        const val COVER_OPEN_START = 8f

        /** ...e chega ao máximo aqui (a tela externa apaga). */
        const val COVER_OPEN_END = 45f

        const val STALL_MS = 450L

        /** Progresso suave (sem degraus) entre dois ângulos. */
        fun progressBetween(from: Float, to: Float, angle: Float): Float {
            if (to <= from) return if (angle >= to) 1f else 0f
            val t = ((angle - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
