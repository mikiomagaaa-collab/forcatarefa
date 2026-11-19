import Foundation
import Observation
import Photos
import UIKit

/// Ajustes do app (preferências) e a conexão com o gateway (chave no Keychain).
@MainActor @Observable
final class AppSettings {
    var gatewayURL: String
    var consent: Bool
    var speakAnswers: Bool
    var continuous: Bool
    var profile: Router.Profile
    var hasKey: Bool

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        gatewayURL = defaults.string(forKey: "gatewayURL") ?? ""
        consent = defaults.bool(forKey: "consent")
        speakAnswers = defaults.object(forKey: "speakAnswers") as? Bool ?? true
        continuous = defaults.object(forKey: "continuous") as? Bool ?? true
        profile = Router.Profile(rawValue: defaults.string(forKey: "profile") ?? "") ?? .balanced
        hasKey = !(Keychain.load() ?? "").isEmpty
    }

    /// Guarda as preferências (as telas chamam a cada mudança).
    func persist() {
        defaults.set(gatewayURL, forKey: "gatewayURL")
        defaults.set(consent, forKey: "consent")
        defaults.set(speakAnswers, forKey: "speakAnswers")
        defaults.set(continuous, forKey: "continuous")
        defaults.set(profile.rawValue, forKey: "profile")
    }

    var url: URL? { GatewayURL.normalize(gatewayURL) }

    func saveKey(_ key: String) {
        let clean = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return }
        Keychain.save(clean)
        hasKey = true
    }

    func removeKey() {
        Keychain.delete()
        hasKey = false
    }
}

/// Salva imagens e vídeos nas Fotos (só a permissão de adicionar).
enum PhotoSaver {
    static func save(image: UIImage) async -> Bool {
        guard await authorized() else { return false }
        return await withCheckedContinuation { cont in
            PHPhotoLibrary.shared().performChanges({ PHAssetChangeRequest.creationRequestForAsset(from: image) }) { ok, _ in cont.resume(returning: ok) }
        }
    }

    static func save(video: URL) async -> Bool {
        guard await authorized() else { return false }
        return await withCheckedContinuation { cont in
            PHPhotoLibrary.shared().performChanges({ _ = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: video) }) { ok, _ in cont.resume(returning: ok) }
        }
    }

    private static func authorized() async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        return status == .authorized || status == .limited
    }
}

/// A conversa: pedaços em tempo real, pergunta nova interrompe a anterior, voz e histórico salvo.
@MainActor @Observable
final class ChatStore {
    var messages: [ChatMessage] = []
    var streaming: String?
    var notice: String?
    var modelLabel: String?
    var avatar: AvatarState = .idle
    /// Sobe quando uma resposta falada termina de ser lida (a tela volta a ouvir, se ligado).
    var followUps = 0

    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var models: [RemoteModel] = []
    let settings: AppSettings
    let gateway: GatewayClient
    let speaker = Speaker()
    private let file: URL

    var busy: Bool { streaming != nil }

    init(settings: AppSettings, gateway: GatewayClient, file: URL? = nil) {
        self.settings = settings
        self.gateway = gateway
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.file = file ?? dir.appendingPathComponent("conversa.json")
        if let data = try? Data(contentsOf: self.file), let saved = try? JSONDecoder().decode([ChatMessage].self, from: data) {
            messages = saved
        }
    }

    func clear() {
        stop()
        messages = []
        save()
    }

    func stop() {
        task?.cancel()
        speaker.stop()
    }

    /// Manda uma pergunta. Se ainda houver resposta saindo, ela é parada e a NOVA é respondida.
    func send(_ text: String, viaVoice: Bool = false) {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        speaker.stop()
        let previous = task
        previous?.cancel()
        generation += 1
        let gen = generation
        task = Task { [weak self] in
            await previous?.value
            await self?.answer(clean, viaVoice: viaVoice, generation: gen)
        }
    }

    private func chooseModel() async throws -> RemoteModel {
        guard gateway.configured else { throw GatewayError.notConfigured }
        guard settings.consent else { throw GatewayError.consent }
        if models.isEmpty { models = try await gateway.models() }
        guard let model = Router.pick(models, profile: settings.profile) else {
            throw GatewayError.server("O seu gateway ainda não tem modelos de conversa (publique a versão 1.2 ou mais nova).")
        }
        return model
    }

    func refreshModels() async {
        models = (try? await gateway.models()) ?? models
    }

    private func answer(_ question: String, viaVoice: Bool, generation gen: Int) async {
        messages.append(ChatMessage(role: .user, text: question))
        save()
        streaming = ""
        avatar = .thinking
        var text = ""
        var status = MessageStatus.done
        var label: String?
        do {
            let model = try await chooseModel()
            label = model.isFree ? "\(model.label) · grátis" : "\(model.label) · online"
            modelLabel = label
            let free = model.isFree
            let history = History.build(Array(messages.dropLast()), maxTurns: free ? 8 : 24, maxChars: free ? 24_000 : 120_000)
            let wire = [WireMessage(role: "system", content: Prompts.system())] + history + [WireMessage(role: "user", content: question)]
            for try await piece in gateway.chat(model: model.id, messages: wire) {
                text += piece
                streaming = text
                avatar = .speaking
            }
            if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                status = .error
                text = "O modelo não devolveu nenhum texto. Tente reformular a pergunta."
            }
        } catch is CancellationError {
            status = .stopped
        } catch let e as URLError where e.code == .cancelled {
            status = .stopped
        } catch {
            if Task.isCancelled {
                status = .stopped
            } else {
                status = .error
                text = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
        streaming = nil
        messages.append(ChatMessage(role: .assistant, text: text.trimmingCharacters(in: .whitespacesAndNewlines), status: status, model: label))
        save()
        avatar = status == .done ? .success : status == .error ? .error : .idle
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            if self?.busy == false { self?.avatar = .idle }
        }
        if gen == generation { task = nil }
        guard viaVoice, status == .done else { return }
        if settings.speakAnswers {
            avatar = .speaking
            speaker.speak(text) { [weak self] in
                guard let self else { return }
                self.avatar = .idle
                if self.settings.continuous { self.followUps += 1 }
            }
        } else if settings.continuous {
            followUps += 1
        }
    }

    private func save() {
        let keep = Array(messages.suffix(200))
        if let data = try? JSONEncoder().encode(keep) { try? data.write(to: file, options: .atomic) }
    }
}
