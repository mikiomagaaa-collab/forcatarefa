import Foundation

/// O que o L.I.L.Y Gateway tem ligado (os campos podem faltar em gateways antigos).
struct GatewayFeatures: Codable, Equatable {
    var code: Bool?
    var video: Bool?
    var videoPaid: Bool?
    var image: Bool?
    var chatFree: Bool?
    var search: Bool?
    var repos: Bool?
}

struct FreeImages: Codable, Equatable {
    var model: String?
    var perDay: Int?
    var usedToday: Int?
}

struct GatewayFree: Codable, Equatable {
    var chat: String?
    var image: FreeImages?
}

struct GatewayStatus: Codable, Equatable {
    var ok: Bool?
    var version: String?
    var features: GatewayFeatures?
    var free: GatewayFree?
}

/// Um modelo online (lido ao vivo no gateway).
struct RemoteModel: Codable, Equatable, Identifiable, Hashable {
    var id: String
    var name: String
    var vendor: String?
    var tier: String?
    var roles: [String]?
    var context: Int?
    var priceIn: Double?
    var priceOut: Double?
    var free: Bool?

    var isFree: Bool { free == true }
    /// "Anthropic: Claude Sonnet" → "Claude Sonnet".
    var label: String {
        if let r = name.range(of: ": ") { return String(name[r.upperBound...]) }
        return name
    }
    var typicalUsd: Double { (2_000 * (priceIn ?? 0) + 800 * (priceOut ?? 0)) / 1_000_000 }
}

struct ModelList: Codable { var models: [RemoteModel] }

/// Mensagem no formato que o gateway espera.
struct WireMessage: Codable, Equatable {
    var role: String
    var content: String
}

struct ChatBody: Encodable {
    var model: String
    var messages: [WireMessage]
    var maxTokens: Int
    var temperature: Double
}

struct OnlineImage: Codable {
    var image: String
    var model: String?
    var usedToday: Int?
    var perDay: Int?
}

struct VideoModel: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var modes: [String]?
    var durations: [Int]?
    var aspects: [String]?
    var resolutions: [String]?
    var free: Bool?
    var summary: String?
}

struct VideoModelList: Codable { var models: [VideoModel] }

struct VideoSubmitted: Codable {
    var jobId: String
    var model: String?
    var free: Bool?
    var stream: Bool?
    var typicalSeconds: Int?
    var estimatedUsd: Double?
}

struct VideoFile: Codable, Equatable { var url: String }

struct VideoStatus: Codable, Equatable {
    /// queued, running, done ou failed.
    var status: String
    var elapsed: Int?
    var typicalSeconds: Int?
    var video: VideoFile?
    var error: String?

    var finished: Bool { status == "done" || status == "failed" }
}

enum Role: String, Codable { case user, assistant }
enum MessageStatus: String, Codable { case done, stopped, error }

struct ChatMessage: Codable, Identifiable, Equatable {
    var id = UUID()
    var role: Role
    var text: String
    var status: MessageStatus = .done
    var model: String?
    var date = Date()
}
