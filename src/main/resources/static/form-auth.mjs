// 쿠키는 브라우저가 관리하고 CSRF 토큰과 개발용 인증은 메모리에만 둔다.
export function createClient(fetcher = fetch) {
 let mode, basic = '', csrf;
 async function send(path, body, retry = true) {
  if (!mode) throw new Error('AUTH_MODE_UNAVAILABLE');
  const write = body !== undefined;
  if (mode === 'member' && write && !csrf) await refreshCsrf();
  const headers = {'X-Requested-With':'XMLHttpRequest'};
  if (mode === 'local' && basic) headers.Authorization = basic;
  if (write) {
   headers['Content-Type'] = 'application/json';
   if (mode === 'member') headers[csrf.headerName] = csrf.token;
  }
  const response = await fetcher('/api/v1' + path, {method:write?'POST':'GET', headers,
   credentials:'same-origin', cache:'no-store', body:write?JSON.stringify(body):undefined});
  const data = response.status === 204 ? null : await response.json().catch(()=>({}));
  if (!response.ok) {
   // CSRF 필터가 거부한 요청만 한 번 재전송한다. 일반 실패나 저장 요청의 네트워크 오류는 재시도하지 않는다.
   if (mode === 'member' && write && retry && response.status === 403 && data.code === 'CSRF_INVALID') {
    await refreshCsrf(); return send(path, body, false);
   }
   if (response.status === 401) csrf = undefined;
   const error = new Error(data?.code || 'HTTP_' + response.status); error.status = response.status; throw error;
  }
  if (path === '/auth/login' || path === '/auth/logout') csrf = undefined;
  return data;
 }
 async function refreshCsrf() { csrf = await send('/auth/csrf'); }
 return {
  async init() {
   const response = await fetcher('/api/v1/form/config', {credentials:'same-origin',cache:'no-store'});
   if (!response.ok) throw new Error('AUTH_MODE_UNAVAILABLE');
   const config = await response.json();
   if (!['member','local'].includes(config.authMode)) throw new Error('AUTH_MODE_UNAVAILABLE');
   mode = config.authMode; return mode;
  },
  request:send,
  setBasic(value) { if (mode !== 'local') throw new Error('BASIC_NOT_ALLOWED'); basic = value; },
  clear() { basic = ''; csrf = undefined; }
 };
}
