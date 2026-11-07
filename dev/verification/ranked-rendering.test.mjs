import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as state from '../../src/main/resources/static/assessment-state.mjs';

// 실제 app.js 렌더 함수를 격리된 DOM 대역에서 실행한다. 브라우저 레이아웃 검증은 아니다.
function renderer() {
 const element={innerHTML:'',querySelector:()=>({append(){}}),querySelectorAll:()=>[],focus:()=>{}};
 const context=vm.createContext({...state,createClient:()=>({}),document:{querySelector:()=>element},location:{hash:'#taste'},
   history:{replaceState(){}},URLSearchParams,window:{scrollTo(){}},setTimeout,clearTimeout});
 const source=readFileSync(new URL('../../src/main/resources/static/app.js',import.meta.url),'utf8');
 vm.runInContext(source.replace(/^import .*;\r?\n/gm,'').replace(/initialize\(\);\s*$/, ''),context);
 return context;
}
test('letter question layout preserves content, original images and saved selections',()=>{
 const context=renderer();
 context.fixture={kind:'SINGLE_CHOICE',prompt:'원래 질문 <제목>',description:'원래 설명',options:[
  {id:'A',label:'선택 A',images:['/assets/gender/male/Q10/A.png']},
  {id:'B',label:'선택 B',images:[]}
 ]};
 vm.runInContext("session={apparelScope:'mens'};questions=[{questionId:'Q01',questionPayload:fixture}];answers.Q01={selectedOptionId:'A'};render()",context);
 const html=vm.runInContext('main.innerHTML',context);
 assert.match(html,/<article class="question-letter" aria-labelledby="question-title">/);
 assert.match(html,/<h1 id="question-title">원래 질문 &lt;제목&gt;<\/h1>/);
 assert.match(html,/원래 설명/);
 assert.match(html,/01 \/ 10/);
 assert.match(html,/src="\/assets\/gender\/male\/Q10\/A.png"/);
 assert.match(html,/type="radio"[^>]*value="A"[^>]*checked/);
 assert.match(html,/id="back" disabled/);
 assert.match(html,/id="next">다음 질문/);
 assert.equal(vm.runInContext('answers.Q01.selectedOptionId',context),'A');

 context.fixture=JSON.parse(readFileSync(new URL('../fixtures/simple/ranked_comparison_question_payload.json',import.meta.url),'utf8'));
 vm.runInContext("questions=[{questionId:'Q09',questionPayload:fixture}];answers.Q09={selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionIds:['B','A']}]};render()",context);
 const ranked=vm.runInContext('main.innerHTML',context);
 assert.match(ranked,/1위<\/span><span class="choice-name">항목 B/);assert.match(ranked,/2위<\/span><span class="choice-name">항목 A/);
 assert.match(ranked,/각 비교의 선택 안내를 따라주세요/);
 assert.equal(vm.runInContext("answers.Q09.selections[1].selectedOptionIds.join(',')",context),'B,A');
});
test('card types and rank badges follow removal, compaction and reselection',()=>{
 const context=renderer();
 context.fixture=JSON.parse(readFileSync(new URL('../fixtures/simple/ranked_comparison_question_payload.json',import.meta.url),'utf8'));
 vm.runInContext("questions=[{questionId:'Q09',questionPayload:fixture}];answers.Q09={selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionIds:['B','A','C']}]}",context);
 const card=id=>vm.runInContext(`optionMarkup(fixture.comparisons[1].options.find(o=>o.id==='${id}'),questions[0],'C02')`,context);
 vm.runInContext("answers.Q09=choose(fixture,answers.Q09,'A','C02')",context);
 assert.match(card('A'),/rank-empty">미선택/);assert.doesNotMatch(card('A'),/checked/);
 assert.match(card('C'),/rank-badge ">2위/);
 vm.runInContext("answers.Q09=choose(fixture,answers.Q09,'A','C02')",context);
 assert.match(card('A'),/choice-ranked selected/);assert.match(card('A'),/rank-badge ">3위/);
 const single=vm.runInContext("optionMarkup(fixture.comparisons[0].options[0],questions[0],'C01')",context);
 assert.match(single,/choice-single selected/);assert.match(single,/type="radio"/);assert.doesNotMatch(single,/rank-badge/);
 const multiple=vm.runInContext("optionMarkup({id:'A',label:'항목 A'},{questionId:'Q07',questionPayload:{kind:'MULTIPLE_CHOICE'}})",context);
 assert.match(multiple,/choice-multiple/);assert.match(multiple,/type="checkbox"/);assert.doesNotMatch(multiple,/rank-badge/);
});
test('report renders supplied evidence only and preserves revisit, restart and storefront actions',async()=>{
 const context=renderer(),nodes=new Map();
 context.document.querySelector=selector=>{
  if(!nodes.has(selector))nodes.set(selector,{});
  return nodes.get(selector);
 };
 const shopButton={};
 context.document.createElement=()=>({querySelector:()=>shopButton,setAttribute(){}});
 context.data={title:'서버의 제목 <확인>',description:'서버 설명',session:{definitionId:'fixture',revision:3,status:'SUBMITTED'},evidence:[
  {questionId:'Q02',prompt:'스타일',selections:[{label:'실제 선택 스타일',images:[]}]},
  {questionId:'Q08',comparisonId:'C01',prompt:'비교 기록',selections:[{label:'실제 선택 A',images:[]}],alternatives:[{label:'대안 B'}]},
  {questionId:'Q07',prompt:'회피',meaning:'EXPLICIT_AVOIDANCE',selections:[{label:'실제 회피 C',images:[]}]}
 ]};
 vm.runInContext("main.append=()=>{};session={sessionId:'saved'};var requests=[];api=async path=>{requests.push(path);return data};var destination='';selectApparel=()=>{destination='apparel'};shop=()=>{destination='shop'}",context);
 await vm.runInContext('result()',context);
 const html=vm.runInContext('main.innerHTML',context);
 assert.match(html,/class="taste-report"/);assert.match(html,/서버의 제목 &lt;확인&gt;/);
 for(const text of ['실제 선택 스타일','실제 선택 A','대안 B','실제 회피 C','선택하지 않은 쪽이 싫다는 뜻은 아니에요'])assert.ok(html.includes(text));
 assert.doesNotMatch(html,/마음이 가는 색|첫눈에 끌린 착장|일치율|92%|추천 상품/);
 nodes.get('#restart').onclick();assert.equal(vm.runInContext('destination',context),'apparel');
 shopButton.onclick();assert.equal(vm.runInContext('destination',context),'shop');
 context.data={...context.data,title:'재조회한 서버 제목',evidence:[]};
 await vm.runInContext('result()',context);
 const revisited=vm.runInContext('main.innerHTML',context);
 assert.match(revisited,/재조회한 서버 제목/);assert.doesNotMatch(revisited,/실제 선택 스타일|피하고 싶은 착장이 없었어요/);
 assert.equal(vm.runInContext('requests.join(",")',context),'/sessions/saved/preference-summary,/sessions/saved/initial-candidates,/sessions/saved/matched-demo-products,/sessions/saved/preference-summary,/sessions/saved/initial-candidates,/sessions/saved/matched-demo-products');
});
test('rank badges, review, result and download preserve saved ranking',async()=>{
 const context=renderer();
 const fixture=JSON.parse(readFileSync(new URL('../fixtures/simple/ranked_comparison_question_payload.json',import.meta.url),'utf8'));
 context.fixture=fixture;
 vm.runInContext(`answers.Q09={selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionIds:['B','A']}]};
   questions=[{questionId:'Q09',questionPayload:fixture}];`,context);
 const b=vm.runInContext("optionMarkup(fixture.comparisons[1].options[1],questions[0],'C02')",context);
 const a=vm.runInContext("optionMarkup(fixture.comparisons[1].options[0],questions[0],'C02')",context);
 assert.match(b,/checkbox/);assert.match(b,/1위<\/span><span class="choice-name">항목 B/);assert.match(a,/2위<\/span><span class="choice-name">항목 A/);
 const review=vm.runInContext('answerLabels(questions[0])',context);
 assert.ok(review.indexOf('1위 항목 B')<review.indexOf('2위 항목 A'));
 assert.match(review,/이 비교에서 비선호: 항목 C/);
 context.evidence={questionId:'Q09',comparisonId:'C02',prompt:'순위형',meaning:'RANKED_COMPARATIVE_PREFERENCE',
   selections:[{optionId:'B',label:'항목 B',images:[],rank:1},{optionId:'A',label:'항목 A',images:[],rank:2}],
   alternatives:[],notPreferred:[{optionId:'C',label:'항목 C',images:[]}]};
 const card=vm.runInContext('evidenceCard(evidence)',context);
 assert.ok(card.indexOf('1위 · 항목 B')<card.indexOf('2위 · 항목 A'));
 assert.match(card,/이 비교에서 선호하지 않은 항목: 항목 C/);
 assert.doesNotMatch(card,/싫다는 뜻은 아니에요/);
 const single=vm.runInContext("evidenceCard({...evidence,notPreferred:[],alternatives:[{label:'단일 B'}]})",context);
 assert.match(single,/선택하지 않은 쪽이 싫다는 뜻은 아니에요/);

 // 다운로드 버튼의 실제 핸들러가 만드는 Blob도 확인한다.
 const nodes=new Map();
 context.document.querySelector=selector=>{
   if(!nodes.has(selector))nodes.set(selector,{innerHTML:'',querySelector:()=>({}),querySelectorAll:()=>[],append(){}});
   return nodes.get(selector);
 };
 context.document.createElement=()=>({querySelector:()=>({}),click(){},setAttribute(){}});
 vm.runInContext('main.append=()=>{}',context);
 let blob;
 context.Blob=Blob;context.URL={createObjectURL(value){blob=value;return 'blob:test';},revokeObjectURL(){}};
 context.setTimeout=()=>0;
 context.data={title:'테스트',description:'',session:{definitionId:'fixture',revision:1,status:'SUBMITTED'},evidence:[context.evidence,
   ...['Q05','Q06','Q07','Q10'].map(questionId=>({questionId,selections:[],alternatives:[],meaning:'NO_EXPLICIT_AVOIDANCE'}))]};
 vm.runInContext("session={sessionId:'fixture'};api=async()=>data",context);
 await vm.runInContext('result()',context);
 nodes.get('#download').onclick();
 const download=await blob.text();
 assert.match(download,/1위 항목 B \/ 2위 항목 A/);
 assert.match(download,/이 비교에서 선호하지 않은 항목: 항목 C/);
});

test('matched demo products render server fields, empty and failure states without losing report',async()=>{
 for(const outcome of ['products','empty','failure']){
  const context=renderer(),sections=[];
  context.document.createElement=()=>{const e={setAttribute(){},querySelector:()=>({})};sections.push(e);return e;};
  context.data={title:'나의 직접 선호 기록',description:'서버 리포트',session:{status:'SUBMITTED',definitionId:'def',revision:10},evidence:[]};
  context.matched={products:outcome==='products'?[{id:'look-01',name:'서버 상품 <A>',imageUrl:'/assets/gender/male/Q06/TasteDNA_Q06_A.png',price:59000,demo:true,evidence:[{questionId:'Q06',optionId:'A'}]}]:[]};
  context.fail=outcome==='failure';
  vm.runInContext("main.append=()=>{};session={sessionId:'saved'};api=async path=>{if(path.endsWith('/preference-summary'))return data;if(fail)throw new Error('network');return matched}",context);
  await vm.runInContext('result()',context);
  assert.match(vm.runInContext('main.innerHTML',context),/나의 직접 선호 기록/);
  const html=sections.find(e=>e.className==='matched-products').innerHTML;
  assert.match(html,/내가 선택한 스타일과 연결된 FORM 전시 상품/);
  assert.match(html,/Q02에서 직접 선택한 스타일과 상품에 등록된 스타일 속성이 일치/);
  assert.doesNotMatch(html,/같은 이미지를 사용하는/);
  assert.doesNotMatch(html,/추천|유사도|점수/);
  if(outcome==='products'){
   assert.match(html,/서버 상품 &lt;A&gt;/);assert.match(html,/59,000원/);
   assert.match(html,/Q06 · A/);assert.match(html,/src="\/assets\/gender\/male\/Q06\/TasteDNA_Q06_A.png"/);
  }else assert.match(html,outcome==='empty'?/연결된 FORM 전시 상품이 없습니다/:/전시 상품을 불러오지 못했어요/);
 }
});

test('unsubmitted report cannot render or request matched products',async()=>{
 const context=renderer();
 vm.runInContext("session={sessionId:'pending'};var calls=[];api=async path=>{calls.push(path);return {session:{status:'IN_PROGRESS'}}}",context);
 await assert.rejects(vm.runInContext('result()',context),/SESSION_NOT_SUBMITTED/);
 assert.equal(vm.runInContext('calls.length',context),1);
 assert.doesNotMatch(vm.runInContext('main.innerHTML',context),/matched-products|FORM 전시 상품/);
});

test('mens and womens report keeps products near summary, four at a time, and details collapsed',async()=>{
 for(const apparelScope of ['mens','womens']){
  const context=renderer(),sections=[],more={};
  context.document.createElement=()=>{const e={setAttribute(){},querySelector:()=>more};sections.push(e);return e;};
  context.data={title:'미니멀',description:'직접 선택 요약',session:{status:'SUBMITTED',definitionId:'def',revision:10,apparelScope},evidence:[
   {questionId:'Q02',prompt:'스타일',selections:[{optionId:'R01',label:'미니멀',images:[]}]},
   {questionId:'Q09',comparisonId:'C02',prompt:'순위',selections:[{label:'B',rank:1,images:['/assets/gender/female/Q09/B.png']},{label:'A',rank:2,images:[]}],notPreferred:[{label:'C'}]}
  ]};
  context.matched={products:Array.from({length:9},(_,i)=>({id:'p'+i,name:'상품 '+i,imageUrl:'/assets/gender/female/Q06/product-'+i+'.png',price:59000,evidence:[{questionId:'Q02',optionId:'R01',styleCode:'R01',styleLabel:'미니멀'},{questionId:'Q01',optionId:'A',colorCode:'A',colorLabel:'블랙'}]}))};
  vm.runInContext("main.append=()=>{};session={sessionId:'saved'};api=async path=>path.endsWith('/preference-summary')?data:matched",context);
  await vm.runInContext('result()',context);
  const html=vm.runInContext('main.innerHTML',context);
  assert.match(html,new RegExp((apparelScope==='mens'?'남성복':'여성복')+' · Simple'));
  assert.ok(html.indexOf('id="report-products"')<html.indexOf('id="direct-preferences"'));
  assert.match(html,/<details class="report-history"><summary>내 상세 선택 기록/);
  assert.doesNotMatch(html,/<details[^>]*class="report-history"[^>]*\bopen/);
  assert.ok(html.indexOf('1위 · B')<html.indexOf('2위 · A'));
  assert.match(html,/이 비교에서 선호하지 않은 항목: C/);
  assert.match(html,/src="\/assets\/gender\/female\/Q09\/B.png"/);
  const products=sections.find(e=>e.className==='matched-products');
  const count=()=> (products.innerHTML.match(/class="matched-product-card"/g)||[]).length;
  assert.equal(count(),4);assert.match(products.innerHTML,/Q01 · A · 색상 블랙 \(A\)/);assert.match(products.innerHTML,/Q02 · R01 · 미니멀 \(R01\)/);
  more.onclick();assert.equal(count(),8);
  more.onclick();assert.equal(count(),9);assert.doesNotMatch(products.innerHTML,/id="more-products"/);
 }
});

test('Simple representative is separate from comprehensive result and optional',async()=>{
 for(const representative of [{sources:[{optionLabel:'미니멀'}]},null]){
  const context=renderer();
  context.document.createElement=()=>({setAttribute(){},querySelector:()=>({})});
  context.data={title:'직접 선택 스타일',description:'',session:{status:'SUBMITTED',definitionId:'def',revision:2},evidence:[]};
  context.initial={sessionId:'saved',definitionId:'def',revision:2,simpleRepresentative:representative};
  vm.runInContext("main.append=()=>{};session={sessionId:'saved'};api=async path=>path.endsWith('/preference-summary')?data:path.endsWith('/initial-candidates')?initial:{products:[]}",context);
  await vm.runInContext('result()',context);
  const html=vm.runInContext('main.innerHTML',context);
  if(representative){assert.match(html,/Simple 대표 스타일/);assert.match(html,/<h2>미니멀<\/h2>/);}
  else assert.doesNotMatch(html,/Simple 대표 스타일/);
  assert.match(html,/종합 유형은 아직 확정하지 않습니다/);
 }
});