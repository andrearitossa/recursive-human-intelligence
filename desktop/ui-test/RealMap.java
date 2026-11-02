import javax.swing.*;import java.awt.*;import java.io.*;import javax.imageio.ImageIO;
/** Paints Loop's own Patterns page with a copy of real history. Makes no network calls. */
public class RealMap{public static void main(String[] a)throws Exception{
 Loop.main(new String[0]);while(Loop.instance==null)Thread.sleep(100);Loop l=Loop.instance;
 SwingUtilities.invokeAndWait(()->{l.frame.setFocusableWindowState(false);l.frame.setLocation(-4000,-4000);l.select("Patterns");});
 long t=System.currentTimeMillis();while(l.patterns.result==null&&System.currentTimeMillis()-t<60000)Thread.sleep(200);Thread.sleep(800);
 File dir=new File(a[0],"shots");dir.mkdirs();
 for(String n:new String[]{"real-map","real-selected"}){
  if(n.endsWith("selected"))SwingUtilities.invokeAndWait(()->{for(var g:l.patterns.result.groups())if(g.label.startsWith("script")||g.label.startsWith("frustration"))l.patterns.toggle(g);});
  Thread.sleep(300);SwingUtilities.invokeAndWait(()->{try{Container p=l.frame.getContentPane();double k=1.25;var i=new java.awt.image.BufferedImage((int)(p.getWidth()*k),(int)(p.getHeight()*k),1);Graphics2D g=i.createGraphics();g.scale(k,k);p.printAll(g);g.dispose();ImageIO.write(i,"png",new File(dir,n+".png"));}catch(Exception e){throw new RuntimeException(e);}});}
 System.out.println(l.patterns.overview.getText());
 for(var g:l.patterns.result.groups())if(g.label.startsWith("frustration")){System.out.println("friction chats="+g.sessions.size()+" requests="+g.items.size());for(var x:g.examples.subList(0,Math.min(8,g.examples.size())))System.out.println("  "+x.request().text().strip().replaceAll("\\s+"," ").substring(0,Math.min(110,x.request().text().strip().replaceAll("\\s+"," ").length())));}
 Runtime.getRuntime().halt(0);
}}
