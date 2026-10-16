'use strict';
const $=id=>document.getElementById(id);
let token=null,page=0,assessments=[];
function notice(text){$('notice').textContent=text;$('notice').hidden=!text;}
function logout(){$("payout-recovery-form")?.reset();$("payout-recovery-status")?.replaceChildren();payoutReportPage=0;["payout-report","payout-funding-events","payout-report-status"].forEach(id=>$(id)?.replaceChildren());clearAiReports();SavExSession.clear();token=null;assessments=[];walletAuditPage=0;transactionPage=0;['customer-report-table','transaction-summary','transaction-report','transaction-sync-status'].forEach(id=>$(id).replaceChildren());$('wallet-audit-report').replaceChildren();$('payment-exceptions').replaceChildren();$('wallet-audit-status').textContent='';$('transfer-balance-status').textContent='Balance has not been checked.';$('assessment-report').replaceChildren();$('login-panel').hidden=false;$('setup-panel').hidden=true;$('review-panel').hidden=true;$('logout').hidden=true;$('reports').replaceChildren();$('customers').replaceChildren();['login-form','register-form','setup-form','code-form','review-form'].forEach(id=>$(id).reset());}
async function api(path,body){
  if(token && !path.startsWith('/auth/'))await SavExSession.ensure();
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),30000);
  try {const response=await fetch('/api/v1'+path,{method:body?'POST':'GET',cache:'no-store',headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:controller.signal});
    const result=await response.json().catch(()=>({}));
    if(!response.ok || result.success===false){if(response.status===401 && token)logout();const error=new Error(result.message||'This request could not be completed.');error.status=response.status;throw error;}return result.data;
  } finally {clearTimeout(timeout);}
}
async function busy(form,work){const buttons=[...form.querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);try{await work();}catch(e){notice(e.message);}finally{buttons.forEach(b=>b.disabled=false);}}
async function refresh(){
  const selected=$('customers').value;
  const [customers,reports]=await Promise.all([api('/admin/customer-reviews/assessments?page='+page+'&status='+encodeURIComponent($('customer-status').value)),api('/admin/customer-reviews')]);
  assessments=customers.content;
  $('customers').replaceChildren();const empty=document.createElement('option');empty.value='';empty.textContent='Select a customer';$('customers').append(empty);
  customers.content.forEach(c=>{const option=document.createElement('option');option.value=c.customerId;option.textContent=c.fullName+' — '+c.email+' — '+c.reviewState.replaceAll('_',' ').toLowerCase();$('customers').append(option);});
  if(assessments.some(c=>c.customerId===selected))$('customers').value=selected;
  showAssessment();
  await refreshTransferBalance();
  await refreshWalletAudit();
  await refreshTransactions();
  $('queue-page').textContent='Customer page '+(page+1);$('previous').disabled=page===0;$('next').disabled=customers.last;
  $('customer-report-table').replaceChildren(SavExTable(['Customer','Email','Account','Identity','Review state','Action'],customers.content.map(c=>{
    const choose=document.createElement('button');choose.type='button';choose.className='secondary';choose.textContent='Review';choose.addEventListener('click',()=>{$('customers').value=c.customerId;showAssessment();});
    return [c.fullName,c.email,c.accountStatus,c.identityStatus,c.reviewState.replaceAll('_',' '),choose];}),'Customer account reports'));
  $('reports').replaceChildren(SavExTable(['Decision','Environment','Method','Customer ID','Reviewer ID','Recorded','Evidence reference','Findings'],reports.map(r=>
    [r.decision,r.providerMode,r.identityMethod,r.userId,r.reviewerId,new Date(r.createdAt).toLocaleString(),r.evidenceReference,r.reviewNotes||'Legacy review']),'Recorded identity reviews'));
}
function showAssessment(){
  const report=assessments.find(c=>c.customerId===$('customers').value);$('assessment-report').replaceChildren();
  if(!report)return;
  const rows=[['Report ID',report.reportId],['Customer ID',report.customerId],['Name',report.fullName],['Email',report.email],['Phone',report.phone],
    ['Registered',new Date(report.registeredAt).toLocaleString()],['Checked',new Date(report.checkedAt).toLocaleString()],['Account',report.accountStatus],['Identity',report.identityStatus],
    ['Automatic email-only approval',report.emailAccountApprovedAt?new Date(report.emailAccountApprovedAt).toLocaleString():'Not recorded']];
  $('assessment-report').append(SavExTable(['Field','Value'],rows,'Selected customer'),
    SavExTable(['Check','Result','Finding'],report.checks.map(c=>[c.name,c.passed?'Passed':'Needs attention',c.finding]),'Account checks'),
    SavExTable(['Funding blocker'],report.fundingBlockers.map(reason=>[reason]),'Funding readiness','No funding blockers recorded.'));
  $('review-form').elements.emailOwnershipChecked.checked=report.emailVerified;
}
let transferBalanceBusy=false;
async function refreshTransferBalance(){
  if(!token || transferBalanceBusy)return;
  transferBalanceBusy=true;const sessionToken=token;$('transfer-balance-refresh').disabled=true;
  try {
    const result=await api('/admin/payments/transfer-balance');if(token!==sessionToken)return;
    const format=value=>new Intl.NumberFormat('en-NG',{style:'currency',currency:'NGN'}).format(value/100);
    $('transfer-balance-status').replaceChildren(SavExTable(['Environment','Status','Available','Ledger','Checked','Notes'],[[result.mode,result.status,
      result.status==='AVAILABLE'?format(result.availableKobo):'Unavailable',result.status==='AVAILABLE'?format(result.ledgerKobo):'Unavailable',
      new Date(result.checkedAt).toLocaleString(),result.message]],'Merchant transfer balance'));
  }catch(error){if(token===sessionToken)$('transfer-balance-status').textContent='Balance could not be checked. '+error.message;}
  finally{transferBalanceBusy=false;$('transfer-balance-refresh').disabled=false;}
}
$('transfer-balance-refresh').addEventListener('click',refreshTransferBalance);
let walletAuditPage=0,walletAuditBusy=false;
async function refreshWalletAudit(){
  if(!token || walletAuditBusy)return;
  walletAuditBusy=true;const sessionToken=token,requestedPage=walletAuditPage;
  ['wallet-audit-refresh','wallet-audit-previous','wallet-audit-next'].forEach(id=>$(id).disabled=true);
  try{
    const [report,issues]=await Promise.all([api('/admin/reconciliation/wallets?page='+requestedPage),api('/admin/reconciliation/issues')]);
    if(token!==sessionToken)return;
    const format=value=>new Intl.NumberFormat('en-NG',{style:'currency',currency:'NGN'}).format(value/100);
    $('wallet-audit-status').textContent='Page '+(report.page+1)+' · '+report.totalWallets+' wallets · Checked '+new Date(report.checkedAt).toLocaleString();
    $('wallet-audit-report').replaceChildren(SavExTable(['Customer ID','Wallet ID','Balance','Reserved','Journal available','Journal reserved','Difference','Result'],report.content.map(row=>
      [row.customerId,row.walletId,format(row.walletBalanceKobo),format(row.reservedKobo),format(row.journalAvailableKobo),format(row.journalReservedKobo),format(row.differenceKobo),row.matched?'Matched':'Investigate']),'Wallet-to-journal reconciliation'));
    $('payment-exceptions').replaceChildren(SavExTable(['Order ID','Reason','Status','Since'],issues.map(issue=>[issue.orderId,issue.reason,issue.status,new Date(issue.createdAt).toLocaleString()]),'Open payment exceptions','No open payment exceptions recorded.'));
    $('wallet-audit-previous').disabled=requestedPage===0;$('wallet-audit-next').disabled=report.last;
  }catch(error){if(token===sessionToken)$('wallet-audit-status').textContent='Wallet check unavailable. '+error.message;}
  finally{walletAuditBusy=false;$('wallet-audit-refresh').disabled=false;}
}
$('wallet-audit-refresh').addEventListener('click',refreshWalletAudit);
$('wallet-audit-previous').addEventListener('click',()=>{walletAuditPage=Math.max(0,walletAuditPage-1);refreshWalletAudit();});
$('wallet-audit-next').addEventListener('click',()=>{walletAuditPage++;refreshWalletAudit();});
let transactionPage=0,transactionBusy=false;
function renderTransactions(report){
  const format=(value,currency)=>value==null?'—':/^[A-Z]{3}$/.test(currency||'')?new Intl.NumberFormat('en-NG',{style:'currency',currency}).format(value/100):(value/100)+' (currency unknown)';
  $('transaction-sync-status').textContent=report.mode+' · '+report.state.replaceAll('_',' ')+' · '+(report.from?report.from+' to '+report.to:'No date range yet')+
    ' · '+report.totalRows+' report rows · Page '+(report.page+1)+' · '+report.pagesRead+' provider pages'+
    (report.checkedAt?' · Compared '+new Date(report.checkedAt).toLocaleString():'')+' · '+report.message;
  $('transaction-summary').replaceChildren(SavExTable(['Result','Attempt/missing-record rows'],Object.entries(report.counts).map(([result,count])=>[result.replaceAll('_',' '),count]),'Transaction matching summary'));
  $('transaction-report').replaceChildren(SavExTable(['Reference','Flutterwave transaction ID','Saved attempt ID','Customer ID','SavEx status','Flutterwave status','Expected total','Provider amount','Expected currency','Provider currency','Result','Priority','Finding'],report.content.map(row=>
    [row.reference,row.providerId,row.savedProviderId,row.customerId,row.savexStatus,row.providerStatus,format(row.expectedKobo,row.expectedCurrency),format(row.providerKobo,row.currency),row.expectedCurrency,row.currency,
      row.result.replaceAll('_',' '),row.severity,row.finding]),'Flutterwave collection attempts','No imported records in this view. Check sync status and date range.'));
  $('transaction-previous').disabled=report.page===0;$('transaction-next').disabled=report.last;
  $('transaction-sync').disabled=report.state==='RUNNING';
}
async function refreshTransactions(sync=false){
  if(!token || transactionBusy)return;
  transactionBusy=true;const sessionToken=token;
  try{
    if(sync){await api('/admin/reconciliation/transactions/sync',{});transactionPage=0;}
    const report=await api('/admin/reconciliation/transactions?page='+transactionPage+'&filter='+encodeURIComponent($('transaction-filter').value));
    if(token!==sessionToken)return;renderTransactions(report);
  }catch(error){if(token===sessionToken)$('transaction-sync-status').textContent='Transaction report unavailable. '+error.message;}
  finally{transactionBusy=false;}
}
$('transaction-sync').addEventListener('click',()=>refreshTransactions(true));
$('transaction-refresh').addEventListener('click',()=>refreshTransactions());
$('transaction-filter').addEventListener('change',()=>{transactionPage=0;refreshTransactions();});
$('transaction-previous').addEventListener('click',()=>{if(!transactionBusy){transactionPage=Math.max(0,transactionPage-1);refreshTransactions();}});
$('transaction-next').addEventListener('click',()=>{if(!transactionBusy){transactionPage++;refreshTransactions();}});
setInterval(()=>{if(token && !document.hidden && !$('review-panel').hidden)refreshTransactions();},10000);
setInterval(()=>{if(token && !document.hidden && !$('review-panel').hidden)refreshTransferBalance();},60000);
$('customers').addEventListener('change',showAssessment);
$('customer-status').addEventListener('change',()=>{page=0;busy($('review-panel'),refresh);});
async function enter(data){token=data.accessToken;SavExSession.start(data,next=>{token=next.accessToken;},()=>{logout();notice('Your session has ended. Please sign in again.');});$('login-panel').hidden=true;$('logout').hidden=false;try{await refresh();$('review-panel').hidden=false;$('setup-panel').hidden=true;notice('Signed in to customer reviews.');}catch(e){if(e.status!==403 && e.status!==401)throw e;if(!token)return;$('setup-panel').hidden=false;notice('Verify your reviewer email, then complete first administrator setup.');}}
['login','register'].forEach(kind=>$(kind+'-form').addEventListener('submit',event=>{event.preventDefault();const reviewEventForm=event.currentTarget;busy(reviewEventForm,async()=>{const fields=Object.fromEntries(new FormData(reviewEventForm));const body=kind==='login'?{emailOrPhone:fields.email,password:fields.password}:fields;const data=await api('/auth/'+kind,body);reviewEventForm.reset();await enter(data);});}));
$('logout').addEventListener('click',async()=>{const revocation=SavExSession.signOut();logout();notice('Signing out…');try{await revocation;notice('Signed out.');}catch{notice('Signed out on this device. Server sign-out could not be confirmed; the session will expire automatically.');}});
$('send-code').addEventListener('click',()=>busy($('setup-panel'),async()=>{const result=await api('/verification/email/send',{});notice(result.message);}));
$('code-form').addEventListener('submit',event=>{event.preventDefault();const reviewEventForm=event.currentTarget;busy(reviewEventForm,async()=>{const result=await api('/verification/email/check',{code:reviewEventForm.elements.code.value.trim()});reviewEventForm.reset();notice(result.message);});});
$('setup-form').addEventListener('submit',event=>{event.preventDefault();const reviewEventForm=event.currentTarget;busy(reviewEventForm,async()=>{const fields=Object.fromEntries(new FormData(reviewEventForm));try{await api('/verification/admin-setup',fields);logout();notice('Administrator activated. Sign in again.');}finally{reviewEventForm.reset();}});} );
$('refresh').addEventListener('click',()=>busy($('review-panel'),refresh));
$('previous').addEventListener('click',()=>{page=Math.max(0,page-1);busy($('review-panel'),refresh);});
$('next').addEventListener('click',()=>{page++;busy($('review-panel'),refresh);});
$('review-form').addEventListener('submit',event=>{event.preventDefault();const reviewEventForm=event.currentTarget;busy(reviewEventForm,async()=>{const form=reviewEventForm,fields=Object.fromEntries(new FormData(form));const approve=fields.decision==='approve';if(approve && (!form.elements.emailOwnershipChecked.checked || !form.elements.identityMatched.checked))throw new Error('Complete both evidence checks before approval.');
  if(!confirm('Record this '+(approve?'approval':'rejection')+' for the selected customer?'))return;
  try{await api('/admin/customer-reviews',{...fields,identityMethod:'DOCUMENT_REVIEW',emailOwnershipChecked:form.elements.emailOwnershipChecked.checked,identityMatched:form.elements.identityMatched.checked,phoneOwnershipChecked:false,approve});form.reset();await refresh();notice('Evidence report and decision recorded.');}finally{form.elements.password.value='';}
});});

let aiRun=null,aiEpoch=0;
function clearAiReports(){aiEpoch++;aiRun=null;['ai-result','ai-lessons','ai-daily-reports','ai-runs','ai-status'].forEach(id=>$(id)?.replaceChildren());if($('ai-feedback'))$('ai-feedback').hidden=true;$('ai-form')?.reset();}
function aiTable(id,headers,rows,caption){$(id).replaceChildren(SavExTable(headers,rows,caption));}
function showAiRun(run){
  aiRun=run.id;const report=run.content;
  aiTable('ai-result',['Detail','Value'],[['Reference',report.reference],['Warning',report.warning],['Evidence checked',report.evidence.checkedAt],['Local status',report.evidence.localStatus],['Provider settlement recorded',report.evidence.providerSettlementRecorded?'Yes':'No'],['Bank receipt recorded',report.evidence.bankReceiptRecorded?'Yes':'No'],['Summary',report.analysis.summary],['Uncertainties',report.analysis.uncertainties],['Suggested next steps',report.analysis.nextSteps],['Model',report.model],['Policy',report.policyVersion]],'AI investigation advice');
  $('ai-feedback').hidden=false;
}
async function refreshAiReports(){
  const current=aiEpoch,session=token;if(!session)return;
  const [status,runs,lessons,daily]=await Promise.all([api('/admin/ai/status'),api('/admin/ai/reports?kind=RUN'),api('/admin/ai/reports?kind=LESSON'),api('/admin/ai/reports?kind=DAILY')]);
  if(current!==aiEpoch || !token || token!==session)return;
  $('ai-status').textContent=(status.enabled?'AI enabled':'AI disabled — configure the API key and enable it to generate analysis')+' · Model '+status.model+' · Daily request limit '+status.dailyRequestLimit+' · Daily feedback review '+(status.learningEnabled?'enabled':'disabled');
  aiTable('ai-runs',['Date','Reference','State','Action'],runs.map(run=>{
    const button=document.createElement('button');button.className='secondary';button.type='button';button.textContent='View investigation';button.addEventListener('click',()=>showAiRun(run));
    return [new Date(run.createdAt).toLocaleString(),run.content.reference,run.state,button];
  }),'Latest 100 AI investigations');
  aiTable('ai-lessons',['Day','Category','Feedback','Guidance','State','Reviewed at','Actions'],lessons.map(lesson=>{
    const actions=document.createElement('div');actions.className='report-actions';
    for(const approve of [true,false]){
      const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=approve?'Approve guidance':'Reject or revoke';
      button.addEventListener('click',()=>busy($('ai-panel'),async()=>{await api('/admin/ai/lessons/'+lesson.id+'/review',{approve});await refreshAiReports();}));actions.append(button);
    }
    return [lesson.content.day,lesson.content.category,lesson.content.feedbackCount,lesson.content.guidance,lesson.state,lesson.reviewedAt?new Date(lesson.reviewedAt).toLocaleString():'Not reviewed',actions];
  }),'Latest 100 learning proposals');
  aiTable('ai-daily-reports',['Day','Feedback count','Complete','Feedback breakdown','Finding'],daily.map(row=>[row.content.day,row.content.feedbackTotal,row.content.complete?'Yes':'Partial',JSON.stringify(row.content.feedbackCounts),row.content.message]),'Latest 100 daily feedback reviews');
}
$('ai-refresh')?.addEventListener('click',()=>busy($('ai-panel'),refreshAiReports));
$('ai-form')?.addEventListener('submit',event=>{
  event.preventDefault();const reference=event.currentTarget.elements.reference.value.trim(),current=aiEpoch,session=token;
  busy($('ai-panel'),async()=>{aiRun=null;$('ai-feedback').hidden=true;$('ai-result').textContent='Collecting evidence and generating analysis…';
    const run=await api('/admin/ai/investigate',{reference});if(current!==aiEpoch || !token || token!==session)return;
    showAiRun(run);await refreshAiReports();
  });
});
$('ai-feedback')?.addEventListener('submit',event=>{event.preventDefault();if(!aiRun)return;const id=aiRun,rating=event.currentTarget.elements.rating.value;
  busy($('ai-panel'),async()=>{await api('/admin/ai/runs/'+id+'/feedback',{rating});$('ai-feedback').hidden=true;notice('Feedback saved for the next daily review.');});
});
$('ai-daily')?.addEventListener('click',()=>busy($('ai-panel'),async()=>{await api('/admin/ai/daily-review',{});await refreshAiReports();}));let payoutReportPage=0,payoutReportBusy=false;
async function refreshPayoutReport(){
  if(!token || payoutReportBusy)return;
  const sessionToken=token;payoutReportBusy=true;
  try {
    const report=await api('/admin/payout-wallets?page='+payoutReportPage);if(token!==sessionToken)return;
    const format=value=>value==null?'Not confirmed':new Intl.NumberFormat('en-NG',{style:'currency',currency:'NGN'}).format(value/100);
    $('payout-report-status').textContent='Page '+(report.page+1)+' · '+report.total+' funding accounts';
    $('payout-report').replaceChildren(SavExTable(['Customer','Mode','State','Account','Customer balance','Reserved','Journal available','Journal reserved','Result','Provider available','Last check','Note'],report.content.map(row=>[
      row.customerId,row.mode,row.state,row.maskedAccount||'Not issued',format(row.balanceKobo),format(row.reservedKobo),format(row.journalAvailableKobo),format(row.journalReservedKobo),row.matched?'Matched':'Investigate',format(row.providerAvailableKobo),row.checkedAt?new Date(row.checkedAt).toLocaleString():'Not checked',row.note||'']),'Payout wallet reconciliation'));
    $('payout-funding-events').replaceChildren(SavExTable(['Transfer ID','Mode','Verification','Received'],report.fundingEvents.map(row=>[row.transferId,row.mode,row.state,new Date(row.createdAt).toLocaleString()]),'Latest funding verification events'));
    $('payout-report-previous').disabled=report.page===0;$('payout-report-next').disabled=report.last;
  }catch(error){if(token===sessionToken)$('payout-report-status').textContent=error.message;}
  finally{payoutReportBusy=false;}
}
$('payout-report-refresh')?.addEventListener('click',refreshPayoutReport);
$('payout-report-previous')?.addEventListener('click',()=>{payoutReportPage=Math.max(0,payoutReportPage-1);refreshPayoutReport();});
$('payout-report-next')?.addEventListener('click',()=>{payoutReportPage++;refreshPayoutReport();});
$('payout-recovery-form')?.addEventListener('submit',async event=>{
  event.preventDefault();const sessionToken=token,form=event.currentTarget,button=form.querySelector('button');button.disabled=true;
  try {const body=Object.fromEntries(new FormData(form));const result=await api('/money/payout-wallet/recover',{method:'POST',body:JSON.stringify(body)});if(token!==sessionToken)return;$('payout-recovery-status').textContent=result;form.reset();await refreshPayoutReport();}
  catch(error){if(token===sessionToken)$('payout-recovery-status').textContent=error.message;}
  finally{if(token===sessionToken)form.elements.password.value='';button.disabled=false;}
});
