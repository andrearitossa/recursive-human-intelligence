import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/** Shared look and layout helpers, so every screen aligns the same way. */
final class Ui {
    static final Color BG=new Color(247,247,242),GREEN=new Color(50,74,45),MUTED=new Color(96,106,88),LINE=new Color(218,225,209),INK=new Color(41,60,38),SOFT=new Color(235,240,228);
    static final String SANS=installed("Segoe UI")?"Segoe UI":Font.SANS_SERIF;
    static boolean installed(String name){return java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains(name);}
    static Font sans(int style,int size){return new Font(SANS,style,size);}
    static Font serif(int size){return new Font(installed("Georgia")?"Georgia":Font.SERIF,Font.PLAIN,size);}
    static void install(){
        System.setProperty("swing.boldMetal","false");
        Font base=sans(Font.PLAIN,14);
        for(String key:new String[]{"Label.font","Button.font","CheckBox.font","ToggleButton.font","TextField.font","PasswordField.font","TextArea.font","ComboBox.font","Table.font","TableHeader.font","List.font","ToolTip.font","OptionPane.messageFont","OptionPane.buttonFont"})UIManager.put(key,base);
        UIManager.put("Panel.background",BG);UIManager.put("OptionPane.background",BG);UIManager.put("Button.focus",new Color(0,0,0,0));UIManager.put("CheckBox.focus",new Color(0,0,0,0));
    }
    static JButton button(String text){
        JButton b=new JButton(text);b.setFocusPainted(false);b.setBackground(Color.WHITE);b.setForeground(INK);
        b.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(8,14,8,14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static JButton primary(String text){JButton b=button(text);b.setBackground(GREEN);b.setForeground(Color.WHITE);b.setBorder(new CompoundBorder(new LineBorder(GREEN,1,true),new EmptyBorder(8,14,8,14)));return b;}
    static JLabel label(String text,Font font,Color color){JLabel l=new JLabel(text);l.putClientProperty("html.disable",Boolean.TRUE);l.setFont(font);l.setForeground(color);return l;}
    static JLabel heading(String text){return label(text,serif(30),INK);}
    static JLabel section(String text){return label(text,sans(Font.BOLD,15),GREEN);}
    static JLabel muted(String text){return label(text,sans(Font.PLAIN,13),MUTED);}
    /** Read-only text that wraps to its container and never interprets markup. */
    static JTextArea prose(String text,Color color){
        JTextArea area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setForeground(color);area.setFont(sans(Font.PLAIN,14));area.setBorder(null);area.setFocusable(false);return area;
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
