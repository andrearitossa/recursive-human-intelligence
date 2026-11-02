const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
// Check the application-owned browser template independently of the JVM.
const source=fs.readFileSync('desktop/Artifacts.java','utf8');
const block=source.split('String inner="""')[1].split('""".replace')[0];
const decoded=block.replace(/\\(\\|n|r|t|b|f|"|')/g,(_,escape)=>({'\\':'\\',n:'\n',r:'\r',t:'\t',b:'\b',f:'\f','"':'"',"'":"'"}[escape]));
const template=decoded.split('<script>')[1].split('</script>')[0];
function run(data,input){
 const nodes={};const element=()=>({value:'',textContent:'',hidden:false,append(){},click(){}});
 const document={getElementById(id){return nodes[id]??=element()},createElement:element,addEventListener(){}};
 vm.runInNewContext(template.replace('__DATA__',JSON.stringify(data)),{document,Blob,URL,setTimeout});
 nodes.input.value=input;nodes.run.onclick();return nodes;
}
test('generated CSV tool preserves quoting and multiline fields',()=>{
 const nodes=run({kind:'csv'},' name ,notes\n Alice ,"a,b"\n , \nBob,"line 1\nline ""2"""');
 assert.equal(nodes.output.value,'name,notes\r\nAlice,"a,b"\r\nBob,"line 1\nline ""2"""');
});
test('generated deterministic transformer applies only configured operations',()=>{
 const nodes=run({kind:'transform',operations:[{type:'trim'},{type:'remove_empty'},{type:'deduplicate'},{type:'replace',from:'foo',to:'bar'}]},' foo \n\nfoo\nbaz ');
 assert.equal(nodes.output.value,'bar\nbaz');
});
test('invalid CSV reports an error without a result',()=>{
 const nodes=run({kind:'csv'},'"broken');assert.equal(nodes.output.value,'');assert.match(nodes.error.textContent,/Unclosed/);
});
