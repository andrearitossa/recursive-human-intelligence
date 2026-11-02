const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
function setup(){
 const window={dispatchEvent(){},addEventListener(){}};
 const ctx={window,localStorage:{getItem(){return null},setItem(){}},setInterval(){},setTimeout,CustomEvent:class{},LoopEngine:require('../app/engine.js'),importChats:require('../app/import.js')};
 vm.runInNewContext(fs.readFileSync('app/storage.js','utf8'),ctx);
 return window.loop;
}
test('folder selection, cancellation, reselection and repeated import',async()=>{
 const loop=setup();
 await assert.rejects(loop.call('defaults'),/Choose your home or chat folder first/);
 const file=name=>({name:'chat.jsonl',webkitRelativePath:name+'/chat.jsonl',size:50,text:async()=>JSON.stringify({role:'user',content:'docker'})});
 await loop.call('set-folder',[file('first')]);
 assert.equal((await loop.call('state')).homeName,'first');
 assert.equal(await loop.call('set-folder',[]),null);
 assert.equal((await loop.call('state')).homeName,'first');
 await loop.call('set-folder',[file('second')]);
 assert.equal((await loop.call('state')).homeName,'second');
 assert.equal((await loop.call('defaults')).chats,1);
 assert.equal((await loop.call('defaults')).requests,0);
});
test('folder controls are native directory inputs and reset after selection',()=>{
 const html=fs.readFileSync('app/index.html','utf8');
 for(const id of ['home-input','folder-input'])assert.match(html,new RegExp(`id="${id}" type="file" webkitdirectory`));
 const script=fs.readFileSync('app/app.js','utf8');
 assert.match(script,/finally\{input.value='';\}/);
 assert.doesNotMatch(script,/Select a small chat folder/);
});
