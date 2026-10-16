const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/session.js'),'utf8');
function client(fetch){
  const timers=new Map();let next=0;
  const context=vm.createContext({window:{},fetch,AbortController,Date,setTimeout(fn){timers.set(++next,fn);return next;},clearTimeout(id){timers.delete(id);}});
  vm.runInContext(source,context);return {session:context.window.SavExSession,timers};
}
function response(data,status=200){return {ok:status===200,status,json:async()=>({success:status===200,data})};}
test('concurrent renewal is single-flight and uses the rotated credential next time',async()=>{
  const requests=[];let resolve;const updates=[];
  const current=client((url,options)=>{requests.push({url,body:JSON.parse(options.body)});return new Promise(done=>resolve=done);});
  current.session.start({refreshToken:'first',expiresIn:900},data=>updates.push(data),()=>{});
  const first=current.session.ensure(true),second=current.session.ensure(true);
  assert.equal(first,second);assert.equal(requests.length,1);
  resolve(response({refreshToken:'second',accessToken:'new-access',expiresIn:900}));await first;
  const next=current.session.ensure(true);assert.equal(requests[1].body.refreshToken,'second');
  resolve(response({refreshToken:'third',accessToken:'newer-access',expiresIn:900}));await next;
  assert.equal(updates.length,2);assert.ok(requests.every(item=>item.url==='/api/v1/auth/refresh'));
});
test('sign-out during renewal discards stale results and sends only its own credential',async()=>{
  const requests=[];let resolve,updates=0;
  const current=client((url,options)=>{requests.push({url,body:JSON.parse(options.body)});return url.endsWith('/refresh')?new Promise(done=>resolve=done):Promise.resolve(response('signed out'));});
  current.session.start({refreshToken:'first',expiresIn:900},()=>updates++,()=>{});
  const renewal=current.session.ensure(true);await current.session.signOut();
  resolve(response({refreshToken:'stale',accessToken:'stale',expiresIn:900}));await renewal;
  assert.equal(updates,0);assert.equal(current.timers.size,0);
  assert.equal(requests[1].url,'/api/v1/auth/logout');assert.equal(requests[1].body.refreshToken,'first');
});
test('server rejection ends the local session and never retries a payment',async()=>{
  let ended=0;const paths=[];
  const current=client(async url=>{paths.push(url);return response(null,401);});
  current.session.start({refreshToken:'first',expiresIn:900},()=>{},()=>ended++);
  await assert.rejects(current.session.ensure(true));assert.equal(ended,1);assert.equal(current.timers.size,0);
  await current.session.ensure(true);assert.deepEqual(paths,['/api/v1/auth/refresh']);
});
test('temporary renewal failure keeps the current session and schedules a retry',async()=>{
  let ended=0;const current=client(async()=>{throw new TypeError('network unavailable');});
  current.session.start({refreshToken:'first',expiresIn:900},()=>{},()=>ended++);
  await assert.rejects(current.session.ensure(true));assert.equal(ended,0);assert.equal(current.timers.size,1);
});
