import SwiftUI

@MainActor
struct SettingsView: View {
    @Bindable var settings: AppSettings
    let chat: ChatStore
    @State private var key = ""
    @State private var testing = false
    @State private var result: String?
    @State private var status: GatewayStatus?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("https://lily-gateway.seu-nome.workers.dev", text: $settings.gatewayURL)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                    SecureField(settings.hasKey ? "Guardada no Keychain (deixe vazio para manter)" : "Chave de acesso (LILY_ACCESS_KEY)", text: $key)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button(testing ? "Testando…" : "Salvar e testar") { test() }.disabled(testing)
                    if settings.hasKey { Button("Remover a chave", role: .destructive) { settings.removeKey() } }
                    if let result { Text(result).font(.footnote).foregroundStyle(result.hasPrefix("Conectado") ? .green : .orange) }
                    if let f = status?.features {
                        Label(f.chatFree == true ? "Conversa grátis: \(status?.free?.chat ?? "ligada")" : "Sem conversa grátis (gateway antigo?)", systemImage: f.chatFree == true ? "checkmark.circle" : "xmark.circle")
                        Label(f.image == true ? "Imagens grátis" : "Sem imagens grátis", systemImage: f.image == true ? "checkmark.circle" : "xmark.circle")
                        Label(f.video == true ? "Vídeo (grátis\(f.videoPaid == true ? " e pago" : ""))" : "Sem vídeo", systemImage: f.video == true ? "checkmark.circle" : "xmark.circle")
                    }
                } header: {
                    Text("Serviço online (L.I.L.Y Gateway)")
                } footer: {
                    Text("O mesmo gateway do app Android e da L.I.L.Y Web. A chave fica no Keychain do iPhone.")
                }

                Section {
                    Toggle("Permitir processamento externo", isOn: $settings.consent)
                    Picker("Respostas", selection: $settings.profile) {
                        ForEach(Router.Profile.allCases) { Text($0.label).tag($0) }
                    }
                } header: {
                    Text("Privacidade e modelos")
                } footer: {
                    Text("O que você manda (texto, descrição de imagem ou vídeo) sai do iPhone e passa pelo seu gateway e pelo provedor. O modelo grátis vem primeiro; \"Qualidade\" usa os pagos, se houver.")
                }

                Section("Voz") {
                    Toggle("Ler as respostas faladas em voz alta", isOn: $settings.speakAnswers)
                    Toggle("Conversa contínua (ouve de novo depois de responder)", isOn: $settings.continuous)
                }

                Section("Sobre") {
                    LabeledContent("Versão", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                    NavigationLink("Termos de uso") { LegalView(title: "Termos de uso", file: "TERMOS-DE-USO") }
                    NavigationLink("Privacidade") { LegalView(title: "Privacidade", file: "PRIVACIDADE") }
                    NavigationLink("Créditos") { LegalView(title: "Créditos", file: "CREDITOS") }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Ajustes")
            .onChange(of: settings.consent) { _, _ in settings.persist() }
            .onChange(of: settings.speakAnswers) { _, _ in settings.persist() }
            .onChange(of: settings.continuous) { _, _ in settings.persist() }
            .onChange(of: settings.profile) { _, _ in settings.persist() }
        }
    }

    private func test() {
        guard settings.url != nil else {
            result = "Endereço inválido: use o endereço https do seu gateway."
            return
        }
        settings.persist()
        settings.saveKey(key)
        key = ""
        testing = true
        Task {
            defer { testing = false }
            do {
                let s = try await chat.gateway.status()
                status = s
                result = "Conectado ao gateway \(s.version ?? "")."
                await chat.refreshModels()
            } catch {
                result = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}

/// Termos, privacidade e créditos (os mesmos arquivos da pasta legal/ do projeto).
@MainActor
struct LegalView: View {
    let title: String
    let file: String

    var body: some View {
        ScrollView {
            Text(content).frame(maxWidth: .infinity, alignment: .leading).padding()
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var content: AttributedString {
        guard let url = Bundle.main.url(forResource: file, withExtension: "md"), let text = try? String(contentsOf: url, encoding: .utf8) else {
            return AttributedString("Documento não encontrado.")
        }
        return (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
    }
}
