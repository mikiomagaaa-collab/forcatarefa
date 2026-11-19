import XCTest
@testable import LILY

/// As regras que o app de iPhone divide com o Android e a Web (mesmo comportamento nos três).
final class LogicTests: XCTestCase {
    func testPedacosDoOpenRouterEDoWorkersAI() {
        XCTAssertEqual(SSE.parse(#"data: {"choices":[{"delta":{"content":"Olá"}}]}"#)?.delta, "Olá")
        XCTAssertEqual(SSE.parse(#"data: {"response":"Oi"}"#)?.delta, "Oi")
        XCTAssertNil(SSE.parse(#"data: {"choices":[{"delta":{"reasoning_content":"pensando"}}]}"#)?.delta)
        XCTAssertEqual(SSE.parse("data: [DONE]")?.done, true)
        XCTAssertNil(SSE.parse(": OPENROUTER PROCESSING"))
        XCTAssertEqual(SSE.parse(#"data: {"error":{"message":"sem créditos"}}"#)?.error, "sem créditos")
        XCTAssertEqual(SSE.parse(#"data: {"response":"","usage":{"prompt_tokens":10,"completion_tokens":4}}"#)?.completionTokens, 4)
    }

    func testModeloGratisVemPrimeiroMenosNaQualidade() {
        let free = RemoteModel(id: "free/gpt-oss-20b", name: "L.I.L.Y Online (grátis, beta)", tier: "fast", roles: ["chat"], priceIn: 0, priceOut: 0, free: true)
        let sonnet = RemoteModel(id: "anthropic/claude-sonnet", name: "Anthropic: Claude Sonnet", tier: "strong", roles: ["chat"], priceIn: 3, priceOut: 15)
        let opus = RemoteModel(id: "anthropic/claude-opus", name: "Anthropic: Claude Opus", tier: "frontier", roles: ["chat"], priceIn: 5, priceOut: 25)
        XCTAssertEqual(Router.pick([sonnet, opus, free], profile: .balanced)?.id, free.id)
        XCTAssertEqual(Router.pick([sonnet, opus, free], profile: .quality)?.id, opus.id)
        XCTAssertEqual(Router.pick([sonnet, opus], profile: .balanced)?.id, sonnet.id)
        XCTAssertNil(Router.pick([RemoteModel(id: "x", name: "x", roles: ["review"])], profile: .balanced))
        XCTAssertEqual(sonnet.label, "Claude Sonnet")
    }

    func testHistoricoAlternaLimitaENaoLevaRespostaParada() {
        let msgs = [
            ChatMessage(role: .user, text: "Quem é o Neymar?"),
            ChatMessage(role: .assistant, text: "Um jogador de futebol brasileiro."),
            ChatMessage(role: .user, text: "Conte a história do Brasil"),
            ChatMessage(role: .assistant, text: "Em 1500…", status: .stopped),
            ChatMessage(role: .user, text: "Quantos anos ele tem?"),
        ]
        // A última (a pergunta atual) sai do histórico; a resposta parada não entra.
        let h = History.build(Array(msgs.dropLast()), maxTurns: 8, maxChars: 10_000)
        XCTAssertEqual(h.map(\.role), ["user", "assistant"])
        XCTAssertEqual(h.first?.content, "Quem é o Neymar?")
        let short = History.build(msgs, maxTurns: 2, maxChars: 10_000)
        XCTAssertTrue(short.count <= 2)
        XCTAssertNotEqual(short.last?.role, "user")
    }

    func testSoHttps() {
        XCTAssertNotNil(GatewayURL.normalize("https://lily-gateway.x.workers.dev/"))
        XCTAssertNil(GatewayURL.normalize("http://lily-gateway.x.workers.dev"))
        XCTAssertNotNil(GatewayURL.normalize("http://127.0.0.1:8787"))
        XCTAssertNil(GatewayURL.normalize("nada"))
        XCTAssertEqual(GatewayURL.normalize("https://g.test///")?.absoluteString, "https://g.test")
    }

    func testRespostasDoGatewayComCamposFaltando() throws {
        let status = try JSONDecoder().decode(GatewayStatus.self, from: Data(#"{"ok":true,"version":"1.2.0","features":{"chatFree":true}}"#.utf8))
        XCTAssertEqual(status.features?.chatFree, true)
        XCTAssertNil(status.features?.image)
        let lines = [#"{"status":"running","elapsed":1,"free":true}"#, #"{"status":"done","elapsed":61,"video":{"url":"https://x.hf.space/gradio_api/file=/tmp/a.mp4"}}"#]
        let decoded = try lines.map { try JSONDecoder().decode(VideoStatus.self, from: Data($0.utf8)) }
        XCTAssertFalse(decoded[0].finished)
        XCTAssertTrue(decoded[1].finished)
        XCTAssertEqual(decoded[1].video?.url, "https://x.hf.space/gradio_api/file=/tmp/a.mp4")
        XCTAssertEqual(DataURL.bytes("data:image/png;base64,AAEC"), Data([0, 1, 2]))
    }

    func testInstrucaoPedeParaResponderAUltimaMensagem() {
        let s = Prompts.system(memories: ["Meu nome é Ana"])
        XCTAssertTrue(s.contains("ÚLTIMA mensagem"))
        XCTAssertTrue(s.contains("- Meu nome é Ana"))
    }
}
