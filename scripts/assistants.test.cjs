const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
function client(api) {
  const elements=new Map();
  function el(id) {
    if(!elements.has(id))elements.set(id,{value:'',hidden:false,disabled:false,options:[],listeners:{},textContent:'',
      addEventListener(type,handler){this.listeners[type]=handler;},replaceChildren(...children){this.children=children;},focus(){this.focused=true;},
      reset(){},querySelector(){return el(id+'-submit');}});
    return elements.get(id);
  }
  const form=el('assistant-payment');form.elements={kind:{value:'WITHDRAWAL'},amount:{value:'200'},bankCode:{value:'044',replaceChildren(){}},accountNumber:{value:'0123456789'}};
  const payment=el('money-form');payment.elements={amount:{},destination:{},password:{value:'old-password'},serviceId:{options:[{value:'044'}]}};
  el('payment-kind').options=[{value:'WITHDRAWAL',disabled:false}];
  const notices=[];
  const context=vm.createContext({$:el,epoch:1,token:'fixture',api,Option:function(text,value){this.text=text;this.value=value;},
    money:value=>'NGN '+value,location:{hash:'#assistants'},notice:message=>notices.push(message),node:(tag,text)=>({tag,text}),
    SavExTable:(headers,rows)=>({rows}),loadPayments:async()=>{},changePaymentKind:async()=>{},scheduleBankLookup:()=>{}});
  vm.runInContext(source.slice(source.indexOf('let assistantProposal=null;')),context);
  return {context,el,form,payment,notices};
}
const proposal={kind:'WITHDRAWAL',amountKobo:20000,applicationFeeKobo:100,totalDebitKobo:20100,bankCode:'044',accountNumber:'0123456789',recipientName:'Fixture Recipient',message:'Proposal only'};
test('proposal preparation and handoff never submit an order or copy a password',async()=>{
  const calls=[];const c=client(async(path)=>{calls.push(path);return proposal;});
  c.form.listeners.submit({preventDefault(){},currentTarget:c.form});
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(c.el('assistant-use-proposal').hidden,false);
  await c.el('assistant-use-proposal').listeners.click({currentTarget:c.el('assistant-use-proposal')});
  assert.deepEqual(calls,['/assistants/payments/prepare']);
  assert.equal(c.payment.elements.amount.value,'200.00');assert.equal(c.payment.elements.destination.value,'0123456789');
  assert.equal(c.payment.elements.password.value,'');assert.equal(c.context.location.hash,'payments');
});
test('a proposal arriving after sign-out cannot be displayed or used',async()=>{
  let finish;const c=client(()=>new Promise(resolve=>finish=resolve));
  c.form.listeners.submit({preventDefault(){},currentTarget:c.form});
  c.context.epoch=2;c.context.token=null;c.context.resetAssistants();finish(proposal);
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(c.el('assistant-use-proposal').hidden,true);
  assert.equal(c.el('assistant-payment-result').children.length,0);
});
test('editing payment details invalidates an outstanding proposal',async()=>{
  let finish;const c=client(()=>new Promise(resolve=>finish=resolve));
  c.form.listeners.submit({preventDefault(){},currentTarget:c.form});
  c.form.elements.amount.value='300';c.form.listeners.input();finish(proposal);
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(c.el('assistant-use-proposal').hidden,true);
  assert.match(c.el('assistant-payment-result').textContent,/Details changed/);
});
