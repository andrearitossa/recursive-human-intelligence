import com.sun.net.httpserver.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Drives the real window end to end: real clicks on real components, with a fake local provider. */
public class Drive {
  static File shots;static Loop l;static int fails=0,passes=0;
  static Path home=Path.of(System.getProperty("user.home"));
  static void ok(boolean c,String what){if(c){passes++;System.out.println("  ok   "+what);}else{fails++;System.out.println("  FAIL "+what);}}
  static void ui(Runnable r)throws Exception{SwingUtilities.invokeAndWait(r);}
  static void later(Runnable r){SwingUtilities.invokeLater(r);}
  static void pause(long ms)throws Exception{Thread.sleep(ms);}
  static void idle()throws Exception{long t=System.currentTimeMillis();pause(150);while(l.busy&&System.currentTimeMillis()-t<120000)pause(100);pause(250);}
  /** Paints only Loop's own components, never the screen. At 1.25 scale, matching a common Windows setting. */
  static void shot(String name,Window w)throws Exception{ui(()->{try{Container p=((RootPaneContainer)w).getContentPane();double k=1.25;var i=new java.awt.image.BufferedImage((int)(p.getWidth()*k),(int)(p.getHeight()*k),java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D g=i.createGraphics();g.scale(k,k);p.printAll(g);g.dispose();ImageIO.write(i,"png",new File(shots,name+".png"));}catch(Exception e){throw new RuntimeException(e);}});}
  static void shot(String name)throws Exception{shot(name,l.frame);}
  static AbstractButton find(Container c,String text){for(Component k:c.getComponents()){if(k instanceof AbstractButton b&&text.equals(b.getText())&&b.isShowing())return b;if(k instanceof Container n){AbstractButton r=find(n,text);if(r!=null)return r;}}return null;}
  static JDialog dialog(String title)throws Exception{long t=System.currentTimeMillis();while(System.currentTimeMillis()-t<8000){Window[] all=Window.getWindows();for(int i=all.length-1;i>=0;i--)if(all[i] instanceof JDialog d&&d.isShowing()&&(title==null||title.equals(d.getTitle())))return d;pause(60);}throw new IllegalStateException("No dialog "+title);}
  static boolean anyDialog(){for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing())return true;return false;}
  static void press(Container c,String text)throws Exception{AbstractButton b=find(c,text);if(b==null)throw new IllegalStateException("No button "+text);later(b::doClick);pause(200);}
  static String allText(Container c){StringBuilder s=new StringBuilder();for(Component k:c.getComponents()){if(k instanceof JLabel a)s.append(a.getText()).append('\n');if(k instanceof javax.swing.text.JTextComponent t)s.append(t.getText()).append('\n');if(k instanceof Container n)s.append(allText(n));}return s.toString();}
  static String dismiss(String button)throws Exception{JDialog d=dialog(null);String t=allText(d);press(d,button);pause(150);return t;}
  static void closeAll()throws Exception{ui(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing())d.dispose();});}
  static String status(){return l.status.getText();}
  static void write(String rel,String... lines)throws Exception{Path f=home.resolve(rel);Files.createDirectories(f.getParent());Files.writeString(f,String.join("\n",lines)+"\n");}
  static String u(String sid,String text){return Json.write(Map.of("type","user","sessionId",sid,"message",Map.of("role","user","content",text)));}

  // ---- fake provider ----
  static volatile String mode="ok";static final List<String> seen=new CopyOnWriteArrayList<>();static final List<Map<String,String>> headers=new CopyOnWriteArrayList<>();
  static String opportunity(String body){
    Map<?,?> req=Json.map(Json.parse(body));List<?> messages=(List<?>)req.get("messages");String input=Json.text(Json.map(messages.get(messages.size()-1)).get("content"));
    if(input.startsWith("Connection"))return "OK";
    Map<?,?> in=Json.map(Json.parse(input));
    if(in.containsKey("messages")){List<Object> out=new ArrayList<>();for(Object o:(List<?>)in.get("messages")){Map<?,?> x=Json.map(o);String t=Json.text(x.get("text"));boolean c=t.contains("CSV"),st=t.contains("standup");out.add(Map.of("ref",x.get("ref"),"task",c?"Clean report CSV":st?"Write standup notes":"Other request","mode","build","area",c?"Data & ML":st?"Writing & career":"Personal & other","friction",false));}return Json.write(Map.of("items",out));}
    if(in.containsKey("tasks")&&!in.containsKey("previouslyTold")){List<String> c=new ArrayList<>(),st=new ArrayList<>();for(Object t:(List<?>)in.get("tasks")){String x=Json.text(t);if(x.contains("CSV"))c.add(x.split(":")[0]);if(x.contains("standup"))st.add(x.split(":")[0]);}return Json.write(Map.of("patterns",List.of(Map.of("name","Cleans report CSVs","description","Keeps coming back.","friction",false,"refs",c),Map.of("name","Drafts standup notes","description","Keeps coming back.","friction",false,"refs",st))));}
    if(in.containsKey("previouslyTold")){List<String> refs=new ArrayList<>();for(Object t:(List<?>)in.get("tasks"))refs.add(Json.text(t).split(":")[0]);String told=String.valueOf(in.get("previouslyTold"));
      if(told.contains("Second item"))return "{\"items\":[]}";boolean again=told.contains("Report cleanup");
      return Json.write(Map.of("items",List.of(Map.of("name",again?"Second item":"Report cleanup","description","A short line for you.","message","You re-explain the same report cleanup in every chat.","you","clean the empty rows","better","Drop empty rows and trim cells in this file, then show me the row count before and after.","actions",List.of(Map.of("text","Read chapter one this week.","source","Python for Data Analysis")),"evidence",refs.subList(0,2)),Map.of("name",again?"Third item":"Standup habit","description","Another short line.","message","More.","actions",List.of(Map.of("text","Try it.","source","")),"evidence",refs.subList(0,2)))));}
    if(in.containsKey("feedback"))return "```json\n"+Json.write(Map.of("content","Better: also removes duplicates.","steps",List.of("Paste","Preview"),"quiz",List.of(),"operations",List.of(Map.of("type","trim"),Map.of("type","deduplicate"))))+"\n```";
    if(mode.equals("none"))return "{\"opportunity\":null}";
    if(mode.equals("garbage"))return "I think you should relax.";
    List<String> ids=new ArrayList<>();Set<String> sess=new HashSet<>();for(Object o:(List<?>)in.get("requests")){Map<?,?> r=Json.map(o);if(Json.text(r.get("text")).contains("standup")&&sess.add(Json.text(r.get("session"))))ids.add(Json.text(r.get("id")));}
    if(mode.equals("invented"))ids=List.of("deadbeef","cafe","f00d");
    if(mode.equals("script"))return Json.write(Map.of("opportunity",Map.of("topic","x","title","x","description","d","kind","transform","evidence",ids,"content","c","operations",List.of(Map.of("type","shell","from","rm -rf")))));
    return Json.write(Map.of("opportunity",Map.of("topic","standup-notes","title","Standup note tidier","description","Tidies your notes before standup.","kind","transform","evidence",ids,"content","<img src=x onerror=alert(1)> Trim lines and drop blanks.","steps",List.of("Paste notes","Preview"),"quiz",List.of(),"operations",List.of(Map.of("type","trim"),Map.of("type","remove_empty")))));
  }
  static int server()throws Exception{
    HttpServer s=HttpServer.create(new InetSocketAddress("localhost",0),0);s.setExecutor(Executors.newCachedThreadPool());
    s.createContext("/",x->{
      String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);seen.add(body);
      Map<String,String> h=new HashMap<>();x.getRequestHeaders().forEach((k,v)->h.put(k.toLowerCase(),v.get(0)));h.put("path",x.getRequestURI().getPath());headers.add(h);
      try{if(mode.equals("slow"))Thread.sleep(20000);}catch(InterruptedException e){}
      int code=mode.equals("401")?401:mode.equals("429")?429:200;String reply;
      try{String text=code==200?opportunity(body):"no";reply=x.getRequestURI().getPath().contains("messages")?Json.write(Map.of("content",List.of(Map.of("type","text","text",text)))):Json.write(Map.of("choices",List.of(Map.of("message",Map.of("content",text)))));}catch(Exception e){code=500;reply=e.toString();}
      if(code==200&&body.contains("\"stream\":true")){
        // Real server-sent chunks, so the tested path is the one a provider uses.
        boolean claude=x.getRequestURI().getPath().contains("messages");String text=claude?Json.text(Json.map(((List<?>)Json.map(Json.parse(reply)).get("content")).get(0)).get("text")):Json.text(Json.map(Json.map(((List<?>)Json.map(Json.parse(reply)).get("choices")).get(0)).get("message")).get("content"));
        x.getResponseHeaders().set("Content-Type","text/event-stream");x.sendResponseHeaders(200,0);var out=x.getResponseBody();
        try{for(int at=0;at<text.length();at+=40){String piece=text.substring(at,Math.min(text.length(),at+40));String line=claude?Json.write(Map.of("type","content_block_delta","delta",Map.of("text",piece))):Json.write(Map.of("choices",List.of(Map.of("delta",Map.of("content",piece)))));out.write(("data: "+line+"\n\n").getBytes(StandardCharsets.UTF_8));out.flush();if(mode.equals("drip"))Thread.sleep(250);}out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));}catch(Exception gone){}
        x.close();return;
      }
      byte[] b=reply.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(code,b.length);x.getResponseBody().write(b);x.close();
    });s.start();return s.getAddress().getPort();
  }

  public static void main(String[] a)throws Exception{
    Thread guard=new Thread(()->{try{Thread.sleep(170000);}catch(Exception e){}System.out.println("WATCHDOG");Runtime.getRuntime().halt(9);});guard.setDaemon(true);guard.start();
    try{run(a);}catch(Throwable t){t.printStackTrace(System.out);Runtime.getRuntime().halt(8);}
  }
  static void run(String[] a)throws Exception{
    shots=new File(a[0],"shots");shots.mkdirs();
    // Throwaway home only.
    if(!home.toString().contains("loop-test-home"))throw new IllegalStateException("Refusing to run outside the test home");
    if(Files.exists(home))try(var w=Files.walk(home)){for(Path p:w.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
    for(int i=0;i<3;i++)write(".claude/projects/-work-reports/s"+i+".jsonl",
      "{\"type\":\"user\",\"isMeta\":true,\"sessionId\":\"s"+i+"\",\"message\":{\"role\":\"user\",\"content\":\"Clean this CSV meta\"}}",
      u("s"+i,"<system-reminder>clean csv docker always</system-reminder>"),
      u("s"+i,"<command-name>/clear</command-name><command-args></command-args>"),
      u("s"+i,"<system-reminder>ctx</system-reminder>\nPlease clean the empty rows in my CSV report number "+i),
      u("s"+i,"Write my standup notes from these bullets, day "+i),
      "not json at all");
    write(".codex/sessions/2026/a.jsonl","{\"type\":\"session_meta\",\"payload\":{\"parent_thread_id\":\"t1\",\"cwd\":\"/x\"}}","{\"type\":\"response_item\",\"payload\":{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"subagent prompt about csv cleanup here\"}]}}");
    write(".pi/agent/sessions/p/secret.jsonl","{\"type\":\"session\",\"cwd\":\"/home/me/client-secret\"}",Json.write(Map.of("type","message","message",Map.of("role","user","content","confidential client request from pi"))));
    write(".claude/projects/-home-me-client-secret/c.jsonl",u("c1","confidential client request from claude"));
    Files.writeString(home.resolve("settings-seed"),"");Files.createDirectories(home.resolve(".loop"));Files.writeString(home.resolve(".loop/settings.properties"),"cross=false\nnotifications=false\n");

    Loop.main(new String[0]);while(Loop.instance==null)pause(100);l=Loop.instance;ui(()->{l.frame.setFocusableWindowState(false);l.frame.setLocation(-4000,-4000);});pause(500);
    System.out.println("Home, empty");
    ok(allText(l.workspace).contains("Keep working. RHI will look for ways to make it easier."),"spec empty-state text");ok(find(l.workspace,"Get feedback")!=null,"empty Home offers the one action");ok(l.frame.getTitle().startsWith("RHI"),"app is named RHI");shot("01-empty");
    ui(()->l.workspace.analyze(true));pause(200);ok(status().contains("Import your chats first"),"Find before import explains next step: "+status());

    System.out.println("Exclusions");
    ui(()->{l.workspace.excluded.setText("relative/path");l.select("Chats");});later(l.scan::doClick);pause(300);
    String m=dismiss("OK");ok(m.contains("full path"),"relative exclusion rejected");
    ui(()->l.workspace.excluded.setText("/home/me/client-secret"));

    System.out.println("Import");
    later(l.scan::doClick);idle();
    var texts=l.engine.requests.values().stream().map(ChatEngine.Request::text).toList();
    ok(texts.size()==6,"only typed requests kept, got "+texts.size());
    ok(texts.stream().noneMatch(t->t.contains("system-reminder")||t.contains("command-name")||t.contains("meta")||t.contains("subagent")),"harness/meta/subagent text filtered");
    ok(texts.stream().noneMatch(t->t.contains("confidential")),"excluded project skipped for Claude path and Pi cwd");
    shot("02-imports");
    later(l.scan::doClick);idle();ok(status().contains("No new requests"),"re-import adds nothing: "+status());
    System.out.println("Remove chats");
    ok(l.rows.getRowCount()==3&&l.stats.getText().equals("3 chats"),"one row per chat: "+l.stats.getText());
    ok(!find(l.frame,"Remove").isEnabled(),"Remove disabled without selection");
    ok(String.valueOf(l.rows.getValueAt(0,0)).contains("clean")||String.valueOf(l.rows.getValueAt(0,0)).contains("standup"),"row named by first request");
    ui(()->l.table.setRowSelectionInterval(0,0));press(l.frame,"Remove");pause(150);ok(l.rows.getRowCount()==2&&l.engine.requests.size()==4,"Remove takes the chat out");
    ok(Files.exists(home.resolve(".claude/projects/-work-reports/s0.jsonl")),"original file kept");
    shot("02b-removed");
    ui(()->l.engine.stamps.clear());later(l.scan::doClick);idle();ok(l.rows.getRowCount()==2,"removed chat does not return on import");
    press(l.frame,"Undo");pause(150);ok(l.rows.getRowCount()==3&&l.engine.requests.size()==6,"Undo restores it");
    ui(()->l.chatSearch.setText("NUMBER 1"));pause(100);ok(l.table.getRowCount()==1,"chat search filters");ui(()->l.chatSearch.setText(""));

    System.out.println("Curated solution");
    ui(()->{l.select("Home");l.workspace.analyze(true);});idle();
    ok(l.workspace.store.tools.size()==1&&l.workspace.store.tools.get(0).kind.equals("csv"),"CSV cleaner created locally: "+status());
    ok(allText(l.workspace).contains("NEW"),"new solution is featured");shot("03-home-tool");
    Solutions.Tool csv=l.workspace.store.tools.get(0);

    System.out.println("CSV cleaner");
    press(l.workspace,"Open");JDialog cd=dialog("CSV cleaner");
    Path in=home.resolve("italian report.csv");Files.write(in," nome ; città ; note \r\n Andrea ; Forlì ;\" a;b \"\r\n ; ; \r\nLuca;Roma;ok\r\n".getBytes("windows-1252"));
    JTextArea prev=(JTextArea)((JScrollPane)((JPanel)cd.getContentPane()).getComponent(0)).getViewport().getView();
    Transferable tr=new Transferable(){public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}public boolean isDataFlavorSupported(DataFlavor f){return f.equals(DataFlavor.javaFileListFlavor);}public Object getTransferData(DataFlavor f){return List.of(in.toFile());}};
    ui(()->prev.getTransferHandler().importData(new TransferHandler.TransferSupport(prev,tr)));
    ok(prev.getText().equals("nome;città;note\nAndrea;Forlì;\"a;b\"\nLuca;Roma;ok"),"drag-and-drop: ANSI + semicolon cleaned: "+prev.getText().replace('\n','|'));
    shot("04-csv",cd);
    AbstractButton sv=find(cd,"Save copy");later(sv::doClick);
    JDialog ch=null;for(int wait=0;wait<100&&ch==null;wait++){pause(100);for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&d!=cd&&((JDialog)w).getContentPane().getComponentCount()>0&&((JDialog)w).getContentPane().getComponent(0) instanceof JFileChooser)ch=d;}
    JFileChooser fc=(JFileChooser)ch.getContentPane().getComponent(0);ok(fc.getSelectedFile().getName().equals("italian report-clean.csv"),"save suggests -clean name");
    later(fc::approveSelection);pause(600);
    Path outFile=home.resolve("italian report-clean.csv");ok(Files.exists(outFile)&&Files.readString(outFile).startsWith("nome;città;note\r\n"),"cleaned copy saved");
    ok(Arrays.equals(Files.readAllBytes(in)," nome ; città ; note \r\n Andrea ; Forlì ;\" a;b \"\r\n ; ; \r\nLuca;Roma;ok\r\n".getBytes("windows-1252")),"original untouched");
    Path bad=home.resolve("bad.csv");Files.writeString(bad,"a,\"oops\n");later(()->prev.getTransferHandler().importData(new TransferHandler.TransferSupport(prev,new Transferable(){public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}public boolean isDataFlavorSupported(DataFlavor f){return true;}public Object getTransferData(DataFlavor f){return List.of(bad.toFile());}})));
    pause(400);{JDialog ed=null;for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&d!=cd)ed=d;String em=allText(ed);press(ed,"OK");ok(em.contains("unclosed"),"broken CSV explained: "+em.strip());}
    closeAll();

    System.out.println("Tool screen");
    ui(()->l.workspace.details(csv));JDialog td=dialog(csv.title);String body=allText(td);
    ok(body.contains("across 3 separate sessions"),"why-this-exists counts sessions");ok(!body.contains("s0.jsonl"),"no raw paths in evidence");shot("05-tool",td);
    press(td,"Useful");td=dialog(csv.title);pause(150);ok(!find(td,"Marked useful").isEnabled()&&csv.feedback.size()==1,"Useful stored once, stays on screen");
    press(td,"Improve");String msg=dismiss("OK");ok(msg.contains("Connect one in Settings")&&l.page.equals("Settings"),"Improve without model leads to Settings");
    closeAll();ui(()->l.workspace.details(csv));td=dialog(csv.title);press(td,"Archive");pause(200);ok(csv.archived&&!allText(l.workspace).contains(csv.title),"Archive hides from library");
    ui(()->{l.select("Home");l.workspace.archived.doClick();});pause(100);ok(allText(l.workspace).contains(csv.title),"visible under archived");
    ui(()->l.workspace.archived.doClick());
    ui(()->l.workspace.analyze(true));idle();ok(l.workspace.store.tools.size()==1,"archived topic not re-suggested");
    ui(()->l.workspace.details(csv));td=dialog(csv.title);press(td,"Restore");pause(150);ok(!csv.archived,"restored");
    ui(()->l.workspace.search.setText("zzz"));pause(100);ok(allText(l.workspace).contains("No solutions match"),"search empty message");ui(()->l.workspace.search.setText("REPORT"));pause(100);ok(allText(l.workspace).contains(csv.title),"search is case-insensitive");ui(()->l.workspace.search.setText(""));

    System.out.println("Providers");
    int port=server();
    ui(()->{l.select("Settings");l.workspace.provider.setSelectedItem(Models.Provider.CLAUDE);l.workspace.endpoint.setText("http://localhost:"+port+"/v1/messages");l.workspace.model.setText("m-claude");l.workspace.key.setText("sk-ant-SESSION");});pause(100);shot("06-settings");
    press(l.frame,"Test connection");idle();ok(status().contains("Connection works: Claude"),"Claude test: "+status());
    var h=headers.get(headers.size()-1);ok("sk-ant-SESSION".equals(h.get("x-api-key"))&&h.containsKey("anthropic-version"),"Claude headers");ok(!seen.get(seen.size()-1).contains("standup"),"test sends no chat history");
    ui(()->l.workspace.provider.setSelectedItem(Models.Provider.OPENROUTER));ok(new String(l.workspace.key.getPassword()).isEmpty(),"key not leaked across providers");
    ui(()->{l.workspace.endpoint.setText("http://localhost:"+port+"/or");l.workspace.model.setText("m-or");l.workspace.key.setText("or-key");});press(l.frame,"Test connection");idle();h=headers.get(headers.size()-1);ok("Bearer or-key".equals(h.get("authorization"))&&status().contains("OpenRouter"),"OpenRouter test");
    ui(()->l.workspace.provider.setSelectedItem(Models.Provider.CLAUDE));ok(new String(l.workspace.key.getPassword()).equals("sk-ant-SESSION"),"typed key restored on switching back");
    ui(()->l.workspace.provider.setSelectedItem(Models.Provider.OPENAI));ui(()->{l.workspace.endpoint.setText("http://localhost:"+port+"/oa");l.workspace.model.setText("m-oa");l.workspace.key.setText("oa-key");});
    mode="401";press(l.frame,"Test connection");m=dismiss("OK");ok(m.contains("HTTP 401")&&m.contains("API key"),"401 explained");idle();
    mode="ok";press(l.frame,"Test connection");idle();ok(status().contains("OpenAI"),"OpenAI test");
    ui(()->l.workspace.model.setText(""));press(l.frame,"Test connection");m=dismiss("OK");ok(m.contains("model ID"),"missing model explained");ui(()->l.workspace.model.setText("m-oa"));
    ui(()->l.workspace.endpoint.setText("http://example.com/x"));press(l.frame,"Test connection");m=dismiss("OK");ok(m.contains("HTTPS"),"remote http blocked");ui(()->l.workspace.endpoint.setText("http://localhost:"+port+"/oa"));

    System.out.println("Consent and analysis");
    int before=seen.size();
    ui(()->{l.select("Home");l.workspace.analyze(true);});idle();ok(seen.size()==before,"no consent: nothing sent to provider");
    ui(()->{l.workspace.consent.setSelected(true);});
    // secrets + filtered sample
    ChatEngine.Request sec=new ChatEngine.Request("s9","my key is sk-abcdefghijklmnopqrstuvwx please use it");ui(()->l.engine.requests.put(ChatEngine.id(sec),sec));
    for(String bad2:List.of("garbage","invented","script")){mode=bad2;int n=l.workspace.store.tools.size();later(()->l.workspace.analyze(true));m=dismiss("OK");idle();ok(l.workspace.store.tools.size()==n,bad2+" rejected: "+m.strip());}
    String sent=seen.get(seen.size()-1);ok(!sent.contains("sk-abcdefghijkl")&&sent.contains("[redacted]"),"credential redacted before sending");ok(!sent.contains(".jsonl")&&!sent.contains("\\\"session\\\":\\\"s0"),"session names anonymised");ok(!sent.contains("system-reminder"),"sample has no harness text");
    mode="slow";ui(()->l.workspace.analyze(true));pause(500);ok(l.cancel.isShowing(),"Stop visible while working");long t0=System.currentTimeMillis();ui(()->l.cancel.doClick());idle();ok(System.currentTimeMillis()-t0<4000&&!anyDialog()&&status().contains("Stopped"),"Stop cancels a slow request silently: "+status());
    mode="ok";ui(()->l.workspace.analyze(true));idle();
    Solutions.Tool tx=l.workspace.store.tools.stream().filter(x->x.topic.equals("standup-notes")).findFirst().orElse(null);ok(tx!=null,"model-backed tool created: "+status());
    mode="none";ui(()->l.workspace.analyze(true));idle();ok(status().contains("No")||status().contains("no"),"no-opportunity message: "+status());mode="ok";
    shot("07-home-two");

    System.out.println("Patterns");
    ui(()->l.select("Patterns"));long tp=System.currentTimeMillis();while(l.patterns.result==null&&System.currentTimeMillis()-tp<20000)pause(100);pause(500);
    ok(l.patterns.result.requests()>0,"step 1 analyzed: "+l.patterns.overview.getText());ok(!l.patterns.result.groups().isEmpty(),"groups found: "+l.patterns.result.groups().size());shot("10-patterns");
    ok(l.patterns.analyze.isShowing(),"AI action visible");
    mode="slow";later(l.patterns.analyze::doClick);{JDialog sd=dialog("Analyze with AI");ok(allText(sd).contains("requests to OpenAI"),"states what will be sent");press(sd,"OK");}pause(500);ok(l.patterns.running&&l.cancel.isShowing(),"run shows progress");ui(()->l.cancel.doClick());idle();pause(600);ok(!l.patterns.running&&!l.patterns.llm.hasPatterns(),"Stop ends AI run: "+status());mode="ok";
    later(l.patterns.analyze::doClick);{JDialog sd=dialog("Analyze with AI");press(sd,"Cancel");}pause(150);ok(!l.busy,"Cancel sends nothing");
    int sentBefore=seen.size();mode="drip";later(l.patterns.analyze::doClick);{JDialog sd=dialog("Analyze with AI");press(sd,"OK");}
    {long tw=System.currentTimeMillis();while(l.patterns.llm.partial.isEmpty()&&System.currentTimeMillis()-tw<9000)pause(30);pause(150);ok(l.busy&&!l.patterns.llm.partial.isEmpty()&&l.patterns.result.groups().stream().anyMatch(x->x.label.equals("Cleans report CSVs")),"first pattern on the map while still working");Patterns.Group live=l.patterns.result.groups().get(0);ui(()->l.patterns.toggle(live));ok(l.patterns.selected.size()==1,"can select while working");}
    idle();pause(900);mode="ok";
    ok(seen.size()-sentBefore==2&&l.patterns.llm.hasPatterns(),"AI patterns built in 2 calls: "+(seen.size()-sentBefore));ok(l.patterns.result.groups().stream().anyMatch(x->x.label.equals("Cleans report CSVs")),"map uses AI names");ok(l.patterns.analyze.getText().equals("Regroup"),"action becomes Regroup");shot("13-ai-map");
    {boolean inside=true;var mp=l.patterns.map;for(double[] b:mp.bubbles.values())if(b[0]-b[2]<0||b[1]-b[2]<0||b[0]+b[2]>mp.getWidth()||b[1]+b[2]>mp.getHeight())inside=false;ok(inside&&mp.bubbles.size()==l.patterns.result.groups().size(),"every bubble fits on the map");}
    Patterns.Group pg=l.patterns.result.groups().get(0);ui(()->l.patterns.toggle(pg));ok(l.patterns.scope.getText().contains("1 pattern"),"bubble selects scope");{int barTotal=l.patterns.barCounts().values().stream().mapToInt(Integer::intValue).sum();ok(barTotal==pg.items.size(),"percentages follow the selection: "+barTotal);}shot("11-patterns-selected");
    later(l.patterns.ask::doClick);idle();pause(300);
    ok(l.page.equals("Home"),"Find the problems moves to Home");
    ok(l.patterns.feedback.size()==2&&allText(l.patterns.results).contains("Report cleanup"),"Feedback listed: "+status());ok(!allText(l.patterns.results).contains("re-explain"),"list shows only name and one line");shot("12-feedback");
    Insights.Finding fb=l.patterns.feedback.get(0);ui(()->l.patterns.detail(fb));JDialog fd=dialog("Report cleanup");ok(allText(fd).contains("re-explain")&&find(fd,"Find: Python for Data Analysis")!=null,"click opens detail with source");ok(allText(fd).contains("SHARPER"),"shows the sharper version");shot("15-detail",fd);press(fd,"Why am I seeing this?");pause(100);{JDialog esc=fd;ui(()->esc.getRootPane().getActionForKeyStroke(KeyStroke.getKeyStroke("ESCAPE")).actionPerformed(new java.awt.event.ActionEvent(esc,0,"")));pause(150);ok(!esc.isShowing(),"Esc closes detail");}closeAll();
    ok(seen.get(seen.size()-1).contains("tasks"),"agent receives all tasks");ok(Files.exists(Insights.file(home.resolve(".loop"))),"feedback persisted");
    ok(find(l.workspace,"Get feedback")!=null,"Home has one action");
    mode="drip";later(l.workspace.analyze::doClick);{long tw=System.currentTimeMillis();while(l.patterns.feedback.size()<3&&System.currentTimeMillis()-tw<9000)pause(30);}
    {long tw=System.currentTimeMillis();boolean sawLive=false;while(l.busy&&System.currentTimeMillis()-tw<20000){if(l.patterns.feedback.size()==3&&l.busy&&allText(l.patterns.results).contains("Second item"))sawLive=true;pause(30);}ok(sawLive,"item visible on Home before the run finishes");}mode="ok";pause(400);
    ok(l.logs.getText().contains("Grouping done:")&&l.logs.getText().contains("Pass 1 input:")&&l.logs.getText().contains("nothing new and strong enough")&&l.logs.getText().contains("first text after")&&l.logs.getText().contains("Batch read:"),"logs explain each step");ok(l.logs.getText().contains("→ OpenAI m-oa")&&l.logs.getText().contains("streaming")&&l.logs.getText().contains("← 200"),"logs show API calls");ok(!l.logs.getText().contains("oa-key")&&!l.logs.getText().contains("standup notes from"),"logs hold no key or chat text");
    ui(()->l.logsToggle.doClick());pause(200);ok(l.logs.isShowing(),"logs toggle opens");shot("16-logs");ui(()->l.logsToggle.doClick());pause(100);ok(!l.logs.isShowing(),"logs toggle closes");
    ok(l.patterns.feedback.size()==4&&allText(l.workspace).contains("Second item")&&l.patterns.feedback.get(0).name().equals("Second item")&&l.patterns.feedback.get(1).name().equals("Third item"),"second run adds new items");ok(l.workspace.homeScroll.getVerticalScrollBar().getValue()==0,"new items are first");ok(seen.get(seen.size()-1).contains("Report cleanup"),"earlier items passed so they are not repeated");shot("14-home-feedback");
    press(l.patterns.results,"Mark as done");pause(200);ok(!allText(l.patterns.results).contains("Second item")&&l.patterns.feedback.get(0).removed(),"Mark as done hides item, keeps it on file");
    ok(find(l.patterns.results,"Show done (1)")!=null,"done items tucked behind a toggle");press(l.patterns.results,"Show done (1)");pause(150);ok(allText(l.patterns.results).contains("Second item"),"done items can be shown");shot("17-done");press(l.patterns.results,"Undo");pause(150);ok(!l.patterns.feedback.get(0).removed(),"Undo brings it back");press(l.patterns.results,"Mark as done");pause(150);ok(l.patterns.feedback.get(0).removed(),"done again");
    ok(l.workspace.analyze.getText().equals("Check for new chats"),"button says what it will do once caught up: "+l.workspace.analyze.getText());ok(status().length()>0,"status after full read: "+status());
    {int c=seen.size();later(l.workspace.analyze::doClick);idle();pause(300);ok(seen.size()==c&&status().contains("caught up"),"nothing changed means no model call: "+status());}
    {Insights.Finding one=l.patterns.feedback.get(1);ui(()->l.patterns.detail(one));JDialog rd=dialog(one.name());ok(find(rd,"Remove")!=null,"detail offers Remove");press(rd,"Remove");pause(200);ok(!l.patterns.feedback.contains(one)&&!rd.isShowing(),"Remove in detail deletes it");ok(l.workspace.analyze.getText().equals("Get feedback"),"removing unlocks a new read");}
    {ui(()->l.patterns.showDone=true);ui(l.patterns::renderReports);pause(150);Insights.Finding doneOne=l.patterns.feedback.get(0);ok(find(l.patterns.results,"Remove")!=null,"done row offers Remove");}
    {ok(l.patterns.llm.upToDate(l.engine.requests),"patterns up to date after a run");int callsBefore=seen.size();Map<String,ChatEngine.Request>[] gone=new Map[1];ui(()->{gone[0]=l.engine.removeSessions(Set.of("s0"));l.refreshChats();});ok(l.patterns.llm.changes(l.engine.requests)[1]>0,"removed chat noticed");
     later(l.workspace.analyze::doClick);idle();pause(500);boolean regrouped=false;for(String call:seen.subList(callsBefore,seen.size()))if(call.contains("find what a person keeps doing"))regrouped=true;ok(regrouped&&l.logs.getText().contains("removed since the last grouping"),"Get feedback regroups after a removal");ok(l.patterns.llm.upToDate(l.engine.requests),"up to date again");
     int calls2=seen.size();later(l.workspace.analyze::doClick);idle();pause(500);boolean again=false;for(String call:seen.subList(calls2,seen.size()))if(call.contains("find what a person keeps doing"))again=true;ok(!again,"no needless regroup when nothing changed");
     ui(()->{l.engine.restoreSessions(gone[0]);l.refreshChats();});}
    System.out.println("Changing the list during a run");
    ui(()->{for(Insights.Finding f:new ArrayList<>(l.patterns.feedback))l.patterns.removeFeedback(f);});
    mode="drip";later(l.workspace.analyze::doClick);{long tw=System.currentTimeMillis();while(l.patterns.feedback.isEmpty()&&System.currentTimeMillis()-tw<15000)pause(20);Insights.Finding firstIn=l.patterns.feedback.get(0);ui(()->l.patterns.removeFeedback(firstIn));
     while(l.patterns.feedback.isEmpty()&&System.currentTimeMillis()-tw<15000)pause(20);Insights.Finding nextIn=l.patterns.feedback.get(0);ui(()->l.patterns.setDone(nextIn,true));}
    idle();mode="ok";pause(400);{var names=l.patterns.feedback.stream().map(Insights.Finding::name).toList();ok(names.contains("Second item")&&names.contains("Third item")&&allText(l.patterns.results).contains("Second item"),"later passes still appear after a mid-run removal: "+names);ok(names.indexOf("Second item")<names.indexOf("Third item"),"order kept");ok(Insights.load(home.resolve(".loop")).size()==names.size(),"all saved");}
    ui(()->l.select("Home"));
    System.out.println("Improve, versions");
    ui(()->l.workspace.details(tx));td=dialog(tx.title);press(td,"Improve");pause(300);JDialog q=null;for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&d!=td)q=d;
    JTextArea req=findArea(q);ui(()->req.setText("Also remove duplicates"));press(q,"OK");idle();pause(300);
    ok(tx.versions.size()==2&&seen.get(seen.size()-1).contains("Also remove duplicates"),"revision saved as version 2: "+status());
    td=dialog(tx.title);body=allText(td);ok(body.contains("Better: also removes"),"reopens with revised content");
    press(td,"Versions");pause(300);JDialog vd=null;for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&d.getTitle().equals("Version history"))vd=d;shot("08-versions",vd);
    JComboBox<?> combo=findCombo(vd);ui(()->combo.setSelectedIndex(0));press(vd,"OK");pause(400);ok(tx.versions.size()==3&&Json.text(tx.current().get("content")).contains("onerror")&&Json.text(tx.current().get("change")).contains("Restored"),"restore adds version 3");closeAll();
    Path art=Artifacts.write(l.workspace.store,tx);ok(!Files.readString(art).contains("<img src=x"),"artifact escapes model content");

    System.out.println("Pause");
    ui(()->l.workspace.pause.doClick());ok(l.workspace.pause.getText().equals("Resume"),"pause label");
    ui(()->l.workspace.analyze(true));pause(100);ok(status().contains("Paused"),"Find explains pause: "+status());ui(()->l.scan());pause(100);ok(status().contains("Paused")&&!l.busy,"Import refuses while paused");
    ui(()->l.workspace.pause.doClick());ok(Files.readString(home.resolve(".loop/settings.properties")).contains("paused=false"),"pause persisted");

    System.out.println("Background behavior");
    ui(()->{l.workspace.model.setText("");l.workspace.analyze(false);});pause(250);ok(!anyDialog()&&status().contains("skipped"),"scheduled analysis never pops up a dialog: "+status());ui(()->l.workspace.model.setText("m-oa"));
    int count=l.workspace.store.tools.size();ChatEngine.Request fresh=new ChatEngine.Request("s10","A brand new thing to analyze please");ui(()->l.engine.requests.put(ChatEngine.id(fresh),fresh));int calls=seen.size();ui(()->l.workspace.analyze(false));idle();ok(seen.size()==calls&&l.workspace.store.tools.size()==count,"weekly cooldown blocks automatic suggestion");
    if(l.tray!=null){WindowEventHelper.close(l.frame);pause(300);ok(!l.frame.isVisible(),"close hides to tray");ui(l::reveal);pause(300);ok(l.frame.isVisible(),"tray Open restores");}else System.out.println("  note tray unsupported");
    ok(l.settingsFile.toFile().exists()&&!Files.readString(l.settingsFile).contains("oa-key")&&!Files.readString(l.settingsFile).contains("sk-ant"),"keys never written to settings");

    System.out.println("Key storage");
    if(KeyStore.available()){ok(KeyStore.save("rhi-check","sk-roundtrip-123"),"key saved");ok(KeyStore.load("rhi-check").equals("sk-roundtrip-123"),"key read back");
      Path enc=home.resolve(".loop/keys/rhi-check.dpapi");if(Files.exists(enc))ok(!Files.readString(enc).contains("sk-roundtrip"),"key is encrypted on disk");
      KeyStore.delete("rhi-check");ok(KeyStore.load("rhi-check").isEmpty(),"key deleted");}
    System.out.println("Add folders");
    Path custom=home.resolve("extra");Files.createDirectories(custom);Files.writeString(custom.resolve("x.jsonl"),u("x1","custom folder request about something"));
    ui(()->l.select("Chats"));press(l.frame,"Add folder");pause(900);JDialog ad=dialog(null);JFileChooser afc=(JFileChooser)ad.getContentPane().getComponent(0);ui(()->afc.setSelectedFile(custom.toFile()));later(afc::approveSelection);pause(300);idle();
    ok(l.extra.size()==1,"custom folder added");ok(status().contains("1 new request"),"added folder imported at once: "+status());
    System.out.println("Autosave");
    ui(()->{l.workspace.schedule.doClick();});ok(Files.readString(l.settingsFile).contains("schedule=true"),"checkbox saves itself");ui(()->l.workspace.schedule.doClick());
    System.out.println("Persistence, delete");
    Solutions copy=new Solutions(home.resolve(".loop"));copy.load();ok(copy.tools.size()==2&&copy.tools.stream().anyMatch(x->x.versions.size()==3),"library survives reload");
    ChatEngine e2=new ChatEngine();e2.load(l.data);e2.loadIndex(l.data.resolveSibling("scan-index.tsv"));ok(e2.stamps.size()>=4,"index survives reload");
    ui(()->l.select("Settings"));pause(150);press(l.frame,"Delete history");dismiss("Cancel");ok(Files.exists(l.data),"cancel keeps history");
    press(l.frame,"Delete history");dismiss("OK");pause(200);
    ok(!Files.exists(Insights.file(home.resolve(".loop")))&&!Files.exists(l.data)&&!Files.exists(home.resolve(".loop/solutions.json"))&&l.workspace.store.tools.isEmpty()&&l.engine.requests.isEmpty(),"delete removes history & solutions");
    ok(Files.exists(home.resolve(".claude/projects/-work-reports/s0.jsonl")),"original transcripts untouched");
    try(var ls=Files.list(home.resolve(".loop"))){ok(ls.noneMatch(p->p.toString().endsWith(".html")),"artifacts deleted");}
    System.out.println("\n"+passes+" passed, "+fails+" failed");
    Runtime.getRuntime().addShutdownHook(new Thread(()->System.out.println("quit clean")));
    ui(l::quit);
  }
  static JTextArea findArea(Container c){for(Component k:c.getComponents()){if(k instanceof JTextArea t)return t;if(k instanceof Container n){var r=findArea(n);if(r!=null)return r;}}return null;}
  static JTextField findField(Container c){for(Component k:c.getComponents()){if(k instanceof JTextField t)return t;if(k instanceof Container n){var r=findField(n);if(r!=null)return r;}}return null;}
  static JComboBox<?> findCombo(Container c){for(Component k:c.getComponents()){if(k instanceof JComboBox<?> t)return t;if(k instanceof Container n){var r=findCombo(n);if(r!=null)return r;}}return null;}
  static class WindowEventHelper{static void close(Window w)throws Exception{ui(()->w.dispatchEvent(new java.awt.event.WindowEvent(w,java.awt.event.WindowEvent.WINDOW_CLOSING)));}}
}
