const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function client(api){
 const fields=new Map();function el(id){if(!fields.has(id))fields.set(id,{value:'',hidden:false,disabled:false,textContent:'',replaceChildren(...rows){this.options=rows;this.value='';}});return fields.get(id);}
 el('payment-kind').value='AIRTIME';el('money-form').elements={password:{},destination:{},serviceId:{},variationCode:{},amount:{}};
 const context=vm.createContext({$:el,api,epoch:1,token:'fixture',catalogueVersion:0,catalogueLoading:false,paymentCatalog:[],billCategories:{AIRTIME:'AIRTIME'},paymentCapabilities:{bills:true},resetBankLookup(){},bankDetailsConfirmed(){return true;},
 Option:function(text,value){this.text=text;this.value=value;},options(select,rows){select.replaceChildren(...rows.map(row=>({text:row.name,value:row.biller_code})));}});
 vm.runInContext(source.slice(source.indexOf('function syncPaymentButton()'),source.indexOf('let assessmentRefreshInProgress')),context);
 vm.runInContext(source.slice(source.indexOf('async function changePaymentKind()'),source.indexOf("$('payment-catalogue-retry')?.addEventListener")),context);
 return {context,el};
}
test('provider rejection is visible beside the dropdown and prevents review',async()=>{
 const c=client(async()=>{throw new Error('Flutterwave HTTP 400: confirm Bills API access');});await c.context.changePaymentKind();
 assert.match(c.el('payment-catalogue-status').textContent,/Bills API access/);assert.equal(c.el('payment-catalogue-retry').hidden,false);
 assert.equal(c.el('payment-provider').disabled,true);assert.equal(c.el('payment-submit').disabled,true);assert.equal(c.el('payment-provider').options[0].text,'Providers unavailable');
});
test('successful catalogue populates providers while payment still requires a selected package',async()=>{
 const c=client(async()=>[{biller_code:'BIL001',name:'Fixture Network'}]);await c.context.changePaymentKind();
 assert.equal(c.el('payment-provider').disabled,false);assert.equal(c.el('payment-provider').options[0].value,'BIL001');assert.equal(c.el('payment-submit').disabled,true);
});
test('empty catalogue is explicit and stale responses cannot populate another session',async()=>{
 const empty=client(async()=>[]);await empty.context.changePaymentKind();assert.match(empty.el('payment-catalogue-status').textContent,/No providers/);
 let finish;const c=client(()=>new Promise(resolve=>finish=resolve));const work=c.context.changePaymentKind();c.context.epoch=2;c.context.token=null;finish([{biller_code:'OLD',name:'Old'}]);await work;
 assert.notEqual(c.el('payment-provider').options[0].value,'OLD');
});
