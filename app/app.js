let state,page='home',selected=null;const $=s=>document.querySelector(s);const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function call(action,payload){return window.loop.call(action,payload)}
function toast(s){$('#toast').textContent=s;$('#toast').style.display='block';clearTimeout(window.toastTimer);window.toastTimer=setTimeout(()=>$('#toast').style.display='none',4500)}
async function refresh(){try{state=await call('state');render()}catch(e){toast(e.message)}}
function render(){renderImportSummary();$('#home-label').textContent=state.homeSelected?'Change home':'Choose home';$('#choose-home').title=state.homeSelected?state.homeName:'Select folder access without uploading your home';document.querySelectorAll('[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===page));$('#status').textContent=state.paused?'◌ Collection paused':'● Saved in this browser';$('#pause').textContent=state.paused?'Resume collection':'Pause collection';if(selected)return tool();if(page==='settings')return settings();home()}
function home(){const tools=state.tools.filter(t=>Boolean(t.archived)===(page==='archive'));$('#content').innerHTML=`<div class="intro"><div><div class="eyebrow">A LITTLE SPACE TO GROW</div><h1>${page==='archive'?'Room for something new.':'Make room for better work.'}</h1><p>Small tools. Less repetition. More of your day back.</p></div><button id="analyze">↗ Find opportunities</button></div>${page==='home'?`<section class="hero"><div><span class="eyebrow">YOUR NEXT SMALL WIN</span><h2>${tools.length?'Built around the way you work.':'Good things start with a pattern.'}</h2><p>${tools.length?'Your personal tools are ready when you need them. Open one below to get started.':'Import conversations from three separate sessions. Loop looks for recurring CSV or Docker requests and offers a useful starting point.'}</p><button class="primary" id="hero-import">${tools.length?'Find another opportunity →':'Connect your conversations →'}</button><p class="muted">Local pattern matching · No model connection required</p></div><div class="orb">∞</div></section>`:''}<div class="section-line"><span>Your ${page==='archive'?'archived tools':'collection'} <span class="muted">/ ${tools.length}</span></span><input id="search" aria-label="Search tools" placeholder="Search your tools…"></div><div class="grid" id="cards"></div><p class="muted">${state.events.length} imported requests · ${new Set(state.events.map(e=>e.session)).size} sessions</p>`;
const cards=query=>{$('#cards').innerHTML=tools.filter(t=>(t.title+' '+t.description).toLowerCase().includes(query.toLowerCase())).map(t=>`<article class="card"><span class="badge">${esc(t.kind)}</span><h2>${esc(t.title)}</h2><p>${esc(t.description)}</p><p class="muted">From ${new Set(t.evidence.map(e=>e.session)).size} sessions · v${t.versions.length}</p><button data-open="${t.id}">Open ${t.kind==='Reference'?'reference':'tool'} ↗</button></article>`).join('')||'<div class="panel"><p>Your next useful thing will live here.</p></div>';document.querySelectorAll('[data-open]').forEach(b=>b.onclick=()=>{selected=b.dataset.open;render()})};cards('');$('#search').oninput=e=>cards(e.target.value);$('#analyze').onclick=analyze;if($('#hero-import'))$('#hero-import').onclick=tools.length?analyze:importFiles}
async function importFiles(){
 const button=$('#import');button.disabled=true;button.textContent='Importing…';
 try {
  const result=await call('defaults');await refresh();
  if(result) toast(`${result.chats} chats found · ${result.requests} new requests`);
 } catch(error){toast(error.message)}
 finally{button.disabled=false;button.textContent='＋ Import chats'}
}
async function analyze(){try{const n=await call('analyze',true);await refresh();toast(n?`${n} useful starting point${n>1?'s':''} added.`:state.paused?'Resume collection to analyze.':'No new patterns yet. Look for similar requests in three separate sessions.')}catch(e){toast(e.message)}}
async function manualImport(){try{const result=await call('import');await refresh();if(result)toast(`${result.chats} chats found · ${result.requests} new requests`)}catch(e){toast(e.message)}}
function settings(){$('#content').innerHTML=`<h1>Your space. Your rules.</h1><p>Choose what Loop learns from.</p><section class="panel"><div class="row"><div><strong>Conversation imports</strong><p>Claude Code JSONL or user-message JSONL. Only user messages are imported.<br>Choose individual files; Loop does not scan your machine.</p></div><button id="settings-import">Import files</button></div><div class="row"><div><strong>Analysis</strong><p>Local CSV and Docker pattern detection. Checked every minute while this page is open.<br>Automatic suggestions are limited to one per week; manual analysis bypasses this limit.</p></div><span class="badge">LOCAL ENGINE</span></div><div class="row"><div><strong>Model connection</strong><p>Not connected. This prototype uses curated tools rather than AI generation.</p></div></div><div class="row"><div><strong>Delete local history & tools</strong><p>Removes imported requests, solutions, and feedback from this browser.</p></div><button id="delete">Delete everything</button></div></section>`;$('#settings-import').onclick=manualImport;$('#delete').onclick=async()=>{if(confirm('Delete all Loop history, tools, and feedback?')){await call('delete');refresh();toast('Local history deleted.')}}}
function tool(){const t=state.tools.find(t=>t.id===selected),v=t.versions.at(-1);$('#content').innerHTML=`<button id="back">← Your collection</button><h1>${esc(t.title)}</h1><p>${esc(t.description)}</p><span class="badge">${esc(t.kind)} · VERSION ${t.versions.length}</span><div class="toolbar"><button id="useful">♡ Useful (${t.feedback.length})</button><button id="archive">${t.archived?'Restore':'Archive'}</button></div><section class="panel">${t.id==='csv'?`<h2>A fresh start for your CSV.</h2><p>Standard comma-separated CSV. Quoted fields and line breaks are supported.<br>Original files stay untouched. Preview the output before downloading.</p><label><input type="checkbox" id="trim" ${v.trim?'checked':''}> Trim spaces around values</label><label><input type="checkbox" id="empty" ${v.empty?'checked':''}> Remove empty rows</label><button id="version">Save these preferences as a new version</button><label class="drop" id="drop">Drop a CSV here, or choose a file<br><br><input type="file" id="csv-file" accept=".csv,text/csv"></label><div id="output"></div>`:`<h2>Your container essentials</h2><p>Inspect before changing anything. These commands help you see what is running.</p><pre>docker ps             # running containers\ndocker ps -a          # all containers\ndocker images         # local images\ndocker logs NAME      # inspect output\ndocker exec -it NAME sh # open a shell\ndocker compose up -d  # start a Compose project\ndocker compose stop   # stop its containers</pre><p>This is a curated reference. Commands have not been customized to your projects.</p>`}</section><details><summary>Why this tool exists · ${t.evidence.length} requests</summary>${t.evidence.map(e=>`<div class="panel"><span class="muted">${esc(e.session)}</span><p>${esc(e.text)}</p></div>`).join('')}</details>`;$('#back').onclick=()=>{selected=null;render()};$('#useful').onclick=async()=>{await call('tool',{id:t.id,action:'useful'});await refresh();toast('Thanks. Feedback saved.')};$('#archive').onclick=async()=>{await call('tool',{id:t.id,action:'archive'});selected=null;refresh()};if(t.id==='csv'){$('#version').onclick=async()=>{await call('tool',{id:t.id,action:'improve',trim:$('#trim').checked,empty:$('#empty').checked});refresh();toast('Preferences saved. Previous versions retained.')};$('#csv-file').onchange=e=>processFile(e.target.files[0]);$('#drop').ondragover=e=>e.preventDefault();$('#drop').ondrop=e=>{e.preventDefault();processFile(e.dataTransfer.files[0])}}}
async function processFile(file){if(!file)return;try{if(file.size>10*1024*1024)throw Error('Choose a file smaller than 10 MB.');const cleaned=CSV.clean(await file.text(),{trim:$('#trim').checked,empty:$('#empty').checked});$('#output').innerHTML='<h3>Preview</h3><pre></pre><button class="primary" id="download">Download clean CSV ↓</button>';$('#output pre').textContent=cleaned.slice(0,3000)||'(No rows remain)';$('#download').onclick=()=>{const url=URL.createObjectURL(new Blob([cleaned],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=file.name.replace(/\.csv$/i,'')+'-clean.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}}catch(e){toast(e.message)}}
document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>{page=b.dataset.page;selected=null;render()});$('#import').onclick=importFiles;$('#pause').onclick=async()=>{await call('pause');refresh()};window.loop.onRefresh(refresh);refresh();


$('#manual-import').onclick=manualImport;

function renderImportSummary(){
 const report=state.lastImport;
 if(!report){
  $('#import-summary').innerHTML=`<div class="import-empty"><span class="import-symbol">↗</span><div><strong>${state.homeSelected?'Ready when you are.':'Bring your chats. Find your next shortcut.'}</strong><p>${state.homeSelected?esc(state.homeName)+' selected. Click Import chats to scan.':'Choose home, or choose a specific chat folder for Windows / WSL paths.'}</p></div></div>`;
  return;
 }
 $('#import-summary').innerHTML=`<div class="import-overview"><div><span class="eyebrow">LAST IMPORT · ${esc(new Date(report.date).toLocaleDateString())}</span><h2>${report.chats} <span>chat${report.chats===1?'':'s'} found</span></h2><p>${report.requests} new requests added${report.skipped?' · '+report.skipped+' files skipped':''}</p></div><span class="local-pill">● Stays on your device</span></div>${report.folders.length?`<details class="folder-details" open><summary>In ${report.folders.length} folder${report.folders.length===1?'':'s'}</summary><div class="folder-list">${report.folders.map(folder=>`<div class="folder-item"><span class="folder-icon">▱</span><div><strong>${esc(folder.name)}</strong><span class="folder-path" title="${esc(folder.path)}">${esc(folder.path)}</span></div><div class="folder-count"><strong>${folder.chats}</strong><span>chats${folder.skipped?' · '+folder.skipped+' skipped':''}</span></div></div>`).join('')}</div></details>`:'<p class="no-chats">No matching chats here. Try a sessions/projects folder, or use Manual import.</p>'}`;
}

window.addEventListener('loop-import-progress',event=>{
 const p=event.detail, node=$('#import-progress');
 if(p.phase==='done'){node.hidden=true;return;}
 node.hidden=false;
 if(p.phase==='error'){node.innerHTML=`<strong>Import stopped</strong><p>${esc(p.message)}</p>`;return;}
 const counts=Object.entries(p.tools||{}).map(([name,count])=>`<span><strong>${count.toLocaleString()}</strong> ${esc(name)}</span>`).join('');
 node.innerHTML=`<div class="progress-heading"><span class="pulse-dot"></span><strong>${esc(p.phase)}</strong><span class="muted">${p.total?`${p.processed.toLocaleString()} / ${p.total.toLocaleString()} files`:p.files?`${p.files.toLocaleString()} transcripts found`:''}</span></div>${p.total?`<progress max="${p.total}" value="${p.processed}"></progress>`:''}<div class="live-counts">${counts}</div>${p.path?`<p class="folder-path">${esc(p.path)}</p>`:''}`;
});


async function acceptFolder(input){
 try{
  const files=Array.from(input.files || []);
  if(!files.length)return;
  const result=await call('set-folder',files);await refresh();
  if(result)toast(`${result.name} selected. Click Import chats.`);
 }catch(error){toast(error.message)}
 finally{input.value='';}
}
$('#folder-input').onchange=event=>acceptFolder(event.target);
$('#home-input').onchange=event=>acceptFolder(event.target);
// Keep the native input as the actual click target in Brave. Only intercept
// when directory handles are available, to avoid enumerating an entire home.
$('#home-input').onclick=async event=>{
 if(!window.showDirectoryPicker)return;
 event.preventDefault();
 try{const result=await call('choose-home');await refresh();if(result)toast(`${result.name} connected. Click Import chats.`)}
 catch(error){toast(error.message)}
};
