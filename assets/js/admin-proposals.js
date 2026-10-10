import {adminRequest} from './auth.js';
import {renderText,status} from './api.js';
import {additionalCategories} from './catalogue.js';
const $=selector=>document.querySelector(selector);
const plain=value=>value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
export async function initProposalWorkbench(){
 const baseline=await(await fetch('../assets/data/proposals.json')).json();
 const categories=[...baseline.categories,...additionalCategories];
 const categoryNames=new Map(categories.map(c=>[c.id,c.title]));
 let rows=[],progress=new Map(),labels=new Map(),current=null,isNew=false,dirty=false;
 const selected=new Set();
 for(const select of [$('#editorial-category'),$('#workbench-category')])for(const c of categories){const option=renderText('option',c.title);option.value=c.id;select.append(option);}
 function counts(){document.dispatchEvent(new CustomEvent('admin-catalogue-metrics',{detail:{total:rows.length,published:rows.filter(p=>p.editorial_state==='Publicada').length,pending:rows.filter(p=>p.editorial_state!=='Publicada').length,completed:[...progress.values()].filter(p=>p.stage==='Realizada').length}}));}
 function matches(){const query=plain($('#workbench-search').value.trim());const category=Number($('#workbench-category').value);const state=$('#workbench-state').value;return rows.filter(p=>(!category||p.category===category)&&(!state||p.editorial_state===state)&&plain(`${p.id} ${p.title} ${p.description}`).includes(query));}
 function render(){
  const found=matches();const list=$('#workbench-list');list.replaceChildren();
  for(const p of found){const row=renderText('article','','proposal-row');row.classList.toggle('is-selected',p.id===current&&!isNew);const checkbox=document.createElement('input');checkbox.type='checkbox';checkbox.checked=selected.has(p.id);checkbox.setAttribute('aria-label',`Selecionar proposta ${p.id}, ${p.title}`);checkbox.addEventListener('change',()=>{if(checkbox.checked)selected.add(p.id);else selected.delete(p.id);selection();});
   const button=renderText('button','','proposal-row-open');button.type='button';const heading=renderText('div','','proposal-row-heading');heading.append(renderText('span',String(p.id).padStart(2,'0'),'proposal-row-number'),renderText('h3',p.title));const meta=renderText('div','','proposal-row-meta');meta.append(renderText('span',p.editorial_state,p.editorial_state==='Publicada'?'pill pill-public':'pill pill-review'),renderText('span',progress.get(p.id)?.stage||'Apresentada','pill'));if(p.approved)meta.append(renderText('span','Aprovada pela escola','pill pill-public'));const label=labels.get(p.id);if(label?.priority)meta.append(renderText('span','Prioridade','pill pill-priority'));button.append(heading,renderText('p',categoryNames.get(p.category),'proposal-row-category'),renderText('p',p.description,'proposal-row-excerpt'),meta);button.addEventListener('click',()=>open(p.id));row.append(checkbox,button);list.append(row);
  }
  if(!found.length)list.append(renderText('p','Nenhuma proposta corresponde a este filtro.','workspace-empty'));
  $('#workbench-count').textContent=`${found.length} de ${rows.length} propostas`;
  $('#editorial-count').textContent=`${rows.filter(p=>p.editorial_state==='Publicada').length} publicadas · ${rows.filter(p=>p.editorial_state!=='Publicada').length} em revisão`;
  selection();counts();
 }
 function selection(){const count=selected.size;$('#workbench-selection').textContent=count?`${count} selecionada${count===1?'':'s'}`:'Selecione propostas para ações em conjunto';$('#workbench-bulk-submit').disabled=!count;$('#workbench-bulk-confirm-field').hidden=$('#workbench-bulk-state').value!=='Publicada';const found=matches();$('#workbench-select-all').checked=found.length>0&&found.every(p=>selected.has(p.id));}
 function tab(name){document.querySelectorAll('[data-proposal-editor-tab]').forEach(button=>button.setAttribute('aria-selected',String(button.dataset.proposalEditorTab===name)));document.querySelectorAll('[data-proposal-editor-panel]').forEach(panel=>panel.hidden=panel.dataset.proposalEditorPanel!==name);}
 function open(id){
  if(dirty){status($('#workbench-status'),'Salve as alterações ou clique em Voltar à lista para fechar esta edição antes de escolher outra proposta.','error');return;}
  const p=rows.find(p=>p.id===id);if(!p)return;current=id;isNew=false;$('#editorial-save').textContent='Salvar alterações';$('#proposal-editor').hidden=false;$('#proposal-workbench').dataset.editing='true';$('#editorial-form').reset();$('#editorial-id').value=String(id);$('#editorial-state').disabled=false;$('#editorial-visibility').hidden=false;$('#editorial-visibility').textContent=p.editorial_state==='Publicada'?'Ocultar do site':'Republicar no site';
  for(const [field,column] of [['title','title'],['description','description'],['category','category'],['objective','objective'],['approach','approach'],['responsible','responsible'],['authorization','authorization_notes'],['state','editorial_state']])$(`#editorial-${field}`).value=p[column]??'';
  $('#editorial-school-approved').disabled=false;$('#editorial-school-approved').checked=Boolean(p.approved);$('#editorial-confirm').checked=p.editorial_state==='Publicada';$('#proposal-editor-title').textContent=`Proposta ${id}`;$('#proposal-editor-subtitle').textContent=p.title;
  document.querySelectorAll('[data-proposal-editor-tab]').forEach(b=>b.hidden=false);tab('text');status($('#editorial-status'),'Você pode ocultar e republicar esta proposta sem perder seu número ou histórico.');document.dispatchEvent(new CustomEvent('proposal-selected',{detail:id}));render();$('#editorial-title').focus();
 }
 function close(){dirty=false;current=null;isNew=false;$('#proposal-editor').hidden=true;$('#proposal-workbench').dataset.editing='false';render();}
 async function load(){
  const results=await Promise.allSettled([adminRequest('/rest/v1/proposal_catalogue?select=*&order=id.asc'),adminRequest('/rest/v1/proposal_progress?select=*'),adminRequest('/rest/v1/proposal_classifications?select=*')]);
  if(results[0].status==='rejected')throw results[0].reason;rows=results[0].value;
  if(results[1].status==='fulfilled')progress=new Map(results[1].value.map(p=>[p.proposal_id,p]));if(results[2].status==='fulfilled')labels=new Map(results[2].value.map(p=>[p.proposal_id,p]));
  const select=$('#editorial-id');select.replaceChildren();for(const p of rows){const option=renderText('option',`${p.id}. ${p.title}`);option.value=p.id;select.append(option);}if(current)select.value=String(current);render();document.dispatchEvent(new CustomEvent('catalogue-updated',{detail:rows}));
 }
 $('#workbench-search').addEventListener('input',render);$('#workbench-category').addEventListener('change',render);$('#workbench-state').addEventListener('change',render);
 $('#workbench-select-all').addEventListener('change',e=>{const found=matches();if(e.target.checked&&found.length>100){e.target.checked=false;status($('#workbench-status'),'Filtre a lista para selecionar até 100 propostas por vez.','error');return;}for(const p of found)if(e.target.checked)selected.add(p.id);else selected.delete(p.id);render();});
 $('#workbench-clear-selection').addEventListener('click',()=>{selected.clear();render();});$('#workbench-bulk-state').addEventListener('change',selection);
 $('#workbench-refresh').addEventListener('click',async()=>{if(dirty){status($('#workbench-status'),'Salve sua edição antes de atualizar a lista.','error');return;}try{await load();status($('#workbench-status'),'Lista atualizada.','success');}catch(e){status($('#workbench-status'),e.message,'error');}});
 $('#new-proposal').addEventListener('click',()=>{if(dirty){status($('#workbench-status'),'Salve sua edição ou volte à lista antes de criar outra proposta.','error');return;}isNew=true;current=null;$('#editorial-visibility').hidden=true;$('#editorial-save').textContent='Salvar rascunho';$('#editorial-form').reset();$('#editorial-school-approved').disabled=true;$('#editorial-state').value='Rascunho';$('#editorial-state').disabled=true;$('#editorial-id').value='';$('#proposal-editor-title').textContent='Nova proposta';$('#proposal-editor-subtitle').textContent='O número será gerado ao salvar. Ela começa privada.';$('#proposal-editor').hidden=false;$('#proposal-workbench').dataset.editing='true';document.querySelectorAll('[data-proposal-editor-tab]').forEach(b=>b.hidden=b.dataset.proposalEditorTab!=='text');tab('text');status($('#editorial-status'),'Preencha título, descrição e categoria. Salvar cria um rascunho privado.');$('#editorial-title').focus();});
 $('#proposal-editor-close').addEventListener('click',close);$('#editorial-form').addEventListener('input',e=>{if(e.target.id!=='editorial-confirm')dirty=true;});
 $('#editorial-visibility').addEventListener('click',async e=>{
  const button=e.currentTarget;button.disabled=true;
  try{const proposal=rows.find(p=>p.id===current);if(isNew||!proposal)throw new Error('Escolha uma proposta salva da lista.');if(dirty)throw new Error('Salve as alterações antes de mudar a publicação.');const state=proposal.editorial_state==='Publicada'?'Rascunho':'Publicada';const confirmed=$('#editorial-confirm').checked;if(state==='Publicada'&&!confirmed)throw new Error('Confirme a revisão e a autorização para republicar.');const id=current;const changed=await adminRequest('/rest/v1/rpc/set_proposal_editorial',{method:'POST',body:{proposal_ids:[id],next_state:state,review_confirmed:confirmed}});if(changed!==1)throw new Error('A lista mudou. Atualize e confira a proposta.');await load();open(id);status($('#editorial-status'),state==='Publicada'?'Proposta republicada na sua posição da lista.':'Proposta ocultada do site. Ela continua guardada aqui e pode ser republicada.','success');
  }catch(error){status($('#editorial-status'),error.message,'error');}finally{button.disabled=false;}
 });
 document.querySelectorAll('[data-proposal-editor-tab]').forEach(b=>b.addEventListener('click',()=>tab(b.dataset.proposalEditorTab)));
 document.addEventListener('proposal-progress-updated',e=>{progress=new Map(e.detail.map(p=>[p.proposal_id,p]));render();});document.addEventListener('classification-updated',e=>{labels.set(e.detail.proposal_id,e.detail);render();});
 $('#editorial-form').addEventListener('submit',async e=>{
  e.preventDefault();const button=e.submitter;button.disabled=true;
  try{if(!isNew&&!rows.some(p=>p.id===current))throw new Error('Escolha uma proposta da lista.');const state=isNew?'Rascunho':$('#editorial-state').value;if(state==='Publicada'&&!$('#editorial-confirm').checked)throw new Error('Confirme a revisão e a autorização para publicar.');const body={};for(const [field,column] of [['title','title'],['description','description'],['category','category'],['objective','objective'],['approach','approach'],['responsible','responsible'],['authorization','authorization_notes']])body[column]=$(`#editorial-${field}`).value.trim();body.category=Number(body.category);body.approved=$('#editorial-school-approved').checked;
   let id=current;if(isNew)id=await adminRequest('/rest/v1/rpc/create_new_proposal',{method:'POST',body:{payload:body}});else await adminRequest(`/rest/v1/proposal_catalogue?id=eq.${id}`,{method:'PATCH',body:{...body,editorial_state:state}});dirty=false;await load();open(id);status($('#editorial-status'),state==='Rascunho'?'Rascunho salvo no banco. Revise e aprove antes de publicar.':'Alterações salvas no banco.','success');
  }catch(error){status($('#editorial-status'),error.message,'error');}finally{button.disabled=false;}
 });
 $('#workbench-bulk-form').addEventListener('submit',async e=>{
  e.preventDefault();const button=e.submitter;button.disabled=true;
  try{if(dirty)throw new Error('Salve a proposta que está editando antes da ação em conjunto.');const ids=[...selected];if(!ids.length||ids.length>100)throw new Error('Selecione entre 1 e 100 propostas.');const state=$('#workbench-bulk-state').value;const confirmed=$('#workbench-bulk-confirm').checked;if(state==='Publicada'&&!confirmed)throw new Error('Confirme a revisão e a autorização das propostas selecionadas.');const changed=await adminRequest('/rest/v1/rpc/set_proposal_editorial',{method:'POST',body:{proposal_ids:ids,next_state:state,review_confirmed:confirmed}});if(changed!==ids.length)throw new Error('A lista mudou. Atualize e confira as propostas.');selected.clear();$('#workbench-bulk-confirm').checked=false;await load();if(current)open(current);status($('#workbench-status'),state==='Aprovada pela equipe'?`${changed} proposta${changed===1?'':'s'} aprovada${changed===1?'':'s'} pela equipe. ${changed===1?'Permanece privada':'Permanecem privadas'} até a publicação.`:`Situação editorial salva para ${changed} proposta${changed===1?'':'s'}.`,'success');
  }catch(error){status($('#workbench-status'),error.message,'error');}finally{selection();}
 });
 await load();
}
