import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Regroups saved descriptions in a COPY of Loop's data and reports coverage. args: copyDir model outFile */
public class EvalPatterns{public static void main(String[] a)throws Exception{
 Path dir=Path.of(a[0]);ChatEngine e=new ChatEngine();e.load(dir.resolve("history.tsv"));e.loadRemoved(dir.resolve("removed-chats.txt"));LlmPatterns llm=new LlmPatterns(dir);
 StringBuilder o=new StringBuilder();
 if(a[1].equals("audit")){o.append("BEFORE\n");}
 else{Models.Model m=Models.connect(new Models.Config(Models.Provider.OPENAI,Models.Provider.OPENAI.endpoint,a[1],System.getenv("OPENAI_API_KEY")));ExecutorService p=Executors.newFixedThreadPool(4);long t0=System.currentTimeMillis();List<String> at=new ArrayList<>();llm.merge(m,e.requests,"",(s,d,t,l)->{if(!l.isEmpty())at.add((System.currentTimeMillis()-t0)/1000+"s");},p);o.append("ARRIVALS "+at+" total "+(System.currentTimeMillis()-t0)/1000+"s\n");p.shutdownNow();}
 var r=llm.result(e.requests);Set<String> in=new HashSet<>();for(var g:r.groups())for(var i:g.items)in.add(i.id());
 o.append("saved="+llm.saved.size()+" shown="+r.groups().size()+" described="+r.requests()+" grouped="+in.size()+" pct="+(100*in.size()/Math.max(1,r.requests()))+" unread="+llm.pending(e.requests).size()+"\n");
 for(var g:r.groups())o.append(String.format("%3dc %3dr %s %s - %s%n",g.sessions.size(),g.items.size(),g.friction?"F":" ",g.label,g.description));
 List<String> rest=new ArrayList<>();for(var i:Patterns.distinct(e.requests)){var d=llm.descriptions.get(i.id());if(d!=null&&!in.contains(i.id()))rest.add(d.area()+" | "+d.task());}Collections.shuffle(rest,new Random(3));
 o.append("\nUNGROUPED sample of "+rest.size()+"\n");rest.stream().limit(70).forEach(x->o.append("  "+x+"\n"));
 Files.writeString(Path.of(a[2]),o.toString());System.out.println("written");
}}
