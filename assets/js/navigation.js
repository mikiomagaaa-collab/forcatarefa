const toggle = document.querySelector('.menu-toggle');
const menu = document.querySelector('#menu');
function closeMenu(){menu?.classList.remove('open');toggle?.setAttribute('aria-expanded','false');toggle?.setAttribute('aria-label','Abrir menu');}
toggle?.addEventListener('click',()=>{const open=toggle.getAttribute('aria-expanded')!=='true';toggle.setAttribute('aria-expanded',String(open));toggle.setAttribute('aria-label',open?'Fechar menu':'Abrir menu');menu.classList.toggle('open',open);});
menu?.addEventListener('click',e=>{if(e.target.closest('a'))closeMenu();});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&menu?.classList.contains('open')){closeMenu();toggle.focus();}});
