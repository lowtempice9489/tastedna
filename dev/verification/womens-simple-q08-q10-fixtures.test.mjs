import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync,statSync} from 'node:fs';

const expected={
 Q08:[['C01','SINGLE','AB',3],['C02','SINGLE','ABCD',1],['C03','SINGLE','AB',1],['C04','RANKED_MULTI_SELECT','ABC',1],['C05','RANKED_MULTI_SELECT','ABCD',1]],
 Q09:[['C01','SINGLE','AB',4],['C02','RANKED_MULTI_SELECT','ABCDE',1],['C03','SINGLE','AB',4],['C04','SINGLE','AB',4]]
};
for(const q of ['Q08','Q09','Q10'])test(`${q}: draft structure, group mapping and image inventory`,()=>{
 const f=JSON.parse(readFileSync(new URL(`../fixtures/simple/womens_simple_${q.toLowerCase()}_draft.json`,import.meta.url),'utf8'));
 assert.equal(f.status,'DRAFT_NOT_FOR_ACTIVATION');assert.equal(f.questionId,q);
 assert.equal(f.apparelScope,'WOMENS');assert.equal(f.mode,'SIMPLE');
 assert.equal(f.unresolved.minimumSelectionsAndRequired,'PENDING_APPROVAL');
 assert.equal(f.unresolved.observationMeaning,'PENDING_APPROVAL');
 assert.ok(!('prompt' in f.questionPayload));
 const images=[];
 function options(group,ids,size,prefix){
  assert.deepEqual(group.options.map(o=>o.id),[...ids]);
  assert.equal(new Set(group.options.map(o=>o.id)).size,ids.length);
  assert.ok(!('minSelections' in group));
  for(const o of group.options){
   assert.equal(o.images.length,size);
   for(const [i,p] of o.images.entries()){
    assert.ok(p.startsWith(prefix));assert.doesNotMatch(p,/desktop|mobile/);
    if(size>1)assert.equal(p,`${prefix}${o.id}${i+1}.png`);
    images.push(p);
   }
  }
 }
 if(q==='Q10'){
  assert.equal(f.questionPayload.kind,'MULTIPLE_CHOICE');assert.equal(f.questionPayload.maxSelections,6);
  options(f.questionPayload,'ABCDEFGHIJKLMNOPQRS',1,'/assets/gender/female/Q10/');
  for(const o of f.questionPayload.options)assert.equal(o.images[0],`/assets/gender/female/Q10/${o.id}.png`);
  images.push(...Object.values(f.referenceImages));
 }else{
  assert.equal(f.questionPayload.kind,'COMPARISON');
  assert.deepEqual(f.questionPayload.comparisons.map(g=>g.id),expected[q].map(g=>g[0]));
  for(const [i,[id,mode,ids,size]] of expected[q].entries()){
   const g=f.questionPayload.comparisons[i];assert.equal(g.selectionMode,mode);
   assert.equal(g.overviewImages.length,2);assert.match(g.overviewImages[0],/desktop/);assert.match(g.overviewImages[1],/mobile/);
   const prefix=`/assets/gender/female/${q}/${id}/`;
   g.overviewImages.forEach(p=>assert.ok(p.startsWith(prefix)));
   images.push(...g.overviewImages);options(g,ids,size,prefix);
   const evidence=f.designEvidence[i];assert.equal(evidence.comparisonId,id);assert.equal(evidence.sourceImage,g.overviewImages[0]);
  }
 }
 assert.equal(new Set(images).size,images.length);
 for(const p of images){
  const file=new URL('../../'+p.slice('/assets/'.length),import.meta.url);
  assert.ok(statSync(file).isFile());
  assert.ok(readdirSync(new URL('.',file)).includes(decodeURIComponent(file.pathname.split('/').at(-1))));
  assert.equal(readFileSync(file).subarray(0,8).toString('hex'),'89504e470d0a1a0a');
 }
 const root=new URL(`../../gender/female/${q}/`,import.meta.url);
 const inventory=readdirSync(root,{recursive:true}).filter(p=>p.endsWith('.png')).map(p=>`/assets/gender/female/${q}/`+p.replaceAll('\\','/'));
 assert.deepEqual([...images].sort(),inventory.sort());
});
