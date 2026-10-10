import {backendReady,request,renderText} from './api.js';
import {animateMonth} from './calendar-motion.js';
const $=s=>document.querySelector(s);
const months=['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'];
const iso=(y,m,d)=>`${y}-${String(m+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
const display=value=>new Date(value+'T12:00:00Z').toLocaleDateString('pt-BR',{timeZone:'America/Sao_Paulo'});
let year=2027,month=0,selected='',rows=[],failed=false;
const grid=$('#public-calendar-grid');
function render(direction=0){
 $('#public-calendar-year').value=String(year);$('#public-calendar-month').value=String(month);$('#public-calendar-heading').textContent=`${months[month]} de ${year}`;$('#public-calendar-prev').disabled=year===2027&&month===0;$('#public-calendar-next').disabled=year===2028&&month===11;
 grid.replaceChildren();for(const label of ['SEG','TER','QUA','QUI','SEX','SÁB','DOM'])grid.append(renderText('span',label,'calendar-weekday'));
 const start=(new Date(Date.UTC(year,month,1)).getUTCDay()+6)%7;const days=new Date(Date.UTC(year,month+1,0)).getUTCDate();
 for(let i=0;i<Math.ceil((start+days)/7)*7;i++){const day=i-start+1;if(day<1||day>days){const blank=renderText('span','','calendar-blank');blank.setAttribute('aria-hidden','true');grid.append(blank);continue;}const date=iso(year,month,day);const events=rows.filter(r=>r.event_date<=date&&(r.end_date||r.event_date)>=date&&r.stage!=='Cancelado');const button=renderText('button','','calendar-day');button.type='button';button.setAttribute('aria-label',`${display(date)}, ${events.length} evento${events.length===1?'':'s'} publicado${events.length===1?'':'s'}`);button.setAttribute('aria-pressed',String(selected===date));button.append(renderText('strong',day));if(events.length)button.append(renderText('span',`${events.length} evento${events.length===1?'':'s'}`,'calendar-day-count'));button.addEventListener('click',()=>{selected=selected===date?'':date;render();});grid.append(button);}
 const first=iso(year,month,1),last=iso(year,month,days);const events=rows.filter(r=>selected?r.event_date<=selected&&(r.end_date||r.event_date)>=selected:r.event_date<=last&&(r.end_date||r.event_date)>=first);$('#public-calendar-list-title').textContent=selected?`Eventos de ${display(selected)}`:'Eventos publicados no mês';$('#public-calendar-clear').hidden=!selected;const list=$('#public-calendar-events');list.replaceChildren();
 for(const row of events){const card=renderText('article','','calendar-event');card.append(renderText('p',`${display(row.event_date)}${row.end_date?' a '+display(row.end_date):''}${row.start_time?' · '+row.start_time.slice(0,5):''}`,'calendar-event-date'),renderText('h3',row.title),renderText('span',row.stage,'badge'),renderText('p',row.public_description));if(row.stage==='Proposto')card.append(renderText('p','Atividade planejada. A realização ainda depende de confirmação.','category-label'));list.append(card);}
 if(!events.length)list.append(renderText('p',failed?'O calendário está temporariamente indisponível. Recarregue a página para tentar novamente.':'Nenhum evento publicado para este período. Novas atividades aparecerão após revisão e autorização da equipe.','calendar-empty'));
 if(direction)animateMonth(grid,direction);
}
for(const [direction,id] of [[-1,'#public-calendar-prev'],[1,'#public-calendar-next']])$(id).addEventListener('click',()=>{const value=year*12+month+direction;year=Math.floor(value/12);month=value%12;selected='';render(direction);});
$('#public-calendar-year').addEventListener('change',e=>{year=Number(e.target.value);selected='';render(1);});$('#public-calendar-month').addEventListener('change',e=>{month=Number(e.target.value);selected='';render(1);});$('#public-calendar-clear').addEventListener('click',()=>{selected='';render();});
for(let i=0;i<12;i++){const option=renderText('option',months[i]);option.value=i;$('#public-calendar-month').append(option);}render();
grid.setAttribute('aria-busy','true');$('#public-calendar-status').textContent='Consultando eventos publicados…';
if(backendReady)try{rows=await request('/rest/v1/management_calendar?select=id,title,event_date,end_date,start_time,event_kind,stage,public_description&order=event_date.asc,start_time.asc');}catch{failed=true;}else failed=true;
grid.setAttribute('aria-busy','false');$('#public-calendar-status').textContent=failed?'Não foi possível consultar a agenda.':'Somente eventos autorizados para publicação aparecem nesta agenda.';render();
