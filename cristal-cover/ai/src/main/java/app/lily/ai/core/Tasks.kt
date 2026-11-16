package app.lily.ai.core

/** O que pedem à IA. O roteador escolhe o mecanismo por tarefa. */
enum class TaskKind(val label: String) {
    CHAT("Conversa"),
    ASK("Pergunta"),
    CORRECT("Corrigir"),
    REWRITE("Reescrever"),
    SUMMARIZE("Resumir"),
    EXPAND("Expandir"),
    TRANSLATE("Traduzir"),
    EXPLAIN("Explicar"),
    ORGANIZE("Organizar"),
    GENERATE_TEXT("Gerar texto"),
    REPLY("Sugerir resposta"),
    DESCRIBE_IMAGE("Descrever imagem"),
    OCR("Extrair texto"),
    LABELS("Identificar objetos"),
    DENOISE("Remover ruído"),
    UPSCALE("Ampliar resolução"),
    ENHANCE("Melhorar foto"),
    REMOVE_BACKGROUND("Remover fundo"),
    TRANSCRIBE_MIC("Transcrever (microfone)"),
    TRANSCRIBE_AUDIO("Transcrever áudio"),
    SPEAK("Ler em voz alta"),
    LANGUAGE_ID("Identificar idioma"),
    GENERATE_IMAGE("Gerar imagem"),
    ;

    val isText: Boolean get() = this in TEXT_TASKS

    companion object {
        val TEXT_TASKS = setOf(CHAT, ASK, CORRECT, REWRITE, SUMMARIZE, EXPAND, TRANSLATE, EXPLAIN, ORGANIZE, GENERATE_TEXT, REPLY)
    }
}
