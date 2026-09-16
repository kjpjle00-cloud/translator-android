const CACHE_PREFIX='ans-translator-';
const CACHE=CACHE_PREFIX+'v6-8-7-20260915';
const ASSETS=['./','./index.html','./styles.css?v=6.8.7','./conversation.css?v=6.8.7','./speech-session.js?v=6.8.7','./app-v6.8.7.js?v=6.8.7','./manifest.webmanifest','./icon-192.png','./icon-512.png','./apple-touch-icon.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)));self.skipWaiting();});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith(CACHE_PREFIX)&&k!==CACHE).map(k=>caches.delete(k)))));self.clients.claim();});
self.addEventListener('fetch',e=>{
  if(e.request.method!=='GET')return;
  const url=new URL(e.request.url);
  const base=new URL('./',self.location.href);
  // Never persist conversation requests or cross-origin translation responses.
  if(url.origin!==base.origin)return;
  if(!ASSETS.some(path=>new URL(path,base).href===url.href))return;
  e.respondWith((async()=>{
    const cache=await caches.open(CACHE);
    const cached=await cache.match(e.request);
    if(cached)return cached;
    try{
      const response=await fetch(e.request);
      if(response.ok)await cache.put(e.request,response.clone());
      return response;
    }catch(error){
      if(e.request.mode==='navigate')return (await cache.match(new URL('index.html',base).href))||Response.error();
      return Response.error();
    }
  })());
});
