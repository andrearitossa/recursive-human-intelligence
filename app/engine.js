(function(root){
const recipes = [
 {id:'csv',pattern:/\b(csv|spreadsheet|excel)\b/i,title:'A little less spreadsheet work.',description:'Trim whitespace, remove empty rows, and download a clean CSV.',kind:'File tool'},
 {id:'docker',pattern:/\bdocker\b/i,title:'Docker, within reach.',description:'A small command reference for your next container task.',kind:'Reference'}
];
function extract(text, source='import') {
 const events=[];
 for (const line of text.split('\n')) { if(!line.trim()) continue; let obj; try {obj=JSON.parse(line)} catch {continue}
 let message=obj.message || obj;
 if(obj.type==='response_item') message=obj.payload || {};
 if(obj.type==='event_msg') continue; // Codex mirrors prompts here; response_item is canonical.
 const role=message.role || obj.role || obj.type;
 if(role!=='user') continue;
 const content=message.content;
 const value=typeof content==='string'?content:Array.isArray(content)?content.filter(x=>(x.type==='text'||x.type==='input_text')).map(x=>x.text).join('\n'):'';
 if(!value.trim()) continue;
 const session=String(obj.sessionId||obj.session_id||source);
 events.push({id:JSON.stringify([session,value]),session,text:value});
 }
 return events;
}
function importEvents(state,text,source) {const seen=new Set(state.events.map(x=>x.id)); const fresh=extract(text,source).filter(x=>!seen.has(x.id)&&seen.add(x.id));state.events.push(...fresh); return fresh.length}
function analyze(state, now=Date.now(), manual=false) {
 if(state.paused) return [];
 if(!manual && now-(state.lastSuggested||0)<7*86400000) return [];
 const added=[];
 for(const recipe of recipes){
 const evidence=state.events.filter(x=>recipe.pattern.test(x.text));
 if(new Set(evidence.map(x=>x.session)).size<3||state.tools.some(t=>t.id===recipe.id))continue;
 state.tools.push({...recipe,pattern:undefined,evidence:evidence.map(x=>({session:x.session,text:x.text})),created:now,versions:[{date:now,trim:true,empty:true}],feedback:[],archived:false});added.push(recipe.title);
 if(!manual) break;
 }
 if(added.length)state.lastSuggested=now;
 return added;
}
const api={extract,importEvents,analyze};
if(typeof module!=='undefined')module.exports=api;else root.LoopEngine=api;
})(globalThis);
