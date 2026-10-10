import {backendReady,isLocalPreview,safeStorage} from './api.js';
import {config} from './config.js';
const storage=safeStorage(),session=safeStorage('sessionStorage');
const optedOut=()=>storage.get('ft-traffic-opt-out')==='true'||navigator.doNotTrack==='1'||navigator.globalPrivacyControl===true;
if(backendReady&&!isLocalPreview&&!location.pathname.includes('/admin/')&&!optedOut()){
 let browser;try{browser=JSON.parse(storage.get('ft-traffic-browser')||'null');}catch{}
 if(!browser?.id||Date.now()-browser.created>30*86400000){browser={id:crypto.randomUUID(),created:Date.now()};storage.set('ft-traffic-browser',JSON.stringify(browser));}
 let visit;try{visit=JSON.parse(session.get('ft-traffic-visit')||'null');}catch{}
 const page=location.pathname.split('/').pop()||'index.html';if(!visit?.id||Date.now()-visit.updated>30*60000)visit={id:crypto.randomUUID(),seconds:0,updated:Date.now(),page};
 let last=performance.now(),pending=false,wasVisible=document.visibilityState==='visible';
 const tick=()=>{const now=performance.now();if(wasVisible){visit.seconds=Math.min(43200,visit.seconds+Math.min(65,(now-last)/1000));visit.updated=Date.now();}last=now;wasVisible=document.visibilityState==='visible';session.set('ft-traffic-visit',JSON.stringify(visit));};
 const send=async()=>{if(pending||optedOut())return;pending=true;tick();try{await fetch(config.supabaseUrl+'/functions/v1/record-visit',{method:'POST',headers:{apikey:config.supabasePublicKey,Authorization:`Bearer ${config.supabasePublicKey}`,'Content-Type':'application/json'},body:JSON.stringify({visit:visit.id,browser:browser.id,page:visit.page,seconds:Math.floor(visit.seconds)}),credentials:'omit',keepalive:true});}catch{}finally{pending=false;}};
 send();setInterval(()=>{tick();if(document.visibilityState==='visible')send();},60000);document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden'){tick();send();}else{if(Date.now()-visit.updated>30*60000)visit={id:crypto.randomUUID(),seconds:0,updated:Date.now(),page};last=performance.now();wasVisible=true;send();}});window.addEventListener('pagehide',()=>{tick();send();});
}
const button=document.querySelector('#traffic-privacy-toggle');if(button){const show=()=>button.textContent=storage.get('ft-traffic-opt-out')==='true'?'Permitir estatísticas de visita':'Desativar estatísticas neste navegador';show();button.addEventListener('click',()=>{storage.set('ft-traffic-opt-out',String(storage.get('ft-traffic-opt-out')!=='true'));show();});}
