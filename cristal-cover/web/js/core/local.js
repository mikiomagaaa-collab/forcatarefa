// IA no seu computador: modelos que rodam dentro do navegador com WebGPU (WebLLM, MLC AI, Apache 2.0).
// Nada é carregado até você escolher um modelo; depois do primeiro download, ele fica no cache do navegador.

const WEBLLM = 'https://cdn.jsdelivr.net/npm/@mlc-ai/web-llm@0.2.87/+esm';

/** Os modelos que vale oferecer (se existirem na lista oficial da versão carregada). */
const PREFERRED = [
  { id: 'Qwen3-1.7B-q4f16_1-MLC', name: 'Qwen3 1.7B', note: 'Bom português, leve' },
  { id: 'gemma-4-E2B-it-q4f16_1-MLC', name: 'Gemma 4 E2B', note: 'Google, equilibrado' },
  { id: 'Llama-3.2-3B-Instruct-q4f16_1-MLC', name: 'Llama 3.2 3B', note: 'Meta, mais capaz' },
  { id: 'Phi-4-mini-instruct-q4f16_1-MLC', name: 'Phi-4 mini', note: 'Microsoft, raciocínio' },
  { id: 'Qwen3-0.6B-q4f16_1-MLC', name: 'Qwen3 0.6B', note: 'O menor, para PCs simples' },
];

let lib = null;
let engine = null;
let loadedId = null;

export function supported() {
  return typeof navigator !== 'undefined' && !!navigator.gpu;
}

async function webllm() {
  if (!lib) lib = await import(/* @vite-ignore */ WEBLLM);
  return lib;
}

/** Os modelos disponíveis de verdade (lidos na lista oficial da biblioteca), com a memória de vídeo pedida. */
export async function models() {
  const w = await webllm();
  const list = w.prebuiltAppConfig?.model_list || [];
  return PREFERRED.map((p) => {
    const rec = list.find((m) => m.model_id === p.id);
    return rec ? { ...p, vramMB: Math.round(rec.vram_required_MB || 0) } : null;
  }).filter(Boolean);
}

export function current() {
  return loadedId;
}

/** Baixa (na primeira vez) e carrega o modelo. onProgress(fração, texto). */
export async function load(id, onProgress) {
  if (!supported()) throw new Error('Este navegador não tem WebGPU. Use o Chrome ou o Edge atualizados.');
  if (loadedId === id && engine) return;
  const w = await webllm();
  if (engine) await engine.unload?.();
  engine = await w.CreateMLCEngine(id, { initProgressCallback: (r) => onProgress?.(r.progress ?? 0, r.text || '') });
  loadedId = id;
}

/** Resposta em pedaços, no mesmo formato do online: onText(acumulado). */
export async function chat(messages, { onText, signal, temperature = 0.7 } = {}) {
  if (!engine) throw new Error('Nenhum modelo do navegador carregado.');
  const stream = await engine.chat.completions.create({ messages, stream: true, temperature });
  let text = '';
  for await (const chunk of stream) {
    if (signal?.aborted) {
      engine.interruptGenerate?.();
      break;
    }
    const d = chunk.choices?.[0]?.delta?.content;
    if (d) {
      text += d;
      onText?.(text);
    }
  }
  return { text, usage: null, model: loadedId };
}
