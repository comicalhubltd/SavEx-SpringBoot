'use strict';
const $ = (id) => document.getElementById(id);
const money = (value) => new Intl.NumberFormat('en-NG', {style:'currency', currency:'NGN'}).format(value);
let token = null, user = null, mode = 'login', page = 0, epoch = 0, installPrompt = null;
let lastAssessmentSignature=null;
function assessmentSignature(report) {
  return JSON.stringify([report.accountStatus,report.identityStatus,report.emailVerified,report.fundingAvailable,report.fundingBlockers]);
}
function notice(message, error = false) { $('notice').textContent = message; $('notice').classList.toggle('error', error); $('notice').hidden = !message; }
function clearSession() {
  resetBankLookup();
  resetAssistants();
  if(typeof SavExSession!=='undefined')SavExSession.clear();
  closePaymentReceipt();
  checkoutWatch++;
  const oldCheckout=activeCheckout;activeCheckout=null;
  token = null; user = null; epoch++; page = 0;
  $('customer-transfer-balance').textContent='Withdrawal funding status has not been checked.';
  oldCheckout?.modal?.close();
  lastAssessmentSignature=null;
  paymentDraft=null; paymentKey=null; paymentSignature=null; paymentCapabilities=null;
  paymentFundingSource='CHECKOUT';
  if($('payout-wallet-details'))$('payout-wallet-details').replaceChildren();
  if($('payout-wallet-panel'))$('payout-wallet-panel').hidden=true;
  $('payout-wallet-create')?.reset();
  if($('withdrawal-source'))$('withdrawal-source').value='CHECKOUT';
  if ($('payment-confirm').open) $('payment-confirm').close();
  $('money-form').reset(); $('payment-history').replaceChildren();
  $('email-form').reset(); $('email-status').textContent='Check your registered email to confirm ownership.';
  $('dashboard').hidden = true; $('welcome').hidden = false; $('logout').hidden = true;
  $('groups').replaceChildren(); ['available','balance','locked'].forEach(id => $(id).textContent = '—');
  $('auth-form').reset(); $('create-form').reset(); $('join-form').reset();
  if ($('create-dialog').open) $('create-dialog').close();
}
async function api(path, options = {}) {
  if(token && !path.startsWith('/auth/') && typeof SavExSession!=='undefined')await SavExSession.ensure();
  const localServer = ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
  if (!navigator.onLine && !localServer) throw new Error('Connect to the internet and try again.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), path === '/auth/register' ? 60000 : 30000);
  let response;
  try { response = await fetch('/api/v1' + path, { ...options, cache:'no-store', headers:{...(options.body ? {'Content-Type':'application/json'} : {}), ...(token ? {Authorization:'Bearer ' + token} : {}), ...(options.headers || {})}, signal:controller.signal }); }
  catch {
    if (controller.signal.aborted) {
      throw new Error(path === '/auth/register'
        ? 'The server took too long to respond. Your account may have been created; try signing in before registering again.'
        : 'The server took too long to respond. Please try again shortly.');
    }
    throw new Error(localServer
      ? 'The local SavEx server is not reachable. Start SavEx in VS Code with mvn spring-boot:run, keep it running, then reload this page.'
      : 'Could not reach SavEx. Check your connection and try again shortly.');
  } finally { clearTimeout(timeout); }
  const result = await response.json().catch(() => ({}));
  if (response.status === 401 && token) { clearSession(); throw new Error('Your session has ended. Please sign in again.'); }
  if (!response.ok || result.success === false) {
    const details = result.message === 'Validation failed' && result.data ? Object.values(result.data).join('. ') : '';
    const error=new Error(details || result.message || 'This request could not be completed.');error.status=response.status;throw error;
  }
  return result.data;
}

async function busy(form, work) {
  const buttons = [...form.querySelectorAll('button')];
  buttons.forEach(button => button.disabled = true);
  try { await work(); } catch(error) {
    if (form.id === 'create-form') { $('create-error').textContent = error.message; $('create-error').hidden = false; }
    else notice(error.message, true);
  } finally { buttons.forEach(button => button.disabled = false); if(form.id==='money-form') syncPaymentButton(); }
}
function setMode(next) {
  mode = next; const register = mode === 'register';
  $('register-fields').hidden = !register;
  ['fullName','phone'].forEach(name => $('auth-form').elements[name].required = register);
  const identity = $('auth-form').elements.emailOrPhone;
  identity.type = register ? 'email' : 'text';
  $('identity-label').firstChild.textContent = register ? 'Email address' : 'Email or phone';
  $('auth-form').elements.password.minLength = register ? 8 : 1;
  $('auth-form').elements.password.autocomplete = register ? 'new-password' : 'current-password';
  $('auth-title').textContent = register ? 'Start your savings journey' : 'Welcome back';
  $('auth-help').textContent = register ? 'Create your account to get started.' : 'Sign in to your savings space.';
  $('auth-submit').textContent = register ? 'Create account' : 'Sign in';
  ['login','register'].forEach(value => { $(value + '-tab').classList.toggle('selected', mode === value); $(value + '-tab').setAttribute('aria-pressed', String(mode === value)); });
  notice('');
}
$('login-tab').addEventListener('click', () => setMode('login'));
$('register-tab').addEventListener('click', () => setMode('register'));
$('auth-form').addEventListener('submit', event => {
  event.preventDefault();
  busy(event.currentTarget, async () => {
    const fields = Object.fromEntries(new FormData($('auth-form')));
    const body = mode === 'register' ? {fullName:fields.fullName.trim(), phone:fields.phone.trim(), email:fields.emailOrPhone.trim(), password:fields.password} : {emailOrPhone:fields.emailOrPhone.trim(),password:fields.password};
    const data = await api('/auth/' + mode, {method:'POST',body:JSON.stringify(body)});
    token = data.accessToken; user = data.user; epoch++;
    SavExSession.start(data,next=>{token=next.accessToken;user=next.user;},()=>{clearSession();notice('Your session has ended. Please sign in again.',true);});
    $('welcome').hidden = true; $('dashboard').hidden = false; $('logout').hidden = false;
    $('greeting').textContent = 'Hello, ' + user.fullName.split(' ')[0];
    $('auth-form').reset(); notice(''); page = 0;
    showEmail(await api('/verification/email'));
    await openDashboard();
  });
});
function showEmail(result) {
  $('email-status').textContent=result.message;
  $('email-send').hidden=result.verified;
  $('email-check').hidden=result.verified;
  $('email-form').elements.code.disabled=result.verified;
  if(result.verified && user) user.emailVerified=true;
}
async function openDashboard() {
  $('payment-name').value=user.fullName;
  $('payment-email').value=user.email;
  const assessment=await api('/verification/account-assessment');
  lastAssessmentSignature=assessmentSignature(assessment);
  user.status=assessment.accountStatus;user.emailVerified=assessment.emailVerified;
  const pending=user.status!=='ACTIVE', canView=!pending || user.emailVerified;
  $('payments-panel').hidden=!canView;
  $('email-panel').hidden=user.emailVerified;
  document.querySelectorAll('#dashboard > .section-heading, #dashboard > .summary').forEach(el=>el.hidden=!canView);
  document.querySelectorAll('#dashboard > .workspace').forEach(el=>el.hidden=pending);
  if(!canView) { notice('Verify your email to enter your dashboard.'); return; }
  notice(pending?'Complete email verification and valid account details for automatic account approval.':
    assessment.identityStatus==='UNVERIFIED'?'Your account is active. Identity remains unverified; the administrator can review your report.':'');
  await refresh();
  await loadPayments();
}
$('email-send').addEventListener('click',()=>busy($('email-form'),async()=>showEmail(await api('/verification/email/send',{method:'POST'}))));
$('email-form').addEventListener('submit',event=>{
  event.preventDefault();
  busy(event.currentTarget,async()=>{
    const code=$('email-form').elements.code.value.trim();
    showEmail(await api('/verification/email/check',{method:'POST',body:JSON.stringify({code})}));
    $('email-form').elements.code.value='';
    if(user.emailVerified) await openDashboard();
  });
});
function node(tag, text, className) { const el = document.createElement(tag); el.textContent = text; if (className) el.className = className; return el; }
function renderGroups(data) {
  $('groups').replaceChildren();
  if (!data.content.length) {
    const empty = node('div', '', 'empty');
    empty.append(node('h3','Your next goal starts here'), node('p','Create a circle or join one with an invitation.'));
    $('groups').append(empty);
  }
  for (const group of data.content) {
    const article = node('article','','card group');
    const head = node('div','','group-head');
    head.append(node('h3',group.name),node('span',group.status,'badge')); article.append(head);
    if (group.description) article.append(node('p',group.description,'muted small'));
    const details = node('div','','group-details');
    for (const [label,value] of [['Per cycle',money(group.contributionNaira)],['Members',group.currentMembers + ' / ' + group.maxMembers],['Frequency',({WEEKLY:'Weekly',BIWEEKLY:'Every two weeks',MONTHLY:'Monthly'})[group.cycleFrequency] || group.cycleFrequency]]) {
      const item = node('div',label); item.append(node('strong',value)); details.append(item);
    }
    article.append(details, node('p','Starts ' + group.startDate,'small muted'));
    const bottom = node('div','','group-bottom'); bottom.append(node('span','Invite: ' + group.inviteCode));
    const copy = node('button','Copy code','secondary'); copy.type = 'button';
    copy.addEventListener('click', async () => { try { await navigator.clipboard.writeText(group.inviteCode); notice('Invite code copied.'); } catch { notice('Copy this invite code: ' + group.inviteCode); } });
    bottom.append(copy);
    if(group.status==='ACTIVE') {
      const contribute=node('button','Contribute to cycle '+(group.currentCycle+1),'primary');
      contribute.type='button';contribute.addEventListener('click',async()=>{
        contributionGroup=group;$('payment-kind').value='CONTRIBUTION';await changePaymentKind();
        $('money-form').elements.amount.value=group.contributionNaira;$('money-form').elements.amount.readOnly=true;
        $('payments-panel').scrollIntoView({behavior:'smooth'});$('money-form').elements.password.focus();
      });bottom.append(contribute);
    }
    article.append(bottom); $('groups').append(article);
  }
  $('previous').disabled = data.first; $('next').disabled = data.last;
  $('page-label').textContent = 'Page ' + (data.number + 1) + ' of ' + Math.max(1,data.totalPages);
}
async function loadGroups() {
  const current = epoch;
  const data = await api('/groups/my?page=' + page + '&size=6&sort=createdAt,desc');
  if (current === epoch && token) renderGroups(data);
}
async function refresh() {
  const current = epoch; $('refresh').disabled = true;
  ['available','balance','locked'].forEach(id => $(id).textContent = '—');
  try {
    const results = await Promise.allSettled([api('/wallet'),loadGroups()]);
    if (current !== epoch || !token) return;
    if (results[0].status === 'fulfilled') {
      const wallet = results[0].value;
      $('available').textContent = money(wallet.availableNaira); $('balance').textContent = money(wallet.balanceNaira); $('locked').textContent = money(wallet.lockedNaira);
    }
    const failure = results.find(result => result.status === 'rejected');
    if (failure) notice(failure.reason.message,true);
  } finally { $('refresh').disabled = false; }
}
$('refresh').addEventListener('click', () => { openDashboard().catch(error=>notice(error.message,true)); });
$('logout').addEventListener('click', async () => {
  const revocation=SavExSession.signOut();clearSession();notice('Signing out…');
  try{await revocation;notice('You have signed out.');}
  catch{notice('Signed out on this device. Server sign-out could not be confirmed; the session will expire automatically.',true);}
});
$('previous').addEventListener('click', async () => { page = Math.max(0,page-1); try { await loadGroups(); } catch(error) {notice(error.message,true);} });
$('next').addEventListener('click', async () => { page++; try { await loadGroups(); } catch(error) {page--; notice(error.message,true);} });
$('join-form').addEventListener('submit', event => { event.preventDefault(); busy(event.currentTarget, async () => {
  const code = $('join-form').elements.inviteCode.value.trim().toUpperCase();
  await api('/groups/join/' + encodeURIComponent(code),{method:'POST'});
  $('join-form').reset(); page = 0; notice('You have joined the circle.'); await refresh();
}); });
$('show-create').addEventListener('click', () => {
  const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate()+1);
  const date = tomorrow.getFullYear() + '-' + String(tomorrow.getMonth()+1).padStart(2,'0') + '-' + String(tomorrow.getDate()).padStart(2,'0');
  $('create-form').elements.startDate.min = date; $('create-form').elements.startDate.value = date;
  $('create-error').hidden = true; $('create-dialog').showModal();
});
$('close-create').addEventListener('click', () => $('create-dialog').close());
$('create-form').addEventListener('submit', event => { event.preventDefault(); busy(event.currentTarget, async () => {
  const values = Object.fromEntries(new FormData($('create-form')));
  await api('/groups',{method:'POST',body:JSON.stringify({...values, contributionNaira:Number(values.contributionNaira), maxMembers:Number(values.maxMembers), payoutOrder:'SEQUENTIAL'})});
  $('create-dialog').close(); $('create-form').reset(); page = 0; notice('Circle created. Share its invite code with your people.'); await refresh();
}); });
window.addEventListener('beforeinstallprompt', event => {event.preventDefault(); installPrompt = event; $('install').hidden = false;});
$('install').addEventListener('click', async () => {if (installPrompt) { await installPrompt.prompt(); installPrompt = null; $('install').hidden = true; }});
window.addEventListener('appinstalled', () => {installPrompt=null; $('install').hidden=true;});
function connection() { $('offline').hidden = navigator.onLine; }
window.addEventListener('online',connection); window.addEventListener('offline',connection); connection();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
// Access tokens and personal data live only in memory. Reloading requires sign-in.

let contributionGroup=null;
const billCategories={AIRTIME:'AIRTIME',DATA:'MOBILEDATA',ELECTRICITY:'UTILITYBILLS',CABLE:'CABLEBILLS',INTERNET:'INTSERVICE'};
let paymentCapabilities=null, paymentDraft=null, paymentKey=null, paymentSignature=null, paymentCatalog=[];
let catalogueVersion=0,catalogueLoading=false;
let bankLookupVersion=0,bankLookupTimer=null,verifiedBank=null;
function resetBankLookup() {
  bankLookupVersion++;clearTimeout(bankLookupTimer);verifiedBank=null;
  if($('bank-account-name'))$('bank-account-name').value='';
  if($('bank-lookup-status'))$('bank-lookup-status').textContent='';
  if($('bank-lookup-retry'))$('bank-lookup-retry').hidden=true;
}
function bankDetailsConfirmed() {
  const f=$('money-form').elements;
  return !!verifiedBank && verifiedBank.bank===f.serviceId.value && verifiedBank.account===f.destination.value.trim();
}
function scheduleBankLookup(immediate=false) {
  resetBankLookup();
  if($('payment-kind').value!=='WITHDRAWAL')return;
  paymentDraft=null;
  if($('payment-confirm').open)$('payment-confirm').close();
  syncPaymentButton();
  const f=$('money-form').elements,bank=f.serviceId.value,account=f.destination.value.trim();
  if(!bank || !/^[0-9]{10}$/.test(account)){
    $('bank-lookup-status').textContent='Select a bank and enter a 10-digit account number to confirm the recipient.';return;
  }
  const current=epoch,version=bankLookupVersion;
  $('bank-lookup-status').textContent='Checking account holder name…';
  bankLookupTimer=setTimeout(async()=>{
    if(!token || current!==epoch || version!==bankLookupVersion)return;
    try {
      const result=await api('/money/resolve-bank',{method:'POST',body:JSON.stringify({accountNumber:account,bankCode:bank})});
      if(!token || current!==epoch || version!==bankLookupVersion || $('payment-kind').value!=='WITHDRAWAL')return;
      if(result.account_number!==account || typeof result.account_name!=='string' || !result.account_name.trim())throw new Error('The account holder could not be confirmed. Check the bank and account number.');
      verifiedBank={bank,account,name:result.account_name.trim()};
      $('bank-account-name').value=verifiedBank.name;
      $('bank-lookup-status').textContent='Account found. Confirm this is the person you intend to pay.';
    }catch(error){
      if(!token || current!==epoch || version!==bankLookupVersion)return;
      $('bank-lookup-status').textContent=error.message;$('bank-lookup-retry').hidden=false;
    }finally{if(current===epoch && version===bankLookupVersion)syncPaymentButton();}
  },immediate?0:500);
}
$('money-form').elements.destination.addEventListener('input',()=>scheduleBankLookup());
$('bank-lookup-retry')?.addEventListener('click',()=>scheduleBankLookup(true));
async function loadPaymentHistory() {
  const current=epoch, data=await api('/money/orders');
  if(current!==epoch || !token) return;
  const history=$('payment-history'),rows=[];
  const states={QUEUED:'Queued',SUBMITTING:'Processing',PENDING:'Awaiting confirmation',SUCCESS:'Completed',FAILED:'Failed',REVIEW:'Needs support review',REVERSED:'Reversed'};
  for(const entry of data) {
    const actions=node('div','','report-actions');
    if(['SUCCESS','REVERSED'].includes(entry.status)) {
      const receipt=node('button','View receipt','secondary');receipt.type='button';
      receipt.addEventListener('click',()=>busy($('money-form'),()=>showPaymentReceipt(entry.id)));actions.append(receipt);
    }
    if(entry.kind==='DEPOSIT' && entry.fundingSource!=='PAYOUT_WALLET' && ['QUEUED','PENDING'].includes(entry.status)) {
      const resume=node('button','Continue payment in SavEx','secondary');resume.type='button';
      resume.addEventListener('click',()=>busy($('money-form'),()=>openInlineCheckout(entry.id)));actions.append(resume);
    }
    const status=node('span',states[entry.status]||entry.status,'transaction-status status-'+entry.status.toLowerCase());
    rows.push([new Date(entry.createdAt).toLocaleString(),entry.reference,entry.kind.toLowerCase()+(entry.fundingSource==='PAYOUT_WALLET'?' · personal funding account':''),status,
      money(entry.amountKobo/100),money((entry.applicationFeeKobo||0)/100),money(entry.totalDebitKobo/100),
      entry.kind==='ELECTRICITY' && entry.status==='SUCCESS'?(entry.rechargeToken||'Contact support if a prepaid token is missing'):'—',actions]);
  }
  history.replaceChildren(SavExTable(['Date','Reference','Service','Status','Amount','SavEx fee','Total','Electricity token','Actions'],rows,'Recent payments','No payments yet.'));
}
function closePaymentReceipt() {
  const dialog=document.getElementById('payment-receipt');
  if(dialog){dialog.close();dialog.remove();}
  document.body.classList.remove('receipt-print');
}
async function showPaymentReceipt(id) {
  const current=epoch,receipt=await api('/money/orders/'+encodeURIComponent(id)+'/receipt');
  if(current!==epoch || !token)return;
  closePaymentReceipt();
  const dialog=node('dialog');dialog.id='payment-receipt';dialog.setAttribute('aria-labelledby','receipt-heading');
  const heading=node('h2','SavEx payment receipt');heading.id='receipt-heading';dialog.append(heading);
  const lines=[['Reference',receipt.reference],['Provider reference',receipt.providerReference||'Not recorded'],
    ['Environment',receipt.environment],['Service',receipt.kind],['Status',receipt.status],
    ['Amount',money(receipt.amountKobo/100)],['SavEx fee',money(receipt.serviceFeeKobo/100)],['Authorized total',money(receipt.authorizedTotalKobo/100)],
    ['Created',new Date(receipt.createdAt).toLocaleString()],['Updated',new Date(receipt.updatedAt).toLocaleString()]];
  if(receipt.maskedDestination)lines.push(['Recipient',receipt.maskedDestination]);
  if(receipt.electricityToken)lines.push(['Electricity token',receipt.electricityToken]);
  if(receipt.fundingSource==='PAYOUT_WALLET') {
    lines.push(['Funding source','Personal funding account']);
    if(receipt.kind==='DEPOSIT')lines.push(['Gross incoming transfer',money(receipt.providerAmountKobo/100)],['Provider funding fee',money(receipt.providerFeeKobo/100)],['Net wallet credit',money(receipt.amountKobo/100)]);
  }
  lines.push(['Notes',receipt.note]);dialog.append(SavExTable(['Field','Value'],lines,'Payment receipt details'));
  const print=node('button','Print or save PDF','primary');print.type='button';print.addEventListener('click',()=>window.print());
  const close=node('button','Close','secondary');close.type='button';close.addEventListener('click',closePaymentReceipt);
  dialog.append(print,close);dialog.addEventListener('cancel',event=>{event.preventDefault();closePaymentReceipt();});
  document.body.append(dialog);document.body.classList.add('receipt-print');dialog.showModal();
}
async function loadPayments() {
  if(!token) return;
  const current=epoch;
  paymentCapabilities=await api('/money/capabilities');
  if(current!==epoch || !token) return;
  for(const option of $('payment-kind').options) option.disabled=option.value==='DEPOSIT'||option.value==='CONTRIBUTION'
    ? !paymentCapabilities.collections : option.value==='WITHDRAWAL'?!paymentCapabilities.transfers:!paymentCapabilities.bills;
  if(paymentCapabilities.mode==='LIVE') $('payment-kind').value='DEPOSIT';
  $('payment-readiness').replaceChildren();
  const funding=paymentCapabilities.collections?'Wallet funding is available.':
    'Wallet funding: '+(paymentCapabilities.fundingBlockers||['Awaiting activation']).join('. ')+'.';
  $('payment-readiness').append(node('p',funding));
  $('payment-readiness').append(node('p',paymentCapabilities.transfers?'Bank withdrawals are available.':
    (paymentCapabilities.withdrawalBlockers || ['Bank withdrawals and transfers are awaiting activation or account approval.']).join(' · ')));
  $('payment-readiness').append(node('p',paymentCapabilities.bills?'Bill payments are enabled. Provider availability is checked when you select a service.':
    (paymentCapabilities.billBlockers||['Bill payments are awaiting activation or account approval.']).join(' · ')));
  $('payment-mode').textContent=paymentCapabilities.mode==='OFF'
    ? 'Payments are awaiting setup. No real money can move yet.'
    : paymentCapabilities.mode==='TEST' ? 'Test payments only. Use Flutterwave test details; these balances are not real money.' : 'Payments in Nigerian naira.';
  await changePaymentKind(); await loadPaymentHistory();
  if(user.status==='ACTIVE')await refreshCustomerTransferBalance();
  await loadPayoutWallet();
}
let paymentFundingSource='CHECKOUT';
async function loadPayoutWallet() {
  if(!token || !$('payout-wallet-panel'))return;
  const current=epoch;
  try {
    const wallet=await api('/money/payout-wallet');if(current!==epoch || !token)return;
    $('payout-wallet-panel').hidden=!wallet.enabled && wallet.state==='NOT_CREATED';
    $('payout-wallet-status').textContent=wallet.mode+' · '+wallet.state.replaceAll('_',' ')+' · '+(wallet.note||'Funding is verified before it becomes available.');
    const rows=[['Account name',wallet.accountName||'Not issued'],['Bank',wallet.bankName||'Not issued'],['Account number',wallet.accountNumber||'Not issued'],
      ['Verified customer balance',money(wallet.balanceKobo/100)],['Reserved withdrawals',money(wallet.reservedKobo/100)],['Available customer balance',money(wallet.availableKobo/100)],
      ['Provider available balance',wallet.providerAvailableKobo==null?'Not confirmed':money(wallet.providerAvailableKobo/100)],['Last provider check',wallet.checkedAt?new Date(wallet.checkedAt).toLocaleString():'Not checked']];
    $('payout-wallet-details').replaceChildren(SavExTable(['Detail','Value'],rows,'Personal funding account'));
    $('payout-wallet-create').hidden=!wallet.enabled || !['NOT_CREATED','ACCOUNT_PENDING'].includes(wallet.state);
    $('payout-wallet-refresh').hidden=!wallet.enabled || wallet.state==='NOT_CREATED';
    const option=$('withdrawal-source')?.querySelector('option[value="PAYOUT_WALLET"]');if(option)option.disabled=!wallet.enabled || wallet.state!=='ACTIVE';
    syncPaymentButton();
  }catch(error){if(current===epoch && token){$('payout-wallet-status').textContent=error.message;}}
}
$('payout-wallet-create')?.addEventListener('submit',event=>{
  event.preventDefault();const current=epoch;
  busy(event.currentTarget,async()=>{
    const password=$('payout-wallet-create').elements.password.value;
    try {await api('/money/payout-wallet',{method:'POST',body:JSON.stringify({password})});}
    finally{if(current===epoch){$('payout-wallet-create').elements.password.value='';await loadPayoutWallet();}}
  });
});
$('payout-wallet-refresh')?.addEventListener('click',()=>busy($('payout-wallet-panel'),async()=>{
  const current=epoch;await api('/money/payout-wallet/refresh',{method:'POST'});
  if(current!==epoch || !token)return;await loadPayoutWallet();notice('Funding verification requested. Refresh payments shortly to see the result.');
}));
$('withdrawal-source')?.addEventListener('change',()=>{
  paymentDraft=null;paymentKey=null;paymentSignature=null;$('money-form').elements.password.value='';
  if($('payment-confirm').open)$('payment-confirm').close();
  syncPaymentButton();
});
let customerTransferBalanceBusy=false;
async function refreshCustomerTransferBalance(){
  if(!token || customerTransferBalanceBusy || user?.status!=='ACTIVE')return;
  const current=epoch;customerTransferBalanceBusy=true;
  try {
    const result=await api('/money/transfer-balance');if(current!==epoch || !token)return;
    $('customer-transfer-balance').textContent='Withdrawal funding ('+result.mode+'): '+result.message+
      ' Checked '+new Date(result.checkedAt).toLocaleTimeString()+'.';
  }catch(error){if(current===epoch && token)$('customer-transfer-balance').textContent='Withdrawal funding could not be confirmed. '+error.message;}
  finally{customerTransferBalanceBusy=false;}
}
setInterval(()=>{if(token && !document.hidden)refreshCustomerTransferBalance();},60000);
function options(select,rows,valueField,labelField) {
  select.replaceChildren(new Option('Select',''));
  for(const row of rows) select.add(new Option(row[labelField]||row.name||row[valueField],row[valueField]));
}
async function changePaymentKind() {
  if($('withdrawal-source-label'))$('withdrawal-source-label').hidden=$('payment-kind').value!=='WITHDRAWAL';
  const requestVersion=++catalogueVersion;catalogueLoading=false;
  resetBankLookup();
  const kind=$('payment-kind').value, outgoing=kind!=='DEPOSIT', destination=outgoing&&kind!=='CONTRIBUTION', bills=!!billCategories[kind];
  paymentCatalog=[];
  $('payment-customer-label').textContent=kind==='ELECTRICITY'?'Meter number':kind==='CABLE'?'Decoder or smartcard number':kind==='INTERNET'?'Internet customer account':kind==='WITHDRAWAL'?'Bank account number':'Nigerian phone number';
  $('payment-provider-label').hidden=!destination; $('payment-destination-label').hidden=!destination;
  $('payment-password-label').hidden=!outgoing; $('payment-package-label').hidden=!bills;
  const form=$('money-form');
  form.elements.password.required=outgoing; form.elements.destination.required=destination;
  form.elements.serviceId.required=destination; form.elements.variationCode.required=bills;
  form.elements.amount.readOnly=false;
  $('bank-lookup-panel').hidden=kind!=='WITHDRAWAL';
  form.elements.destination.maxLength=kind==='WITHDRAWAL'?10:64;
  form.elements.destination.inputMode=kind==='WITHDRAWAL'?'numeric':'text';
  const enabled=paymentCapabilities && (['DEPOSIT','CONTRIBUTION'].includes(kind)?paymentCapabilities.collections:kind==='WITHDRAWAL'?paymentCapabilities.transfers:paymentCapabilities.bills);
  $('payment-submit').disabled=!enabled;
  if(kind==='WITHDRAWAL')syncPaymentButton();
  $('payment-provider').replaceChildren(new Option('Select',''));
  $('payment-package').replaceChildren(new Option('Select',''));
  $('payment-provider').disabled=false;$('payment-package').disabled=false;
  $('payment-catalogue-status').textContent='';$('payment-catalogue-retry').hidden=true;
  if(!enabled || !destination) return;
  const current=epoch;
  catalogueLoading=true;$('payment-provider').disabled=true;syncPaymentButton();
  $('payment-provider').replaceChildren(new Option('Loading providers…',''));
  $('payment-catalogue-status').textContent='Loading available providers…';
  try {
    const data=await api(kind==='WITHDRAWAL'?'/money/banks':'/money/billers?category='+billCategories[kind]);
    if(current!==epoch || !token || $('payment-kind').value!==kind || requestVersion!==catalogueVersion)return;
    if(!Array.isArray(data)||!data.length)throw new Error('No providers were returned for this service. Contact support to confirm availability.');
    options($('payment-provider'),data,kind==='WITHDRAWAL'?'code':'biller_code','name');
    $('payment-provider').disabled=false;$('payment-catalogue-status').textContent='Choose a provider'+(bills?' to load its packages.':'.');
  }catch(error){
    if(current!==epoch || !token || requestVersion!==catalogueVersion)return;
    $('payment-provider').replaceChildren(new Option('Providers unavailable',''));
    $('payment-catalogue-status').textContent=error.message;$('payment-catalogue-retry').hidden=false;
  }finally{if(current===epoch && requestVersion===catalogueVersion){catalogueLoading=false;syncPaymentButton();}}
}
$('payment-catalogue-retry')?.addEventListener('click',()=>changePaymentKind().catch(error=>notice(error.message,true)));
$('payment-kind').addEventListener('change',()=>changePaymentKind().catch(e=>notice(e.message,true)));
document.querySelectorAll('[data-payment-kind]').forEach(button=>button.addEventListener('click',()=>{
  const select=$('payment-kind');
  const option=Array.from(select.options).find(item=>item.value===button.dataset.paymentKind);
  if(!option || option.disabled || select.disabled){notice('This service is currently unavailable. Check the availability details above.',true);return;}
  select.value=option.value;
  select.dispatchEvent(new Event('change'));
  select.focus();
}));
$('payment-provider').addEventListener('change',async()=>{
  scheduleBankLookup();
  const kind=$('payment-kind').value, provider=$('payment-provider').value, current=epoch;
  paymentCatalog=[]; $('payment-package').replaceChildren(new Option('Select',''));
  $('money-form').elements.amount.readOnly=false;
  if(!billCategories[kind] || !provider) return;
  const requestVersion=++catalogueVersion;catalogueLoading=true;$('payment-package').disabled=true;syncPaymentButton();
  $('payment-catalogue-status').textContent='Loading available packages…';
  try {
    const catalog=await api('/money/items?biller='+encodeURIComponent(provider));
    if(current!==epoch || !token || $('payment-kind').value!==kind || $('payment-provider').value!==provider || requestVersion!==catalogueVersion) return;
    if(!Array.isArray(catalog)||!catalog.length)throw new Error('No packages are available for this provider. Choose another provider or contact support.');
    paymentCatalog=catalog;
    options($('payment-package'),paymentCatalog,'item_code','name');
    $('payment-package').disabled=false;$('payment-catalogue-status').textContent='Choose a package and confirm the customer details.';
  } catch(e) { if(current===epoch && requestVersion===catalogueVersion){$('payment-catalogue-status').textContent=e.message;$('payment-catalogue-retry').hidden=false;} }
  finally{if(current===epoch && requestVersion===catalogueVersion){catalogueLoading=false;syncPaymentButton();}}
});
$('payment-package').addEventListener('change',()=>{
  const item=paymentCatalog.find(row=>row.item_code===$('payment-package').value);
  const fixed=item && Number(item.amount)>0;
  $('money-form').elements.amount.readOnly=!!fixed;
  if(fixed) $('money-form').elements.amount.value=item.amount;
  else $('money-form').elements.amount.value='';
  if(item?.label_name)$('payment-customer-label').textContent=item.label_name;
  syncPaymentButton();
});
$('payments-refresh').addEventListener('click',()=>busy($('money-form'),openDashboard));
$('money-form').addEventListener('submit',event=>{
  event.preventDefault();
  busy(event.currentTarget,async()=>{
    const f=$('money-form').elements, kind=f.kind.value, amount=Math.round(Number(f.amount.value)*100);
    if(!Number.isSafeInteger(amount)||amount<100) throw new Error('Enter a valid amount.');
    let recipient=f.destination.value.trim(), label=recipient;
    if(kind==='WITHDRAWAL') {
      if(!bankDetailsConfirmed())throw new Error('Wait for the account holder name to be confirmed before reviewing this withdrawal.');
      const lookupVersion=bankLookupVersion;
      const bank=await api('/money/resolve-bank',{method:'POST',body:JSON.stringify({accountNumber:recipient,bankCode:f.serviceId.value})});
      if(lookupVersion!==bankLookupVersion || !bankDetailsConfirmed() || bank.account_number!==recipient || bank.account_name?.trim()!==verifiedBank?.name)throw new Error('Recipient details changed. Check the account again before continuing.');
      label=bank.account_name+' · '+recipient;
    }
    paymentDraft={kind,amountKobo:amount,currency:'NGN',destination:['DEPOSIT','CONTRIBUTION'].includes(kind)?null:recipient,
      serviceId:['DEPOSIT','CONTRIBUTION'].includes(kind)?null:f.serviceId.value,groupId:kind==='CONTRIBUTION'?contributionGroup?.id:null,cycleNumber:kind==='CONTRIBUTION'?contributionGroup?.currentCycle+1:null,variationCode:billCategories[kind]?f.variationCode.value:null,
      password:kind==='DEPOSIT'?null:f.password.value};
    if(billCategories[kind]) {
      const quote=await api('/money/validate-bill',{method:'POST',body:JSON.stringify({...paymentDraft,password:null})});
      label=quote.customerName?quote.customerName+' · '+quote.customer:quote.customer;
    }
    const feeQuote=await api('/money/quote',{method:'POST',body:JSON.stringify({...paymentDraft,password:null})});
    paymentDraft.applicationFeeKobo=feeQuote.applicationFeeKobo;
    paymentFundingSource=kind==='WITHDRAWAL'?($('withdrawal-source')?.value||'CHECKOUT'):'CHECKOUT';
    const signature=JSON.stringify({...paymentDraft,password:null,fundingSource:paymentFundingSource});
    if(signature!==paymentSignature) { paymentKey=crypto.randomUUID(); paymentSignature=signature; }
    $('payment-summary').textContent=f.kind.selectedOptions[0].text+' · '+money(amount/100)+(label?' · '+label:'')+
      (f.serviceId.value?' · '+f.serviceId.selectedOptions[0].text:'')+
      (f.variationCode.value?' · '+f.variationCode.selectedOptions[0].text:'');
    if(kind==='CONTRIBUTION') $('payment-summary').textContent='Contribute '+money(amount/100)+' to '+contributionGroup.name+', cycle '+(contributionGroup.currentCycle+1)+'.';
    $('payment-summary').textContent+=' · SavEx fee '+money(feeQuote.applicationFeeKobo/100)+' · Total '+money(feeQuote.totalDebitKobo/100);
    if(kind==='WITHDRAWAL')$('payment-summary').textContent+=' · From '+(paymentFundingSource==='PAYOUT_WALLET'?'personal funding account':'checkout wallet');
    $('payment-confirm').showModal();
  });
});
$('payment-cancel').addEventListener('click',()=>{$('payment-confirm').close();paymentDraft=null;$('money-form').elements.password.value='';});
$('payment-send').addEventListener('click',async()=>{
  if(!paymentDraft || !token) return;
  $('payment-send').disabled=true;
  let recorded=false;
  try {
    if(paymentDraft.kind==='DEPOSIT')await loadCheckoutLibrary();
    const path=paymentDraft.kind==='WITHDRAWAL' && paymentFundingSource==='PAYOUT_WALLET'?'/money/payout-wallet/withdrawals':'/money/orders';
    const result=await api(path,{method:'POST',headers:{'Idempotency-Key':paymentKey},body:JSON.stringify(paymentDraft)});
    recorded=true;
    paymentKey=null;paymentSignature=null;
    notice('Payment recorded: '+result.reference+'. Refresh payments to check its progress.');
    $('payment-confirm').close();await loadPaymentHistory();await refresh();
    await loadPayoutWallet();
    if(result.kind==='DEPOSIT' && ['QUEUED','PENDING'].includes(result.status))await openInlineCheckout(result.id);
  } catch(e) { notice(e.message+(recorded?' Your order is saved. Refresh payment history; do not create a second order.':e.status>=400 && e.status<500?'':' The outcome may be uncertain. Check recent payments before retrying with the same details.'),true);$('payment-confirm').close(); }
  finally {paymentDraft=null;$('money-form').elements.password.value='';$('payment-send').disabled=false;}
});

let checkoutLibraryPromise=null,activeCheckout=null,checkoutWatch=0;
function loadCheckoutLibrary() {
  if(typeof window.FlutterwaveCheckout==='function')return Promise.resolve();
  if(checkoutLibraryPromise)return checkoutLibraryPromise;
  checkoutLibraryPromise=new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src='https://checkout.flutterwave.com/v3.js';script.async=true;
    const timeout=setTimeout(()=>fail(),20000);
    function fail(){clearTimeout(timeout);script.remove();checkoutLibraryPromise=null;reject(new Error('Secure payment window could not load. Check your connection and try again.'));}
    script.onerror=fail;script.onload=()=>{clearTimeout(timeout);if(typeof window.FlutterwaveCheckout==='function')resolve();else fail();};
    document.head.append(script);
  });
  return checkoutLibraryPromise;
}
async function watchCheckout(id,current) {
  const watch=++checkoutWatch;
  for(let attempt=0;attempt<40;attempt++) {
    if(current!==epoch || !token || watch!==checkoutWatch)return;
    await new Promise(resolve=>setTimeout(resolve,3000));
    if(current!==epoch || !token || watch!==checkoutWatch)return;
    try {
      const entries=await api('/money/orders');const entry=entries.find(row=>row.id===id);
      if(current!==epoch || !token || watch!==checkoutWatch)return;
      if(!entry || !['QUEUED','SUBMITTING','PENDING'].includes(entry.status)) {
        await loadPaymentHistory();await refresh();
        notice(entry?.status==='SUCCESS'?'Payment confirmed. Your wallet balance has been updated.':
          entry?.status==='FAILED'?'Payment was not completed. Your wallet was not credited.':
          'Payment needs review. Check recent payments before making another payment.',entry?.status!=='SUCCESS');
        return;
      }
    } catch(error){if(current===epoch && token)notice(error.message,true);return;}
  }
  if(current===epoch && token)notice('Payment is still awaiting confirmation. Refresh payments later; do not pay again.');
}
async function openInlineCheckout(id) {
  if(activeCheckout)throw new Error('A payment window is already open. Complete or close it first.');
  const current=epoch,checkoutState={id,modal:null};activeCheckout=checkoutState;
  try {
    await loadCheckoutLibrary();
    const config=await api('/money/orders/'+id+'/checkout',{method:'POST'});
    if(current!==epoch || !token){activeCheckout=null;return;}
    let finished=false;
    const finish=()=>{
      if(finished)return;finished=true;
      if(activeCheckout===checkoutState)activeCheckout=null;
      if(current!==epoch || !token)return;
      notice('Checking payment with Flutterwave. Your balance changes only after backend confirmation.');
      api('/money/orders/'+id+'/checkout/check',{method:'POST'}).then(()=>watchCheckout(id,current)).catch(error=>notice(error.message,true));
    };
    checkoutState.modal=window.FlutterwaveCheckout({...config,
      customizations:{title:'SavEx',description:'Wallet funding'},
      callback:()=>{finish();checkoutState.modal?.close();},
      onclose:finish
    });
  } catch(error){activeCheckout=null;throw error;}
}

function syncPaymentButton() {
  const kind=$('payment-kind').value;
  $('payment-submit').disabled=!paymentCapabilities || !(kind==='DEPOSIT'||kind==='CONTRIBUTION'
    ? paymentCapabilities.collections : kind==='WITHDRAWAL'?paymentCapabilities.transfers:paymentCapabilities.bills);
  if(kind==='WITHDRAWAL' && !bankDetailsConfirmed())$('payment-submit').disabled=true;
  if(kind==='WITHDRAWAL' && $('withdrawal-source')?.value==='PAYOUT_WALLET' && $('withdrawal-source')?.selectedOptions?.[0]?.disabled)$('payment-submit').disabled=true;
  if(catalogueLoading || (billCategories[kind] && (!$('payment-provider').value || !$('payment-package').value)))$('payment-submit').disabled=true;
}
let assessmentRefreshInProgress=false;
setInterval(async()=>{
  if(!token || !user || document.hidden || assessmentRefreshInProgress)return;
  assessmentRefreshInProgress=true;const current=epoch;
  try {
    const report=await api('/verification/account-assessment');
    if(current!==epoch || !token)return;
    if(assessmentSignature(report)!==lastAssessmentSignature)await openDashboard();
  } catch(error) {if(token)notice(error.message,true);}
  finally {assessmentRefreshInProgress=false;}
},30000);

let assistantProposal=null;
function resetAssistants() {
  assistantProposal=null;
  ['assistant-savings-result','assistant-transaction-result','assistant-payment-result'].forEach(id=>$(id)?.replaceChildren());
  ['assistant-savings','assistant-transaction','assistant-payment'].forEach(id=>$(id)?.reset());
  if($('assistant-reference'))$('assistant-reference').replaceChildren(new Option('Load your transactions first',''));
  if($('assistant-payment'))$('assistant-payment').elements.bankCode.replaceChildren(new Option('Select a bank',''));
  if($('assistant-use-proposal'))$('assistant-use-proposal').hidden=true;
  if($('assistant-bank-fields'))$('assistant-bank-fields').hidden=true;
}
function assistantAmount(value) {
  const amount=Math.round(Number(value)*100);
  if(!Number.isSafeInteger(amount) || amount<0)throw new Error('Enter a valid amount.');
  return amount;
}
async function assistantWork(button,output,work) {
  const current=epoch; if(!token)return;
  button.disabled=true;output.textContent='Checking…';
  try {await work(current);}catch(error){if(current===epoch && token)output.textContent=error.message;}
  finally {button.disabled=false;}
}
function assistantReport(output,rows,message) {
  output.replaceChildren(SavExTable(['Detail','Value'],rows,'Assistant result','No result'),node('p',message));
}
$('assistant-savings')?.addEventListener('submit',event=>{
  event.preventDefault();const f=event.currentTarget.elements;
  assistantWork(event.currentTarget.querySelector('button'),$('assistant-savings-result'),async current=>{
    const result=await api('/assistants/savings',{method:'POST',body:JSON.stringify({goalKobo:assistantAmount(f.goal.value),savedKobo:assistantAmount(f.saved.value),periods:Number(f.periods.value),frequency:f.frequency.value})});
    if(current!==epoch || !token)return;
    assistantReport($('assistant-savings-result'),[['Remaining goal',money(result.remainingKobo/100)],['Suggested contribution',money(result.contributionKobo/100)],['Schedule',result.periods+' '+result.frequency.toLowerCase()+' contributions'],['Planned contributions total',money(result.plannedTotalKobo/100)]],result.message);
  });
});
$('assistant-history-load')?.addEventListener('click',event=>assistantWork(event.currentTarget,$('assistant-transaction-result'),async current=>{
  const rows=await api('/money/orders');if(current!==epoch || !token)return;
  $('assistant-reference').replaceChildren(new Option('Select a transaction',''),...rows.map(row=>new Option(row.kind+' · '+money(row.amountKobo/100)+' · '+row.status+' · '+row.reference,row.reference)));
  $('assistant-transaction-result').textContent=rows.length?'Select a transaction to see its explanation.':'No transactions recorded yet.';
}));
$('assistant-transaction')?.addEventListener('submit',event=>{
  event.preventDefault();const reference=event.currentTarget.elements.reference.value;
  assistantWork(event.currentTarget.querySelector('button'),$('assistant-transaction-result'),async current=>{
    const result=await api('/assistants/transactions/'+encodeURIComponent(reference));if(current!==epoch || !token)return;
    assistantReport($('assistant-transaction-result'),[['Reference',result.reference],['Service',result.kind],['Recorded status',result.status],['Amount',money(result.amountKobo/100)],['Total including SavEx fee',money(result.totalDebitKobo/100)],['Status meaning',result.explanation],['Next step',result.nextStep]],'Based on SavEx records checked at '+new Date(result.checkedAt).toLocaleString()+'.');
  });
});
$('assistant-payment')?.addEventListener('change',()=>{
  const f=$('assistant-payment').elements,withdrawal=f.kind.value==='WITHDRAWAL';
  $('assistant-bank-fields').hidden=!withdrawal;f.bankCode.required=withdrawal;f.accountNumber.required=withdrawal;
  assistantProposal=null;$('assistant-use-proposal').hidden=true;$('assistant-payment-result').replaceChildren();
});
$('assistant-payment')?.addEventListener('input',()=>{assistantProposal=null;$('assistant-use-proposal').hidden=true;});
$('assistant-banks-load')?.addEventListener('click',event=>assistantWork(event.currentTarget,$('assistant-payment-result'),async current=>{
  const banks=await api('/money/banks');if(current!==epoch || !token)return;
  $('assistant-payment').elements.bankCode.replaceChildren(new Option('Select a bank',''),...banks.map(bank=>new Option(bank.name,bank.code)));
  $('assistant-payment-result').textContent='Select your bank and enter the account number.';
}));
$('assistant-payment')?.addEventListener('submit',event=>{
  event.preventDefault();const form=event.currentTarget;
  assistantProposal=null;$('assistant-use-proposal').hidden=true;
  const f=form.elements,payload={kind:f.kind.value,amountKobo:assistantAmount(f.amount.value),bankCode:f.kind.value==='WITHDRAWAL'?f.bankCode.value:null,accountNumber:f.kind.value==='WITHDRAWAL'?f.accountNumber.value:null};
  const signature=JSON.stringify(payload);
  assistantWork(form.querySelector('button[type=submit]'),$('assistant-payment-result'),async current=>{
    const result=await api('/assistants/payments/prepare',{method:'POST',body:signature});
    if(current!==epoch || !token)return;
    // Do not revive a proposal if its fields changed during the request.
    const now=JSON.stringify({kind:f.kind.value,amountKobo:assistantAmount(f.amount.value),bankCode:f.kind.value==='WITHDRAWAL'?f.bankCode.value:null,accountNumber:f.kind.value==='WITHDRAWAL'?f.accountNumber.value:null});
    if(now!==signature){$('assistant-payment-result').textContent='Details changed. Prepare a new proposal.';return;}
    assistantProposal=result;
    const rows=[['Service',result.kind],['Amount',money(result.amountKobo/100)],['SavEx fee',money(result.applicationFeeKobo/100)],['Total wallet debit or funding payment',money(result.totalDebitKobo/100)]];
    if(result.recipientName)rows.push(['Recipient',result.recipientName],['Account',result.accountNumber]);
    assistantReport($('assistant-payment-result'),rows,result.message);$('assistant-use-proposal').hidden=false;
  });
});
$('assistant-use-proposal')?.addEventListener('click',event=>assistantWork(event.currentTarget,$('assistant-payment-result'),async current=>{
  const proposal=assistantProposal;if(!proposal)return;
  await loadPayments();if(current!==epoch || !token || assistantProposal!==proposal)return;
  const option=Array.from($('payment-kind').options).find(item=>item.value===proposal.kind);
  if(!option || option.disabled)throw new Error('This service is currently unavailable. Check payment availability.');
  $('payment-kind').value=proposal.kind;await changePaymentKind();
  if(current!==epoch || !token || assistantProposal!==proposal)return;
  const f=$('money-form').elements;
  if(proposal.kind==='WITHDRAWAL' && !Array.from(f.serviceId.options).some(item=>item.value===proposal.bankCode))throw new Error('This bank is no longer available. Prepare a new proposal.');
  f.amount.value=(proposal.amountKobo/100).toFixed(2);f.destination.value=proposal.accountNumber || '';f.serviceId.value=proposal.bankCode || '';f.password.value='';
  if(proposal.kind==='WITHDRAWAL')scheduleBankLookup(true);
  location.hash='payments';$('payment-kind').focus();
  $('assistant-payment-result').textContent='Proposal copied to Payments. Review the details and confirm there. No money has been sent.';
  notice('Payment proposal prepared. Check the details, enter your password if required, then choose Review payment.');
}));
