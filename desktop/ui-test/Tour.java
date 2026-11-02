import javax.swing.*;import java.awt.*;import java.io.*;import javax.imageio.ImageIO;
/** Paints every screen from a throwaway copy of data, at normal and minimum sizes. No network. */
public class Tour{static File dir;static Loop l;
 static void shot(String n,Window w)throws Exception{Thread.sleep(700);SwingUtilities.invokeAndWait(()->{try{Container p=((RootPaneContainer)w).getContentPane();var i=new java.awt.image.BufferedImage(p.getWidth(),p.getHeight(),1);Graphics2D g=i.createGraphics();p.printAll(g);g.dispose();ImageIO.write(i,"png",new File(dir,n+".png"));}catch(Exception e){throw new RuntimeException(e);}});}
 public static void main(String[] a)throws Exception{dir=new File(a[0],"tour");dir.mkdirs();Loop.main(new String[0]);while(Loop.instance==null)Thread.sleep(100);l=Loop.instance;
 SwingUtilities.invokeAndWait(()->{l.frame.setFocusableWindowState(false);l.frame.setLocation(-4000,-4000);});
 for(int[] size:new int[][]{{1080,780},{780,560}}){String s=size[0]+"-";SwingUtilities.invokeAndWait(()->l.frame.setSize(size[0],size[1]));
  for(String p:new String[]{"Home","Patterns","Chats","Settings"}){SwingUtilities.invokeAndWait(()->l.select(p));if(p.equals("Patterns")){long t=System.currentTimeMillis();while(l.patterns.result==null&&System.currentTimeMillis()-t<60000)Thread.sleep(200);Thread.sleep(1500);}shot(s+p,l.frame);}}
 SwingUtilities.invokeAndWait(()->l.frame.setSize(1080,780));
 if(!l.workspace.store.tools.isEmpty()){SwingUtilities.invokeLater(()->l.workspace.details(l.workspace.store.tools.get(0)));Thread.sleep(800);for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()){shot("tool",d);d.dispose();}}
 System.out.println("title="+l.frame.getTitle());Runtime.getRuntime().halt(0);}}
