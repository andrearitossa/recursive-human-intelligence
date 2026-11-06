import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Application window, imports, tray, and shared task state. Feedback and Settings live in Workspace. */
public final class Loop {
    static Loop instance;
    static java.nio.channels.FileChannel instanceChannel;
    static java.nio.channels.FileLock instanceLock;

    final JFrame frame=new JFrame("RHI · Recursive Human Intelligence");
    final ChatEngine engine=new ChatEngine();
    final Path data=Path.of(System.getProperty("user.home"),".loop","history.tsv");
    final Path settingsFile=data.resolveSibling("settings.properties");
    final Properties settings=new Properties();
    final List<ChatEngine.Root> extra=new ArrayList<>();
    final JLabel status=Ui.muted(" ");
    final JLabel stats=Ui.muted("");
    final DefaultTableModel rows=new DefaultTableModel(new String[]{"Chat","Source","Requests","Last interacted"},0){
        @Override public boolean isCellEditable(int r,int c){return false;}
        @Override public Class<?> getColumnClass(int column){return column==2?Integer.class:column==3?Long.class:String.class;}
    };
    final JProgressBar progress=new JProgressBar();
    final JButton locations=Ui.button("Chat locations"),removeChats=Ui.button("Remove"),undoRemove=Ui.button("Undo"),cancel=Ui.button("Stop");
    final AtomicBoolean stopped=new AtomicBoolean();
    final CardLayout pageLayout=new CardLayout();
    final JPanel pages=new JPanel(pageLayout);
    final Map<String,JToggleButton> nav=new LinkedHashMap<>();
    final JTable table=new JTable(rows);
    final JTextField chatSearch=new JTextField(18);
    final TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(rows);
    final List<String> rowSessions=new ArrayList<>();
    Map<String,ChatEngine.Request> lastRemoved=Map.of();
    final Path removedFile=data.resolveSibling("removed-chats.txt");
    boolean busy=false,quitRequested=false;
    String page="Dashboard";
    Workspace workspace;
    PatternsPage patterns;
    AgentsPage dashboard;
    TrayIcon tray;
    MenuItem trayPause;

    Loop(){
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(tray!=null){frame.setVisible(false);if(!"true".equals(settings.getProperty("tray.told"))){settings.setProperty("tray.told","true");try{saveSettings();}catch(IOException x){Activity.log("✕ could not save settings");}tray.displayMessage("RHI is still running","It stays in the tray for the weekly check. Quit it from the tray icon.",TrayIcon.MessageType.NONE);}}else quit();}});
        try{Path dir=data.getParent();Files.createDirectories(dir);Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException|IOException ignored){}
        loadState();
        workspace=new Workspace(this);patterns=new PatternsPage(this);
        dashboard=new AgentsPage(this::agentRoots,()->{select("Feedback");workspace.findProblems();},()->workspace.connectOrAsk());pages.setOpaque(false);pages.add(dashboard,"Dashboard");pages.add(workspace,"Feedback");pages.add(patterns,"Patterns");pages.add(chatsPage(),"Chats");pages.add(workspace.settingsPanel,"Settings");
        JPanel root=new JPanel(new BorderLayout(0,16));root.setBackground(Ui.BG);root.setBorder(new EmptyBorder(18,28,14,28));frame.setContentPane(root);
        root.add(header(),BorderLayout.NORTH);root.add(pages,BorderLayout.CENTER);JPanel south=new JPanel(new BorderLayout());south.setOpaque(false);south.add(footer(),BorderLayout.NORTH);south.add(logsPanel(),BorderLayout.CENTER);root.add(south,BorderLayout.SOUTH);
        select("Dashboard");setTaskControls(false);setupTray();refreshChats();refreshLibrary();
        frame.setSize(1080,780);frame.setMinimumSize(new Dimension(780,560));frame.setLocationRelativeTo(null);frame.setIconImages(List.of(Ui.icon(16),Ui.icon(24),Ui.icon(32),Ui.icon(48),Ui.icon(256)));frame.setVisible(true);TitleBar.style();
    }
    private void loadState(){
        try{engine.load(data);engine.loadRemoved(removedFile);engine.loadIndex(data.resolveSibling("scan-index.tsv"));}
        catch(Exception e){
            // Moved aside, so the next import cannot overwrite something recoverable.
            try{Files.move(data,data.resolveSibling("history-unreadable-"+System.currentTimeMillis()+".tsv"));}catch(IOException ignored){}
            error(e);
        }
        try{
            if(Files.exists(settingsFile))try(Reader reader=Files.newBufferedReader(settingsFile)){settings.load(reader);}
            for(String key:settings.stringPropertyNames())if(key.startsWith("root."))addSaved(settings.getProperty("tool."+key.substring(5),"Custom"),Path.of(settings.getProperty(key)));
            for(String key:settings.stringPropertyNames())if(key.startsWith("removedpath."))removedPaths.add(settings.getProperty(key));
            settings.remove("home"); // A saved home already lives on as its chat folders.
        }catch(Exception e){error(e);}
    }
    /** A saved folder that is really a home, or a folder of homes, means only their chat folders. */
    private void addSaved(String tool,Path path){
        if(!tool.equals("Custom")){extra.add(new ChatEngine.Root(tool,path));return;}
        expand(path);
    }
    /** A folder that is really a home, or a folder of homes, means only their chat folders. */
    private void expand(Path path){
        List<Path> homes=new ArrayList<>();if(isHome(path))homes.add(path);
        else try(DirectoryStream<Path> inside=Files.newDirectoryStream(path)){for(Path child:inside)if(isHome(child))homes.add(child);}catch(IOException|RuntimeException ignored){}
        if(homes.isEmpty())extra.add(new ChatEngine.Root(ChatEngine.toolFor(path),path));else for(Path h:homes)ChatEngine.addHome(extra,h);
    }
    private JComponent header(){
        JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);
        top.add(Ui.label("RHI",Ui.heading(32),Ui.ACCENT),BorderLayout.WEST);
        links.setOpaque(false);buildNav();
        top.add(links,BorderLayout.EAST);return top;
    }
    private final JPanel links=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,4));
    private void buildNav(){
        links.removeAll();nav.clear();ButtonGroup group=new ButtonGroup();
        List<String> names=List.of("Dashboard","Feedback","Patterns","Chats","Settings");
        for(String name:names){
            JToggleButton link=new JToggleButton(name);link.setFocusPainted(false);link.setContentAreaFilled(false);link.setOpaque(true);link.setBackground(Ui.BG);link.setForeground(Ui.INK);link.setBorder(new EmptyBorder(7,14,7,14));link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Ui.fit(link,Ui.sans(Font.BOLD,14),name);link.addActionListener(e->select(name));group.add(link);links.add(link);nav.put(name,link);
        }
    }
    void select(String name){
        page=name;pageLayout.show(pages,name);if(name.equals("Patterns"))patterns.refresh();if(name.equals("Chats"))reloadChats();if(name.equals("Settings"))reloadPaths();
        if(name.equals("Settings"))SwingUtilities.invokeLater(()->workspace.settingsScroll.getVerticalScrollBar().setValue(0));
        nav.forEach((label,link)->{boolean on=label.equals(name);link.setSelected(on);link.setBackground(on?Ui.SOFT:Ui.BG);link.setFont(Ui.sans(on?Font.BOLD:Font.PLAIN,14));});
    }
    final Set<String> removedPaths=Collections.synchronizedSet(new LinkedHashSet<>());
    private List<ChatEngine.Root> found;private long foundAt;
    /** The standard places: this home, other Windows / WSL homes, and Claude Desktop's metadata. Off the EDT. */
    synchronized List<ChatEngine.Root> standardPaths(){
        if(found!=null&&System.currentTimeMillis()-foundAt<60000)return found;
        Path home=Path.of(System.getProperty("user.home"));
        List<ChatEngine.Root> own=ChatEngine.defaults(home,System.getenv()),all=new ArrayList<>(own);discoverOtherHomes(all);
        Set<Path> homes=new LinkedHashSet<>();homes.add(home);
        for(ChatEngine.Root r:all)if(r.tool().equals("Claude")&&r.path().getParent()!=null&&r.path().getParent().getParent()!=null)homes.add(r.path().getParent().getParent());
        for(Path h:homes){Path desktop=h.resolve("AppData/Local/Claude-Data");if(Files.isDirectory(desktop))all.add(new ChatEngine.Root(ChatEngine.DESKTOP,desktop));}
        all=ChatEngine.unique(all);migrateSources(own,all);found=all;foundAt=System.currentTimeMillis();return all;
    }
    /** Older versions kept source checkboxes. What they turned off stays off, now as entries in the one list. */
    private void migrateSources(List<ChatEngine.Root> own,List<ChatEngine.Root> all){
        boolean changed=false;
        for(String tool:List.of("Codex","Claude","Pi")){String old=(String)settings.remove("source."+tool);if(old==null)continue;changed=true;if(old.equals("false")){for(ChatEngine.Root r:all)if(r.tool().equals(tool))removedPaths.add(r.path().toString());if(tool.equals("Claude"))for(ChatEngine.Root r:all)if(r.tool().equals(ChatEngine.DESKTOP))removedPaths.add(r.path().toString());}}
        String cross=(String)settings.remove("cross");
        if(cross!=null){changed=true;if(cross.equals("false"))for(ChatEngine.Root r:all)if(!own.contains(r)&&!r.tool().equals(ChatEngine.DESKTOP))removedPaths.add(r.path().toString());}
        if(changed)try{saveRoots();}catch(IOException e){Activity.log("Could not save chat locations");}
    }
    /** The one list of chat locations, read by Dashboard, Chats, and the import alike. Off the EDT. */
    List<ChatEngine.Root> agentRoots(){
        List<ChatEngine.Root> roots;synchronized(removedPaths){roots=ChatEngine.effective(standardPaths(),removedPaths,extra);}
        dashboard.agents.hide=hidden();return roots;
    }
    /** Removed chats and skipped projects, as one test both pages use. */
    private java.util.function.Predicate<Agents.Session> hidden(){
        Set<String> gone;List<Path> skipped;
        try{gone=new HashSet<>(engine.removed);}catch(ConcurrentModificationException e){gone=Set.of();}
        try{skipped=workspace.excludedPaths();}catch(Exception e){skipped=List.of();}
        Set<String> marks=gone;List<Path> skip=skipped;
        return s->marks.contains("file:"+s.path)||marks.contains(s.id)||marks.contains(s.path)||(s.cwd!=null&&!s.cwd.isEmpty()&&ChatEngine.isExcluded(s.cwd,null,skip))||folderSkipped(s.path,skip);
    }
    /** Claude names a project's folder after its path, so a skipped project can show only there. */
    private static boolean folderSkipped(String file,List<Path> skip){
        Path folder=Path.of(file).getParent();return folder!=null&&folder.getFileName()!=null&&ChatEngine.isExcluded(folder.toString(),folder.getFileName().toString(),skip);
    }
    private volatile List<Agents.Session> listed;
    private final Object listing=new Object();
    /** Reads the same list the Dashboard shows, under a lock so the newest read is always the one kept. Off the EDT. */
    private List<Agents.Session> readChats(List<ChatEngine.Root> roots){synchronized(listing){listed=Agents.chats(dashboard.agents.refresh(roots));return listed;}}
    void reloadChats(){
        if(listed==null)stats.setText("Reading your chats…");
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground(){readChats(agentRoots());return null;}
            @Override protected void done(){refreshChats();}
        }.execute();
    }
    // ---- Chat locations, edited in Settings ----
    final DefaultListModel<ChatEngine.Root> pathRows=new DefaultListModel<>();
    final JList<ChatEngine.Root> pathList=new JList<>(pathRows);
    final JButton addPath=Ui.button("Add path"),removePath=Ui.button("Remove path"),restorePaths=Ui.button("Restore standard paths");
    JComponent pathsPanel(){
        pathList.setVisibleRowCount(8);pathList.setFixedCellHeight(28);
        pathList.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean sel,boolean focus){
            ChatEngine.Root r=(ChatEngine.Root)v;boolean there=Files.exists(r.path());
            super.getListCellRendererComponent(l,r.tool()+"   ·   "+r.path()+(there?"":"   (not found)"),i,sel,false);setBorder(new EmptyBorder(0,8,0,8));if(!sel&&!there)setForeground(Ui.MUTED);return this;}});
        pathList.getSelectionModel().addListSelectionListener(e->removePath.setEnabled(pathList.getSelectedIndex()>=0));removePath.setEnabled(false);
        JScrollPane scroll=new JScrollPane(pathList);scroll.setBorder(new LineBorder(Ui.LINE));scroll.setPreferredSize(new Dimension(600,8*28+2));
        addPath.addActionListener(e->{JFileChooser c=new JFileChooser();c.setDialogTitle("Add a path where chats are saved");c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);c.setFileHidingEnabled(false);if(c.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)addChatPath(c.getSelectedFile().toPath());});
        removePath.addActionListener(e->{for(ChatEngine.Root r:pathList.getSelectedValuesList())removeChatPath(r);});
        restorePaths.addActionListener(e->restoreChatPaths());
        Ui.Stack box=new Ui.Stack();box.put(scroll,0);box.put(Ui.row(addPath,removePath,restorePaths),8);return box;
    }
    void reloadPaths(){
        new SwingWorker<List<ChatEngine.Root>,Void>(){
            @Override protected List<ChatEngine.Root> doInBackground(){return agentRoots();}
            @Override protected void done(){try{List<ChatEngine.Root> now=get();pathRows.clear();for(ChatEngine.Root r:now)pathRows.addElement(r);}catch(Exception e){Activity.log("Could not list chat locations");}}
        }.execute();
    }
    void addChatPath(Path chosen){
        try{
            Path path=chosen.toAbsolutePath().normalize();
            if(!Files.isDirectory(path))throw new IOException("That folder does not exist or cannot be opened: "+path);
            removedPaths.removeIf(p->Path.of(p).toAbsolutePath().normalize().equals(path));expand(path);
            List<ChatEngine.Root> unique=ChatEngine.unique(extra);extra.clear();extra.addAll(unique);engine.readd(path);saveRoots();Activity.log("Chat location added: "+path);
            pathsChanged();scan(true);
        }catch(Exception e){error(e);}
    }
    void removeChatPath(ChatEngine.Root root){
        try{
            Path path=root.path().toAbsolutePath().normalize();
            if(!extra.removeIf(r->r.path().toAbsolutePath().normalize().equals(path)))removedPaths.add(root.path().toString());
            saveRoots();Activity.log("Chat location removed: "+path);status.setText("Location removed. The files themselves were not touched.");pathsChanged();
        }catch(Exception e){error(e);}
    }
    void restoreChatPaths(){
        try{removedPaths.clear();saveRoots();status.setText("Standard paths restored.");pathsChanged();}catch(Exception e){error(e);}
    }
    private void pathsChanged(){reloadPaths();reloadChats();dashboard.reload();}
    private JComponent chatsPage(){
        table.setRowHeight(30);table.setShowVerticalLines(false);table.setGridColor(Ui.LINE);table.setFillsViewportHeight(true);table.setBackground(Color.WHITE);table.getTableHeader().setReorderingAllowed(false);table.setRowSorter(sorter);
        ((DefaultTableCellRenderer)table.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);table.setIntercellSpacing(new Dimension(8,0));table.setShowHorizontalLines(true);
        int[] widths={660,130,70,140};for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer(){@Override protected void setValue(Object v){setText(v instanceof Long t?when(t):"");}});
        sorter.setSortKeys(List.of(new RowSorter.SortKey(3,SortOrder.DESCENDING)));
        table.getSelectionModel().addListSelectionListener(e->updateChatButtons());
        table.getInputMap().put(KeyStroke.getKeyStroke("DELETE"),"removeChats");
        table.getActionMap().put("removeChats",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){removeSelectedChats();}});
        chatSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e){filterChats();}
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e){filterChats();}
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e){filterChats();}
        });
        JScrollPane tableScroll=new JScrollPane(table);tableScroll.setBorder(new LineBorder(Ui.LINE));tableScroll.getViewport().setBackground(Color.WHITE);
        JPanel below=new JPanel(new BorderLayout());below.setOpaque(false);below.add(stats,BorderLayout.WEST);below.add(Ui.row(new JLabel("Search"),Ui.field(chatSearch),locations,undoRemove,removeChats),BorderLayout.EAST);
        JPanel page=new JPanel(new BorderLayout(0,10));page.setOpaque(false);page.add(tableScroll,BorderLayout.CENTER);page.add(below,BorderLayout.SOUTH);
        locations.setToolTipText("See and edit where chats are saved");locations.addActionListener(e->select("Settings"));removeChats.addActionListener(e->removeSelectedChats());undoRemove.addActionListener(e->undoRemovedChats());
        return page;
    }
    private void filterChats(){
        String q=chatSearch.getText().strip();
        sorter.setRowFilter(q.isEmpty()?null:RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(q),0,1));
    }
    void updateChatButtons(){removeChats.setEnabled(!busy&&table.getSelectedRowCount()>0);undoRemove.setVisible(!lastMarks.isEmpty());undoRemove.setEnabled(!busy);}
    /** Today 14:05, Yesterday 14:05, Nov 5, 14:05; the year appears once it differs. */
    static String when(long millis){
        var at=java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault());var today=java.time.LocalDate.now(at.getZone());var day=at.toLocalDate();
        String time=at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if(day.equals(today))return "Today "+time;if(day.equals(today.minusDays(1)))return "Yesterday "+time;
        return at.format(java.time.format.DateTimeFormatter.ofPattern(day.getYear()==today.getYear()?"MMM d, HH:mm":"MMM d yyyy, HH:mm",Locale.ENGLISH));
    }
    /** One row per chat on the Dashboard, the same list, last interacted first. */
    void refreshChats(){
        List<Agents.Session> all=listed;if(all==null){updateChatButtons();return;}
        Set<String> chosen=new HashSet<>();for(int view:table.getSelectedRows())chosen.add(rowSessions.get(table.convertRowIndexToModel(view)));
        var hide=hidden();rows.setRowCount(0);rowSessions.clear();
        for(Agents.Session s:all){
            if(hide.test(s))continue;
            String t=s.name().strip().replaceAll("\\s+"," ");
            rows.addRow(new Object[]{t.length()>140?t.substring(0,140)+"…":t,s.surface(),s.turns.size(),(long)(s.last*1000)});rowSessions.add(s.path);
        }
        for(int i=0;i<rowSessions.size();i++)if(chosen.contains(rowSessions.get(i))){int v=table.convertRowIndexToView(i);if(v>=0)table.addRowSelectionInterval(v,v);}
        stats.setText(rowSessions.isEmpty()?"No chats in your chat locations.":rowSessions.size()+(rowSessions.size()==1?" chat":" chats"));
        updateChatButtons();
    }
    Set<String> lastMarks=Set.of();
    void removeSelectedChats(){
        if(busy)return;
        Set<String> files=new HashSet<>();for(int view:table.getSelectedRows())files.add(rowSessions.get(table.convertRowIndexToModel(view)));
        if(files.isEmpty())return;
        // The file mark hides it everywhere; the sessions it held leave the history used for feedback.
        Set<String> marks=new HashSet<>();for(String f:files){marks.add("file:"+f);marks.addAll(ChatEngine.sessionsIn(Path.of(f)));}
        boolean caughtUp=patterns.caughtUp();lastRemoved=engine.removeSessions(marks);lastMarks=marks;patterns.forget(lastRemoved.keySet(),caughtUp);
        if(saveChats())status.setText(files.size()+(files.size()==1?" chat":" chats")+" deleted from RHI. The original files were not touched.");
        refreshChats();refreshLibrary();dashboard.reload();
    }
    void undoRemovedChats(){
        if(busy)return;
        engine.restoreSessions(lastRemoved);engine.removed.removeAll(lastMarks);lastRemoved=Map.of();lastMarks=Set.of();
        if(saveChats())status.setText("Restored.");
        reloadChats();refreshLibrary();dashboard.reload();
    }
    private boolean saveChats(){try{engine.save(data);engine.saveRemoved(removedFile);return true;}catch(IOException e){error(e);return false;}}
    private JComponent footer(){
        progress.setPreferredSize(new Dimension(160,6));progress.setBorderPainted(false);progress.setForeground(Ui.ACCENT);progress.setBackground(Ui.LINE);progress.setVisible(false);
        cancel.addActionListener(e->{stopped.set(true);status.setText("Stopping after the current file…");cancel.setEnabled(false);if(workspace.job!=null)workspace.interrupt();});
        JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));right.setOpaque(false);right.add(progress);right.add(cancel);
        JPanel foot=new JPanel(new BorderLayout(12,0));foot.setOpaque(false);foot.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,Ui.LINE),new EmptyBorder(10,0,0,0)));foot.setPreferredSize(new Dimension(100,48));
        status.setMinimumSize(new Dimension(0,0));logsToggle.setFocusPainted(false);logsToggle.setContentAreaFilled(false);logsToggle.setBorder(new EmptyBorder(4,0,4,12));logsToggle.setForeground(Ui.MUTED);logsToggle.setFont(Ui.sans(Font.PLAIN,13));logsToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Ui.fit(logsToggle,Ui.sans(Font.PLAIN,13),">  Logs");foot.add(logsToggle,BorderLayout.WEST);foot.add(status,BorderLayout.CENTER);foot.add(right,BorderLayout.EAST);return foot;
    }
    final JToggleButton logsToggle=new JToggleButton(">  Logs");
    final JTextArea logs=new JTextArea();
    private JScrollPane logsScroll;
    private JComponent logsPanel(){
        logs.setEditable(false);logs.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));logs.setForeground(new Color(0xCBC2CA));logs.setBackground(new Color(0x15121A));logs.setMargin(new Insets(8,10,8,10));logs.setCaretColor(logs.getBackground());
        logsScroll=new JScrollPane(logs);logsScroll.setBorder(null);logsScroll.setPreferredSize(new Dimension(100,170));logsScroll.setVisible(Boolean.parseBoolean(settings.getProperty("logs","false")));
        logsToggle.setSelected(logsScroll.isVisible());logsToggle.setText(logsScroll.isVisible()?"v  Logs":">  Logs");
        logsToggle.addActionListener(e->{
            boolean on=logsToggle.isSelected();logsScroll.setVisible(on);logsToggle.setText(on?"v  Logs":">  Logs");settings.setProperty("logs",Boolean.toString(on));
            try{saveSettings();}catch(IOException ignored){}
            frame.revalidate();
        });
        Activity.listen(line->SwingUtilities.invokeLater(()->{logs.append(line+"\n");logs.setCaretPosition(logs.getDocument().getLength());}));
        return logsScroll;
    }
    void deleteHistory(){
        if(JOptionPane.showConfirmDialog(frame,"Delete imported history, solutions, feedback, and generated tools?\nOriginal chats stay untouched.","Delete history",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        try{
            Files.deleteIfExists(data);Files.deleteIfExists(data.resolveSibling("scan-index.tsv"));engine.requests.clear();engine.stamps.clear();workspace.store.clear();
            engine.removed.clear();lastMarks=Set.of();Files.deleteIfExists(removedFile);Files.deleteIfExists(Insights.file(data.getParent()));Files.deleteIfExists(data.resolveSibling("insights.json"));patterns.forget();lastRemoved=Map.of();refreshChats();status.setText("History deleted. Original chats were not touched.");refreshLibrary();
        }catch(IOException error){error(error);}
    }
    void error(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();Activity.log("✕ "+(cause.getMessage()==null?cause.toString():cause.getMessage()));JOptionPane.showMessageDialog(frame,cause.getMessage()==null?cause.toString():cause.getMessage(),"RHI",JOptionPane.ERROR_MESSAGE);}
    private static boolean isHome(Path p){return Files.isDirectory(p.resolve(".claude"))||Files.isDirectory(p.resolve(".codex"))||Files.isDirectory(p.resolve(".pi"));}
    void scan(){select("Chats");scan(true);}
    /** Imports, then continues on success. Used by the one-click flow, which stays on Feedback. */
    void scan(Runnable after){afterScan=after;scan(!workspace.auto);if(!busy){afterScan=null;workspace.flowing=false;workspace.refresh();}}
    private Runnable afterScan;
    void scan(boolean manual){
        if(busy)return;
        List<Path> exclusions;
        try{exclusions=workspace.excludedPaths();}catch(Exception e){if(manual)error(e);else status.setText(e.getMessage());return;}
        if(workspace.paused()){status.setText("Paused. Resume in Settings.");return;}
        busy=true;stopped.set(false);setTaskControls(true);status.setText("Checking default locations…");
        new SwingWorker<ChatEngine.Update,ChatEngine.Update>(){
            boolean completed=false;
            @Override protected ChatEngine.Update doInBackground()throws Exception{
                publish(new ChatEngine.Update("Reading your chat locations…",0,0,0,List.of()));
                List<ChatEngine.Root> roots=new ArrayList<>(agentRoots());
                // Exactly what the Dashboard lists, so feedback and Chats never disagree.
                Set<Path> only=new HashSet<>();for(Agents.Session s:readChats(roots))only.add(Path.of(s.path).toAbsolutePath().normalize());engine.only=only;
                roots.removeIf(r->r.tool().equals(ChatEngine.DESKTOP));
                long began=System.currentTimeMillis();ChatEngine.Update result=engine.scan(roots,stopped::get,this::publish,exclusions);Activity.log("Import took "+(System.currentTimeMillis()-began)/100/10.0+"s");engine.save(data);engine.saveRemoved(removedFile);engine.saveIndex(data.resolveSibling("scan-index.tsv"));return result;
            }
            @Override protected void process(List<ChatEngine.Update> updates){show(updates.get(updates.size()-1));}
            @Override protected void done(){
                try{ChatEngine.Update u=get();completed=true;Activity.log("Import: "+u.chats()+" chats, "+u.added()+" new requests, "+u.skipped()+" unreadable");status.setText(stopped.get()?"Stopped. Finished chats were kept.":u.added()==0?"No new requests.":u.added()+(u.added()==1?" new request":" new requests")+" imported.");refreshChats();refreshLibrary();}
                catch(Exception e){status.setText("Import could not finish. Try again to save what was read.");if(manual)error(e);}
                finally{busy=false;setTaskControls(false);}
                if(quitRequested)quit();
                else{
                    Runnable next=afterScan;afterScan=null;
                    if(next!=null&&completed&&!stopped.get())next.run();
                    else if(completed&&!manual&&!stopped.get())workspace.analyze(false);
                }
            }
        }.execute();
    }
    void discoverOtherHomes(List<ChatEngine.Root> roots){
        if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")){
            // Finds installed WSL distributions without opening a shell in them or changing them.
            try{
                Process process=new ProcessBuilder("wsl.exe","--list","--quiet").redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if(!process.waitFor(4,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();return;}
                String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_16LE).replace("\0","");
                for(String distro:output.split("\\R")){
                    if(stopped.get())break;
                    distro=distro.strip();if(distro.isEmpty())continue;
                    try(DirectoryStream<Path> users=Files.newDirectoryStream(Path.of("\\\\wsl.localhost\\"+distro+"\\home"))){for(Path user:users)ChatEngine.addHome(roots,user);}catch(IOException ignored){}
                }
            }catch(Exception ignored){}
        }else if(Files.isDirectory(Path.of("/mnt/c/Users"))){
            try(DirectoryStream<Path> users=Files.newDirectoryStream(Path.of("/mnt/c/Users"))){for(Path user:users)if(!Files.isSymbolicLink(user)&&Files.isDirectory(user))ChatEngine.addHome(roots,user);}catch(IOException ignored){}
        }
    }
    private long lastList;
    /** The list fills as chats are discovered. */
    void show(ChatEngine.Update update){status.setText(update.status());if(System.currentTimeMillis()-lastList>250){lastList=System.currentTimeMillis();refreshChats();}}
    void refreshLibrary(){workspace.refresh();}
    void setTaskControls(boolean active){
        updateChatButtons();addPath.setEnabled(!active);cancel.setEnabled(active);cancel.setVisible(active);progress.setVisible(active);progress.setIndeterminate(active);
        workspace.setBusy(active);patterns.setBusy(active);
    }
    void saveRoots()throws IOException{
        settings.stringPropertyNames().stream().filter(k->k.startsWith("root.")||k.startsWith("tool.")||k.startsWith("removedpath.")).toList().forEach(settings::remove);
        int index=0;for(ChatEngine.Root r:ChatEngine.unique(extra)){settings.setProperty("root."+index,r.path().toString());settings.setProperty("tool."+index,r.tool());index++;}
        int off=0;synchronized(removedPaths){for(String p:removedPaths)settings.setProperty("removedpath."+(off++),p);}
        saveSettings();
    }
    void saveSettings()throws IOException{
        Files.createDirectories(settingsFile.getParent());Path temp=Files.createTempFile(settingsFile.getParent(),"settings-",".tmp");
        try{
            try(Writer writer=Files.newBufferedWriter(temp)){settings.store(writer,"Loop preferences. API keys are never stored here.");}
            Files.move(temp,settingsFile,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    void setupTray(){
        if(!SystemTray.isSupported())return;
        try{
            var image=Ui.icon(32);
            PopupMenu menu=new PopupMenu();MenuItem open=new MenuItem("Open RHI"),quit=new MenuItem("Quit");trayPause=new MenuItem(workspace.paused()?"Resume":"Pause");menu.add(open);menu.add(trayPause);menu.addSeparator();menu.add(quit);
            open.addActionListener(e->SwingUtilities.invokeLater(this::reveal));trayPause.addActionListener(e->SwingUtilities.invokeLater(workspace::togglePause));quit.addActionListener(e->SwingUtilities.invokeLater(this::quit));
            tray=new TrayIcon(image,"RHI",menu);tray.setImageAutoSize(true);tray.addActionListener(e->SwingUtilities.invokeLater(this::reveal));SystemTray.getSystemTray().add(tray);
        }catch(Exception e){tray=null;}
    }
    void reveal(){frame.setVisible(true);frame.setState(Frame.NORMAL);frame.setAlwaysOnTop(true);frame.toFront();frame.setAlwaysOnTop(false);}
    void quit(){
        workspace.autosave();
        if(busy){quitRequested=true;stopped.set(true);workspace.interrupt();status.setText("Stopping, then quitting…");return;}
        workspace.timer.stop();if(tray!=null)SystemTray.getSystemTray().remove(tray);frame.dispose();
        try{if(instanceLock!=null)instanceLock.release();if(instanceChannel!=null)instanceChannel.close();}catch(IOException ignored){}
        System.exit(0);
    }
    /** Opening RHI again shows the running window. The port is on this computer only, and a connection carries no data. */
    private static void listenForReopen(Path directory){
        try{
            java.net.ServerSocket server=new java.net.ServerSocket(0,4,java.net.InetAddress.getLoopbackAddress());
            Files.writeString(directory.resolve("instance.port"),""+server.getLocalPort());
            Thread t=new Thread(()->{while(true){try(java.net.Socket ignored=server.accept()){SwingUtilities.invokeLater(()->{if(instance!=null)instance.reveal();});}catch(IOException e){return;}}},"reopen");t.setDaemon(true);t.start();
        }catch(IOException e){Activity.log("Could not listen for a second launch");}
    }
    private static boolean askRunningToOpen(Path directory){
        try(java.net.Socket s=new java.net.Socket()){
            int port=Integer.parseInt(Files.readString(directory.resolve("instance.port")).strip());
            s.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),port),2000);return true;
        }catch(Exception e){return false;}
    }
    public static void main(String[] args){
        // Before any AWT class loads: smooth text on every platform, using the display's own subpixel order where known.
        if(System.getProperty("awt.useSystemAAFontSettings")==null)System.setProperty("awt.useSystemAAFontSettings","lcd");
        System.setProperty("swing.aatext","true");
        if(args.length>0){
            try{
                if(args[0].equals("--search")){ToolsCli.search(String.join(" ",Arrays.copyOfRange(args,1,args.length)));return;}
                throw new IllegalArgumentException("Usage: Loop [--search query]");
            }catch(Exception e){System.err.println(e.getMessage());System.exit(1);return;}
        }
        try{
            Path directory=Path.of(System.getProperty("user.home"),".loop");Files.createDirectories(directory);
            instanceChannel=java.nio.channels.FileChannel.open(directory.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            instanceLock=instanceChannel.tryLock();
            if(instanceLock==null){instanceChannel.close();if(!askRunningToOpen(directory))JOptionPane.showMessageDialog(null,"RHI is already running. Open it from the tray.");return;}
            listenForReopen(directory);
        }catch(Exception e){JOptionPane.showMessageDialog(null,"Could not open your RHI data: "+e.getMessage());return;}
        SwingUtilities.invokeLater(()->{Ui.install();instance=new Loop();});
    }
}
