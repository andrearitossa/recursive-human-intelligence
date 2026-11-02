const {test}=require('node:test');const assert=require('node:assert/strict');const {importEvents,analyze}=require('../app/engine.js');const CSV=require('../app/csv.js');
test('separate sessions trigger a tool, assistant messages and duplicate imports do not',()=>{const s={events:[],tools:[]};for(let i=0;i<3;i++){const text=JSON.stringify({type:'user',sessionId:String(i),message:{role:'user',content:'Clean my csv'}});assert.equal(importEvents(s,text,'x'),1);assert.equal(importEvents(s,text,'x'),0)}importEvents(s,JSON.stringify({role:'assistant',content:'docker'}),'other');assert.equal(s.events.length,3);assert.equal(analyze(s,Date.now(),true).length,1);assert.equal(analyze(s,Date.now(),true).length,0);s.tools[0].archived=true;assert.equal(analyze(s,Date.now(),true).length,0)});
test('pause and weekly cooldown prevent automatic analysis',()=>{const s={events:[0,1,2].map(i=>({session:String(i),text:'docker'})),tools:[],paused:true};assert.deepEqual(analyze(s),[]);s.paused=false;s.lastSuggested=Date.now();assert.deepEqual(analyze(s),[]);assert.equal(analyze(s,Date.now(),true).length,1)});
test('CSV preserves quoted commas, multiline fields and escaped quotes',()=>{const input=' name ,notes\r\n Alice ,"a,b"\r\n , \r\nBob,"line 1\nline ""2"""';const result=CSV.clean(input,{trim:true,empty:true});assert.deepEqual(CSV.parse(result),[['name','notes'],['Alice','a,b'],['Bob','line 1\nline "2"']]);assert.throws(()=>CSV.parse('"unclosed'));assert.equal(CSV.clean(' a , b ',{trim:false,empty:false}),' a , b ')});
test('imports native Codex, Claude and Pi user entries without assistant or mirrored events',()=>{
 const {extract}=require('../app/engine.js');
 const lines=[
 {type:'response_item',payload:{type:'message',role:'user',content:[{type:'input_text',text:'Clean CSV'}]}},
 {type:'event_msg',payload:{type:'user_message',message:'Clean CSV'}},
 {type:'user',sessionId:'claude-session',message:{role:'user',content:[{type:'text',text:'Docker help'}]}},
 {type:'message',message:{role:'user',content:[{type:'text',text:'Pi CSV'}]}},
 {type:'message',message:{role:'assistant',content:[{type:'text',text:'Ignore me'}]}}
 ].map(JSON.stringify).join('\n');
 assert.deepEqual(extract(lines,'folder/chat.jsonl').map(e=>e.text),['Clean CSV','Docker help','Pi CSV']);
 assert.equal(extract(lines,'folder/chat.jsonl')[1].session,'claude-session');
});
test('import summary counts chat files per folder and separates new requests and skips',async()=>{
 const importer=require('../app/import.js'),engine=require('../app/engine.js');
 const state={events:[],tools:[]};
 const file=(path,text,size=100)=>({name:path.split('/').at(-1),webkitRelativePath:path,size,text:async()=>text});
 const files=[file('home/.codex/sessions/day/a.jsonl',JSON.stringify({role:'user',content:'csv'})),file('home/.codex/sessions/day/b.jsonl',JSON.stringify({role:'assistant',content:'csv'})),file('home/.claude/projects/app/c.jsonl',JSON.stringify({role:'user',content:'docker'})),file('home/.claude/projects/app/big.jsonl','',11*1024*1024)];
 const first=await importer(state,files,true,engine);
 assert.equal(first.chats,2);assert.equal(first.requests,2);assert.equal(first.skipped,1);assert.equal(first.folders.length,2);
 assert.equal((await importer(state,files,true,engine)).requests,0);
 assert.equal((await importer(state,files,true,engine)).chats,2);
 assert.equal(await importer(state,[],true,engine),null);
});
