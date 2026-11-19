import SwiftUI

/// Os estados da avatar (os mesmos do app Android e da Web).
enum AvatarState: Equatable {
    case idle, listening, thinking, speaking, working, success, error

    var label: String {
        switch self {
        case .idle: return "Pronta"
        case .listening: return "Ouvindo"
        case .thinking: return "Pensando"
        case .speaking: return "Falando"
        case .working: return "Trabalhando"
        case .success: return "Pronto!"
        case .error: return "Não deu certo"
        }
    }

    var glow: Color {
        switch self {
        case .listening: return Color(red: 0.36, green: 0.86, blue: 1.0)
        case .thinking, .working: return Color(red: 0.72, green: 0.61, blue: 1.0)
        case .speaking: return Color(red: 1.0, green: 0.55, blue: 0.85)
        case .success: return Color(red: 0.35, green: 0.95, blue: 0.6)
        case .error: return Color(red: 1.0, green: 0.45, blue: 0.4)
        case .idle: return Color(red: 0.6, green: 0.5, blue: 1.0)
        }
    }
}

/// A L.I.L.Y desenhada em código (personagem original, sem imagens de terceiros): respira, pisca,
/// olha em volta, mexe a boca ao falar e muda a luz conforme o estado.
struct AvatarView: View {
    var state: AvatarState
    var level: Double = 0

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                AvatarDrawing.draw(&context, size: size, time: t, state: state, level: level)
            }
        }
        .accessibilityLabel("L.I.L.Y · \(state.label)")
    }
}

enum AvatarDrawing {
    static func draw(_ ctx: inout GraphicsContext, size: CGSize, time t: Double, state: AvatarState, level: Double) {
        let s = min(size.width, size.height)
        let cx = size.width / 2
        let breath = sin(t * 1.6) * 0.012 * s
        let cy = size.height * 0.52 + breath

        // Aura (luz do estado).
        let pulse = state == .thinking || state == .working ? 0.08 * sin(t * 4) : 0
        let auraR = s * (0.48 + pulse)
        ctx.fill(
            Path(ellipseIn: CGRect(x: cx - auraR, y: cy - auraR, width: auraR * 2, height: auraR * 2)),
            with: .radialGradient(Gradient(colors: [state.glow.opacity(0.45), state.glow.opacity(0)]), center: CGPoint(x: cx, y: cy), startRadius: s * 0.1, endRadius: auraR)
        )

        // Cabelo (atrás).
        let hair = Color(red: 0.55, green: 0.42, blue: 0.95)
        let hairRect = CGRect(x: cx - s * 0.27, y: cy - s * 0.33, width: s * 0.54, height: s * 0.62)
        ctx.fill(Path(roundedRect: hairRect, cornerRadius: s * 0.24), with: .linearGradient(Gradient(colors: [hair, hair.opacity(0.75)]), startPoint: CGPoint(x: cx, y: hairRect.minY), endPoint: CGPoint(x: cx, y: hairRect.maxY)))

        // Rosto.
        let face = CGRect(x: cx - s * 0.2, y: cy - s * 0.22, width: s * 0.4, height: s * 0.46)
        ctx.fill(Path(ellipseIn: face), with: .color(Color(red: 0.98, green: 0.88, blue: 0.86)))

        // Franja.
        var fringe = Path()
        fringe.move(to: CGPoint(x: face.minX - s * 0.01, y: face.minY + s * 0.12))
        fringe.addQuadCurve(to: CGPoint(x: face.maxX + s * 0.01, y: face.minY + s * 0.12), control: CGPoint(x: cx, y: face.minY - s * 0.1))
        fringe.addLine(to: CGPoint(x: face.maxX, y: face.minY + s * 0.02))
        fringe.addQuadCurve(to: CGPoint(x: face.minX, y: face.minY + s * 0.02), control: CGPoint(x: cx, y: face.minY - s * 0.06))
        ctx.fill(fringe, with: .color(hair))

        // Olhos: piscam a cada ~4 s e olham para os lados quando ela pensa.
        let blinkPhase = t.truncatingRemainder(dividingBy: 4.2)
        let open = blinkPhase < 0.12 ? max(0.08, abs(blinkPhase - 0.06) / 0.06) : 1
        let look = state == .thinking ? sin(t * 1.3) * s * 0.012 : 0
        let eyeY = cy - s * 0.02
        let iris = Color(red: 0.28, green: 0.75, blue: 0.95)
        for dx in [-s * 0.085, s * 0.085] {
            let w = s * 0.07
            let h = s * 0.085 * open
            let eye = CGRect(x: cx + dx - w / 2, y: eyeY - h / 2, width: w, height: h)
            ctx.fill(Path(ellipseIn: eye), with: .color(.white))
            let ir = CGRect(x: cx + dx - w * 0.33 + look, y: eyeY - h * 0.36, width: w * 0.66, height: h * 0.72)
            ctx.fill(Path(ellipseIn: ir), with: .color(iris))
            let pupil = CGRect(x: cx + dx - w * 0.14 + look, y: eyeY - h * 0.16, width: w * 0.28, height: h * 0.32)
            ctx.fill(Path(ellipseIn: pupil), with: .color(Color(red: 0.08, green: 0.1, blue: 0.2)))
        }

        // Boca: abre com a fala; sorriso no sucesso.
        let talking = state == .speaking ? (0.35 + 0.65 * abs(sin(t * 11))) * max(0.4, level + 0.4) : 0
        let mouthY = cy + s * 0.11
        if talking > 0.05 {
            let mh = s * 0.045 * talking
            ctx.fill(Path(roundedRect: CGRect(x: cx - s * 0.035, y: mouthY - mh / 2, width: s * 0.07, height: mh), cornerRadius: mh / 2), with: .color(Color(red: 0.75, green: 0.3, blue: 0.4)))
        } else {
            var smile = Path()
            let wide = state == .success ? 0.06 : 0.04
            smile.move(to: CGPoint(x: cx - s * wide, y: mouthY))
            smile.addQuadCurve(to: CGPoint(x: cx + s * wide, y: mouthY), control: CGPoint(x: cx, y: mouthY + s * (state == .error ? -0.02 : 0.035)))
            ctx.stroke(smile, with: .color(Color(red: 0.75, green: 0.3, blue: 0.4)), style: StrokeStyle(lineWidth: s * 0.012, lineCap: .round))
        }

        // Anel de luz quando ouve (cresce com a voz) ou pensa.
        if state == .listening || state == .thinking {
            let r = s * (0.3 + 0.04 * level + 0.01 * sin(t * 3))
            ctx.stroke(Path(ellipseIn: CGRect(x: cx - r, y: cy - r * 1.1, width: r * 2, height: r * 2.2)), with: .color(state.glow.opacity(0.7)), lineWidth: s * 0.008)
        }

        // Gola da roupa (fechada).
        var collar = Path()
        collar.move(to: CGPoint(x: cx - s * 0.22, y: face.maxY + s * 0.12))
        collar.addQuadCurve(to: CGPoint(x: cx + s * 0.22, y: face.maxY + s * 0.12), control: CGPoint(x: cx, y: face.maxY - s * 0.02))
        collar.addLine(to: CGPoint(x: cx + s * 0.26, y: size.height))
        collar.addLine(to: CGPoint(x: cx - s * 0.26, y: size.height))
        collar.closeSubpath()
        ctx.fill(collar, with: .color(Color(red: 0.14, green: 0.13, blue: 0.24)))
        ctx.stroke(Path(CGRect(x: cx - s * 0.002, y: face.maxY + s * 0.06, width: s * 0.004, height: s * 0.08)), with: .color(state.glow), lineWidth: s * 0.006)
    }
}
