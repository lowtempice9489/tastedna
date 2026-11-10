// 임시 쇼핑몰 UI는 TasteDNA와 분리한다. 실제 도입처에서는 진입 링크와 API만 연결하면 된다.
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const image=url=>url?.startsWith('/assets/gender/')?encodeURI(url):'';
export async function storefront(main, startTaste) {
 main.innerHTML=`<div class="form-home">
 <section class="commerce-promotions" aria-label="FORM 스타일 에디트">
  <div class="commerce-hero">
   <div class="commerce-copy"><p class="eyebrow">FORM STYLE EDIT / 01</p><h1>가벼워지는 계절,<br>새로운 스타일.</h1><p>일상에 편안함과 세련됨을 더하는<br>FORM의 스타일 에디트를 만나보세요.</p><a class="primary" href="#collection">컬렉션 둘러보기 <span aria-hidden="true">↗</span></a><small>EVERYDAY, IN YOUR OWN WAY.</small></div>
   <img class="commerce-model" src="/assets/gender/TasteDNA_FORM_Main/form-hero-main.png" alt="재킷을 입은 남성과 니트를 입은 여성의 봄 스타일">
  </div>
  <div class="commerce-side">
   <a class="edit-card" href="#collection" data-edit-category="재킷"><div><p class="eyebrow">THE OUTER EDIT</p><h2>일상을 바꾸는<br>아우터 한 벌</h2><span>재킷 살펴보기 ↗</span></div><img src="/assets/gender/TasteDNA_FORM_Main/form-hero-outer.png" alt="남성 데님 아우터 착장"></a>
   <a class="edit-card" href="#collection" data-edit-category="텍스처"><div><p class="eyebrow">TOUCH & TEXTURE</p><h2>눈길이 머무는<br>소재의 표정</h2><span>텍스처 살펴보기 ↗</span></div><img src="/assets/gender/TasteDNA_FORM_Main/form-hero-texture.png" alt="여성 아이보리 니트 착장"></a>
  </div>
 </section>
 <div class="commerce-note"><span>FORM / EXHIBITION COLLECTION</span><p>좋아하는 옷을 고르는 일에서, 나를 알아가는 일까지.</p><span>전시용 데모 스토어</span></div>
 <section class="taste-banner" aria-labelledby="taste-banner-title">
  <div><p class="eyebrow">FORM의 특별한 취향 기록</p><h2 id="taste-banner-title">TasteDNA</h2><h3>나의 취향에서 시작하는, 조금 더 나다운 선택.</h3><p>마음이 가는 색과 스타일을 고르고, 직접 선택한 취향을 발견해보세요.</p></div>
  <div class="banner-motif" aria-hidden="true"><span>Style</span><i></i><i></i><i></i><span>Taste</span></div>
  <div class="banner-action"><button class="primary" id="taste-entry">TasteDNA 알아보기 <span aria-hidden="true">↗</span></button><small>10개의 질문 · 나의 직접 선호 기록</small></div>
 </section>
 <section id="collection" aria-labelledby="collection-title"><div class="collection-head"><div><p class="eyebrow">THE FORM COLLECTION</p><h2 id="collection-title">지금, 눈여겨볼 스타일</h2></div><span class="muted">전시용 샘플 · 실제 판매 상품이 아닙니다</span></div><div id="product-filters" class="filters"></div><div id="products" class="product-grid"><p>상품을 불러오고 있어요.</p></div></section>
 </div>`;
 main.querySelector('#taste-entry').onclick=startTaste;
 try{
  const response=await fetch('/api/v1/demo/products',{cache:'no-store'});
  if(!response.ok)throw Error();
  const products=await response.json();
  // 응답이 늦게 도착한 사이 다른 화면으로 이동했으면 현재 화면을 변경하지 않는다.
  if(!main.querySelector('#products'))return;
  const categories=['전체',...new Set(products.map(p=>p.category))];
  const render=category=>{
   main.querySelector('#product-filters').innerHTML=categories.map(c=>'<button class="filter '+(c===category?'active':'')+'" aria-pressed="'+(c===category)+'" data-category="'+esc(c)+'">'+esc(c)+'</button>').join('');
   main.querySelector('#products').innerHTML=products.filter(p=>category==='전체'||p.category===category).map(p=>'<button class="product" data-product="'+esc(p.id)+'"><div class="product-image"><img loading="lazy" src="'+esc(image(p.imageUrl))+'" alt="'+esc(p.name)+'"><span>VIEW LOOK ↗</span></div><div class="product-info"><small>'+esc(p.category)+'</small><h3>'+esc(p.name)+'</h3><p>'+Number(p.price).toLocaleString('ko-KR')+'원 <small>예시 가격</small></p></div></button>').join('')||'<p>등록된 전시 상품이 없습니다.</p>';
   main.querySelectorAll('[data-category]').forEach(b=>b.onclick=()=>render(b.dataset.category));
   main.querySelectorAll('[data-product]').forEach(b=>b.onclick=()=>detail(products.find(p=>p.id===b.dataset.product),startTaste));
  };
  main.querySelectorAll('[data-edit-category]').forEach(link=>link.onclick=()=>render(categories.includes(link.dataset.editCategory)?link.dataset.editCategory:'전체'));
  render('전체');
 }catch{
  const target=main.querySelector('#products');
  if(target)target.innerHTML='<div class="error-panel"><p>전시 상품을 불러오지 못했습니다. 서버와 DB 연결을 확인해주세요.</p><button class="secondary" id="retry-products">다시 불러오기</button></div>';
  main.querySelector('#retry-products')?.addEventListener('click',()=>storefront(main,startTaste));
 }
}
function detail(product,startTaste){
 const modal=document.createElement('dialog');modal.className='product-detail';
 modal.innerHTML='<button class="close" aria-label="상품 상세 닫기">✕</button><div class="detail-grid"><img src="'+esc(image(product.imageUrl))+'" alt="'+esc(product.name)+'"><div><p class="eyebrow">FORM / EXHIBITION ITEM</p><h2>'+esc(product.name)+'</h2><p>'+Number(product.price).toLocaleString('ko-KR')+'원 <small>예시 가격</small></p><p class="muted">'+esc(product.description)+'</p><p class="demo-note">TasteDNA의 쇼핑몰 연동을 보여주는 전시용 상품입니다. 주문·결제는 제공하지 않습니다.</p><button class="primary" id="detail-taste">이런 옷에 끌리는 나, 알아보기 ↗</button></div></div>';
 document.body.append(modal);modal.showModal();
 modal.querySelector('.close').onclick=()=>modal.close();
 modal.onclick=e=>{if(e.target===modal)modal.close();};
 modal.onclose=()=>modal.remove();
 modal.querySelector('#detail-taste').onclick=()=>{modal.close();startTaste();};
}

