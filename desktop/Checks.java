import java.nio.file.*;
import java.util.*;
public class Checks {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(ChatEngine.extract("{\"type\":\"response_item\",\"payload\":{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"csv\"}]}}","session").text().equals("csv"),"Codex format");
        check(ChatEngine.extract("{\"type\":\"message\",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"docker\"}]}}","pi").session().equals("pi"),"Pi format");
        check(ChatEngine.extract("{\"type\":\"event_msg\",\"payload\":{\"type\":\"user_message\"}}","s")==null,"Mirrored Codex event ignored");
        check(ChatEngine.extract("{\"role\":\"assistant\",\"content\":\"csv\"}","s")==null,"Assistant ignored");
        check(Json.parse("{\"text\":\"hello\\n\\u0041\"}") instanceof Map,"JSON escapes");
        Path temp=Files.createTempDirectory("loop-checks-");
        try{
            Path chats=Files.createDirectories(temp.resolve(".codex/sessions/day"));
            for(int i=0;i<3;i++)Files.writeString(chats.resolve(i+".jsonl"),"{\"role\":\"user\",\"content\":\"Clean CSV\"}\nmalformed\n");
            ChatEngine e=new ChatEngine();
            List<ChatEngine.Root> roots=ChatEngine.defaults(temp,Map.of());
            var result=e.scan(roots,()->false,u->{});
            check(result.chats()==3&&result.added()==3,"Chats counted per file");
            check(e.scan(roots,()->false,u->{}).added()==0,"Deduplication");
            List<ChatEngine.Root> overlapping=new ArrayList<>(roots);overlapping.add(new ChatEngine.Root("Custom",chats));
            check(e.scan(overlapping,()->false,u->{}).chats()==3,"Overlapping folders do not double-count chats");

            check(e.sessions("csv")==3,"Distinct sessions");
            check(e.scan(roots,()->true,u->{}).chats()==0,"Cancellation");
            Path store=temp.resolve("data/history.tsv");e.save(store);ChatEngine loaded=new ChatEngine();loaded.load(store);check(loaded.requests.equals(e.requests),"Persistence round trip");
            check(ChatEngine.defaults(temp,Map.of("CODEX_HOME",temp.resolve("custom").toString())).stream().anyMatch(r->r.path().equals(temp.resolve("custom/sessions"))),"Environment override");
            check(Csv.clean(" name ,notes\n Alice ,\"a,b\"\n , \nBob,\"line 1\nline \"\"2\"\"\"").equals("name,notes\r\nAlice,\"a,b\"\r\nBob,\"line 1\nline \"\"2\"\"\""),"CSV quoting and blank rows");
            Path broken=temp.resolve("broken.tsv");Files.writeString(broken,Files.readString(store)+"bad history line\n");
            int before=loaded.requests.size();boolean rejected=false;
            try{loaded.load(broken);}catch(java.io.IOException expected){rejected=true;}
            check(rejected&&loaded.requests.size()==before,"Broken history does not partially replace existing state");
            boolean threw=false;try{Csv.clean("\"broken");}catch(IllegalArgumentException expected){threw=true;}check(threw,"Unclosed CSV rejected");
        }finally{try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        FeatureChecks.run();
        System.out.println("All Java checks passed.");
    }
}
