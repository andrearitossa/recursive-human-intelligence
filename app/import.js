(function(root) {
  async function importChats(state, files, defaults, engine, onProgress = () => {}) {
    if (!files.length) return null;
    const groups = new Map();
    const report = {date:Date.now(), chats:0, requests:0, skipped:0, folders:[]};
    let processed = 0, lastUpdate = 0;
    const toolCounts = {};
    for (const sourceFile of files) {
      processed++;
      const file = sourceFile.handle ? await sourceFile.handle.getFile() : sourceFile;
      if (!file.name.toLowerCase().endsWith('.jsonl')) continue;
      const path = sourceFile.webkitRelativePath || file.webkitRelativePath || file.name;
      const match = path.match(/(?:^|\/)(\.codex\/sessions|\.claude\/projects|\.pi\/agent\/sessions)(?:\/|$)/);
      if (defaults && !match && !['sessions','projects'].includes(path.split('/')[0])) continue;
      const folder = path.includes('/') ? path.slice(0,path.lastIndexOf('/')) : 'Selected files';
      const name = match ? ({'.codex/sessions':'Codex','.claude/projects':'Claude','.pi/agent/sessions':'Pi'})[match[1]] : 'Chats';
      const id = name+'\0'+folder;
      if (!groups.has(id)) groups.set(id,{name,path:folder,chats:0,requests:0,skipped:0});
      const group = groups.get(id);
      try {
        if (file.size > 10*1024*1024) throw Error('Oversized file');
        const text = await file.text();
        if (!engine.extract(text,path).length) continue;
        group.chats++; report.chats++;
        toolCounts[name]=(toolCounts[name]||0)+1;
        const added = engine.importEvents(state,text,path);
        group.requests+=added;report.requests+=added;
      } catch {group.skipped++;report.skipped++;}
      if(Date.now()-lastUpdate>80 || processed===files.length){
        onProgress({phase:'Reading chats…',processed,total:files.length,chats:report.chats,requests:report.requests,tools:{...toolCounts},path:folder});
        lastUpdate=Date.now();await new Promise(resolve=>setTimeout(resolve,0));
      }
    }
    report.folders = [...groups.values()].sort((a,b)=>b.chats-a.chats || a.path.localeCompare(b.path));
    state.lastImport = report;
    return report;
  }
  if(typeof module!=='undefined') module.exports=importChats;
  else root.importChats=importChats;
})(globalThis);
