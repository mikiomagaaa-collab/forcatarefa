import {renderText} from './api.js';
import {radarRows} from './radar-data.js';

function card(row) {
  const article = renderText('article', '', 'radar-card');
  const heading = renderText('div', '', 'radar-card-heading');
  const title = renderText('div');
  title.append(renderText('span', `PROPOSTA ${String(row.id).padStart(2, '0')}`, 'radar-number'));
  const h3 = renderText('h3');
  const link = renderText('a', row.title);
  link.href = `proposta.html?id=${row.id}`;
  h3.append(link); title.append(h3);
  heading.append(title, renderText('strong', row.percent === null ? 'Sem medição' : `${row.percent}%`, 'radar-percent'));
  article.append(heading, renderText('p', row.stage, 'radar-stage'));
  if (row.percent === null) article.append(renderText('div', '', 'radar-track'), renderText('p', 'Percentual ainda não registrado pela gestão.', 'radar-basis'));
  else {
    const bar = document.createElement('progress');
    bar.max = 100; bar.value = row.percent;
    bar.setAttribute('aria-label', `${row.title}: ${row.percent}% concluída`);
    article.append(bar, renderText('p', row.basis, 'radar-basis'));
    if (row.updatedAt && !Number.isNaN(Date.parse(row.updatedAt))) article.append(renderText('p', `Registro atualizado em ${new Date(row.updatedAt).toLocaleDateString('pt-BR', {timeZone: 'America/Sao_Paulo'})}.`, 'radar-date'));
  }
  return article;
}

export function renderRadar({proposals, progress, unavailable = false}) {
  const root = document.querySelector('#radar-content');
  if (!root) return;
  root.replaceChildren();
  if (unavailable) { root.append(renderText('p', 'Não foi possível consultar o progresso. Confira sua conexão e recarregue a página para tentar novamente.', 'notice')); return; }
  const rows = radarRows(proposals, progress);
  if (!rows.length) { root.append(renderText('p', 'As propostas aparecerão aqui quando forem publicadas pela equipe.', 'notice')); return; }
  const measured = rows.filter(r => r.percent !== null).length;
  const status = renderText('p', '', 'radar-summary');
  status.setAttribute('role', 'status');
  const first = renderText('div', '', 'radar-list');
  first.append(...rows.slice(0, 3).map(card));
  root.append(status, first);
  const remaining = renderText('div', '', 'radar-list');
  remaining.id = 'radar-more'; remaining.hidden = true;
  function summary(expanded) {
    status.textContent = `Mostrando ${expanded ? rows.length : Math.min(3, rows.length)} de ${rows.length} propostas. ${measured ? `${measured} com progresso registrado.` : 'Os percentuais serão publicados quando a gestão registrar avanços confirmados.'}`;
  }
  summary(false);
  if (rows.length > 3) {
    const toggle = renderText('button', 'Mostrar mais propostas', 'button radar-toggle');
    toggle.type = 'button'; toggle.setAttribute('aria-expanded', 'false'); toggle.setAttribute('aria-controls', remaining.id);
    let populated = false;
    toggle.addEventListener('click', () => {
      const expanded = toggle.getAttribute('aria-expanded') !== 'true';
      if (expanded && !populated) { remaining.append(...rows.slice(3).map(card)); populated = true; }
      remaining.hidden = !expanded;
      toggle.setAttribute('aria-expanded', String(expanded));
      toggle.textContent = expanded ? 'Mostrar menos propostas' : 'Mostrar mais propostas';
      summary(expanded);
    });
    root.append(toggle, remaining);
  }
}
