import AVFoundation
import Foundation
import Observation
import Speech

/// Lê as respostas em voz alta (vozes do iPhone, português do Brasil).
@MainActor
final class Speaker: NSObject, AVSpeechSynthesizerDelegate {
    private let synth = AVSpeechSynthesizer()
    private var onDone: (@MainActor () -> Void)?

    override init() {
        super.init()
        synth.delegate = self
    }

    var speaking: Bool { synth.isSpeaking }

    /// [then] roda só quando a leitura chega ao fim (não quando é interrompida).
    func speak(_ text: String, then: (@MainActor () -> Void)? = nil) {
        stop()
        onDone = then
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let utterance = AVSpeechUtterance(string: String(text.prefix(4_000)))
        utterance.voice = AVSpeechSynthesisVoice(language: "pt-BR")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synth.speak(utterance)
    }

    func stop() {
        onDone = nil
        if synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            let done = self.onDone
            self.onDone = nil
            done?()
        }
    }
}

/// Ouve só depois de um toque (ou da conversa contínua que a pessoa ligou) e para no silêncio.
@MainActor @Observable
final class SpeechInput {
    var listening = false
    var partial = ""
    var level: Double = 0

    @ObservationIgnored private let engine = AVAudioEngine()
    @ObservationIgnored private var request: SFSpeechAudioBufferRecognitionRequest?
    @ObservationIgnored private var recognition: SFSpeechRecognitionTask?
    @ObservationIgnored private var silence: Task<Void, Never>?
    @ObservationIgnored private var onFinal: ((String) -> Void)?
    @ObservationIgnored private var delivered = false

    static func authorize() async -> Bool {
        let speech: Bool = await withCheckedContinuation { cont in
            SFSpeechRecognizer.requestAuthorization { cont.resume(returning: $0 == .authorized) }
        }
        guard speech else { return false }
        return await AVAudioApplication.requestRecordPermission()
    }

    func start(onFinal: @escaping (String) -> Void, onError: @escaping (String) -> Void) async {
        if listening { stop(); return }
        guard await Self.authorize() else {
            onError("Permita o microfone e o reconhecimento de fala nos Ajustes do iPhone.")
            return
        }
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "pt-BR")), recognizer.isAvailable else {
            onError("O reconhecimento de fala em português não está disponível agora.")
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            onError("Não consegui usar o microfone: \(error.localizedDescription)")
            return
        }
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
        request = req
        self.onFinal = onFinal
        delivered = false
        partial = ""
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.removeTap(onBus: 0)
        // O áudio chega numa linha do sistema (não na principal): só repassa e mede o volume.
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { @Sendable buffer, _ in
            req.append(buffer)
            guard let data = buffer.floatChannelData?[0] else { return }
            let n = Int(buffer.frameLength)
            var sum: Float = 0
            for i in 0..<n { sum += data[i] * data[i] }
            let rms = n > 0 ? sqrt(sum / Float(n)) : 0
            Task { @MainActor in self.level = Double(min(1, rms * 12)) }
        }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            onError("Não consegui abrir o microfone: \(error.localizedDescription)")
            return
        }
        listening = true
        recognition = recognizer.recognitionTask(with: req) { @Sendable result, error in
            let text = result?.bestTranscription.formattedString
            let final = (result?.isFinal ?? false) || error != nil
            Task { @MainActor in
                if let text, !text.isEmpty {
                    self.partial = text
                    self.armSilence()
                }
                if final { self.finish() }
            }
        }
        armSilence(seconds: 6)
    }

    /// Para depois de um tempo sem fala nova (1,4 s depois da última palavra; 6 s se ninguém falar).
    private func armSilence(seconds: Double = 1.4) {
        silence?.cancel()
        silence = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            if !Task.isCancelled { self?.finish() }
        }
    }

    private func finish() {
        guard listening else { return }
        let text = partial.trimmingCharacters(in: .whitespacesAndNewlines)
        stop()
        if !delivered, !text.isEmpty {
            delivered = true
            onFinal?(text)
        }
    }

    func stop() {
        silence?.cancel()
        silence = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        recognition?.cancel()
        recognition = nil
        request = nil
        listening = false
        level = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
