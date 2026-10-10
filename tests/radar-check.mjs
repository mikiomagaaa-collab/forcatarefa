import assert from 'node:assert/strict';
import {radarRows, radarMeasurement} from '../assets/js/radar-data.js';
let checks = 0;
const proposals = Array.from({length:45}, (_,i)=>({id:i+1,title:`Proposta ${i+1}`,editorial_state:'Publicada'}));
const records = [
  {proposal_id:5,stage:'Em execução',completion_percent:70,completion_basis:'Sete das dez entregas previstas foram concluídas.'},
  {proposal_id:7,stage:'Em execução',completion_percent:70,completion_basis:'Sete das dez entregas previstas foram concluídas.'},
  {proposal_id:8,stage:'Aprovada'},
  {proposal_id:17,stage:'Realizada',note:'Realização confirmada pela equipe.'},
  {proposal_id:27,stage:'Em planejamento',completion_percent:0,completion_basis:'Nenhuma das entregas previstas foi realizada.'},
  {proposal_id:31,stage:'Em execução',completion_percent:101,completion_basis:'Percentual inválido não deve ser mostrado.'}
];
const rows = radarRows(proposals,records);
assert.deepEqual(rows.slice(0,3).map(r=>[r.id,r.percent]),[[17,100],[5,70],[7,70]]); checks++;
assert.equal(rows.length,45); checks++;
assert.equal(rows.find(r=>r.id===8).percent,null); checks++;
assert.equal(rows.find(r=>r.id===27).percent,0); checks++;
assert.equal(rows.find(r=>r.id===31).percent,null); checks++;
assert.equal(rows.find(r=>r.id===1).percent,null); checks++;
assert.equal(rows.find(r=>r.id===17).basis,records[3].note); checks++;
assert.deepEqual(radarRows(proposals,[]).slice(0,3).map(r=>r.id),[1,2,3]); checks++;
assert.equal(radarRows([{id:5,editorial_state:'Rascunho'}],records).length,0); checks++;
assert.deepEqual(radarMeasurement('0','Nenhuma entrega foi concluída.','Em planejamento'),{completion_percent:0,completion_basis:'Nenhuma entrega foi concluída.'}); checks++;
assert.deepEqual(radarMeasurement('','Texto anterior','Em execução'),{completion_percent:null,completion_basis:''}); checks++;
assert.equal(radarMeasurement('30','','Realizada').completion_percent,100); checks++;
assert.equal(radarMeasurement('','','Realizada','Resultado confirmado pela equipe.').completion_basis,'Resultado confirmado pela equipe.'); checks++;
for(const value of ['-1','101','30.5','NaN','Infinity']) {assert.throws(()=>radarMeasurement(value,'Explicação pública válida.','Em execução'));checks++;}
assert.throws(()=>radarMeasurement('20','','Em execução')); checks++;
assert.throws(()=>radarMeasurement('100','Explicação pública válida.','Aprovada')); checks++;
assert.throws(()=>radarMeasurement('20','<script>Olá mundo</script>','Em execução')); checks++;
assert.throws(()=>radarMeasurement('20','a'.repeat(1001),'Em execução')); checks++;
assert.deepEqual(records[0].completion_percent,70); checks++;
console.log(`${checks} verificações do Radar FT+: ranking, empates, 45 propostas, valores não medidos, aprovação separada e validação dos registros.`);
