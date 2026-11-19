package app.lily.ai.engine

import app.lily.ai.core.TaskKind

/** Como a pessoa escreve (vem do teclado): no informal, abreviações e gírias ficam. */
enum class WritingStyle { FORMAL, NATURAL, INFORMAL }

/** Os tons da reescrita. */
enum class Tone(val label: String, val instruction: String) {
    NEUTRAL("Neutro", "num tom neutro e claro"),
    FORMAL("Formal", "num tom formal e educado"),
    CASUAL("Descontraído", "num tom descontraído, como numa conversa entre amigos"),
    FRIENDLY("Amigável", "num tom caloroso e gentil"),
    CONCISE("Direto", "de forma curta e direta, sem perder o sentido"),
}

/**
 * As instruções da L.I.L.Y para cada ferramenta. Pedem só o resultado (sem comentários) para o
 * teclado poder trocar o texto direto, e nunca inventar o que não está no texto.
 */
object Prompts {
    fun system(memories: List<String>, extra: String? = null, online: Boolean = false): String = buildString {
        if (online) {
            append("Você é a L.I.L.Y, a assistente do Liquid Flip. Nesta resposta você roda num modelo online, pelo serviço que a pessoa configurou. ")
        } else {
            append("Você é a L.I.L.Y, a assistente do Liquid Flip, rodando inteiramente no aparelho da pessoa, sem internet. ")
        }
        append("Responda em português do Brasil, a menos que peçam outro idioma. Seja clara, gentil e direta. ")
        append("Se faltar contexto ou você não souber, diga isso com honestidade em vez de inventar. ")
        append("Você não navega na internet nem acessa a agenda ou outros apps, a não ser pelo que a pessoa enviar aqui.")
        if (memories.isNotEmpty()) {
            append("\n\nO que a pessoa pediu para você lembrar (use só quando for útil):\n")
            memories.forEach { append("- ").append(it).append('\n') }
        }
        if (extra != null) append("\n").append(extra)
    }

    /** Instrução + texto de uma ferramenta de texto. */
    fun tool(task: TaskKind, text: String, style: WritingStyle = WritingStyle.NATURAL, tone: Tone = Tone.NEUTRAL, language: String = "português do Brasil", instruction: String? = null): String {
        val keep = when (style) {
            WritingStyle.INFORMAL -> " Mantenha o jeito informal: NÃO troque abreviações como vc, pq, tbm, nn, blz, msg, gírias e risadas (kkk) pela forma completa."
            WritingStyle.NATURAL -> " Mantenha o jeito de escrever da pessoa."
            WritingStyle.FORMAL -> ""
        }
        val only = " Responda só com o resultado, sem aspas, sem explicações e sem comentários."
        return when (task) {
            TaskKind.CORRECT -> "Corrija só os erros de ortografia, acentuação, digitação e pontuação do texto abaixo, sem mudar o sentido nem o estilo.$keep$only\n\nTexto:\n$text"
            TaskKind.REWRITE -> "Reescreva o texto abaixo ${tone.instruction}, mantendo o sentido.${if (style == WritingStyle.INFORMAL && tone != Tone.FORMAL) keep else ""}$only\n\nTexto:\n$text"
            TaskKind.SUMMARIZE -> "Resuma o texto abaixo em português, com os pontos principais, em poucas frases ou tópicos curtos. Não acrescente nada que não esteja no texto.$only\n\nTexto:\n$text"
            TaskKind.EXPAND -> "Desenvolva o texto abaixo com mais detalhes e clareza, mantendo o sentido e o tom.$keep$only\n\nTexto:\n$text"
            TaskKind.TRANSLATE -> "Traduza o texto abaixo para $language, mantendo o tom.$only\n\nTexto:\n$text"
            TaskKind.EXPLAIN -> "Explique o texto abaixo em português simples, como para alguém que não conhece o assunto.\n\nTexto:\n$text"
            TaskKind.ORGANIZE -> "Organize as informações do texto abaixo em tópicos claros (listas, datas e responsáveis quando houver). Não invente nada.$only\n\nTexto:\n$text"
            TaskKind.GENERATE_TEXT -> "Escreva o que a pessoa pediu: ${instruction ?: text}.$keep$only"
            TaskKind.REPLY -> "A pessoa recebeu a mensagem abaixo. Escreva uma resposta curta e natural que ela possa mandar${instruction?.let { ", com esta intenção: $it" } ?: ""}.$keep$only\n\nMensagem recebida:\n$text"
            TaskKind.ASK -> instruction?.let { "$it\n\nSobre este texto:\n$text" } ?: text
            TaskKind.DESCRIBE_IMAGE -> instruction ?: "Descreva esta imagem em português: o que aparece, onde parece ser, cores e detalhes importantes. Se houver texto na imagem, diga o que está escrito."
            TaskKind.TRANSCRIBE_AUDIO -> instruction ?: "Transcreva este áudio palavra por palavra, em português. Depois de uma linha em branco, escreva \"Resumo:\" e resuma em uma ou duas frases."
            else -> text
        }
    }

    /** Título curto para uma conversa nova (das primeiras palavras, sem gastar o modelo). */
    fun titleFrom(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return "Nova conversa"
        val words = clean.split(' ').take(6).joinToString(" ")
        return (if (words.length > 42) words.take(42).trimEnd() + "…" else words).replaceFirstChar { it.uppercase() }
    }
}
