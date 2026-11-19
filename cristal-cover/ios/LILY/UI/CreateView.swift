import AVKit
import SwiftUI

/// Criar imagens e vídeos pelo gateway: grátis (Workers AI e Hugging Face) quando disponível.
@MainActor
struct CreateView: View {
    let gateway: GatewayClient
    let settings: AppSettings

    enum Kind: String, CaseIterable, Identifiable {
        case image = "Imagem", video = "Vídeo"
        var id: String { rawValue }
    }

    @State private var kind: Kind = .image
    @State private var prompt = ""
    @State private var working = false
    @State private var progress: String?
    @State private var image: UIImage?
    @State private var videoURL: URL?
    @State private var message: String?
    @State private var duration = 4
    @State private var aspect = "9:16"
    @State private var job: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("O que criar", selection: $kind) {
                        ForEach(Kind.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    TextField(kind == .image ? "Ex.: um farol de vidro líquido ao pôr do sol" : "Ex.: uma gota de vidro caindo em câmera lenta", text: $prompt, axis: .vertical)
                        .lineLimit(2...5)
                    if kind == .video {
                        Picker("Duração", selection: $duration) { ForEach([2, 4, 6, 8], id: \.self) { Text("\($0) s").tag($0) } }
                        Picker("Formato", selection: $aspect) {
                            Text("Retrato 9:16").tag("9:16")
                            Text("Paisagem 16:9").tag("16:9")
                            Text("Quadrado 1:1").tag("1:1")
                        }
                    }
                } footer: {
                    Text(kind == .image
                         ? "Grátis pelo Workers AI do seu gateway (até ~150 por dia), 1024 × 1024."
                         : "Grátis pela cota diária de GPU do Hugging Face (1 a 2 vídeos por dia). Deixe o app aberto até o fim: se a conexão cair, o vídeo é cancelado.")
                }
                Section {
                    if working {
                        HStack {
                            ProgressView()
                            Text(progress ?? "Gerando…").foregroundStyle(.secondary)
                            Spacer()
                            Button("Cancelar") { job?.cancel() }
                        }
                    } else {
                        Button(kind == .image ? "Gerar imagem" : "Gerar vídeo") { generate() }
                            .disabled(prompt.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    if let message { Text(message).font(.footnote).foregroundStyle(.secondary) }
                }
                if let image {
                    Section("Resultado") {
                        Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 16))
                        Button("Salvar nas Fotos") { Task { message = await PhotoSaver.save(image: image) ? "Salva nas Fotos." : "Não consegui salvar (permita o acesso às Fotos)." } }
                        ShareLink(item: Image(uiImage: image), preview: SharePreview("L.I.L.Y", image: Image(uiImage: image)))
                    }
                }
                if let videoURL {
                    Section("Vídeo") {
                        VideoPlayer(player: AVPlayer(url: videoURL)).frame(height: 360).clipShape(RoundedRectangle(cornerRadius: 16))
                        Button("Salvar nas Fotos") { Task { message = await PhotoSaver.save(video: videoURL) ? "Salvo nas Fotos." : "Não consegui salvar (permita o acesso às Fotos)." } }
                        ShareLink(item: videoURL)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Criar")
        }
    }

    private func generate() {
        guard gateway.configured else { message = GatewayError.notConfigured.errorDescription; return }
        guard settings.consent else { message = GatewayError.consent.errorDescription; return }
        let text = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        working = true
        message = nil
        progress = nil
        job = Task {
            defer { working = false }
            do {
                if kind == .image {
                    let out = try await gateway.image(prompt: text)
                    guard let data = DataURL.bytes(out.image), let picture = UIImage(data: data) else { throw GatewayError.badResponse }
                    image = picture
                    videoURL = nil
                    if let used = out.usedToday, let per = out.perDay { message = "Hoje: \(used) de \(per) imagens grátis." }
                } else {
                    let models = try await gateway.videoModels()
                    guard let model = models.first(where: { $0.free == true }) ?? models.first else { throw GatewayError.server("O seu gateway ainda não tem vídeo.") }
                    let sub = try await gateway.videoSubmit(model: model.id, prompt: text, duration: duration, aspect: aspect)
                    progress = "Na fila…"
                    var status = try await gateway.videoStatus(jobId: sub.jobId) { s in
                        Task { @MainActor in progress = "Gerando · \(s.elapsed ?? 0) s" }
                    }
                    // Pagos (fal.ai): a fila responde aos poucos; pergunta de novo até terminar.
                    while !status.finished {
                        try await Task.sleep(nanoseconds: 5_000_000_000)
                        status = try await gateway.videoStatus(jobId: sub.jobId) { _ in }
                        progress = "Gerando · \(status.elapsed ?? 0) s"
                    }
                    guard status.status == "done", let link = status.video?.url, let url = URL(string: link) else {
                        throw GatewayError.server(status.error ?? "O vídeo não foi gerado.")
                    }
                    progress = "Baixando o vídeo…"
                    videoURL = try await gateway.download(url)
                    image = nil
                    message = model.free == true ? "Vídeo grátis pronto." : "Vídeo pronto."
                }
            } catch is CancellationError {
                message = "Cancelado."
            } catch {
                message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}
