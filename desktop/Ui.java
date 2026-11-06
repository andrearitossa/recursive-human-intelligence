import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/** Shared look and layout helpers, so every screen aligns the same way. */
public final class Ui {
    // The andrearitossa.com palette.
    static final Color BG=new Color(0xFAF7F2),ACCENT=new Color(0x6B1338),ACCENT_HOVER=new Color(0x85234B),INK=new Color(0x1C161A),BODY=new Color(0x4A4048),MUTED=new Color(0x756A71),LINE=new Color(0xE1DEDA),SOFT=new Color(0xF3EAE8);
    /** EB Garamond ships with the app, so it looks the same on every computer. */
    private static final Font BASE=loadFont();
    private static Font loadFont(){
        try(java.io.InputStream in=Ui.class.getResourceAsStream("/fonts/EBGaramond.ttf")){
            java.io.InputStream use=in!=null?in:new java.io.FileInputStream(fontFile());
            Font f=Font.createFont(Font.TRUETYPE_FONT,use);GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);return f;
        }catch(Exception e){return new Font(Font.SERIF,Font.PLAIN,12);}
    }
    /** Found beside the program, wherever it was launched from; otherwise Java silently falls back to Times. */
    private static java.io.File fontFile()throws Exception{
        java.io.File build=java.nio.file.Path.of(Ui.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile();
        for(java.io.File dir=build;dir!=null;dir=dir.getParentFile()){java.io.File f=new java.io.File(dir,"fonts/EBGaramond.ttf");if(f.isFile())return f;}
        return new java.io.File("fonts/EBGaramond.ttf");
    }
    // Garamond runs small, so text sizes are stepped up to stay as readable as before.
    static Font sans(int style,int size){return BASE.deriveFont(style,size+2f);}
    static Font serif(int size){return BASE.deriveFont(Font.PLAIN,(float)size+2);}
    static Font heading(int size){return BASE.deriveFont(Font.BOLD,(float)size).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING,-0.01f));}
    static final int RADIUS=10;
    /** Fills a button's own rounded shape, so no square corner shows behind its outline. */
    private static final class RoundButton extends javax.swing.plaf.basic.BasicButtonUI {
        @Override public void paint(Graphics graphics,JComponent c){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(c.getBackground());g.fill(new java.awt.geom.RoundRectangle2D.Float(0,0,c.getWidth(),c.getHeight(),RADIUS,RADIUS));g.dispose();
            super.paint(graphics,c);
        }
        @Override protected void paintButtonPressed(Graphics g,AbstractButton b){}
        /** A disabled button keeps its own text color, faded, so a dark-filled button stays readable. */
        @Override protected void paintText(Graphics g,AbstractButton b,Rectangle r,String text){
            if(b.isEnabled()){super.paintText(g,b,r,text);return;}
            Color f=b.getForeground();g.setColor(new Color(f.getRed(),f.getGreen(),f.getBlue(),150));FontMetrics m=g.getFontMetrics();
            javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(b,(Graphics2D)g,text,-1,r.x+getTextShiftOffset(),r.y+m.getAscent()+getTextShiftOffset());
        }
    }
    /** A rounded outline with smooth corners; Swing's own LineBorder draws them jagged. */
    static Border round(Color color,int thickness){
        return new javax.swing.border.AbstractBorder(){
            @Override public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){
                Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
                g.setColor(color);g.setStroke(new BasicStroke(thickness));float inset=thickness/2f;
                g.draw(new java.awt.geom.RoundRectangle2D.Float(x+inset,y+inset,w-thickness,h-thickness,RADIUS,RADIUS));g.dispose();
            }
            @Override public Insets getBorderInsets(Component c,Insets in){in.set(thickness,thickness,thickness,thickness);return in;}
        };
    }
    static void install(){
        System.setProperty("swing.boldMetal","false");
        Font base=sans(Font.PLAIN,14);
        for(String key:new String[]{"Label.font","Button.font","CheckBox.font","ToggleButton.font","TextField.font","PasswordField.font","TextArea.font","ComboBox.font","Table.font","TableHeader.font","List.font","ToolTip.font","OptionPane.messageFont","OptionPane.buttonFont"})UIManager.put(key,base);
        UIManager.put("ScrollBarUI",ThinBar.class.getName());UIManager.put("CheckBox.icon",new Tick());UIManager.put("ScrollPane.background",BG);
        UIManager.put("Table.selectionBackground",SOFT);UIManager.put("Table.selectionForeground",INK);UIManager.put("Table.focusCellHighlightBorder",new EmptyBorder(1,1,1,1));
        UIManager.put("TableHeader.background",BG);UIManager.put("TableHeader.cellBorder",new CompoundBorder(new MatteBorder(0,0,1,0,LINE),new EmptyBorder(6,6,6,6)));
        UIManager.put("Panel.background",BG);UIManager.put("OptionPane.background",BG);UIManager.put("Button.focus",new Color(0,0,0,0));UIManager.put("CheckBox.focus",new Color(0,0,0,0));
    }
    static JButton button(String text){
        JButton b=new JButton(text);b.setUI(new RoundButton());b.setOpaque(false);b.setContentAreaFilled(false);b.setFocusPainted(false);b.setBackground(Color.WHITE);b.setForeground(INK);
        b.setBorder(new CompoundBorder(Ui.round(LINE,1),new EmptyBorder(8,14,8,14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));hover(b,Color.WHITE,SOFT);return b;
    }
    static JButton primary(String text){JButton b=button(text);b.setBackground(ACCENT);b.setForeground(Color.WHITE);b.setBorder(new CompoundBorder(Ui.round(ACCENT,1),new EmptyBorder(8,14,8,14)));hover(b,ACCENT,ACCENT_HOVER);return b;}
    static void hover(JButton b,Color normal,Color over){
        for(var old:b.getMouseListeners())if(old instanceof Hover)b.removeMouseListener(old);
        b.addMouseListener(new Hover(b,normal,over));
    }
    private record Hover(JButton b,Color normal,Color over) implements java.awt.event.MouseListener{
        public void mouseEntered(java.awt.event.MouseEvent e){if(b.isEnabled())b.setBackground(over);}
        public void mouseExited(java.awt.event.MouseEvent e){b.setBackground(normal);}
        public void mouseClicked(java.awt.event.MouseEvent e){}public void mousePressed(java.awt.event.MouseEvent e){}public void mouseReleased(java.awt.event.MouseEvent e){}
    }
    /** A flat text field whose border wakes up on focus. */
    static <T extends javax.swing.text.JTextComponent> T field(T f){
        Border rest=new CompoundBorder(new LineBorder(LINE),new EmptyBorder(6,8,6,8)),on=new CompoundBorder(new LineBorder(ACCENT),new EmptyBorder(6,8,6,8));
        f.setBorder(rest);f.addFocusListener(new java.awt.event.FocusAdapter(){@Override public void focusGained(java.awt.event.FocusEvent e){f.setBorder(on);}@Override public void focusLost(java.awt.event.FocusEvent e){f.setBorder(rest);}});return f;
    }
    /** Fixes a text button's width to its widest state, so a label is never cut off. */
    static void fit(AbstractButton b,Font widest,String longest){
        var m=b.getFontMetrics(widest);java.awt.Insets in=b.getInsets();b.setPreferredSize(new Dimension(m.stringWidth(longest)+in.left+in.right+8,m.getHeight()+in.top+in.bottom));
    }
    /** The RHI mark: a burgundy tile with an R, drawn at any size. */
    static java.awt.image.BufferedImage icon(int size){
        var i=new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_ARGB);Graphics2D g=i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(ACCENT);g.fillRoundRect(0,0,size,size,size/4,size/4);
        g.setColor(BG);g.setFont(BASE.deriveFont(Font.BOLD,size*.82f));FontMetrics m=g.getFontMetrics();g.drawString("R",(size-m.stringWidth("R"))/2f,(size+m.getAscent()-m.getDescent())/2f);g.dispose();return i;
    }
    /** Every detail window closes with Esc. */
    static void closeOnEscape(JDialog d){d.getRootPane().registerKeyboardAction(e->d.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);}
    public static final class ThinBar extends javax.swing.plaf.basic.BasicScrollBarUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c){return new ThinBar();}
        @Override protected void installDefaults(){super.installDefaults();scrollBarWidth=10;}
        private JButton none(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
        @Override protected JButton createDecreaseButton(int o){return none();}
        @Override protected JButton createIncreaseButton(int o){return none();}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(BG);g.fillRect(r.x,r.y,r.width,r.height);}
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){if(r.isEmpty())return;Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);d.setColor(isDragging||isThumbRollover()?MUTED:new Color(0xD6D1CD));d.fillRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,6,6);d.dispose();}
    }
    private static final class Tick implements Icon {
        public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            boolean on=((AbstractButton)c).isSelected();Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(on?ACCENT:Color.WHITE);d.fillRoundRect(x+1,y+1,15,15,5,5);d.setColor(on?ACCENT:new Color(0xB9B2B0));d.drawRoundRect(x+1,y+1,15,15,5,5);
            if(on){d.setColor(Color.WHITE);d.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));d.drawPolyline(new int[]{x+5,x+8,x+13},new int[]{y+9,y+12,y+6},3);}d.dispose();
        }
    }
    static JLabel label(String text,Font font,Color color){JLabel l=new JLabel(text);l.putClientProperty("html.disable",Boolean.TRUE);l.setFont(font);l.setForeground(color);return l;}
    static JLabel heading(String text){return label(text,heading(32),INK);}
    static JLabel section(String text){JLabel l=label(text.toUpperCase(java.util.Locale.ROOT),sans(Font.BOLD,11).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING,0.16f)),ACCENT);return l;}
    static JLabel muted(String text){return label(text,sans(Font.PLAIN,13),MUTED);}
    /** Read-only text that wraps to its container and never interprets markup. */
    static JTextArea prose(String text,Color color){
        JTextArea area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setForeground(color);area.setFont(sans(Font.PLAIN,14));area.setBorder(null);area.setFocusable(false);((javax.swing.text.DefaultCaret)area.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);return area;
    }
    static JButton link(String text){JButton b=new JButton(text);b.setBorder(new EmptyBorder(2,0,2,0));b.setContentAreaFilled(false);b.setOpaque(false);b.setFocusPainted(false);b.setForeground(ACCENT);b.setFont(sans(Font.PLAIN,12));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;}
    static JPanel row(Component... items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));p.setOpaque(false);for(int i=0;i<items.length;i++){if(i>0)p.add(Box.createHorizontalStrut(8));p.add(items[i]);}return p;}
    static JScrollPane scroll(Component view){JScrollPane s=new JScrollPane(view);s.setBorder(null);s.getViewport().setBackground(BG);s.getVerticalScrollBar().setUnitIncrement(18);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return s;}
    static Stack card(boolean highlighted){
        Stack p=new Stack(){@Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Color.WHITE);g.fill(new java.awt.geom.RoundRectangle2D.Float(0,0,getWidth(),getHeight(),RADIUS,RADIUS));g.dispose();}};
        p.setBorder(new CompoundBorder(round(highlighted?ACCENT:LINE,highlighted?2:1),new EmptyBorder(16,18,16,18)));return p;
    }

    /** A left-aligned vertical stack whose rows fill the width; tracks a scroll pane's width so text wraps. */
    static class Stack extends JPanel implements Scrollable {
        private final GridBagConstraints c=new GridBagConstraints();
        Stack(){super(new GridBagLayout());setOpaque(false);c.gridx=0;c.gridy=GridBagConstraints.RELATIVE;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTHWEST;}
        Stack put(Component item,int gapAbove){c.insets=new Insets(gapAbove,0,0,0);add(item,c);return this;}
        Stack put(Component item){return put(item,0);}
        /** Keeps rows at the top when the view is taller than its content. */
        Stack end(){c.weighty=1;add(Box.createGlue(),c);c.weighty=0;return this;}
        @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        @Override public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 18;}
        @Override public int getScrollableBlockIncrement(Rectangle r,int o,int d){return r.height;}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return getParent() instanceof JViewport v&&v.getHeight()>getPreferredSize().height;}
    }
    private Ui(){}
}
