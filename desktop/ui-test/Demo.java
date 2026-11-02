import com.sun.net.httpserver.*;
import javax.imageio.*;import javax.imageio.metadata.*;import javax.imageio.stream.*;import javax.swing.*;
import java.awt.*;import java.awt.image.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.List;
/** Records the README media: a fictional user, a fake local model, the real app. No real chats are involved. */
public class Demo{
 static Loop l;static List<BufferedImage> frames=new ArrayList<>();static List<Integer> delays=new ArrayList<>();static volatile Window source;static volatile boolean rec=true;
 static final String[][] T={
  {"deploy","Deploy & infra","commit push and deploy the invoice app","deploy to prod now","push this and ship it","it broke prod after deploy, roll back"},
  {"docker","Learning","why does my docker volume lose data","explain docker networking again","how do docker volumes work, remind me","docker compose error, what does this mean"},
  {"scope","Agents & tooling","make it cleaner and simpler","clean this up, make it nicer","why did you change the login page, undo that","simplify everything in the app"},
  {"sql","Learning","explain this sql join again","why is this query slow","how do indexes work again","what is a left join, remind me"},
  {"price","Research","is this invoice idea a winner","would people pay for this","is there a market for this tool","should I add a pricing page"},
  {"cv","Writing & career","rewrite my resume headline","which job title sounds better","polish my linkedin summary","is senior engineer too much for my resume"},
  {"tests","Coding","test it yourself end to end","did you actually run this","it still fails on my machine","check the signup flow really works"}};
 static final String[][] P={{"deploy","Bundles coding with deploying","Shipping is part of nearly every coding request."},{"docker","Re-learns Docker basics","The same Docker questions return across projects."},{"scope","Gives open-ended edit requests","Broad cleanups lead to changes that get undone."},{"sql","Re-asks SQL fundamentals","Joins and indexes keep coming back."},{"price","Asks AI to judge demand","Market questions go to the assistant, not to customers."},{"cv","Rewrites the career story","Titles and headlines keep being reopened."},{"tests","Re-checks finished work","Work is often rechecked after being called done."}};
 static int port;static Map<String,Patterns.Item> none;
 static String reply(String body){
  Map<?,?> req=Json.map(Json.parse(body));List<?> ms=(List<?>)req.get("messages");Map<?,?> in=Json.map(Json.parse(Json.text(Json.map(ms.get(ms.size()-1)).get("content"))));List<?> tasks=(List<?>)in.get("tasks");
  Map<String,List<String>> by=new LinkedHashMap<>();for(Object t:tasks){String[] p=Json.text(t).split(": ",2);String key=p[1].replace("! ","").split(" ")[0];by.computeIfAbsent(key,k->new ArrayList<>()).add(p[0]);}
  if(!in.containsKey("previouslyTold")){List<Object> out=new ArrayList<>();for(String[] p:P)out.add(Map.of("name",p[1],"description",p[2],"friction",p[0].equals("tests"),"refs",by.getOrDefault(p[0],List.of())));return Json.write(Map.of("patterns",out));}
  if(!String.valueOf(in.get("previouslyTold")).equals("[]"))return "{\"items\":[]}";
  return Json.write(Map.of("items",List.of(
   item("Say what must not change","Open-ended edits are what keep getting undone.","You often ask for something cleaner, then ask to undo what changed. Naming the boundary up front prevents the rework.","make it cleaner and simpler","Simplify the settings page only. Keep every feature and show me the plan first.","Use that wording on your next cleanup request.","",by.get("scope")),
   item("Own Docker for good","One good course beats re-asking the same questions.","Volumes and networking keep coming back across your projects. A weekend on the fundamentals would end most of these chats.","","","Work through the volumes and networking chapters this weekend.","Docker Deep Dive by Nigel Poulton",by.get("docker")),
   item("Ask customers, not AI","An assistant's opinion is not demand.","You keep asking whether the invoice idea is a winner. Five real conversations will tell you more than any answer here.","","","Ask one small business owner how they handle invoices today.","The Mom Test by Rob Fitzpatrick",by.get("price")),
   item("Separate shipping from coding","A tested change and a release are two decisions.","Deploying is bundled into your coding requests, and some of those end in rollbacks. Stop at a tested result, then decide.","","","On your next change, ask for the tested result first and deploy afterward.","",by.get("deploy")))));
 }
 static Map<String,Object> item(String n,String d,String m,String y,String b,String a,String s,List<String> ev){return Map.of("name",n,"description",d,"message",m,"you",y,"better",b,"actions",List.of(Map.of("text",a,"source",s)),"evidence",ev.subList(0,3));}
 static BufferedImage paint(Window w)throws Exception{BufferedImage[] o=new BufferedImage[1];SwingUtilities.invokeAndWait(()->{Container p=((RootPaneContainer)w).getContentPane();BufferedImage i=new BufferedImage(p.getWidth(),p.getHeight(),1);Graphics2D g=i.createGraphics();p.printAll(g);g.dispose();o[0]=i;});return o[0];}
 static long sq(int x){return (long)x*x;}
 static boolean same(BufferedImage a,BufferedImage b){if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
 static volatile String caption="";static final int W=800,H=544,BAR=46;
 /** Every frame has one size: the app, optional detail window on top, and a caption bar. */
 static void snap(int ms)throws Exception{
  BufferedImage base=paint(l.frame);BufferedImage s=new BufferedImage(W,H+BAR,1);Graphics2D g=s.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  g.setColor(Ui.BG);g.fillRect(0,0,W,H);g.drawImage(base,0,0,W,H,null);
  if(source!=l.frame){BufferedImage d=paint(source);g.setColor(new Color(41,60,38,120));g.fillRect(0,0,W,H);int dw=Math.min(W-90,d.getWidth()),dh=d.getHeight()*dw/d.getWidth();dh=Math.min(dh,H-30);int x=(W-dw)/2,y=(H-dh)/2;g.setColor(Ui.BG);g.fillRoundRect(x-1,y-1,dw+2,dh+2,6,6);g.drawImage(d,x,y,dw,dh,null);}
  g.setColor(Ui.ACCENT);g.fillRect(0,H,W,BAR);g.setColor(Color.WHITE);g.setFont(Ui.sans(Font.BOLD,17));g.drawString(caption,(W-g.getFontMetrics().stringWidth(caption))/2,H+30);g.dispose();
  if(!frames.isEmpty()&&same(frames.get(frames.size()-1),s)){delays.set(delays.size()-1,delays.get(delays.size()-1)+ms);return;}frames.add(s);delays.add(ms);}
 static void hold(int ms)throws Exception{long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){snap(160);Thread.sleep(140);}}
 static void png(String n,Window w,File d)throws Exception{ImageIO.write(paint(w),"png",new File(d,n+".png"));}
 public static void main(String[] a)throws Exception{
  File docs=new File(a[0]);Path home=Path.of(System.getProperty("user.home"));if(!home.toString().contains("loop-demo-home"))throw new IllegalStateException();
  Random r=new Random(5);int chat=0;for(String[] t:T)for(int c=0;c<14;c++){Path f=home.resolve(".claude/projects/-work/"+(chat++)+".jsonl");Files.createDirectories(f.getParent());StringBuilder b=new StringBuilder();String v=t[2+c%4]+(c>3?" for the "+"abcdefghijklmn".charAt(c)+" page":"");b.append(Json.write(Map.of("type","user","sessionId","s"+chat,"message",Map.of("role","user","content",v)))).append("\n");Files.writeString(f,b.toString());}
  Path dir=home.resolve(".loop");Files.createDirectories(dir);
  ChatEngine e=new ChatEngine();e.scan(ChatEngine.defaults(home,Map.of()),()->false,u->{});LlmPatterns seed=new LlmPatterns(dir);
  for(var i:Patterns.distinct(e.requests)){String tx=i.request().text();String[] pick=null;for(String[] t:T)for(int k=2;k<t.length;k++)if(tx.startsWith(t[k]))pick=t;seed.descriptions.put(i.id(),new LlmPatterns.Description(pick[0]+" "+tx.split(" ")[0],Patterns.mode(tx),pick[1],pick[0].equals("tests")));}seed.save();
  HttpServer s=HttpServer.create(new InetSocketAddress("localhost",0),0);s.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());s.createContext("/",x->{String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);String text=reply(body);if(body.contains("previouslyTold"))try{Thread.sleep(3200);}catch(Exception ig){}x.getResponseHeaders().set("Content-Type","text/event-stream");x.sendResponseHeaders(200,0);var out=x.getResponseBody();try{for(int at=0;at<text.length();at+=40){String p=text.substring(at,Math.min(text.length(),at+40));out.write(("data: "+Json.write(Map.of("choices",List.of(Map.of("delta",Map.of("content",p)))))+"\n\n").getBytes(StandardCharsets.UTF_8));out.flush();Thread.sleep(Math.max(40,3500/(text.length()/40+1)));}out.write("data: [DONE]\n\n".getBytes());}catch(Exception ig){}x.close();});s.start();port=s.getAddress().getPort();
  Files.writeString(dir.resolve("settings.properties"),"cross=false\nnotifications=false\nconsent=true\nrememberKey=false\nprovider=OPENAI\nmodel.OPENAI=demo\nendpoint.OPENAI=http\\://localhost\\:"+port+"/v1\n");
  Loop.main(new String[0]);while(Loop.instance==null)Thread.sleep(50);l=Loop.instance;
  SwingUtilities.invokeAndWait(()->{l.frame.setFocusableWindowState(false);l.frame.setLocation(-4000,-4000);l.frame.setSize(1000,680);l.workspace.key.setText("demo");});Thread.sleep(600);source=l.frame;
  caption="One click. No setup screens.";hold(1300);caption="Your chats become patterns, live";SwingUtilities.invokeLater(l.workspace.analyze::doClick);
  Thread.sleep(250);SwingUtilities.invokeAndWait(()->l.select("Patterns"));
  long t0=System.currentTimeMillis();while(l.patterns.llm.saved.isEmpty()&&System.currentTimeMillis()-t0<30000){snap(160);Thread.sleep(140);}
  SwingUtilities.invokeAndWait(()->l.select("Patterns"));Thread.sleep(700);hold(1500);png("patterns",l.frame,docs);caption="Then honest feedback, item by item";SwingUtilities.invokeAndWait(()->l.select("Home"));
  t0=System.currentTimeMillis();while(l.busy&&System.currentTimeMillis()-t0<30000){snap(160);Thread.sleep(140);}
  hold(1400);png("feedback",l.frame,docs);
  Insights.Finding f=l.patterns.feedback.get(0);SwingUtilities.invokeLater(()->l.patterns.detail(f));Thread.sleep(500);JDialog d=null;for(Window w:Window.getWindows())if(w instanceof JDialog x&&x.isShowing())d=x;JDialog dd=d;SwingUtilities.invokeAndWait(()->{dd.setFocusableWindowState(false);dd.setLocation(-4000,0);});Thread.sleep(250);
  png("detail",d,docs);caption="What you wrote, and a sharper version";source=d;hold(3600);
  // One exact palette from the real colors, with no dithering, so flat areas stay flat.
  Map<Integer,Integer> counts=new HashMap<>();for(BufferedImage im:frames){int[] px=im.getRGB(0,0,im.getWidth(),im.getHeight(),null,0,im.getWidth());for(int v:px)counts.merge(v&0xffffff,1,Integer::sum);}
  List<Integer> top=new ArrayList<>(counts.keySet());top.sort((x,y)->counts.get(y)-counts.get(x));if(top.size()>256)top=top.subList(0,256);
  byte[] rr=new byte[256],gg=new byte[256],bb=new byte[256];for(int i=0;i<top.size();i++){int c=top.get(i);rr[i]=(byte)(c>>16);gg[i]=(byte)(c>>8);bb[i]=(byte)c;}
  IndexColorModel cm=new IndexColorModel(8,256,rr,gg,bb);Map<Integer,Byte> near=new HashMap<>();List<Integer> pal=top;
  try(ImageOutputStream o=ImageIO.createImageOutputStream(new File(docs,"demo.gif"))){ImageWriter w=ImageIO.getImageWritersByFormatName("gif").next();w.setOutput(o);w.prepareWriteSequence(null);
   for(int i=0;i<frames.size();i++){BufferedImage im=frames.get(i);BufferedImage canvas=new BufferedImage(im.getWidth(),im.getHeight(),BufferedImage.TYPE_BYTE_INDEXED,cm);byte[] data=((DataBufferByte)canvas.getRaster().getDataBuffer()).getData();int[] px=im.getRGB(0,0,im.getWidth(),im.getHeight(),null,0,im.getWidth());
    for(int k=0;k<px.length;k++){int v=px[k]&0xffffff;Byte b=near.get(v);if(b==null){int best=0;long bd=Long.MAX_VALUE;for(int q=0;q<pal.size();q++){int c=pal.get(q);long d3=sq((c>>16&255)-(v>>16&255))+sq((c>>8&255)-(v>>8&255))+sq((c&255)-(v&255));if(d3<bd){bd=d3;best=q;}}b=(byte)best;near.put(v,b);}data[k]=b;}
    ImageWriteParam p=w.getDefaultWriteParam();IIOMetadata m=w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(canvas),p);String fmt=m.getNativeMetadataFormatName();IIOMetadataNode root=(IIOMetadataNode)m.getAsTree(fmt);
    IIOMetadataNode gce=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);gce.setAttribute("delayTime",String.valueOf(Math.max(2,delays.get(i)/10)));gce.setAttribute("disposalMethod","none");
    IIOMetadataNode apps=new IIOMetadataNode("ApplicationExtensions"),app=new IIOMetadataNode("ApplicationExtension");app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);root.appendChild(apps);m.setFromTree(fmt,root);
    w.writeToSequence(new IIOImage(canvas,null,m),p);
    if(i==0||i==frames.size()/3||i==frames.size()*2/3||i==frames.size()-1)ImageIO.write(canvas,"png",new File(a[1],"frame"+i+".png"));}w.endWriteSequence();}
  System.out.println("frames="+frames.size()+" items="+l.patterns.feedback.size()+" patterns="+l.patterns.llm.saved.size());Runtime.getRuntime().halt(0);
 }
}
