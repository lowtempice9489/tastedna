import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

// 실제 화면 핸들러를 DOM 대역에서 실행한다. 브라우저 배치·실제 쿠키 검증과 구분한다.
function screen(){
 const nodes=new Map();
 function node(key){
  if(!nodes.has(key))nodes.set(key,{value:'',textContent:'',disabled:false,focus(){},
   addEventListener(event,handler){this['on'+event]=handler;},
   querySelector:node,querySelectorAll:()=>[],close(){this.open=false;},
   set innerHTML(html){this.html=html;for(const match of html.matchAll(/id="([^"]+)"[^>]*?(?:value="([^"]*)")?[^>]*>/g)){
    const element=node('#'+match[1]);element.value=(match[0].match(/value="([^"]*)"/)||[])[1]||'';
   }},get innerHTML(){return this.html||'';}});
  return nodes.get(key);
 }
 const calls=[];
 const client={clear(){calls.push(['clear']);},async request(path,body){calls.push([path,body]);return {memberId:'form-a',email:'a@example.test'};}};
 const context=vm.createContext({createClient:()=>client,document:{querySelector:selector=>['#next','#back'].includes(selector)?null:node(selector),querySelectorAll:()=>[]},
  storefront(){},location:{hash:'#taste',pathname:'/'},history:{replaceState(){}},URLSearchParams,TextEncoder,
  window:{scrollTo(){}},setTimeout:()=>0,clearTimeout(){}});
 const source=readFileSync(new URL('../../src/main/resources/static/app.js',import.meta.url),'utf8');
 vm.runInContext(source.replace(/^import .*;\r?\n/gm,'').replace(/initialize\(\);\s*$/,''),context);
 vm.runInContext("authMode='member';",context);
 return {context,node,calls,client,run:code=>vm.runInContext(code,context)};
}
const settle=()=>new Promise(resolve=>setImmediate(resolve));
test('womens card only shows preparation notice and returns to wardrobe selection',()=>{
 const {run,node,calls}=screen();
 run('selectApparel();');
 assert.match(node('main').innerHTML,/wardrobe-grid/);
 assert.match(node('main').innerHTML,/wardrobe-name">남성복/);
 assert.match(node('main').innerHTML,/wardrobe-pending">준비 중/);
 node('#choose-womens').onclick();
 assert.match(node('main').innerHTML,/여성복 취향 테스트를/);
 assert.match(node('main').innerHTML,/준비하고 있어요/);
 node('#scope-back').onclick();
 assert.match(node('main').innerHTML,/id="wardrobe-title"/);
 node('#scope-back').onclick();
 assert.match(node('main').innerHTML,/id="category-title"/);
 assert.equal(calls.length,0);
});
test('atelier cover retains wardrobe selection and FORM login entry without starting an anonymous assessment',()=>{
 const {run,node,calls}=screen();
 run('home();');
 assert.match(node('main').innerHTML,/id="taste-guide"/);
 assert.match(node('main').innerHTML,/남성복 Simple 10문항/);
 node('#begin').onclick();
 assert.match(node('main').innerHTML,/어떤 취향을 탐색해볼까요/);
 node('#category-apparel').onclick();
 assert.match(node('main').innerHTML,/여성복 · 준비 중 안내/);
 node('#choose-mens').onclick();
 assert.match(node('main').innerHTML,/FORM 로그인/);
 assert.equal(run('returnTo'),'taste');assert.equal(calls.length,0);
});
test('pending categories cannot enter tests and both back buttons restore the preceding screen',()=>{
 const {run,node,calls}=screen();
 run('selectCategory();');
 const html=node('main').innerHTML;
 assert.equal((html.match(/class="category-card category-pending" aria-disabled="true"/g)||[]).length,3);
 for(const name of ['리빙','뷰티','라이프'])assert.match(html,new RegExp('category-name">'+name));
 assert.equal((html.match(/<button[^>]*category-card/g)||[]).length,1);
 assert.equal((html.match(/class="category-state">준비 중/g)||[]).length,3);
 node('#category-apparel').onclick();
 assert.equal(node('#scope-back').textContent,'← 카테고리로');
 node('#scope-back').onclick();assert.match(node('main').innerHTML,/id="category-title"/);
 node('#category-back').onclick();assert.match(node('main').innerHTML,/id="atelier-title"/);
 assert.equal(calls.length,0);
});
test('same-document session link requires login and preserves destination',()=>{
 const {run,node,calls}=screen();
 run("location.hash='#session=saved-link';window.onhashchange();");
 assert.match(node('main').innerHTML,/FORM 로그인/);
 assert.equal(run('returnTo'),'saved-link');assert.equal(calls.length,0);
});
test('FORM signup requires explicit login and resumes original TasteDNA destination',async()=>{
 const {node,calls,run}=screen();
 run("returnTo='saved-session';formAuth(true);");
 node('#form-email').value='a@example.test';node('#form-password').value='test-password-123';node('#form-confirm').value='test-password-123';
 node('form').onsubmit({preventDefault(){}});await settle();
 assert.equal(calls[0][0],'/auth/signup');assert.equal(calls.length,1);
 assert.match(node('main').innerHTML,/FORM 로그인/);assert.equal(run('member'),null);
 assert.equal(node('#form-password').value,'');
 run('loadSession=async id=>{globalThis.loaded=id;};');
 node('#form-password').value='test-password-123';node('form').onsubmit({preventDefault(){}});await settle();
 assert.equal(calls[1][0],'/auth/login');assert.equal(run('loaded'),'saved-session');
 assert.equal(run('member.memberId'),'form-a');assert.equal(node('#form-password').value,'');
});
test('expired authentication clears visible answers and requests FORM login while preserving return target',async()=>{
 const {run,node}=screen();
 run("member={email:'a@example.test'};session={sessionId:'owned'};answers={Q01:{selectedOptionIds:['A']}};location.hash='#session=owned';");
 await run("action(async()=>{const error=new Error('AUTHENTICATION_REQUIRED');error.status=401;throw error;})");
 assert.equal(run('session'),null);assert.equal(run('Object.keys(answers).length'),0);
 assert.equal(run('returnTo'),'owned');assert.match(node('main').innerHTML,/FORM 로그인/);
});
test('logout clears in-memory member and assessment; login without member cannot start an assessment',async()=>{
 const {run,node,calls}=screen();
 run("login();");assert.match(node('main').innerHTML,/FORM 로그인/);assert.equal(calls.length,0);
 run("member={email:'a@example.test'};session={sessionId:'owned'};answers={Q01:{}};updateMemberNav();");
 node('#form-logout').onclick();await settle();
 assert.equal(calls[0][0],'/auth/logout');assert.equal(run('member'),null);
 assert.equal(run('session'),null);assert.equal(run('Object.keys(answers).length'),0);
 assert.equal(run('returnTo'),null);
});
