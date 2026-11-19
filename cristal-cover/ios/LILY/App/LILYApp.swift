import SwiftUI

@main
@MainActor
struct LILYApp: App {
    @State private var settings: AppSettings
    @State private var chat: ChatStore

    init() {
        let settings = AppSettings()
        let gateway = GatewayClient(baseURL: { GatewayURL.normalize(UserDefaults.standard.string(forKey: "gatewayURL") ?? "") }, key: { Keychain.load() })
        _settings = State(initialValue: settings)
        _chat = State(initialValue: ChatStore(settings: settings, gateway: gateway))
    }

    var body: some Scene {
        WindowGroup {
            RootView(settings: settings, chat: chat)
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
    }
}

enum Theme {
    static let accent = Color(red: 0.72, green: 0.61, blue: 1.0)
    static let background = LinearGradient(
        colors: [Color(red: 0.07, green: 0.05, blue: 0.14), Color(red: 0.04, green: 0.04, blue: 0.08)],
        startPoint: .top,
        endPoint: .bottom
    )
}

@MainActor
struct RootView: View {
    let settings: AppSettings
    let chat: ChatStore

    var body: some View {
        TabView {
            ChatView(chat: chat, settings: settings)
                .tabItem { Label("Conversa", systemImage: "bubble.left.and.bubble.right") }
            CreateView(gateway: chat.gateway, settings: settings)
                .tabItem { Label("Criar", systemImage: "sparkles") }
            SettingsView(settings: settings, chat: chat)
                .tabItem { Label("Ajustes", systemImage: "gearshape") }
        }
    }
}
