import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, statSync} from 'node:fs';

// 운영 정의를 로드하지 않고 준비용 fixture와 원본 파일만 검사한다.
for(const [questionId,count] of [['Q05',10],['Q06',8],['Q07',11]]){
 test(`${questionId}: draft policy, unique options and exact original image paths`,()=>{
  const fixture=JSON.parse(readFileSync(new URL(`../fixtures/simple/womens_simple_${questionId.toLowerCase()}_draft.json`,import.meta.url),'utf8'));
  assert.equal(fixture.status,'DRAFT_NOT_FOR_ACTIVATION');
  assert.equal(fixture.apparelScope,'WOMENS');assert.equal(fixture.mode,'SIMPLE');
  assert.equal(fixture.questionId,questionId);
  const payload=fixture.questionPayload;
  assert.equal(payload.kind,'MULTIPLE_CHOICE');assert.equal(payload.maxSelections,3);
  const expectedIds=Array.from({length:count},(_,i)=>String.fromCharCode(65+i));
  assert.deepEqual(payload.options.map(o=>o.id),expectedIds);
  assert.equal(new Set(payload.options.map(o=>o.id)).size,count);
  const paths=payload.options.flatMap(o=>o.images);
  assert.equal(new Set(paths).size,count);
  const directory=new URL(`../../gender/female/${questionId}/`,import.meta.url);
  const names=readdirSync(directory);
  for(const option of payload.options){
   assert.deepEqual(option.images,[`/assets/gender/female/${questionId}/${option.id}.png`]);
   assert.ok(names.includes(`${option.id}.png`),'대소문자까지 일치하는 원본 파일');
   const file=new URL(`${option.id}.png`,directory);
   assert.ok(statSync(file).isFile());
   const png=readFileSync(file);
   assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
   assert.ok(png.readUInt32BE(16)>0&&png.readUInt32BE(20)>0);
   assert.equal(Object.hasOwn(option,'label'),false);
  }
  assert.deepEqual(Object.keys(payload).sort(),['kind','maxSelections','options']);
  assert.deepEqual(Object.keys(fixture.unresolved).sort(),[
   'prompt','description','optionLabels','minimumSelectionsAndRequired','noneOptionAndExclusivity','observationMeaning'
  ].sort());
  assert.ok(Object.values(fixture.unresolved).every(value=>value==='PENDING_APPROVAL'));
 });
}
