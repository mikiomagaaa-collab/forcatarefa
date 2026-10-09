import {loadCatalogue, publicMetrics, stageOf} from './catalogue.js';
import { backendReady, request, renderText } from './api.js';
import { loadClassifications, appendLabels } from './classifications.js';
const icons = [
  '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z"/>',
  '<path d="M4 10h16v5H4zM6 15v5m12-5v5M6 10V5m12 5V5M3 20h18"/>',
  '<path d="M7 7h10c3 0 5 8 3 10-1 1-3-2-4-2H8c-1 0-3 3-4 2-2-2 0-10 3-10zM7 10v4m-2-2h4m7-1h.1m2 2h.1"/>',
  '<path d="M4 3h16v18H4zM8 7h8M8 11h3m-3 5 2 2 6-5"/>',
  '<path d="M9 18h6m-5 3h4M8 13a6 6 0 1 1 8 0l-1 3H9z"/>',
  '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2M18 3l3 3"/>'
];
export async function initProposals() {
  const loadingControls=[...document.querySelectorAll('#filter-all,#filter-priority,#filter-early,#clear-filters,#show-more,#proposal-stage')];
  loadingControls.forEach(control=>control.disabled=true);
  document.querySelector('#proposal-grid')?.setAttribute('aria-busy','true');
  document.querySelector('#proposal-count').textContent='Carregando propostas…';
  const data = await loadCatalogue();
  const {proposals,categories}=data; publicMetrics(data);
  if(document.querySelector('#ft-priorities'))document.querySelector('#ft-priorities').textContent='…';
  const { items: classifications } = await loadClassifications();
  let progress = new Map();
  const categoryMap = new Map(categories.map(c => [c.id, c.title]));
  const priorities = [5, 7, 17, 27, 19, 8];
  const summaries = [
    'Valorizar talentos com exposições, apresentações artísticas e projetos tecnológicos, especialmente de quem ainda não teve a chance de mostrar suas habilidades.',
    'Propor assentos próximos à biblioteca para criar um espaço confortável e tranquilo durante os intervalos.',
    'Organizar desafios e torneios amistosos com os jogos existentes, incluindo jogos desenvolvidos pelos alunos.',
    'Estabelecer metas, planejar despesas, reduzir desperdícios e decidir prioridades com os estudantes.',
    'Ajudar os clubes diretamente com materiais, planejamento e ideias para seus projetos e atividades.',
    'Organizar o rodízio semanal já aprovado: uma turma poderá sair cinco minutos mais cedo. Implementação a organizar.'
  ];
  if(document.querySelector('#ft-priorities'))document.querySelector('#ft-priorities').textContent=proposals.filter(p=>classifications.get(p.id)?.priority).length;
  if(document.querySelector('#priority-grid'))priorities.forEach((id, i) => {
    const proposal = proposals.find(p => p.id === id);
    const card = document.createElement('article'); card.className = 'priority-card';
    const top = document.createElement('div'); top.className = 'priority-top';
    const icon = document.createElement('div'); icon.className = 'icon-box';
    icon.innerHTML = `<svg class="icon" aria-hidden="true" viewBox="0 0 24 24">${icons[i]}</svg>`;
    top.append(icon, renderText('span', `PROPOSTA ${String(id).padStart(2, '0')}`));
    card.append(top); appendLabels(card, classifications.get(id));
    card.append(renderText('h3', proposal.title), renderText('span', categoryMap.get(proposal.category), 'category-label'), renderText('p',data.online?proposal.description:summaries[i],'proposal-summary'));
    if (id === 8) card.append(renderText('span', 'Aprovada pela gestão', 'badge approved'));
    const link = renderText('a', 'Saiba mais', 'text-link'); link.href = `#proposta-${id}`; card.append(link);
    document.querySelector('#priority-grid').append(card);
  });
  let category = 0; let expanded = false; let priorityOnly = false; let earlyOnly = false; let selectedStage='';
  const search = document.querySelector('#proposal-search');
  const grid = document.querySelector('#proposal-grid');
  const catalogue = document.querySelector('#catalogue-details');
  const more = document.querySelector('#show-more');
  const filterContainer = document.querySelector('#filters');
  const normalize = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  [{ id: 0, title: 'Todas' }, ...categories].forEach(c => {
    const button = renderText('button', c.title, 'filter'); button.type = 'button'; button.dataset.category = c.id;
    button.setAttribute('aria-pressed', String(c.id === 0));
    button.addEventListener('click', () => { category = c.id; expanded = true; render(); });
    filterContainer.append(button);
  });
  const stageSelect=document.querySelector('#proposal-stage');
  stageSelect?.addEventListener('change',()=>{selectedStage=stageSelect.value;expanded=true;render();});
  function render() {
    const query = normalize(search.value.trim());
    const matches = proposals.filter(p => (!category || p.category === category) && (!priorityOnly || classifications.get(p.id)?.priority) && (!earlyOnly || classifications.get(p.id)?.early) && (!selectedStage || stageOf(p,progress)===selectedStage) && normalize(`${p.id} ${p.title} ${p.description} ${categoryMap.get(p.category)}`).includes(query));
    const visible = expanded || query || category || selectedStage || priorityOnly || earlyOnly ? matches : matches.slice(0, 9);
    grid.replaceChildren();
    for (const p of visible) {
      const classification = classifications.get(p.id);
      const card = document.createElement('article'); card.className = `proposal-card${classification?.priority ? ' priority' : ''}`; card.id = `proposta-${p.id}`;
      const meta = document.createElement('div'); meta.className = 'proposal-meta'; meta.append(renderText('span', `PROPOSTA ${String(p.id).padStart(2, '0')}`, 'proposal-number'));
      card.append(meta); appendLabels(card, classification);
      card.append(renderText('span', categoryMap.get(p.category), 'category-label'), renderText('h3', p.title), renderText('p', p.description, 'proposal-summary'));
      if (p.approved) card.append(renderText('span', 'Aprovada pela gestão', 'badge approved'));
      const update = progress.get(p.id);
      card.append(renderText('p',update?.stage||'Apresentada','record-stage')); if(update?.note) card.append(renderText('p',update.note,'confirmed-progress proposal-summary'));
      const link = renderText('a', 'Ver detalhes da proposta', 'proposal-link'); link.href = `proposta.html?id=${p.id}`;
      card.append(link); grid.append(card);
    }
    document.querySelector('#proposal-count').textContent = `${earlyOnly ? 'Ações iniciais planejadas · ' : ''}${matches.length} ${matches.length === 1 ? 'proposta encontrada' : 'propostas encontradas'}${visible.length < matches.length ? ` · exibindo ${visible.length}` : ''}`;
    document.querySelector('#proposal-empty').hidden = matches.length !== 0;
    more.hidden = visible.length === matches.length;
    document.querySelector('#filter-all').setAttribute('aria-pressed', String(!priorityOnly && !earlyOnly));
    document.querySelector('#filter-priority').setAttribute('aria-pressed', String(priorityOnly));
    document.querySelector('#filter-early').setAttribute('aria-pressed', String(earlyOnly));
    filterContainer.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.category) === category)));
  }
  function reset() { category = 0; search.value = ''; priorityOnly = false; earlyOnly = false; selectedStage='';if(stageSelect)stageSelect.value=''; expanded = true; catalogue.open = true; render(); }
  function directLink() {
    const hash = window.location.hash;
    if (hash === '#propostas' || (location.pathname.endsWith('catalogo.html')&&!hash.startsWith('#proposta-'))) { catalogue.open = true; return; }
    if (!/^#proposta-([1-9]|[1-3]\d|4[0-5])$/.test(hash)) return;
    reset();
    requestAnimationFrame(() => document.querySelector(hash)?.scrollIntoView({ block: 'start' }));
  }
  search.addEventListener('input', () => { expanded = true; render(); });
  more.addEventListener('click', () => { expanded = true; render(); });
  document.querySelector('#filter-all').addEventListener('click', reset);
  document.querySelector('#filter-priority').addEventListener('click', () => { priorityOnly = !priorityOnly; expanded = true; render(); });
  document.querySelector('#filter-early').addEventListener('click', () => { earlyOnly = !earlyOnly; expanded = true; render(); });
  document.querySelector('#clear-filters').addEventListener('click', reset);
  document.querySelector('#all-proposals-link')?.addEventListener('click', reset);
  window.addEventListener('hashchange', directLink);
  document.addEventListener('click', event => {
    const link = event.target.closest('a[href^="#proposta-"]');
    if (link) { reset(); if (window.location.hash === link.getAttribute('href')) directLink(); }
  });
  const categoryParam=Number(new URLSearchParams(location.search).get('categoria'));if(categories.some(c=>c.id===categoryParam)){category=categoryParam;expanded=true;}
  render(); directLink();
  loadingControls.forEach(control=>control.disabled=false);
  grid.setAttribute('aria-busy','false');
  if (backendReady) {
    try { const updates = await request('/rest/v1/proposal_progress?select=proposal_id,stage,note'); progress = new Map(updates.map(update => [update.proposal_id, update])); render(); }
    catch { document.querySelector('#proposal-count').textContent += ' · acompanhamento online temporariamente indisponível'; }
  }
}
