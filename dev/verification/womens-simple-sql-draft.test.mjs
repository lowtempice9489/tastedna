import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';

const sql=readFileSync(new URL('../fixtures/simple/womens_simple_q05_q06_q10_definition_draft.sql',import.meta.url),'utf8');
const blocks=[...sql.matchAll(/\/\* DRAFT (Q\d{2})\n([\s\S]*?)\*\//g)];

test('SQL draft contains only three inert INSERT templates, no activation or DB commands',()=>{
 assert.deepEqual(blocks.map(b=>b[1]),['Q05','Q06','Q10']);
 assert.equal(sql.replace(/\/\*[\s\S]*?\*\//g,'').replace(/--[^\n]*/g,'').trim(),'');
 for(const [,q,body] of blocks){
  assert.match(body,/^INSERT INTO td_question_definition/);
  assert.match(body,/\{\{APPROVED_WOMENS_DEFINITION_ID\}\}/);
  assert.ok(body.includes(`{{APPROVED_${q}_REQUIRED}}`));
  assert.ok(body.includes(`{{APPROVED_${q}_DEPENDENCY_PAYLOAD}}`));
  assert.doesNotMatch(body,/\b(UPDATE|DELETE|USE|CREATE|COMMIT|active|td_option_scoring_rule)\b/i);
 }
});

for(const [q,max,count] of [['Q05',3,10],['Q06',3,8],['Q10',6,19]]){
 test(`${q}: SQL JSON matches fixture, option codes and real images`,()=>{
  const body=blocks.find(b=>b[1]===q)?.[2];assert.ok(body);
  const match=body.match(/'(?<id>Q\d{2})',\s*(?<order>\d+),\s*'(?<json>\{[^\n]+\})',/);
  assert.ok(match);assert.equal(match.groups.id,q);assert.equal(Number(match.groups.order),Number(q.slice(1)));
  const payload=JSON.parse(match.groups.json.replaceAll("''","'"));
  const fixture=JSON.parse(readFileSync(new URL(`../fixtures/simple/womens_simple_${q.toLowerCase()}_draft.json`,import.meta.url),'utf8'));
  assert.deepEqual(payload,fixture.questionPayload);
  assert.equal(payload.kind,'MULTIPLE_CHOICE');assert.equal(payload.maxSelections,max);
  assert.deepEqual(Object.keys(payload).sort(),['kind','maxSelections','options']);
  assert.deepEqual(payload.options.map(o=>o.id),Array.from({length:count},(_,i)=>String.fromCharCode(65+i)));
  const directory=new URL(`../../gender/female/${q}/`,import.meta.url);
  const names=readdirSync(directory);
  for(const option of payload.options){
   assert.deepEqual(Object.keys(option).sort(),['id','images']);
   assert.deepEqual(option.images,[`/assets/gender/female/${q}/${option.id}.png`]);
   assert.ok(names.includes(`${option.id}.png`));
  }
 });
}
