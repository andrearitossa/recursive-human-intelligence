import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;

final class ChatEngine {
    record Root(String tool, Path path) {}
    record Request(String session, String text) {}
    record Folder(String tool, String path, int chats, int added, int skipped) {}
    record Update(String status, int chats, int added, int skipped, List<Folder> folders) {}
    final LinkedHashMap<String,Request> requests=new LinkedHashMap<>();
    /** Chats the user removed; later imports must not bring them back. */
    final Set<String> removed=new HashSet<>();
    Map<String,Request> removeSessions(Set<String> sessions){
        Map<String,Request> taken=new LinkedHashMap<>();
        requests.entrySet().removeIf(e->{if(!sessions.contains(e.getValue().session()))return false;taken.put(e.getKey(),e.getValue());return true;});
        removed.addAll(sessions);return taken;
    }
    void restoreSessions(Map<String,Request> taken){for(Request r:taken.values())removed.remove(r.session());requests.putAll(taken);}
    /** Adding a folder again is the way back: its removed chats return on the next scan. */
    final Set<Path> readding=new HashSet<>();
    void readd(Path folder){Path f=folder.toAbsolutePath().normalize();readding.add(f);stamps.keySet().removeIf(p->p.startsWith(f));}
    private boolean put(Request r){synchronized(requests){return requests.putIfAbsent(id(r),r)==null;}}
    /** A safe copy for the screen while an import is adding to the real map. */
    List<Request> snapshot(){synchronized(requests){return new ArrayList<>(requests.values());}}
    void saveRemoved(Path file)throws IOException{
        Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"removed-",".tmp");
        Files.write(temp,removed);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
    }
    void loadRemoved(Path file)throws IOException{
        removed.clear();if(Files.exists(file))removed.addAll(Files.readAllLines(file));
        requests.values().removeIf(r->removed.contains(r.session()));
    }
    private static final Set<String> JUNK=Set.of("node_modules",".cache",".git",".venv","venv","__pycache__",".uv-cache",".npm",".cargo",".rustup");
    record Stamp(long modified,long size,boolean chat) {}
    final Map<Path,Stamp> stamps=new HashMap<>();
    /** Transcripts are streamed line by line, so long sessions, usually the most useful, stay importable. */
    static final long MAX_FILE=512L*1024*1024;
    /** Wrapper tags that harnesses inject around or before what the user typed. */
    private static final Pattern WRAPPER=Pattern.compile("<([A-Za-z_][\\w-]*)(\\s[^>]*)?>.*?</\\1>|<[A-Za-z_][\\w-]*(\\s[^>]*)?/>",Pattern.DOTALL);
    private static final Pattern INJECTED=Pattern.compile("(?s)^\\s*(# (Context from my IDE setup|AGENTS\\.md instructions|Response annotations|Task context)|Caveat: The messages below|\\[Request interrupted|This session is being continued from a previous conversation|Base directory for this skill:|The following is the Codex agent history).*");
    private static final Pattern PLACEHOLDER=Pattern.compile("\\[external unsupported block: \\w+\\]|\\[Image: source: [^\\]]*\\]|\\[Image #\\d+\\]");
    private static final Pattern PASTED=Pattern.compile("(?s)</?pasted_content[^>]*>");
    /** Returns only what the user wrote, or an empty string for harness-generated messages. */
    static String userText(String raw){
        String text=PLACEHOLDER.matcher(PASTED.matcher(raw).replaceAll("")).replaceAll("");
        text=text.replaceFirst("^\\s*The user sent a new message while you were working:\\s*","");
        text=text.replaceAll("(?s)This is how Claude Code surfaces messages the user sends mid-turn.*?Address the message above as you continue this turn\\.","");
        String previous;
        do{
            previous=text;text=text.strip();
            var match=WRAPPER.matcher(text);
            if(match.lookingAt())text=text.substring(match.end());
            else{ // A trailing block is harness-generated too; tags inside prose are left alone.
                int open=text.lastIndexOf('<');
                if(open>0&&text.endsWith(">")){var tail=WRAPPER.matcher(text);while(tail.find())if(tail.end()==text.length()){text=text.substring(0,tail.start());break;}}
            }
        }while(!text.equals(previous));
        return INJECTED.matcher(text).matches()?"":text.strip();
    }
    static List<Root> defaults(Path home, Map<String,String> env) {
        List<Root> roots=new ArrayList<>(); addHome(roots,home);
        addEnv(roots,env,"CODEX_HOME","Codex","sessions");
        addEnv(roots,env,"CLAUDE_CONFIG_DIR","Claude","projects");
        addEnv(roots,env,"PI_CODING_AGENT_DIR","Pi","sessions");
        addEnv(roots,env,"PI_CODING_AGENT_SESSION_DIR","Pi","");
        // WSL reading Windows installations. Other Windows users can be added manually.
        String profile=env.get("LOOP_WINDOWS_HOME");
        if(profile!=null&&!profile.isBlank())addHome(roots,Path.of(profile));
        return unique(roots);
    }
    static void addEnv(List<Root> roots,Map<String,String> env,String key,String tool,String child){String p=env.get(key);if(p!=null&&!p.isBlank())roots.add(new Root(tool,Path.of(p).resolve(child)));}
    static void addHome(List<Root> roots,Path home){roots.add(new Root("Codex",home.resolve(".codex/sessions")));roots.add(new Root("Claude",home.resolve(".claude/projects")));roots.add(new Root("Pi",home.resolve(".pi/agent/sessions")));}
    static List<Root> unique(List<Root> roots){Map<Path,Root> out=new LinkedHashMap<>();for(Root r:roots)out.putIfAbsent(r.path().toAbsolutePath().normalize(),r);return new ArrayList<>(out.values());}
    static Request extract(String line,String fallback){
        Map<?,?> obj=Json.map(Json.parse(line));String type=Json.text(obj.get("type"));
        if(type.equals("event_msg")||Boolean.TRUE.equals(obj.get("isMeta"))||Boolean.TRUE.equals(obj.get("isSidechain"))||Boolean.TRUE.equals(obj.get("isCompactSummary"))||obj.containsKey("toolUseResult"))return null;
        // Claude marks harness-originated turns with a non-human origin.
        if(obj.get("origin") instanceof Map<?,?> origin&&!"human".equals(origin.get("kind")))return null;
        Map<?,?> message=type.equals("response_item")?Json.map(obj.get("payload")):obj.get("message") instanceof Map?Json.map(obj.get("message")):obj;
        String role=Json.text(message.get("role"));if(role.isEmpty())role=type;
        if(!role.equals("user"))return null;
        Object content=message.get("content");StringBuilder text=new StringBuilder();
        if(content instanceof String)text.append(content);
        else if(content instanceof List)for(Object item:(List<?>)content){Map<?,?> part=Json.map(item);String t=Json.text(part.get("type"));if(t.equals("text")||t.equals("input_text")){if(text.length()>0)text.append('\n');text.append(Json.text(part.get("text")));}}
        String typed=userText(text.toString());if(typed.isEmpty())return null;
        String session=Json.text(obj.get("sessionId"));if(session.isEmpty())session=Json.text(obj.get("session_id"));if(session.isEmpty())session=fallback;
        return new Request(session,typed);
    }
    /** Compares by text so Windows, UNC-to-WSL, and Linux spellings of a project path all match. */
    static String comparable(String path){String out=path.replace('\\','/').replaceFirst("(?i)^//(wsl\\.localhost|wsl\\$)/[^/]+","").replaceAll("/+$","");return out.toLowerCase(Locale.ROOT);}
    static boolean isExcluded(String path,String folderName,List<Path> excluded){
        String candidate=comparable(path);
        for(Path x:excluded){String blocked=comparable(x.toString());if(candidate.equals(blocked)||candidate.startsWith(blocked+"/"))return true;
            // Claude stores a project under its path with separators replaced by dashes.
            if(folderName!=null&&folderName.equalsIgnoreCase(x.toString().replace("\\\\wsl.localhost\\","").replaceAll("[^A-Za-z0-9]","-")))return true;
            if(folderName!=null&&folderName.equalsIgnoreCase(blocked.replaceAll("[^a-z0-9]","-")))return true;}
        return false;
    }
    static String id(Request request){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((request.session()+"\0"+request.text()).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    Update scan(List<Root> roots,BooleanSupplier stopped,Consumer<Update> progress)throws IOException{return scan(roots,stopped,progress,List.of());}
    Update scan(List<Root> roots,BooleanSupplier stopped,Consumer<Update> progress,List<Path> excluded)throws IOException{
        Map<String,Folder> folders=new LinkedHashMap<>();Set<Path> seenFiles=new HashSet<>();int[] totals={0,0,0};long[] last={0};
        Consumer<String> publish=status->{if(System.currentTimeMillis()-last[0]>100){progress.accept(new Update(status,totals[0],totals[1],totals[2],new ArrayList<>(folders.values())));last[0]=System.currentTimeMillis();}};
        for(Root root:unique(roots)){
            if(stopped.getAsBoolean())break;
            progress.accept(new Update("Checking "+root.tool()+" · "+root.path(),totals[0],totals[1],totals[2],new ArrayList<>(folders.values())));
            if(!Files.exists(root.path())){folders.put(root.path().toString(),new Folder(root.tool(),root.path()+" (not found)",0,0,0));Activity.log(root.tool()+": not found at "+root.path());continue;}
            int[] before=totals.clone();
            Files.walkFileTree(root.path(),EnumSet.noneOf(FileVisitOption.class),64,new SimpleFileVisitor<Path>(){
                public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes attrs){if(p.getFileName()!=null&&JUNK.contains(p.getFileName().toString()))return FileVisitResult.SKIP_SUBTREE;if(p.getFileName()!=null&&p.getFileName().toString().equals("subagents")&&(root.tool().equals("Claude")||p.toString().contains(".claude")))return FileVisitResult.SKIP_SUBTREE;if(p.getFileName()!=null&&isExcluded(p.toString(),p.getFileName().toString(),excluded))return FileVisitResult.SKIP_SUBTREE;publish.accept("Checking "+p);return stopped.getAsBoolean()?FileVisitResult.TERMINATE:FileVisitResult.CONTINUE;}
                public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){
                    if(stopped.getAsBoolean())return FileVisitResult.TERMINATE;
                    if(isExcluded(file.toString(),null,excluded))return FileVisitResult.CONTINUE;
                    if(!attrs.isRegularFile()||!file.toString().toLowerCase(Locale.ROOT).endsWith(".jsonl"))return FileVisitResult.CONTINUE;
                    if(!seenFiles.add(file.toAbsolutePath().normalize()))return FileVisitResult.CONTINUE;
                    String folder=file.getParent().toString(),key=root.tool()+"\0"+folder;
                    Folder old=folders.getOrDefault(key,new Folder(root.tool(),folder,0,0,0));
                    boolean chat=false;int added=0,skipped=0;
                    Path canonical=file.toAbsolutePath().normalize();Stamp stamp=stamps.get(canonical);
                    if(stamp!=null&&stamp.modified()==attrs.lastModifiedTime().toMillis()&&stamp.size()==attrs.size()){
                        int n=stamp.chat()?1:0;totals[0]+=n;folders.put(key,new Folder(root.tool(),folder,old.chats()+n,old.added(),old.skipped()));publish.accept("Already imported · "+file);return FileVisitResult.CONTINUE;
                    }
                    try{
                        if(attrs.size()>MAX_FILE)throw new IOException("File is too large");
                        List<Request> pending=new ArrayList<>();boolean discard=false;
                        // A stray invalid byte must not lose a whole chat, so it is replaced rather than rejected.
                        try(BufferedReader reader=new BufferedReader(new InputStreamReader(Files.newInputStream(file),StandardCharsets.UTF_8))){
                            String line;while((line=reader.readLine())!=null){if(stopped.getAsBoolean())return FileVisitResult.TERMINATE;if(!line.contains("\"user\"")&&!line.contains("\"cwd\""))continue;try{if(!pending.isEmpty()||!discard){Map<?,?> head=Json.map(Json.parse(line));Map<?,?> payload=Json.map(head.get("payload"));String cwd=Json.text(head.containsKey("cwd")?head.get("cwd"):payload.get("cwd"));if(!cwd.isEmpty()&&isExcluded(cwd,null,excluded))discard=true;if(Json.text(head.get("type")).equals("session_meta")&&payload.get("parent_thread_id")!=null)discard=true;}if(discard)break;Request r=extract(line,file.toAbsolutePath().normalize().toString());if(r!=null){chat=true;pending.add(r);}}catch(IllegalArgumentException ignored){}}
                        }
                        if(discard){stamps.remove(canonical);return FileVisitResult.CONTINUE;}
                        if(readding.stream().anyMatch(canonical::startsWith))for(Request r:pending)removed.remove(r.session());
                        for(Request r:pending)if(!removed.contains(r.session())&&put(r))added++;
                        stamps.put(canonical,new Stamp(attrs.lastModifiedTime().toMillis(),attrs.size(),chat));
                    }catch(IOException e){skipped=1;chat=false;Activity.log("Cannot read "+file+": "+e.getClass().getSimpleName());}
                    totals[0]+=chat?1:0;totals[1]+=added;totals[2]+=skipped;
                    folders.put(key,new Folder(root.tool(),folder,old.chats()+(chat?1:0),old.added()+added,old.skipped()+skipped));
                    publish.accept("Reading "+file);return FileVisitResult.CONTINUE;
                }
                public FileVisitResult visitFileFailed(Path file,IOException error){if(!file.toString().toLowerCase(Locale.ROOT).endsWith(".jsonl"))return FileVisitResult.CONTINUE;totals[2]++;Activity.log("Cannot open "+file+": "+error.getClass().getSimpleName());folders.put(file.toString(),new Folder(root.tool(),file+" (unreadable)",0,0,1));publish.accept("Cannot read "+file);return stopped.getAsBoolean()?FileVisitResult.TERMINATE:FileVisitResult.CONTINUE;}
            });
            int found=totals[0]-before[0],fresh=totals[1]-before[1],bad=totals[2]-before[2];
            Activity.log(root.tool()+": "+(found==0?"no chats":found+(found==1?" chat, ":" chats, ")+fresh+(fresh==1?" new request":" new requests"))+(bad>0?", "+bad+" unreadable":"")+" at "+root.path());
        }
        if(!stopped.getAsBoolean())readding.clear();
        Update result=new Update(stopped.getAsBoolean()?"Stopped · completed files retained":"Import complete",totals[0],totals[1],totals[2],new ArrayList<>(folders.values()));progress.accept(result);return result;
    }
    void loadIndex(Path file)throws IOException{
        stamps.clear();if(requests.isEmpty()||!Files.exists(file))return;
        try(BufferedReader in=Files.newBufferedReader(file)){String line;while((line=in.readLine())!=null){String[] p=line.split("\t");if(p.length!=4)continue;try{stamps.put(Path.of(new String(Base64.getDecoder().decode(p[0]),StandardCharsets.UTF_8)),new Stamp(Long.parseLong(p[1]),Long.parseLong(p[2]),Boolean.parseBoolean(p[3])));}catch(IllegalArgumentException ignored){}}}
    }
    void saveIndex(Path file)throws IOException{
        Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"index-",".tmp");
        try(BufferedWriter out=Files.newBufferedWriter(temp)){for(var e:stamps.entrySet()){out.write(Base64.getEncoder().encodeToString(e.getKey().toString().getBytes(StandardCharsets.UTF_8))+"\t"+e.getValue().modified()+"\t"+e.getValue().size()+"\t"+e.getValue().chat());out.newLine();}}
        Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
    }
    int sessions(String regex){Pattern p=Pattern.compile(regex,Pattern.CASE_INSENSITIVE);Set<String> sessions=new HashSet<>();for(Request r:requests.values())if(p.matcher(r.text()).find())sessions.add(r.session());return sessions.size();}
    void save(Path file)throws IOException{
        Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"loop-",".tmp");
        try(BufferedWriter out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){for(Request r:requests.values()){out.write(Base64.getEncoder().encodeToString(r.session().getBytes(StandardCharsets.UTF_8)));out.write('\t');out.write(Base64.getEncoder().encodeToString(r.text().getBytes(StandardCharsets.UTF_8)));out.newLine();}}
        try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    void load(Path file)throws IOException{
        if(!Files.exists(file))return;
        LinkedHashMap<String,Request> loaded=new LinkedHashMap<>();
        try(BufferedReader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            String line;int number=0;
            while((line=in.readLine())!=null){
                number++;String[] parts=line.split("\t",2);
                if(parts.length!=2)throw new IOException("Invalid saved history at line "+number);
                try{
                    String typed=userText(new String(Base64.getDecoder().decode(parts[1]),StandardCharsets.UTF_8));if(typed.isEmpty())continue;Request r=new Request(new String(Base64.getDecoder().decode(parts[0]),StandardCharsets.UTF_8),typed);loaded.put(id(r),r);
                }catch(IllegalArgumentException e){throw new IOException("Invalid saved history at line "+number,e);}
            }
        }
        requests.clear();requests.putAll(loaded);
    }
}
