import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Home library, tool details, provider-neutral analysis, feedback, and settings. UI work stays on the EDT. */
final class Workspace extends JPanel {
    final Loop host;
    final Solutions store;
    final Ui.Stack cards=new Ui.Stack();
    final JTextField search=new JTextField(20);
    final JCheckBox archived=new JCheckBox("Show archived");
    final JButton analyze=Ui.primary("Get feedback"),pause=Ui.button("Pause");
    final JLabel summary=Ui.muted("");
    final JPanel settingsPanel=new JPanel(new BorderLayout());
    final JComboBox<Models.Provider> provider=new JComboBox<>(Models.Provider.values());
    final JTextField endpoint=new JTextField(),model=new JTextField();
    final JPasswordField key=new JPasswordField();
    final JLabel keyHint=Ui.muted("");
    final JTextArea excluded=new JTextArea(3,40);
    final JCheckBox consent=new JCheckBox("Allow selected chat excerpts to be sent to this provider"),schedule=new JCheckBox("Check for new chats every 5 minutes while RHI runs"),notify=new JCheckBox("Desktop notifications"),codex=new JCheckBox("Codex"),claude=new JCheckBox("Claude"),pi=new JCheckBox("Pi"),cross=new JCheckBox("Also look in other Windows / WSL profiles");
    final JButton test=Ui.button("Test connection"),manage=Ui.button("Added folders"),forget=Ui.button("Delete history");
    final Map<Models.Provider,String> sessionKeys=new EnumMap<>(Models.Provider.class);
    final JCheckBox remember=new JCheckBox("Keep my key safe on this computer",true);
    final JButton forgetKey=Ui.button("Forget saved key");
    private final Map<Models.Provider,String> stored=new EnumMap<>(Models.Provider.class);
    Models.Provider shownProvider;
    SwingWorker<?,?> job;
    volatile Thread workerThread;
    JDialog openTool;
    boolean thinking,flowing;
    JScrollPane homeScroll;
    JScrollPane settingsScroll;
    javax.swing.Timer timer;

    Workspace(Loop host){
        this.host=host;store=new Solutions(host.data.getParent());
        try{store.load();}catch(Exception e){host.error(e);}
        setLayout(new BorderLayout(0,14));setOpaque(false);
        JLabel searchLabel=new JLabel("Search");archived.setOpaque(false);
        Ui.Stack heading=new Ui.Stack();
                JPanel controls=new JPanel(new BorderLayout());controls.setOpaque(false);JPanel searchRow=new JPanel(new GridBagLayout());searchRow.setOpaque(false);searchRow.add(Ui.row(searchLabel,search,archived));controls.add(searchRow,BorderLayout.WEST);Ui.field(search);controls.add(analyze,BorderLayout.EAST);
        heading.put(controls);heading.put(summary,6);add(heading,BorderLayout.NORTH);
        homeScroll=Ui.scroll(cards);add(homeScroll,BorderLayout.CENTER);
        analyze.addActionListener(e->findProblems());archived.addActionListener(e->refresh());
        search.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e){refresh();}
            @Override public void removeUpdate(DocumentEvent e){refresh();}
            @Override public void changedUpdate(DocumentEvent e){refresh();}
        });
        buildSettings();
        timer=new javax.swing.Timer(5*60*1000,e->{if(!host.busy&&!paused()&&schedule.isSelected())host.scan(false);});timer.start();
    }
    void interrupt(){Thread t=workerThread;if(t!=null)t.interrupt();}
    boolean paused(){return Boolean.parseBoolean(host.settings.getProperty("paused","false"));}
    void togglePause(){
        if(!paused()&&host.busy){host.stopped.set(true);interrupt();}
        host.settings.setProperty("paused",Boolean.toString(!paused()));
        try{host.saveSettings();}catch(IOException e){host.error(e);}
        if(host.trayPause!=null)host.trayPause.setLabel(paused()?"Resume":"Pause");
        refresh();host.status.setText(paused()?"Paused.":"Resumed.");
    }
    void setBusy(boolean active){if(!active)SwingUtilities.invokeLater(()->{if(!host.busy&&(thinking||flowing)){thinking=false;flowing=false;refresh();}});analyze.setEnabled(!active);test.setEnabled(!active);forget.setEnabled(!active);manage.setEnabled(!active);}

    /** The single action. With a model it finds problems; without one it offers the built-in tools. */
    void findProblems(){
        if(host.busy)return;
        if(paused()){host.status.setText("Paused. Resume in Settings.");return;}
        Models.Model connection=connectOrAsk();
        if(connection==null){if(useLocal){useLocal=false;flowing=true;host.scan(()->analyze(true));}return;}
        flowing=true;refresh();Activity.log("Get feedback: checking for new chats");
        host.scan(()->{
            if(host.engine.requests.isEmpty()){flowing=false;refresh();host.status.setText("No chats found. Add a folder in Chats.");return;}
            if(host.patterns.caughtUp()){flowing=false;refresh();Activity.log("No chats changed: no model call needed");host.status.setText("No new chats. You are all caught up.");return;}
            flowing=true;host.patterns.flow(connection);
        });
    }
    private boolean useLocal;
    /** The model connection, asking for whatever is missing right here instead of sending anyone to Settings. */
    Models.Model connectOrAsk(){
        try{Models.Model ready=connection();if(ready!=null)return ready;}catch(Exception ignored){}
        // The saved key may still be loading right after launch.
        Models.Provider current=(Models.Provider)provider.getSelectedItem();
        if(secret(current).isBlank()){String saved=KeyStore.load(current.name());if(!saved.isEmpty()){stored.put(current,saved);sessionKeys.put(current,saved);if(shownProvider==current)key.setText(saved);try{Models.Model ready=connection();if(ready!=null)return ready;}catch(Exception ignored){}}}
        JComboBox<Models.Provider> pick=new JComboBox<>(Models.Provider.values());pick.setSelectedItem(provider.getSelectedItem());pick.setBackground(Color.WHITE);
        JTextField id=Ui.field(new JTextField(model.getText(),28));JPasswordField secret=Ui.field(new JPasswordField(new String(key.getPassword()),28));
        JCheckBox allow=new JCheckBox("Send my chat excerpts to this provider",consent.isSelected());allow.setOpaque(false);
        pick.addActionListener(e->{Models.Provider p=(Models.Provider)pick.getSelectedItem();id.setText(host.settings.getProperty("model."+p.name(),""));secret.setText(sessionKeys.getOrDefault(p,""));});
        Object[] form={"RHI uses your own AI account to read your chats.",new JLabel("Provider"),pick,new JLabel("Model ID"),id,new JLabel("API key (kept safe by your system)"),secret,allow};
        String[] options={"Connect","Local tools only","Cancel"};
        while(true){
            int choice=JOptionPane.showOptionDialog(host.frame,form,"Connect your AI",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
            if(choice==1)useLocal=true;
            if(choice!=0)return null;
            if(!allow.isSelected()){JOptionPane.showMessageDialog(host.frame,"Tick the box to allow sending excerpts, or choose Local tools only.","Connect your AI",JOptionPane.INFORMATION_MESSAGE);continue;}
            provider.setSelectedItem(pick.getSelectedItem());model.setText(id.getText().strip());key.setText(new String(secret.getPassword()));consent.setSelected(true);
            try{persistForm();Models.Model ready=connection();refresh();return ready;}catch(Exception e){JOptionPane.showMessageDialog(host.frame,message(e),"Connect your AI",JOptionPane.ERROR_MESSAGE);}
        }
    }
    private int homeAt=-1;
    void scrollHomeToTop(){homeAt=0;restoreHome();}
    /** Items arriving must not move what someone is reading, so the position survives each rebuild. */
    private void restoreHome(){SwingUtilities.invokeLater(()->SwingUtilities.invokeLater(()->{if(homeAt>=0){homeScroll.getVerticalScrollBar().setValue(homeAt);homeAt=-1;}}));}
    // ---- Home ----
    void refresh(){
        if(homeScroll!=null){if(homeAt<0)homeAt=homeScroll.getVerticalScrollBar().getValue();restoreHome();}
        analyze.setText(host.patterns!=null&&host.patterns.caughtUp()?"Check for new chats":"Get feedback");cards.removeAll();pause.setText(paused()?"Resume":"Pause");
        summary.setText(paused()?"Paused. RHI will not import or analyze until you resume in Settings.":"");summary.setVisible(paused());
        boolean any=store.tools.stream().anyMatch(t->!t.archived)||archived.isSelected()||!search.getText().isEmpty();search.getParent().getParent().setVisible(any);
        String query=search.getText().strip().toLowerCase(Locale.ROOT);
        List<Solutions.Tool> shown=new ArrayList<>();
        for(Solutions.Tool t:store.tools)if(t.archived==archived.isSelected()&&(t.title+" "+t.description+" "+t.topic).toLowerCase(Locale.ROOT).contains(query))shown.add(t);
        Collections.reverse(shown); // Newest first.
        boolean feature=query.isEmpty()&&!archived.isSelected();
        if((thinking||flowing)&&feature&&!findingsVisible()){JProgressBar bar=new JProgressBar();bar.setIndeterminate(true);bar.setBorderPainted(false);bar.setForeground(Ui.GREEN);bar.setBackground(Ui.LINE);bar.setPreferredSize(new Dimension(100,4));cards.put(Ui.label("Reading everything you asked…",Ui.serif(20),Ui.INK),30);cards.put(Ui.muted("Your feedback appears here as it is written."),4);cards.put(bar,12);cards.put(Box.createVerticalStrut(24));}
        if(feature&&host.patterns!=null&&host.patterns.hasFeedback()){cards.put(Ui.section("Feedback"));cards.put(host.patterns.results,8);if(!shown.isEmpty())cards.put(Ui.section("Saved"),26);}
        for(int i=0;i<shown.size();i++)cards.put(card(shown.get(i),feature&&i==0&&isNew(shown.get(i))),i==0?8:12);
        boolean findings=feature&&host.patterns!=null&&host.patterns.hasFeedback();
        if(shown.isEmpty()&&!findings&&!thinking&&!flowing)cards.put(emptyState(query));
        cards.end();cards.revalidate();cards.repaint();
    }
    private boolean findingsVisible(){return host.patterns!=null&&host.patterns.hasFeedback();}
    static boolean isNew(Solutions.Tool tool){return System.currentTimeMillis()-Solutions.number(tool.versions.get(0).get("date"))<Solutions.WEEK;}
    private JComponent emptyState(String query){
        Ui.Stack empty=new Ui.Stack();
        if(!query.isEmpty())return empty.put(Ui.muted("No solutions match “"+query+"”."),8);
        if(archived.isSelected())return empty.put(Ui.muted("Archived solutions will appear here."),8);
        empty.put(Ui.label("Keep working. RHI will look for ways to make it easier.",Ui.serif(24),Ui.INK),60);
        JButton go=Ui.primary("Get feedback");go.addActionListener(e->findProblems());empty.put(Ui.row(go),18);
        return empty;
    }
    private JComponent card(Solutions.Tool tool,boolean featured){
        Ui.Stack card=Ui.card(featured);
        card.put(Ui.muted((featured?"NEW  ·  ":"")+label(tool.kind)+(tool.versions.size()>1?"  ·  version "+tool.versions.size():"")));
        card.put(Ui.label(tool.title,Ui.serif(featured?26:21),Ui.INK),4);card.put(Ui.prose(tool.description,Ui.MUTED),4);
        JButton open=Ui.primary("Open"),details=Ui.button("Details");open.addActionListener(e->open(tool));details.addActionListener(e->details(tool));
        card.put(Ui.row(open,details),12);return card;
    }
    static String label(String kind){return switch(kind){case "csv"->"File cleaner";case "transform"->"Text tool";case "lesson"->"Lesson";default->"Reference";};}
    void open(Solutions.Tool tool){
        try{
            if(tool.kind.equals("csv")){csvCleaner();return;}
            Path file=Artifacts.write(store,tool);
            if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IOException("Open this file in your browser: "+file);
            Desktop.getDesktop().browse(file.toUri());
        }catch(Exception e){host.error(e);}
    }

    // ---- CSV cleaner ----
    private Path csvInput;
    private String csvOutput;
    void csvCleaner(){
        JDialog dialog=new JDialog(host.frame,"CSV cleaner",false);
        JLabel state=Ui.muted(" ");
        JTextArea preview=new JTextArea();preview.setEditable(false);preview.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));preview.setText("Drop a CSV file here, or choose one.");preview.setMargin(new Insets(12,12,12,12));
        JButton pick=Ui.button("Choose file"),saveAs=Ui.primary("Save copy");saveAs.setEnabled(false);
        Runnable[] choose=new Runnable[1];
        java.util.function.Consumer<Path> load=path->{
            try{
                String source=Csv.read(path);csvOutput=Csv.clean(source);csvInput=path;
                if(csvOutput.isEmpty())throw new IOException("This file has no rows to keep.");
                long before=source.lines().count(),after=csvOutput.lines().count();
                preview.setText(csvOutput.substring(0,Math.min(20000,csvOutput.length())).replace("\r\n","\n"));preview.setCaretPosition(0);
                state.setText(path.getFileName()+": "+before+" lines became "+after+".");saveAs.setEnabled(true);
            }catch(Exception e){saveAs.setEnabled(false);csvOutput=null;state.setText("Could not clean that file.");host.error(e);}
        };
        choose[0]=()->{JFileChooser picker=new JFileChooser();if(picker.showOpenDialog(dialog)==JFileChooser.APPROVE_OPTION)load.accept(picker.getSelectedFile().toPath());};
        pick.addActionListener(e->choose[0].run());
        saveAs.addActionListener(e->{
            JFileChooser picker=new JFileChooser();picker.setSelectedFile(new File(csvInput.toString().replaceFirst("(?i)\\.(csv|txt|tsv)$","")+"-clean.csv"));
            if(picker.showSaveDialog(dialog)!=JFileChooser.APPROVE_OPTION)return;
            Path output=picker.getSelectedFile().toPath();
            try{
                if(output.toAbsolutePath().equals(csvInput.toAbsolutePath()))throw new IOException("Choose a different name, so your original stays untouched.");
                if(Files.exists(output)&&JOptionPane.showConfirmDialog(dialog,"Replace "+output+"?","Overwrite",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                Files.writeString(output,csvOutput);state.setText("Saved "+output);host.status.setText("Saved "+output);
            }catch(Exception error){host.error(error);}
        });
        TransferHandler drop=new TransferHandler(){
            @Override public boolean canImport(TransferSupport s){return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            @Override public boolean importData(TransferSupport s){try{List<?> files=(List<?>)s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);if(files.isEmpty())return false;load.accept(((File)files.get(0)).toPath());return true;}catch(Exception e){host.error(e);return false;}}
        };
        preview.setTransferHandler(drop);
        JPanel root=new JPanel(new BorderLayout(0,10));root.setBorder(new EmptyBorder(16,18,16,18));root.setTransferHandler(drop);
        JScrollPane scroll=new JScrollPane(preview);scroll.setBorder(new LineBorder(Ui.LINE));root.add(scroll,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout());bottom.add(state,BorderLayout.CENTER);bottom.add(Ui.row(pick,saveAs),BorderLayout.EAST);root.add(bottom,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setSize(760,520);dialog.setLocationRelativeTo(host.frame);Ui.closeOnEscape(dialog);dialog.setVisible(true);
    }

    // ---- Tool details ----
    void details(Solutions.Tool tool){
        if(openTool!=null)openTool.dispose();
        JDialog dialog=new JDialog(host.frame,tool.title,false);openTool=dialog;
        Ui.Stack body=new Ui.Stack();body.setBorder(new EmptyBorder(20,24,20,24));
        body.put(Ui.muted(label(tool.kind)+"  ·  version "+tool.versions.size()+(tool.archived?"  ·  archived":"")));
        body.put(Ui.label(tool.title,Ui.serif(26),Ui.INK),4);body.put(Ui.prose(tool.description,Ui.MUTED),6);
        body.put(Ui.section("What it does"),18);body.put(Ui.prose(Json.text(tool.current().get("content")),Ui.INK),6);
        int number=1;for(Object step:Solutions.list(tool.current().get("steps")))body.put(Ui.prose((number++)+".  "+Json.text(step),Ui.INK),4);
        Set<String> sessions=new HashSet<>();List<ChatEngine.Request> found=new ArrayList<>();
        for(String id:tool.evidence){ChatEngine.Request r=host.engine.requests.get(id);if(r!=null){found.add(r);sessions.add(r.session());}}
        body.put(Ui.section("Why this exists"),18);
        body.put(Ui.prose(found.isEmpty()?"Its source requests are no longer in your imported history.":"You asked for something similar "+found.size()+(found.size()==1?" time":" times")+" across "+sessions.size()+" separate sessions.",Ui.INK),6);
        for(ChatEngine.Request r:found.subList(Math.max(0,found.size()-3),found.size())){String t=r.text().strip().replaceAll("\\s+"," ");body.put(Ui.prose("“"+(t.length()>140?t.substring(0,140)+"…":t)+"”",Ui.MUTED),6);}
        if(!tool.feedback.isEmpty()){body.put(Ui.section("Your feedback"),18);for(var f:tool.feedback)body.put(Ui.prose(new Date(Solutions.number(f.get("date"))).toString().substring(4,10)+":  "+Json.text(f.get("text")),Ui.MUTED),4);}
        body.end();
        JLabel state=Ui.muted(" ");
        boolean useful=tool.feedback.stream().anyMatch(f->"useful".equals(f.get("type"))),delegate=tool.feedback.stream().anyMatch(f->"delegate".equals(f.get("type")));
        JButton open=Ui.primary("Open"),improve=Ui.button("Improve"),markUseful=Ui.button(useful?"Marked useful":"Useful"),markDelegate=Ui.button(delegate?"Noted":"Prefer delegating"),archive=Ui.button(tool.archived?"Restore":"Archive"),versions=Ui.button("Versions");
        markUseful.setEnabled(!useful);markDelegate.setEnabled(!delegate);versions.setEnabled(tool.versions.size()>1);
        open.addActionListener(e->open(tool));
        markUseful.addActionListener(e->guarded(state,()->{store.feedback(tool,"useful","This helped.");details(tool);}));
        markDelegate.addActionListener(e->guarded(state,()->{store.feedback(tool,"delegate","Prefer having this done for me instead of learning it.");details(tool);}));
        archive.addActionListener(e->guarded(state,()->{
            boolean previous=tool.archived;tool.archived=!previous;
            try{store.save();}catch(IOException error){tool.archived=previous;throw error;}
            dialog.dispose();refresh();host.status.setText(tool.title+(tool.archived?" archived. RHI will not suggest this topic again.":" restored."));
        }));
        improve.addActionListener(e->improve(tool,dialog));
        versions.addActionListener(e->guarded(state,()->{
            String[] options=new String[tool.versions.size()];
            for(int i=0;i<options.length;i++){var v=tool.versions.get(i);String change=Json.text(v.get("change"));options[i]="Version "+(i+1)+"  ·  "+new Date(Solutions.number(v.get("date")))+(change.isEmpty()?"":"  ·  "+change);}
            Object selected=JOptionPane.showInputDialog(dialog,"Restore a previous version. It is saved as a new version, so nothing is lost.","Version history",JOptionPane.PLAIN_MESSAGE,null,options,options[options.length-1]);
            if(selected!=null){store.restore(tool,Arrays.asList(options).indexOf(selected.toString()));refresh();details(tool);}
        }));
        JPanel bottom=new JPanel(new BorderLayout(0,8));bottom.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,Ui.LINE),new EmptyBorder(12,24,14,24)));
        versions.setVisible(tool.versions.size()>1);bottom.add(state,BorderLayout.NORTH);bottom.add(Ui.row(open,improve,markUseful,markDelegate,archive,versions),BorderLayout.CENTER);
        JPanel root=new JPanel(new BorderLayout());JScrollPane toolScroll=Ui.scroll(body);root.add(toolScroll,BorderLayout.CENTER);root.add(bottom,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.setSize(780,640);dialog.setLocationRelativeTo(host.frame);Ui.closeOnEscape(dialog);dialog.setVisible(true);SwingUtilities.invokeLater(()->toolScroll.getVerticalScrollBar().setValue(0));
    }
    private interface Action{void run()throws Exception;}
    private void guarded(JLabel state,Action action){
        if(host.busy){state.setText("Wait for the current task to finish.");return;}
        try{action.run();}catch(Exception e){host.error(e);}
    }
    private void improve(Solutions.Tool tool,JDialog parent){
        if(host.busy)return;
        Models.Model connection;
        try{connection=connection();}catch(Exception e){host.error(e);return;}
        if(connection==null){JOptionPane.showMessageDialog(parent,"Improving a tool uses your model. Connect one in Settings and allow it to receive excerpts, then try again.","Connect a model",JOptionPane.INFORMATION_MESSAGE);parent.dispose();host.select("Settings");return;}
        JTextArea request=new JTextArea(4,40);request.setLineWrap(true);request.setWrapStyleWord(true);
        if(JOptionPane.showConfirmDialog(parent,new Object[]{"What should change? This and the current tool are sent to "+provider.getSelectedItem()+".",new JScrollPane(request)},"Improve "+tool.title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION||request.getText().isBlank())return;
        parent.dispose();String feedback=request.getText().strip();
        run("Improving "+tool.title+"…",true,()->{store.revise(tool,connection,feedback);return tool;},result->{refresh();host.status.setText(tool.title+" is now version "+tool.versions.size()+". Earlier versions are kept.");details(tool);});
    }

    // ---- Analysis ----
    Models.Model connection(){
        if(!consent.isSelected())return null;
        Models.Provider p=(Models.Provider)provider.getSelectedItem();
        return Models.connect(new Models.Config(p,endpoint.getText().strip(),model.getText().strip(),secret(p)));
    }
    private String secret(Models.Provider p){String s=new String(key.getPassword());if(s.isBlank())s=sessionKeys.getOrDefault(p,"");if(s.isBlank())s=System.getenv().getOrDefault(p.keyEnv,"");return s;}
    void analyze(boolean manual){
        if(host.busy)return;
        if(paused()){host.status.setText("Paused. Resume in Settings.");return;}
        if(manual&&host.engine.requests.isEmpty()){host.status.setText("Nothing to review yet. Import your chats first.");return;}
        Models.Model connection;
        // Scheduled checks stay quiet: a dialog must never interrupt someone every five minutes.
        try{connection=connection();}catch(Exception e){if(manual)host.error(e);else host.status.setText("Scheduled analysis skipped: "+e.getMessage());return;}
        run("Looking for a useful shortcut…",manual,()->store.analyze(host.engine,connection,manual),result->{
            Solutions.Tool tool=(Solutions.Tool)result;
            if(tool!=null){host.status.setText(tool.title+" is ready.");notification(tool);}
            else if(manual)host.status.setText(connection==null?"No clear repeated task yet. Connect a model in Settings for a deeper look.":"No strong new opportunity in this pass.");
            refresh();
        });
    }
    interface Task{Object execute()throws Exception;}
    void run(String label,boolean manual,Task task,java.util.function.Consumer<Object> success){
        if(host.busy)return;
        host.busy=true;host.stopped.set(false);host.setTaskControls(true);host.status.setText(label);
        job=new SwingWorker<Object,Void>(){
            @Override protected Object doInBackground()throws Exception{
                workerThread=Thread.currentThread();
                try{if(host.stopped.get())throw new InterruptedException("Stopped");return task.execute();}finally{workerThread=null;Thread.interrupted();}
            }
            @Override protected void done(){
                Object result=null;Exception failure=null;
                try{result=get();}catch(Exception e){failure=e;}
                host.busy=false;host.setTaskControls(false);job=null;
                if(host.quitRequested){host.quit();return;}
                if(failure==null)success.accept(result);
                else if(host.stopped.get())host.status.setText("Stopped.");
                else{host.status.setText("Could not finish: "+message(failure));if(manual)host.error(failure);}
            }
        };
        job.execute();
    }
    static String message(Exception e){Throwable c=e;while(c.getCause()!=null)c=c.getCause();return c.getMessage()==null?c.toString():c.getMessage();}
    void notification(Solutions.Tool tool){
        if(!notify.isSelected()||host.tray==null)return;
        Set<String> sessions=new HashSet<>();for(String id:tool.evidence){var r=host.engine.requests.get(id);if(r!=null)sessions.add(r.session());}
        host.tray.displayMessage(tool.title+" is ready","This came up in "+sessions.size()+" separate sessions, so RHI made something for it.",TrayIcon.MessageType.INFO);
    }
    boolean enabled(String tool){return switch(tool){case "Codex"->codex.isSelected();case "Claude"->claude.isSelected();case "Pi"->pi.isSelected();default->true;};}

    // ---- Settings ----
    private boolean flag(String name,String fallback){return Boolean.parseBoolean(host.settings.getProperty(name,fallback));}
    void buildSettings(){
        Models.Provider selected;try{selected=Models.Provider.valueOf(host.settings.getProperty("provider","OPENAI"));}catch(Exception e){selected=Models.Provider.OPENAI;}
        provider.setSelectedItem(selected);loadProvider(selected);
        consent.setSelected(flag("consent","false"));schedule.setSelected(flag("schedule","false"));notify.setSelected(flag("notifications","true"));codex.setSelected(flag("source.Codex","true"));claude.setSelected(flag("source.Claude","true"));pi.setSelected(flag("source.Pi","true"));cross.setSelected(flag("cross","true"));
        excluded.setText(host.settings.getProperty("excluded",""));
        Ui.Stack form=new Ui.Stack();form.setBorder(new EmptyBorder(0,0,16,14));
        form.put(Ui.section("Model"));
        field(form,"Provider",provider);field(form,"Model ID",model);field(form,"API key",key);form.put(keyHint,4);form.put(check(remember),4);field(form,"Endpoint",endpoint);
        form.put(check(consent),10);form.put(Ui.row(test,forgetKey),10);
        form.put(Ui.section("Sources"),26);form.put(Ui.row(check(codex),check(claude),check(pi)),6);form.put(check(cross),4);
        JLabel excludeLabel=new JLabel("Skip these project folders, one per line");excludeLabel.setBorder(new EmptyBorder(12,0,5,0));form.put(excludeLabel);
        excluded.setMargin(new Insets(6,8,6,8));JScrollPane excludedScroll=new JScrollPane(excluded);excludedScroll.setBorder(new LineBorder(Ui.LINE));form.put(excludedScroll);
        form.put(Ui.row(manage),10);
        form.put(Ui.section("Background"),26);form.put(check(schedule),6);form.put(check(notify),2);form.put(Ui.row(pause),8);
        form.put(Ui.section("Your data"),26);form.put(Ui.row(forget),8);form.end();
        pause.addActionListener(e->togglePause());
        remember.setSelected(flag("rememberKey","true"));remember.addActionListener(e->autosave());
        forgetKey.addActionListener(e->{Models.Provider p=(Models.Provider)provider.getSelectedItem();sessionKeys.remove(p);stored.remove(p);key.setText("");new Thread(()->KeyStore.delete(p.name())).start();Activity.log("Saved key removed for "+p);host.status.setText("Saved key removed.");updateKeyHint();});
        Models.Provider startup=shownProvider;
        new Thread(()->{String saved=KeyStore.load(startup.name());if(!saved.isEmpty())SwingUtilities.invokeLater(()->{stored.put(startup,saved);if(sessionKeys.getOrDefault(startup,"").isBlank())sessionKeys.put(startup,saved);if(shownProvider==startup&&key.getPassword().length==0)key.setText(saved);updateKeyHint();Activity.log("Loaded your saved key for "+startup);});}).start();
        for(JCheckBox box:List.of(consent,schedule,notify,codex,claude,pi,cross))box.addActionListener(e->autosave());
        java.awt.event.FocusAdapter onLeave=new java.awt.event.FocusAdapter(){@Override public void focusLost(java.awt.event.FocusEvent e){autosave();}};
        for(JComponent field:List.of(endpoint,model,key,excluded))field.addFocusListener(onLeave);
        test.addActionListener(e->{
            try{
                persistForm();Models.Provider p=(Models.Provider)provider.getSelectedItem();
                Models.Model client=Models.connect(new Models.Config(p,endpoint.getText().strip(),model.getText().strip(),secret(p)));
                run("Testing "+p+"…",true,()->client.complete("Reply with OK only.","Connection test. No chat history is included."),result->host.status.setText("Connection works: "+p+" answered."));
            }catch(Exception error){host.error(error);}
        });
        manage.addActionListener(e->manageFolders());forget.addActionListener(e->host.deleteHistory());
        provider.addActionListener(e->{
            // Keep a typed key when switching, so comparing providers does not lose it.
            if(shownProvider!=null)sessionKeys.put(shownProvider,new String(key.getPassword()));
            loadProvider((Models.Provider)provider.getSelectedItem());autosave();
        });
        settingsPanel.setOpaque(false);settingsScroll=Ui.scroll(form);settingsPanel.add(settingsScroll,BorderLayout.CENTER);
    }
    /** Settings save themselves, so nothing is lost by leaving the screen or quitting. */
    void autosave(){
        try{persistForm();refresh();}
        catch(Exception e){host.status.setText(message(e));}
    }
    private static JCheckBox check(JCheckBox box){box.setOpaque(false);box.setBorder(new EmptyBorder(2,0,2,10));return box;}
    private static void field(Ui.Stack form,String label,JComponent input){JLabel l=new JLabel(label);l.setBorder(new EmptyBorder(12,0,5,0));form.put(l);if(input instanceof javax.swing.text.JTextComponent t)Ui.field(t);else input.setBackground(Color.WHITE);form.put(input);}
    void loadProvider(Models.Provider p){shownProvider=p;endpoint.setText(host.settings.getProperty("endpoint."+p.name(),p.endpoint));model.setText(host.settings.getProperty("model."+p.name(),""));key.setText(sessionKeys.getOrDefault(p,""));updateKeyHint();}
    private void updateKeyHint(){Models.Provider p=shownProvider;keyHint.setText(System.getenv(p.keyEnv)!=null?"Using "+p.keyEnv+" unless you paste a key.":"Encrypted by "+KeyStore.name()+" if kept. Never written to RHI's settings.");}
    void persistForm()throws IOException{
        excludedPaths();host.settings.setProperty("excluded",excluded.getText());
        Models.Provider p=(Models.Provider)provider.getSelectedItem();String typed=new String(key.getPassword());sessionKeys.put(p,typed);
        host.settings.setProperty("rememberKey",Boolean.toString(remember.isSelected()));
        if(remember.isSelected()&&!typed.isBlank()&&!typed.equals(stored.get(p))){stored.put(p,typed);new Thread(()->Activity.log(KeyStore.save(p.name(),typed)?"Key encrypted by "+KeyStore.name():"Could not reach "+KeyStore.name()+": the key stays in memory only")).start();}
        host.settings.setProperty("provider",p.name());host.settings.setProperty("endpoint."+p.name(),endpoint.getText().strip());host.settings.setProperty("model."+p.name(),model.getText().strip());
        host.settings.setProperty("consent",Boolean.toString(consent.isSelected()));host.settings.setProperty("schedule",Boolean.toString(schedule.isSelected()));host.settings.setProperty("notifications",Boolean.toString(notify.isSelected()));host.settings.setProperty("cross",Boolean.toString(cross.isSelected()));
        for(String tool:List.of("Codex","Claude","Pi"))host.settings.setProperty("source."+tool,Boolean.toString(enabled(tool)));
        host.saveSettings();
    }
    List<Path> excludedPaths(){
        List<Path> out=new ArrayList<>();
        for(String line:excluded.getText().split("\\R")){
            String path=line.strip();if(path.isEmpty())continue;
            // Linux-style project paths are valid on Windows too: WSL chats record them that way.
            if(!path.startsWith("/")&&!path.startsWith("\\")&&!Path.of(path).isAbsolute())throw new IllegalArgumentException("Excluded folders need a full path: "+path);
            out.add(Path.of(path));
        }
        return out;
    }
    void manageFolders(){
        if(host.extra.isEmpty()){JOptionPane.showMessageDialog(host.frame,"No folders added yet. Add them from Chats.","Added folders",JOptionPane.INFORMATION_MESSAGE);return;}
        DefaultListModel<String> items=new DefaultListModel<>();for(var root:host.extra)items.addElement(root.tool()+"  ·  "+root.path());
        JList<String> list=new JList<>(items);JScrollPane scroll=new JScrollPane(list);scroll.setPreferredSize(new Dimension(650,260));
        if(JOptionPane.showConfirmDialog(host.frame,scroll,"Select folders to remove",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        int[] indexes=list.getSelectedIndices();for(int i=indexes.length-1;i>=0;i--)host.extra.remove(indexes[i]);
        try{host.saveRoots();host.status.setText(indexes.length+" folders removed.");}catch(Exception e){host.error(e);}
    }
}
