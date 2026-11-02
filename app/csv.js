(function(root){
function parse(text){const rows=[];let row=[],cell='',quoted=false;for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++}else quoted=!quoted}else if(c===','&&!quoted){row.push(cell);cell=''}else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell);rows.push(row);row=[];cell=''}else cell+=c}if(quoted)throw Error('Unclosed quoted field');if(cell||row.length){row.push(cell);rows.push(row)}return rows}
function clean(text,options){let rows=parse(text.replace(/^\uFEFF/,''));if(options.trim)rows=rows.map(r=>r.map(c=>c.trim()));if(options.empty)rows=rows.filter(r=>r.some(c=>c.trim()));return rows.map(r=>r.map(c=>/[",\r\n]/.test(c)?'"'+c.replace(/"/g,'""')+'"':c).join(',')).join('\r\n')}
const api={parse,clean};if(typeof module!=='undefined')module.exports=api;else root.CSV=api;
})(globalThis);
