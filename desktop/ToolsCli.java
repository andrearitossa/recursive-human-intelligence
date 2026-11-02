import java.nio.file.*;
import java.util.*;

/** Read-only library lookup for agent harnesses or a terminal. */
final class ToolsCli {
    static void search(String query)throws Exception{
        Solutions store=new Solutions(Path.of(System.getProperty("user.home"),".loop"));store.load();
        String q=query.toLowerCase(Locale.ROOT);List<Map<String,Object>> found=new ArrayList<>();
        for(Solutions.Tool t:store.tools){if(t.archived||!(t.title+" "+t.description+" "+t.topic).toLowerCase(Locale.ROOT).contains(q))continue;
            // Export only validated, application-rendered artifacts; no model call.
            Path artifact=Artifacts.write(store,t);found.add(Map.of("id",t.id,"title",t.title,"description",t.description,"kind",t.kind,"version",t.versions.size(),"path",artifact.toString()));
        }
        System.out.println(Json.write(found));
    }
}
