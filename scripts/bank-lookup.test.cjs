const test=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function client(api){
  const elements=new Map();function el(id){if(!elements.has(id))elements.set(id,{value:'',hidden:false,textContent:'',listeners:{},addEventListener(t,f){this.listeners[t]=f;},close(){this.open=false;}});return elements.get(id);}
  el('payment-kind').value='WITHDRAWAL';const destination=el('destination');destination.value='0123456789';
  el('money-form').elements={destination,serviceId:{value:'044'}};
  const timers=new Map();let timerId=0;
  const context=vm.createContext({$:el,epoch:1,token:'fixture',paymentDraft:null,api,setTimeout:fn=>{timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id),syncPaymentButton:()=>{}});
  vm.runInContext(source.slice(source.indexOf('let bankLookupVersion='),source.indexOf('async function loadPaymentHistory()')),context);
  return {context,el,run:async()=>{const calls=[...timers.values()];timers.clear();await Promise.all(calls.map(fn=>fn()));},confirmed:()=>vm.runInContext('bankDetailsConfirmed()',context)};
}
test('complete bank details resolve and display a confirmed account name without creating a payment',async()=>{
  const paths=[];const c=client(async path=>{paths.push(path);return {account_number:'0123456789',account_name:'Fixture Recipient'};});
  c.context.scheduleBankLookup();assert.equal(c.el('bank-account-name').value,'');await c.run();
  assert.equal(c.el('bank-account-name').value,'Fixture Recipient');assert.equal(c.confirmed(),true);assert.deepEqual(paths,['/money/resolve-bank']);
  c.el('money-form').elements.destination.value='0123';c.context.scheduleBankLookup();assert.equal(c.confirmed(),false);assert.equal(c.el('bank-account-name').value,'');
});
test('old lookup response cannot overwrite a changed account or a signed-out session',async()=>{
  let finish;const c=client(()=>new Promise(resolve=>finish=resolve));c.context.scheduleBankLookup();const pending=c.run();
  c.el('money-form').elements.destination.value='9999999999';c.context.scheduleBankLookup();
  finish({account_number:'0123456789',account_name:'Old Recipient'});await pending;assert.equal(c.el('bank-account-name').value,'');assert.equal(c.confirmed(),false);
  const next=c.run();c.context.epoch=2;c.context.token=null;c.context.resetBankLookup();finish({account_number:'9999999999',account_name:'Late Recipient'});await next;
  assert.equal(c.el('bank-account-name').value,'');assert.equal(c.confirmed(),false);
});
test('mismatched account details block confirmation and offer retry',async()=>{
  const c=client(async()=>({account_number:'9999999999',account_name:'Other Recipient'}));c.context.scheduleBankLookup();await c.run();
  assert.equal(c.confirmed(),false);assert.equal(c.el('bank-lookup-retry').hidden,false);assert.match(c.el('bank-lookup-status').textContent,/could not be confirmed/);
});
