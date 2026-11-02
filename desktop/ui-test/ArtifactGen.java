import java.nio.file.*;import java.util.*;
public class ArtifactGen{public static void main(String[] a)throws Exception{
 Solutions s=new Solutions(Path.of(a[0]));
 String[][] tools={{"csv","Cleaner"},{"transform","</script><img src=x onerror=window.top.pwn=1>Tidy"},{"lesson","Lesson"}};
 for(String[] d:tools){Solutions.Tool t=new Solutions.Tool();t.id=d[0];t.kind=d[0];t.title=d[1];t.description="<b>desc</b>";t.topic=d[0];
  Map<String,Object> v=new LinkedHashMap<>();v.put("content","<img src=x onerror=window.pwn=1> content");v.put("steps",List.of("step <one>"));v.put("quiz",d[0].equals("lesson")?List.of(Map.of("question","Q?","answer","A <b>")):List.of());
  v.put("operations",d[0].equals("transform")?List.of(Map.of("type","trim","from","","to",""),Map.of("type","remove_empty","from","","to",""),Map.of("type","deduplicate","from","","to",""),Map.of("type","replace","from","foo","to","bar")):List.of());t.versions.add(v);
  Files.writeString(Path.of(a[0],d[0]+".html"),Artifacts.html(t));}
}}
