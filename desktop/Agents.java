import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/** Reads agent transcripts, read-only, into per-project activity. Nothing here leaves the computer. */
final class Agents {
    static final class Turn{double start,end;String prompt="",outcome="done";int tools;}
    static final class Session implements Cloneable{
        String tool,id,path,cwd,title,origin,branch,latest,status="done",tty,bridge,liveEntry;
        double first,last,cost;boolean pending,hidden,live,archived;long offset,size,mtime,pid;Path home;List<Turn> turns=new ArrayList<>();
        String key(){return tool+":"+id;}
        boolean wsl(){return path.startsWith("\\\\wsl");}
        boolean windows(){return WINDOWS&&!wsl();}
        String surface(){
            String o=origin==null?"":origin;
            if(tool.equals("Pi"))return "Pi CLI";
            if(tool.equals("Claude"))return o.equals("claude-desktop")?"Claude Desktop":o.equals("claude-vscode")?"Claude in VS Code":"Claude Code";
            return o.equals("Codex Desktop")?"ChatGPT app":o.equals("codex_vscode")?"Codex in VS Code":"Codex CLI";
        }
        String name(){if(title!=null&&!title.isBlank())return title;for(Turn t:turns)if(!t.prompt.isEmpty()&&!t.prompt.matches("/\\S+\\s*"))return t.prompt;return "Untitled session";}
        boolean active(){return status.equals("working")||status.equals("waiting");}
        boolean chat(){return cwd==null||cwd.isEmpty()||SCRATCH.matcher(cwd).find();}
        Session snapshot(){try{Session s=(Session)clone();s.turns=new ArrayList<>(turns);return s;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
    }
    static final class Project{
        final String key,name;final boolean chat;final List<Session> sessions;
        Project(String key,String name,boolean chat,List<Session> sessions){this.key=key;this.name=name;this.chat=chat;this.sessions=sessions;}
        /** The session a card speaks for: status, summary, and buttons all come from this one. */
        Session lead(){return sessions.stream().filter(Session::active).min(Comparator.comparingInt(s->s.status.equals("working")?0:1)).orElseGet(()->sessions.stream().max(Comparator.comparingDouble(s->s.last)).orElse(null));}
        double last(){return sessions.stream().mapToDouble(s->s.last).max().orElse(0);}
    }
    record Event(double ts,String kind,String text){}
    record Proc(long pid,String comm,String start,String cwd,String tty){}

    static final boolean WINDOWS=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");
    static final Pattern SCRATCH=Pattern.compile("[\\\\/]scratch-workspaces[\\\\/]|[\\\\/]Claude-Data[\\\\/]");
    private static final int MAX_TURNS=400,MAX_LINE=4_000_000;
    private static final long STALL=600;
    private static final String[] NOISE={"<local-command","<command-name","<command-message","<system-reminder","<task-notification","<environment_context","<user_instructions","<permissions","<turn_aborted","<subagent_notification","<user_shell_command","Caveat:","[Request interrupted","Base directory for this skill","This session is being continued","<bash-","<collaboration"};
    private static final String PROC_SCRIPT="for p in /proc/[0-9]*; do c=$(cat $p/comm 2>/dev/null); case \"$c\" in claude|codex|pi) n=${p#/proc/}; s=$(sed 's/.*) //' $p/stat 2>/dev/null | cut -d' ' -f20); echo \"P|$n|$c|$s|$(readlink $p/fd/0)|$(readlink $p/cwd)\"; [ \"$c\" = codex ] && for f in $p/fd/*; do t=$(readlink $f); case \"$t\" in */rollout-*.jsonl) echo \"R|$n|$t\";; esac; done;; esac; done";

    private final Map<String,Session> sessions=new HashMap<>();
    private final Map<String,String> roots=new HashMap<>();
    private final Map<String,Map<String,Object>> desktop=new HashMap<>();
    private long desktopAt=0;
    private final Path indexFile=Path.of(System.getProperty("user.home"),".loop","agents-index.json");
    private boolean indexLoaded=false;
    volatile int readDone=0,readTotal=0;

    // ---- Parsing ----
    static double time(Object value){try{return Instant.parse(Json.text(value)).toEpochMilli()/1000.0;}catch(RuntimeException e){return 0;}}
    static String clip(String text,int n){
        String t=(text==null?"":text).replaceAll("[*`#]+","").replaceAll("\\s+"," ").strip();
        return t.length()<=n?t:t.substring(0,n-1)+"…";
    }
    static String text(Object content){
        if(content instanceof String s)return s;
        if(!(content instanceof List<?> list))return "";
        StringBuilder out=new StringBuilder();
        for(Object o:list){Map<?,?> b=Json.map(o);String type=Json.text(b.get("type"));if(type.equals("text")||type.equals("input_text")||type.equals("output_text")){if(out.length()>0)out.append('\n');out.append(Json.text(b.get("text")));}}
        return out.toString();
    }
    static boolean prompt(String text){String t=text.strip();if(t.isEmpty())return false;for(String n:NOISE)if(t.startsWith(n))return false;return true;}
    private static int count(Object content,String type){int n=0;if(content instanceof List<?> l)for(Object o:l)if(type.equals(Json.text(Json.map(o).get("type"))))n++;return n;}
    private static void touch(Session s,double ts){if(ts>0){if(s.first==0)s.first=ts;s.last=Math.max(s.last,ts);}}
    private static void addTurn(Session s,double ts,String text){Turn t=new Turn();t.start=t.end=ts;t.prompt=clip(text,240);s.turns.add(t);if(s.turns.size()>MAX_TURNS)s.turns.remove(0);}
    private static Turn current(Session s,double ts){if(s.turns.isEmpty())addTurn(s,ts,"");return s.turns.get(s.turns.size()-1);}

    static void applyClaude(Session s,Map<?,?> o){
        String kind=Json.text(o.get("type"));
        if(kind.equals("ai-title"))s.title=Json.text(o.get("aiTitle"));
        if(kind.equals("custom-title"))s.title=Json.text(o.get("customTitle"));
        if(kind.equals("cost-state")&&o.get("totalCostUSD") instanceof Number n)s.cost=n.doubleValue();
        if(Boolean.TRUE.equals(o.get("isSidechain"))||!(kind.equals("user")||kind.equals("assistant")||kind.equals("system")))return;
        double ts=time(o.get("timestamp"));
        if(!Json.text(o.get("entrypoint")).isEmpty())s.origin=Json.text(o.get("entrypoint"));
        if(!Json.text(o.get("cwd")).isEmpty())s.cwd=Json.text(o.get("cwd"));
        if(!Json.text(o.get("gitBranch")).isEmpty())s.branch=Json.text(o.get("gitBranch"));
        if(kind.equals("system")){
            String sub=Json.text(o.get("subtype"));
            if(sub.equals("away_summary"))s.latest=clip(Json.text(o.get("content")),400);
            if(sub.equals("turn_duration")&&ts>0&&!s.turns.isEmpty()){Turn t=s.turns.get(s.turns.size()-1);t.end=Math.max(t.end,ts);s.pending=false;}
            return;
        }
        if(ts==0)return;
        touch(s,ts);Map<?,?> msg=Json.map(o.get("message"));Object content=msg.get("content");String body=text(content);
        if(kind.equals("user")){
            if(body.contains("[Request interrupted")&&!s.turns.isEmpty()){s.turns.get(s.turns.size()-1).outcome="interrupted";s.pending=false;}
            else if(count(content,"tool_result")>0){s.pending=true;if(!s.turns.isEmpty())s.turns.get(s.turns.size()-1).end=ts;}
            else if(!Boolean.TRUE.equals(o.get("isMeta"))&&prompt(body)){addTurn(s,ts,body);s.pending=true;}
            return;
        }
        Turn t=current(s,ts);t.end=ts;t.tools+=count(content,"tool_use");
        if(Boolean.TRUE.equals(o.get("isApiErrorMessage")))t.outcome="error";
        if(!body.isBlank())s.latest=clip(body,400);
        String stop=Json.text(msg.get("stop_reason"));if(!stop.isEmpty())s.pending=stop.equals("tool_use");
    }
    static void applyCodex(Session s,Map<?,?> o){
        String kind=Json.text(o.get("type"));Map<?,?> p=Json.map(o.get("payload"));double ts=time(o.get("timestamp"));String sub=Json.text(p.get("type"));
        if(kind.equals("session_meta")){
            s.cwd=Json.text(p.get("cwd"));s.origin=Json.text(p.get("originator"));
            s.hidden=Json.text(p.get("thread_source")).equals("subagent")||String.valueOf(p.get("source")).contains("subagent")||p.containsKey("parent_thread_id");
            if(s.first==0)s.first=ts;return;
        }
        if(kind.equals("turn_context")){if(!Json.text(p.get("cwd")).isEmpty())s.cwd=Json.text(p.get("cwd"));return;}
        if(ts==0||!(kind.equals("event_msg")||kind.equals("response_item")))return;
        touch(s,ts);
        if(kind.equals("event_msg")){
            if(sub.equals("task_started"))s.pending=true;
            else if(sub.equals("task_complete")){s.pending=false;current(s,ts).end=ts;String m=Json.text(p.get("last_agent_message"));if(!m.isEmpty())s.latest=clip(m,400);}
            else if(sub.equals("turn_aborted")){s.pending=false;Turn t=current(s,ts);t.end=ts;t.outcome="interrupted";}
            else if(sub.equals("error"))current(s,ts).outcome="error";
            return;
        }
        if(sub.equals("message")){
            String body=text(p.get("content")),role=Json.text(p.get("role"));
            if(role.equals("user")&&prompt(body))addTurn(s,ts,body);
            else if(role.equals("assistant")){current(s,ts).end=ts;if(!body.isBlank())s.latest=clip(body,400);}
        }else if(sub.equals("custom_tool_call")||sub.equals("function_call")){Turn t=current(s,ts);t.end=ts;t.tools++;}
        else if(sub.endsWith("_output")&&!s.turns.isEmpty())s.turns.get(s.turns.size()-1).end=ts;
    }
    static void applyPi(Session s,Map<?,?> o){
        String kind=Json.text(o.get("type"));double ts=time(o.get("timestamp"));
        if(kind.equals("session")){s.cwd=Json.text(o.get("cwd"));if(s.first==0)s.first=ts;}
        if(!kind.equals("message")||ts==0)return;
        touch(s,ts);Map<?,?> msg=Json.map(o.get("message"));String role=Json.text(msg.get("role"));Object content=msg.get("content");String body=text(content);
        if(role.equals("user")){if(prompt(body)){addTurn(s,ts,body);s.pending=true;}}
        else if(role.equals("toolResult")){s.pending=true;if(!s.turns.isEmpty())s.turns.get(s.turns.size()-1).end=ts;}
        else if(role.equals("assistant")){
            Turn t=current(s,ts);t.end=ts;t.tools+=count(content,"toolCall");if(!body.isBlank())s.latest=clip(body,400);
            String stop=Json.text(msg.get("stopReason"));s.pending=stop.equals("toolUse");
            if(stop.equals("aborted"))t.outcome="interrupted";if(stop.equals("error"))t.outcome="error";
        }
    }
    static void apply(Session s,Map<?,?> o){switch(s.tool){case "Claude"->applyClaude(s,o);case "Codex"->applyCodex(s,o);default->applyPi(s,o);}}
    /** Reads only bytes added since the last pass. A half-written last line waits for the next one. */
    private static void read(Session s)throws IOException{
        try(InputStream in=new BufferedInputStream(Files.newInputStream(Path.of(s.path)),1<<16)){
            long skip=s.offset;while(skip>0){long n=in.skip(skip);if(n<=0)return;skip-=n;}
            ByteArrayOutputStream line=new ByteArrayOutputStream();long seen=0;int b;
            while((b=in.read())>=0){
                seen++;
                if(b!='\n'){if(line.size()<=MAX_LINE)line.write(b);continue;}
                s.offset+=seen;seen=0;
                if(line.size()<=MAX_LINE){
                    String text=line.toString(StandardCharsets.UTF_8);
                    if(!text.startsWith("{\"type\":\"api-request")&&!text.startsWith("{\"type\":\"file-history"))
                        try{apply(s,Json.map(Json.parse(text)));}catch(RuntimeException ignored){}
                }
                line.reset();
            }
        }
    }

    // ---- Index: one read of every log can take a minute, so later launches read only what is new ----
    private void loadIndex(){
        indexLoaded=true;
        try{
            for(Object item:(List<?>)Json.parse(Files.readString(indexFile))){
                Map<?,?> o=Json.map(item);Session s=new Session();
                s.tool=Json.text(o.get("tool"));s.id=Json.text(o.get("id"));s.path=Json.text(o.get("path"));s.home=Path.of(Json.text(o.get("home")));
                s.cwd=(String)o.get("cwd");s.title=(String)o.get("title");s.origin=(String)o.get("origin");s.branch=(String)o.get("branch");s.latest=(String)o.get("latest");
                s.first=num(o.get("first"));s.last=num(o.get("last"));s.cost=num(o.get("cost"));s.pending=Boolean.TRUE.equals(o.get("pending"));s.hidden=Boolean.TRUE.equals(o.get("hidden"));
                s.offset=(long)num(o.get("offset"));s.size=(long)num(o.get("size"));s.mtime=(long)num(o.get("mtime"));
                for(Object raw:(List<?>)o.get("turns")){Map<?,?> m=Json.map(raw);Turn t=new Turn();t.start=num(m.get("start"));t.end=num(m.get("end"));t.prompt=Json.text(m.get("prompt"));t.outcome=Json.text(m.get("outcome"));t.tools=(int)num(m.get("tools"));s.turns.add(t);}
                sessions.put(s.key(),s);
            }
        }catch(IOException|RuntimeException e){sessions.clear();} // Unreadable means rebuild; the transcripts are the source of truth.
    }
    private static double num(Object o){return o instanceof Number n?n.doubleValue():0;}
    private void saveIndex(){
        try{
            List<Object> all=new ArrayList<>();
            for(Session s:sessions.values()){
                Map<String,Object> o=new LinkedHashMap<>();
                o.put("tool",s.tool);o.put("id",s.id);o.put("path",s.path);o.put("home",s.home.toString());o.put("cwd",s.cwd);o.put("title",s.title);o.put("origin",s.origin);o.put("branch",s.branch);o.put("latest",s.latest);
                o.put("first",s.first);o.put("last",s.last);o.put("cost",s.cost);o.put("pending",s.pending);o.put("hidden",s.hidden);o.put("offset",s.offset);o.put("size",s.size);o.put("mtime",s.mtime);
                List<Object> turns=new ArrayList<>();for(Turn t:s.turns){Map<String,Object> m=new LinkedHashMap<>();m.put("start",t.start);m.put("end",t.end);m.put("prompt",t.prompt);m.put("outcome",t.outcome);m.put("tools",t.tools);turns.add(m);}
                o.put("turns",turns);all.add(o);
            }
            Files.createDirectories(indexFile.getParent());Path tmp=indexFile.resolveSibling("agents-index.tmp");Files.writeString(tmp,Json.write(all));Files.move(tmp,indexFile,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException|RuntimeException ignored){} // Only costs a slower next launch.
    }

    // ---- Refresh ----
    /** Chats the user removed or whose project they skip; Dashboard and Chats both obey it. */
    volatile java.util.function.Predicate<Session> hide=s->false;
    /** Lets the README demo stage live states for fictional sessions; null in the real app. */
    static volatile java.util.function.Consumer<Session> stage;
    /** Every listed session, newest first: the one list of chats behind Dashboard, Chats, and the import. */
    static List<Session> chats(List<Project> projects){List<Session> out=new ArrayList<>();for(Project p:projects)out.addAll(p.sessions);out.sort(Comparator.comparingDouble((Session s)->-s.last));return out;}
    synchronized List<Project> refresh(List<ChatEngine.Root> chatRoots){
        if(!indexLoaded)loadIndex();
        Set<String> seen=new HashSet<>();boolean changed=false;List<Object[]> work=new ArrayList<>();
        for(ChatEngine.Root root:chatRoots){
            if(!(root.tool().equals("Claude")||root.tool().equals("Codex")||root.tool().equals("Pi"))||!Files.isDirectory(root.path()))continue;
            int depth=root.tool().equals("Codex")?6:2;
            List<Path> files;
            try(Stream<Path> walk=Files.walk(root.path(),depth)){files=walk.filter(p->p.toString().endsWith(".jsonl")).toList();}catch(IOException|UncheckedIOException e){continue;}
            Path home=root.path().getParent();if(root.tool().equals("Pi"))home=home.getParent();home=home.getParent();
            for(Path file:files)work.add(new Object[]{root,home,file});
        }
        readTotal=work.size();readDone=0;
        for(Object[] job:work){
            ChatEngine.Root root=(ChatEngine.Root)job[0];Path home=(Path)job[1],file=(Path)job[2];readDone++;
            {
                String stem=file.getFileName().toString().replaceFirst("\\.jsonl$","");
                String id=root.tool().equals("Codex")?stem.substring(Math.max(0,stem.length()-36)):root.tool().equals("Pi")?stem.substring(stem.indexOf('_')+1):stem;
                String key=root.tool()+":"+id;if(!seen.add(key))continue; // A mirrored session keeps its first copy.
                try{
                    long size=Files.size(file),mtime=Files.getLastModifiedTime(file).toMillis();Session s=sessions.get(key);
                    if(s!=null&&s.size==size&&s.mtime==mtime)continue;
                    if(s==null||size<s.offset||!s.path.equals(file.toString())){s=new Session();s.tool=root.tool();s.id=id;s.path=file.toString();s.home=home;sessions.put(key,s);}
                    read(s);s.size=size;s.mtime=mtime;changed=true;
                }catch(IOException ignored){}
            }
        }
        changed|=sessions.keySet().retainAll(seen);
        if(changed)saveIndex();
        loadDesktop(chatRoots);markLive();
        Map<String,List<Session>> grouped=new LinkedHashMap<>();
        for(Session s:sessions.values()){
            if(s.hidden||s.turns.isEmpty()||(s.cwd!=null&&s.cwd.startsWith("/tmp"))||hide.test(s))continue;
            Map<String,Object> meta=desktop.get(s.id);if(meta!=null&&Boolean.TRUE.equals(meta.get("archived")))continue;
            Session view=s.snapshot();if(view.cwd==null)view.cwd="";if(meta!=null&&meta.get("title") instanceof String t&&!t.isBlank())view.title=t;if(stage!=null)stage.accept(view);
            String key=view.chat()?"chat:"+view.key():projectRoot(view);
            grouped.computeIfAbsent(key,k->new ArrayList<>()).add(view);
        }
        List<Project> out=new ArrayList<>();
        grouped.forEach((key,list)->{
            list.sort(Comparator.comparingInt((Session s)->rank(s.status)).thenComparing(s->-s.last));
            boolean chat=key.startsWith("chat:");
            String clean=key.replace('\\','/').replaceAll("/+$","");
            out.add(new Project(key,chat?list.get(0).name():clean.substring(clean.lastIndexOf('/')+1),chat,list));
        });
        out.sort(Comparator.comparingInt((Project p)->p.lead().active()?rank(p.lead().status):9).thenComparing(p->-p.last()));
        return out;
    }
    static int rank(String status){return switch(status){case "working"->0;case "waiting"->1;case "stalled"->2;case "error"->3;default->4;};}

    private void loadDesktop(List<ChatEngine.Root> chatRoots){
        if(System.currentTimeMillis()-desktopAt<30000)return;desktopAt=System.currentTimeMillis();desktop.clear();
        for(ChatEngine.Root r:chatRoots){
            if(!r.tool().equals(ChatEngine.DESKTOP))continue;
            Path data=r.path();if(!Files.isDirectory(data))continue;
            try(Stream<Path> walk=Files.walk(data,6)){
                for(Path f:walk.filter(p->{String n=p.getFileName().toString();return n.startsWith("local_")&&n.endsWith(".json")&&p.toString().contains("claude-code-sessions");}).toList()){
                    try{
                        Map<?,?> o=Json.map(Json.parse(Files.readString(f)));Map<String,Object> info=new HashMap<>();info.put("title",o.get("title"));info.put("archived",o.get("isArchived"));
                        desktop.put(Json.text(o.get("cliSessionId")),info);
                        if(o.get("priorCliSessionIds") instanceof List<?> prior)for(Object id:prior)desktop.put(Json.text(id),info);
                    }catch(IOException|RuntimeException ignored){}
                }
            }catch(IOException|UncheckedIOException ignored){}
        }
    }

    /** A Linux cwd seen from Windows needs its distribution's prefix, and the reverse for /mnt. */
    static Path local(Session s){
        String cwd=s.cwd;Matcher win=Pattern.compile("^([A-Za-z]):[\\\\/](.*)").matcher(cwd),mnt=Pattern.compile("^/mnt/([a-z])/(.*)").matcher(cwd);
        try{
            if(WINDOWS&&mnt.find())return Path.of(mnt.group(1)+":\\"+mnt.group(2).replace('/','\\'));
            if(WINDOWS&&cwd.startsWith("/")&&s.wsl())return Path.of("\\\\wsl.localhost\\"+distro(s)+cwd.replace('/','\\'));
            if(!WINDOWS&&win.find())return Path.of("/mnt/"+win.group(1).toLowerCase(Locale.ROOT)+"/"+win.group(2).replace('\\','/'));
            return Path.of(cwd);
        }catch(InvalidPathException e){return null;}
    }
    static String distro(Session s){String[] parts=s.path.replace('\\','/').split("/+");return parts.length>2?parts[2]:"";}
    /** Sub-folders and worktrees group under their repository, and one folder is one project from either OS. */
    private String projectRoot(Session s){
        return roots.computeIfAbsent(s.cwd,c->{
            Path p=local(s),found=p;
            for(Path d=p;d!=null&&d.getParent()!=null;d=d.getParent()){
                if(d.equals(s.home))break;
                Path git=d.resolve(".git");
                try{
                    if(Files.isDirectory(git)){found=d;break;}
                    if(Files.isRegularFile(git)){Matcher m=Pattern.compile("gitdir:\\s*(.+?)[/\\\\]\\.git[/\\\\]worktrees[/\\\\]").matcher(Files.readString(git));found=m.find()?Path.of(m.group(1)):d;break;}
                }catch(IOException|RuntimeException e){break;}
            }
            String text=(found==null?c:found.toString()).replace('\\','/');
            text=text.replaceFirst("(?i)^//wsl[^/]*/[^/]+","");
            Matcher drive=Pattern.compile("^([A-Za-z]):/(.*)").matcher(text);if(drive.find())text="/mnt/"+drive.group(1).toLowerCase(Locale.ROOT)+"/"+drive.group(2);
            return text.replaceAll("/+$","");
        });
    }

    // ---- Live processes ----
    private void markLive(){
        Map<String,List<Proc>> procs=new HashMap<>();Map<String,Map<String,Long>> rollouts=new HashMap<>();
        for(Session s:sessions.values()){
            String side=s.wsl()?distro(s):s.windows()?"":"linux";
            if(!side.isEmpty()&&!procs.containsKey(side))scanProcs(side,procs,rollouts);
        }
        Map<String,Session> newestPi=new HashMap<>();
        for(Session s:sessions.values())if(s.tool.equals("Pi")&&s.cwd!=null)newestPi.merge(s.cwd,s,(a,b)->a.last>=b.last?a:b);
        Map<String,Map<?,?>> claude=new HashMap<>();Set<Path> homes=new HashSet<>();
        for(Session s:sessions.values())if(s.tool.equals("Claude")&&homes.add(s.home)){
            try(DirectoryStream<Path> files=Files.newDirectoryStream(s.home.resolve(".claude/sessions"),"*.json")){
                for(Path f:files)try{Map<?,?> o=Json.map(Json.parse(Files.readString(f)));claude.put(s.home+"|"+Json.text(o.get("sessionId")),o);}catch(IOException|RuntimeException ignored){}
            }catch(IOException|RuntimeException ignored){}
        }
        double now=System.currentTimeMillis()/1000.0;
        for(Session s:sessions.values()){
            s.live=false;s.tty=null;s.bridge=null;s.pid=0;s.liveEntry=null;String side=s.wsl()?distro(s):"linux";List<Proc> list=procs.getOrDefault(side,List.of());String registry=null;
            double quiet=now-s.mtime/1000.0;
            if(s.tool.equals("Claude")){
                Map<?,?> o=claude.get(s.home+"|"+s.id);
                if(o!=null&&o.get("pid") instanceof Number n){
                    long pid=n.longValue();String start=Json.text(o.get("procStart"));
                    Proc proc=list.stream().filter(p->p.pid==pid&&p.start.equals(start)).findFirst().orElse(null);
                    boolean alive=s.windows()?ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false):proc!=null||(list.isEmpty()&&!s.wsl()&&ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
                    if(alive){s.live=true;s.pid=pid;s.tty=proc==null?null:proc.tty;s.bridge=Json.text(o.get("bridgeSessionId"));s.liveEntry=Json.text(o.get("entrypoint"));registry=Json.text(o.get("status"));}
                }
                if(s.live&&registry.equals("busy")){s.status="working";continue;}
                if(s.live&&(registry.equals("waiting")||registry.equals("blocked")||!"claude-desktop".equals(s.liveEntry)||now-s.last<86400)){s.status="waiting";continue;}
            }else if(s.tool.equals("Codex")){
                Long holder=rollouts.getOrDefault(side,Map.of()).get(s.path.startsWith("\\\\wsl")?s.path.replace('\\','/').replaceFirst("^//[^/]+/[^/]+",""):s.path);
                if(holder!=null){
                    Proc tui=list.stream().filter(p->p.comm.equals("codex")&&p.tty.startsWith("/dev/pts/")&&p.cwd.equals(s.cwd)).findFirst().orElse(null);
                    if(s.pending||tui!=null){s.live=true;s.pid=tui!=null?tui.pid:holder;s.tty=tui==null?null:tui.tty;s.status=s.pending?(quiet<STALL?"working":"stalled"):"waiting";continue;}
                }
            }else if(s.cwd!=null&&newestPi.get(s.cwd)==s){
                Proc pi=list.stream().filter(p->p.comm.equals("pi")&&p.cwd.equals(s.cwd)).findFirst().orElse(null);
                if(pi!=null){s.live=true;s.pid=pi.pid;s.tty=pi.tty.startsWith("/dev/pts/")?pi.tty:null;s.status=!s.pending?"waiting":quiet<STALL?"working":"stalled";continue;}
            }
            if(s.pending)s.status=quiet<(s.windows()?STALL:90)?"working":"stalled";
            else if(!s.turns.isEmpty()&&s.turns.get(s.turns.size()-1).outcome.equals("error"))s.status="error";
            else s.status="done";
        }
    }
    private static void scanProcs(String side,Map<String,List<Proc>> procs,Map<String,Map<String,Long>> rollouts){
        List<Proc> list=new ArrayList<>();Map<String,Long> held=new HashMap<>();procs.put(side,list);rollouts.put(side,held);
        if(!side.equals("linux")&&!WINDOWS)return;
        if(side.equals("linux")&&(WINDOWS||!Files.isDirectory(Path.of("/proc"))))return;
        // Over stdin: wsl.exe re-parses its arguments and would mangle the script.
        List<String> command=side.equals("linux")?List.of("sh","-s"):List.of("wsl.exe","-d",side,"-e","sh","-s");
        try{
            Process p=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try(OutputStream script=p.getOutputStream()){script.write((PROC_SCRIPT+"\n").getBytes(StandardCharsets.UTF_8));}
            try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
                for(String line;(line=in.readLine())!=null;){
                    String[] f=line.split("\\|",-1);
                    if(f[0].equals("P")&&f.length>=6)list.add(new Proc(Long.parseLong(f[1]),f[2],f[3],f[5],f[4]));
                    if(f[0].equals("R")&&f.length>=3)held.put(f[2],Long.parseLong(f[1]));
                }
            }
            if(!p.waitFor(8,java.util.concurrent.TimeUnit.SECONDS))p.destroyForcibly();
        }catch(IOException|RuntimeException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }

    // ---- Transcript ----
    static List<Event> events(Session s)throws IOException{
        ArrayDeque<Event> out=new ArrayDeque<>();
        try(BufferedReader in=Files.newBufferedReader(Path.of(s.path),StandardCharsets.UTF_8)){
            for(String line;(line=in.readLine())!=null;){
                if(line.length()>MAX_LINE)continue;
                Map<?,?> o;try{o=Json.map(Json.parse(line));}catch(RuntimeException e){continue;}
                double ts=time(o.get("timestamp"));String role="";Object content=null;List<Event> found=new ArrayList<>();
                if(s.tool.equals("Codex")){Map<?,?> p=Json.map(o.get("payload"));if(!Json.text(o.get("type")).equals("response_item"))continue;
                    String sub=Json.text(p.get("type"));
                    if(sub.equals("custom_tool_call")||sub.equals("function_call")){Object arg=p.get("input")!=null?p.get("input"):p.get("arguments");found.add(new Event(ts,"tool",Json.text(p.get("name"))+": "+clip(String.valueOf(arg),180)));}
                    else if(sub.equals("message")){role=Json.text(p.get("role"));content=p.get("content");}
                }else{
                    if(Boolean.TRUE.equals(o.get("isSidechain"))||Boolean.TRUE.equals(o.get("isMeta")))continue;
                    Map<?,?> m=Json.map(o.get("message"));role=Json.text(m.get("role"));content=m.get("content");
                    if(content instanceof List<?> blocks)for(Object b:blocks){Map<?,?> block=Json.map(b);String t=Json.text(block.get("type"));
                        if(t.equals("tool_use")||t.equals("toolCall")){Map<?,?> in2=Json.map(block.get(t.equals("toolCall")?"arguments":"input"));String what="";for(String k:List.of("description","command","file_path","pattern","url","prompt","query"))if(what.isEmpty())what=Json.text(in2.get(k));found.add(new Event(ts,"tool",Json.text(block.get("name"))+(what.isEmpty()?"":": "+clip(what,180))));}
                    }
                }
                String body=text(content);
                if(role.equals("user")&&prompt(body))out.add(new Event(ts,"prompt",full(body)));
                else if(role.equals("assistant")&&!body.isBlank())out.add(new Event(ts,"reply",full(body)));
                out.addAll(found);while(out.size()>300)out.poll();
            }
        }
        return new ArrayList<>(out);
    }
    private static String full(String t){return t.length()<=50000?t:t.substring(0,50000)+"\n\n[Truncated: the rest is in the session log]";}

    // ---- Links ----
    static String resumeCommand(Session s){return switch(s.tool){case "Claude"->"claude --resume "+s.id;case "Codex"->"codex resume "+s.id;default->"pi --session "+s.id;};}
    static List<String> links(Session s){
        List<String> out=new ArrayList<>();String app=s.surface();
        if(app.equals("ChatGPT app"))out.add("Open in ChatGPT");
        if(app.equals("Claude Desktop"))out.add("Open Claude app");
        if(s.live&&s.bridge!=null&&!s.bridge.isEmpty())out.add("Open in claude.ai");
        if(s.live&&s.tty!=null&&s.wsl())out.add("Find terminal");
        if(!s.live&&s.wsl()&&!app.equals("Claude Desktop")&&!app.equals("ChatGPT app"))out.add("Resume");
        if(!s.chat()&&local(s)!=null)out.add("VS Code");
        return out;
    }
    /** Every argument comes from the local session index and goes through an argument list, never a shell string. */
    static String open(Session s,String link)throws Exception{
        if(!s.id.matches("[\\w-]+"))throw new IOException("Unexpected session id");
        switch(link){
            case "Open in ChatGPT"->{Desktop.getDesktop().browse(new URI("codex://threads/"+s.id));return "Opening this thread in ChatGPT";}
            case "Open Claude app"->{
                if(WINDOWS)new ProcessBuilder("explorer.exe","shell:AppsFolder\\Claude_pzs8sxrjxfjjc!Claude").start();else Desktop.getDesktop().browse(new URI("claude://"));
                return "Opening Claude. Look for: "+clip(s.name(),60);
            }
            case "Open in claude.ai"->{if(!s.bridge.matches("[\\w-]+"))throw new IOException("Unexpected session id");Desktop.getDesktop().browse(new URI("https://claude.ai/code/"+s.bridge));return "Opening in your browser";}
            case "Resume"->{
                if(s.cwd.contains(";"))throw new IOException("This folder's name can't be passed to Windows Terminal");
                new ProcessBuilder("wt.exe","-w","0","new-tab","wsl.exe","-d",distro(s),"--cd",s.cwd,"--","bash","-lic",resumeCommand(s)).start();return "Opened a new terminal tab";
            }
            case "Find terminal"->{
                if(s.tty==null||!s.tty.matches("/dev/pts/\\d+"))throw new IOException("This session has no terminal");
                Process bell=new ProcessBuilder("wsl.exe","-d",distro(s),"-e","sh","-s").redirectError(ProcessBuilder.Redirect.DISCARD).start();
                try(OutputStream script=bell.getOutputStream()){script.write(("printf '\\a' > "+s.tty+"\n").getBytes(StandardCharsets.UTF_8));}
                new ProcessBuilder("powershell.exe","-NoProfile","-Command","$p=Get-Process WindowsTerminal -ErrorAction SilentlyContinue|Select-Object -First 1;if($p){(New-Object -ComObject WScript.Shell).AppActivate($p.Id)|Out-Null}").start();
                return "Raised Windows Terminal and rang the bell on that tab";
            }
            case "VS Code"->{
                if(s.cwd.matches(".*[&|<>^%\"].*"))throw new IOException("This folder's name can't be passed to VS Code");
                if(WINDOWS&&s.wsl()&&s.cwd.startsWith("/")&&!s.cwd.startsWith("/mnt/"))new ProcessBuilder("cmd.exe","/c","code","--remote","wsl+"+distro(s),s.cwd).start();
                else if(WINDOWS)new ProcessBuilder("cmd.exe","/c","code",local(s).toString()).start();
                else new ProcessBuilder("code",local(s).toString()).start();
                return "Opening VS Code";
            }
            default->throw new IOException("Unknown link");
        }
    }
}
