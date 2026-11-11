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
   replaceWith(replacement){this.replacement=replacement;this.onclick=null;},append(){},addEventListener(event,handler){this['on'+event]=handler;},
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
 run("selectApparel();");node('#choose-womens').onclick();run('login()');
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
test('atelier cover reaches guest questions without member session creation',async()=>{
 const {run,node,calls,client}=screen(true);
 const sql=readFileSync(new URL('../../src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql',import.meta.url),'utf8');
 const questions=sql.split(/\r?\n/).filter(line=>line.startsWith('INSERT INTO td_question_definition')).map(line=>({
  questionId:line.match(/,'(Q\d+)',/)[1],questionPayload:JSON.parse(line.slice(line.indexOf(",'{")+2,line.lastIndexOf("','{}',TRUE)")))}));
 client.request=async path=>{calls.push([path]);return {definitionId:'33333333-3333-4333-8333-333333333333',questions};};
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
 await settle();
 assert.equal(calls[0][0],'/guest/simple/questions?apparelScope=mens');
 assert.equal(run('guest'),true);assert.equal(run('questions.length'),10);assert.match(node('main').innerHTML,/SIMPLE · 남성복/);
 assert.ok(!calls.some(([path])=>path.startsWith('/sessions/')));
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
 client.request=async(path,body)=>{calls.push([path,body]);return path==='/auth/login'?{memberId:'form-a',email:'a@example.test'}:path==='/member/results'?{current:null,saved:[],limit:5}:[];};
 node('#form-email').value='a@example.test';node('#form-password').value='test';
 node('form').onsubmit({preventDefault(){}});await settle();
 assert.match(node('main').innerHTML,/현재 조회 가능한 Simple 기록이 없습니다/);
 assert.deepEqual(calls.map(c=>c[0]),['/auth/login','/sessions/history','/member/results']);
});
test('mypage only shows server-verified records and routes resume and submitted results',async()=>{
 const {run,node,calls,client}=screen();
 client.request=async(path,body)=>{calls.push([path,body]);return path==='/member/results'?{current:null,saved:[],limit:5}:path==='/sessions/history'?[{sessionId:'active',mode:'SIMPLE',apparelScope:'mens',status:'IN_PROGRESS',startedAt:'2026-01-02T00:00:00Z'},{sessionId:'saved',mode:'SIMPLE',apparelScope:'womens',status:'SUBMITTED',submittedAt:'2026-01-01T00:00:00Z'}]:{evidence:[{questionId:'Q02',selections:[{label:'미니멀'}]}]};};
 run("member={email:'a@example.test'};session={sessionId:'saved'};loadSession=async id=>{globalThis.loaded=id;};");
 await run('openMyPage()');
 assert.match(node('main').innerHTML,/진행 중/);assert.match(node('main').innerHTML,/제출 완료/);
 assert.match(node('main').innerHTML,/미니멀/);assert.match(node('main').innerHTML,/여성복 Simple/);
 const dashboard=node('main').innerHTML;
 const savedSection=dashboard.slice(dashboard.indexOf('<section class="mypage-records" id="mypage-saved"'),dashboard.indexOf('<section class="mypage-records" id="mypage-recent"'));
 assert.match(savedSection,/직접 저장한 결과가 없습니다/);assert.doesNotMatch(savedSection,/여성복 Simple|남성복 Simple|이어서 답하기/);
 assert.match(dashboard,/내 취향 한눈에/);assert.match(dashboard,/마이페이지 메뉴/);
 node('#mypage-record-1').onclick();await settle();assert.equal(run('loaded'),'saved');
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
  if(path==='/member/results')return {current:null,saved:[{sessionId:saved.sessionId,saved:true,current:false}],limit:5};
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

test('report survives non-auth failures in optional requests', async () => {
 for (const failedPath of ['initial-candidates', 'matched-demo-products']) {
  const { run, node, client, context } = screen();
  const sections = [];

  context.document.createElement = () => {
   const element = {
    innerHTML: '',
    className: '',
    setAttribute() {},
    querySelector: () => ({})
   };
   sections.push(element);
   return element;
  };

  client.request = async path => {
   if (path.endsWith('/' + failedPath)) {
    throw Object.assign(new Error('HTTP_500'), { status: 500 });
   }
   if (path.endsWith('/preference-summary')) {
    return {
     session: {
      sessionId: 'owned',
      status: 'SUBMITTED',
      definitionId: 'def',
      revision: 10,
      apparelScope: 'mens'
     },
     title: '미니멀',
     description: '직접 선택한 취향 기록',
     evidence: [{
      questionId: 'Q02',
      prompt: '마음이 가는 스타일',
      selections: [{ optionId: 'R01', label: '미니멀', images: [] }]
     }]
    };
   }
   if (path.endsWith('/initial-candidates')) {
    return {
     sessionId: 'owned',
     definitionId: 'def',
     revision: 10,
     simpleRepresentative: {
      candidateId: 'R01',
      sources: [{ optionLabel: '미니멀' }]
     }
    };
   }
   if (path.endsWith('/matched-demo-products')) {
    return { products: [] };
   }
   throw new Error(path);
  };

  run("member={email:'a@example.test'};session={sessionId:'owned',apparelScope:'mens'}");
  await run('action(result)');

  assert.notEqual(run('member'), null);
  assert.equal(run('session.sessionId'), 'owned');
  assert.match(node('main').innerHTML, /나의 TasteDNA 리포트/);
  assert.match(node('main').innerHTML, /미니멀/);
  assert.doesNotMatch(node('main').innerHTML, /FORM 로그인/);

  if (failedPath === 'matched-demo-products') {
   assert.ok(
       sections.some(element =>
           element.innerHTML.includes('전시 상품을 불러오지 못했어요')
       ),
       '상품 조회 실패 시 상품 영역에 오류 안내가 표시되어야 합니다.'
   );
  }
 }
});


test('actual male R22 and female R09 FINAL responses remain separate from Q02 R01 across report and history',async()=>{
 for(const [scope,rep,ids,names] of [
  ['mens','R22',['R07','R13','R22'],['Street','Graphic Street','Silhouette-led']],
  ['womens','R09',['R09','R15'],['Romantic','Expressive Romantic']]
 ]){
  const {run,node,client,calls}=screen();
  const saved={sessionId:'saved-'+scope,mode:'SIMPLE',apparelScope:scope,status:'SUBMITTED',definitionId:'def-'+scope,revision:10,submittedAt:'2026-11-10T00:00:00Z'};
  const initial={...saved,finalResult:true,simpleRepresentative:{candidateId:'R01',candidateName:'Minimal',sources:[{optionLabel:'미니멀'}]},
   decision:{status:'FINAL',representativeCandidate:rep,results:ids.map((id,i)=>({candidateId:id,candidateName:names[i],evidence:{status:id==='R22'?'SIMPLE_COMPOSITE_PREFERENCE_INFERENCE':'CANDIDATE_RELATIONS_PENDING',positive:[{questionId:'Q08',optionId:'C01:B',optionLabel:'비교 선택'}]}}))}};
  client.request=async(path,body)=>{
   calls.push([path,body]);
   if(path==='/member/results')return {current:null,saved:[{sessionId:saved.sessionId,saved:true,current:false}],limit:5};
  if(path==='/sessions/history')return [saved];
   if(path.endsWith('/answers'))return {session:saved,answers:{}};
   if(path.endsWith('/questions'))return {questions:[]};
   if(path.endsWith('/preference-summary'))return {session:saved,title:'직접 선택 기록',description:'',evidence:[{questionId:'Q02',selections:[{optionId:'R01',label:'미니멀',images:[]}]},{questionId:'Q01',selections:[{optionId:'A',label:'블랙',images:[]}]}]};
   if(path.endsWith('/initial-candidates'))return initial;
   if(path.endsWith('/matched-demo-products'))return {products:[]};
   throw new Error(path);
  };
  run("member={email:'a@example.test'}");
  for(let revisit=0;revisit<2;revisit++){
   run('session=null');await run('openMyPage()');
   const dashboard=node('main').innerHTML;
   assert.equal((dashboard.match(/종합 RESULT(?:<\/span>| ·)/g)||[]).length,3); // 최근 요약, 저장 목록, 최근 목록
   assert.ok(dashboard.includes(rep+' · '+names[ids.indexOf(rep)]));
   assert.match(dashboard,/R01 · Minimal/);
   assert.doesNotMatch(dashboard,/종합 유형이 아닌 Q02/);
   node('#mypage-saved-0').onclick();await settle();
   const report=node('main').innerHTML;
   assert.ok(report.includes('<h2>'+rep+' · '+names[ids.indexOf(rep)]+'</h2>'));
   assert.match(report,/직접 선택한 스타일 · R01 · Minimal/);
   assert.match(report,/블랙/);assert.match(report,/관측 근거/);
   assert.doesNotMatch(report,/종합.*아직 확정하지 않습니다/);
   assert.match(report,/<details class="report-history">/);
   assert.ok(report.indexOf('id="report-products"')<report.indexOf('class="report-history"'));
  }
  assert.ok(calls.some(([p])=>p==='/sessions/'+saved.sessionId+'/matched-demo-products'));
 }
});

test('FINAL requires matching session snapshot and a named representative inside decision results',async()=>{
 for(const invalid of ['flag','status','missing','name','session','revision','definition']){
  const {run,node,client}=screen();
  const saved={sessionId:'saved',mode:'SIMPLE',status:'SUBMITTED',definitionId:'def',revision:3};
  const initial={...saved,finalResult:true,decision:{status:'FINAL',representativeCandidate:'R22',results:[{candidateId:'R22',candidateName:'Silhouette-led'}]},simpleRepresentative:{sources:[{optionLabel:'미니멀'}]}};
  if(invalid==='flag')initial.finalResult=false;
  if(invalid==='status')initial.decision.status='DEFERRED';
  if(invalid==='missing')initial.decision.representativeCandidate='R09';
  if(invalid==='name')initial.decision.results[0].candidateName='';
  if(invalid==='session')initial.sessionId='other';
  if(invalid==='revision')initial.revision=2;
  if(invalid==='definition')initial.definitionId='other';
  client.request=async path=>path.endsWith('/preference-summary')?{session:saved,title:'직접 선호',description:'',evidence:[]}:path.endsWith('/initial-candidates')?initial:{products:[]};
  run("session={sessionId:'saved'}");await run('result()');
  assert.doesNotMatch(node('main').innerHTML,/종합 RESULT<|Silhouette-led/);
  assert.match(node('main').innerHTML,/종합 유형은 아직 확정하지 않습니다/);
 }
});

test('mypage FINAL lookup expiry clears member data while ordinary failure preserves history',async()=>{
 for(const status of [401,500]){
  const {run,node,client}=screen();
  client.request=async path=>{
   if(path==='/member/results')return {current:null,saved:[],limit:5};
   if(path==='/sessions/history')return [{sessionId:'saved',status:'SUBMITTED',apparelScope:'mens',submittedAt:'2026-11-10T00:00:00Z'}];
   if(path.endsWith('/preference-summary'))return {evidence:[]};
   throw Object.assign(new Error('lookup failed'),{status});
  };
  run("member={email:'a@example.test'}");await run('openMyPage()');
  if(status===401){assert.equal(run('member'),null);assert.match(node('main').innerHTML,/FORM 로그인/);assert.doesNotMatch(node('main').innerHTML,/저장된 테스트 기록/);}
  else {assert.match(node('main').innerHTML,/최근 테스트 기록/);assert.doesNotMatch(node('main').innerHTML,/종합 RESULT/);}
 }
});


test('report management permits deferred save but only FINAL can newly become current; legacy current can clear',async()=>{
 for(const [isFinal,current] of [[false,false],[true,false],[false,true]]){
  const {run,node,client,calls}=screen();
  const initial={finalResult:isFinal,decision:{status:isFinal?'FINAL':'DEFERRED',representativeCandidate:isFinal?'R01':null}};
  client.request=async(path,body)=>{calls.push([path,body]);return {saved:false,current,result:initial};};
  run("member={email:'a@example.test'}");await run("renderResultManagement('result-id')");
  const html=node('#report-management').innerHTML;
  assert.match(html,/id="result-save"/);
  if(isFinal||current)assert.match(html,/id="result-current"/);
  else {assert.doesNotMatch(html,/id="result-current"/);assert.match(html,/결과 저장은 가능합니다/);}
  node('#result-save').onclick();await settle();
  assert.ok(calls.some(([path,body])=>path==='/member/results/result-id/save'&&body));
  if(isFinal||current){node('#result-current').onclick();await settle();
   assert.ok(calls.some(([path,body])=>path===(current?'/member/results/current/clear':'/member/results/result-id/current')&&body));}
  else assert.ok(!calls.some(([path,body])=>path.endsWith('/current')&&body));
 }
});

test('mypage saved deferred record cannot newly become current but remains readable and removable',async()=>{
 const {run,node,client,calls}=screen();
 const record={sessionId:'deferred',status:'SUBMITTED',apparelScope:'womens',definitionId:'def',revision:10};
 const initial={...record,finalResult:false,decision:{status:'DEFERRED',results:[]}};
 client.request=async(path,body)=>{calls.push([path,body]);
  if(path==='/sessions/history')return [record];
  if(path==='/member/results')return {current:null,saved:[{sessionId:record.sessionId,saved:true,result:initial}],limit:5};
  if(path.endsWith('/preference-summary'))return {session:record,evidence:[]};
  if(path.endsWith('/initial-candidates'))return initial;
  return {};
 };
 run("member={email:'a@example.test'};loadSession=async id=>{globalThis.loaded=id;}");await run('myPage()');
 assert.match(node('#saved-current-0').replacement.textContent,/미확정 · 현재 설정 불가/);
 assert.equal(node('#saved-current-0').onclick,null);
 node('#mypage-saved-0').onclick();await settle();assert.equal(run('loaded'),'deferred');
 node('#saved-remove-0').onclick();await settle();
 assert.ok(calls.some(([path,body])=>path==='/member/results/deferred/unsave'&&body));
 assert.ok(!calls.some(([path,body])=>path.endsWith('/current')&&body));
});

test('guest result uses response bundle only and offers no retention or transfer actions',async()=>{
 for(const scope of ['mens','womens']){
  const {run,node,client,calls}=screen();
  client.request=async(path)=>{calls.push([path]);throw new Error('Unexpected persisted guest request');};
  run(`guest=true;session={sessionId:'transient',status:'SUBMITTED',apparelScope:'${scope}',definitionId:'def',revision:10};
   guestResult={summary:{session,title:'직접 선호',description:'',evidence:[]},initial:{...session,finalResult:false,decision:{status:'DEFERRED',results:[]}},products:{products:[]}};`);
  await run('result()');
  assert.equal(calls.length,0);
  assert.match(node('#report-management').innerHTML,/계정 저장·현재 설정·로그인 후 이전은 지원하지 않습니다/);
  assert.doesNotMatch(node('#report-management').innerHTML,/id="result-save"|id="result-current"/);
  assert.equal(node('#download').hidden,true);
 }
});


test('result deletion requires confirmation and refreshes mypage after clearing deleted session memory',async()=>{
 const {run,client,calls}=screen();
 client.request=async(path,body)=>{calls.push([path,body]);return {};};
 run("session={sessionId:'remove-me'};answers={Q01:{selectedOptionIds:['A']}};questions=[{}];step=4;window.confirm=()=>false;myPage=async()=>{globalThis.refreshed=true;}");
 await run("deleteMemberResult('remove-me')");
 assert.equal(calls.length,0);assert.equal(run('session.sessionId'),'remove-me');
 run('window.confirm=()=>true');await run("deleteMemberResult('remove-me')");
 assert.equal(calls.length,1);assert.equal(calls[0][0],'/member/results/remove-me/delete');
 assert.equal(run('session'),null);assert.equal(run('Object.keys(answers).length'),0);
 assert.equal(run('questions.length'),0);assert.equal(run('refreshed'),true);assert.equal(run('returnTo'),'mypage');
});

test('current TasteDNA deletion is refused in the UI before confirmation or request',async()=>{
 const {run,node,calls}=screen();
 run("window.confirm=()=>{throw new Error('current result must not prompt deletion');}");
 await run("deleteMemberResult('current-result',true)");
 assert.equal(calls.length,0);assert.match(node('#notice').textContent,/현재 TasteDNA 설정을 먼저 해제/);
});
