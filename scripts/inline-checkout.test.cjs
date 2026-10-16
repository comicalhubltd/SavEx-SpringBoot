const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/app.js'),'utf8');
function checkoutClient() {
  let config;const requests=[],notices=[];
  const context=vm.createContext({epoch:1,token:'fixture-token',setTimeout,clearTimeout,
    window:{FlutterwaveCheckout(value){config=value;return {close(){value.onclose();}};}},
    api:async(path)=>{requests.push(path);return {public_key:'FLWPUBK_TEST-fixture',tx_ref:'saved-reference',amount:502.5,currency:'NGN',customer:{name:'Customer',email:'fixture@example.test'}};},
    notice:message=>notices.push(message)
  });
  vm.runInContext(source.slice(source.indexOf('let checkoutLibraryPromise='),source.indexOf('function syncPaymentButton()')),context);
  vm.runInContext('watchCheckout=async()=>{};',context);
  return {context,requests,notices,config:()=>config};
}
test('checkout stays on page, uses saved values, and duplicate provider callbacks only request one verification',async()=>{
  const client=checkoutClient();await client.context.openInlineCheckout('order-1');
  const config=client.config();assert.equal(config.amount,502.5);assert.equal(config.tx_ref,'saved-reference');
  assert.equal(config.redirect_url,undefined);assert.equal(config.secret_key,undefined);
  await assert.rejects(client.context.openInlineCheckout('order-2'),/already open/);
  config.callback({status:'successful'});config.callback({status:'successful'});config.onclose();
  await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(client.requests,['/money/orders/order-1/checkout','/money/orders/order-1/checkout/check']);
  assert.ok(client.notices.every(text=>!text.includes('Payment confirmed')));
});
test('closing an old payment window after sign-out cannot verify or change the new session',async()=>{
  const client=checkoutClient();await client.context.openInlineCheckout('order-1');
  client.context.epoch=2;client.context.token=null;client.config().onclose();
  assert.deepEqual(client.requests,['/money/orders/order-1/checkout']);
});
