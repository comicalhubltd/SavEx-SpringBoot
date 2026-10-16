'use strict';
// Credentials live only in this page's memory. Payment requests are never replayed here.
window.SavExSession=(()=>{
  let refreshToken=null,expiresAt=0,timer=null,pending=null,generation=0,onTokens=()=>{},onEnd=()=>{};
  function clear(){generation++;clearTimeout(timer);timer=null;refreshToken=null;expiresAt=0;pending=null;}
  function schedule(){clearTimeout(timer);timer=setTimeout(()=>ensure(true).catch(()=>{}),Math.max(1000,expiresAt-Date.now()-60000));}
  function start(data,tokens,ended){clear();refreshToken=data.refreshToken;expiresAt=Date.now()+data.expiresIn*1000;onTokens=tokens;onEnd=ended;schedule();}
  async function post(path,credential){
    const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),15000);
    try{
      const response=await fetch('/api/v1/auth/'+path,{method:'POST',cache:'no-store',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:credential}),signal:controller.signal});
      const body=await response.json().catch(()=>({}));
      if(!response.ok || body.success===false){const error=new Error(body.message||'Session request failed');error.status=response.status;throw error;}
      return body.data;
    }finally{clearTimeout(timeout);}
  }
  function ensure(force=false){
    if(!refreshToken)return Promise.resolve();
    if(pending)return pending;
    if(!force && Date.now()<expiresAt-60000)return Promise.resolve();
    const version=generation,credential=refreshToken;
    pending=post('refresh',credential).then(data=>{
      if(version!==generation)return;
      refreshToken=data.refreshToken;expiresAt=Date.now()+data.expiresIn*1000;onTokens(data);schedule();
    }).catch(error=>{
      if(version!==generation)return;
      if(error.status===401 || Date.now()>=expiresAt){const ended=onEnd;clear();ended();}
      else {clearTimeout(timer);timer=setTimeout(()=>ensure(true).catch(()=>{}),15000);}
      throw error;
    }).finally(()=>{if(version===generation)pending=null;});
    return pending;
  }
  async function signOut(){
    const credential=refreshToken;clear();
    if(credential)await post('logout',credential);
  }
  return {start,ensure,clear,signOut};
})();
