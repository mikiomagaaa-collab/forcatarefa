import XCTest

/// Abre a L.I.L.Y num iPhone simulado e passa pelas três abas (sem gateway: o que a pessoa vê na
/// primeira abertura). As capturas ficam no resultado dos testes.
final class LILYUITests: XCTestCase {
    func testAbreAsTresTelas() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Oi! Sou a L.I.L.Y."].waitForExistence(timeout: 15))
        attach(app, "1-conversa")

        app.tabBars.buttons["Criar"].tap()
        XCTAssertTrue(app.buttons["Gerar imagem"].waitForExistence(timeout: 10))
        attach(app, "2-criar")

        app.tabBars.buttons["Ajustes"].tap()
        XCTAssertTrue(app.switches["Permitir processamento externo"].waitForExistence(timeout: 10))
        attach(app, "3-ajustes")

        // Sem gateway, perguntar explica o que falta (sem travar nem fechar).
        app.tabBars.buttons["Conversa"].tap()
        // O campo de várias linhas aparece como campo de texto ou área de texto, conforme o iOS.
        let field = app.descendants(matching: .any).matching(identifier: "pergunta").firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText("Oi")
        app.buttons["Enviar"].tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Conecte o L.I.L.Y Gateway")).firstMatch.waitForExistence(timeout: 10))
        attach(app, "4-sem-gateway")
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
