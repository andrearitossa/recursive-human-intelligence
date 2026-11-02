import java.nio.file.*;import java.util.*;import java.util.regex.*;
/** Imports real history into memory only. Prints counts and tag names, never any chat text. */
public class RealImport {
  public static void main(String[] a)throws Exception{
    ChatEngine e=new ChatEngine();List<ChatEngine.Root> roots=new ArrayList<>();
    for(String h:a)ChatEngine.addHome(roots,Path.of(h));
    long t=System.currentTimeMillis();var r=e.scan(roots,()->false,u->{});long ms=System.currentTimeMillis()-t;
    System.out.println("chats="+r.chats()+" requests="+r.added()+" skipped="+r.skipped()+" ms="+ms);
    t=System.currentTimeMillis();var r2=e.scan(roots,()->false,u->{});System.out.println("rescan added="+r2.added()+" ms="+(System.currentTimeMillis()-t));
    Map<String,Integer> tags=new TreeMap<>();Pattern p=Pattern.compile("^\\s*<([A-Za-z_][\\w-]*)");int md=0;
    for(var q:e.requests.values()){Matcher m=p.matcher(q.text());if(m.find())tags.merge(m.group(1),1,Integer::sum);if(q.text().startsWith("# "))md++;}
    System.out.println("starting-with-tag="+tags+" starting-with-heading="+md);
    long csv=e.requests.values().stream().filter(q->Solutions.asksCsvCleanup(q.text())).map(ChatEngine.Request::session).distinct().count();
    long docker=e.requests.values().stream().filter(q->Solutions.asksDockerHelp(q.text())).map(ChatEngine.Request::session).distinct().count();
    System.out.println("sessions asking csv-cleanup="+csv+" docker-help="+docker);
    Solutions s=new Solutions(Files.createTempDirectory("loop-real"));var sample=s.sample(e);int chars=0;Set<String> ses=new HashSet<>();for(var x:sample){chars+=Json.text(x.get("text")).length();ses.add(Json.text(x.get("session")));}
    System.out.println("sample size="+sample.size()+" chars="+chars+" sessions="+ses.size()+" (not sent anywhere)");
  }
}
