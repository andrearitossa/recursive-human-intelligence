import java.nio.file.*;
import java.util.*;

final class FeatureChecks {
    static void check(boolean condition,String message){Checks.check(condition,message);}
    static void run()throws Exception{
        Object round=Json.parse(Json.write(Map.of("text","quote \" slash \\\n<>&","list",List.of(1,true,"é"))));
        check(Json.text(Json.map(round).get("text")).equals("quote \" slash \\\n<>&"),"JSON round trip");
        for(Models.Provider p:Models.Provider.values()){
            Models.Config config=new Models.Config(p,p.endpoint,"test-model","test-key");config.validate();Map<String,Object> body=Models.body(config,"instructions","input");
            check(body.get("model").equals("test-model"),"Provider-neutral model ID");
            check(p==Models.Provider.CLAUDE?body.containsKey("system"):!body.containsKey("system"),"Provider wire format");
            String response=p==Models.Provider.CLAUDE?"{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}":"{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";
            check(Models.decode(p,response).equals("OK"),"Provider text decoding");
        }
        boolean rejected=false;try{new Models.Config(Models.Provider.OPENAI,"http://example.com","m","k").validate();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Remote HTTP rejected");
        Path temp=Files.createTempDirectory("loop-features-");
        try{
            ChatEngine engine=new ChatEngine();for(int i=0;i<3;i++){ChatEngine.Request request=new ChatEngine.Request("session-"+i,"Please clean this CSV report");engine.requests.put(ChatEngine.id(request),request);}
            Solutions store=new Solutions(temp);Map<String,Object> candidate=new LinkedHashMap<>();candidate.put("topic","weekly-report");candidate.put("title","Your weekly report");candidate.put("description","A reusable cleanup helper.");candidate.put("kind","transform");candidate.put("content","Trim lines and remove blanks.");candidate.put("steps",List.of("Paste text","Preview output"));candidate.put("operations",List.of(Map.of("type","trim"),Map.of("type","remove_empty")));candidate.put("quiz",List.of(Map.of("question","What stays unchanged?","answer","Your source file.")));candidate.put("evidence",new ArrayList<>(engine.requests.keySet()));
            Models.Model fake=(system,input)->Json.write(Map.of("opportunity",candidate));
            Solutions.Tool tool=store.analyze(engine,fake,true);check(tool!=null&&tool.evidence.size()==3,"Model-backed opportunity");
            check(store.analyze(engine,fake,false)==null,"Automatic weekly cooldown");
            String artifact=Artifacts.html(tool);check(artifact.contains("sandbox=\"allow-scripts allow-downloads\""),"Artifact sandbox");check(!artifact.contains("allow-same-origin"),"No same-origin privilege");check(artifact.contains("connect-src"),"Artifact network policy");
            store.feedback(tool,"useful","Saved five minutes");store.revise(tool,(system,input)->Json.write(candidate),"Keep blank lines");check(tool.versions.size()==2,"Revision preserves previous version");store.restore(tool,0);check(tool.versions.size()==3,"Restore creates a new version");
            tool.archived=true;store.save();Solutions loaded=new Solutions(temp);loaded.load();check(loaded.tools.size()==1&&loaded.tools.get(0).archived,"Archive persistence");check(loaded.tools.get(0).versions.size()==3,"Versions persist");check(loaded.tools.get(0).feedback.size()==2,"Explicit feedback persists");
            Map<String,Object> invalid=new LinkedHashMap<>(candidate);invalid.put("evidence",List.of("invented"));rejected=false;try{store.validate(invalid,engine,engine.requests.keySet());}catch(java.io.IOException expected){rejected=true;}check(rejected,"Invented evidence rejected");
            invalid.put("evidence",List.of(engine.requests.keySet().iterator().next()));rejected=false;try{store.validate(invalid,engine,engine.requests.keySet());}catch(java.io.IOException expected){rejected=true;}check(rejected,"Single-session evidence rejected");
            invalid.put("evidence",new ArrayList<>(engine.requests.keySet()));invalid.put("operations",List.of(Map.of("type","shell","from","x")));rejected=false;try{store.validate(invalid,engine,engine.requests.keySet());}catch(java.io.IOException expected){rejected=true;}check(rejected,"Executable operations rejected");
            tool.title="</script><script>alert(1)</script>";tool.current().put("content","<img src=x onerror=alert(1)>");String safe=Artifacts.html(tool);check(!safe.contains("</script><script>alert"),"Model content escaped");
            Path generated=Artifacts.write(store,tool);check(Files.exists(generated),"Standalone artifact written");store.clear();check(!Files.exists(generated),"Deletion removes generated artifact");
            Solutions local=new Solutions(temp);Solutions.Tool curated=local.analyze(engine,null,true);check(curated!=null&&curated.kind.equals("csv"),"Local curated fallback");curated.archived=true;check(local.hasTopic("csv-cleanup"),"Dismissed topic stays excluded");
            Path excluded=Files.createDirectories(temp.resolve("excluded"));Files.writeString(excluded.resolve("chat.jsonl"),"{\"role\":\"user\",\"content\":\"csv\"}");
            check(new ChatEngine().scan(List.of(new ChatEngine.Root("Custom",excluded)),()->false,u->{},List.of(excluded)).chats()==0,"Project exclusion");
            engine.save(temp.resolve("history.tsv"));engine.stamps.put(temp.resolve("source.jsonl"),new ChatEngine.Stamp(1,2,true));engine.saveIndex(temp.resolve("index.tsv"));ChatEngine copy=new ChatEngine();copy.load(temp.resolve("history.tsv"));copy.loadIndex(temp.resolve("index.tsv"));check(copy.stamps.size()==1,"Incremental index persistence");
        }finally{try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        harnessFilter();patternChecks();
        long[] used={-1,-1};
        check(Models.delta(Models.Provider.OPENAI,"data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}",used).equals("hi"),"OpenAI stream text");
        check(Models.delta(Models.Provider.CLAUDE,"data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"hi\"}}",used).equals("hi"),"Claude stream text");
        check(Models.delta(Models.Provider.OPENAI,"data: [DONE]",used).isEmpty()&&Models.delta(Models.Provider.OPENAI,": ping",used).isEmpty(),"Stream control lines ignored");
        Models.delta(Models.Provider.OPENAI,"data: {\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}",used);check(used[0]==7&&used[1]==3,"Stream usage read");
        List<String> got=new ArrayList<>();ItemStream items=new ItemStream(m->got.add(Json.text(m.get("name"))));
        String answer="```json\n{\"items\":[{\"name\":\"a}{\\\"\",\"x\":{\"y\":1}},{\"name\":\"b\"},{\"name\":\"cut";
        for(char c:answer.toCharArray())items.accept(String.valueOf(c));check(got.equals(List.of("a}{\"","b")),"Complete items emitted as they arrive: "+got);
        System.out.println("Provider, evidence, library, artifact, and feedback checks passed.");
    }
    static void harnessFilter()throws Exception{
        check(ChatEngine.userText("<system-reminder>x</system-reminder>").isEmpty(),"Injected block dropped");
        check(ChatEngine.userText("<command-name>/clear</command-name><command-args></command-args>").isEmpty(),"Slash command dropped");
        check(ChatEngine.userText("<environment_context>\n<cwd>/x</cwd>\n</environment_context>\nFix my build").equals("Fix my build"),"Leading context stripped");
        check(ChatEngine.userText("Fix it\n<system-reminder>x</system-reminder>").equals("Fix it"),"Trailing context stripped");
        check(ChatEngine.userText("Use a <div> here, then <b>bold</b> text").equals("Use a <div> here, then <b>bold</b> text"),"Tags in prose stay");
        check(ChatEngine.userText("<pasted_content id=\"1\">my notes</pasted_content>").equals("my notes"),"Pasted text kept");
        check(ChatEngine.extract("{\"type\":\"user\",\"isMeta\":true,\"message\":{\"role\":\"user\",\"content\":\"hello\"}}","s")==null,"Meta turn ignored");
        check(ChatEngine.extract("{\"type\":\"user\",\"origin\":{\"kind\":\"task-notification\"},\"message\":{\"role\":\"user\",\"content\":\"hello\"}}","s")==null,"Non-human origin ignored");
        check(ChatEngine.extract("{\"type\":\"user\",\"origin\":{\"kind\":\"human\"},\"message\":{\"role\":\"user\",\"content\":\"hello\"}}","s")!=null,"Human origin kept");
        Path gone=Files.createTempDirectory("loop-removed-");
        try{
            Files.writeString(gone.resolve("a.jsonl"),"{\"sessionId\":\"keep\",\"role\":\"user\",\"content\":\"keep this one\"}\n{\"sessionId\":\"drop\",\"role\":\"user\",\"content\":\"drop this one\"}\n");
            List<ChatEngine.Root> root=List.of(new ChatEngine.Root("Custom",gone));
            ChatEngine chats=new ChatEngine();chats.scan(root,()->false,u->{});check(chats.requests.size()==2,"Both chats imported");
            Map<String,ChatEngine.Request> taken=chats.removeSessions(Set.of("drop"));check(chats.requests.size()==1&&taken.size()==1,"Chat removed");
            chats.stamps.clear();chats.scan(root,()->false,u->{});check(chats.requests.size()==1,"Removed chat stays removed on re-import");
            chats.save(gone.resolve("h.tsv"));chats.saveRemoved(gone.resolve("r.txt"));ChatEngine again=new ChatEngine();again.load(gone.resolve("h.tsv"));again.loadRemoved(gone.resolve("r.txt"));again.scan(root,()->false,u->{});check(again.requests.size()==1,"Removal survives restart");
            ChatEngine all=new ChatEngine();all.scan(root,()->false,u->{});all.removeSessions(Set.of("keep","drop"));all.scan(root,()->false,u->{});check(all.requests.isEmpty(),"Everything removed stays removed");all.readd(gone);all.scan(root,()->false,u->{});check(all.requests.size()==2&&all.removed.isEmpty(),"Adding the folder again brings chats back");all.removeSessions(Set.of("drop"));all.scan(root,()->false,u->{});check(all.requests.size()==1,"A later removal still holds");
            Files.write(gone.resolve("bad.jsonl"),new byte[]{'{','"','r','o','l','e','"',':','"','u','s','e','r','"',',','"','c','o','n','t','e','n','t','"',':','"','h','i',' ',(byte)0xE8,'"','}','\n'});ChatEngine bytes=new ChatEngine();var scanned=bytes.scan(root,()->false,u->{});check(scanned.skipped()==0&&bytes.requests.values().stream().anyMatch(q->q.text().startsWith("hi")),"Invalid byte does not lose a chat");
            chats.restoreSessions(taken);check(chats.requests.size()==2&&chats.removed.isEmpty(),"Undo restores chat");
        }finally{try(var paths=Files.walk(gone)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        List<Path> blocked=List.of(Path.of("/home/me/client"));
        check(ChatEngine.isExcluded("/home/me/client/sub",null,blocked),"Excluded project path");check(!ChatEngine.isExcluded("/home/me/client2",null,blocked),"Sibling not excluded");
        check(ChatEngine.isExcluded("\\\\wsl.localhost\\Ubuntu\\home\\me\\client",null,blocked),"UNC spelling matches");check(ChatEngine.isExcluded("x","-home-me-client",blocked),"Claude folder name matches");
        check(Csv.clean(" a ; b \n 1 ;\" x;y \"\n ; \n").equals("a;b\r\n1;\"x;y\""),"Semicolon CSV keeps its delimiter");
        check(Csv.delimiter("a\tb\n")=='\t'&&Csv.delimiter("\"a;b\",c\n")==',',"Delimiter detection ignores quoted text");
        check(Solutions.asksCsvCleanup("Clean this CSV again")&&!Solutions.asksCsvCleanup("Export the results to CSV"),"A mention of CSV is not a cleanup request");
        check(!Solutions.asksDockerHelp("x".repeat(700)+" how docker"),"Long pastes are not requests");
        check(Solutions.redact("key sk-abcdefghijklmnopqrstuv and token=abcdef123").equals("key [redacted] and [redacted]"),"Credentials redacted");
        check(Json.map(Solutions.parseModel("Here you go: {\"a\":1} done")).containsKey("a"),"Text around JSON tolerated");
        boolean rejected=false;try{Solutions.parseModel("not json");}catch(java.io.IOException expected){rejected=expected.getMessage().contains("valid JSON");}check(rejected,"Invalid model output explained");
    }
    static void patternChecks()throws Exception{
        check(ChatEngine.userText("The following is the Codex agent history added since your last approval").isEmpty(),"Automated approval prompts dropped");
        check(Patterns.mode("why does this fail?")==Patterns.Mode.UNDERSTAND&&Patterns.mode("fix the login crash in auth")==Patterns.Mode.FIX&&Patterns.mode("no, that is wrong")==Patterns.Mode.CORRECT&&Patterns.mode("continue")==Patterns.Mode.STEER,"Interaction modes");
        check(ChatEngine.userText("The user sent a new message while you were working:\nfix the build\n\nThis is how Claude Code surfaces messages the user sends mid-turn, more text. Address the message above as you continue this turn.").equals("fix the build"),"Mid-turn notice stripped");
        check(ChatEngine.userText("The user sent a new message while you were working: fix the build").equals("fix the build"),"Harness prefix stripped");
        Map<String,ChatEngine.Request> requests=new LinkedHashMap<>();
        String[] docker=new String[12],cv=new String[12],tails={"keeps failing restart","permissions broke again","mount path question","rebuild scratch today"};
        for(int i=0;i<12;i++){docker[i]="docker compose volume "+tails[i%4]+" chapter"+"abcdefghijkl".charAt(i);cv[i]="resume bullet backend leadership consulting summary"+" section"+"abcdefghijkl".charAt(i);}
        for(int i=0;i<12;i++)for(String t:List.of(docker[i],cv[i])){ChatEngine.Request r=new ChatEngine.Request("chat-"+i+t.charAt(0),t);requests.put(ChatEngine.id(r),r);}
        ChatEngine.Request copy=new ChatEngine.Request("fork",docker[0]);requests.put(ChatEngine.id(copy),copy);
        Patterns.Result result=Patterns.analyze(requests);check(result.requests()==24,"Replayed messages counted once");
        check(result.groups().size()>=2,"Separate topics found: "+result.groups().size());
        for(Patterns.Group g:result.groups()){boolean d=g.items.stream().anyMatch(i->i.request().text().contains("docker")),c=g.items.stream().anyMatch(i->i.request().text().contains("resume"));check(!(d&&c),"Topics stay apart");}
        Map<String,Patterns.Item> refs=new HashMap<>();Map<String,String> tasks=new HashMap<>();List<Patterns.Item> every=Patterns.distinct(requests);for(Patterns.Item i:every)tasks.put(i.id(),"task");
        String sent=Insights.input(result.groups(),every,tasks,refs,"",List.of(),Patterns.signals(requests));check(refs.containsKey("t24")&&sent.contains("t24: task"),"Agent reads every request");
        String good="{\"items\":[{\"name\":\"n\",\"description\":\"d\",\"message\":\"m\",\"actions\":[{\"text\":\"do\",\"source\":\"Book\"}],\"evidence\":[\"t1\",\"t2\"]},{\"name\":\"bad\",\"description\":\"d\",\"actions\":[{\"text\":\"do\"}],\"evidence\":[\"t1\",\"r999\"]},{\"name\":\"thin\",\"description\":\"d\",\"actions\":[{\"text\":\"do\"}],\"evidence\":[\"t1\"]}]}";
        var findings=Insights.parse(good,refs);check(findings.size()==1&&findings.get(0).actions().get(0).source().equals("Book"),"Item kept; invented or thin evidence dropped");
        check(Insights.parse(good.replace("\"message\":\"m\"","\"message\":\"m\",\"you\":\"never typed this\",\"better\":\"b\""),refs).get(0).you().isEmpty(),"Invented quote dropped");
        check(Insights.parse(good.replace("\"message\":\"m\"","\"message\":\"m\",\"you\":\"Docker Compose volume\",\"better\":\"b\""),refs).get(0).better().equals("b"),"Real quote kept");
        Map<String,ChatEngine.Request> said=new LinkedHashMap<>();for(int i=0;i<5;i++){ChatEngine.Request x=new ChatEngine.Request("c"+i,"please keep simple and sexy for page "+"abcde".charAt(i));said.put(ChatEngine.id(x),x);}
        check(String.valueOf(Patterns.signals(said).get("phrasesRetypedAcrossChats")).contains("simple and sexy"),"Retyped phrases measured");
        boolean rejected=false;try{Insights.parse("{}",refs);}catch(java.io.IOException expected){rejected=true;}check(rejected,"Missing items reported");
        Path aiDir=Files.createTempDirectory("loop-ai-");
        try{
            int[] calls={0};
            Models.Model ai=(system,in)->{synchronized(calls){calls[0]++;}Map<?,?> m=Json.map(Json.parse(in));
                if(system.equals(LlmPatterns.DESCRIBE)){List<Object> out=new ArrayList<>();for(Object o:Solutions.list(m.get("messages"))){Map<?,?> x=Json.map(o);boolean d=Json.text(x.get("text")).contains("docker");out.add(Map.of("ref",x.get("ref"),"task",d?"Fix docker volume":"Rewrite resume bullet","mode",d?"fix":"build","area",d?"Deploy & infra":"Bogus area","friction",d));}out.add(Map.of("ref","a999","task","invented","mode","build","area","Coding","friction",false));return Json.write(Map.of("items",out));}
                List<String> plain=new ArrayList<>(),sore=new ArrayList<>();for(Object t:Solutions.list(m.get("tasks"))){String[] p=Json.text(t).split(": ",2);if(p[1].startsWith("!"))sore.add(p[0]);else plain.add(p[0]);}plain.add("t999");
                return Json.write(Map.of("patterns",List.of(Map.of("name","Resume rewrites","description","d","friction",false,"refs",plain),Map.of("name","Volume frustration","description","d","friction",true,"refs",sore))));};
            LlmPatterns run=new LlmPatterns(aiDir);List<String> stages=Collections.synchronizedList(new ArrayList<>());
            run.run(ai,requests,"",(stage,d,t,l)->stages.add(stage));
            check(calls[0]==2,"24 requests take one read and one group call: "+calls[0]);check(run.pending(requests).isEmpty()&&stages.contains("Reading")&&stages.contains("Grouping"),"AI reads then groups");
            Patterns.Result ar=run.result(requests);check(ar.groups().size()==2,"Patterns plus friction pattern: "+ar.groups().size());
            check(ar.groups().stream().anyMatch(g->g.friction&&g.dominant()==Patterns.Mode.CORRECT),"Friction pattern marked");
            check(run.descriptions.values().stream().noneMatch(d->d.task().equals("invented")),"Invented refs ignored");
            check(run.descriptions.values().stream().allMatch(d->LlmPatterns.AREAS.contains(d.area())),"Unknown areas normalized");
            check(run.result(requests).groups().stream().mapToInt(g->g.sessions.size()).max().orElse(0)<=12,"Chats counted locally");
            int before=calls[0];LlmPatterns again=new LlmPatterns(aiDir);check(again.pending(requests).isEmpty()&&again.hasPatterns(),"AI results persist");
            again.full=true;again.run(ai,requests,"docker",(stage,d,t,l)->{});check(calls[0]-before==1,"Regrouping is one call: "+(calls[0]-before));
            Map<String,ChatEngine.Request> fewer=new LinkedHashMap<>(requests);fewer.keySet().removeIf(k->requests.get(k).text().contains("docker"));check(again.result(fewer).groups().size()==1,"Removed chats leave patterns");
            again.clear();check(!Files.exists(again.file()),"AI results deletable");
            int[] tries={0};LlmPatterns cut=new LlmPatterns(aiDir);
            cut.run((system,in)->{synchronized(tries){tries[0]++;}String answer=ai.complete(system,in);if(system.equals(LlmPatterns.DESCRIBE)&&in.length()>2000)return answer.substring(0,answer.length()/2);if(system.equals(LlmPatterns.MERGE)&&Json.text(Json.map(Json.parse(in)).get("area")).equals("All areas"))return "cut off";return answer;},requests,"",(stage,d,t,l)->{});
            check(cut.pending(requests).isEmpty()&&cut.hasPatterns(),"Cut-off answers recover by splitting");cut.clear();
            LlmPatterns broken=new LlmPatterns(aiDir);boolean threw=false;try{broken.run((x,y)->"not json",requests,"",(stage,d,t,l)->{});}catch(Exception expected){threw=true;}check(threw,"All batches failing is reported");
        }finally{try(var p=Files.walk(aiDir)){for(Path x:p.sorted(Comparator.reverseOrder()).toList())Files.delete(x);}}
        Path dir=Files.createTempDirectory("loop-insights-");try{Insights.save(dir,List.of(findings.get(0).asRemoved()));var back=Insights.load(dir);check(back.size()==1&&back.get(0).removed()&&back.get(0).actions().size()==1,"Feedback and removal persist");}finally{try(var p=Files.walk(dir)){for(Path x:p.sorted(Comparator.reverseOrder()).toList())Files.delete(x);}}
    }
}
