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

/** Application window, imports, tray, and shared task state. Home and Settings live in Workspace. */
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
    final DefaultTableModel rows=new DefaultTableModel(new String[]{"Chat","Source","Requests"},0){
        @Override public boolean isCellEditable(int r,int c){return false;}
        @Override public Class<?> getColumnClass(int column){return column==2?Integer.class:String.class;}
    };
    final JProgressBar progress=new JProgressBar();
    final JButton scan=Ui.primary("Import chats"),choose=Ui.button("Add Folder"),setHome=Ui.button("Set Home Folder"),removeChats=Ui.button("Remove"),undoRemove=Ui.button("Undo"),cancel=Ui.button("Stop");
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
    String page="Home";
    Workspace workspace;
    PatternsPage patterns;
    TrayIcon tray;
    MenuItem trayPause;

    Loop(){
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(tray!=null){frame.setVisible(false);if(!"true".equals(settings.getProperty("tray.told"))){settings.setProperty("tray.told","true");try{saveSettings();}catch(IOException x){Activity.log("✕ could not save settings");}tray.displayMessage("RHI is still running","It stays in the tray for the weekly check. Quit it from the tray icon.",TrayIcon.MessageType.NONE);}}else quit();}});
        try{Path dir=data.getParent();Files.createDirectories(dir);Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException|IOException ignored){}
        loadState();
        workspace=new Workspace(this);patterns=new PatternsPage(this);
        pages.setOpaque(false);pages.add(workspace,"Home");pages.add(patterns,"Patterns");pages.add(chatsPage(),"Chats");pages.add(workspace.settingsPanel,"Settings");
        JPanel root=new JPanel(new BorderLayout(0,16));root.setBackground(Ui.BG);root.setBorder(new EmptyBorder(18,28,14,28));frame.setContentPane(root);
        root.add(header(),BorderLayout.NORTH);root.add(pages,BorderLayout.CENTER);JPanel south=new JPanel(new BorderLayout());south.setOpaque(false);south.add(footer(),BorderLayout.NORTH);south.add(logsPanel(),BorderLayout.CENTER);root.add(south,BorderLayout.SOUTH);
        select("Home");setTaskControls(false);setupTray();refreshChats();refreshLibrary();
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
        }catch(Exception e){error(e);}
    }
    /** A saved folder that is really a home, or a folder of homes, means only their chat folders. */
    private void addSaved(String tool,Path path){
        if(!tool.equals("Custom")){extra.add(new ChatEngine.Root(tool,path));return;}
        List<Path> homes=new ArrayList<>();if(isHome(path))homes.add(path);
        else try(DirectoryStream<Path> inside=Files.newDirectoryStream(path)){for(Path child:inside)if(isHome(child))homes.add(child);}catch(IOException|RuntimeException ignored){}
        if(homes.isEmpty())extra.add(new ChatEngine.Root(tool,path));else for(Path h:homes)ChatEngine.addHome(extra,h);
    }
    private JComponent header(){
        JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);
        top.add(Ui.label("RHI",Ui.heading(32),Ui.ACCENT),BorderLayout.WEST);
        JPanel links=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,4));links.setOpaque(false);ButtonGroup group=new ButtonGroup();
        for(String name:List.of("Home","Patterns","Chats","Settings")){
            JToggleButton link=new JToggleButton(name);link.setFocusPainted(false);link.setContentAreaFilled(false);link.setOpaque(true);link.setBackground(Ui.BG);link.setForeground(Ui.INK);link.setBorder(new EmptyBorder(7,14,7,14));link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Ui.fit(link,Ui.sans(Font.BOLD,14),name);link.addActionListener(e->select(name));group.add(link);links.add(link);nav.put(name,link);
        }
        top.add(links,BorderLayout.EAST);return top;
    }
    void select(String name){
        page=name;pageLayout.show(pages,name);if(name.equals("Patterns"))patterns.refresh();
        if(name.equals("Settings"))SwingUtilities.invokeLater(()->workspace.settingsScroll.getVerticalScrollBar().setValue(0));
        nav.forEach((label,link)->{boolean on=label.equals(name);link.setSelected(on);link.setBackground(on?Ui.SOFT:Ui.BG);link.setFont(Ui.sans(on?Font.BOLD:Font.PLAIN,14));});
    }
    private JComponent chatsPage(){
        table.setRowHeight(30);table.setShowVerticalLines(false);table.setGridColor(Ui.LINE);table.setFillsViewportHeight(true);table.setBackground(Color.WHITE);table.getTableHeader().setReorderingAllowed(false);table.setRowSorter(sorter);
        ((DefaultTableCellRenderer)table.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);table.setIntercellSpacing(new Dimension(8,0));table.setShowHorizontalLines(true);
        int[] widths={720,90,80};for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getSelectionModel().addListSelectionListener(e->updateChatButtons());
        table.getInputMap().put(KeyStroke.getKeyStroke("DELETE"),"removeChats");
        table.getActionMap().put("removeChats",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){removeSelectedChats();}});
        chatSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e){filterChats();}
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e){filterChats();}
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e){filterChats();}
        });
        JScrollPane tableScroll=new JScrollPane(table);tableScroll.setBorder(new LineBorder(Ui.LINE));tableScroll.getViewport().setBackground(Color.WHITE);
        JPanel bar=new JPanel(new BorderLayout());bar.setOpaque(false);bar.add(Ui.row(scan,setHome,choose),BorderLayout.WEST);
        JPanel searchRow=new JPanel(new GridBagLayout());searchRow.setOpaque(false);searchRow.add(Ui.row(new JLabel("Search"),Ui.field(chatSearch)));bar.add(searchRow,BorderLayout.EAST);
        JPanel below=new JPanel(new BorderLayout());below.setOpaque(false);below.add(stats,BorderLayout.WEST);below.add(Ui.row(undoRemove,removeChats),BorderLayout.EAST);
        JPanel page=new JPanel(new BorderLayout(0,10));page.setOpaque(false);page.add(bar,BorderLayout.NORTH);page.add(tableScroll,BorderLayout.CENTER);page.add(below,BorderLayout.SOUTH);
        scan.addActionListener(e->scan());choose.addActionListener(e->pick(false));setHome.addActionListener(e->pick(true));removeChats.addActionListener(e->removeSelectedChats());undoRemove.addActionListener(e->undoRemovedChats());
        return page;
    }
    private void filterChats(){
        String q=chatSearch.getText().strip();
        sorter.setRowFilter(q.isEmpty()?null:RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(q),0,1));
    }
    void updateChatButtons(){removeChats.setEnabled(!busy&&table.getSelectedRowCount()>0);undoRemove.setVisible(!lastRemoved.isEmpty());undoRemove.setEnabled(!busy);}
    /** One row per chat, newest import first, named by what the user first asked. */
    void refreshChats(){
        Map<String,String> titles=new LinkedHashMap<>();Map<String,Integer> counts=new HashMap<>();
        for(ChatEngine.Request r:engine.snapshot()){titles.putIfAbsent(r.session(),r.text());counts.merge(r.session(),1,Integer::sum);}
        List<String> sessions=new ArrayList<>(titles.keySet());Collections.reverse(sessions);
        rows.setRowCount(0);rowSessions.clear();
        for(String s:sessions){String t=titles.get(s).strip().replaceAll("\\s+"," ");rows.addRow(new Object[]{t.length()>140?t.substring(0,140)+"…":t,source(s),counts.get(s)});rowSessions.add(s);}
        stats.setText(sessions.isEmpty()?(engine.removed.isEmpty()?"No chats yet.":"All chats deleted from RHI. Add their folder to bring them back."):sessions.size()+(sessions.size()==1?" chat":" chats"));
        updateChatButtons();
    }
    static String source(String session){String s=session.replace('\\','/');return s.contains("/.codex/")?"Codex":s.contains("/.pi/")?"Pi":s.contains("/")?"Other":"Claude";}
    void removeSelectedChats(){
        if(busy)return;
        Set<String> chosen=new HashSet<>();for(int view:table.getSelectedRows())chosen.add(rowSessions.get(table.convertRowIndexToModel(view)));
        if(chosen.isEmpty())return;
        boolean caughtUp=patterns.caughtUp();lastRemoved=engine.removeSessions(chosen);patterns.forget(lastRemoved.keySet(),caughtUp);
        if(saveChats())status.setText(chosen.size()+(chosen.size()==1?" chat":" chats")+" deleted from RHI. The original files were not touched.");
        refreshChats();refreshLibrary();
    }
    void undoRemovedChats(){
        if(busy)return;
        engine.restoreSessions(lastRemoved);lastRemoved=Map.of();
        if(saveChats())status.setText("Restored.");
        refreshChats();refreshLibrary();
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
            engine.removed.clear();Files.deleteIfExists(removedFile);Files.deleteIfExists(Insights.file(data.getParent()));Files.deleteIfExists(data.resolveSibling("insights.json"));patterns.forget();lastRemoved=Map.of();refreshChats();status.setText("History deleted. Original chats were not touched.");refreshLibrary();
        }catch(IOException error){error(error);}
    }
    void error(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();Activity.log("✕ "+(cause.getMessage()==null?cause.toString():cause.getMessage()));JOptionPane.showMessageDialog(frame,cause.getMessage()==null?cause.toString():cause.getMessage(),"RHI",JOptionPane.ERROR_MESSAGE);}
    private static boolean isHome(Path p){return Files.isDirectory(p.resolve(".claude"))||Files.isDirectory(p.resolve(".codex"))||Files.isDirectory(p.resolve(".pi"));}
    /** Set Home Folder: the one place with .claude, .codex, or .pi. Add Folder: any other folder of chats. */
    void pick(boolean asHome){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(asHome?"Set Home Folder":"Add Folder");chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setFileHidingEnabled(false);
        if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        try{
            Path path=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if(!Files.isDirectory(path))throw new IOException("That folder does not exist or cannot be opened: "+path);
            if(asHome){
                if(!isHome(path))throw new IOException("No .claude, .codex, or .pi folder in "+path);
                String old=settings.getProperty("home");
                if(old!=null){List<ChatEngine.Root> gone=new ArrayList<>();ChatEngine.addHome(gone,Path.of(old));extra.removeAll(gone);}
                ChatEngine.addHome(extra,path);settings.setProperty("home",path.toString());Activity.log("Home folder set to "+path);
            }else{extra.add(new ChatEngine.Root("Custom",path));Activity.log("Folder added: "+path);}
            engine.readd(path);
            List<ChatEngine.Root> unique=ChatEngine.unique(extra);extra.clear();extra.addAll(unique);saveRoots();
            scan();
        }catch(Exception e){error(e);}
    }
    void scan(){select("Chats");scan(true);}
    /** Imports, then continues on success. Used by the one-click flow, which stays on Home. */
    void scan(Runnable after){afterScan=after;scan(!workspace.auto);if(!busy){afterScan=null;workspace.flowing=false;workspace.refresh();}}
    private Runnable afterScan;
    void scan(boolean manual){
        if(busy)return;
        List<Path> exclusions;
        try{exclusions=workspace.excludedPaths();}catch(Exception e){if(manual)error(e);else status.setText(e.getMessage());return;}
        if(workspace.paused()){status.setText("Paused. Resume in Settings.");return;}
        busy=true;stopped.set(false);setTaskControls(true);status.setText("Checking default locations…");
        List<ChatEngine.Root> selectedRoots=ChatEngine.defaults(Path.of(System.getProperty("user.home")),System.getenv());selectedRoots.addAll(extra);
        boolean discover=workspace.cross.isSelected();
        Set<String> disabled=new HashSet<>();for(String tool:List.of("Codex","Claude","Pi"))if(!workspace.enabled(tool))disabled.add(tool);
        new SwingWorker<ChatEngine.Update,ChatEngine.Update>(){
            boolean completed=false;
            @Override protected ChatEngine.Update doInBackground()throws Exception{
                List<ChatEngine.Root> roots=new ArrayList<>(selectedRoots);
                if(discover){publish(new ChatEngine.Update("Finding Windows / WSL chat locations…",0,0,0,List.of()));discoverOtherHomes(roots);}
                roots.removeIf(r->disabled.contains(r.tool()));
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
        scan.setEnabled(!active);choose.setEnabled(!active);setHome.setEnabled(!active);updateChatButtons();cancel.setEnabled(active);cancel.setVisible(active);progress.setVisible(active);progress.setIndeterminate(active);
        workspace.setBusy(active);patterns.setBusy(active);
    }
    void saveRoots()throws IOException{
        settings.stringPropertyNames().stream().filter(k->k.startsWith("root.")||k.startsWith("tool.")).toList().forEach(settings::remove);
        int index=0;for(ChatEngine.Root r:ChatEngine.unique(extra)){settings.setProperty("root."+index,r.path().toString());settings.setProperty("tool."+index,r.tool());index++;}
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
