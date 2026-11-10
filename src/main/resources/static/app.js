import {storefront} from './shop.mjs';
import {createClient} from './form-auth.mjs';
import {activeGroups, selectionMap, choose, valid, isRanked} from './assessment-state.mjs';

const main=document.querySelector('main'), notice=document.querySelector('#notice'), dialog=document.querySelector('#zoom');
const client=createClient();
let selectedScope='mens', guest=false, guestResult=null;
let authMode, member=null, returnTo=null, session=null, questions=[], answers={}, step=0, busy=false, toastTimer;
const titles=['COLOR','STYLE','SILHOUETTE','PATTERN','FIRST IMPRESSION','YOUR WARDROBE','NOT FOR ME','THE DETAILS','STYLING','FINAL LOOK'];
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const asset=p=>'/assets/gender/male/'+p;
const safeUrl=url=>url?.startsWith('/assets/gender/') ? encodeURI(url) : '';
function toast(message){notice.textContent=message;clearTimeout(toastTimer);toastTimer=setTimeout(()=>notice.textContent='',6500);}
function focusMain(){main.focus({preventScroll:true});window.scrollTo({top:0,behavior:'instant'});}
function loading(message){main.innerHTML='<div class="loading"><div class="spinner"></div><p>'+esc(message)+'</p></div>';}
async function api(path, body){
 return client.request(path,body);
}
const errors={
 RESULT_STORAGE_FULL:'저장한 결과가 5개입니다. 마이페이지에서 보관하지 않을 결과를 저장 해제한 뒤 다시 저장해주세요.',
 INVALID_ANSWER:'응답을 확인해주세요. 선택 개수와 비교 순위를 지켜야 합니다.',
 IMAGE_DEFINITION_NOT_READY:'선택한 의류 테스트 정의가 아직 준비되지 않았어요. 다른 의류 범위를 선택하거나 나중에 다시 시도해주세요.',
 INVALID_CREDENTIALS:'이메일 또는 비밀번호를 확인해주세요.',
 EMAIL_ALREADY_REGISTERED:'이미 가입된 이메일입니다. FORM 로그인으로 이동해주세요.',
 VALIDATION_FAILED:'이메일 형식과 비밀번호 조건을 확인해주세요.',
 AUTH_RATE_LIMITED:'요청이 많습니다. 잠시 후 다시 시도해주세요.',
 CSRF_INVALID:'보안 확인이 만료되었습니다. 다시 시도해주세요.',
 ACCESS_DENIED:'이 요청에 접근할 권한이 없습니다.',
 ACTIVE_DEFINITION_NOT_FOUND:'새 질문 정의가 아직 DB에 등록되지 않았습니다. 서버의 마이그레이션 적용 상태를 확인해주세요.',
 SESSION_REVISION_CONFLICT:'다른 화면에서 답변이 바뀌었어요. 최신 저장 답변을 다시 불러왔습니다.',
 SESSION_NOT_EDITABLE:'이미 제출한 테스트입니다. 결과 화면으로 이동합니다.',
 SESSION_NOT_SUBMITTED:'아직 제출하지 않은 테스트입니다.',
 SIMPLE_ANSWERS_INCOMPLETE:'아직 답하지 않은 질문이 있어요.',
 SIMPLE_SESSION_REQUIRED:'현재 진행 중인 세션은 Simple 테스트가 아닙니다. 기존 세션을 확인해주세요.',
 SESSION_NOT_SUBMITTABLE:'이미 제출되었거나 제출할 수 없는 테스트입니다.',
 SESSION_NOT_FOUND:'이 계정에서 해당 테스트를 찾을 수 없습니다.',
 HTTP_401:'계정 또는 비밀번호를 확인해주세요.',
 HTTP_403:'현재 실행 환경에서 접근할 수 없습니다. local 프로필을 확인해주세요.',
 HTTP_500:'서버에서 처리하지 못했습니다. 저장된 답변은 유지됩니다. 잠시 후 다시 시도해주세요.'
};
async function action(fn){
 if(busy)return;busy=true;document.querySelectorAll('button').forEach(b=>b.disabled=true);
 try{await fn();}
 catch(e){
  if(authMode==='member'&&e.status===401&&e.message!=='INVALID_CREDENTIALS'){
   returnTo=new URLSearchParams(location.hash.slice(1)).get('session')||returnTo||'taste';
   clearMember();formAuth();toast('FORM 로그인이 필요합니다. 저장된 답변은 로그인 후 이어갈 수 있어요.');return;
  }
  if(e.message==='SESSION_REVISION_CONFLICT'||e.message==='SESSION_NOT_EDITABLE'||e.message==='SESSION_NOT_SUBMITTABLE'){
   try{await loadSession(session.sessionId);}catch{}
  }
  toast(errors[e.message]||'요청을 완료하지 못했어요. 연결 상태를 확인하고 다시 시도해주세요.');
 }
 finally{busy=false;document.querySelectorAll('button').forEach(b=>b.disabled=false);updateNext();}
}
function home(){
 history.replaceState(null,'','#taste');
 main.innerHTML=`<div class="atelier">
 <section class="atelier-hero" aria-labelledby="atelier-title">
  <div class="atelier-copy"><p class="atelier-kicker">FORM의 특별한 취향 기록 <span aria-hidden="true">/</span> YOUR PERSONAL ATELIER</p><h1 id="atelier-title">TasteDNA</h1><h2>검색보다 먼저,<br><em>나의 취향</em>을 발견하는 시간.</h2><p class="atelier-description">설명하기 어려웠던 내가 좋아하는 것들.<br>작은 선택을 모아, 나만의 취향을 기록해보세요.</p><div class="atelier-actions"><button class="primary" id="begin">테스트 시작하기 <span aria-hidden="true">↗</span></button><a class="secondary" href="#taste-guide">이용 과정 보기 ↓</a></div><p class="atelier-availability">남성복·여성복 Simple 10문항을 선택할 수 있어요.</p></div>
  <div class="atelier-art" aria-hidden="true"><div class="atelier-orbit orbit-one"></div><div class="atelier-orbit orbit-two"></div><span class="art-word word-style">Style</span><span class="art-word word-mood">Mood</span><span class="art-word word-taste">Taste</span><div class="material-card"><div class="material-weave"></div><div class="material-caption"><span>THE TEXTURE OF YOU</span><b>Every shade.<br>Every little detail.</b></div></div><div class="palette-card"><span>PERSONAL NOTES / 01</span><div class="palette-swatches"><i></i><i></i><i></i><i></i></div><em>A little more you.</em></div><span class="art-index">AN EXPLORATION OF PERSONAL TASTE</span></div>
 </section>
 <div class="atelier-philosophy"><p>취향은 한 가지 모습으로<br>정의되지 않으니까.</p><div><span>색감</span><span>질감</span><span>분위기</span><span>스타일</span></div><small>마음이 가는 선택을 있는 그대로 기록해요.</small></div>
 <section class="atelier-guide" id="taste-guide" aria-labelledby="guide-title"><div class="guide-heading"><span></span><h2 id="guide-title"><b>TasteDNA</b>, 이렇게 이용해보세요</h2><span></span></div><div class="guide-grid">
 <article><span class="guide-number">01 / EXPLORE</span><div class="guide-symbol" aria-hidden="true">◌</div><h3>탐색할 취향 선택</h3><p>살펴보고 싶은 의류를 선택해요.<br>남성복·여성복 Simple 테스트를 이용할 수 있어요.</p></article>
 <article><span class="guide-number">02 / CHOOSE</span><div class="guide-symbol" aria-hidden="true">≡</div><h3>마음이 가는 쪽으로</h3><p>10개의 질문에 답하며 색, 스타일,<br>디테일에 대한 선호를 알려주세요.</p></article>
 <article><span class="guide-number">03 / DISCOVER</span><div class="guide-symbol" aria-hidden="true">◎</div><h3>나의 취향 기록 확인</h3><p>직접 고른 선택과 그 근거를<br>하나의 선호 요약으로 돌아보세요.</p></article>
 <article><span class="guide-number">04 / BROWSE</span><div class="guide-symbol" aria-hidden="true">↗</div><h3>새로운 시선으로 탐색</h3><p>직접 고른 스타일과 연결된<br>FORM 전시 상품을 살펴보세요.</p></article>
 </div><p class="guide-footnote">직접 선택한 스타일과 색상을 바탕으로 FORM 전시 상품을 연결해드려요. 관측 근거가 충분하면 종합 RESULT를 제공하며, 근거가 부족하면 직접 선호 기록을 안내합니다.</p></section>
 </div>`;
 document.querySelector('#begin').onclick=selectCategory;
 document.querySelector('.atelier-actions a').onclick=e=>{
  e.preventDefault();document.querySelector('#taste-guide').scrollIntoView({block:'start'});
 };
}
function selectCategory(){
 history.replaceState(null,'','#taste');
 // 준비 중 카드는 버튼으로 만들지 않아 공통 요청 처리 후에도 활성화되지 않는다.
 main.innerHTML=`<section class="category-screen" aria-labelledby="category-title">
 <ol class="category-progress" aria-label="취향 탐색 과정"><li aria-current="step"><span>01</span> 카테고리 선택</li><li><span>02</span> 테스트 진행</li><li><span>03</span> 기록 확인</li></ol>
 <p class="eyebrow">TASTEDNA / EXPLORE YOUR TASTE</p><h1 id="category-title">어떤 취향을 탐색해볼까요?</h1><p class="category-description">마음이 가는 카테고리에서 나의 취향을 발견해보세요.</p>
 <div class="category-grid">
 <button type="button" class="category-card category-available" id="category-apparel"><svg viewBox="0 0 64 64" aria-hidden="true" focusable="false"><path d="M22 12 10 18 3 31l12 7 5-8v25h24V30l5 8 12-7-7-13-12-6c-1 10-19 10-20 0Z"/></svg><span class="category-name">의류</span><span class="category-detail">색과 스타일, 나에게 끌리는 옷</span><span class="category-state">취향 탐색하기 ↗</span></button>
 <article class="category-card category-pending" aria-disabled="true"><svg viewBox="0 0 64 64" aria-hidden="true" focusable="false"><path d="M14 30V18q0-6 6-6h24q6 0 6 6v12M12 27q-7 0-7 7v15h54V34q0-7-7-7t-7 7v5H19v-5q0-7-7-7ZM13 49v7m38-7v7"/></svg><h2 class="category-name">리빙</h2><p class="category-detail">머물고 싶은 공간의 분위기</p><span class="category-state">준비 중</span></article>
 <article class="category-card category-pending" aria-disabled="true"><svg viewBox="0 0 64 64" aria-hidden="true" focusable="false"><path d="M25 23V12h14v11M32 12V6h14M23 23h18l6 12v21H17V35ZM18 38h28"/></svg><h2 class="category-name">뷰티</h2><p class="category-detail">나를 표현하는 색과 감각</p><span class="category-state">준비 중</span></article>
 <article class="category-card category-pending" aria-disabled="true"><svg viewBox="0 0 64 64" aria-hidden="true" focusable="false"><path d="M10 17h34v17a17 17 0 0 1-34 0ZM44 19h6a9 9 0 0 1 0 18h-6M6 55h46"/></svg><h2 class="category-name">라이프</h2><p class="category-detail">일상을 채우는 작은 취향</p><span class="category-state">준비 중</span></article>
 </div><p class="category-note">의류의 남성복·여성복 Simple 테스트를 선택할 수 있어요.<br>리빙·뷰티·라이프는 준비 중입니다.</p><button type="button" class="secondary" id="category-back">← TasteDNA 소개로</button>
 </section>`;
 document.querySelector('#category-apparel').onclick=selectApparel;
 document.querySelector('#category-back').onclick=()=>{home();focusMain();};
 focusMain();
}
function selectApparel(){
 history.replaceState(null,'','#taste');
 main.innerHTML=`<section class="wardrobe-screen" aria-labelledby="wardrobe-title"><p class="eyebrow">TASTEDNA / CHOOSE YOUR WARDROBE</p><h1 id="wardrobe-title">어떤 옷의 취향을<br>알아볼까요?</h1><p class="wardrobe-description">살펴보고 싶은 의류를 선택해주세요.</p>
 <div class="wardrobe-grid">
 <button type="button" class="wardrobe-card wardrobe-mens" id="choose-mens"><span class="wardrobe-art" aria-hidden="true"><svg viewBox="0 0 100 100" focusable="false"><path d="m35 19-18 9-6 51 17 3 4-28v34h36V54l4 28 17-3-6-51-18-9-15 9Z"/><path d="m35 19 15 9-12 19-8-20m35-8L50 28l12 19 8-20M50 28v60M35 62h9m12 0h9"/></svg></span><span class="wardrobe-copy"><span class="wardrobe-kicker">MENSWEAR</span><span class="wardrobe-name">남성복</span><span class="wardrobe-detail">마음이 가는 색과 스타일,<br>나의 의류 취향을 발견해보세요.</span><span class="wardrobe-cta">취향 알아보기 ↗</span></span></button>
 <button type="button" class="wardrobe-card wardrobe-womens" id="choose-womens" aria-label="여성복 취향 알아보기"><span class="wardrobe-art" aria-hidden="true"><svg viewBox="0 0 100 100" focusable="false"><path d="m35 18-16 9-8 34 15 4 9-23-3 43h36l-3-43 9 23 15-4-8-34-16-9-15 8Z"/><path d="m35 18 15 8-10 13-10-18m35-3L50 26l10 13 10-18M50 26v59M33 68h34"/></svg></span><span class="wardrobe-copy"><span class="wardrobe-kicker">WOMENSWEAR</span><span class="wardrobe-name">여성복</span><span class="wardrobe-detail">마음이 가는 색과 스타일,<br>나의 의류 취향을 발견해보세요.</span><span class="wardrobe-cta">취향 알아보기 ↗</span></span></button>
 </div><p class="wardrobe-note">남성복·여성복 Simple 테스트를 이용할 수 있어요.</p><button type="button" class="secondary" id="scope-back">← 카테고리로</button></section>`;
 document.querySelector('#choose-mens').onclick=()=>selectTestMode('mens');
 document.querySelector('#choose-womens').onclick=()=>selectTestMode('womens');
 document.querySelector('#scope-back').textContent='← 카테고리로';
 document.querySelector('#scope-back').onclick=selectCategory;
 focusMain();
}
function selectTestMode(scope='mens'){
 selectedScope=scope;
 history.replaceState(null,'','#taste');
 // Deep은 비대화형 카드로 두어 공통 버튼 활성화 처리에서도 시작할 수 없게 한다.
 main.innerHTML=`<section class="wardrobe-screen mode-screen" aria-labelledby="mode-title">
 <p class="eyebrow">TASTEDNA / ${selectedScope==='womens'?'WOMENSWEAR':'MENSWEAR'}</p><h1 id="mode-title">어떤 방식으로<br>취향을 알아볼까요?</h1><p class="wardrobe-description">지금은 Simple로 나의 취향 기록을 시작할 수 있어요.</p>
 <div class="wardrobe-grid">
 <button type="button" class="mode-card mode-simple" id="choose-simple"><span class="mode-icon" aria-hidden="true"><svg viewBox="0 0 64 64" focusable="false"><circle cx="32" cy="36" r="22"/><path d="M32 14V5m-7 0h14M32 22v15l9 5M48 17l5-5"/></svg></span><span class="mode-name">Simple</span><span class="mode-tag">10문항 · ${selectedScope==='womens'?'여성복':'남성복'}</span><span class="mode-description">색과 스타일, 마음이 가는 착장을 고르고<br>직접 선택한 선호를 기록해보세요.</span><span class="mode-action">Simple 시작하기 <span aria-hidden="true">↗</span></span></button>
 <article class="mode-card mode-deep" aria-disabled="true" aria-labelledby="deep-title"><span class="mode-icon" aria-hidden="true"><svg viewBox="0 0 64 64" focusable="false"><circle cx="27" cy="27" r="18"/><path d="m40 40 15 15M20 19q8-6 14 2"/></svg></span><h2 class="mode-name" id="deep-title">Deep</h2><span class="mode-tag">준비 중</span><p class="mode-description">더 깊이 취향을 탐색할 수 있도록<br>질문과 경험을 준비하고 있어요.</p><span class="mode-unavailable">아직 시작할 수 없어요</span></article>
 </div><p class="wardrobe-note">진행 중인 Simple 기록이 있다면 이어서 답할 수 있어요.</p><button type="button" class="secondary" id="mode-back">← 의류 선택으로</button>
 </section>`;
 document.querySelector('#choose-simple').onclick=()=>authMode==='member'&&!member?action(startGuest):login();
 document.querySelector('#mode-back').onclick=selectApparel;
 focusMain();
}
function login(){
 if(authMode==='member'){
  returnTo=new URLSearchParams(location.hash.slice(1)).get('session')||'taste';
  if(member)action(continueTaste);else formAuth();return;
 }
 if(authMode!=='local')return;
 main.innerHTML='<form class="login"><p class="eyebrow">YOUR PERSONAL STUDY</p><h2>취향의 기록을<br>시작해볼까요?</h2><p class="muted">계정으로 연결하면 저장된 질문부터 이어갈 수 있어요.</p><label for="member">개발용 회원 ID</label><input id="member" autocomplete="username" value="dev-image-demo" required><label for="password">개발용 비밀번호</label><input id="password" type="password" autocomplete="current-password" required><button class="primary">연결하고 시작하기 →</button><small>현재는 로컬 체험 버전입니다. 서버에 설정한 개발용 계정으로 연결하세요.</small></form>';
 document.querySelector('form').onsubmit=e=>{e.preventDefault();const member=document.querySelector('#member').value.trim(),password=document.querySelector('#password').value;
 client.setBasic('Basic '+btoa(Array.from(new TextEncoder().encode(member+':'+password),b=>String.fromCharCode(b)).join('')));
 document.querySelector('#password').value='';
 action(async()=>{
  const id=new URLSearchParams(location.hash.slice(1)).get('session');
  if(id){await loadSession(id);return;}
  const active=await api('/sessions/active');
  if(active){await loadSession(active.sessionId);return;}
  session=await api('/sessions/simple-image'+(selectedScope==='womens'?'?apparelScope=womens':''),{});
  await loadSession(session.sessionId);
 });
 };focusMain();
}
function clearMember(){
 guest=false;guestResult=null;
 member=null;session=null;questions=[];answers={};step=0;client.clear();updateMemberNav();
 if(dialog.open)dialog.close();
}
function updateMemberNav(){
 const nav=document.querySelector('#member-nav');
 if(authMode!=='member'){nav.textContent=authMode==='local'?'LOCAL · 개발용 체험':'';return;}
 nav.innerHTML=member?'<span>'+esc(member.email)+'</span><button id="form-mypage">마이페이지</button><button id="form-logout">로그아웃</button>':'<button id="form-mypage">마이페이지</button><button id="form-login">FORM 로그인</button><button id="form-signup">회원가입</button>';
 nav.querySelector('#form-mypage')?.addEventListener('click',()=>{if(!busy)openMyPage();});
 nav.querySelector('#form-login')?.addEventListener('click',()=>{if(!busy){returnTo=new URLSearchParams(location.hash.slice(1)).get('session')||null;formAuth();}});
 nav.querySelector('#form-signup')?.addEventListener('click',()=>{if(!busy){returnTo=new URLSearchParams(location.hash.slice(1)).get('session')||null;formAuth(true);}});
 nav.querySelector('#form-logout')?.addEventListener('click',()=>action(async()=>{
  await api('/auth/logout',{});returnTo=null;clearMember();shop();toast('로그아웃했습니다.');
 }));
}
function openMyPage(){
 if(authMode!=='member')return;
 returnTo='mypage';
 if(!member){formAuth();return;}
 return action(myPage);
}
function finalCandidate(initial){
 if(initial?.finalResult!==true||initial.decision?.status!=='FINAL'||!Array.isArray(initial.decision.results))return null;
 return initial.decision.results.find(c=>c.candidateId===initial.decision.representativeCandidate&&c.candidateName)||null;
}
function finalMeaning(candidate){
 const inferred=candidate.evidence?.status==='SIMPLE_COMPOSITE_PREFERENCE_INFERENCE';
 return 'Simple은 세밀한 확정 진단이 아닌 대략적인 취향 경향입니다. '+(inferred?'여러 직접 관측을 조합한 취향 가능성입니다. ':'선택한 착장·비교 관측에 기반한 결과입니다. ')+
  '혼합형은 혼합 취향 가능성, 속성형은 두드러진 속성 선호 경향을 뜻하며 영구적인 유형 확정은 아닙니다.';
}
function finalMarkup(initial,heading='h3'){
 const candidate=finalCandidate(initial);if(!candidate)return '';
 const sources=candidate.evidence?.positive||[];
 const evidence=[...new Set(sources.map(s=>s.questionId+' · '+(s.optionLabel||s.optionId)+(s.rank?' ('+s.rank+'순위)':'')))];
 return '<div class="comprehensive-result"><span class="tag">종합 RESULT</span><'+heading+'>'+esc(candidate.candidateId+' · '+candidate.candidateName)+'</'+heading+'><p class="muted">'+esc(finalMeaning(candidate))+'</p><p>유효 후보 · '+initial.decision.results.map(c=>esc(c.candidateId+' · '+c.candidateName)).join(' / ')+'</p>'+(evidence.length?'<p class="source">관측 근거 · '+esc(evidence.join(' / '))+'</p>':'')+'</div>';
}
async function myPage(){
 if(!member){returnTo='mypage';formAuth();return;}
 history.replaceState(null,'','#mypage');returnTo='mypage';
 loading('나의 TasteDNA 기록을 확인하고 있어요.');
 const records=await api('/sessions/history')||[];
 const managed=await api('/member/results');
 const savedEntries=managed.saved||[], currentEntry=managed.current;
 const savedRecords=records.filter(r=>savedEntries.some(e=>e.sessionId===r.sessionId));
 const latest=records.find(r=>r.status==='SUBMITTED');
 let summary=null;
 if(latest){
  try{summary=await api('/sessions/'+encodeURIComponent(latest.sessionId)+'/preference-summary');}
  catch(e){if(e.status===401)throw e;}
 }
 const finals=new Map();
 for(const record of records.filter(r=>r.status==='SUBMITTED')){
  try{
   const initial=await api('/sessions/'+encodeURIComponent(record.sessionId)+'/initial-candidates');
   if(initial?.sessionId===record.sessionId&&(!summary?.session||record!==latest||
      (initial.definitionId===summary.session.definitionId&&initial.revision===summary.session.revision)))finals.set(record.sessionId,initial);
  }catch(e){if(e.status===401)throw e;}
 }
 for(const entry of [...savedEntries,...(currentEntry?[currentEntry]:[])])if(entry.result)finals.set(entry.sessionId,entry.result);
 const latestInitial=latest?finals.get(latest.sessionId):null;
 const recordResult=r=>{const candidate=finalCandidate(finals.get(r.sessionId));return candidate?'<p>종합 RESULT · '+esc(candidate.candidateId+' · '+candidate.candidateName)+'</p>':'';};
 const currentCard='<section class="report-panel"><h2>현재 TasteDNA</h2>'+(currentEntry?finalMarkup(currentEntry.result)+directResultMarkup(currentEntry.result)+'<button class="secondary" id="current-open">결과 보기</button> <button class="secondary" id="current-clear">현재 설정 해제</button>':'<p>미설정 · 결과 리포트에서 직접 설정할 수 있어요.</p>')+'</section>';
 const styles=summary?.evidence?.find(e=>e.questionId==='Q02')?.selections||[];
 const latestDirect=latestInitial?.simpleRepresentative;
 const dateLabel=r=>new Date(r.submittedAt||r.startedAt).toLocaleString('ko-KR');
 const completed=records.filter(r=>r.status==='SUBMITTED');
 const scopeLabel=r=>r.apparelScope==='womens'?'여성복 Simple':'남성복 Simple';
 const recordCard=(r,i)=>'<article class="mypage-record"><div><p class="eyebrow">'+(r.status==='SUBMITTED'?'제출 완료':'진행 중')+'</p><h3>'+esc(scopeLabel(r))+'</h3><p class="muted">'+(r.submittedAt?'제출':'시작')+' · '+esc(dateLabel(r))+'</p>'+recordResult(r)+'</div><button class="secondary" id="mypage-record-'+i+'">'+(r.status==='SUBMITTED'?'결과 보기':'이어서 답하기')+' →</button></article>';
 const latestCard=latest?'<section class="mypage-latest"><div><p class="eyebrow">최근 완료한 취향 기록</p><h2>'+esc(scopeLabel(latest))+'</h2>'+finalMarkup(latestInitial)+'<p class="muted">내가 직접 선택한 스타일</p><h3 class="mypage-style-title">'+(latestDirect?.candidateName?esc(latestDirect.candidateId+' · '+latestDirect.candidateName):styles.length?esc(styles.map(s=>s.label).join(' · ')):'직접 선호 리포트')+'</h3><p class="muted">'+(finalCandidate(latestInitial)?'Q02 직접 선택 기록은 위 종합 RESULT와 별도로 보존합니다.':'종합 유형이 아닌 Q02 직접 선택 기록입니다.')+'</p></div><div class="mypage-latest-actions"><button class="primary" id="mypage-latest">결과 리포트 보기 →</button><button class="secondary" id="mypage-retest">다시 테스트 →</button><button class="secondary" id="mypage-products">연결된 FORM 상품 보기 →</button></div></section>':'<section class="mypage-latest"><div><p class="eyebrow">YOUR FIRST TASTEDNA</p><h2>나의 취향 기록을 시작해보세요</h2><p class="muted">완료한 테스트가 생기면 여기에 직접 선택한 취향을 모아 보여드려요.</p></div></section>';
 const preferenceRows=[['Q02','선호 스타일'],['Q01','선호 색상'],['Q03','선호 실루엣'],['Q04','선호 패턴']].map(([id,label])=>{
  const selections=summary?.evidence?.find(e=>e.questionId===id)?.selections||[];
  return selections.length?'<div><span>'+label+'</span><p>'+esc(selections.map(s=>s.label).join(' · '))+'</p></div>':'';
 }).join('');
 main.innerHTML='<section class="form-mypage"><header class="mypage-heading"><div><p class="eyebrow">FORM / MY PAGE</p><h1>마이페이지</h1></div><p class="mypage-heading-note">내가 고른 취향을 다시 만나는 곳,<br><em>TasteDNA</em>와 함께</p></header><div class="mypage-body"><aside><div class="mypage-profile"><span aria-hidden="true">F</span><div><strong>FORM 회원</strong><p>'+esc(member.email)+'</p></div></div><nav aria-label="마이페이지 메뉴"><a href="#mypage-overview">마이페이지 홈</a><a class="selected" href="#mypage-taste">내 TasteDNA</a><a href="#mypage-saved">저장한 결과</a><a href="#mypage-recent">최근 테스트 기록</a></nav><button class="secondary" id="mypage-shop">FORM 컬렉션으로 →</button></aside><div class="mypage-content" id="mypage-overview"><section class="mypage-member"><div><strong>나의 FORM, 나의 TasteDNA</strong><p>직접 고른 취향을 모아 나다운 선택을 발견하세요.</p></div><div><span>저장한 결과</span><b>'+savedEntries.length+' / 5</b></div><div><span>제출 완료</span><b>'+completed.length+'</b></div></section><div class="mypage-tab">내 TasteDNA</div><section class="mypage-intro" id="mypage-taste"><div><p class="eyebrow">FORM의 취향 기록 서비스 · TasteDNA</p><h2>내 TasteDNA</h2><p>마음이 가는 선택에서 발견한 나의 취향.<br>직접 선택한 선호를 돌아보고, 연결된 상품을 살펴보세요.</p><div class="mypage-banner-tags"><span>취향 테스트</span><span>직접 선호 리포트</span><span>스타일 기반 상품 연결</span></div></div><div class="mypage-banner-art" aria-hidden="true"><i></i><i></i><span>Style</span><em>Taste</em></div></section><h2 class="mypage-section-title">카테고리별 TasteDNA</h2><div class="mypage-categories"><div><strong>의류</strong><small>현재 이용 가능</small><p>남성복·여성복 Simple<br>직접 선택한 패션 취향</p></div><div><strong>리빙 · 준비 중</strong><p>공간의 취향</p></div><div><strong>뷰티 · 준비 중</strong><p>나를 위한 취향</p></div><div><strong>라이프 · 준비 중</strong><p>일상의 취향</p></div></div>'+currentCard+'<div class="mypage-summary-grid">'+latestCard+'<section class="mypage-at-glance"><h2>내 취향 한눈에</h2><p class="muted">최근 완료 기록에서 직접 확인한 선호</p>'+(preferenceRows||'<p class="mypage-empty">아직 표시할 직접 선호 정보가 없습니다.</p>')+'</section></div><div class="mypage-history-grid"><section class="mypage-records" id="mypage-saved"><h2>저장한 결과 <small>'+savedEntries.length+' / 5</small></h2><p class="muted">직접 저장한 결과만 표시합니다. 현재 설정과 저장은 독립적입니다.</p><div class="mypage-saved-grid">'+savedRecords.map(r=>'<article><span class="mypage-saved-mark" aria-hidden="true">TasteDNA</span><h3>'+esc(scopeLabel(r))+'</h3><p>'+esc(dateLabel(r))+'</p>'+recordResult(r)+'<button class="secondary" id="mypage-saved-'+records.indexOf(r)+'">결과 보기 →</button><button class="secondary" id="saved-remove-'+records.indexOf(r)+'">저장 해제</button><button class="secondary" id="saved-current-'+records.indexOf(r)+'">현재 TasteDNA로 설정</button></article>').join('')+'</div>'+(!savedEntries.length?'<p class="mypage-empty">직접 저장한 결과가 없습니다. 제출 이력은 최근 테스트 기록에서 확인하세요.</p>':'')+'</section><section class="mypage-records" id="mypage-recent"><h2>최근 테스트 기록 <small>'+records.length+'</small></h2><p class="muted">최근 제출·시작 순서로 표시해요.</p>'+records.map(recordCard).join('')+(!records.length?'<p class="mypage-empty">현재 조회 가능한 Simple 기록이 없습니다.</p>':'')+'</section></div><button class="secondary" id="mypage-start">TasteDNA 알아보기 →</button></div></div></section>';
 savedRecords.forEach(r=>{
  const i=records.indexOf(r);
  document.querySelector('#mypage-saved-'+i).onclick=()=>action(()=>loadSession(r.sessionId));
  document.querySelector('#saved-remove-'+i).onclick=()=>action(async()=>{await api('/member/results/'+r.sessionId+'/unsave',{});await myPage();});
  document.querySelector('#saved-current-'+i).onclick=()=>action(async()=>{await api('/member/results/'+r.sessionId+'/current',{});await myPage();});
 });
 if(currentEntry){
  document.querySelector('#current-open').onclick=()=>action(()=>loadSession(currentEntry.sessionId));
  document.querySelector('#current-clear').onclick=()=>action(async()=>{await api('/member/results/current/clear',{});await myPage();});
 }
 main.querySelectorAll('aside a').forEach(a=>a.onclick=e=>{e.preventDefault();main.querySelector(a.getAttribute('href'))?.scrollIntoView({behavior:'smooth',block:'start'});});
 records.forEach((r,i)=>document.querySelector('#mypage-record-'+i).onclick=()=>action(async()=>{
  returnTo=r.sessionId;
  if(r.status==='IN_PROGRESS')await api('/sessions/'+encodeURIComponent(r.sessionId)+'/resume',{});
  await loadSession(r.sessionId);
 }));
 if(latest){
  document.querySelector('#mypage-retest').onclick=()=>{returnTo=null;selectApparel();focusMain();};
  document.querySelector('#mypage-latest').onclick=()=>action(()=>loadSession(latest.sessionId));
  document.querySelector('#mypage-products').onclick=()=>action(async()=>{
   await loadSession(latest.sessionId);
   main.querySelector('#report-products')?.scrollIntoView({behavior:'smooth',block:'start'});
  });
 }
 document.querySelector('#mypage-shop').onclick=()=>{returnTo=null;shop();};
 document.querySelector('#mypage-start').onclick=()=>{returnTo=null;home();focusMain();};
 focusMain();
}
async function continueTaste(){
 const target=returnTo;returnTo=null;
 if(target==='mypage'){await myPage();return;}
 if(target&&target!=='taste'){await loadSession(target);return;}
 const active=await api('/sessions/active');
 if(active){await loadSession(active.sessionId);return;}
 session=await api('/sessions/simple-image'+(selectedScope==='womens'?'?apparelScope=womens':''),{});await loadSession(session.sessionId);
}
function formAuth(signup=false,email=''){
 main.innerHTML='<form class="login form-auth"><p class="eyebrow">FORM / MEMBERSHIP</p><h1>'+ (signup?'FORM 회원가입':'FORM 로그인')+'</h1><p class="muted">'+(returnTo?'FORM 회원으로 로그인하고 TasteDNA 취향 기록을 이어가세요.':'나의 스타일을 발견하는 곳, FORM에 오신 것을 환영합니다.')+'</p><label for="form-email">이메일</label><input id="form-email" name="email" type="email" autocomplete="username" maxlength="254" value="'+esc(email)+'" required><label for="form-password">비밀번호</label><input id="form-password" name="password" type="password" autocomplete="'+(signup?'new-password':'current-password')+'" '+(signup?'minlength="12" maxlength="72"':'')+' required>'+(signup?'<small id="password-help">12자 이상, UTF-8 기준 72바이트 이하로 입력해주세요.</small><label for="form-confirm">비밀번호 확인</label><input id="form-confirm" type="password" autocomplete="new-password" required>':'')+'<button class="primary" type="submit">'+(signup?'FORM 회원가입':'로그인')+'</button><button class="secondary" type="button" id="auth-switch">'+(signup?'이미 회원이신가요? 로그인':'처음이신가요? 회원가입')+'</button><button class="auth-back" type="button" id="auth-back">컬렉션으로 돌아가기</button><small>TasteDNA는 FORM에서 제공하는 패션 취향 기록 기능입니다.</small></form>';
 const form=main.querySelector('form');
 form.onsubmit=e=>{e.preventDefault();
  const email=form.querySelector('#form-email').value.trim(),input=form.querySelector('#form-password'),password=input.value;
  if(signup&&(password!==form.querySelector('#form-confirm').value||new TextEncoder().encode(password).length>72)){
   toast('비밀번호 확인이 일치하는지, 비밀번호가 72바이트 이하인지 확인해주세요.');return;
  }
  action(async()=>{
   try{
    const data=await api(signup?'/auth/signup':'/auth/login',{email,password});
    if(signup){formAuth(false,email);toast('가입이 완료되었습니다. FORM에 로그인해주세요.');}
    else{member=data;updateMemberNav();if(returnTo)await continueTaste();else shop();}
   }finally{input.value='';const confirm=form.querySelector('#form-confirm');if(confirm)confirm.value='';}
  });
 };
 main.querySelector('#auth-switch').onclick=()=>{if(!busy)formAuth(!signup,form.querySelector('#form-email').value);};
 main.querySelector('#auth-back').onclick=()=>{if(!busy){returnTo=null;shop();}};
 focusMain();
}

async function startGuest(){
 const data=await api('/guest/simple/questions?apparelScope='+selectedScope);
 guest=true;guestResult=null;answers={};questions=data.questions;step=0;
 session={mode:'SIMPLE',apparelScope:selectedScope,status:'IN_PROGRESS',revision:0,definitionId:data.definitionId};
 returnTo=null;history.replaceState(null,'','#taste');render();focusMain();
 toast('비회원 테스트입니다. 답변과 결과는 저장되지 않으며 새로고침하면 사라집니다.');
}
function directResultMarkup(initial){
 const direct=initial?.simpleRepresentative;
 return direct?'<p>직접 선택한 스타일 · '+esc(direct.candidateId+' · '+direct.candidateName)+'</p>':'<p>직접 선호와 종합 결과는 결과 리포트에서 확인하세요.</p>';
}

async function renderResultManagement(id){
 const box=document.querySelector('#report-management');

 if(guest){
  box.innerHTML='<p class="report-status">비회원 결과 · 이 화면에서만 확인할 수 있으며 계정 저장·현재 설정·로그인 후 이전은 지원하지 않습니다. 새로고침하면 결과가 사라집니다.</p>';
  return;
 }

 if(authMode!=='member'||!member)return;

 try{
  const state=await api('/member/results/'+id);
  const initial=state.result||await api('/sessions/'+id+'/initial-candidates');

  const isFinal=initial?.finalResult===true
      &&initial?.decision?.status==='FINAL'
      &&!!initial?.decision?.representativeCandidate;

  const currentControl=state.current
      ?'<button class="secondary" id="result-current">현재 TasteDNA · 설정 해제</button>'
      :isFinal
          ?'<button class="secondary" id="result-current">현재 TasteDNA로 설정</button>'
          :'<p class="muted">종합 RESULT가 확정되지 않아 현재 TasteDNA로 설정할 수 없습니다. 결과 저장은 가능합니다.</p>';

  box.innerHTML='<section class="report-panel">'
      +'<h2>나의 TasteDNA 관리</h2>'
      +'<p>테스트 완료와 저장, 현재 설정은 각각 별개의 선택입니다.</p>'
      +'<button class="secondary" id="result-save">'
      +(state.saved?'저장 해제':'결과 저장')
      +'</button> '
      +currentControl
      +'</section>';

  box.querySelector('#result-save').onclick=()=>action(async()=>{
   await api('/member/results/'+id+(state.saved?'/unsave':'/save'),{});
   await renderResultManagement(id);
  });

  const currentButton=box.querySelector('#result-current');
  if(currentButton){
   currentButton.onclick=()=>action(async()=>{
    await api(
        state.current
            ?'/member/results/current/clear'
            :'/member/results/'+id+'/current',
        {}
    );
    await renderResultManagement(id);
   });
  }

 }catch(e){
  if(e.status===401)throw e;
  box.innerHTML='<p>결과 관리 상태를 불러오지 못했습니다. 잠시 후 다시 열어주세요.</p>';
 }
}


async function loadSession(id){
 guest=false;guestResult=null;
 // 먼저 읽은 상태로 화면을 바꾸지 않고 답변과 revision의 원자적 스냅샷을 사용한다.
 const [snapshot,list]=await Promise.all([api('/sessions/'+id+'/answers'),api('/sessions/'+id+'/questions')]);
 if(snapshot.session.mode!=='SIMPLE')throw new Error('SIMPLE_SESSION_REQUIRED');
 session=snapshot.session;answers=snapshot.answers;questions=list.questions;
 history.replaceState(null,'','#session='+encodeURIComponent(id));
 if(session.status==='SUBMITTED'){await result();return;}
 const first=questions.findIndex(q=>!valid(q.questionPayload,answers[q.questionId]));
 step=first<0?questions.length:first;render();
}
function imageMarkup(images,alt){
 if(!images?.length)return '';
 return '<div class="pictures '+(images.length===1?'single':'')+'">'+images.map((url,i)=>'<img loading="lazy" src="'+esc(safeUrl(url))+'" alt="'+esc(alt)+(images.length>1?' · '+(i+1):'')+'">').join('')+'</div>';
}
function optionMarkup(option,q,groupId){
 const answer=answers[q.questionId],p=q.questionPayload;
 const group=groupId?p.comparisons.find(g=>g.id===groupId):null;
 const ranked=group&&isRanked(group), groupSelection=groupId?selectionMap(answer)[groupId]:null;
 const rank=ranked&&Array.isArray(groupSelection)?groupSelection.indexOf(option.id)+1:0;
 const selected=groupId?(ranked?rank>0:groupSelection===option.id):p.kind==='MULTIPLE_CHOICE'?answer?.selectedOptionIds?.includes(option.id):answer?.selectedOptionId===option.id;
 const multi=p.kind==='MULTIPLE_CHOICE'||ranked;
 const images=option.images||[];
 const kind=ranked?'ranked':multi?'multiple':'single';
 const badge=ranked?'<span class="rank-badge '+(rank?'':'rank-empty')+'">'+(rank?rank+'위':'미선택')+'</span>':'';
 return '<div class="choice choice-'+kind+' '+(selected?'selected':'')+'"><label>'+imageMarkup(images,option.label)+'<span class="label"><input type="'+(multi?'checkbox':'radio')+'" name="'+esc(groupId||q.questionId)+'" value="'+esc(option.id)+'" data-group="'+esc(groupId||'')+'" '+(selected?'checked':'')+'><span class="choice-copy">'+badge+'<span class="choice-name">'+esc(option.label)+'</span></span></span></label>'+(images.length?'<button class="zoom-button" type="button" data-zoom="'+esc(safeUrl(images[0]))+'">크게 보기 ↗</button>':'')+'</div>';
}
function overviewMarkup(group,q){
 const urls=[...new Set(group.overviewImages||[])];
 const desktop=urls.find(u=>u.includes('_desktop')),mobile=urls.find(u=>u.includes('_mobile'));
 if(session.apparelScope==='womens'&&['Q08','Q09'].includes(q.questionId)&&urls.length===2&&desktop&&mobile){
  // 두 자산은 같은 비교의 화면별 대안이다. 긴 합본은 펼쳐 보고 응답 카드는 그대로 유지한다.
  return '<details class="overview overview-responsive"><summary>비교 참고 이미지 펼치기</summary><p class="muted">아래 개별 선택지에서 답해주세요.</p><picture><source media="(max-width:640px)" srcset="'+esc(safeUrl(mobile))+'"><img src="'+esc(safeUrl(desktop))+'" alt="'+esc(group.label)+' 비교 이미지" loading="lazy"></picture><button type="button" class="zoom-button" data-zoom="'+esc(safeUrl(desktop))+'">가로 비교 크게 보기 ↗</button></details>';
 }
 return urls.map(url=>'<div class="overview"><p class="muted">비교 참고 이미지 · 아래 개별 선택지에서 답해주세요.</p><img src="'+esc(safeUrl(url))+'" alt="'+esc(group.label)+' 비교 이미지"><button class="zoom-button" data-zoom="'+esc(safeUrl(url))+'">전체 크게 보기 ↗</button></div>').join('');
}
function render(){
 if(step===questions.length){review();return;}
 const q=questions[step],p=q.questionPayload,comparison=p.kind==='COMPARISON';
 const rule=comparison?(p.comparisons.some(isRanked)?'각 비교의 선택 안내를 따라주세요':'각 비교에서 하나씩 선택'):p.kind==='MULTIPLE_CHOICE'?'최대 '+p.maxSelections+'개 선택':'하나만 선택';
 let body='';
 if(comparison){
  for(const g of activeGroups(p,selectionMap(answers[q.questionId]))){
   body+='<section class="comparison"><p class="eyebrow">'+esc(g.id)+'</p><h2>'+esc(g.label)+'</h2>';
   if(isRanked(g)) body+='<p class="muted">좋아하는 순서대로 선택해주세요. 선택한 항목을 다시 누르면 해제돼요.<br>선택하지 않은 항목은 이 비교 안에서 선호하지 않는 항목으로 기록돼요.</p><p class="rule">'+(Number.isInteger(g.minSelections)?'최소 '+g.minSelections+'개 선택':'선택 기준 준비 중')+'</p>'+(g.minSelections===0?'<button class="secondary" data-empty-ranked="'+esc(g.id)+'">이 비교에서 선호하는 항목 없음</button>':'');
   body+=overviewMarkup(g,q);
   body+='<div class="choices '+(g.options.some(o=>o.images?.length)?'':'text')+'">'+g.options.map(o=>optionMarkup(o,q,g.id)).join('')+'</div></section>';
  }
 }else body='<div class="choices '+(p.options.some(o=>o.images?.length)?'':'text')+'">'+p.options.map(o=>optionMarkup(o,q)).join('')+'</div>';
 main.innerHTML='<article class="question-letter" aria-labelledby="question-title"><div class="letter-masthead" aria-hidden="true"><span>TasteDNA</span><span>A LETTER TO YOUR TASTE</span></div><div class="progress-meta"><span>SIMPLE · '+(session.apparelScope==='mens'?'남성복':'여성복')+'</span><span>'+String(step+1).padStart(2,'0')+' / 10</span></div><div class="progress"><div style="width:'+((step+1)*10)+'%"></div></div><div class="question-head"><span class="letter-number" aria-hidden="true">'+String(step+1).padStart(2,'0')+'</span><p class="eyebrow">'+esc(titles[step])+'</p><h1 id="question-title">'+esc(p.prompt)+'</h1><p class="muted">'+esc(p.description)+'</p><span class="rule">'+rule+'</span></div>'+body+'<nav class="navigation" aria-label="질문 이동"><button class="secondary" id="back" '+(step===0?'disabled':'')+'>← 이전</button><span class="save-status">다음을 누르면 답변이 저장돼요</span><button class="primary" id="next">'+(step===9?'선택 돌아보기':'다음 질문')+' →</button></nav></article>';
 main.querySelectorAll('input').forEach(input=>input.onchange=()=>{
  const previousY=window.scrollY;
  const previous=answers[q.questionId];
  const next=choose(p,previous,input.value,input.dataset.group||null);
  if(next===previous&&p.kind==='MULTIPLE_CHOICE')toast('최대 '+p.maxSelections+'개까지 선택할 수 있어요.');
  answers[q.questionId]=next;render();window.scrollTo(0,previousY);
  const target=Array.from(main.querySelectorAll('input')).find(el=>el.value===input.value&&el.dataset.group===input.dataset.group);target?.focus({preventScroll:true});
 });
 main.querySelectorAll('[data-empty-ranked]').forEach(button=>button.onclick=()=>{
  answers[q.questionId]=choose(p,answers[q.questionId],null,button.dataset.emptyRanked);render();
 });
 document.querySelector('#back').onclick=()=>{if(step>0){step--;render();focusMain();}};
 document.querySelector('#next').onclick=()=>action(async()=>{
  if(!valid(p,answers[q.questionId]))return;
  if(!guest)session=await api('/sessions/'+session.sessionId+'/answers',{questionId:q.questionId,expectedRevision:session.revision,answerPayload:answers[q.questionId]});
  step++;render();focusMain();
 });
 bindZoom();updateNext();
}
function updateNext(){
 const next=document.querySelector('#next');if(next)next.disabled=busy||!valid(questions[step].questionPayload,answers[questions[step].questionId]);
 const back=document.querySelector('#back');if(back)back.disabled=busy||step===0;
}
function answerLabels(q){
 const p=q.questionPayload,a=answers[q.questionId];
 if(p.kind==='COMPARISON'){
  const selected=selectionMap(a);
  return activeGroups(p,selected).map(g=>{
   if(!isRanked(g))return g.options.find(o=>o.id===selected[g.id])?.label;
   const ids=selected[g.id];
   if(!Array.isArray(ids))return null;
   const ranked=ids.map((id,i)=>(i+1)+'위 '+g.options.find(o=>o.id===id)?.label).join(' · ');
   const notPreferred=g.options.filter(o=>!ids.includes(o.id)).map(o=>o.label).join(' · ');
   return ranked+' / 이 비교에서 비선호: '+(notPreferred||'없음');
  }).filter(Boolean).join(' / ');
 }
 const ids=a?.selectedOptionIds||[a?.selectedOptionId];return p.options.filter(o=>ids.includes(o.id)).map(o=>o.label).join(' · ');
}
function review(){
 main.innerHTML='<section class="review"><p class="eyebrow">BEFORE YOUR RESULT</p><h1>마음이 간 선택들,<br>한 번 돌아볼까요?</h1><p class="muted">제출하면 이번 취향 기록이 완성돼요. 수정하고 싶은 질문을 눌러주세요.</p>'+questions.map((q,i)=>'<div class="review-row"><div><span>'+esc(q.questionId)+'</span><p>'+esc(answerLabels(q)||'선택 필요')+'</p></div><button class="secondary" data-edit="'+i+'">수정</button></div>').join('')+'<div class="navigation"><button class="secondary" id="review-back">← 질문으로</button><button class="primary" id="submit">나의 TasteDNA 보기 ↗</button></div></section>';
 main.querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>{step=Number(b.dataset.edit);render();focusMain();});
 document.querySelector('#review-back').onclick=()=>{step=9;render();focusMain();};
 document.querySelector('#submit').onclick=()=>action(async()=>{
  if(!questions.every(q=>valid(q.questionPayload,answers[q.questionId])))throw new Error('SIMPLE_ANSWERS_INCOMPLETE');
  if(guest){guestResult=await api('/guest/simple/submit',{apparelScope:selectedScope,answers});session=guestResult.summary.session;}
  else session=await api('/sessions/'+session.sessionId+'/submit',{expectedRevision:session.revision});
  await result();focusMain();
 });
}
function evidenceCard(e,label){
 return '<article class="result-card"><p class="source">'+esc(e.questionId)+(e.comparisonId?' · '+esc(e.comparisonId):'')+'</p><h3>'+esc(label||e.prompt)+'</h3>'+e.selections.map(s=>imageMarkup(s.images,s.label)+'<p>'+esc((s.rank?s.rank+'위 · ':'')+s.label)+'</p>').join('')+(e.notPreferred?.length?'<p class="muted report-not-preferred">이 비교에서 선호하지 않은 항목: '+esc(e.notPreferred.map(s=>s.label).join(' / '))+'</p>':'')+(e.alternatives?.length?'<p class="muted report-alternatives"><small>비교한 선택지: '+esc(e.alternatives.map(s=>s.label).join(' / '))+'<br>선택하지 않은 쪽이 싫다는 뜻은 아니에요.</small></p>':'')+'</article>';
}
async function result(){
 const resultSessionId=session.sessionId;
 const data=guest?guestResult.summary:await api('/sessions/'+resultSessionId+'/preference-summary');
 if(data.session?.status!=='SUBMITTED')throw Object.assign(new Error('SESSION_NOT_SUBMITTED'),{code:'SESSION_NOT_SUBMITTED'});
 let representative=null,initialResult=null;
 try{
  const initial=guest?guestResult.initial:await api('/sessions/'+resultSessionId+'/initial-candidates');
  if(initial.sessionId===resultSessionId&&initial.definitionId===data.session.definitionId&&initial.revision===data.session.revision)
   {representative=initial.simpleRepresentative;initialResult=initial;}
 }catch(e){
  // 인증 만료는 공통 로그인 안내로 전달하고, 일반 조회 실패만 리포트에서 격리한다.
  if(e.status===401)throw e;
 }
 const representativeLabel=representative?.sources?.[0]?.optionLabel;
 const comprehensive=finalCandidate(initialResult);
 const directLabel=representative?.candidateName?representative.candidateId+' · '+representative.candidateName:representativeLabel;
 const evidence=data.evidence,byQ=id=>evidence.find(e=>e.questionId===id);
 const scope=data.session.apparelScope||session.apparelScope;
 const scopeLabel=scope==='womens'?'여성복':scope==='mens'?'남성복':'';
 const textCards=[['Q01','마음이 가는 색'],['Q02','마음이 가는 스타일'],['Q03','좋아하는 실루엣'],['Q04','눈길이 가는 패턴']];
 main.innerHTML='<div class="taste-report"><section class="result-heading"><p class="eyebrow">FORM · TasteDNA</p><h1 class="report-edition">나의 TasteDNA 리포트</h1><p class="report-scope">'+esc(scopeLabel)+' · Simple</p>'+(comprehensive?finalMarkup(initialResult,'h2')+'<p class="report-status">직접 선택한 스타일 · '+esc(directLabel||(data.evidence.find(e=>e.questionId==='Q02')?.selections||[]).map(s=>s.label).join(' · ')||'직접 선택 근거 없음')+'</p>':'<span class="tag">'+(representativeLabel?'Simple 대표 스타일':'나의 직접 선호 기록')+'</span><h2>'+esc(representativeLabel||data.title)+'</h2><p class="muted">'+esc(data.description)+'</p><p class="report-status">직접 선택한 취향 요약 · 종합 유형은 아직 확정하지 않습니다.</p>')+'</section><div id="report-management"></div><div id="report-products"></div><section class="report-panel" aria-labelledby="direct-preferences"><p class="eyebrow">YOUR PERSONAL SELECTIONS</p><h2 id="direct-preferences">직접 고른 선호 항목</h2><div class="result-grid report-keywords">'+textCards.filter(([id])=>byQ(id)).map(([id,label])=>'<article class="result-card"><p class="source">'+id+'</p><h3>'+label+'</h3><div class="chips">'+(byQ(id)?.selections||[]).map(s=>'<span class="chip">'+esc(s.label)+'</span>').join('')+'</div></article>').join('')+'</div>'+(byQ('Q07')?'<div class="report-boundaries"><h3>명시적으로 회피한 요소</h3><p>'+esc(byQ('Q07').meaning==='NO_EXPLICIT_AVOIDANCE'?'회피 대상 없음':byQ('Q07').selections.map(s=>s.label).join(' · '))+'</p></div>':'')+(byQ('Q02')?.selections.length?'<p class="report-direction">탐색 방향 · 직접 선택한 '+esc(byQ('Q02').selections.map(s=>s.label).join(' · '))+' 스타일이 등록된 전시 상품을 살펴보세요.</p>':'')+'</section><details class="report-history"><summary>내 상세 선택 기록 <span>질문별 이미지와 비교 순위 보기</span></summary><div class="report-history-body">'+textCards.filter(([id])=>byQ(id)).map(([id,label])=>evidenceCard(byQ(id),label)).join('')+'<section class="result-section"><p class="eyebrow">THE LOOKS YOU CHOSE</p><h2>당신의 눈길이 머문 착장</h2><div class="result-grid">'+[['Q05','첫눈에 끌린 착장'],['Q06','내 옷으로 고른 옷'],['Q10','마지막에 선택한 착장']].filter(([id])=>byQ(id)).map(([id,label])=>evidenceCard(byQ(id),label)).join('')+'</div></section><section class="result-section"><p class="eyebrow">SMALL DETAILS, PERSONAL CHOICES</p><h2>디테일과 조합에서 발견한 취향</h2><div class="evidence-list">'+evidence.filter(e=>['Q08','Q09'].includes(e.questionId)).map(e=>evidenceCard(e)).join('')+'</div></section><section class="result-section avoidance"><p class="eyebrow">YOUR OWN BOUNDARIES</p><h2>내 옷으로는 피하고 싶은 것</h2>'+(byQ('Q07')?(byQ('Q07').meaning==='NO_EXPLICIT_AVOIDANCE'?'<p>이번 선택에서는 딱히 피하고 싶은 착장이 없었어요.</p>':'<p class="muted">직접 피하고 싶다고 고른 착장만 담았어요.</p><div class="result-grid">'+byQ('Q07').selections.map(s=>evidenceCard({...byQ('Q07'),selections:[s]},'회피한 착장')).join('')+'</div>'):'')+'</section></div></details><div class="result-note"><p>이 결과는 응답에 기반한 패션 선호 기록입니다. '+(comprehensive?'성격이나 심리 상태를 진단하지 않으며, 종합 RESULT는 이번 응답의 제한적인 취향 경향입니다.':'성격이나 심리 상태를 진단하지 않으며, 종합 패션 유형은 아직 확정하지 않습니다.')+' 첫인상과 마지막 선택이 달라도 서로 다른 순간의 선호로 함께 남겨요.</p><details><summary>기록 정보</summary><p>정의 '+esc(data.session.definitionId)+'<br>답변 버전 '+data.session.revision+' · 제출 완료</p></details></div><div class="result-actions"><button class="secondary" id="download">내 취향 기록 내려받기</button><button class="primary" id="restart">새로운 취향 기록 시작 →</button></div></div>';
 const products=document.createElement('section');products.className='matched-products';products.setAttribute('aria-live','polite');products.innerHTML='<p class="muted">연결된 FORM 전시 상품을 확인하고 있어요.</p>';main.querySelector('#report-products').append(products);
 await renderResultManagement(resultSessionId);
 const bridge=document.createElement('section');bridge.className='result-shop';bridge.innerHTML='<div><p class="eyebrow">BACK TO YOUR WARDROBE</p><p>발견한 취향을 떠올리며 컬렉션을 둘러보세요.</p></div><button class="secondary" id="result-shop">쇼핑몰로 돌아가기 ↗</button>';main.append(bridge);bridge.querySelector('button').onclick=shop;
 document.querySelector('#restart').onclick=selectApparel;
 document.querySelector('#download').hidden=guest;
 document.querySelector('#download').onclick=()=>{
  if(guest)return;
  const lines=['TasteDNA — 나의 취향 기록',...(comprehensive?['종합 RESULT · '+comprehensive.candidateId+' · '+comprehensive.candidateName,finalMeaning(comprehensive)]:[]),data.title,'',...evidence.map(e=>e.questionId+(e.comparisonId?' / '+e.comparisonId:'')+' · '+e.prompt+'\n'+e.selections.map(s=>(s.rank?s.rank+'위 ':'')+s.label).join(' / ')+(e.notPreferred?.length?'\n이 비교에서 선호하지 않은 항목: '+e.notPreferred.map(s=>s.label).join(' / '):'')),'',comprehensive?'종합 RESULT와 직접 선택 기록을 구분하며 심리 진단으로 사용하지 않습니다.':'종합 유형이나 심리 진단이 아닌 직접 선택한 취향 기록입니다.'];
  const url=URL.createObjectURL(new Blob([lines.join('\n\n')],{type:'text/plain;charset=utf-8'}));
  const a=document.createElement('a');a.href=url;a.download='TasteDNA.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 };
 bindZoom();
 // 리포트를 먼저 표시하고 상품 조회 실패는 이 영역 안에서만 안내한다.
 const heading='<p class="eyebrow">FORM · DEMO COLLECTION</p><h2>내가 선택한 스타일과 연결된 FORM 전시 상품</h2><p class="muted">Q02에서 직접 선택한 스타일과 상품에 등록된 스타일 속성이 일치하는 전시 상품입니다. 상품명과 가격은 시연용 예시입니다.</p>';
 try{
  const matched=guest?guestResult.products:await api('/sessions/'+resultSessionId+'/matched-demo-products');
  // 전체 응답은 유지하고 화면에는 네 개씩 공개한다. 매칭 순서나 근거는 바꾸지 않는다.
  let visible=4;
  const renderProducts=()=>{
   products.innerHTML=heading+(matched.products.length?'<div class="matched-product-grid">'+matched.products.slice(0,visible).map(p=>'<article class="matched-product-card"><img src="'+esc(safeUrl(p.imageUrl))+'" alt="'+esc(p.name)+'" loading="lazy"><div><span class="tag">FORM 전시 상품</span><h3>'+esc(p.name)+'</h3><p class="matched-product-price">'+esc(Number(p.price).toLocaleString('ko-KR'))+'원</p><p class="source">선택 근거: '+p.evidence.map(e=>esc(e.questionId)+' · '+esc(e.optionId)+(e.styleLabel?' · '+esc(e.styleLabel):'')+(e.styleCode?' ('+esc(e.styleCode)+')':'')+(e.colorLabel?' · 색상 '+esc(e.colorLabel):'')+(e.colorCode?' ('+esc(e.colorCode)+')':'')).join(' / ')+'</p></div></article>').join('')+'</div>':'<p class="matched-product-state">이번 선택과 연결된 FORM 전시 상품이 없습니다.</p>');
   if(visible<matched.products.length){
    products.innerHTML+='<button type="button" class="secondary" id="more-products">더 보기 · '+Math.min(visible,matched.products.length)+' / '+matched.products.length+'</button>';
    products.querySelector('#more-products').onclick=()=>{visible+=4;renderProducts();};
   }
  };
  renderProducts();
 }catch(e){
  if(e.status===401)throw e;
  products.innerHTML=heading+'<p class="matched-product-state">전시 상품을 불러오지 못했어요. 직접 선호 기록은 위에서 확인할 수 있습니다.</p>';
 }
}
function bindZoom(){
 main.querySelectorAll('[data-zoom]').forEach(b=>b.onclick=()=>{dialog.querySelector('img').src=b.dataset.zoom;dialog.querySelector('img').alt='선택지 원본 크게 보기';dialog.showModal();});
 main.querySelectorAll('img').forEach(img=>img.onerror=()=>{img.alt='이미지를 불러오지 못했습니다. 새로고침 후 다시 확인해주세요.';img.classList.add('image-error');});
}
dialog.querySelector('button').onclick=()=>dialog.close();
dialog.onclick=e=>{if(e.target===dialog)dialog.close();};
function shop(){ history.replaceState(null,'',location.pathname); storefront(main,()=>{home();focusMain();}); focusMain(); }
document.querySelector('#nav-shop').onclick=e=>{e.preventDefault();if(!busy)shop();};
document.querySelector('#nav-taste').onclick=e=>{e.preventDefault();if(!busy){home();focusMain();}};
// 같은 문서의 저장 링크로 이동해도 인증 확인과 세션 복원을 거친다.
window.onhashchange=()=>{
 if(!busy&&location.hash==='#mypage')openMyPage();
 else if(!busy&&new URLSearchParams(location.hash.slice(1)).get('session'))login();
};
async function initialize(){
 loading('FORM을 불러오고 있어요.');
 try{
  authMode=await client.init();
  if(authMode==='member'){
   try{member=await api('/auth/me');}catch(e){if(e.status!==401)throw e;}
  }
  updateMemberNav();
  if(location.hash==='#mypage')openMyPage();else if(new URLSearchParams(location.hash.slice(1)).get('session'))login();else if(location.hash==='#taste')home();else shop();
 }catch{
  main.innerHTML='<section class="error-panel"><p>FORM에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.</p><button id="retry-init" class="secondary">다시 연결</button></section>';
  main.querySelector('#retry-init').onclick=initialize;
 }
}
initialize();

