import Foundation
import Security

enum GatewayError: LocalizedError {
    case notConfigured
    case consent
    case http(Int, String)
    case server(String)
    case badResponse

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Conecte o L.I.L.Y Gateway em Ajustes (endereço e chave de acesso)."
        case .consent: return "Ligue \"Permitir processamento externo\" em Ajustes para usar o serviço online."
        case let .http(code, message): return message.isEmpty ? "O gateway respondeu com erro \(code)." : message
        case let .server(message): return message
        case .badResponse: return "Resposta inesperada do gateway."
        }
    }
}

/// A chave de acesso fica no Keychain do iPhone (nunca em texto nas preferências).
enum Keychain {
    private static let service = "app.lily.ios.gateway"
    private static let account = "access-key"

    static func save(_ value: String) {
        delete()
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    static func load() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// Cliente do L.I.L.Y Gateway (o mesmo do app Android e da L.I.L.Y Web).
final class GatewayClient {
    var baseURL: () -> URL?
    var key: () -> String?
    private let session: URLSession

    init(baseURL: @escaping () -> URL?, key: @escaping () -> String?, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.key = key
        self.session = session
    }

    var configured: Bool { baseURL() != nil && !(key() ?? "").isEmpty }

    private func request(_ path: String, method: String = "GET", timeout: TimeInterval = 60) throws -> URLRequest {
        guard let base = baseURL(), let key = key(), !key.isEmpty, let url = URL(string: base.absoluteString + path) else { throw GatewayError.notConfigured }
        var req = URLRequest(url: url, timeoutInterval: timeout)
        req.httpMethod = method
        req.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json, text/event-stream, application/x-ndjson", forHTTPHeaderField: "Accept")
        req.setValue("LILY-iOS", forHTTPHeaderField: "User-Agent")
        return req
    }

    static func errorMessage(_ data: Data) -> String {
        if let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let e = obj["error"] as? String { return e }
        return ""
    }

    private func send<T: Decodable>(_ req: URLRequest, as type: T.Type) async throws -> T {
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code >= 400 || code == 0 { throw GatewayError.http(code, Self.errorMessage(data)) }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw GatewayError.badResponse
        }
    }

    func status() async throws -> GatewayStatus {
        try await send(try request("/v1/status"), as: GatewayStatus.self)
    }

    func models() async throws -> [RemoteModel] {
        try await send(try request("/v1/models"), as: ModelList.self).models
    }

    /// A resposta em pedaços (cada pedaço é texto novo). Cancelar a Task fecha a conexão.
    func chat(model: String, messages: [WireMessage], maxTokens: Int = 1500) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var req = try self.request("/v1/chat", method: "POST", timeout: 120)
                    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    req.httpBody = try JSONEncoder().encode(ChatBody(model: model, messages: messages, maxTokens: maxTokens, temperature: 0.6))
                    let (bytes, resp) = try await self.session.bytes(for: req)
                    try await Self.check(resp, bytes)
                    for try await line in bytes.lines {
                        guard let event = SSE.parse(line) else { continue }
                        if event.done { break }
                        if let err = event.error { throw GatewayError.server("O modelo online parou: \(err)") }
                        if let piece = event.delta, !piece.isEmpty { continuation.yield(piece) }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private static func check(_ resp: URLResponse, _ bytes: URLSession.AsyncBytes) async throws {
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard code >= 400 || code == 0 else { return }
        var data = Data()
        for try await b in bytes {
            data.append(b)
            if data.count > 64_000 { break }
        }
        throw GatewayError.http(code, errorMessage(data))
    }

    func image(prompt: String) async throws -> OnlineImage {
        var req = try request("/v1/image", method: "POST", timeout: 90)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["prompt": prompt, "seed": Int.random(in: 1...1_000_000)])
        return try await send(req, as: OnlineImage.self)
    }

    func videoModels() async throws -> [VideoModel] {
        try await send(try request("/v1/video/models"), as: VideoModelList.self).models
    }

    func videoSubmit(model: String, prompt: String, duration: Int, aspect: String) async throws -> VideoSubmitted {
        var req = try request("/v1/video", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["model": model, "prompt": prompt, "duration": duration, "aspect": aspect])
        return try await send(req, as: VideoSubmitted.self)
    }

    /// A situação do vídeo. No grátis o gateway mantém a conexão aberta (NDJSON) até o fim: cada linha
    /// vai para onProgress e volta a última. Se a conexão cair, o Space cancela o vídeo.
    func videoStatus(jobId: String, onProgress: @escaping (VideoStatus) -> Void) async throws -> VideoStatus {
        let req = try request("/v1/video/\(jobId)", timeout: 120)
        let (bytes, resp) = try await session.bytes(for: req)
        try await Self.check(resp, bytes)
        let type = (resp as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Type") ?? ""
        var last = VideoStatus(status: "running")
        if !type.contains("ndjson") {
            var data = Data()
            for try await b in bytes { data.append(b) }
            return try JSONDecoder().decode(VideoStatus.self, from: data)
        }
        for try await line in bytes.lines {
            guard let data = line.data(using: .utf8), let s = try? JSONDecoder().decode(VideoStatus.self, from: data) else { continue }
            last = s
            if s.finished { break }
            onProgress(s)
        }
        return last
    }

    func download(_ url: URL) async throws -> URL {
        let (tmp, resp) = try await session.download(from: url)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code >= 400 { throw GatewayError.http(code, "Não consegui baixar o vídeo.") }
        let target = FileManager.default.temporaryDirectory.appendingPathComponent("lily-\(UUID().uuidString).mp4")
        try? FileManager.default.removeItem(at: target)
        try FileManager.default.moveItem(at: tmp, to: target)
        return target
    }
}
