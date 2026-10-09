import { initAdminPlatform } from './admin-platform.js';
import { backendReady, isLocalPreview, status, renderText } from './api.js';
import { config } from './config.js';
import { adminRequest, signOut, readSession } from './auth.js';
const $ = selector => document.querySelector(selector);
let members = []; let proposals = []; let progress = new Map(); let suggestionOffset = 0;
const textValid = text => !/[<>\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(text);
const date = value => new Date(value).toLocaleString('pt-BR');
async function work(button, output, action, success) {
  button.disabled = true;
  try { await action(); if (success) status(output, success, 'success'); }
  catch (error) { status(output, error.message, 'error'); }
  finally { button.disabled = false; }
}
async function loadVotes() {
  const data = await adminRequest('/rest/v1/rpc/vote_summary', { method: 'POST', body: {} });
  const metrics = [
    [data.ft_intentions, 'Intenções FORÇA TAREFA'],
    [data.opposition_reported, 'Outra chapa · dado informado'],
    [data.student_total, 'Alunos · dado informado'],
    [data.unrepresented_estimate, 'Diferença aritmética estimada']
  ];
  $('#vote-metrics').replaceChildren();
  for (const [value, label] of metrics) { const card = document.createElement('div'); card.className = 'metric'; card.append(renderText('strong', value), renderText('span', label)); $('#vote-metrics').append(card); }
  $('#vote-bars').replaceChildren();
  status($('#vote-metrics-status'), 'Registros voluntários por navegador, sem garantia de pessoas únicas. Não representam votos oficiais nem uma pesquisa representativa. Os dados de 310 estudantes e 80 votos da outra chapa são referências informadas, sem verificação eleitoral.');
}
function renderMembers() {
  $('#members-editor').replaceChildren();
  members.forEach((name, index) => {
    const row = document.createElement('div'); row.className = 'admin-member';
    const input = document.createElement('input'); input.value = name; input.maxLength = 60; input.setAttribute('aria-label', `Nome do integrante ${index + 1}`);
    input.addEventListener('input', () => { members[index] = input.value; status($('#team-admin-status'), 'Há alterações ainda não salvas.'); });
    row.append(input);
    for (const [label, delta] of [['Subir', -1], ['Descer', 1]]) {
      const button = renderText('button', label); button.type = 'button'; button.disabled = index + delta < 0 || index + delta >= members.length;
      button.addEventListener('click', () => { [members[index], members[index + delta]] = [members[index + delta], members[index]]; renderMembers(); status($('#team-admin-status'), 'Ordem alterada. Salve para publicar.'); }); row.append(button);
    }
    const remove = renderText('button', 'Remover'); remove.type = 'button'; remove.addEventListener('click', () => { members.splice(index, 1); renderMembers(); status($('#team-admin-status'), 'Integrante removido da edição. Salve para publicar.'); }); row.append(remove); $('#members-editor').append(row);
  });
}
async function loadTeam() { const data = await adminRequest('/rest/v1/team_members?select=name,position&order=position.asc'); members = data.map(member => member.name); renderMembers(); }
async function loadSuggestions(append = false) {
  if (!append) { suggestionOffset = 0; $('#suggestions-list').replaceChildren(); }
  const filter = $('#suggestion-filter').value;
  const data = await adminRequest(`/rest/v1/suggestions?select=*&order=created_at.desc,id.desc&limit=30&offset=${suggestionOffset}${filter ? `&status=eq.${filter}` : ''}`);
  for (const suggestion of data) {
    const card = document.createElement('article'); card.className = 'suggestion';
    card.append(renderText('strong', suggestion.category), renderText('div', `${suggestion.name || 'Sem nome'} · ${suggestion.classroom || 'Turma não informada'} · ${date(suggestion.created_at)}`, 'suggestion-meta'), renderText('p', suggestion.message));
    const label = document.createElement('label'); label.textContent = 'Andamento da mensagem';
    const select = document.createElement('select');
    for (const [value, text] of [['nova','Nova'],['lida','Lida'],['analisada','Analisada']]) { const option = renderText('option', text); option.value = value; select.append(option); }
    select.value = suggestion.status;
    select.addEventListener('change', () => work(select, $('#suggestions-admin-status'), async () => { await adminRequest(`/rest/v1/suggestions?id=eq.${suggestion.id}`, { method: 'PATCH', body: { status: select.value } }); }, 'Andamento salvo.'));
    label.append(select); card.append(label);
    const erase = renderText('button', 'Excluir mensagem', 'button secondary'); erase.type = 'button';
    erase.addEventListener('click', () => {
      if (!window.confirm('Confirme que a solicitação de remoção foi validada ou que esta mensagem deixou de ser necessária. Excluir permanentemente esta mensagem e seus dados opcionais?')) return;
      work(erase, $('#suggestions-admin-status'), async () => {
        await adminRequest('/rest/v1/rpc/erase_suggestion', { method: 'POST', body: { target_id: suggestion.id, confirmed: true } });
        await loadSuggestions();
      }, 'Mensagem excluída.');
    });
    card.append(erase);
    const block = renderText('button', 'Suspender envios desta sessão', 'button secondary'); block.type = 'button';
    block.addEventListener('click', () => { $('#suspension-hash').value = suggestion.session_hash; $('#suspension-reason').focus(); $('#admin-security').scrollIntoView(); }); card.append(block); $('#suggestions-list').append(card);
  }
  suggestionOffset += data.length; $('#older-suggestions').hidden = data.length < 30;
  status($('#suggestions-admin-status'), suggestionOffset ? `${suggestionOffset} mensagens carregadas.` : 'Nenhuma sugestão recebida neste filtro.');
}
async function loadProgress() { const data = await adminRequest('/rest/v1/proposal_progress?select=*'); progress = new Map(data.map(row => [row.proposal_id, row])); showProgress(); }
function updateCompletionButton() {
  const completed = progress.get(Number($('#progress-proposal').value))?.stage === 'Realizada';
  $('#progress-complete').disabled = completed;
  $('#progress-complete').textContent = completed ? 'Proposta já realizada' : 'Marcar proposta como realizada';
}
function showProgress() {
  const id = Number($('#progress-proposal').value); const proposal = proposals.find(p => p.id === id); if (!proposal) return;
  $('#progress-description').textContent = proposal.description;
  $('#progress-stage').value = progress.get(id)?.stage || 'Apresentada';
  $('#progress-note').value = progress.get(id)?.note || '';
  $('#progress-needs').value = progress.get(id)?.needs || '';
  $('#progress-response').value = progress.get(id)?.official_response || '';
  updateCompletionButton();
}
async function saveProgress(stage) {
  const note = $('#progress-note').value.trim();
  const needs = $('#progress-needs').value.trim();
  const official_response = $('#progress-response').value.trim();
  if (![note, needs, official_response].every(textValid)) throw new Error('Use somente texto nos campos de acompanhamento.');
  await adminRequest('/rest/v1/proposal_progress?on_conflict=proposal_id', { method: 'POST', headers: { Prefer: 'resolution=merge-duplicates,return=minimal' }, body: { proposal_id: Number($('#progress-proposal').value), stage, note, needs, official_response, updated_at: new Date().toISOString() } });
  await loadProgress();
}
async function loadUpdates() {
  const data = await adminRequest('/rest/v1/institutional_updates?select=*&order=published_at.desc&limit=30');
  $('#admin-updates').replaceChildren();
  for (const update of data) { const card = document.createElement('article'); card.className = 'suggestion'; card.append(renderText('h3', update.title), renderText('div', date(update.published_at), 'suggestion-meta'), renderText('p', update.body)); $('#admin-updates').append(card); }
}
async function loadSecurity(owner) {
  const [suspensions, events] = await Promise.all([
    adminRequest(`/rest/v1/session_suspensions?select=*&suspended_until=gt.${encodeURIComponent(new Date().toISOString())}&order=updated_at.desc&limit=50`),
    adminRequest('/rest/v1/security_events?select=created_at,event_type,session_hash&order=created_at.desc&limit=30')
  ]);
  $('#active-suspensions').replaceChildren();
  for (const row of suspensions) { const card = document.createElement('article'); card.className = 'suggestion'; card.append(renderText('p', `Sessão ${row.session_hash.slice(0, 12)}… · até ${date(row.suspended_until)} · ${row.reason}`)); const button = renderText('button', 'Editar suspensão', 'button secondary'); button.addEventListener('click', () => { $('#suspension-hash').value = row.session_hash; $('#suspension-reason').value = row.reason; $('#suspension-hash').focus(); }); card.append(button); $('#active-suspensions').append(card); }
  $('#security-events').replaceChildren();
  for (const row of events) $('#security-events').append(renderText('p', `${date(row.created_at)} · ${row.event_type} · sessão ${row.session_hash.slice(0, 12)}…`, 'suggestion-meta'));
  if (owner) {
    const accounts = await adminRequest('/rest/v1/account_controls?select=*'); $('#accounts-list').replaceChildren();
    for (const row of accounts) { const card = document.createElement('article'); card.className = 'suggestion'; card.append(renderText('p', `${row.user_id} · ${row.is_owner ? 'Proprietário' : row.is_admin ? 'Administrador' : 'Sem administração'} · ${row.blocked ? 'Bloqueado' : 'Permitido'}`)); $('#accounts-list').append(card); }
  }
}
let isOwner = false;
async function init() {
  if (isLocalPreview) { $('#admin-guard').replaceChildren(renderText('p', 'Você está na prévia local. A gestão usa o site online.'), Object.assign(renderText('a', 'Abrir a gestão no site online', 'text-link'), { href: `${config.publicSiteUrl}#gestao` })); return; }
  if (!backendReady) { $('#admin-guard').replaceChildren(renderText('p', 'O painel ainda depende da configuração do serviço seguro de autenticação e dados.'), Object.assign(renderText('a', 'Voltar ao site', 'text-link'), { href: '../' })); return; }
  if (!readSession()) { $('#admin-guard').replaceChildren(renderText('p', 'Entre com uma conta autorizada para acessar o painel.'), Object.assign(renderText('a', 'Abrir autenticação', 'text-link'), { href: '../#gestao' })); return; }
  try {
    const authorized = await adminRequest('/rest/v1/rpc/is_admin', { method: 'POST', body: {} });
    if (!authorized) throw new Error('Esta conta não tem autorização administrativa.');
    isOwner = await adminRequest('/rest/v1/rpc/is_owner', { method: 'POST', body: {} });
    $('#admin-content').hidden = false; $('#admin-guard').hidden = true; $('#logout').hidden = false; $('#account-management').hidden = !isOwner;
    const response = await fetch('../assets/data/proposals.json'); if (!response.ok) throw new Error('Não foi possível carregar as propostas.');
    ({ proposals } = await response.json());
    for (const p of proposals) { const option = renderText('option', `${p.id}. ${p.title}`); option.value = p.id; $('#progress-proposal').append(option); }
    initAdminPlatform().catch(error => status($('#classification-status'), error.message, 'error'));
    const jobs = [loadVotes(), loadTeam(), loadSuggestions(), loadProgress(), loadUpdates(), loadSecurity(isOwner)];
    const outputs = ['#vote-metrics-status','#team-admin-status','#suggestions-admin-status','#progress-admin-status','#update-admin-status','#suspension-status'];
    const results = await Promise.allSettled(jobs); results.forEach((result, index) => { if (result.status === 'rejected') status($(outputs[index]), result.reason.message, 'error'); });
    setInterval(() => { if (document.visibilityState === 'visible') loadVotes().catch(error => status($('#vote-metrics-status'), error.message, 'error')); }, 30000);
  } catch (error) { $('#admin-content').hidden = true; status($('#admin-guard'), error.message, 'error'); }
}
$('#logout').addEventListener('click', async () => { try { await signOut(); } catch {} window.location.assign('../'); });
$('#refresh-votes').addEventListener('click', event => work(event.target, $('#vote-metrics-status'), loadVotes));
$('#refresh-suggestions').addEventListener('click', event => work(event.target, $('#suggestions-admin-status'), () => loadSuggestions()));
$('#older-suggestions').addEventListener('click', event => work(event.target, $('#suggestions-admin-status'), () => loadSuggestions(true)));
$('#suggestion-filter').addEventListener('change', event => work(event.target, $('#suggestions-admin-status'), () => loadSuggestions()));
$('#team-add-form').addEventListener('submit', event => {
  event.preventDefault(); const name = $('#new-member').value.trim();
  if (!name || !textValid(name) || members.length >= 100) { status($('#team-admin-status'), 'Informe um nome válido. O limite é de 100 integrantes.', 'error'); return; }
  members.push(name); $('#new-member').value = ''; renderMembers(); status($('#team-admin-status'), 'Integrante adicionado à edição. Salve para publicar.');
});
$('#save-team').addEventListener('click', event => work(event.target, $('#team-admin-status'), async () => {
  if (members.some(name => !name.trim() || name.trim().length > 60 || !textValid(name))) throw new Error('Confira todos os nomes antes de salvar.');
  await adminRequest('/rest/v1/rpc/replace_team', { method: 'POST', body: { members: members.map(name => name.trim()) } }); await loadTeam();
}, 'Equipe salva online. Os visitantes verão os nomes ao recarregar o site.'));
$('#progress-proposal').addEventListener('change', showProgress);
$('#progress-form').addEventListener('submit', event => {
  event.preventDefault(); work(event.submitter, $('#progress-admin-status'), () => saveProgress($('#progress-stage').value), 'Andamento salvo e disponível no site.');
});
$('#progress-complete').addEventListener('click', async event => {
  await work(event.currentTarget, $('#progress-admin-status'), () => saveProgress('Realizada'), 'Proposta marcada como realizada e disponível no site.');
  updateCompletionButton();
});
$('#update-form').addEventListener('submit', event => {
  event.preventDefault(); work(event.submitter, $('#update-admin-status'), async () => {
    const title = $('#update-title').value.trim(); const body = $('#update-body').value.trim(); if (!textValid(title) || !textValid(body)) throw new Error('Use somente texto, sem HTML.');
    await adminRequest('/rest/v1/institutional_updates', { method: 'POST', body: { title, body } }); $('#update-form').reset(); await loadUpdates();
  }, 'Atualização publicada.');
});
$('#suspension-form').addEventListener('submit', event => {
  event.preventDefault(); work(event.submitter, $('#suspension-status'), async () => {
    await adminRequest('/rest/v1/rpc/suspend_session', { method: 'POST', body: { target_hash: $('#suspension-hash').value.trim(), until_time: new Date(Date.now() + Number($('#suspension-duration').value) * 60000).toISOString(), justification: $('#suspension-reason').value.trim() } }); await loadSecurity(isOwner);
  }, 'Controle da sessão salvo. As demais sessões continuam acessíveis.');
});
$('#account-form').addEventListener('submit', event => {
  event.preventDefault(); work(event.submitter, $('#account-status'), async () => {
    const minutes = Number($('#account-suspension').value);
    await adminRequest('/rest/v1/rpc/manage_account', { method: 'POST', body: { target_user: $('#account-id').value.trim(), admin_access: $('#account-admin').value === 'true', block_access: $('#account-blocked').value === 'true', until_time: minutes ? new Date(Date.now() + minutes * 60000).toISOString() : null, justification: $('#account-reason').value.trim() } }); await loadSecurity(isOwner);
  }, 'Permissões e controles da conta salvos.');
});
init();
