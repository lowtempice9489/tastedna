import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as state from '../../src/main/resources/static/assessment-state.mjs';

// 실제 화면 핸들러를 DOM 대역에서 실행한다. 브라우저 배치·실제 쿠키 검증과 구분한다.
function screen(questionScreen=false){
 const nodes=new Map();
 function node(key){
  if(!nodes.has(key))nodes.set(key,{value:'',textContent:'',disabled:false,focus(){},
   append(){},addEventListener(event,handler){this['on'+event]=handler;},
   querySelector:node,querySelectorAll:()=>[],close(){this.open=false;},
   set innerHTML(html){this.html=html;for(const match of html.matchAll(/id="([^"]+)"[^>]*?(?:value="([^"]*)")?[^>]*>/g)){
    const element=node('#'+match[1]);element.value=(match[0].match(/value="([^"]*)"/)||[])[1]||'';
   }},get innerHTML(){return this.html||'';}});
  return nodes.get(key);
 }
 const calls=[];
 const client={clear(){calls.push(['clear']);},async request(path,body){calls.push([path,body]);return {memberId:'form-a',email:'a@example.test'};}};
 const context=vm.createContext({...state,createClient:()=>client,document:{querySelector:selector=>['#next','#back'].includes(selector)&&(!questionScreen||!node('main').innerHTML.includes('id="'+selector.slice(1)+'"'))?null:node(selector),querySelectorAll:()=>[],createElement:()=>({setAttribute(){},querySelector:()=>({})})},
  storefront(){},location:{hash:'#taste',pathname:'/'},history:{replaceState(){}},URLSearchParams,TextEncoder,
  window:{scrollTo(){}},setTimeout:()=>0,clearTimeout(){}});
 const source=readFileSync(new URL('../../src/main/resources/static/app.js',import.meta.url),'utf8');
 vm.runInContext(source.replace(/^import .*;\r?\n/gm,'').replace(/initialize\(\);\s*$/,''),context);
 vm.runInContext("authMode='member';",context);
 return {context,node,calls,client,run:code=>vm.runInContext(code,context)};
}
const settle=()=>new Promise(resolve=>setImmediate(resolve));

test('female selection survives FORM login and reports server definition rejection',async()=>{
 const {run,node,calls,client}=screen();
 run("selectApparel();");node('#choose-womens').onclick();node('#choose-simple').onclick();
 assert.match(node('main').innerHTML,/FORM 로그인/);
 client.request=async(path,body)=>{calls.push([path,body]);if(path==='/auth/login')return {memberId:'form-a',email:'a@example.test'};if(path==='/sessions/active')return null;throw new Error('IMAGE_DEFINITION_NOT_READY');};
 node('#form-email').value='a@example.test';node('#form-password').value='test password';
 node('form').onsubmit({preventDefault(){}});await settle();
 assert.ok(calls.some(([p])=>p==='/sessions/simple-image?apparelScope=womens'));
 assert.match(node('#notice').textContent,/정의가 아직 준비되지 않았어요/);
});

test('female server definitions render, restore, save, revise and submit through shared flow',async()=>{
 const {run,node,context,calls,client}=screen(true);
 const definitions=[];
 for(const file of ['V13__add_mens_image_simple_definition.sql','V18__add_womens_simple_q05_q07.sql','V19__add_womens_simple_q08_q10.sql']){
  const sql=readFileSync(new URL('../../src/main/resources/db/migration/'+file,import.meta.url),'utf8');
  for(const line of sql.split(/\r?\n/))if(line.startsWith('INSERT INTO td_question_definition')){
   const id=line.match(/,'(Q\d+)',/)[1];if(file.startsWith('V13')&&id>'Q04')continue;
   definitions.push({questionId:id,questionPayload:JSON.parse(line.slice(line.indexOf(",'{")+2,line.lastIndexOf("','{}',TRUE)")))});
  }
 }
 assert.equal(definitions.length,10);
 let session={sessionId:'female',apparelScope:'womens',mode:'SIMPLE',status:'IN_PROGRESS',revision:0};
 const saved={};node('main').append=()=>{};
 client.request=async(path,body)=>{
  calls.push([path,body]);
  if(path.endsWith('/answers')&&body){saved[body.questionId]=JSON.parse(JSON.stringify(body.answerPayload));session={...session,revision:session.revision+1};return session;}
  if(path.endsWith('/answers'))return {session,answers:JSON.parse(JSON.stringify(saved))};
  if(path.endsWith('/questions'))return {questions:definitions};
  if(path.endsWith('/submit')){session={...session,status:'SUBMITTED'};return session;}
  if(path.endsWith('/preference-summary'))return {title:'실제 서버 직접 선호',description:'',session,evidence:[]};
  if(path.endsWith('/matched-demo-products'))return {products:[]};
  throw new Error(path);
 };
 await run("loadSession('female')");
 for(let i=0;i<10;i++){
  const q=definitions[i],p=q.questionPayload;
  const html=node('main').innerHTML;
  assert.ok(html.includes(p.prompt));assert.match(html,/SIMPLE · 여성복/);
  if(p.kind==='COMPARISON'){
   assert.match(html,/비교 참고 이미지/);
   for(const g of p.comparisons){
    context.groupId=g.id;context.option=g.selectionMode==='RANKED_MULTI_SELECT'?'B':'A';
    run("answers[questions[step].questionId]=choose(questions[step].questionPayload,answers[questions[step].questionId],option,groupId)");
    if(g.selectionMode==='RANKED_MULTI_SELECT'){
     run("answers[questions[step].questionId]=choose(questions[step].questionPayload,answers[questions[step].questionId],'A',groupId)");
     run("answers[questions[step].questionId]=choose(questions[step].questionPayload,answers[questions[step].questionId],'B',groupId)");
     run("answers[questions[step].questionId]=choose(questions[step].questionPayload,answers[questions[step].questionId],'B',groupId)");
     assert.equal(run("selectionMap(answers[questions[step].questionId])[groupId].join(',')"),'A,B');
    }
   }
  }else{
   run("answers[questions[step].questionId]=choose(questions[step].questionPayload,null,questions[step].questionPayload.options[0].id)");
   if(['Q05','Q06','Q07'].includes(q.questionId)){
    run("answers[questions[step].questionId]=choose(questions[step].questionPayload,answers[questions[step].questionId],'NONE')");
    assert.equal(run("answers[questions[step].questionId].selectedOptionIds.join(',')"),'NONE');
   }
  }
  run('render()');node('#next').onclick();await settle();
 }
 assert.equal(Object.keys(saved).length,10);
 await run("loadSession('female')");assert.match(node('main').innerHTML,/마음이 간 선택들/);
 run('step=7;render()');assert.match(node('main').innerHTML,/1위/);node('#next').onclick();await settle();
 run('step=10;review()');node('#submit').onclick();await settle();
 assert.match(node('main').innerHTML,/실제 서버 직접 선호/);
 assert.equal(session.status,'SUBMITTED');
 assert.equal(calls.filter(([p,b])=>p.endsWith('/answers')&&b).length,11);
});
test('Deep remains non-interactive and mode back returns to wardrobe',()=>{
 const {run,node,calls}=screen();
 run('selectApparel();');node('#choose-mens').onclick();
 const html=node('main').innerHTML;
 assert.match(html,/<article class="mode-card mode-deep" aria-disabled="true"/);
 assert.match(html,/mode-tag">준비 중/);
 assert.equal((html.match(/<button[^>]*mode-card/g)||[]).length,1);
 assert.doesNotMatch(html,/id="choose-deep"/);
 node('#mode-back').onclick();assert.match(node('main').innerHTML,/id="wardrobe-title"/);
 assert.equal(calls.length,0);
});
test('authenticated Simple choice preserves active session resume and existing new-session endpoint',async()=>{
 for(const active of [{sessionId:'existing'},null]){
  const {run,node,calls,client}=screen();
  client.request=async(path,body)=>{calls.push([path,body]);return path==='/sessions/active'?active:{sessionId:'new-simple'};};
  run("member={memberId:'form-a'};loadSession=async id=>{globalThis.loaded=id;};selectApparel();");
  node('#choose-mens').onclick();assert.equal(calls.length,0);
  node('#choose-simple').onclick();await settle();
  assert.equal(run('loaded'),active?'existing':'new-simple');
  assert.deepEqual(calls.map(c=>c[0]),active?['/sessions/active']:['/sessions/active','/sessions/simple-image']);
 }
});
test('womens card opens shared Simple mode and returns to wardrobe selection',()=>{
 const {run,node,calls}=screen();
 run('selectApparel();');
 assert.match(node('main').innerHTML,/wardrobe-grid/);
 assert.match(node('main').innerHTML,/wardrobe-name">남성복/);
 assert.match(node('main').innerHTML,/여성복 취향 알아보기/);
 node('#choose-womens').onclick();
 assert.match(node('main').innerHTML,/10문항 · 여성복/);
 node('#mode-back').onclick();
 assert.match(node('main').innerHTML,/id="wardrobe-title"/);
 node('#scope-back').onclick();
 assert.match(node('main').innerHTML,/id="category-title"/);
 assert.equal(calls.length,0);
});
test('atelier cover retains wardrobe selection and FORM login entry without starting an anonymous assessment',()=>{
 const {run,node,calls}=screen();
 run('home();');
 assert.match(node('main').innerHTML,/id="taste-guide"/);
 assert.match(node('main').innerHTML,/남성복·여성복 Simple 10문항/);
 node('#begin').onclick();
 assert.match(node('main').innerHTML,/어떤 취향을 탐색해볼까요/);
 node('#category-apparel').onclick();
 assert.match(node('main').innerHTML,/여성복 취향 알아보기/);
 node('#choose-mens').onclick();
 assert.match(node('main').innerHTML,/id="mode-title"/);
 assert.equal(calls.length,0);
 node('#choose-simple').onclick();
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

test('report optional requests propagate expired login instead of retaining member data',async()=>{
 for(const expiredPath of ['initial-candidates','matched-demo-products']){
  const {run,node,client}=screen();
  client.request=async path=>{
   if(path.endsWith('/'+expiredPath))throw Object.assign(new Error('AUTHENTICATION_REQUIRED'),{status:401});
   if(path.endsWith('/preference-summary'))return {session:{status:'SUBMITTED',definitionId:'def',revision:1},title:'저장된 취향',evidence:[]};
   return {sessionId:'owned',definitionId:'def',revision:1,simpleRepresentative:null};
  };
  run("member={email:'a@example.test'};session={sessionId:'owned'};answers={Q01:{selectedOptionIds:['A']}};location.hash='#session=owned';");
  await run('action(result)');
  assert.equal(run('member'),null);
  assert.equal(run('session'),null);
  assert.equal(run('Object.keys(answers).length'),0);
  assert.equal(run('returnTo'),'owned');
  assert.match(node('main').innerHTML,/FORM 로그인/);
  assert.doesNotMatch(node('main').innerHTML,/저장된 취향/);
 }
});

test('mypage requires FORM login and returns without creating a test',async()=>{
 const {run,node,calls,client}=screen();
 run('openMyPage()');assert.match(node('main').innerHTML,/FORM 로그인/);assert.equal(run('returnTo'),'mypage');assert.equal(calls.length,0);
 client.request=async(path,body)=>{calls.push([path,body]);return path==='/auth/login'?{memberId:'form-a',email:'a@example.test'}:null;};
 node('#form-email').value='a@example.test';node('#form-password').value='test';
 node('form').onsubmit({preventDefault(){}});await settle();
 assert.match(node('main').innerHTML,/현재 조회 가능한 Simple 기록이 없습니다/);
 assert.deepEqual(calls.map(c=>c[0]),['/auth/login','/sessions/history']);
});
test('mypage only shows server-verified records and routes resume and submitted results',async()=>{
 const {run,node,calls,client}=screen();
 client.request=async(path,body)=>{calls.push([path,body]);return path==='/sessions/history'?[{sessionId:'active',mode:'SIMPLE',apparelScope:'mens',status:'IN_PROGRESS',startedAt:'2026-01-02T00:00:00Z'},{sessionId:'saved',mode:'SIMPLE',apparelScope:'womens',status:'SUBMITTED',submittedAt:'2026-01-01T00:00:00Z'}]:{evidence:[{questionId:'Q02',selections:[{label:'미니멀'}]}]};};
 run("member={email:'a@example.test'};session={sessionId:'saved'};loadSession=async id=>{globalThis.loaded=id;};");
 await run('openMyPage()');
 assert.match(node('main').innerHTML,/진행 중/);assert.match(node('main').innerHTML,/제출 완료/);
 assert.match(node('main').innerHTML,/미니멀/);assert.match(node('main').innerHTML,/여성복 Simple/);
 const dashboard=node('main').innerHTML;
 const savedSection=dashboard.slice(dashboard.indexOf('<section class="mypage-records" id="mypage-saved"'),dashboard.indexOf('<section class="mypage-records" id="mypage-recent"'));
 assert.match(savedSection,/여성복 Simple/);assert.doesNotMatch(savedSection,/남성복 Simple|이어서 답하기/);
 assert.match(dashboard,/내 취향 한눈에/);assert.match(dashboard,/마이페이지 메뉴/);
 node('#mypage-saved-1').onclick();await settle();assert.equal(run('loaded'),'saved');
 node('#mypage-retest').onclick();assert.match(node('main').innerHTML,/남성복|여성복/);
 run('session=null');await run('openMyPage()');assert.match(node('main').innerHTML,/최근 완료한 취향 기록/);
 node('#mypage-latest').onclick();await settle();assert.equal(run('loaded'),'saved');
 assert.match(node('main').innerHTML,/리빙 · 준비 중/);assert.doesNotMatch(node('main').innerHTML,/포인트|주문|일치율/);
 node('#mypage-record-0').onclick();await settle();assert.equal(run('loaded'),'active');
 assert.ok(calls.some(c=>c[0]==='/sessions/active/resume'&&c[1]));
 node('#mypage-record-1').onclick();await settle();assert.equal(run('loaded'),'saved');
 assert.ok(!calls.some(c=>c[0]==='/sessions/saved/resume'));
 run('updateMemberNav()');node('#form-logout').onclick();await settle();
 assert.equal(run('member'),null);assert.equal(run('session'),null);
});
test('mypage hides inaccessible recent record and clears screen on expired login',async()=>{
 for(const status of [404,401]){
  const {run,node,client}=screen();
  client.request=async path=>{throw Object.assign(new Error('SESSION_NOT_FOUND'),{status});};
  run("member={email:'a@example.test'};session={sessionId:'private-record'}");
  await run('openMyPage()');
  assert.doesNotMatch(node('main').innerHTML,/기록 private-record/);
  if(status===401){assert.equal(run('member'),null);assert.match(node('main').innerHTML,/FORM 로그인/);}
  else assert.doesNotMatch(node('main').innerHTML,/저장된 테스트 기록/);
 }
});

test('saved history opens the actual report and matching API after in-memory state is cleared',async()=>{
 for(const scope of ['mens','womens'])for(const labels of [['미니멀'],['미니멀','모던'],[]]){
 let previousHtml;
 for(let reload=0;reload<2;reload++){
 const {run,node,calls,client}=screen();
 const saved={sessionId:'persisted',mode:'SIMPLE',apparelScope:scope,status:'SUBMITTED',definitionId:'def',revision:10,submittedAt:'2026-01-01T00:00:00Z'};
 const selections=labels.map((label,i)=>({optionId:'R0'+(i+1),label,images:[]}));
 client.request=async(path,body)=>{
  calls.push([path,body]);
  if(path==='/sessions/history')return [saved];
  if(path.endsWith('/answers'))return {session:saved,answers:{}};
  if(path.endsWith('/questions'))return {questions:[]};
  if(path.endsWith('/preference-summary'))return {session:saved,title:'직접 선택 리포트',description:'',evidence:[{questionId:'Q02',prompt:'스타일',selections}]};
  if(path.endsWith('/initial-candidates'))return {...saved,finalResult:false,decision:{status:'DEFERRED',results:[]},simpleRepresentative:labels.length===1?{candidateId:'R01',sources:[{questionId:'Q02',optionId:'R01',optionLabel:labels[0],answerRevision:10}]}:null};
  if(path.endsWith('/matched-demo-products'))return {products:[]};
  throw new Error(path);
 };
 run("member={email:'a@example.test'};session=null");
 await run('openMyPage()');
 node('#report-products').scrollIntoView=()=>{node('#report-products').scrolled=true;};
 node(reload?'#mypage-saved-0':'#mypage-products').onclick();await settle();
 const html=node('main').innerHTML;
 if(labels.length===1){assert.match(html,/Simple 대표 스타일/);assert.match(html,/<h2>미니멀<\/h2>/);}
 else{assert.doesNotMatch(html,/Simple 대표 스타일/);assert.match(html,/직접 선택 리포트/);}
 for(const label of labels)assert.ok(html.includes('<span class="chip">'+label+'</span>'));
 assert.match(html,/종합 유형은 아직 확정하지 않습니다/);
 assert.match(html,/<details class="report-history">/);
 assert.doesNotMatch(html,/1위|2위/);
 assert.ok(calls.some(([p])=>p==='/sessions/persisted/initial-candidates'));
 assert.ok(calls.some(([p])=>p==='/sessions/persisted/matched-demo-products'));
 if(!reload)assert.equal(node('#report-products').scrolled,true);
 if(reload)assert.equal(html,previousHtml);
 previousHtml=html;
 assert.ok(!calls.some(([p])=>p.endsWith('/resume')));
 }
 }
});
