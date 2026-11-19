package app.lily.ai.codex

import app.lily.ai.core.Profile
import app.lily.ai.remote.RemoteModel

/** O tipo de trabalho de programação (cada um pede um modelo diferente). */
enum class CodeRole(val label: String, val remoteRole: String, val tiers: List<String>) {
    QUICK_FIX("Correção simples", "quick", listOf("fast", "strong")),
    GENERATE("Escrever código", "generate", listOf("strong", "frontier")),
    REFACTOR("Refatoração", "refactor", listOf("strong", "frontier")),
    DEBUG("Depuração", "debug", listOf("strong", "frontier")),
    ARCHITECTURE("Arquitetura", "architecture", listOf("frontier", "strong")),
    DOCS("Documentação", "docs", listOf("fast", "strong")),
    SECURITY("Revisão de segurança", "security", listOf("frontier", "strong")),
    REVIEW("Revisão final", "review", listOf("frontier", "strong")),
    EXPLAIN("Explicar código", "explain", listOf("fast", "strong")),
}

/** A escolha do roteador de programação, com os motivos (mostrados na tela). */
data class CodePick(val model: RemoteModel, val role: CodeRole, val reasons: List<String>)

/**
 * O roteador do Codex: entende que tipo de tarefa é e escolhe UM modelo (nunca vários sem precisar)
 * pelo papel, pelo nível de raciocínio necessário, pelo custo e pela velocidade. A revisão final usa
 * um modelo de outro fornecedor, para uma segunda opinião de verdade.
 */
object CodeRouter {
    private val rules: List<Pair<CodeRole, Regex>> = listOf(
        CodeRole.SECURITY to Regex("""segurança|seguran|vulnerab|security|injection|xss|csrf|segredo|secret|token vazad|permiss""", RegexOption.IGNORE_CASE),
        CodeRole.ARCHITECTURE to Regex("""arquitetura|architecture|projetar|design do sistema|estrutura do projeto|módulos|camadas|escal""", RegexOption.IGNORE_CASE),
        CodeRole.DEBUG to Regex("""erro|error|exception|crash|fecha|falha|bug|não funciona|nao funciona|stack ?trace|depura|debug|fails?|travando""", RegexOption.IGNORE_CASE),
        CodeRole.REFACTOR to Regex("""refator|refactor|reorganiz|limp(ar|eza) (o )?código|simplific|extrair|renomear""", RegexOption.IGNORE_CASE),
        CodeRole.DOCS to Regex("""document|readme|comentári|docstring|kdoc|javadoc|guia""", RegexOption.IGNORE_CASE),
        CodeRole.EXPLAIN to Regex("""^(explique|explica|o que (faz|é)|como funciona|entender|explain|what does)""", RegexOption.IGNORE_CASE),
        CodeRole.QUICK_FIX to Regex("""typo|corrigir (o )?nome|trocar (a |o )?(cor|texto|nome)|ajuste rápido|pequen""", RegexOption.IGNORE_CASE),
    )

    fun classify(task: String, repair: Boolean = false): CodeRole {
        if (repair) return CodeRole.DEBUG
        val t = task.trim()
        return rules.firstOrNull { (_, re) -> re.containsMatchIn(t) }?.first ?: CodeRole.GENERATE
    }

    /**
     * [escalate] = a tentativa anterior não resolveu: sobe um nível de raciocínio. [avoidVendor] = o
     * fornecedor que escreveu a solução (a revisão vai para outro).
     */
    fun pick(
        role: CodeRole,
        models: List<RemoteModel>,
        profile: Profile,
        manual: String? = null,
        escalate: Boolean = false,
        avoidVendor: String? = null,
    ): CodePick? {
        val reasons = ArrayList<String>()
        manual?.let { id ->
            models.firstOrNull { it.id == id }?.let { return CodePick(it, role, listOf("Escolhido por você: ${it.label}.")) }
        }
        val withRole = models.filter { role.remoteRole in it.roles }.ifEmpty { models }
        val pool = if (avoidVendor != null) withRole.filter { it.vendor != avoidVendor }.ifEmpty { withRole } else withRole
        if (pool.isEmpty()) return null
        var tiers = role.tiers
        if (escalate || profile == Profile.QUALITY) tiers = listOf("frontier") + tiers.filter { it != "frontier" }
        if (profile == Profile.ECO && !escalate && role != CodeRole.ARCHITECTURE && role != CodeRole.SECURITY) tiers = listOf("fast") + tiers.filter { it != "fast" }
        for (tier in tiers) {
            val inTier = pool.filter { it.tier == tier }
            if (inTier.isEmpty()) continue
            // Mesmo nível: o mais barato; nas correções rápidas, o mais rápido entre os baratos.
            val chosen = if (role == CodeRole.QUICK_FIX) inTier.sortedWith(compareBy({ if (it.speed == "fast") 0 else 1 }, { it.typicalUsd })).first() else inTier.minBy { it.typicalUsd }
            reasons += "${role.label}: nível ${tierName(tier)}" + (if (escalate) " (a tentativa anterior não resolveu)" else "") + "."
            if (avoidVendor != null && chosen.vendor != avoidVendor) reasons += "Revisão por outro fornecedor (${chosen.vendor}), independente de quem escreveu."
            reasons += "Entre os iguais, o de menor custo: ${chosen.label} (~US$ ${"%.4f".format(java.util.Locale.forLanguageTag("pt-BR"), chosen.typicalUsd)} por resposta típica)."
            return CodePick(chosen, role, reasons)
        }
        val fallback = pool.minBy { it.typicalUsd }
        return CodePick(fallback, role, listOf("${role.label}: o único disponível agora (${fallback.label})."))
    }

    private fun tierName(t: String) = when (t) {
        "frontier" -> "raciocínio máximo"
        "strong" -> "forte"
        else -> "rápido"
    }
}
