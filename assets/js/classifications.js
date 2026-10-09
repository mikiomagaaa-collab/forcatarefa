import { backendReady, request, renderText } from './api.js';
export const explanations = {
  priority: 'Uma das cinco propostas centrais do programa da chapa. A etiqueta não informa aprovação ou execução.',
  early: 'Primeiras ações planejadas para um eventual mandato, caso a chapa seja eleita. Não significa que a proposta já começou ou que será concluída imediatamente.'
};
export async function loadClassifications() {
  const response = await fetch(new URL('../data/classifications.json', import.meta.url));
  if (!response.ok) throw new Error('Não foi possível carregar as classificações.');
  const data = await response.json();
  const items = new Map(data.map(item => [item.proposal_id, item]));
  if (backendReady) {
    try {
      const online = await request('/rest/v1/proposal_classifications?select=proposal_id,priority,early,initial_action');
      for (const item of online) if (Number.isInteger(item.proposal_id) && item.proposal_id >= 1 && item.proposal_id <= 35 && typeof item.priority === 'boolean' && typeof item.early === 'boolean') items.set(item.proposal_id, item);
      return { items, online: true };
    } catch { return { items, online: false }; }
  }
  return { items, online: false };
}
const icons = {
  priority: '<path d="m12 3 2.8 5.7 6.3.9-4.6 4.4 1.1 6.3L12 17.3l-5.6 3 1.1-6.3L3 9.6l6.2-.9z"/>',
  early: '<path d="M14 4c2-1 4-1 6-1 0 2 0 4-1 6l-7 7-4-4zM14 4l-5 1-4 5 3 2m11-3-1 5-5 4-1-2M7 16c-2 0-3 2-3 4 2 0 4-1 4-3"/><circle cx="15.5" cy="7.5" r="1.5"/>'
};
export function labelElement(type) {
  const label = document.createElement('span'); label.className = `proposal-tag tag-${type}`; label.tabIndex = 0;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('width', '16'); svg.setAttribute('height', '16'); svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '1.7'); svg.setAttribute('stroke-linecap', 'round'); svg.setAttribute('stroke-linejoin', 'round'); svg.setAttribute('aria-hidden', 'true'); svg.innerHTML = icons[type];
  label.append(svg, renderText('span', type === 'early' ? 'Início do mandato' : 'Prioridade'));
  label.title = explanations[type]; label.setAttribute('aria-label', `${label.textContent}. ${explanations[type]}`);
  return label;
}
export function appendLabels(card, classification) {
  if (!classification?.priority && !classification?.early) return;
  const labels = document.createElement('div'); labels.className = 'proposal-tags';
  if (classification.early) labels.append(labelElement('early'));
  if (classification.priority) labels.append(labelElement('priority'));
  card.append(labels);
}
