import javax.swing.*;import java.awt.*;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.List;import javax.imageio.ImageIO;
/** Paints the dashboard alone from the given home folders, read-only, without touching ~/.loop. */
public class DashShot{public static void main(String[] a)throws Exception{
 System.setProperty("awt.useSystemAAFontSettings","lcd");System.setProperty("swing.aatext","true");
 List<ChatEngine.Root> roots=new ArrayList<>();for(int i=1;i<a.length;i++)ChatEngine.addHome(roots,Path.of(a[i]));
 AgentsPage[] p=new AgentsPage[1];JFrame[] f=new JFrame[1];
 SwingUtilities.invokeAndWait(()->{Ui.install();p[0]=new AgentsPage(()->roots,()->{});f[0]=new JFrame();JPanel r=new JPanel(new BorderLayout());r.setBackground(Ui.BG);r.setBorder(new javax.swing.border.EmptyBorder(18,28,14,28));r.add(p[0]);f[0].setContentPane(r);f[0].setFocusableWindowState(false);f[0].setSize(1280,900);f[0].setLocation(-4000,-4000);f[0].setVisible(true);});
 long t=System.currentTimeMillis();while(p[0].agents==null||System.currentTimeMillis()-t<3000)Thread.sleep(200);
 t=System.currentTimeMillis();Thread.sleep(Long.getLong("wait",12000));System.out.println("waited "+(System.currentTimeMillis()-t));
 if(Boolean.getBoolean("side")){SwingUtilities.invokeAndWait(()->{JTextArea hit=first(p[0]);hit.dispatchEvent(new java.awt.event.MouseEvent(hit,java.awt.event.MouseEvent.MOUSE_CLICKED,0,0,5,5,1,false));});Thread.sleep(6000);}
 for(double k:new double[]{1.25,2}){double s=k;SwingUtilities.invokeAndWait(()->{try{Container c=f[0].getContentPane();var i=new java.awt.image.BufferedImage((int)(c.getWidth()*s),(int)(c.getHeight()*s),1);Graphics2D g=i.createGraphics();g.scale(s,s);c.printAll(g);g.dispose();ImageIO.write(i,"png",new File(a[0],"dash-"+s+".png"));}catch(Exception e){throw new RuntimeException(e);}});}
 System.out.println(SwingUtilities.isEventDispatchThread()+" scale="+f[0].getGraphicsConfiguration().getDefaultTransform().getScaleX());Runtime.getRuntime().halt(0);} static JTextArea first(Container c){for(Component k:c.getComponents()){if(k instanceof JTextArea t&&t.isShowing())return t;if(k instanceof Container b){JTextArea f=first(b);if(f!=null)return f;}}return null;}}
