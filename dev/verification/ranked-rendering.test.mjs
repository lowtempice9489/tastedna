import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as state from '../../src/main/resources/static/assessment-state.mjs';

// 실제 app.js 렌더 함수를 격리된 DOM 대역에서 실행한다. 브라우저 레이아웃 검증은 아니다.
function renderer() {
 const element={innerHTML:'',querySelector:()=>({}),querySelectorAll:()=>[],focus:()=>{}};
 const context=vm.createContext({...state,createClient:()=>({}),document:{querySelector:()=>element},location:{hash:'#taste'},
   history:{replaceState(){}},URLSearchParams,window:{scrollTo(){}},setTimeout,clearTimeout});
 const source=readFileSync(new URL('../../src/main/resources/static/app.js',import.meta.url),'utf8');
 vm.runInContext(source.replace(/^import .*;\r?\n/gm,'').replace(/initialize\(\);\s*$/, ''),context);
 return context;
}
test('rank badges, review, result and download preserve saved ranking',async()=>{
 const context=renderer();
 const fixture=JSON.parse(readFileSync(new URL('../fixtures/simple/ranked_comparison_question_payload.json',import.meta.url),'utf8'));
 context.fixture=fixture;
 vm.runInContext(`answers.Q09={selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionIds:['B','A']}]};
   questions=[{questionId:'Q09',questionPayload:fixture}];`,context);
 const b=vm.runInContext("optionMarkup(fixture.comparisons[1].options[1],questions[0],'C02')",context);
 const a=vm.runInContext("optionMarkup(fixture.comparisons[1].options[0],questions[0],'C02')",context);
 assert.match(b,/checkbox/);assert.match(b,/1위 · 항목 B/);assert.match(a,/2위 · 항목 A/);
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
 context.document.createElement=()=>({querySelector:()=>({}),click(){}});
 vm.runInContext('main.append=()=>{}',context);
 let blob;
 context.Blob=Blob;context.URL={createObjectURL(value){blob=value;return 'blob:test';},revokeObjectURL(){}};
 context.setTimeout=()=>0;
 context.data={title:'테스트',description:'',session:{definitionId:'fixture',revision:1},evidence:[context.evidence,
   ...['Q05','Q06','Q07','Q10'].map(questionId=>({questionId,selections:[],alternatives:[],meaning:'NO_EXPLICIT_AVOIDANCE'}))]};
 vm.runInContext("session={sessionId:'fixture'};api=async()=>data",context);
 await vm.runInContext('result()',context);
 nodes.get('#download').onclick();
 const download=await blob.text();
 assert.match(download,/1위 항목 B \/ 2위 항목 A/);
 assert.match(download,/이 비교에서 선호하지 않은 항목: 항목 C/);
});
