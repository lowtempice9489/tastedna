import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
const sql=readFileSync(new URL('../fixtures/simple/womens_simple_q05_q06_q10_definition_draft.sql',import.meta.url),'utf8');
const v13=readFileSync(new URL('../../src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql',import.meta.url),'utf8');
const rows=v13.split('\n').filter(l=>l.startsWith('INSERT INTO td_question_definition'));
const payloads=new Map([...sql.matchAll(/SET @womens_(q\d{2})_payload = '([^\n]+)';/g)].map(m=>[m[1].toUpperCase(),JSON.parse(m[2].replaceAll("''","'"))]));
test('ten question templates stay inert and executable preparation only uses SET',()=>{
 assert.equal(payloads.size,10);
 assert.deepEqual([...sql.matchAll(/\/\* DRAFT (Q\d{2})/g)].map(m=>m[1]),[...payloads.keys()]);
 const executable=sql.replace(/\/\*[\s\S]*?\*\//g,'').replace(/--[^\n]*/g,'');
 assert.equal(executable.replace(/SET @\w+ = '[^\n]*';/g,'').trim(),'');
 assert.match(sql,/SET @womens_version_code = 'SIMPLE-WOMENS-IMAGE-V1'/);
 const id=sql.match(/SET @womens_definition_id = '([^']+)'/)[1];
 assert.match(id,/^[0-9a-f-]{36}$/);assert.ok(!v13.includes(id));
 assert.match(sql,/APPROVED_PUBLISHED_AT\}\},FALSE/);
 for(const q of payloads.keys())assert.ok(sql.includes(`{{APPROVED_${q}_REQUIRED}}`));
});
for(let i=1;i<=10;i++){
 const q='Q'+String(i).padStart(2,'0');
 test(`${q}: prepared payload exactly matches approved source`,()=>{
  const actual=payloads.get(q);let expected;
  if(i<=4){const l=rows[i-1];expected=JSON.parse(l.slice(l.indexOf(",'{")+2,l.lastIndexOf("','{}',TRUE)")).replaceAll("''","'").replaceAll('\\\\','\\'));}
  else expected=JSON.parse(readFileSync(new URL(`../fixtures/simple/womens_simple_${q.toLowerCase()}_draft.json`,import.meta.url),'utf8')).questionPayload;
  assert.deepEqual(actual,expected);
  const groups=actual.comparisons||[actual];
  if(actual.comparisons)assert.equal(new Set(groups.map(g=>g.id)).size,groups.length);
  for(const g of groups){
   assert.equal(new Set(g.options.map(o=>o.id)).size,g.options.length);
   for(const url of [...(g.overviewImages||[]),...g.options.flatMap(o=>o.images||[])]){
    assert.ok(url.startsWith('/assets/gender/female/'));
    assert.ok(existsSync(new URL('../../'+url.slice('/assets/'.length),import.meta.url)),url);
   }
   if(g.selectionMode==='RANKED_MULTI_SELECT')assert.ok(!('minSelections' in g));
  }
 });
}
test('shared direct observation data matches V13 without new scoring',()=>{
 const actual=JSON.parse(sql.match(/SET @womens_shared_direct_rules = '([^\n]+)';/)[1].replaceAll("''","'"));
 const expected=v13.split('\n').filter(l=>l.startsWith('INSERT INTO td_option_scoring_rule')&&/,'Q0[1-4]',/.test(l)).map(l=>{
  const m=l.match(/VALUES \('[^']+','(Q0[1-4])','([^']+)','([^']*)','([^']*)'\);/);
  return {questionId:m[1],optionId:m[2],scoringPayload:JSON.parse(m[3]),observationPayload:JSON.parse(m[4])};
 });
 assert.deepEqual(actual,expected);assert.ok(actual.length>0);
 for(const rule of actual){assert.deepEqual(rule.scoringPayload,{});assert.ok(payloads.get(rule.questionId).options.some(o=>o.id===rule.optionId));}
});
