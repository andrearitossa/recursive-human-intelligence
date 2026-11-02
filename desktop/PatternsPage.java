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
    final CardLayout steps=new CardLayout();final JPanel stepBody=new JPanel(steps);
    final JToggleButton stepOne=stepButton("1  Your patterns"),stepTwo=stepButton("2  What to do");
    final JButton ask=Ui.primary("Find the problems"),analyze=Ui.primary("Analyze with AI");
    final JTextField focus=new JTextField(20);
    final JProgressBar reading=new JProgressBar();
    final JLabel ticker=Ui.muted(" ");
    final LlmPatterns llm;
    boolean running;
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
    final List<Insights.Report> reports;

    PatternsPage(Loop host){
        this.host=host;reports=Insights.load(host.data.getParent());llm=new LlmPatterns(host.data.getParent());
        setLayout(new BorderLayout(0,12));setOpaque(false);
        JPanel stepper=new JPanel(new BorderLayout());stepper.setOpaque(false);stepper.add(Ui.row(stepOne,stepTwo),BorderLayout.WEST);
        focus.setText(host.settings.getProperty("patterns.focus",""));focus.setToolTipText("Optional, for example: my side projects");
        focus.addFocusListener(new FocusAdapter(){@Override public void focusLost(FocusEvent e){host.settings.setProperty("patterns.focus",focus.getText().strip());try{host.saveSettings();}catch(Exception ignored){}}});
        JPanel run=new JPanel(new GridBagLayout());run.setOpaque(false);run.add(Ui.row(new JLabel("Focus on"),focus,analyze));stepper.add(run,BorderLayout.EAST);
        analyze.addActionListener(e->runAi());
        ButtonGroup group=new ButtonGroup();group.add(stepOne);group.add(stepTwo);
        stepOne.addActionListener(e->show("one"));stepTwo.addActionListener(e->show("two"));
        add(stepper,BorderLayout.NORTH);
        stepBody.setOpaque(false);stepBody.add(stepOnePage(),"one");
        results.setBorder(new EmptyBorder(0,0,16,14));stepBody.add(Ui.scroll(results),"two");add(stepBody,BorderLayout.CENTER);
        ask.addActionListener(e->ask());
        show("one");renderReports();
    }
    private static JToggleButton stepButton(String text){JToggleButton b=new JToggleButton(text);b.setFocusPainted(false);b.setContentAreaFilled(false);b.setOpaque(true);b.setBorder(new EmptyBorder(7,14,7,14));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;}
    void show(String step){
        steps.show(stepBody,step);boolean one=step.equals("one");stepOne.setSelected(one);stepTwo.setSelected(!one);
        for(JToggleButton b:List.of(stepOne,stepTwo)){boolean on=b.isSelected();b.setBackground(on?Ui.GREEN:Ui.BG);b.setForeground(on?Color.WHITE:Ui.INK);}
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
        else if(llm.hasPatterns()){int unread=llm.pending(host.engine.requests).size();overview.setText(result.groups().size()+" patterns from "+result.requests()+" requests in "+result.sessions()+" chats"+(unread>0?"  ·  "+unread+" new to read":""));analyze.setText(unread>0?"Read "+unread+" new":"Regroup");}
        else{overview.setText("Quick keyword map of "+result.requests()+" requests. AI finds clearer patterns.");analyze.setText("Analyze with AI");}
        bars.repaint();map.layoutBubbles();renderDetail();
    }
    List<Patterns.Group> visible(){List<Patterns.Group> out=new ArrayList<>();if(result!=null)for(Patterns.Group g:result.groups())if(modeFilter==null||g.dominant()==modeFilter)out.add(g);return out;}
    void toggle(Patterns.Group g){if(!selected.remove(g))selected.add(g);map.repaint();renderDetail();}
    void renderDetail(){
        detail.removeAll();
        if(selected.isEmpty()){
            detail.put(Ui.section("Largest patterns"));
            int shown=0;for(Patterns.Group g:visible()){if(shown++==8)break;JButton row=new JButton(g.label+"  ("+g.sessions.size()+")");row.setHorizontalAlignment(SwingConstants.LEFT);row.setFocusPainted(false);row.setContentAreaFilled(false);row.setBorder(new EmptyBorder(5,0,5,0));row.setForeground(Ui.INK);row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));row.addActionListener(e->toggle(g));detail.put(row,shown==1?6:0);}
        }else for(Patterns.Group g:selected){
            detail.put(Ui.label(g.label,Ui.sans(Font.BOLD,15),COLORS.get(g.dominant())),detail.getComponentCount()==0?0:18);
            detail.put(Ui.muted(g.sessions.size()+" chats  ·  "+g.items.size()+" requests  ·  mostly "+g.dominant().label.toLowerCase(Locale.ROOT)),2);
            if(!g.description.isBlank())detail.put(Ui.prose(g.description,Ui.INK),6);
            int n=0;for(Patterns.Item item:g.examples){if(n++==5)break;String t=item.request().text().strip().replaceAll("\\s+"," ");detail.put(Ui.prose("“"+(t.length()>150?t.substring(0,150)+"…":t)+"”",Ui.MUTED),8);}
        }
        detail.end();detail.revalidate();detail.repaint();SwingUtilities.invokeLater(()->side.getVerticalScrollBar().setValue(0));
        List<Patterns.Group> s=scoped();int chats=0;for(Patterns.Group g:s)chats+=g.sessions.size();
        scope.setText(selected.isEmpty()?(modeFilter==null?"All "+s.size()+" patterns":s.size()+" "+modeFilter.label.toLowerCase(Locale.ROOT)+" patterns"):selected.size()+(selected.size()==1?" pattern":" patterns")+" selected");
        ask.setEnabled(!host.busy&&!s.isEmpty());
    }
    List<Patterns.Group> scoped(){return selected.isEmpty()?visible():new ArrayList<>(selected);}
    void setBusy(boolean active){analyze.setEnabled(!active);focus.setEnabled(!active);ask.setEnabled(!active&&result!=null&&!scoped().isEmpty());if(!active)refresh();}
    void ask(){
        Models.Model connection;
        try{connection=host.workspace.connection();}catch(Exception e){host.error(e);return;}
        if(connection==null){JOptionPane.showMessageDialog(host.frame,"This step uses your model. Add one in Settings and allow it to receive excerpts.","Connect a model",JOptionPane.INFORMATION_MESSAGE);host.select("Settings");return;}
        List<Patterns.Group> groups=scoped();String label=scope.getText();Map<String,Patterns.Item> refs=new HashMap<>();String input=Insights.input(groups,refs,focus.getText().strip());
        host.workspace.run("Reading "+refs.size()+" excerpts for what they reveal…",true,()->Insights.parse(connection.complete(Insights.INSTRUCTIONS,input),refs),out->{
            @SuppressWarnings("unchecked") List<Insights.Finding> findings=(List<Insights.Finding>)out;
            reports.add(0,new Insights.Report(System.currentTimeMillis(),label,findings));
            try{Insights.save(host.data.getParent(),reports);}catch(Exception e){host.error(e);}
            host.status.setText(findings.isEmpty()?"No strong problem in that selection.":findings.size()+(findings.size()==1?" problem found.":" problems found."));
            renderReports();show("two");
        });
    }
    /** Reads requests in batches and regroups, with the map growing as each batch returns. */
    void runAi(){
        Models.Model connection;
        try{connection=host.workspace.connection();}catch(Exception e){host.error(e);return;}
        if(connection==null){JOptionPane.showMessageDialog(host.frame,"This uses your model. Add one in Settings and allow it to receive excerpts.","Connect a model",JOptionPane.INFORMATION_MESSAGE);host.select("Settings");return;}
        Map<String,ChatEngine.Request> copy=new LinkedHashMap<>(host.engine.requests);
        List<Patterns.Item> todo=llm.pending(copy);
        if(todo.isEmpty()&&llm.descriptions.isEmpty()){host.status.setText("Import your chats first.");return;}
        // Reading sends far more than other steps, so the amount is stated every time.
        if(!todo.isEmpty()&&JOptionPane.showConfirmDialog(host.frame,"Send "+todo.size()+" requests to "+host.workspace.provider.getSelectedItem()+"?\nEach is cut to "+LlmPatterns.EXCERPT+" characters, about "+Math.max(1,LlmPatterns.characters(todo)/1000)+"k characters in total. Obvious credentials are removed, but names and other details may remain.","Analyze with AI",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        String wanted=focus.getText().strip();
        running=true;selected.clear();modeFilter=null;liveBar.setVisible(true);reading.setValue(0);reading.setIndeterminate(todo.isEmpty());ticker.setText(" ");show("one");stepOne.setSelected(true);
        overview.setText(todo.isEmpty()?"Grouping…":"Reading 0 of "+todo.size()+"…");
        host.workspace.run("Analyzing your requests…",true,()->{
            llm.run(connection,copy,wanted,(stage,done,total,latest)->SwingUtilities.invokeLater(()->{
                reading.setIndeterminate(false);reading.setMaximum(Math.max(1,total));reading.setValue(done);
                overview.setText(stage.equals("Grouping")?"Grouping into patterns…":"Reading "+done+" of "+total+" requests…");if(stage.equals("Grouping")&&done==0)reading.setIndeterminate(true);
                if(!latest.isEmpty())ticker.setText((stage.equals("Grouping")?"Found: ":"“")+latest+(stage.equals("Grouping")?"":"”"));
                if(stage.equals("Reading")){result=llm.live(copy);bars.repaint();map.layoutBubbles();renderDetail();}
            }));
            return null;
        },out->finishRun(copy,null));
        watchFinish();
    }
    /** A stopped or failed run must also leave the running state. */
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
    void forget(){try{llm.clear();}catch(Exception e){host.error(e);}reports.clear();analyzedSignature=-1;renderReports();}
    void renderReports(){
        results.removeAll();
        if(reports.isEmpty())results.put(Ui.label("Pick patterns in step 1, then choose Find the problems.",Ui.serif(20),Ui.INK),30);
        boolean first=true;
        for(Insights.Report r:reports){
            results.put(Ui.muted(new Date(r.date()).toString().substring(4,16)+"  ·  "+r.scope()),first?0:26);first=false;
            if(r.findings().isEmpty())results.put(Ui.prose("Nothing strong enough to suggest.",Ui.MUTED),6);
            for(Insights.Finding f:r.findings())results.put(finding(f),10);
        }
        results.end();results.revalidate();results.repaint();
    }
    private JComponent finding(Insights.Finding f){
        Ui.Stack card=Ui.card(false);
        card.put(Ui.muted(f.confidence().toUpperCase(Locale.ROOT)+" CONFIDENCE"));
        JTextArea problem=Ui.prose(f.problem(),Ui.INK);problem.setFont(Ui.serif(21));card.put(problem,4);
        if(!f.rootCause().isBlank())card.put(Ui.prose(f.rootCause(),Ui.MUTED),6);
        for(Insights.Suggestion s:f.suggestions())card.put(suggestion(f,s),14);
        JButton quotes=Ui.button("Show the evidence ("+f.evidence().size()+")");Ui.Stack shown=new Ui.Stack();shown.setVisible(false);
        for(Insights.Quote q:f.evidence()){String t=q.text().replaceAll("\\s+"," ");shown.put(Ui.prose("“"+(t.length()>260?t.substring(0,260)+"…":t)+"”",Ui.MUTED),8);}
        quotes.addActionListener(e->{shown.setVisible(!shown.isVisible());card.revalidate();});
        card.put(Ui.row(quotes),14);card.put(shown);return card;
    }
    private JComponent suggestion(Insights.Finding f,Insights.Suggestion s){
        Ui.Stack box=new Ui.Stack();box.setOpaque(true);box.setBackground(Ui.SOFT);box.setBorder(new EmptyBorder(12,14,12,14));
        box.put(Ui.label(s.type().toUpperCase(Locale.ROOT),Ui.sans(Font.BOLD,11),Ui.GREEN));box.put(Ui.label(s.title(),Ui.sans(Font.BOLD,15),Ui.INK),2);
        if(!s.why().isBlank())box.put(Ui.prose(s.why(),Ui.MUTED),4);
        box.put(Ui.section("Start here"),8);JTextArea first=Ui.prose(s.firstStep(),Ui.INK);first.setFocusable(true);box.put(first,2);
        int n=2;for(String step:s.steps())box.put(Ui.prose((n++)+".  "+step,Ui.INK),4);
        if(!s.payoff().isBlank())box.put(Ui.prose("→ "+s.payoff(),Ui.GREEN),8);
        JButton keep=Ui.button("Save to Home"),copy=Ui.button("Copy");
        copy.addActionListener(e->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(s.firstStep()),null);host.status.setText("Copied.");});
        String topic="insight-"+s.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-");
        if(host.workspace.store.hasTopic(topic)){keep.setText("Saved");keep.setEnabled(false);}
        keep.addActionListener(e->{try{host.workspace.store.saveSuggestion(topic,s.title(),s.why().isBlank()?f.problem():s.why(),f.problem()+"\n\n"+s.firstStep(),s.steps(),f.evidence().stream().map(Insights.Quote::id).toList());keep.setText("Saved");keep.setEnabled(false);host.refreshLibrary();host.status.setText(s.title()+" is on Home.");}catch(Exception error){host.error(error);}});
        box.put(Ui.row(copy,keep),10);return box;
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
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int x=0,w=getWidth();g.setFont(Ui.sans(Font.PLAIN,12));int lx=0;
            for(Patterns.Mode m:Patterns.Mode.values()){
                int n=result.modes().getOrDefault(m,0);if(n==0)continue;int bw=Math.max(3,(int)Math.round(w*(double)n/result.requests()));
                boolean dim=modeFilter!=null&&modeFilter!=m;Color c=COLORS.get(m);g.setColor(dim?new Color(c.getRed(),c.getGreen(),c.getBlue(),70):c);g.fillRect(x,0,bw-1,16);hits.add(new Object[]{new Rectangle(x,0,bw,16),m});x+=bw;
                String text=m.label+" "+Math.round(100.0*n/result.requests())+"%";int tw=g.getFontMetrics().stringWidth(text);
                g.fillOval(lx,28,9,9);g.setColor(dim?Ui.LINE:Ui.MUTED);g.drawString(text,lx+13,37);hits.add(new Object[]{new Rectangle(lx,22,tw+16,22),m});lx+=tw+26;
            }
            g.dispose();
        }
    }
    /** The pattern map: bubbles glide to new sizes and places, so changes are felt rather than just appearing. */
    final class Map_ extends JComponent {
        final Map<Patterns.Group,double[]> bubbles=new LinkedHashMap<>();final Map<String,double[]> shown=new HashMap<>();Patterns.Group hover;
        final javax.swing.Timer motion=new javax.swing.Timer(16,e->step());
        Map_(){
            addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){layoutBubbles();}});
            addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){Patterns.Group g=at(e.getPoint());if(g!=null&&!running)toggle(g);}});
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
            for(double scale=1;scale>.2&&!place(groups,unit*scale,biggest*scale,w,h);scale*=.9){}
            Set<String> names=new HashSet<>();for(Patterns.Group g:groups)names.add(g.label);shown.keySet().retainAll(names);
            motion.start();
        }
        private boolean place(List<Patterns.Group> groups,double unit,double biggest,int w,int h){
            bubbles.clear();List<double[]> placed=new ArrayList<>();
            for(Patterns.Group g:groups){
                double r=Math.min(biggest,Math.max(13,Math.sqrt(g.sessions.size())*unit));boolean fits=false;double x=0,y=0;
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
                if(b[2]<28)continue;
                g.setColor(Color.WHITE);g.setFont(Ui.sans(Font.BOLD,b[2]>52?13:11));FontMetrics m=g.getFontMetrics();
                List<String> lines=wrap(p.label,m,b[2]*1.6,b[2]>52?3:2);int y=(int)(b[1]-(lines.size()*m.getHeight())/2.0+m.getAscent());
                for(String t:lines){while(m.stringWidth(t)>b[2]*1.8&&t.length()>3)t=t.substring(0,t.length()-1);g.drawString(t,(int)(b[0]-m.stringWidth(t)/2.0),y);y+=m.getHeight();}
            }
            g.dispose();
        }
    }
}
