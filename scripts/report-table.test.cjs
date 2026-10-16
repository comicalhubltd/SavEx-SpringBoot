const {test}=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
function tableClient(){
  const createElement=tag=>({tag,nodeType:1,children:[],append(...children){this.children.push(...children);},setAttribute(name,value){this[name]=value;}});
  const context=vm.createContext({window:{},document:{createElement}});
  vm.runInContext(fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/report-table.js'),'utf8'),context);
  return {render:context.window.SavExTable,createElement};
}
test('reports use semantic tables, text-only untrusted values, and working action elements',()=>{
  const {render,createElement}=tableClient(),button=createElement('button');
  const wrapper=render(['Reference','Action'],[['<img onerror=bad()>',button]],'Transactions');
  const table=wrapper.children[0],head=table.children[1],body=table.children[2];
  assert.equal(wrapper.tabIndex,0);assert.equal(table.tag,'table');assert.equal(table.children[0].textContent,'Transactions');
  assert.equal(head.children[0].children[0].scope,'col');assert.equal(body.children[0].children[0].textContent,'<img onerror=bad()>');
  assert.equal(body.children[0].children[1].children[0],button);
});
test('empty and missing data remain explicit table cells',()=>{
  const {render}=tableClient();const empty=render(['A','B'],[],'Report','No records');
  assert.equal(empty.children[0].children[2].children[0].children[0].colSpan,2);
  const values=render(['A'],[[null]],'Report');assert.equal(values.children[0].children[2].children[0].children[0].textContent,'—');
});
