const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/app.js'), 'utf8');
// Exercise the actual browser transport without loading the unrelated DOM event handlers.
const start = source.indexOf('async function api(');
const end = source.indexOf('async function busy(');
assert.ok(start >= 0 && end > start);
function client(fetch, overrides = {}) {
  const context = vm.createContext({
    fetch, AbortController, setTimeout, clearTimeout,
    navigator: {onLine:true}, location: {hostname:'localhost'}, token:null,
    clearSession() {}, ...overrides
  });
  vm.runInContext(source.slice(start,end),context);
  return context.api;
}
test('stopped local server explains how to start Java', async () => {
  const api = client(async () => {throw new TypeError('Failed to fetch');});
  await assert.rejects(api('/auth/register',{method:'POST'}),/Start SavEx in VS Code with mvn spring-boot:run/);
});
test('localhost works without internet or AbortSignal.timeout', async () => {
  const api = client(async () => ({ok:true,status:200,json:async () => ({success:true,data:'created'})}),
    {navigator:{onLine:false}});
  assert.equal(await api('/auth/register',{method:'POST'}),'created');
});
test('registration timeout is ambiguous and never retries the POST', async () => {
  let requests = 0;
  let deadline;
  const api = client((url,options) => {
    requests++;
    return new Promise((resolve,reject) => options.signal.addEventListener('abort',() => reject(new Error('aborted'))));
  }, {setTimeout(fn,ms) {deadline=ms; queueMicrotask(fn); return 1;},clearTimeout() {}});
  await assert.rejects(api('/auth/register',{method:'POST'}),/account may have been created; try signing in/);
  assert.equal(requests,1);
  assert.equal(deadline,60000);
});
test('server validation errors remain visible', async () => {
  const api = client(async () => ({ok:false,status:422,json:async () => ({
    success:false,message:'Validation failed',data:{phone:'Enter a valid Nigerian phone number'}
  })}));
  await assert.rejects(api('/auth/register',{method:'POST'}),/Enter a valid Nigerian phone number/);
});
test('remote offline requests show connectivity advice', async () => {
  const api = client(async () => assert.fail('must not fetch while offline'), {
    navigator:{onLine:false},location:{hostname:'savex.example'}
  });
  await assert.rejects(api('/auth/login'),/Connect to the internet/);
});
test('remote connection failures do not show local developer commands', async () => {
  const api = client(async () => {throw new TypeError('network');}, {location:{hostname:'savex.example'}});
  await assert.rejects(api('/auth/login'),error => /Could not reach SavEx/.test(error.message) && !/mvn/.test(error.message));
});

test('payment idempotency key is preserved alongside authentication', async () => {
  let sent;
  const api = client(async (url,options) => {sent=options;return {ok:true,status:200,json:async()=>({success:true,data:{reference:'saved'}})};}, {token:'fixture-token'});
  await api('/money/orders',{method:'POST',headers:{'Idempotency-Key':'fixture-payment-key'},body:'{}'});
  assert.equal(sent.headers['Idempotency-Key'],'fixture-payment-key');
  assert.equal(sent.headers.Authorization,'Bearer fixture-token');
  assert.equal(sent.headers['Content-Type'],'application/json');
});
test('payment network errors never automatically submit a second charge', async () => {
  let calls=0;
  const api=client(async()=>{calls++;throw new TypeError('connection lost');});
  await assert.rejects(api('/money/orders',{method:'POST',headers:{'Idempotency-Key':'fixture-payment-key'},body:'{}'}));
  assert.equal(calls,1);
});
