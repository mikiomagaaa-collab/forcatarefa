import {initProposals} from './proposals.js';
import {loadCatalogue} from './catalogue.js';
import {renderText} from './api.js';
if(document.querySelector('#proposal-grid'))initProposals().catch(e=>document.querySelector('#proposal-count').textContent=e.message);
const root=document.querySelector('#thematic-proposals');
if(root)loadCatalogue().then(({proposals,categories})=>{
  const selected=root.dataset.categories.split(',').map(Number);
  const rows=proposals.filter(p=>selected.includes(p.category));root.replaceChildren();
  for(const p of rows){const card=renderText('article','','ft-record');card.append(renderText('p',`PROPOSTA ${p.id} / ${categories.find(c=>c.id===p.category).title}`,'eyebrow'),renderText('h2',p.title),renderText('p',p.description));const a=renderText('a','Conhecer a proposta','text-link');a.href=`proposta.html?id=${p.id}`;card.append(a);root.append(card);}
  if(!rows.length)root.append(renderText('p','As propostas deste tema aparecerão aqui após revisão e publicação da equipe.','empty-records'));
}).catch(e=>root.replaceChildren(renderText('p',e.message,'notice')));
