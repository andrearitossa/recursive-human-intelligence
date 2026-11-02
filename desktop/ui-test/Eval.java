import java.nio.file.*;import java.util.*;
/** Runs the feedback agent on a copy of real saved data, writes the result to a file, and saves nothing into Loop. */
public class Eval{public static void main(String[] a)throws Exception{
 // args: loopDir model outFile [focus]
 Path dir=Path.of(a[0]);ChatEngine e=new ChatEngine();e.load(dir.resolve("history.tsv"));e.loadRemoved(dir.resolve("removed-chats.txt"));
 LlmPatterns llm=new LlmPatterns(dir);Patterns.Result r=llm.hasPatterns()?llm.result(e.requests):Patterns.analyze(e.requests);
 Models.Model m=Models.connect(new Models.Config(Models.Provider.OPENAI,Models.Provider.OPENAI.endpoint,a[1],System.getenv("OPENAI_API_KEY")));
 Map<String,Patterns.Item> refs=new HashMap<>();Map<String,String> tasks=new HashMap<>();llm.descriptions.forEach((id,d)->tasks.put(id,d.task()));
 List<Insights.Finding> told=new ArrayList<>();
 String in=Insights.input(r.groups(),Patterns.distinct(e.requests),tasks,refs,a.length>3?a[3]:"",told,Patterns.signals(e.requests));
 StringBuilder o=new StringBuilder("signals="+Patterns.signals(e.requests)+"\npatterns="+r.groups().size()+" refs="+refs.size()+" chars="+in.length()+"\n");
 long t0=System.currentTimeMillis();List<String> arrive=new ArrayList<>();ItemStream st=new ItemStream(x->arrive.add((System.currentTimeMillis()-t0)/1000+"s "+Json.text(x.get("name"))));
 String answer=m.stream(Insights.INSTRUCTIONS,in,st::accept);o.append("ARRIVALS "+arrive+" total "+(System.currentTimeMillis()-t0)/1000+"s\n");
 for(Insights.Finding f:Insights.parse(answer,refs)){o.append("\n## "+f.name()+"\n   "+f.description()+"\n   MSG "+f.message()+"\n"+(f.you().isEmpty()?"":"   YOU "+f.you()+"\n   BETTER "+f.better()+"\n"));for(var x:f.actions())o.append("   -> "+x.text()+(x.source().isEmpty()?"":"  [SOURCE: "+x.source()+"]")+"\n");for(var q:f.evidence()){String t=q.text().replaceAll("\\s+"," ");o.append("   Q "+t.substring(0,Math.min(110,t.length()))+"\n");}}
 Files.writeString(Path.of(a[2]),o.toString());System.out.println("written");
}}
