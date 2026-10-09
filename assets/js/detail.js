import { backendReady, request, renderText } from './api.js';
import { loadClassifications, appendLabels } from './classifications.js';
const out = document.querySelector('#proposal-detail');
async function init() {
  const id = Number(new URLSearchParams(location.search).get('id'));
  const data = await (await fetch('assets/data/proposals.json')).json();
  const p = data.proposals.find(p => p.id === id);
  if (!p) { out.replaceChildren(renderText('h1', 'Proposta não encontrada')); return; }
  document.title = `${p.title} | FORÇA TAREFA`;
  out.replaceChildren(renderText('p', `PROPOSTA ${String(id).padStart(2,'0')}`, 'eyebrow'));
  const { items } = await loadClassifications(); const classification = items.get(id);
  appendLabels(out, classification);
  out.append(renderText('h1', p.title), renderText('p', data.categories.find(c => c.id === p.category).title, 'category-label'), renderText('p', p.description, 'lead'));
  if (classification?.early) out.append(renderText('h2', 'Primeiros passos planejados'), renderText('p', classification.initial_action), renderText('p', 'Planejamento para um eventual mandato, caso a chapa seja eleita. A execução depende de viabilidade e autorizações.', 'notice'));
  if (p.approved) out.append(renderText('span', 'Aprovada pela gestão', 'badge approved'));
  if (backendReady) {
    try {
      const rows = await request(`/rest/v1/proposal_progress?select=*&proposal_id=eq.${id}`);
      if (rows[0]) { const u=rows[0]; out.append(renderText('h2','Acompanhamento confirmado'),renderText('p',u.stage),renderText('p',u.note)); for (const [k,label] of [['needs','Recursos necessários'],['official_response','Resposta recebida']]) if(u[k]) out.append(renderText('h3',label),renderText('p',u[k])); out.append(renderText('p',`Atualizado em ${new Date(u.updated_at).toLocaleDateString('pt-BR',{timeZone:'America/Sao_Paulo'})}.`)); }
    } catch { out.append(renderText('p','O acompanhamento online está temporariamente indisponível.')); }
  }
  const link=renderText('a','Enviar uma ideia sobre esta proposta','button navy');link.href='index.html#participacao';out.append(link);
}
init().catch(()=>out.replaceChildren(renderText('p','Não foi possível carregar a proposta. Recarregue a página.')));
