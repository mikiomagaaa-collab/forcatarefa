import SwiftUI

/// A conversa: perguntar a qualquer hora (a pergunta nova para a anterior), falar e ouvir.
@MainActor
struct ChatView: View {
    let chat: ChatStore
    let settings: AppSettings
    @State private var draft = ""
    @State private var voice = SpeechInput()
    @State private var seenFollowUps = 0
    @FocusState private var typing: Bool

    private var avatarState: AvatarState { voice.listening ? .listening : chat.avatar }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                messagesList
                if let notice = chat.notice {
                    Text(notice).font(.footnote).foregroundStyle(.orange).padding(.horizontal).padding(.top, 4)
                }
                composer
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("L.I.L.Y")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    AvatarView(state: avatarState, level: voice.level).frame(width: 34, height: 34)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { chat.clear() } label: { Image(systemName: "square.and.pencil") }
                        .accessibilityLabel("Nova conversa")
                }
            }
            .onChange(of: chat.followUps) { _, value in
                // Conversa contínua: depois de ler a resposta, ouve de novo uma vez.
                guard value > seenFollowUps else { return }
                seenFollowUps = value
                if settings.continuous && !voice.listening { listen() }
            }
            .onAppear { seenFollowUps = chat.followUps }
        }
    }

    private var messagesList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if chat.messages.isEmpty && chat.streaming == nil {
                        VStack(spacing: 14) {
                            AvatarView(state: avatarState, level: voice.level).frame(width: 190, height: 190)
                            Text("Oi! Sou a L.I.L.Y.").font(.title2.bold())
                            Text("Pergunte qualquer coisa: escreva ou toque no microfone. Uma pergunta nova pode ser feita a qualquer hora.")
                                .font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.top, 40)
                    }
                    ForEach(chat.messages) { message in
                        Bubble(message: message).id(message.id)
                    }
                    if let text = chat.streaming {
                        Bubble(message: ChatMessage(role: .assistant, text: text.isEmpty ? "…" : text, model: chat.modelLabel)).id("streaming")
                    }
                    if voice.listening {
                        Text(voice.partial.isEmpty ? "Ouvindo…" : voice.partial)
                            .font(.callout).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .trailing).id("listening")
                    }
                }
                .padding()
            }
            .onChange(of: chat.messages.count) { _, _ in
                if let last = chat.messages.last?.id { withAnimation { proxy.scrollTo(last, anchor: .bottom) } }
            }
            .onChange(of: chat.streaming) { _, _ in proxy.scrollTo("streaming", anchor: .bottom) }
        }
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Pergunte qualquer coisa", text: $draft, axis: .vertical)
                .lineLimit(1...6)
                .focused($typing)
                .accessibilityIdentifier("pergunta")
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22))
                .onSubmit(sendDraft)
            Button(action: listen) {
                Image(systemName: voice.listening ? "waveform" : "mic.fill")
                    .frame(width: 42, height: 42)
                    .background(voice.listening ? Theme.accent : Color.white.opacity(0.1), in: Circle())
                    .foregroundStyle(voice.listening ? .black : .white)
            }
            .accessibilityLabel(voice.listening ? "Parar de ouvir" : chat.busy ? "Fazer outra pergunta" : "Falar")
            if !draft.trimmingCharacters(in: .whitespaces).isEmpty {
                Button(action: sendDraft) {
                    Image(systemName: "arrow.up").font(.headline).frame(width: 42, height: 42).background(Theme.accent, in: Circle()).foregroundStyle(.black)
                }
                .accessibilityLabel(chat.busy ? "Enviar (para a resposta atual)" : "Enviar")
            } else if chat.busy {
                Button { chat.stop() } label: {
                    Image(systemName: "stop.fill").frame(width: 42, height: 42).background(Color.white.opacity(0.15), in: Circle())
                }
                .accessibilityLabel("Parar a resposta")
            }
        }
        .padding(.horizontal).padding(.vertical, 8)
    }

    private func sendDraft() {
        let text = draft
        draft = ""
        chat.send(text)
    }

    private func listen() {
        chat.speaker.stop()
        Task {
            await voice.start(onFinal: { text in chat.send(text, viaVoice: true) }, onError: { chat.notice = $0 })
        }
    }
}

@MainActor
struct Bubble: View {
    let message: ChatMessage

    var body: some View {
        let mine = message.role == .user
        VStack(alignment: mine ? .trailing : .leading, spacing: 4) {
            Text(rendered)
                .textSelection(.enabled)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(mine ? Theme.accent.opacity(0.85) : Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
                .foregroundStyle(mine ? Color.black : (message.status == .error ? Color.orange : Color.white))
            if !mine {
                HStack(spacing: 8) {
                    if let model = message.model { Text(model) }
                    if message.status == .stopped { Text("parada") }
                }
                .font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
    }

    private var rendered: AttributedString {
        (try? AttributedString(markdown: message.text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(message.text)
    }
}
