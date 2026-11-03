// 임시 쇼핑몰 UI는 TasteDNA와 분리한다. 실제 도입처에서는 진입 링크와 API만 연결하면 된다.
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const image=url=>url?.startsWith('/assets/gender/')?encodeURI(url):'';
export async function storefront(main, startTaste) {
 main.innerHTML='<section class="shop-hero"><div><p class="eyebrow">FORM / DEMO STORE · ISSUE 01</p><h1>입는 것에서<br>나를 발견하는 일.</h1><p class="muted">오늘의 옷을 고르기 전에,<br>내가 좋아하는 것부터 알아보세요.</p><a class="primary" href="#collection">컬렉션 둘러보기 ↓</a></div><div class="shop-cover"><img src="/assets/gender/male/Q10/Q10_C.png" alt="니트 가디건과 여유로운 팬츠 착장"><span>Everyday, in your own way.</span></div></section><section class="taste-banner"><div><p class="eyebrow">A LITTLE MORE PERSONAL</p><h2>나에게 어울리는 옷보다,<br>내가 끌리는 옷을 찾는 시간.</h2><p>TasteDNA · 10개의 질문으로 발견하는 나의 패션 취향</p></div><button class="primary" id="taste-entry">내 TasteDNA 알아보기 ↗</button></section><section id="collection"><div class="collection-head"><div><p class="eyebrow">THE EDIT</p><h2>취향을 탐색하는 컬렉션</h2></div><span class="muted">전시용 샘플 · 실제 판매 상품이 아닙니다</span></div><div id="product-filters" class="filters"></div><div id="products" class="product-grid"><p>상품을 불러오고 있어요.</p></div></section>';
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

