import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';
import {activeGroups, choose, valid} from '../../src/main/resources/static/assessment-state.mjs';
const rankedQuestion=JSON.parse(readFileSync(new URL('../fixtures/simple/ranked_comparison_question_payload.json',import.meta.url),'utf8'));

test('ranked click order, middle removal, reselect and saved restoration',()=>{
 let a=choose(rankedQuestion,null,'A','C01');
 a=choose(rankedQuestion,a,'B','C02');a=choose(rankedQuestion,a,'A','C02');
 assert.deepEqual(a.selections[1].selectedOptionIds,['B','A']);
 a=choose(rankedQuestion,a,'C','C02');a=choose(rankedQuestion,a,'A','C02');
 assert.deepEqual(a.selections[1].selectedOptionIds,['B','C']);
 a=choose(rankedQuestion,a,'A','C02');
 assert.deepEqual(a.selections[1].selectedOptionIds,['B','C','A']);
 const restored=JSON.parse(JSON.stringify(a));assert.ok(valid(rankedQuestion,restored));
 assert.deepEqual(restored.selections[1].selectedOptionIds,['B','C','A']);
 assert.deepEqual(choose(rankedQuestion,restored,'B','C02').selections[1].selectedOptionIds,['C','A']);
});
test('ranked validation keeps fields, IDs, duplicates and minimum strict',()=>{
 const answer=ids=>({selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionIds:ids}]});
 for(const ids of [[],['A','A'],['Z'],[null],'A'])assert.equal(valid(rankedQuestion,answer(ids)),false);
 assert.equal(valid(rankedQuestion,{selections:[{comparisonId:'C01',selectedOptionIds:['A']},{comparisonId:'C02',selectedOptionIds:['B']}]}),false);
 assert.equal(valid(rankedQuestion,{selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionId:'B'}]}),false);
 assert.equal(valid(rankedQuestion,{selections:[{comparisonId:'C01',selectedOptionId:'A'},{comparisonId:'C02',selectedOptionIds:['B'],selectedOptionId:'B'}]}),false);
 const missing=structuredClone(rankedQuestion);delete missing.comparisons[1].minSelections;
 assert.equal(valid(missing,answer(['A'])),false);
 const zero=structuredClone(rankedQuestion);zero.comparisons[1].minSelections=0;
 assert.ok(valid(zero,answer([])));assert.ok(valid(zero,choose(zero,answer(['B']),null,'C02')));
 const all=structuredClone(rankedQuestion);all.comparisons[1].minSelections=3;
 assert.equal(valid(all,answer(['B','A'])),false);assert.ok(valid(all,answer(['B','A','C'])));
});
test('ranked child disappears when the single parent hides it',()=>{
 const q=structuredClone(rankedQuestion);q.comparisons[1].showWhen={comparisonId:'C01',optionIds:['A']};
 let a=choose(q,null,'A','C01');assert.equal(valid(q,a),false);
 a=choose(q,a,'B','C02');assert.ok(valid(q,a));
 a=choose(q,a,'B','C01');assert.deepEqual(a.selections,[{comparisonId:'C01',selectedOptionId:'B'}]);
 assert.ok(valid(q,a));
});
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

