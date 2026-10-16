'use strict';
window.SavExTable=(headers,rows,caption,empty='No records to display.')=>{
  const wrapper=document.createElement('div');wrapper.className='report-table-wrap';wrapper.tabIndex=0;
  wrapper.setAttribute('role','region');wrapper.setAttribute('aria-label',caption);
  const table=document.createElement('table');table.className='report-table';
  const title=document.createElement('caption');title.textContent=caption;table.append(title);
  const head=document.createElement('thead'),heading=document.createElement('tr');
  headers.forEach(text=>{const cell=document.createElement('th');cell.scope='col';cell.textContent=text;heading.append(cell);});head.append(heading);table.append(head);
  const body=document.createElement('tbody');
  rows.forEach(values=>{const row=document.createElement('tr');values.forEach(value=>{const cell=document.createElement('td');
    if(value && typeof value==='object' && value.nodeType===1)cell.append(value);else cell.textContent=value==null?'—':String(value);
    row.append(cell);});body.append(row);});
  if(!rows.length){const row=document.createElement('tr'),cell=document.createElement('td');cell.colSpan=headers.length;cell.textContent=empty;row.append(cell);body.append(row);}
  table.append(body);wrapper.append(table);return wrapper;
};
