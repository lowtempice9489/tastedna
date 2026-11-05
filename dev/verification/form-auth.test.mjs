import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createClient} from '../../src/main/resources/static/form-auth.mjs';

const response=(status,data)=>({status,ok:status>=200&&status<300,json:async()=>data});
function fixture(mode='member'){
 const calls=[];let token=0;
 const queue=[];
 const client=createClient(async(path,options)=>{
  calls.push({path,...options});
  if(path.endsWith('/form/config'))return response(200,{authMode:mode});
  if(path.endsWith('/auth/csrf'))return response(200,{headerName:'X-CSRF-TOKEN',token:'token-'+ ++token});
  return queue.shift()||response(200,{memberId:'form-test'});
 });
 return {client,calls,queue};
}
test('signup does not log in; explicit login and logout rotate CSRF; cookie credentials without Basic',async()=>{
 const {client,calls,queue}=fixture();await client.init();
 await client.request('/auth/signup',{email:'test@example.test',password:'test-only-password'});
 assert.equal(calls.filter(c=>c.path.endsWith('/auth/login')).length,0);
 await client.request('/auth/login',{email:'test@example.test',password:'test-only-password'});
 await client.request('/sessions/simple-image',{});
 queue.push(response(204,null));await client.request('/auth/logout',{});
 await client.request('/auth/signup',{});
 const posts=calls.filter(c=>c.method==='POST');
 assert.deepEqual(posts.map(c=>c.headers['X-CSRF-TOKEN']),['token-1','token-1','token-2','token-2','token-3']);
 assert.ok(calls.every(c=>c.credentials==='same-origin'&&c.cache==='no-store'));
 assert.ok(calls.every(c=>!c.headers?.Authorization));
 assert.throws(()=>client.setBasic('Basic forbidden'),/BASIC_NOT_ALLOWED/);
});
test('CSRF rejection refreshes and retries once, other failures never replay mutations',async()=>{
 const {client,calls,queue}=fixture();await client.init();
 queue.push(response(403,{code:'CSRF_INVALID'}),response(200,{}));
 await client.request('/sessions/id/answers',{expectedRevision:1});
 const posts=calls.filter(c=>c.method==='POST');
 assert.equal(posts.length,2);assert.equal(posts[0].body,posts[1].body);
 assert.notEqual(posts[0].headers['X-CSRF-TOKEN'],posts[1].headers['X-CSRF-TOKEN']);
 queue.push(response(403,{code:'CSRF_INVALID'}),response(403,{code:'CSRF_INVALID'}));
 await assert.rejects(client.request('/sessions/id/answers',{}),/CSRF_INVALID/);
 queue.push(response(401,{code:'AUTHENTICATION_REQUIRED'}));
 const before=calls.length;
 await assert.rejects(client.request('/sessions/id/submit',{}),{status:401});
 assert.equal(calls.length,before+1);
 queue.push(response(404,{code:'SESSION_NOT_FOUND'}));
 await assert.rejects(client.request('/sessions/other/answers'),/SESSION_NOT_FOUND/);
});
test('local Basic remains explicit and memory-only, configuration failures cannot enable it',async()=>{
 const {client,calls}=fixture('local');await client.init();client.setBasic('Basic test');
 await client.request('/sessions/simple-image',{});
 assert.equal(calls.at(-1).headers.Authorization,'Basic test');
 assert.equal(calls.at(-1).headers['X-CSRF-TOKEN'],undefined);
 client.clear();await client.request('/sessions/active');assert.equal(calls.at(-1).headers.Authorization,undefined);
 const broken=createClient(async()=>response(500,{}));
 await assert.rejects(broken.init(),/AUTH_MODE_UNAVAILABLE/);
 await assert.rejects(broken.request('/sessions/active'),/AUTH_MODE_UNAVAILABLE/);
 assert.throws(()=>broken.setBasic('Basic test'),/BASIC_NOT_ALLOWED/);
});
