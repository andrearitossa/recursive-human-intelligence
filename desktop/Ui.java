import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/** Shared look and layout helpers, so every screen aligns the same way. */
public final class Ui {
    static final Color BG=new Color(247,247,242),GREEN=new Color(50,74,45),MUTED=new Color(96,106,88),LINE=new Color(218,225,209),INK=new Color(41,60,38),SOFT=new Color(235,240,228);
    static final String SANS=installed("Segoe UI")?"Segoe UI":Font.SANS_SERIF;
    static boolean installed(String name){return java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains(name);}
    static Font sans(int style,int size){return new Font(SANS,style,size);}
    static Font serif(int size){return new Font(installed("Georgia")?"Georgia":Font.SERIF,Font.PLAIN,size);}
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
        JButton b=new JButton(text);b.setFocusPainted(false);b.setBackground(Color.WHITE);b.setForeground(INK);
        b.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(8,14,8,14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));hover(b,Color.WHITE,SOFT);return b;
    }
    static JButton primary(String text){JButton b=button(text);b.setBackground(GREEN);b.setForeground(Color.WHITE);b.setBorder(new CompoundBorder(new LineBorder(GREEN,1,true),new EmptyBorder(8,14,8,14)));hover(b,GREEN,new Color(66,96,60));return b;}
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
        Border rest=new CompoundBorder(new LineBorder(LINE),new EmptyBorder(6,8,6,8)),on=new CompoundBorder(new LineBorder(GREEN),new EmptyBorder(6,8,6,8));
        f.setBorder(rest);f.addFocusListener(new java.awt.event.FocusAdapter(){@Override public void focusGained(java.awt.event.FocusEvent e){f.setBorder(on);}@Override public void focusLost(java.awt.event.FocusEvent e){f.setBorder(rest);}});return f;
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
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){if(r.isEmpty())return;Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);d.setColor(isDragging||isThumbRollover()?MUTED:new Color(200,207,192));d.fillRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,6,6);d.dispose();}
    }
    private static final class Tick implements Icon {
        public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            boolean on=((AbstractButton)c).isSelected();Graphics2D d=(Graphics2D)g.create();d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            d.setColor(on?GREEN:Color.WHITE);d.fillRoundRect(x+1,y+1,15,15,5,5);d.setColor(on?GREEN:new Color(180,188,172));d.drawRoundRect(x+1,y+1,15,15,5,5);
            if(on){d.setColor(Color.WHITE);d.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));d.drawPolyline(new int[]{x+5,x+8,x+13},new int[]{y+9,y+12,y+6},3);}d.dispose();
        }
    }
    static JLabel label(String text,Font font,Color color){JLabel l=new JLabel(text);l.putClientProperty("html.disable",Boolean.TRUE);l.setFont(font);l.setForeground(color);return l;}
    static JLabel heading(String text){return label(text,serif(30),INK);}
    static JLabel section(String text){return label(text,sans(Font.BOLD,15),GREEN);}
    static JLabel muted(String text){return label(text,sans(Font.PLAIN,13),MUTED);}
    /** Read-only text that wraps to its container and never interprets markup. */
    static JTextArea prose(String text,Color color){
        JTextArea area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setForeground(color);area.setFont(sans(Font.PLAIN,14));area.setBorder(null);area.setFocusable(false);((javax.swing.text.DefaultCaret)area.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);return area;
    }
    static JPanel row(Component... items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));p.setOpaque(false);for(int i=0;i<items.length;i++){if(i>0)p.add(Box.createHorizontalStrut(8));p.add(items[i]);}return p;}
    static JScrollPane scroll(Component view){JScrollPane s=new JScrollPane(view);s.setBorder(null);s.getViewport().setBackground(BG);s.getVerticalScrollBar().setUnitIncrement(18);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return s;}
    static Stack card(boolean highlighted){Stack p=new Stack();p.setOpaque(true);p.setBackground(Color.WHITE);p.setBorder(new CompoundBorder(new LineBorder(highlighted?GREEN:LINE,highlighted?2:1,true),new EmptyBorder(18,22,18,22)));return p;}

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
