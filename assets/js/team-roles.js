import {renderText} from './api.js';
export const teamRoles=Object.freeze([
 {title:'Coordenador Geral',key:'general',path:'M3 7l5 4 4-7 4 7 5-4-2 12H5zM7 22h10'},
 {title:'Vice-Coordenador Geral',key:'vice',path:'M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6zM8 12l3 3 5-6'},
 {title:'Coordenador Comunitário',key:'community',path:'M9 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6M3 21v-3a6 6 0 0 1 12 0v3m3-16a3 3 0 0 1 0 6m0 4a5 5 0 0 1 3 5'},
 {title:'Coordenador de Convivência Escolar',key:'coexistence',path:'M12 20 3 11a5 5 0 0 1 9-6 5 5 0 0 1 9 6zM8 12l2 2 5-5'},
 {title:'Coordenador de Comunicação',key:'communication',path:'M4 9h5l11-5v16L9 15H4zM7 15l2 6h4l-3-6M23 9v6'},
 {title:'Coordenador de Saúde e Sustentabilidade',key:'health',path:'M20 3C8 3 3 9 6 16s14 3 14-13zM3 22 16 9M9 7v9h9'},
 {title:'Coordenador Desportivo',key:'sport',path:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M12 8l5 4-2 6H9l-2-6zM12 3v5M3 10l4 2m10 0 4-2M7 20l2-2m6 0 2 2'},
 {title:'Coordenador Artístico',key:'art',path:'M12 3a9 9 0 0 0 0 18h2a2 2 0 0 0 1-4c-2-1-1-3 1-3h3a3 3 0 0 0 3-3c0-5-5-8-10-8zM7 10h.1M10 6h.1M15 6h.1M18 10h.1'}
]);
export function roleDefinition(title){return teamRoles.find(role=>role.title===title)||{title:title||'Cargo a definir',key:'neutral',path:'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8M4 22v-2a8 8 0 0 1 16 0v2'};}
export function roleIcon(title){const role=roleDefinition(title);const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');for(const [key,value] of Object.entries({viewBox:'0 0 24 24',fill:'none',stroke:'currentColor','stroke-width':'1.7','stroke-linecap':'round','stroke-linejoin':'round','aria-hidden':'true'}))svg.setAttribute(key,value);svg.classList.add('role-icon');const path=document.createElementNS(svg.namespaceURI,'path');path.setAttribute('d',role.path);svg.append(path);return svg;}
export function teamCard(member){const role=roleDefinition(member.role);const card=renderText('article','','team-role-card role-'+role.key);const identity=renderText('div','','team-member-identity');const icon=renderText('span','','role-emblem');icon.append(roleIcon(member.role));identity.append(icon,renderText('h3',member.name));const footer=renderText('div','','team-member-role');footer.append(renderText('p',role.title),roleIcon(member.role));card.append(identity,footer);return card;}
