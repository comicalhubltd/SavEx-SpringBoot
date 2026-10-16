const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function client(api){
 const fields=new Map(),option={disabled:true};function el(id){if(!fields.has(id))fields.set(id,{hidden:true,textContent:'',querySelector(){return option;},replaceChildren(...rows){this.rows=rows;}});return fields.get(id);}
 const context=vm.createContext({$:el,api,epoch:1,token:'fixture',money:v=>String(v),SavExTable:(headers,rows)=>({headers,rows}),syncPaymentButton(){}});
 vm.runInContext(source.slice(source.indexOf('async function loadPayoutWallet()'),source.indexOf("$('payout-wallet-create')?.addEventListener")),context);
 return {context,el,option};
}
const wallet={enabled:true,mode:'TEST',state:'ACTIVE',accountName:'Test Saver',accountNumber:'0123456789',bankName:'Fixture Bank',balanceKobo:99000,reservedKobo:10000,availableKobo:89000,providerAvailableKobo:99000};
test('payout account details and its separate balances are displayed without changing checkout balances',async()=>{
 const c=client(async()=>wallet);await c.context.loadPayoutWallet();assert.equal(c.el('payout-wallet-panel').hidden,false);assert.equal(c.el('payout-wallet-create').hidden,true);assert.equal(c.option.disabled,false);
 const rows=c.el('payout-wallet-details').rows[0].rows;assert.equal(rows.find(r=>r[0]==='Account number')[1],'0123456789');assert.equal(rows.find(r=>r[0]==='Available customer balance')[1],'890');assert.equal(c.el('balance').textContent,'');
});
test('uncertain creation cannot show another create button or enable payout withdrawals',async()=>{
 const c=client(async()=>({...wallet,state:'CREATION_UNCERTAIN',accountNumber:null}));await c.context.loadPayoutWallet();assert.equal(c.el('payout-wallet-create').hidden,true);assert.equal(c.option.disabled,true);
});
test('funding account responses arriving after sign-out are discarded',async()=>{
 let finish;const c=client(()=>new Promise(resolve=>finish=resolve));const work=c.context.loadPayoutWallet();c.context.epoch=2;c.context.token=null;finish(wallet);await work;assert.equal(c.el('payout-wallet-details').rows,undefined);
});
