import {storefront} from './shop.mjs';
import {activeGroups, selectionMap, choose, valid} from './assessment-state.mjs';

const main=document.querySelector('main'), notice=document.querySelector('#notice'), dialog=document.querySelector('#zoom');
let authorization='', session=null, questions=[], answers={}, step=0, busy=false, toastTimer;
const titles=['COLOR','STYLE','SILHOUETTE','PATTERN','FIRST IMPRESSION','YOUR WARDROBE','NOT FOR ME','THE DETAILS','STYLING','FINAL LOOK'];
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const asset=p=>'/assets/gender/male/'+p;
const safeUrl=url=>url?.startsWith('/assets/gender/') ? encodeURI(url) : '';
function toast(message){notice.textContent=message;clearTimeout(toastTimer);toastTimer=setTimeout(()=>notice.textContent='',6500);}
function focusMain(){main.focus({preventScroll:true});window.scrollTo({top:0,behavior:'instant'});}
function loading(message){main.innerHTML='<div class="loading"><div class="spinner"></div><p>'+esc(message)+'</p></div>';}
async function api(path, body){
 const response=await fetch('/api/v1'+path,{method:body===undefined?'GET':'POST',headers:{Authorization:authorization,'X-Requested-With':'XMLHttpRequest',...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store'});
 if(response.status===204)return null;
 const data=await response.json().catch(()=>({}));
 if(!response.ok){const error=new Error(data.code||'HTTP_'+response.status);error.status=response.status;throw error;}
 return data;
}
const errors={
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
  if(e.message==='SESSION_REVISION_CONFLICT'||e.message==='SESSION_NOT_EDITABLE'||e.message==='SESSION_NOT_SUBMITTABLE'){
   try{await loadSession(session.sessionId);}catch{}
  }
  toast(errors[e.message]||'요청을 완료하지 못했어요. 연결 상태를 확인하고 다시 시도해주세요.');
 }
 finally{busy=false;document.querySelectorAll('button').forEach(b=>b.disabled=false);updateNext();}
}
function home(){
 history.replaceState(null,'','#taste');
 main.innerHTML='<section class="hero"><div class="hero-copy"><p class="eyebrow">DISCOVER YOUR TASTE · SIMPLE TEST</p><h1>내가 끌리는 것들,<br>그 안에 있는<br><em>나의 취향.</em></h1><p class="muted">설명하기 어려웠던 나의 스타일.<br>열 가지 질문과 눈길이 가는 착장으로<br>당신만의 취향을 발견해보세요.</p><div class="hero-meta"><span>10개의 질문</span><span>텍스트 + 이미지</span><span>남성 · 여성</span></div><button class="primary" id="begin">내 취향 알아보기 <span>↗</span></button></div><div class="hero-gallery"><img src="'+asset('Q10/Q10_A.png')+'" alt="편안한 니트와 팬츠 착장"><img src="'+asset('Q10/J.png')+'" alt="재킷을 활용한 착장"><span class="gallery-tag">A little more you.</span></div></section><section class="intro-note"><span>01 &nbsp; 평소 입는 옷보다, 실제로 마음이 가는 옷을 골라요.</span><span>02 &nbsp; 선택한 취향을 근거와 함께 확인해요.</span><span>03 &nbsp; 제출 전에는 언제든 답을 바꿀 수 있어요.</span></section>';
 document.querySelector('#begin').onclick=selectApparel;
}
function selectApparel(){
 history.replaceState(null,'','#taste');
 main.innerHTML='<section class="login"><p class="eyebrow">CHOOSE YOUR WARDROBE</p><h1>어떤 옷의 취향을<br>알아볼까요?</h1><p class="muted">살펴보고 싶은 의류를 선택해주세요.</p><button class="primary" id="choose-mens">남성</button><button class="secondary" id="choose-womens">여성 · 준비 중</button><small>남성복은 지금 테스트할 수 있어요. 여성복은 질문과 선택지를 준비하고 있어요.</small><button class="secondary" id="scope-back">← 소개로</button></section>';
 document.querySelector('#choose-mens').onclick=login;
 document.querySelector('#choose-womens').onclick=womenComingSoon;
 document.querySelector('#scope-back').onclick=()=>{home();focusMain();};
 focusMain();
}
function womenComingSoon(){
 main.innerHTML='<section class="login"><p class="eyebrow">WOMENS · COMING SOON</p><h1>여성복 취향 테스트를<br>준비하고 있어요.</h1><p class="muted">여성복에 맞는 질문과 더 다양한 이미지 선택지를 정리하고 있어요. 준비가 끝나면 여기서 시작할 수 있어요.</p><button class="primary" id="scope-back">← 남성 · 여성 다시 선택</button></section>';
 document.querySelector('#scope-back').onclick=selectApparel;
 focusMain();
}
function login(){
 main.innerHTML='<form class="login"><p class="eyebrow">YOUR PERSONAL STUDY</p><h2>취향의 기록을<br>시작해볼까요?</h2><p class="muted">계정으로 연결하면 저장된 질문부터 이어갈 수 있어요.</p><label for="member">개발용 회원 ID</label><input id="member" autocomplete="username" value="dev-image-demo" required><label for="password">개발용 비밀번호</label><input id="password" type="password" autocomplete="current-password" required><button class="primary">연결하고 시작하기 →</button><small>현재는 로컬 체험 버전입니다. 서버에 설정한 개발용 계정으로 연결하세요. 여성복 문항은 준비 중입니다.</small></form>';
 document.querySelector('form').onsubmit=e=>{e.preventDefault();const member=document.querySelector('#member').value.trim(),password=document.querySelector('#password').value;
 authorization='Basic '+btoa(Array.from(new TextEncoder().encode(member+':'+password),b=>String.fromCharCode(b)).join(''));
 action(async()=>{
  const id=new URLSearchParams(location.hash.slice(1)).get('session');
  if(id){await loadSession(id);return;}
  const active=await api('/sessions/active');
  if(active){await loadSession(active.sessionId);return;}
  session=await api('/sessions/simple-image',{});
  await loadSession(session.sessionId);
 });
 };focusMain();
}
async function loadSession(id){
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
 const selected=groupId?selectionMap(answer)[groupId]===option.id:p.kind==='MULTIPLE_CHOICE'?answer?.selectedOptionIds?.includes(option.id):answer?.selectedOptionId===option.id;
 const multi=p.kind==='MULTIPLE_CHOICE';
 const images=option.images||[];
 return '<div class="choice '+(selected?'selected':'')+'"><label>'+imageMarkup(images,option.label)+'<span class="label"><input type="'+(multi?'checkbox':'radio')+'" name="'+esc(groupId||q.questionId)+'" value="'+esc(option.id)+'" data-group="'+esc(groupId||'')+'" '+(selected?'checked':'')+'><span>'+esc(option.label)+'</span></span></label>'+(images.length?'<button class="zoom-button" type="button" data-zoom="'+esc(safeUrl(images[0]))+'">크게 보기 ↗</button>':'')+'</div>';
}
function render(){
 if(step===questions.length){review();return;}
 const q=questions[step],p=q.questionPayload,comparison=p.kind==='COMPARISON';
 const rule=comparison?'각 비교에서 하나씩 선택':p.kind==='MULTIPLE_CHOICE'?'최대 '+p.maxSelections+'개 선택':'하나만 선택';
 let body='';
 if(comparison){
  for(const g of activeGroups(p,selectionMap(answers[q.questionId]))){
   body+='<section class="comparison"><p class="eyebrow">'+esc(g.id)+'</p><h2>'+esc(g.label)+'</h2>';
   for(const url of g.overviewImages||[])body+='<div class="overview"><img src="'+esc(safeUrl(url))+'" alt="'+esc(g.label)+' 비교 이미지"><button class="zoom-button" data-zoom="'+esc(safeUrl(url))+'">전체 크게 보기 ↗</button></div>';
   body+='<div class="choices '+(g.options.some(o=>o.images?.length)?'':'text')+'">'+g.options.map(o=>optionMarkup(o,q,g.id)).join('')+'</div></section>';
  }
 }else body='<div class="choices '+(p.options.some(o=>o.images?.length)?'':'text')+'">'+p.options.map(o=>optionMarkup(o,q)).join('')+'</div>';
 main.innerHTML='<div class="progress-meta"><span>SIMPLE · '+(session.apparelScope==='mens'?'남성복':'여성복')+'</span><span>'+String(step+1).padStart(2,'0')+' / 10</span></div><div class="progress"><div style="width:'+((step+1)*10)+'%"></div></div><div class="question-head"><p class="eyebrow">'+esc(titles[step])+'</p><h1>'+esc(p.prompt)+'</h1><p class="muted">'+esc(p.description)+'</p><span class="rule">'+rule+'</span></div>'+body+'<nav class="navigation" aria-label="질문 이동"><button class="secondary" id="back" '+(step===0?'disabled':'')+'>← 이전</button><span class="save-status">다음을 누르면 답변이 저장돼요</span><button class="primary" id="next">'+(step===9?'선택 돌아보기':'다음 질문')+' →</button></nav>';
 main.querySelectorAll('input').forEach(input=>input.onchange=()=>{
  const previousY=window.scrollY;
  const previous=answers[q.questionId];
  const next=choose(p,previous,input.value,input.dataset.group||null);
  if(next===previous&&p.kind==='MULTIPLE_CHOICE')toast('최대 '+p.maxSelections+'개까지 선택할 수 있어요.');
  answers[q.questionId]=next;render();window.scrollTo(0,previousY);
  const target=Array.from(main.querySelectorAll('input')).find(el=>el.value===input.value&&el.dataset.group===input.dataset.group);target?.focus({preventScroll:true});
 });
 document.querySelector('#back').onclick=()=>{if(step>0){step--;render();focusMain();}};
 document.querySelector('#next').onclick=()=>action(async()=>{
  if(!valid(p,answers[q.questionId]))return;
  session=await api('/sessions/'+session.sessionId+'/answers',{questionId:q.questionId,expectedRevision:session.revision,answerPayload:answers[q.questionId]});
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
 if(p.kind==='COMPARISON'){const selected=selectionMap(a);return activeGroups(p,selected).map(g=>g.options.find(o=>o.id===selected[g.id])?.label).filter(Boolean).join(' / ');}
 const ids=a?.selectedOptionIds||[a?.selectedOptionId];return p.options.filter(o=>ids.includes(o.id)).map(o=>o.label).join(' · ');
}
function review(){
 main.innerHTML='<section class="review"><p class="eyebrow">BEFORE YOUR RESULT</p><h1>마음이 간 선택들,<br>한 번 돌아볼까요?</h1><p class="muted">제출하면 이번 취향 기록이 완성돼요. 수정하고 싶은 질문을 눌러주세요.</p>'+questions.map((q,i)=>'<div class="review-row"><div><span>'+esc(q.questionId)+'</span><p>'+esc(answerLabels(q)||'선택 필요')+'</p></div><button class="secondary" data-edit="'+i+'">수정</button></div>').join('')+'<div class="navigation"><button class="secondary" id="review-back">← 질문으로</button><button class="primary" id="submit">나의 TasteDNA 보기 ↗</button></div></section>';
 main.querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>{step=Number(b.dataset.edit);render();focusMain();});
 document.querySelector('#review-back').onclick=()=>{step=9;render();focusMain();};
 document.querySelector('#submit').onclick=()=>action(async()=>{
  if(!questions.every(q=>valid(q.questionPayload,answers[q.questionId])))throw new Error('SIMPLE_ANSWERS_INCOMPLETE');
  session=await api('/sessions/'+session.sessionId+'/submit',{expectedRevision:session.revision});
  await result();focusMain();
 });
}
function evidenceCard(e,label){
 return '<article class="result-card"><p class="source">'+esc(e.questionId)+(e.comparisonId?' · '+esc(e.comparisonId):'')+'</p><h3>'+esc(label||e.prompt)+'</h3>'+e.selections.map(s=>imageMarkup(s.images,s.label)+'<p>'+esc(s.label)+'</p>').join('')+(e.alternatives?.length?'<p class="muted"><small>비교한 선택지: '+esc(e.alternatives.map(s=>s.label).join(' / '))+'<br>선택하지 않은 쪽이 싫다는 뜻은 아니에요.</small></p>':'')+'</article>';
}
async function result(){
 const data=await api('/sessions/'+session.sessionId+'/preference-summary');
 const evidence=data.evidence,byQ=id=>evidence.find(e=>e.questionId===id);
 const textCards=[['Q01','마음이 가는 색'],['Q03','좋아하는 실루엣'],['Q04','눈길이 가는 패턴']];
 main.innerHTML='<section class="result-heading"><p class="eyebrow">YOUR TASTE, IN YOUR WORDS</p><span class="tag">나의 직접 선호 기록</span><h1>'+esc(data.title)+'</h1><p class="muted">'+esc(data.description)+'</p></section><section class="result-grid">'+textCards.map(([id,label])=>'<article class="result-card"><p class="source">'+id+'</p><h3>'+label+'</h3><div class="chips">'+(byQ(id)?.selections||[]).map(s=>'<span class="chip">'+esc(s.label)+'</span>').join('')+'</div></article>').join('')+'</section><section class="result-section"><p class="eyebrow">THE LOOKS YOU CHOSE</p><h2>당신의 눈길이 머문 착장</h2><div class="result-grid">'+[['Q05','첫눈에 끌린 착장'],['Q06','내 옷으로 고른 옷'],['Q10','마지막에 선택한 착장']].map(([id,label])=>evidenceCard(byQ(id),label)).join('')+'</div></section><section class="result-section"><p class="eyebrow">SMALL DETAILS, PERSONAL CHOICES</p><h2>디테일과 조합에서 발견한 취향</h2><div class="evidence-list">'+evidence.filter(e=>['Q08','Q09'].includes(e.questionId)).map(e=>evidenceCard(e)).join('')+'</div></section><section class="result-section avoidance"><p class="eyebrow">YOUR OWN BOUNDARIES</p><h2>내 옷으로는 피하고 싶은 것</h2>'+(byQ('Q07').meaning==='NO_EXPLICIT_AVOIDANCE'?'<p>이번 선택에서는 딱히 피하고 싶은 착장이 없었어요.</p>':'<p class="muted">직접 피하고 싶다고 고른 착장만 담았어요.</p><div class="result-grid">'+byQ('Q07').selections.map(s=>evidenceCard({...byQ('Q07'),selections:[s]},'회피한 착장')).join('')+'</div>')+'</section><div class="result-note"><p>이 결과는 응답에 기반한 패션 선호 기록입니다. 성격이나 심리 상태를 진단하지 않으며, 종합 패션 유형은 아직 확정하지 않습니다. 첫인상과 마지막 선택이 달라도 서로 다른 순간의 선호로 함께 남겨요.</p><details><summary>기록 정보</summary><p>정의 '+esc(data.session.definitionId)+'<br>답변 버전 '+data.session.revision+' · 제출 완료</p></details></div><div class="result-actions"><button class="secondary" id="download">내 취향 기록 내려받기</button><button class="primary" id="restart">새로운 취향 기록 시작 →</button></div>';
 const bridge=document.createElement('section');bridge.className='result-shop';bridge.innerHTML='<div><p class="eyebrow">BACK TO YOUR WARDROBE</p><p>발견한 취향을 떠올리며 컬렉션을 둘러보세요.</p></div><button class="secondary" id="result-shop">쇼핑몰로 돌아가기 ↗</button>';main.append(bridge);bridge.querySelector('button').onclick=shop;
 document.querySelector('#restart').onclick=selectApparel;
 document.querySelector('#download').onclick=()=>{
  const lines=['TasteDNA — 나의 직접 선호 기록',data.title,'',...evidence.map(e=>e.questionId+(e.comparisonId?' / '+e.comparisonId:'')+' · '+e.prompt+'\n'+e.selections.map(s=>s.label).join(' / ')),'','종합 유형이나 심리 진단이 아닌 직접 선택한 취향 기록입니다.'];
  const url=URL.createObjectURL(new Blob([lines.join('\n\n')],{type:'text/plain;charset=utf-8'}));
  const a=document.createElement('a');a.href=url;a.download='TasteDNA.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 };
 bindZoom();
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
if(new URLSearchParams(location.hash.slice(1)).get('session'))login();else if(location.hash==='#taste')home();else shop();

