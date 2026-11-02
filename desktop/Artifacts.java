import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Renders a validated declarative tool; model output is never executed. */
final class Artifacts {
    static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    static String html(Solutions.Tool tool){
        Map<String,Object> data=new LinkedHashMap<>(tool.current());data.put("title",tool.title);data.put("description",tool.description);data.put("kind",tool.kind);
        String json=Json.write(data).replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026").replace("\u2028","\\u2028").replace("\u2029","\\u2029");
        String inner="""
            <!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'none'; form-action 'none'; base-uri 'none'"><meta name="viewport" content="width=device-width"><style>
            *{box-sizing:border-box}body{margin:0;padding:48px 24px;background:#f7f7f2;color:#293c26;font:16px/1.65 system-ui}main{max-width:760px;margin:auto}h1{font:42px Georgia,serif;line-height:1.2}p,li{white-space:pre-wrap}section{background:white;border:1px solid #dce3d2;border-radius:16px;padding:28px;margin:24px 0}button{padding:12px 18px;border:0;border-radius:8px;background:#324a2d;color:white;cursor:pointer;margin:8px 8px 8px 0}textarea{display:block;width:100%;min-height:180px;border:1px solid #dce3d2;border-radius:8px;padding:15px;font:14px monospace}summary{cursor:pointer}details{margin:15px 0}small{color:#7c8870}#error{color:#a13232}
            </style></head><body><main><small>LOOP · YOUR PERSONAL TOOL</small><h1 id="title"></h1><p id="description"></p><section><p id="content"></p><ol id="steps"></ol></section><section id="quiz"></section><section id="transform" hidden><label>Drop a file anywhere on this page, or choose one <input id="file" type="file"></label><p>Or paste your input:</p><textarea id="input" aria-label="Input"></textarea><button id="run">Preview result</button><p id="error" role="status"></p><textarea id="output" aria-label="Output" readonly></textarea><button id="save">Download result</button><p><small>Your original file stays untouched. Review the result before using it.</small></p></section><p><small>Generated from a validated template. No network access. No connection to Loop or its credentials.</small></p></main><script>
            const data=__DATA__;
            for(const id of ['title','description','content'])document.getElementById(id).textContent=data[id]||'';
            const steps=document.getElementById('steps');for(const text of data.steps||[]){const li=document.createElement('li');li.textContent=text;steps.append(li)}
            const quiz=document.getElementById('quiz');quiz.hidden=!(data.quiz||[]).length;
            for(const q of data.quiz||[]){const details=document.createElement('details'),summary=document.createElement('summary'),answer=document.createElement('p');summary.textContent=q.question;answer.textContent=q.answer;details.append(summary,answer);quiz.append(details)}
            const input=document.getElementById('input'),output=document.getElementById('output'),error=document.getElementById('error');
            document.getElementById('transform').hidden=!['transform','csv'].includes(data.kind);
            async function load(file){try{if(!file)return;if(file.size>10485760)throw Error('Choose a file smaller than 10 MB.');const bytes=await file.arrayBuffer();try{input.value=new TextDecoder('utf-8',{fatal:true}).decode(bytes)}catch{input.value=new TextDecoder('windows-1252').decode(bytes)}error.textContent='Loaded '+file.name+'. Choose Preview result.'}catch(e){error.textContent=e.message}}
            document.getElementById('file').onchange=e=>load(e.target.files[0]);
            for(const name of ['dragover','drop'])document.addEventListener(name,e=>e.preventDefault());
            document.addEventListener('drop',e=>load(e.dataTransfer.files[0]));
            function delimiter(text){let best=',',most=0;for(const d of [',',';','\\t']){let n=0,q=false,seen=false;for(const c of text){if(c==='"')q=!q;else if(!q&&(c==='\\n'||c==='\\r')){if(seen)break}else{if(c.trim())seen=true;if(!q&&c===d)n++}}if(n>most){best=d;most=n}}return best}
            function csv(text){const d=delimiter(text),rows=[];let row=[],cell='',quoted=false;for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++}else quoted=!quoted}else if(c===d&&!quoted){row.push(cell.trim());cell=''}else if((c==='\\n'||c==='\\r')&&!quoted){if(c==='\\r'&&text[i+1]==='\\n')i++;row.push(cell.trim());rows.push(row);row=[];cell=''}else cell+=c}if(quoted)throw Error('Unclosed CSV quoted field.');if(cell||row.length){row.push(cell.trim());rows.push(row)}return rows.filter(r=>r.some(c=>c.trim())).map(r=>r.map(c=>c.includes(d)||/["\\r\\n]/.test(c)?'"'+c.replaceAll('"','""')+'"':c).join(d)).join('\\r\\n')}
            document.getElementById('run').onclick=()=>{try{let result=input.value;if(result.length>10485760)throw Error('Input is too large.');if(data.kind==='csv')result=csv(result.replace(/^\\uFEFF/,''));else for(const op of data.operations||[]){if(op.type==='trim')result=result.split('\\n').map(s=>s.trim()).join('\\n');if(op.type==='remove_empty')result=result.split('\\n').filter(s=>s.trim()).join('\\n');if(op.type==='deduplicate')result=[...new Set(result.split('\\n'))].join('\\n');if(op.type==='replace')result=result.split(op.from).join(op.to);if(result.length>10485760)throw Error('Output exceeded 10 MB.')}output.value=result;error.textContent='Preview ready.'}catch(e){error.textContent=e.message;output.value=''}};
            document.getElementById('save').onclick=()=>{const url=URL.createObjectURL(new Blob([output.value],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=data.kind==='csv'?'cleaned.csv':'result.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)};
            </script></body></html>
            """.replace("__DATA__",json);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>"+escape(tool.title)+"</title><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; frame-src 'self' about:; script-src 'unsafe-inline'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><style>html,body,iframe{margin:0;width:100%;height:100%;border:0}</style></head><body><iframe title=\""+escape(tool.title)+"\" sandbox=\"allow-scripts allow-downloads\" srcdoc=\""+escape(inner)+"\"></iframe></body></html>";
    }
    static Path write(Solutions store,Solutions.Tool tool)throws IOException{Files.createDirectories(store.directory);Path file=store.directory.resolve(tool.id+".html");Files.writeString(file,html(tool));return file;}
}
