import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import vm from 'node:vm';
const root=new URL('../',import.meta.url);
const source=await readFile(new URL('assets/js/theme-init.js',root),'utf8');
function browser(saved,systemDark=false,blocked=false,reduced=false){
 const storage=new Map(saved?[['ft-theme-preference',saved]]:[]), listeners=new Map(),events=[];
 const classes=new Set(),element={dataset:{},classList:{toggle:(name,on)=>on?classes.add(name):classes.delete(name),remove:name=>classes.delete(name)}};
 const media={matches:systemDark,addEventListener:(_,fn)=>listeners.set('system',fn)};
 const meta={content:''};
 const document={documentElement:element,querySelector:()=>meta,dispatchEvent:e=>events.push(e.type),addEventListener:(name,fn)=>listeners.set(name,fn)};
 const window={matchMedia:query=>query.includes('color-scheme')?media:{matches:reduced},addEventListener:(name,fn)=>listeners.set(name,fn)};
 vm.runInNewContext(source,{window,document,localStorage:{getItem:key=>{if(blocked)throw Error();return storage.get(key)||null;},setItem:(key,value)=>{if(blocked)throw Error();storage.set(key,value);}},CustomEvent:class{constructor(type){this.type=type;}},setTimeout:()=>1,clearTimeout:()=>{}});
 return {window,element,media,storage,listeners,classes,events,meta};
}
let count=0;const check=(actual,expected)=>{assert.deepEqual(actual,expected);count++;};
let b=browser(undefined,true);check(b.element.dataset.theme,'light');check(b.element.dataset.themePreference,'light');
b=browser('dark');check(b.element.dataset.theme,'dark');b.listeners.get('DOMContentLoaded')();check(b.meta.content,'#0E192A');
b.window.ftTheme.setPreference('light');check(b.element.dataset.theme,'light');check(b.storage.get('ft-theme-preference'),'light');check(b.classes.has('theme-changing'),true);
b.window.ftTheme.setPreference('invalid');check(b.element.dataset.theme,'light');
b.window.ftTheme.setPreference('system');check(b.element.dataset.theme,'light');b.media.matches=true;b.listeners.get('system')();check(b.element.dataset.theme,'dark');
b.window.ftTheme.setPreference('light');b.listeners.get('system')();check(b.element.dataset.theme,'light');
b.listeners.get('storage')({key:'ft-theme-preference',newValue:'dark'});check(b.element.dataset.theme,'dark');b.listeners.get('storage')({key:'ft-theme-preference',newValue:null});check(b.element.dataset.theme,'light');
b=browser('system',true);check(b.element.dataset.theme,'dark');b.media.matches=false;b.listeners.get('system')();check(b.element.dataset.theme,'light');
b=browser('unknown',true);check(b.element.dataset.theme,'light');
b=browser(undefined,false,true);b.window.ftTheme.setPreference('dark');check(b.element.dataset.theme,'dark');
b=browser('light',false,false,true);b.window.ftTheme.setPreference('dark');check(b.classes.has('theme-changing'),false);
b=browser('dark');check(b.element.dataset.theme,'dark');check(b.classes.has('theme-changing'),false);
const css=await readFile(new URL('assets/css/theme-tokens.css',root),'utf8');const dark=css.split(':root[data-theme="dark"]')[1];
const vars=new Map([...dark.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map(m=>[m[1],m[2]]));
function resolve(name){const value=vars.get(name);assert(value,name);return value.startsWith('var(')?resolve(value.slice(4,-1)):value;}
function luminance(hex){let h=hex.replace('#','');if(h.length===3)h=[...h].map(c=>c+c).join('');const c=[0,2,4].map(i=>parseInt(h.slice(i,i+2),16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722;}
function contrast(a,b){const x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
for(const text of ['--text-primary','--text-secondary','--text-muted','--accent-primary','--link-color','--error-color','--success-color','--warning-color'])for(const background of ['--bg-primary','--bg-secondary','--surface','--surface-elevated']){assert(contrast(resolve(text),resolve(background))>=4.5,`${text} sobre ${background}`);count++;}
for(const [text,background]of [['--error-color','--error-surface'],['--success-color','--success-surface'],['--accent-primary','--accent-surface'],['--link-color','--info-surface'],['--button-accent-text','--accent-primary']]){assert(contrast(resolve(text),resolve(background))>=4.5);count++;}
assert(contrast(resolve('--border-interactive'),resolve('--surface'))>=3);count++;
assert(contrast(resolve('--focus-color'),resolve('--surface-elevated'))>=3);count++;
for(const file of [...(await readdir(root)).filter(f=>f.endsWith('.html')),'admin/index.html']){const html=await readFile(new URL(file,root),'utf8');assert(html.indexOf('theme-init.js')<html.indexOf('assets/css/style.css'),file);assert(html.includes('theme-tokens.css')&&html.includes('theme.css')&&html.includes('assets/js/theme.js'),file);count++;}
console.log(`${count} verificações de preferência, persistência, sistema, movimento reduzido, carregamento inicial e contraste do modo escuro aprovadas.`);
