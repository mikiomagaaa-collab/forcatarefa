package app.cristal.cover.ui.glass

import android.graphics.RuntimeShader
import app.cristal.cover.core.Logx

/**
 * Shaders AGSL do Liquid Glass.
 *
 * O shader recebe o fundo (já borrado pelo RenderEffect anterior da cadeia) em `content` e produz
 * o corpo do vidro:
 *  1. máscara de retângulo arredondado por SDF, com borda suavizada;
 *  2. refração: perto da borda, a amostra é deslocada para dentro seguindo um perfil de lente
 *     esférica, o que "dobra" a imagem como em vidro grosso;
 *  3. dispersão: R, G e B são amostrados com deslocamentos levemente diferentes, criando a franja
 *     colorida na borda;
 *  4. brilho especular na borda, mais forte do lado de onde vem a luz, e um contra-brilho fraco
 *     do lado oposto;
 *  5. reação ao toque: uma pequena lente e um brilho sob o dedo.
 */
object GlassShaders {

    const val LIQUID = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uBezel;
uniform float uRefraction;
uniform float uDispersion;
uniform float4 uTint;
uniform float2 uLight;
uniform float uSpecular;
uniform float uSaturation;
uniform float2 uTouch;
uniform float uTouchAmt;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 xy) {
    float2 halfSize = uSize * 0.5;
    float2 p = xy - halfSize;
    float d = sdRoundRect(p, halfSize, uRadius);
    float mask = clamp(0.5 - d, 0.0, 1.0);
    if (mask <= 0.0) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    float2 g = float2(
        sdRoundRect(p + float2(1.0, 0.0), halfSize, uRadius) - sdRoundRect(p - float2(1.0, 0.0), halfSize, uRadius),
        sdRoundRect(p + float2(0.0, 1.0), halfSize, uRadius) - sdRoundRect(p - float2(0.0, 1.0), halfSize, uRadius));
    float gl = length(g);
    float2 n = float2(0.0, 0.0);
    if (gl > 0.0001) {
        n = g / gl;
    }

    float inside = max(-d, 0.0);
    float t = clamp(1.0 - inside / max(uBezel, 1.0), 0.0, 1.0);
    float bend = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    float2 disp = -n * bend * uRefraction;

    float2 tv = xy - uTouch;
    float sigma = max(min(uSize.x, uSize.y) * 0.45, 24.0);
    float tg = uTouchAmt * exp(-dot(tv, tv) / (2.0 * sigma * sigma));
    disp = disp - tv * tg * 0.16;

    float2 lo = float2(0.5, 0.5);
    float2 hi = max(uSize - float2(0.5, 0.5), lo);
    float k = uDispersion * bend;
    float4 cG = content.eval(clamp(xy + disp, lo, hi));
    float4 cR = content.eval(clamp(xy + disp * (1.0 + k), lo, hi));
    float4 cB = content.eval(clamp(xy + disp * (1.0 - k), lo, hi));
    float3 col = float3(cR.r, cG.g, cB.b);

    float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(luma, luma, luma), col, uSaturation);
    col = mix(col, uTint.rgb, uTint.a);

    float rim = 1.0 - smoothstep(0.0, max(uBezel * 0.4, 1.5), inside);
    float hairline = 1.0 - smoothstep(0.0, 1.6, inside);
    float ndl = dot(n, uLight);
    float lit = clamp(ndl, 0.0, 1.0);
    float back = clamp(-ndl, 0.0, 1.0);
    float spec = lit * lit + 0.35 * back * back * back;
    float glow = rim * spec * uSpecular + hairline * (0.10 + 0.25 * spec) * uSpecular;
    col = col + float3(glow, glow, glow);
    col = col + float3(tg, tg, tg) * 0.10;
    col = clamp(col, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));

    return half4(half3(col * mask), half(mask));
}
"""

    /** `null` se o driver recusar o shader; quem chama cai para o vidro com blur simples. */
    fun newLiquid(): RuntimeShader? = try {
        RuntimeShader(LIQUID)
    } catch (t: Throwable) {
        Logx.e("AGSL: o shader do vidro não compilou; usando blur simples", t)
        null
    }

    /** Verificado uma única vez por processo. */
    val liquidSupported: Boolean by lazy { newLiquid() != null }
}
