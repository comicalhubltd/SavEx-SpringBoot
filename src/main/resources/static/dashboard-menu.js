'use strict';
(() => {
  const themeButtons=Array.from(document.querySelectorAll('[data-theme-toggle]'));
  function applyTheme(dark){
    document.documentElement.dataset.theme=dark?'dark':'light';
    themeButtons.forEach(button=>{button.textContent=dark?'Light mode':'Dark mode';button.setAttribute('aria-pressed',String(dark));});
  }
  let savedTheme=null;try{savedTheme=localStorage.getItem('savex-theme');}catch{}
  applyTheme(savedTheme==='dark');
  themeButtons.forEach(button=>button.addEventListener('click',()=>{
    const dark=document.documentElement.dataset.theme!=='dark';applyTheme(dark);
    try{localStorage.setItem('savex-theme',dark?'dark':'light');}catch{}
  }));
  const nav = document.querySelector('.dashboard-nav');
  if (!nav) return;
  const links = Array.from(nav.querySelectorAll('[data-view]'));
  const dashboard = document.getElementById('dashboard');
  if (dashboard) {
    dashboard.querySelectorAll(':scope > .section-heading, :scope > .summary').forEach(el => el.dataset.dashboardView = 'overview');
    const account = document.getElementById('email-panel');
    const status = document.createElement('section');
    status.className = 'card'; status.dataset.dashboardView = 'account';
    const heading = document.createElement('h2'); heading.textContent = 'Your account';
    const text = document.createElement('p');
    const update = () => { text.textContent = account.hidden ? 'Your email is verified. Your account and payment availability are checked automatically.' : 'Complete email verification below. Account restrictions and payment availability appear in your dashboard messages.'; };
    status.append(heading, text); account.before(status); update();
    new MutationObserver(update).observe(account, {attributes:true, attributeFilter:['hidden']});
  }
  const panels = Array.from(document.querySelectorAll('[data-dashboard-view]'));
  function show(focus) {
    const requested = location.hash.slice(1);
    const selected = links.find(link => link.dataset.view === requested) || links[0];
    panels.forEach(panel => panel.classList.toggle('menu-panel-inactive', panel.dataset.dashboardView !== selected.dataset.view));
    links.forEach(link => {
      if (link === selected) link.setAttribute('aria-current','page');
      else link.removeAttribute('aria-current');
    });
    if (focus) {
      const panel = panels.find(item => item.dataset.dashboardView === selected.dataset.view && !item.hidden);
      if (panel) {panel.tabIndex = -1; panel.focus({preventScroll:true});}
      nav.scrollIntoView({block:'start'});
    }
  }
  links.forEach(link => link.addEventListener('click', event => {
    event.preventDefault();
    history.replaceState(null,'',link.getAttribute('href'));
    show(true);
  }));
  // Customer review buttons already select the customer in the existing form.
  const queue = document.getElementById('customer-report-table');
  if (queue) queue.addEventListener('click', event => {
    if (event.target.closest('button')) {
      history.replaceState(null,'','#identity'); show(true);
    }
  });
  window.addEventListener('hashchange',()=>show(false));
  show(false);
})();
