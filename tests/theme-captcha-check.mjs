import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const listeners = new Map();
const calls = [];
const document = {
  documentElement: { dataset: { theme: 'dark' } },
  getElementById: () => ({}),
  addEventListener: (name, callback) => listeners.set(name, callback)
};
const window = {
  location: { hostname: 'example.com' },
  matchMedia: () => ({ matches: false }),
  turnstile: {
    render: (target, settings) => { calls.push({ target, settings }); return `widget-${calls.length}`; },
    remove: id => calls.push({ removed: id }),
    reset: id => calls.push({ reset: id }),
    getResponse: id => id === 'widget-3' ? 'fresh-token' : ''
  }
};
const source = (await readFile(new URL('../assets/js/api.js', import.meta.url), 'utf8'))
  .replace(/^import[^\n]+\n/, '')
  .replace(/\bexport /g, '');
const context = vm.createContext({ window, document, config: { supabaseUrl: 'https://example.supabase.co', supabasePublicKey: 'public', turnstileSiteKey: 'test-only' } });
vm.runInContext(`${source}\nthis.api = { mountCaptcha, captchaToken, resetCaptcha };`, context);
await context.api.mountCaptcha('captcha-vote', 'vote');
assert.equal(calls[0].settings.theme, 'dark');
assert.equal(calls[0].settings.action, 'vote');
assert.equal(calls[0].settings['response-field'], false);
await context.api.mountCaptcha('captcha-vote', 'vote');
assert.equal(calls.length, 1);
listeners.get('ft-theme-change')();
assert.equal(calls.length, 1);
document.documentElement.dataset.theme = 'light';
listeners.get('ft-theme-change')();
assert.equal(calls[1].removed, 'widget-1');
assert.equal(calls[2].settings.theme, 'light');
assert.equal(calls[2].settings.action, 'vote');
assert.equal(context.api.captchaToken('captcha-vote'), 'fresh-token');
context.api.resetCaptcha('captcha-vote');
assert.equal(calls[3].reset, 'widget-3');
assert.throws(() => context.api.captchaToken('not-mounted'), /Conclua a verificação/);
console.log('Turnstile: 11 verificações de tema e proteção passaram. Nenhuma solicitação real enviada.');
