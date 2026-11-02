import java.nio.file.*;
public class RealPatterns{public static void main(String[] a)throws Exception{
 ChatEngine e=new ChatEngine();e.load(Path.of(a[0]));e.loadRemoved(Path.of(a[0]).resolveSibling("removed-chats.txt"));long t=System.currentTimeMillis();
 var r=Patterns.analyze(e.requests);System.out.println("ms="+(System.currentTimeMillis()-t)+" requests="+r.requests()+" sessions="+r.sessions()+" unclustered="+r.unclustered()+" modes="+r.modes());
 for(var g:r.groups()){System.out.println(g.sessions.size()+"s "+g.items.size()+"r ["+g.dominant().label+"] "+g.label);for(int i=0;i<Math.min(2,g.examples.size());i++)System.out.println("     "+g.examples.get(i).request().text().strip().replaceAll("\\s+"," ").substring(0,Math.min(90,g.examples.get(i).request().text().strip().replaceAll("\\s+"," ").length())));}
}}
