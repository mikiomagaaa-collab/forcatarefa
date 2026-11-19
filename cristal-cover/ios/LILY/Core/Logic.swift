import Foundation

/// Lê os pedaços da resposta (SSE). Aceita o formato do OpenRouter/OpenAI (choices[0].delta.content)
/// e o do Workers AI ("response"), como o app Android e a L.I.L.Y Web.
enum SSE {
    struct Event: Equatable {
        var delta: String?
        var done = false
        var error: String?
        var completionTokens: Int?
    }

    static func parse(_ line: String) -> Event? {
        let t = line.trimmingCharacters(in: .whitespaces)
        guard t.hasPrefix("data:") else { return nil }
        let payload = t.dropFirst(5).trimmingCharacters(in: .whitespaces)
        if payload == "[DONE]" { return Event(done: true) }
        guard let data = payload.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        var event = Event()
        if let err = obj["error"] {
            if let e = err as? [String: Any], let m = e["message"] as? String { event.error = m }
            else if let s = err as? String { event.error = s }
            else { event.error = "erro do serviço" }
        }
        if let choices = obj["choices"] as? [[String: Any]], let first = choices.first,
           let delta = first["delta"] as? [String: Any], let content = delta["content"] as? String {
            event.delta = content
        } else if let response = obj["response"] as? String {
            event.delta = response
        }
        if let usage = obj["usage"] as? [String: Any] {
            event.completionTokens = usage["completion_tokens"] as? Int
        }
        return event
    }
}

/// Escolhe o modelo online: o grátis primeiro (menos no perfil Qualidade), como o app e a Web.
enum Router {
    enum Profile: String, CaseIterable, Codable, Identifiable {
        case eco, balanced, quality
        var id: String { rawValue }
        var label: String {
            switch self {
            case .eco: return "Econômico"
            case .balanced: return "Equilibrado"
            case .quality: return "Qualidade"
            }
        }
    }

    static func pick(_ models: [RemoteModel], profile: Profile, role: String = "chat") -> RemoteModel? {
        let withRole = models.filter { ($0.roles ?? []).contains(role) }
        guard !withRole.isEmpty else { return nil }
        if profile != .quality, let free = withRole.first(where: { $0.isFree }) { return free }
        let order: [String]
        switch profile {
        case .eco: order = ["fast", "strong", "frontier"]
        case .quality: order = ["frontier", "strong", "fast"]
        case .balanced: order = ["strong", "fast", "frontier"]
        }
        for tier in order {
            if let best = withRole.filter({ $0.tier == tier }).min(by: { $0.typicalUsd < $1.typicalUsd }) { return best }
        }
        return withRole.min(by: { $0.typicalUsd < $1.typicalUsd })
    }
}

/// O histórico que vai junto com a pergunta: só respostas completas, alternando pessoa e L.I.L.Y,
/// limitado em trocas e em caracteres (o modelo grátis leva pouco, para a cota do dia render).
enum History {
    static func build(_ messages: [ChatMessage], maxTurns: Int, maxChars: Int) -> [WireMessage] {
        var picked: [ChatMessage] = []
        var used = 0
        for m in messages.reversed() {
            if picked.count >= maxTurns { break }
            if m.status != .done || m.text.isEmpty { continue }
            let text = String(m.text.prefix(2_000))
            if used + text.count > maxChars { break }
            used += text.count
            picked.append(ChatMessage(role: m.role, text: text))
        }
        picked.reverse()
        var merged: [WireMessage] = []
        for m in picked {
            let role = m.role == .user ? "user" : "assistant"
            if let last = merged.last, last.role == role {
                merged[merged.count - 1].content += "\n\n" + m.text
            } else {
                merged.append(WireMessage(role: role, content: m.text))
            }
        }
        while merged.first?.role == "assistant" { merged.removeFirst() }
        if merged.last?.role == "user" { merged.removeLast() }
        return merged
    }
}

enum Prompts {
    static func system(memories: [String] = []) -> String {
        var s = "Você é a L.I.L.Y, a assistente do PROJECT LIQUID FLIP, respondendo num iPhone por um modelo online, pelo serviço que a pessoa configurou. "
        s += "Responda em português do Brasil, a menos que peçam outro idioma. Seja clara, gentil e direta. "
        s += "Responda sempre à ÚLTIMA mensagem da pessoa; nunca repita nem continue a resposta anterior. "
        s += "Use as mensagens anteriores só como contexto: em perguntas curtas (\"e ele?\", \"quantos anos ele tem?\") elas dizem a quem ou a que \"ele\", \"ela\" e \"isso\" se referem. "
        s += "Se não souber, diga com honestidade em vez de inventar."
        if !memories.isEmpty {
            s += "\n\nO que a pessoa pediu para você lembrar (use só quando for útil):\n" + memories.map { "- \($0)" }.joined(separator: "\n")
        }
        return s
    }
}

enum GatewayURL {
    /// Só https (http apenas para o próprio aparelho, em testes).
    static func normalize(_ raw: String) -> URL? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        guard let url = URL(string: s), let scheme = url.scheme?.lowercased(), let host = url.host else { return nil }
        if scheme == "https" { return url }
        if scheme == "http" && (host == "127.0.0.1" || host == "localhost") { return url }
        return nil
    }
}

enum DataURL {
    /// Os bytes de um "data:image/...;base64,...".
    static func bytes(_ s: String) -> Data? {
        guard let comma = s.range(of: "base64,") else { return Data(base64Encoded: s) }
        return Data(base64Encoded: String(s[comma.upperBound...]))
    }
}
