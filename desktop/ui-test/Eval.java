import java.nio.file.*;import java.util.*;
/** Runs the feedback agent on a copy of real saved data, writes the result to a file, and saves nothing into Loop. */
public class Eval{public static void main(String[] a)throws Exception{
 // args: loopDir model outFile [focus]
 Path dir=Path.of(a[0]);ChatEngine e=new ChatEngine();e.load(dir.resolve("history.tsv"));e.loadRemoved(dir.resolve("removed-chats.txt"));
 LlmPatterns llm=new LlmPatterns(dir);Patterns.Result r=llm.hasPatterns()?llm.result(e.requests):Patterns.analyze(e.requests);
 Models.Model m=Models.connect(new Models.Config(Models.Provider.OPENAI,Models.Provider.OPENAI.endpoint,a[1],System.getenv("OPENAI_API_KEY")));
 Map<String,String> tasks=new HashMap<>();llm.descriptions.forEach((id,d)->tasks.put(id,d.task()));
 List<Insights.Finding> told=new ArrayList<>();Map<String,Object> sig=Patterns.signals(e.requests);StringBuilder o=new StringBuilder();
 for(int round=1;round<=Integer.getInteger("passes",Insights.ROUNDS);round++){Map<String,Patterns.Item> refs=new HashMap<>();String in=Insights.input(r.groups(),Patterns.distinct(e.requests),tasks,refs,"",told,sig);long t0=System.currentTimeMillis();
  List<Insights.Finding> got=new ArrayList<>();long now0=System.currentTimeMillis();ItemStream st=new ItemStream(x->{Insights.Finding one=Insights.parseOne(x,refs,now0);if(one!=null)got.add(one);});m.stream(Insights.INSTRUCTIONS,in,st::accept);o.append("\n=== PASS "+round+": "+got.size()+" items, "+(System.currentTimeMillis()-t0)/1000+"s\n");
  for(Insights.Finding f:got){o.append("## "+f.name()+"\n   "+f.description()+"\n   MSG "+f.message()+"\n"+(f.you().isEmpty()?"":"   YOU "+f.you()+" => "+f.better()+"\n"));for(var x:f.actions())o.append("   -> "+x.text()+(x.source().isEmpty()?"":"  [SOURCE: "+x.source()+"]")+"\n");}
  told.addAll(got);if(got.isEmpty())break;}
 Files.writeString(Path.of(a[2]),o.toString());System.out.println("written");
}}
