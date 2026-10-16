const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/app.js'),'utf8');
function client(api){
  const elements=new Map(),body={children:[],classList:{add(){},remove(){}},append(element){this.children.push(element);elements.set(element.id,element);}};
  function node(tag,text){return {tag,text,children:[],listeners:{},append(...children){this.children.push(...children);},setAttribute(){},addEventListener(event,callback){this.listeners[event]=callback;},showModal(){this.open=true;},close(){this.open=false;},remove(){elements.delete(this.id);body.children=body.children.filter(child=>child!==this);}};}
  const context=vm.createContext({epoch:1,token:'fixture',api,node,money:value=>'NGN '+value,document:{body,createElement:tag=>node(tag),getElementById:id=>elements.get(id)},window:{print(){}}});
  vm.runInContext(fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/report-table.js'),'utf8'),context);context.SavExTable=context.window.SavExTable;
  vm.runInContext(source.slice(source.indexOf('function closePaymentReceipt()'),source.indexOf('async function loadPayments()')),context);
  return {context,body};
}
test('receipt uses backend amounts and renders untrusted references as text',async()=>{
  const receipt={reference:'<img onerror=bad()>',providerReference:'123',environment:'TEST',kind:'DEPOSIT',status:'SUCCESS',amountKobo:10000,serviceFeeKobo:50,authorizedTotalKobo:10050,createdAt:'2026-10-10T10:00:00Z',updatedAt:'2026-10-10T10:00:00Z',note:'Confirmed'};
  const current=client(async()=>receipt);await current.context.showPaymentReceipt('order');
  const dialog=current.body.children[0];assert.equal(dialog.open,true);
  const descendants=element=>[element,...element.children.flatMap(descendants)];
  const all=descendants(dialog);
  assert.ok(all.some(child=>child.textContent==='<img onerror=bad()>'));
  assert.ok(all.some(child=>child.textContent==='NGN 100.5'));
  assert.ok(all.some(child=>child.tag==='table'));
  current.context.closePaymentReceipt();assert.equal(current.body.children.length,0);
});
test('receipt arriving after sign-out is discarded',async()=>{
  let resolve;const current=client(()=>new Promise(done=>resolve=done));
  const pending=current.context.showPaymentReceipt('order');current.context.epoch=2;current.context.token=null;resolve({});
  await pending;assert.equal(current.body.children.length,0);
});
