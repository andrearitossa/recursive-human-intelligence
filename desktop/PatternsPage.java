import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.util.*;
import java.util.List;

/** The two-step view: 1 see your patterns, 2 what they mean and what to do. */
final class PatternsPage extends JPanel {
    static final Map<Patterns.Mode,Color> COLORS=new EnumMap<>(Map.of(Patterns.Mode.BUILD,new Color(74,124,89),Patterns.Mode.FIX,new Color(193,84,62),Patterns.Mode.UNDERSTAND,new Color(70,120,170),Patterns.Mode.CHECK,new Color(196,150,50),Patterns.Mode.PLAN,new Color(125,92,160),Patterns.Mode.RESEARCH,new Color(52,145,145),Patterns.Mode.CORRECT,new Color(190,70,110),Patterns.Mode.STEER,new Color(140,145,135)));
    final Loop host;
    final JButton ask=Ui.primary("Get feedback"),analyze=Ui.button("Analyze with AI");
    final JTextField focus=new JTextField(20);
    final JProgressBar reading=new JProgressBar();
    final JLabel ticker=Ui.muted(" ");
    final LlmPatterns llm;
    boolean running;
    boolean showDone;
    final JLabel scope=Ui.muted(""),overview=Ui.muted("");
    final Map_ map=new Map_();
    final ModeBars bars=new ModeBars();
    final Ui.Stack detail=new Ui.Stack(),results=new Ui.Stack();
    final List<Patterns.Group> selected=new ArrayList<>();
    Patterns.Mode modeFilter;
    JScrollPane side;
    JPanel liveBar;
    Patterns.Result result;
    int analyzedSignature=-1;
    final List<Insights.Finding> feedback;

    PatternsPage(Loop host){
        this.host=host;feedback=Insights.load(host.data.getParent());llm=new LlmPatterns(host.data.getParent());
        setLayout(new BorderLayout(0,12));setOpaque(false);
        JPanel stepper=new JPanel(new BorderLayout());stepper.setOpaque(false);        focus.setText(host.settings.getProperty("patterns.focus",""));focus.setToolTipText("Optional, for example: my side projects");
        focus.addFocusListener(new FocusAdapter(){@Override public void focusLost(FocusEvent e){host.settings.setProperty("patterns.focus",focus.getText().strip());try{host.saveSettings();}catch(Exception ignored){}}});
        JPanel run=new JPanel(new GridBagLayout());run.setOpaque(false);Ui.field(focus);run.add(Ui.row(new JLabel("Focus on"),focus,analyze));stepper.add(run,BorderLayout.EAST);
        analyze.addActionListener(e->runAi());
        add(stepper,BorderLayout.NORTH);
        add(stepOnePage(),BorderLayout.CENTER);
        ask.addActionListener(e->ask());
        renderReports();
    }
    private JComponent stepOnePage(){
        JPanel left=new JPanel(new BorderLayout(0,8));left.setOpaque(false);
        JPanel top=new JPanel(new BorderLayout(0,8));top.setOpaque(false);top.add(overview,BorderLayout.NORTH);top.add(bars,BorderLayout.CENTER);left.add(top,BorderLayout.NORTH);left.add(map,BorderLayout.CENTER);
        reading.setPreferredSize(new Dimension(100,5));reading.setBorderPainted(false);reading.setForeground(Ui.GREEN);reading.setBackground(Ui.LINE);
        JPanel live=new JPanel(new BorderLayout(0,5));live.setOpaque(false);live.add(reading,BorderLayout.NORTH);live.add(ticker,BorderLayout.SOUTH);live.setVisible(false);liveBar=live;left.add(live,BorderLayout.SOUTH);
        side=Ui.scroll(detail);side.setPreferredSize(new Dimension(340,100));detail.setBorder(new EmptyBorder(0,6,0,10));
        JPanel action=new JPanel(new BorderLayout(0,6));action.setOpaque(false);action.setBorder(new EmptyBorder(10,6,0,10));action.add(scope,BorderLayout.NORTH);action.add(ask,BorderLayout.SOUTH);
        JPanel right=new JPanel(new BorderLayout());right.setOpaque(false);right.add(side,BorderLayout.CENTER);right.add(action,BorderLayout.SOUTH);
        JPanel page=new JPanel(new BorderLayout(14,0));page.setOpaque(false);page.add(left,BorderLayout.CENTER);page.add(right,BorderLayout.EAST);return page;
    }
    /** Re-clusters only when the chats changed. Runs off the UI thread because large histories take seconds. */
    void refresh(){
        int signature=host.engine.requests.keySet().hashCode();
        if(signature==analyzedSignature||host.busy||running)return;
        analyzedSignature=signature;overview.setText("Reading your requests…");
        Map<String,ChatEngine.Request> copy=new LinkedHashMap<>(host.engine.requests);
        new SwingWorker<Patterns.Result,Void>(){
            @Override protected Patterns.Result doInBackground(){return llm.hasPatterns()?llm.result(copy):Patterns.analyze(copy);}
            @Override protected void done(){try{result=get();selected.clear();modeFilter=null;render();}catch(Exception e){overview.setText("Could not analyze: "+Workspace.message(e));}}
        }.execute();
    }
    void render(){
        if(result.requests()==0){overview.setText("Import your chats first.");}
        else if(running){}
        else if(llm.hasPatterns()){int unread=llm.pending(host.engine.requests).size();overview.setText(result.groups().size()+" patterns from "+result.requests()+" requests in "+result.sessions()+" chats"+(unread>0?"  ·  "+unread+" new to read":""));analyze.setText(unread>0?"Read "+unread+" new":"Regroup");if(!running&&unread==0&&!llm.upToDate(host.engine.requests))overview.setText(overview.getText()+"  ·  chats changed, Get feedback will update this");}
        else{overview.setText("Quick keyword map of "+result.requests()+" requests. AI finds clearer patterns.");analyze.setText("Analyze with AI");}
        bars.repaint();map.layoutBubbles();renderDetail();
    }
    List<Patterns.Group> visible(){List<Patterns.Group> out=new ArrayList<>();if(result!=null)for(Patterns.Group g:result.groups())if(modeFilter==null||g.dominant()==modeFilter)out.add(g);return out;}
    void toggle(Patterns.Group g){if(!selected.remove(g))selected.add(g);map.repaint();bars.repaint();renderDetail();}
    void renderDetail(){
        detail.removeAll();
        if(selected.isEmpty()){
            detail.put(Ui.section("All patterns"));
            int shown=0;for(Patterns.Group g:visible()){shown++;JButton row=new JButton(g.label+"  ("+g.sessions.size()+")");row.setHorizontalAlignment(SwingConstants.LEFT);row.setFocusPainted(false);row.setContentAreaFilled(false);row.setBorder(new EmptyBorder(5,0,5,0));row.setForeground(Ui.INK);row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));row.addActionListener(e->toggle(g));
                row.addMouseListener(new MouseAdapter(){@Override public void mouseEntered(MouseEvent e){map.hover=g;row.setForeground(COLORS.get(g.dominant()));map.repaint();}@Override public void mouseExited(MouseEvent e){map.hover=null;row.setForeground(Ui.INK);map.repaint();}});detail.put(row,shown==1?6:0);}
        }else for(Patterns.Group g:selected){
            detail.put(Ui.label(g.label,Ui.sans(Font.BOLD,15),COLORS.get(g.dominant())),detail.getComponentCount()==0?0:18);
            detail.put(Ui.muted(g.sessions.size()+" chats  ·  "+g.items.size()+" requests  ·  mostly "+g.dominant().label.toLowerCase(Locale.ROOT)),2);
            if(!g.description.isBlank())detail.put(Ui.prose(g.description,Ui.INK),6);
            int n=0;for(Patterns.Item item:g.examples){if(n++==5)break;String t=item.request().text().strip().replaceAll("\\s+"," ");detail.put(Ui.prose("“"+(t.length()>150?t.substring(0,150)+"…":t)+"”",Ui.MUTED),8);}
        }
        detail.end();detail.revalidate();detail.repaint();if(!running)SwingUtilities.invokeLater(()->side.getVerticalScrollBar().setValue(0));
        List<Patterns.Group> s=scoped();int chats=0;for(Patterns.Group g:s)chats+=g.sessions.size();
        scope.setText(selected.isEmpty()?(modeFilter==null?"All "+s.size()+" patterns":s.size()+" "+modeFilter.label.toLowerCase(Locale.ROOT)+" patterns"):selected.size()+(selected.size()==1?" pattern":" patterns")+" selected");
        ask.setEnabled(!host.busy&&!s.isEmpty());
    }
    List<Patterns.Group> scoped(){return selected.isEmpty()?visible():new ArrayList<>(selected);}
    void setBusy(boolean active){analyze.setEnabled(!active);focus.setEnabled(!active);ask.setEnabled(!active&&result!=null&&!scoped().isEmpty());if(!active&&!running)refresh();}
    /** The one "Find the problems" action. Works from any screen, and answers appear on Home. */
    void ask(){
        Models.Model connection=host.workspace.connectOrAsk();if(connection!=null)ask(connection);
    }
    /** The whole path from Home: read what is new, group it, then write the feedback. */
    void flow(Models.Model connection){
        selected.clear();
        Map<String,ChatEngine.Request> now=host.engine.requests;
        if(llm.upToDate(now)){Activity.log("Patterns: up to date");result=llm.result(now);analyzedSignature=now.keySet().hashCode();render();ask(connection);return;}
        int[] c=llm.changes(now);Activity.log("Patterns: "+c[0]+" requests added, "+c[1]+" removed since the last grouping. Updating.");
        runAi(connection,()->ask(connection));
    }
    void ask(Models.Model connection){
        boolean ready=result!=null&&analyzedSignature==host.engine.requests.keySet().hashCode();
        List<Patterns.Group> chosen=ready?scoped():null;String wanted=focus.getText().strip();List<Insights.Finding> told=new ArrayList<>(feedback);boolean selected0=selected.isEmpty();
        Map<String,ChatEngine.Request> copy=new LinkedHashMap<>(host.engine.requests);
        host.select("Home");host.workspace.thinking=true;host.workspace.refresh();
        int[] next={0};
        host.workspace.run("Writing your feedback…",true,()->{
            List<Patterns.Group> groups=chosen!=null?chosen:(llm.hasPatterns()?llm.result(copy):Patterns.analyze(copy)).groups();
            // With nothing selected the agent reads every described request, not just the samples.
            Map<String,String> tasks=new HashMap<>();llm.descriptions.forEach((id,d)->tasks.put(id,d.task()));
            List<Patterns.Item> described=new ArrayList<>();
            if(chosen==null||selected0)described.addAll(Patterns.distinct(copy));else for(Patterns.Group g:groups)described.addAll(g.items);
            Map<String,Patterns.Item> refs=new HashMap<>();long now=System.currentTimeMillis();int[] shown={0};
            Map<String,Object> signals=Patterns.signals(copy);
            Activity.log("Signals: median "+signals.get("medianWords")+" words per request, "+signals.get("percentSixWordsOrFewer")+"% are six words or fewer, "+((List<?>)signals.get("phrasesRetypedAcrossChats")).size()+" phrases retyped across chats");
            ItemStream stream=new ItemStream(m->{
                Insights.Finding one=Insights.parseOne(m,refs,now);if(one==null)return;shown[0]++;
                Activity.log("+ "+one.name()+"  ("+one.evidence().size()+" quotes"+(one.actions().stream().anyMatch(a->!a.source().isBlank())?", names a source":"")+(one.you().isEmpty()?"":", sharper version")+")");
                SwingUtilities.invokeLater(()->{
                    // Each new item lands right after the last one from this run, above older feedback.
                    feedback.add(next[0]++,one);
                    try{Insights.save(host.data.getParent(),feedback);}catch(Exception e){Activity.log("✕ could not save feedback");}
                    host.status.setText("Writing your feedback… "+next[0]+" so far");renderReports();if(next[0]==1)host.workspace.scrollHomeToTop();
                });
            });
            String sentInput=Insights.input(groups,described,tasks,refs,wanted,told,signals);
            long lines=refs.keySet().stream().filter(k->k.startsWith("t")).count();
            Activity.log("Feedback input: "+lines+" request lines, "+(refs.size()-lines)+" excerpts from "+groups.size()+" patterns, "+Math.min(told.size(),Insights.MAX_TOLD)+" earlier items to avoid"+(wanted.isEmpty()?"":", focus set"));
            String response=connection.stream(Insights.INSTRUCTIONS,sentInput,stream::accept);
            if(shown[0]==0)Insights.parse(response,refs);
            return shown[0];
        },out->{
            int count=(Integer)out;
            host.status.setText(count==0?"Nothing new to say.":count+(count==1?" new item.":" new items."));Activity.log("Feedback done: "+count+" items");
            renderReports();
        });
        host.workspace.scrollHomeToTop();
    }
    /** Reads requests in batches and regroups, with the map growing as each batch returns. */
    void runAi(){
        Models.Model connection=host.workspace.connectOrAsk();if(connection!=null)runAi(connection,null);
    }
    void runAi(Models.Model connection,Runnable after){
        Map<String,ChatEngine.Request> copy=new LinkedHashMap<>(host.engine.requests);
        List<Patterns.Item> todo=llm.pending(copy);
        if(todo.isEmpty()&&llm.descriptions.isEmpty()){host.status.setText("Import your chats first.");return;}
        // Reading sends far more than other steps, so the amount is stated every time.
        if(!todo.isEmpty()&&JOptionPane.showConfirmDialog(host.frame,"Send "+todo.size()+" requests to "+host.workspace.provider.getSelectedItem()+"?\nEach is cut to "+LlmPatterns.EXCERPT+" characters, about "+Math.max(1,LlmPatterns.characters(todo)/1000)+"k characters in total. Obvious credentials are removed, but names and other details may remain.","Analyze with AI",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        String wanted=focus.getText().strip();
        host.workspace.flowing=after!=null;running=true;selected.clear();modeFilter=null;liveBar.setVisible(true);reading.setValue(0);reading.setIndeterminate(todo.isEmpty());ticker.setText(" ");
        overview.setText(todo.isEmpty()?"Grouping…":"Reading 0 of "+todo.size()+"…");
        host.workspace.run("Analyzing your requests…",true,()->{
            llm.run(connection,copy,wanted,(stage,done,total,latest)->SwingUtilities.invokeLater(()->{
                reading.setIndeterminate(false);reading.setMaximum(Math.max(1,total));reading.setValue(done);
                String now=stage.equals("Grouping")?"Grouping… "+done+(done==1?" pattern":" patterns")+" so far":"Reading "+done+" of "+total+" requests…";
                overview.setText(now);host.status.setText(now);if(stage.equals("Grouping"))reading.setIndeterminate(true);
                if(!latest.isEmpty())ticker.setText((stage.equals("Grouping")?"Found: ":"“")+latest+(stage.equals("Grouping")?"":"”"));
                result=llm.live(copy);rebind();bars.repaint();map.layoutBubbles();renderDetail();
            }));
            return null;
        },out->{finishRun(copy,null);if(after!=null)after.run();});
        watchFinish();
    }
    /** A stopped or failed run must also leave the running state. */
    /** Selections survive as the live map updates, so it can be used while still growing. */
    private void rebind(){
        Set<String> names=new HashSet<>();for(Patterns.Group g:selected)names.add(g.label);selected.clear();
        for(Patterns.Group g:result.groups())if(names.contains(g.label))selected.add(g);
    }
    private void watchFinish(){
        javax.swing.Timer t=new javax.swing.Timer(250,null);
        t.addActionListener(e->{if(!host.busy){t.stop();if(running)finishRun(new LinkedHashMap<>(host.engine.requests),host.status.getText());}});t.start();
    }
    private void finishRun(Map<String,ChatEngine.Request> copy,String message){
        running=false;liveBar.setVisible(false);
        result=llm.hasPatterns()?llm.result(copy):Patterns.analyze(copy);analyzedSignature=host.engine.requests.keySet().hashCode();selected.clear();render();
        if(message!=null)host.status.setText(message);
        else host.status.setText(llm.failedBatches>0?llm.failedBatches+(llm.failedBatches==1?" batch":" batches")+" could not be read. Run it again to retry.":result.groups().size()+" patterns found.");
    }
    void forget(){try{llm.clear();}catch(Exception e){host.error(e);}feedback.clear();analyzedSignature=-1;renderReports();}
    /** A clean list: a name and one line. Everything else waits behind a click. */
    void renderReports(){
        results.removeAll();boolean first=true;int done=0;
        for(Insights.Finding f:feedback){if(f.removed()){done++;continue;}results.put(row(f,first,false));first=false;}
        if(done>0){
            JButton toggle=quiet((showDone?"Hide done":"Show done")+" ("+done+")");toggle.addActionListener(e->{showDone=!showDone;renderReports();});
            results.put(Ui.row(toggle),first?0:12);
            if(showDone)for(Insights.Finding f:feedback)if(f.removed())results.put(row(f,false,true));
        }
        results.revalidate();results.repaint();host.workspace.refresh();
    }
    boolean hasFeedback(){return !feedback.isEmpty();}
    void setDone(Insights.Finding f,boolean done){
        // Done items stay on file, so the agent knows not to bring them back.
        feedback.set(feedback.indexOf(f),f.withDone(done));
        try{Insights.save(host.data.getParent(),feedback);}catch(Exception e){host.error(e);}
        host.status.setText(done?f.name()+" is done.":f.name()+" is back on your list.");renderReports();
    }
    /** A text link: present when wanted, without competing with the content. */
    private static JButton quiet(String text){
        JButton b=new JButton(text);b.setFocusPainted(false);b.setContentAreaFilled(false);b.setBorder(new EmptyBorder(6,8,6,8));b.setForeground(Ui.MUTED);b.setFont(Ui.sans(Font.PLAIN,13));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter(){@Override public void mouseEntered(MouseEvent e){b.setForeground(Ui.GREEN);}@Override public void mouseExited(MouseEvent e){b.setForeground(Ui.MUTED);}});return b;
    }
    private JComponent row(Insights.Finding f,boolean top,boolean done){
        Color rest=done?Ui.BG:Color.WHITE;
        JPanel row=new JPanel(new BorderLayout(12,0));row.setBackground(rest);row.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,Ui.LINE),new EmptyBorder(done?9:14,18,done?9:14,12)));row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Ui.Stack text=new Ui.Stack();text.put(Ui.label(f.name(),done?Ui.sans(Font.PLAIN,14):Ui.serif(19),done?Ui.MUTED:Ui.INK));if(!done)text.put(Ui.label(f.description(),Ui.sans(Font.PLAIN,14),Ui.MUTED),2);row.add(text,BorderLayout.CENTER);
        JButton mark=quiet(done?"Undo":"Mark as done");mark.addActionListener(e->setDone(f,!done));JPanel east=new JPanel(new GridBagLayout());east.setOpaque(false);east.add(mark);row.add(east,BorderLayout.EAST);
        MouseAdapter open=new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){detail(f);}@Override public void mouseEntered(MouseEvent e){if(!done)row.setBackground(new Color(251,252,249));}@Override public void mouseExited(MouseEvent e){row.setBackground(rest);}};row.addMouseListener(open);text.addMouseListener(open);
        return row;
    }
    void detail(Insights.Finding f){
        JDialog dialog=new JDialog(host.frame,f.name(),false);
        Ui.Stack body=new Ui.Stack();body.setBorder(new EmptyBorder(26,30,20,30));
        body.put(Ui.label(f.name(),Ui.serif(28),Ui.INK));
        JTextArea message=Ui.prose(f.message().isBlank()?f.description():f.message(),Ui.INK);message.setFont(Ui.sans(Font.PLAIN,16));body.put(message,12);
        if(!f.you().isBlank()){
            Ui.Stack quote=new Ui.Stack();quote.setBorder(new CompoundBorder(new MatteBorder(0,3,0,0,Ui.GREEN),new EmptyBorder(2,12,2,0)));
            quote.put(Ui.label("YOU WROTE",Ui.sans(Font.BOLD,11),Ui.MUTED));quote.put(Ui.prose("“"+f.you()+"”",Ui.MUTED),2);
            quote.put(Ui.label("SHARPER",Ui.sans(Font.BOLD,11),Ui.GREEN),8);JTextArea sharp=Ui.prose(f.better(),Ui.INK);quote.put(sharp,2);body.put(quote,16);
        }
        for(Insights.Action a:f.actions()){
            Ui.Stack box=new Ui.Stack();box.setOpaque(true);box.setBackground(Ui.SOFT);box.setBorder(new EmptyBorder(14,16,14,16));box.put(Ui.prose(a.text(),Ui.INK));
            JButton copy=Ui.button("Copy");copy.addActionListener(e->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(a.text()),null);host.status.setText("Copied.");});
            if(a.source().isBlank())box.put(Ui.row(copy),10);
            else{JButton find=Ui.primary("Find: "+(a.source().length()>60?a.source().substring(0,60)+"…":a.source()));find.addActionListener(e->search(a.source()));box.put(Ui.row(find,copy),10);}
            body.put(box,14);
        }
        JButton why=Ui.button("Why am I seeing this?");Ui.Stack quotes=new Ui.Stack();quotes.setVisible(false);
        for(Insights.Quote q:f.evidence()){String t=q.text().replaceAll("\\s+"," ");quotes.put(Ui.prose("“"+(t.length()>220?t.substring(0,220)+"…":t)+"”",Ui.MUTED),8);}
        why.addActionListener(e->{quotes.setVisible(!quotes.isVisible());body.revalidate();});
        JButton remove=Ui.button(f.removed()?"Undo":"Mark as done");remove.addActionListener(e->{setDone(f,!f.removed());dialog.dispose();});
        body.put(Ui.row(why,remove),18);body.put(quotes);body.end();
        dialog.setContentPane(Ui.scroll(body));dialog.getContentPane().setBackground(Ui.BG);dialog.setSize(640,560);dialog.setLocationRelativeTo(host.frame);Ui.closeOnEscape(dialog);dialog.setVisible(true);
    }
    /** A web search for the exact title always works, unlike a link a model might invent. */
    void search(String source){
        try{Desktop.getDesktop().browse(java.net.URI.create("https://www.google.com/search?q="+java.net.URLEncoder.encode(source,java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){host.error(e);}
    }

    /** The bar describes what is in view: the selection, else the patterns found so far, else everything read. */
    Map<Patterns.Mode,Integer> barCounts(){
        boolean grouping=running&&!llm.partial.isEmpty();
        if(selected.isEmpty()&&!grouping)return result.modes();
        Map<String,Patterns.Item> unique=new HashMap<>();
        for(Patterns.Group g:selected.isEmpty()?result.groups():selected)for(Patterns.Item i:g.items)unique.put(i.id(),i);
        Map<Patterns.Mode,Integer> counts=new EnumMap<>(Patterns.Mode.class);for(Patterns.Item i:unique.values())counts.merge(i.mode(),1,Integer::sum);
        return counts;
    }
    /** How the user works: a single stacked bar. Clicking a color filters the map. */
    final class ModeBars extends JComponent {
        final List<Object[]> hits=new ArrayList<>();
        ModeBars(){
            setPreferredSize(new Dimension(100,48));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){for(Object[] h:hits)if(((Rectangle)h[0]).contains(e.getPoint())){Patterns.Mode m=(Patterns.Mode)h[1];modeFilter=modeFilter==m?null:m;selected.clear();render();return;}}});
        }
        @Override protected void paintComponent(Graphics graphics){
            hits.clear();if(result==null||result.requests()==0)return;
            Map<Patterns.Mode,Integer> counts=barCounts();int all=counts.values().stream().mapToInt(Integer::intValue).sum();if(all==0)return;
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int x=0,w=getWidth();g.setFont(Ui.sans(Font.PLAIN,12));int lx=0,ly=28;
            for(Patterns.Mode m:Patterns.Mode.values()){
                int n=counts.getOrDefault(m,0);if(n==0)continue;int bw=Math.max(3,(int)Math.round(w*(double)n/all));
                boolean dim=modeFilter!=null&&modeFilter!=m;Color c=COLORS.get(m);g.setColor(dim?new Color(c.getRed(),c.getGreen(),c.getBlue(),70):c);g.fillRect(x,0,bw-1,16);hits.add(new Object[]{new Rectangle(x,0,bw,16),m});x+=bw;
                String text=m.label+" "+Math.round(100.0*n/all)+"%";int tw=g.getFontMetrics().stringWidth(text);
                if(lx+tw+16>w&&lx>0){lx=0;ly+=19;}
                g.fillOval(lx,ly,9,9);g.setColor(dim?Ui.LINE:Ui.MUTED);g.drawString(text,lx+13,ly+9);hits.add(new Object[]{new Rectangle(lx,ly-5,tw+16,19),m});lx+=tw+26;
            }
            g.dispose();
            int wanted=ly+16;if(getPreferredSize().height!=wanted){setPreferredSize(new Dimension(100,wanted));revalidate();}
        }
    }
    /** The pattern map: bubbles glide to new sizes and places, so changes are felt rather than just appearing. */
    final class Map_ extends JComponent {
        final Map<Patterns.Group,double[]> bubbles=new LinkedHashMap<>();final Map<String,double[]> shown=new HashMap<>();Patterns.Group hover;
        final javax.swing.Timer motion=new javax.swing.Timer(16,e->step());
        Map_(){
            addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){layoutBubbles();}});
            addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){Patterns.Group g=at(e.getPoint());if(g!=null)toggle(g);}});
            addMouseMotionListener(new MouseMotionAdapter(){@Override public void mouseMoved(MouseEvent e){Patterns.Group g=at(e.getPoint());if(g!=hover){hover=g;setCursor(Cursor.getPredefinedCursor(g==null?Cursor.DEFAULT_CURSOR:Cursor.HAND_CURSOR));setToolTipText(g==null?null:g.label+" · "+g.sessions.size()+" chats");repaint();}}});
        }
        double[] now(Patterns.Group g){double[] b=shown.get(g.label);return b==null?bubbles.get(g):b;}
        Patterns.Group at(Point p){for(Patterns.Group g:bubbles.keySet()){double[] b=now(g);if(p.distance(b[0],b[1])<=b[2])return g;}return null;}
        void step(){
            boolean moving=false;
            for(var e:bubbles.entrySet()){double[] t=e.getValue(),c=shown.computeIfAbsent(e.getKey().label,k->new double[]{t[0],t[1],0});for(int i=0;i<3;i++){double d=t[i]-c[i];if(Math.abs(d)>.3){c[i]+=d*.14;moving=true;}else c[i]=t[i];}}
            if(!moving)motion.stop();repaint();
        }
        void layoutBubbles(){
            bubbles.clear();List<Patterns.Group> groups=visible();int w=getWidth(),h=getHeight();if(groups.isEmpty()||w<50||h<50){shown.clear();repaint();return;}
            double area=0;for(Patterns.Group g:groups)area+=g.sessions.size();
            double unit=Math.sqrt(w*h*0.50/(Math.PI*area));
            double biggest=Math.min(w,h)*.26;
            // Shrinks until every bubble fits, so none is ever cut off, however few or many there are.
            for(double scale=1;scale>.03&&!place(groups,unit*scale,biggest*scale,13*scale,w,h);scale*=.9){}
            Set<String> names=new HashSet<>();for(Patterns.Group g:groups)names.add(g.label);shown.keySet().retainAll(names);
            motion.start();
        }
        private boolean place(List<Patterns.Group> groups,double unit,double biggest,double smallest,int w,int h){
            bubbles.clear();List<double[]> placed=new ArrayList<>();
            for(Patterns.Group g:groups){
                double r=Math.min(biggest,Math.max(smallest,Math.sqrt(g.sessions.size())*unit));boolean fits=false;double x=0,y=0;
                // Golden-angle spiral outward: the largest pattern sits in the middle, stable between runs.
                for(int i=0;i<6000&&!fits;i++){double a=i*2.39996,d=Math.sqrt(i)*5;x=w/2.0+Math.cos(a)*d*1.35;y=h/2.0+Math.sin(a)*d*.85;
                    if(x-r<2||y-r<2||x+r>w-2||y+r>h-2)continue;fits=true;for(double[] o:placed)if(Math.hypot(o[0]-x,o[1]-y)<o[2]+r+4){fits=false;break;}}
                if(!fits)return false;
                double[] b={x,y,r};placed.add(b);bubbles.put(g,b);
            }
            return true;
        }
        private List<String> wrap(String label,FontMetrics m,double width,int max){
            List<String> lines=new ArrayList<>();String line="";
            for(String word:label.replace(" · "," ").split(" ")){String next=line.isEmpty()?word:line+" "+word;if(m.stringWidth(next)>width&&!line.isEmpty()){lines.add(line);line=word;}else line=next;}
            if(!line.isEmpty())lines.add(line);
            return lines.size()>max?lines.subList(0,max):lines;
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for(Patterns.Group p:bubbles.keySet()){
                double[] b=now(p);if(b[2]<1)continue;boolean on=selected.contains(p),off=!selected.isEmpty()&&!on;Color c=COLORS.get(p.dominant());
                g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),off?70:p==hover||on?255:205));g.fill(new Ellipse2D.Double(b[0]-b[2],b[1]-b[2],b[2]*2,b[2]*2));
                if(on){g.setColor(Ui.INK);g.setStroke(new BasicStroke(2.5f));g.draw(new Ellipse2D.Double(b[0]-b[2]-3,b[1]-b[2]-3,b[2]*2+6,b[2]*2+6));}
                if(b[2]<24)continue;
                // Whole words only: shrink the text before ever cutting one.
                g.setColor(Color.WHITE);FontMetrics m=null;List<String> lines=null;double room=b[2]*1.78;
                for(int size=b[2]>52?14:12;size>=9;size--){
                    g.setFont(Ui.sans(Font.BOLD,size));m=g.getFontMetrics();lines=wrap(p.label,m,room,99);boolean fits=lines.size()<=3&&lines.size()*m.getHeight()<=b[2]*1.5;
                    for(String t:lines)if(m.stringWidth(t)>room)fits=false;
                    if(fits)break;lines=null;
                }
                if(lines==null)continue; // Too small for its name: readable on hover and in the list.
                int y=(int)(b[1]-(lines.size()*m.getHeight())/2.0+m.getAscent());
                for(String t:lines){g.drawString(t,(int)(b[0]-m.stringWidth(t)/2.0),y);y+=m.getHeight();}
            }
            g.dispose();
        }
    }
}
