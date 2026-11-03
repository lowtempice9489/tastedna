import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';
import {activeGroups, choose, valid} from '../../src/main/resources/static/assessment-state.mjs';
const sql=readFileSync(new URL('../../src/main/resources/db/migration/V13__add_mens_image_simple_definition.sql',import.meta.url),'utf8');
const questions=sql.split('\n').filter(l=>l.startsWith('INSERT INTO td_question_definition')).map(l=>JSON.parse(l.slice(l.indexOf(",'{")+2,l.lastIndexOf("','{}',TRUE)")).replaceAll("''","'").replaceAll('\\\\','\\')));
test('10 questions preserve text and image contracts',()=>{
 assert.equal(questions.length,10);
 for(const q of questions.slice(0,4))assert.ok(!JSON.stringify(q).includes('/assets/'));
 assert.equal(questions[9].options.length,10);
});
test('Q07 exclusive NONE and maximum selection',()=>{
 const q=questions[6];let a=choose(q,null,'A');a=choose(q,a,'G');
 assert.ok(valid(q,a));assert.deepEqual(choose(q,a,'B'),a);
 a=choose(q,a,'NONE');assert.deepEqual(a.selectedOptionIds,['NONE']);
 a=choose(q,a,'G');assert.deepEqual(a.selectedOptionIds,['G']);
 assert.ok(!valid(q,{selectedOptionIds:['NONE','A']}));
});
test('conditional followup is required and cleared after parent changes',()=>{
 const q=questions[7];let a=choose(q,null,'A','C01');a=choose(q,a,'B','C02');a=choose(q,a,'C','C03');
 assert.ok(!valid(q,a));a=choose(q,a,'B','C02_POCKETS');assert.ok(valid(q,a));
 a=choose(q,a,'A','C02');assert.ok(valid(q,a));assert.equal(a.selections.length,3);
 assert.ok(!a.selections.some(s=>s.comparisonId==='C02_POCKETS'));
});
test('saved five-way comparison restores and rejects extra or unknown options',()=>{
 const q=questions[8];let a=choose(q,null,'A','C01');a=choose(q,a,'E','C02');a=choose(q,a,'B','C03');
 assert.ok(valid(q,JSON.parse(JSON.stringify(a))));
 assert.ok(!valid(q,choose(q,a,'Z','C02')));
 assert.ok(!valid(q,{selections:[...a.selections,{comparisonId:'X',selectedOptionId:'A'}]}));
});
test('every image path maps to an existing source image',()=>{
 let count=0;
 function walk(x){if(typeof x==='string'&&x.startsWith('/assets/gender/')){assert.ok(existsSync(x.slice('/assets/'.length)),x);count++;}else if(x&&typeof x==='object')Object.values(x).forEach(walk);}
 questions.forEach(walk);assert.ok(count>30);
});


test('migrated DB configuration preserves experimental metadata without promoting verification',()=>{
 const migration=readFileSync(new URL('../../src/main/resources/db/migration/V14__move_remaining_scoring_configuration_to_db.sql',import.meta.url),'utf8');
 const files=['simple_style_core_discrimination_v0.1.json','simple_q05_q10_outfit_metadata_v0.1.json','simple_q08_comparison_metadata_v0.1.json','simple_q09_combination_metadata_v0.1.json'];
 const rows=migration.split('\n').filter(l=>l.startsWith('INSERT INTO td_scoring_configuration'));
 assert.equal(rows.length,4);
 rows.forEach((line,index)=>{
  const migrated=JSON.parse(line.slice(line.indexOf(",'{")+2,line.lastIndexOf("');")).replaceAll("''","'").replaceAll('\\\\','\\'));
  const source=JSON.parse(readFileSync(new URL('../fixtures/result/'+files[index],import.meta.url),'utf8'));
  assert.deepEqual(migrated,source);
 });
});

