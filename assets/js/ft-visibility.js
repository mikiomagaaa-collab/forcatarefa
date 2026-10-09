import {backendReady,request} from './api.js';
export function applyFtAvailability(data){
 const records=data.records||[];
 const available={
  'ft-overview':true,
  'ft-projects':Boolean(data.projects?.length||records.some(r=>r.kind==='goal')),
  'ft-budget':records.some(r=>r.kind==='budget'),
  'ft-decisions':records.some(r=>r.kind==='decision'),
  'ft-team':Boolean(data.team?.length),
  'ft-transparency':Boolean(data.updates?.length||data.legacy?.length||data.progress?.some(p=>p.note||p.official_response)),
  'ft-participation':records.some(r=>r.kind==='return')
 };
 for(const link of document.querySelectorAll('.ft-navigation a,.ft-tiles a')){
  const id=link.hash.slice(1);if(id in available)link.hidden=!available[id];
 }
 for(const tiles of document.querySelectorAll('.ft-tiles'))tiles.hidden=![...tiles.querySelectorAll('a')].some(a=>!a.hidden);
 for(const panel of document.querySelectorAll('.ft-panel'))panel.dataset.available=String(Boolean(available[panel.id]));
 document.querySelector('.ft-dashboard')?.classList.remove('ft-loading');
 document.querySelector('.ft-home')?.classList.remove('ft-loading');
 document.dispatchEvent(new CustomEvent('ft-availability-ready'));
}
export async function initHomeFtAvailability(){
 const names=['projects','records','team','progress','updates','legacy'];
 const paths=['ft_projects?select=id&published=eq.true','management_records?select=kind&published=eq.true','team_members?select=name','proposal_progress?select=note,official_response','institutional_updates?select=id&limit=1','project_records?select=id&kind=eq.ft&published=eq.true'];
 const responses=await Promise.allSettled(paths.map(path=>backendReady?request('/rest/v1/'+path):Promise.resolve([])));
 const data={};responses.forEach((r,i)=>data[names[i]]=r.status==='fulfilled'?r.value:[]);applyFtAvailability(data);
}
