# Como contribuir

Obrigado por querer ajudar a L.I.L.Y a ser útil para mais gente.

## Antes de começar

- Leia o `README.md`, a `docs/GUIA.md` e o plano em `docs/ROADMAP.md`.
- Ao contribuir, você concorda em licenciar a sua contribuição sob a Apache License 2.0 (a mesma do
  projeto) e em seguir os [Termos de Uso](legal/TERMOS-DE-USO.md).

## Princípios do projeto

1. **Funcionar de verdade.** Nada de telas demonstrativas, botões sem função ou dados falsos que
   pareçam reais. Se algo depende de um serviço externo, a tela diz isso.
2. **No aparelho primeiro.** O que pode rodar localmente roda localmente; o que vai para fora é
   opcional e avisado.
3. **Honestidade.** Registre limitações reais. Não declare compatibilidade sem comprovação.
4. **Privacidade e segurança.** Nada de rastreadores, chaves no app ou acesso sem permissão.
5. **Acessível e em português claro.** Textos simples, sem jargão desnecessário.

## Como enviar uma mudança

1. Crie um ramo a partir do principal.
2. Rode os testes: `./gradlew testDebugUnitTest` (apps) e `node --test test/*.test.js` (gateway).
3. Descreva no pull request o que mudou, como testou e o que não foi possível testar.

## Relatar problemas

Use as issues do repositório. Para falhas da L.I.L.Y AI, anexe o relatório do app
**L.I.L.Y Diagnóstico** (Compartilhar). Problemas de segurança: veja `SEGURANCA.md`.
