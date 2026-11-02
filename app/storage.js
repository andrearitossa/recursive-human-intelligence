/* Browser-only persistence and file import. No server or model connection. */
(function () {
  const key = 'loop.workspace.v1';
  const empty = () => ({ events: [], tools: [], paused: false, lastSuggested: 0 });
  const listeners = new Set();
  let homeFiles = null;
  let homeHandle = null;
  let homeName = "";
  let cancelPicker = null;
  let importing = false;
  const progress = detail => window.dispatchEvent(new CustomEvent("loop-import-progress", {detail}));
  let workspace;
  let loadError;
  try {
    const saved = localStorage.getItem(key);
    workspace = saved ? JSON.parse(saved) : empty();
    if (!Array.isArray(workspace.events) || !Array.isArray(workspace.tools)) throw Error('Invalid saved workspace');
  } catch (error) {
    workspace = empty();
    loadError = 'Could not read browser storage. Enable local storage or clear the saved Loop workspace.';
  }
  const snapshot = () => JSON.parse(JSON.stringify(workspace));
  function save(next) {
    try { localStorage.setItem(key, JSON.stringify(next)); }
    catch { throw Error('Browser storage is full or unavailable. No changes were saved.'); }
    workspace = next;
  }
  function selectFiles(folder = false) {
    return new Promise(resolve => {
      if(cancelPicker) cancelPicker();
      const input = document.createElement('input');
      input.type = 'file'; input.accept = '.jsonl'; input.multiple = true;
      if (folder) input.setAttribute('webkitdirectory', '');
      input.hidden = true; document.body.append(input);
      let settled=false;
      const finish = files => { if(settled)return; settled=true;input.remove();cancelPicker=null;resolve(files); };
      cancelPicker=()=>finish([]);
      input.addEventListener('change', () => finish(Array.from(input.files)), { once: true });
      input.addEventListener('cancel', () => finish([]), { once: true });
      input.click();
    });
  }
  async function chatFiles(home) {
    const files = [], tools = {};
    let visited = 0, lastUpdate = 0;
    async function directory(parts) {
      let dir=home;
      for(const part of parts) dir=await dir.getDirectoryHandle(part);
      return dir;
    }
    async function walk(dir, path, tool) {
      for await (const [name, entry] of dir.entries()) {
        visited++;
        if(Date.now()-lastUpdate>100){
          progress({phase:'Finding transcripts…',files:files.length,visited,tools:{...tools},path});
          lastUpdate=Date.now();await new Promise(resolve=>setTimeout(resolve,0));
        }
        if(entry.kind==='directory') await walk(entry,path+'/'+name,tool);
        else if(name.endsWith('.jsonl')) {
          tools[tool]=(tools[tool]||0)+1;
          // Delay reading until parsing so only one file is loaded at a time.
          files.push({name,webkitRelativePath:path+'/'+name,tool,handle:entry});
        }
      }
    }
    const targets=[['Codex',['.codex','sessions']],['Claude',['.claude','projects']],['Pi',['.pi','agent','sessions']]];
    if(home.name === '.codex') targets.unshift(['Codex',['sessions']]);
    if(home.name === '.claude') targets.unshift(['Claude',['projects']]);
    if(home.name === '.pi') targets.unshift(['Pi',['agent','sessions']]);
    if(home.name === 'agent') targets.unshift(['Pi',['sessions']]);
    if(['sessions','projects'].includes(home.name)) {
      await walk(home,home.name,home.name==='projects'?'Claude':'Chats');
      return files;
    }
    for(const [tool,parts] of targets){
      tools[tool]=0;
      progress({phase:'Checking '+tool+'…',files:files.length,tools:{...tools}});
      let dir;
      try {dir=await directory(parts);}
      catch(error){if(error.name==='NotFoundError')continue;throw error;}
      await walk(dir,home.name+'/'+parts.join('/'),tool);
    }
    return files;
  }
  window.loop = {
    onRefresh(callback) { listeners.add(callback); },
    async call(action, payload) {
      if (loadError && action !== 'delete') throw Error(loadError);
      if (action === 'state') return {...snapshot(), homeSelected:homeHandle !== null || homeFiles !== null, homeName};
      if (action === 'set-folder') {
        if(importing)throw Error('Wait for the current import to finish.');
        if(!payload?.length)return null;
        homeFiles=Array.from(payload);homeHandle=null;
        homeName=homeFiles[0].webkitRelativePath?.split('/')[0] || 'Selected folder';
        return {name:homeName};
      }
      if (action === 'choose-folder') {
        if (importing) throw Error('Wait for the current import to finish.');
        const files = await selectFiles(true);
        if (!files.length) return null;
        homeFiles = files; homeHandle = null;
        homeName = files[0].webkitRelativePath.split('/')[0];
        return {name:homeName};
      }
      if (action === 'choose-home') {
        if (importing) throw Error('Wait for the current import to finish.');
        if (window.showDirectoryPicker) {
          try {
            const handle = await window.showDirectoryPicker({id:'loop-home',mode:'read'});
            homeHandle = handle; homeFiles = null; homeName = handle.name;
            return {name:homeName};
          } catch(error) { if(error.name==='AbortError') return null; throw Error('Browser blocked this location. Use Choose chat folder for a specific transcript folder, including WSL paths, or Manual import for files.'); }
        }
        // Brave and other browsers may disable directory handles. Use the
        // standard picker while keeping the same choose-then-import flow.
        const files = await selectFiles(true);
        if (!files.length) return null;
        homeFiles = files; homeHandle = null;
        homeName = files[0].webkitRelativePath.split('/')[0];
        return {name:homeName, compatibility:true};
      }
      const next = snapshot();
      if (action === 'defaults' || action === 'import') {
        if (next.paused) throw Error('Resume collection before importing.');
        if (importing) throw Error('An import is already running.');
        if (action === 'defaults' && !homeHandle && homeFiles === null) throw Error('Choose your home or chat folder first.');
        importing = true;
        try {
          let files;
          if(action === 'defaults') {
            progress({phase:'Checking chat folders…',chats:0,files:0,tools:{}});
            files = homeHandle ? await chatFiles(homeHandle) : homeFiles;
          } else files = await selectFiles();
          const report = await importChats(next, files, false, LoopEngine, progress);
          if (report) save(next);
          progress({phase:'done',report});
          return report;
        } catch(error) { progress({phase:'error',message:error.message}); throw error; }
        finally { importing = false; }
      }
      if (action === 'analyze') {
        const added = LoopEngine.analyze(next, Date.now(), Boolean(payload));
        save(next); return added.length;
      }
      if (action === 'pause') next.paused = !next.paused;
      else if (action === 'delete') { homeFiles = null; homeHandle = null; homeName = ''; save(empty()); loadError = null; return snapshot(); }
      else if (action === 'tool') {
        const tool = next.tools.find(tool => tool.id === payload.id);
        if (!tool) throw Error('Tool not found');
        if (payload.action === 'archive') tool.archived = !tool.archived;
        if (payload.action === 'useful') tool.feedback.push({ date: Date.now(), type: 'useful' });
        if (payload.action === 'improve') tool.versions.push({ date: Date.now(), trim: Boolean(payload.trim), empty: Boolean(payload.empty) });
      } else throw Error('Unknown action');
      save(next); return snapshot();
    }
  };
  setInterval(async () => {
    if (loadError) return;
    try {
      const count = await window.loop.call('analyze', false);
      if (count) { listeners.forEach(callback => callback()); if (typeof toast === 'function') toast('A new tool is ready in your collection.'); }
    } catch (error) { if (typeof toast === 'function') toast(error.message); }
  }, 60000);
  window.addEventListener('storage', event => {
    if (event.key !== key) return;
    try { workspace = event.newValue ? JSON.parse(event.newValue) : empty(); listeners.forEach(callback => callback()); } catch { /* Keep current state if another tab writes invalid data. */ }
  });
})();
