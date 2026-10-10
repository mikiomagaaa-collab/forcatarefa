import { backendReady, request } from './api.js';
export const additionalCategories = [{id:7,title:'Bem-estar Estudantil'},{id:8,title:'Infraestrutura e Manutenção'}];
export const stageOf = (proposal, progress) => progress.get(proposal.id)?.stage || 'Apresentada';
export async function loadCatalogue() {
  const response = await fetch(new URL('../data/proposals.json', import.meta.url));
  if (!response.ok) throw new Error('Não foi possível carregar as propostas. Recarregue a página.');
  const baseline = await response.json();
  const categories = [...baseline.categories, ...additionalCategories];
  let proposals = backendReady?[]:baseline.proposals;
  let online = false;
  if (backendReady) {
    try {
      const rows = await request('/rest/v1/proposal_catalogue?select=*&editorial_state=eq.Publicada&order=id.asc');
      if(Array.isArray(rows)){proposals=rows;online=true;}
    } catch {}
  }
  return {proposals, categories, online};
}
export function publicMetrics({proposals,categories,online}) {
  const active = new Set(proposals.map(p=>p.category));
  document.querySelectorAll('[data-proposal-total]').forEach(el=>el.textContent=backendReady&&!online?'…':proposals.length);
  document.querySelectorAll('[data-category-total]').forEach(el=>el.textContent=backendReady&&!online?'…':categories.filter(c=>active.has(c.id)).length);
  document.querySelectorAll('[data-proposals-link]').forEach(el=>el.textContent=backendReady&&!online?'Ver propostas':`Ver todas as ${proposals.length} propostas`);
  if(backendReady&&!online){const target=document.querySelector('#catalogue-details')||document.querySelector('.ft-dashboard');if(target&&!document.querySelector('.catalogue-offline')){const message=document.createElement('p');message.className='notice catalogue-offline';message.textContent='Não foi possível consultar as propostas publicadas. Recarregue a página para tentar novamente.';target.before(message);}}
}
