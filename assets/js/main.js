import { initElection } from './election.js';
import { config } from './config.js';
import { backendReady, participationReady, request, status, safeStorage, sessionId, mountCaptcha, captchaToken, resetCaptcha, renderText } from './api.js';
import { saveSession } from './auth.js';
import { initProposals } from './proposals.js';

const toggle = document.querySelector('.menu-toggle');
const menu = document.querySelector('#menu');
toggle?.addEventListener('click', () => {
  const open = toggle.getAttribute('aria-expanded') !== 'true';
  toggle.setAttribute('aria-expanded', String(open));
  toggle.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
  menu.classList.toggle('open', open);
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && menu.classList.contains('open')) {
    menu.classList.remove('open'); toggle.setAttribute('aria-expanded', 'false'); toggle.setAttribute('aria-label', 'Abrir menu'); toggle.focus();
  }
});
initProposals().catch(error => { document.querySelector('#proposal-count').textContent = error.message; });

const storage = safeStorage();
if(storage.get('ft-display-name') === 'NNN') storage.remove('ft-display-name');
const nameForm = document.querySelector('#name-form');
const nameInput = document.querySelector('#display-name');
const greeting = document.querySelector('#greeting');
const dialog = document.querySelector('#login-dialog');
function showName() {
  const name = (storage.get('ft-display-name') || '').slice(0, 60);
  greeting.hidden = !name; nameForm.hidden = Boolean(name);
  document.querySelector('#greeting-text').textContent = name ? `Olá, ${name}! Que bom ter você por aqui.` : '';
  document.querySelector('#suggestion-name').value = name;
  nameInput.value = name;
}
async function openLogin() {
  dialog.showModal();
  if (participationReady) {
    try { await mountCaptcha('login-captcha', 'admin_login'); document.querySelector('#login-submit').disabled = false; status(document.querySelector('#login-status'), 'Acesso permitido somente a contas autorizadas.'); }
    catch (error) { status(document.querySelector('#login-status'), error.message, 'error'); }
  }
}
nameForm.addEventListener('submit', event => {
  event.preventDefault(); const name = nameInput.value.trim();
  if (name === 'participantechapasixseven') { nameInput.value = ''; openLogin(); return; }
  if (name) storage.set('ft-display-name', name); else storage.remove('ft-display-name');
  showName();
});
document.querySelector('#change-name').addEventListener('click', () => { greeting.hidden = true; nameForm.hidden = false; nameInput.focus(); });
document.querySelector('#remove-name').addEventListener('click', () => { storage.remove('ft-display-name'); showName(); nameInput.focus(); });
document.querySelector('#admin-open').addEventListener('click', openLogin);
document.querySelector('#login-close').addEventListener('click', () => dialog.close());
dialog.addEventListener('close', () => { document.querySelector('#admin-password').value = ''; resetCaptcha('login-captcha'); });
showName();

document.querySelector('#login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = document.querySelector('#login-submit'); if (button.disabled) return;
  button.disabled = true; button.textContent = 'Verificando…';
  try {
    const data = await request('/functions/v1/admin-login', { method: 'POST', body: { email: document.querySelector('#admin-email').value.trim(), password: document.querySelector('#admin-password').value, captcha: captchaToken('login-captcha'), session: sessionId() } });
    saveSession(data); window.location.assign('admin/');
  } catch (error) { status(document.querySelector('#login-status'), error.message, 'error'); }
  finally { button.disabled = !participationReady; button.textContent = 'Entrar na gestão'; resetCaptcha('login-captcha'); }
});

const suggestionForm = document.querySelector('#suggestion-form');
document.querySelector('#suggestion-message').addEventListener('input', event => { document.querySelector('#char-count').textContent = `${event.target.value.length.toLocaleString('pt-BR')} / 2.000 caracteres`; });
let lastSuggestion = 0;
suggestionForm.addEventListener('submit', async event => {
  event.preventDefault(); const button = document.querySelector('#suggestion-submit'); if (button.disabled) return;
  const output = document.querySelector('#suggestion-status');
  if (Date.now() - lastSuggestion < 30000) { status(output, 'Aguarde 30 segundos antes de tentar enviar novamente.', 'error'); return; }
  button.disabled = true; button.textContent = 'Enviando…';
  try {
    const form = new FormData(suggestionForm);
    const data = await request('/functions/v1/submit-suggestion', { method: 'POST', body: { name: String(form.get('name')).trim(), classroom: String(form.get('classroom')).trim(), category: form.get('category'), message: String(form.get('message')).trim(), website: form.get('website'), session: sessionId(), captcha: captchaToken('suggestion-captcha') } });
    if (!data?.received) throw new Error('O servidor não confirmou o recebimento. Tente novamente.');
    lastSuggestion = Date.now(); suggestionForm.reset(); showName(); document.querySelector('#char-count').textContent = '0 / 2.000 caracteres';
    status(output, 'Sua sugestão foi recebida. Obrigado por participar!', 'success');
  } catch (error) { status(output, error.message, 'error'); }
  finally { resetCaptcha('suggestion-captcha'); button.disabled = !participationReady; button.textContent = 'Enviar sugestão'; }
});
document.querySelector('#vote-form').addEventListener('submit', async event => {
  event.preventDefault(); const button = document.querySelector('#vote-submit'); if (button.disabled) return;
  const output = document.querySelector('#vote-status'); let recorded = false;
  button.disabled = true; button.textContent = 'Registrando…';
  try {
    const data = await request('/functions/v1/register-intention', { method: 'POST', body: { session: sessionId(), captcha: captchaToken('vote-captcha') } });
    if (typeof data?.recorded !== 'boolean') throw new Error('O servidor não confirmou o registro. Tente novamente.');
    recorded = true; storage.set('ft-intention-received', 'true');
    status(output, data.recorded ? 'Sua intenção foi registrada. Obrigado pelo apoio!' : 'Este navegador já tem uma intenção registrada. Obrigado pelo apoio!', 'success');
  } catch (error) { status(output, error.message, 'error'); }
  finally { resetCaptcha('vote-captcha'); button.disabled = recorded || !participationReady; button.textContent = recorded ? 'INTENÇÃO REGISTRADA' : 'VOU VOTAR NO FORÇA TAREFA!'; }
});

async function loadPublic() {
  if (!backendReady) return;
  try {
    const team = await request('/rest/v1/team_members?select=id,name,position&order=position.asc');
    const grid = document.querySelector('#team-grid'); grid.replaceChildren();
    for (const member of team) grid.append(renderText('div', member.name, 'member'));
    document.querySelector('#team-empty').hidden = team.length > 0;
  } catch { status(document.querySelector('#team-status'), 'Não foi possível carregar os nomes da equipe. Tente recarregar a página.', 'error'); }
  try {
    const updates = await request('/rest/v1/institutional_updates?select=title,body,published_at&order=published_at.desc&limit=20');
    const list = document.querySelector('#updates-list');
    list.querySelectorAll('.dynamic-update').forEach(element => element.remove());
    for (const update of updates) {
      const card = document.createElement('article'); card.className = 'update-card dynamic-update';
      card.append(renderText('span', new Date(update.published_at).toLocaleDateString('pt-BR'), 'category-label'), renderText('h3', update.title), renderText('p', update.body)); list.append(card);
    }
  } catch { if (!document.querySelector('#updates-list .updates-error')) { const message = renderText('p', 'Novas atualizações estão temporariamente indisponíveis.', 'notice updates-error'); document.querySelector('#updates-list').append(message); } }
}
loadPublic();
if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => { if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target); } });
  }, { threshold: 0.2 });
  document.querySelectorAll('.reveal').forEach(element => observer.observe(element));
}
window.addEventListener('pageshow', event => { if (event.persisted) loadPublic(); });
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && backendReady) loadPublic(); });
for (const contact of config.contacts) {
  try { const url = new URL(contact.url); if (url.protocol !== 'https:' && url.protocol !== 'mailto:') continue; const link = renderText('a', contact.label, 'text-link'); link.href = url.href; if (url.protocol === 'https:') { link.target = '_blank'; link.rel = 'noopener noreferrer'; } document.querySelector('#contact-links').append(link); } catch {}
}
if (participationReady) {
  Promise.allSettled([mountCaptcha('suggestion-captcha', 'suggestion'), mountCaptcha('vote-captcha', 'intention')]).then(results => {
    results.forEach((result, index) => {
      const prefix = index === 0 ? 'suggestion' : 'vote';
      if (result.status === 'fulfilled') {
        const voted = index === 1 && storage.get('ft-intention-received') === 'true';
        document.querySelector(`#${prefix}-submit`).disabled = voted;
        status(document.querySelector(`#${prefix}-status`), voted ? 'Este navegador já registrou sua intenção de voto.' : index === 0 ? 'Sua mensagem será recebida de forma privada pela gestão da chapa.' : 'Registre uma única intenção neste navegador.');
        if (voted) document.querySelector('#vote-submit').textContent = 'INTENÇÃO REGISTRADA';
      } else status(document.querySelector(`#${prefix}-status`), result.reason.message, 'error');
    });
    initElection().catch(() => {});
  });
}
if (window.location.hash === '#gestao') openLogin();
window.addEventListener('hashchange', () => { if (window.location.hash === '#gestao' && !dialog.open) openLogin(); });
menu?.addEventListener('click', event => {
  if (event.target.closest('a')) {
    menu.classList.remove('open');
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-label', 'Abrir menu');
  }
});

initElection().catch(() => {});
setInterval(() => { if(document.visibilityState==='visible')initElection().catch(() => {}); },60000);
document.querySelectorAll('.timeline-step').forEach(item => item.addEventListener('toggle', () => { if(item.open) document.querySelectorAll('.timeline-step').forEach(other => { if(other!==item)other.open=false; }); }));
