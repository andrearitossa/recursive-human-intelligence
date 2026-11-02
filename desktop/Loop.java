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

    final JFrame frame=new JFrame("Loop");
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
    final JButton scan=Ui.primary("Import chats"),choose=Ui.button("Add folder"),removeChats=Ui.button("Remove"),undoRemove=Ui.button("Undo"),cancel=Ui.button("Stop");
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
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(tray!=null)frame.setVisible(false);else quit();}});
        try{Path dir=data.getParent();Files.createDirectories(dir);Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException|IOException ignored){}
        loadState();
        workspace=new Workspace(this);patterns=new PatternsPage(this);
        pages.setOpaque(false);pages.add(workspace,"Home");pages.add(patterns,"Patterns");pages.add(chatsPage(),"Chats");pages.add(workspace.settingsPanel,"Settings");
        JPanel root=new JPanel(new BorderLayout(0,16));root.setBackground(Ui.BG);root.setBorder(new EmptyBorder(18,28,14,28));frame.setContentPane(root);
        root.add(header(),BorderLayout.NORTH);root.add(pages,BorderLayout.CENTER);root.add(footer(),BorderLayout.SOUTH);
        select("Home");setTaskControls(false);setupTray();refreshChats();refreshLibrary();
        frame.setSize(1080,780);frame.setMinimumSize(new Dimension(780,560));frame.setLocationRelativeTo(null);frame.setVisible(true);
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
            for(String key:settings.stringPropertyNames())if(key.startsWith("root."))extra.add(new ChatEngine.Root(settings.getProperty("tool."+key.substring(5),"Custom"),Path.of(settings.getProperty(key))));
        }catch(Exception e){error(e);}
    }
    private JComponent header(){
        JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);
        top.add(Ui.label("loop",Ui.sans(Font.BOLD,30),Ui.GREEN),BorderLayout.WEST);
        JPanel links=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,4));links.setOpaque(false);ButtonGroup group=new ButtonGroup();
        for(String name:List.of("Home","Patterns","Chats","Settings")){
            JToggleButton link=new JToggleButton(name);link.setFocusPainted(false);link.setContentAreaFilled(false);link.setOpaque(true);link.setBackground(Ui.BG);link.setForeground(Ui.INK);link.setBorder(new EmptyBorder(7,14,7,14));link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            link.addActionListener(e->select(name));group.add(link);links.add(link);nav.put(name,link);
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
        JPanel bar=new JPanel(new BorderLayout());bar.setOpaque(false);bar.add(Ui.row(scan,choose),BorderLayout.WEST);
        JPanel searchRow=new JPanel(new GridBagLayout());searchRow.setOpaque(false);searchRow.add(Ui.row(new JLabel("Search"),chatSearch));bar.add(searchRow,BorderLayout.EAST);
        JPanel below=new JPanel(new BorderLayout());below.setOpaque(false);below.add(stats,BorderLayout.WEST);below.add(Ui.row(undoRemove,removeChats),BorderLayout.EAST);
        JPanel page=new JPanel(new BorderLayout(0,10));page.setOpaque(false);page.add(bar,BorderLayout.NORTH);page.add(tableScroll,BorderLayout.CENTER);page.add(below,BorderLayout.SOUTH);
        scan.addActionListener(e->scan());choose.addActionListener(e->pick());removeChats.addActionListener(e->removeSelectedChats());undoRemove.addActionListener(e->undoRemovedChats());
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
        for(ChatEngine.Request r:engine.requests.values()){titles.putIfAbsent(r.session(),r.text());counts.merge(r.session(),1,Integer::sum);}
        List<String> sessions=new ArrayList<>(titles.keySet());Collections.reverse(sessions);
        rows.setRowCount(0);rowSessions.clear();
        for(String s:sessions){String t=titles.get(s).strip().replaceAll("\\s+"," ");rows.addRow(new Object[]{t.length()>140?t.substring(0,140)+"…":t,source(s),counts.get(s)});rowSessions.add(s);}
        stats.setText(sessions.isEmpty()?"No chats yet.":sessions.size()+(sessions.size()==1?" chat":" chats"));
        updateChatButtons();
    }
    static String source(String session){String s=session.replace('\\','/');return s.contains("/.codex/")?"Codex":s.contains("/.pi/")?"Pi":s.contains("/")?"Other":"Claude";}
    void removeSelectedChats(){
        if(busy)return;
        Set<String> chosen=new HashSet<>();for(int view:table.getSelectedRows())chosen.add(rowSessions.get(table.convertRowIndexToModel(view)));
        if(chosen.isEmpty())return;
        lastRemoved=engine.removeSessions(chosen);
        if(saveChats())status.setText(chosen.size()+(chosen.size()==1?" chat":" chats")+" removed from Loop. The original files were not touched.");
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
        progress.setPreferredSize(new Dimension(160,6));progress.setBorderPainted(false);progress.setForeground(Ui.GREEN);progress.setBackground(Ui.LINE);progress.setVisible(false);
        cancel.addActionListener(e->{stopped.set(true);status.setText("Stopping after the current file…");cancel.setEnabled(false);if(workspace.job!=null)workspace.interrupt();});
        JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));right.setOpaque(false);right.add(progress);right.add(cancel);
        JPanel foot=new JPanel(new BorderLayout(12,0));foot.setOpaque(false);foot.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,Ui.LINE),new EmptyBorder(10,0,0,0)));foot.setPreferredSize(new Dimension(100,48));
        status.setMinimumSize(new Dimension(0,0));foot.add(status,BorderLayout.CENTER);foot.add(right,BorderLayout.EAST);return foot;
    }
    void deleteHistory(){
        if(JOptionPane.showConfirmDialog(frame,"Delete imported history, solutions, feedback, and generated tools?\nOriginal chats stay untouched.","Delete history",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        try{
            Files.deleteIfExists(data);Files.deleteIfExists(data.resolveSibling("scan-index.tsv"));engine.requests.clear();engine.stamps.clear();workspace.store.clear();
            engine.removed.clear();Files.deleteIfExists(removedFile);Files.deleteIfExists(Insights.file(data.getParent()));patterns.forget();lastRemoved=Map.of();refreshChats();status.setText("History deleted. Original chats were not touched.");refreshLibrary();
        }catch(IOException error){error(error);}
    }
    void error(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();JOptionPane.showMessageDialog(frame,cause.getMessage()==null?cause.toString():cause.getMessage(),"Loop",JOptionPane.ERROR_MESSAGE);}
    void pick(){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Add a folder with chats");chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setFileHidingEnabled(false);
        if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        try{
            Path path=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if(!Files.isDirectory(path))throw new IOException("That folder does not exist or cannot be opened: "+path);
            // A home folder is recognized by its harness folders, so one button covers both cases.
            boolean isHome=Files.isDirectory(path.resolve(".claude"))||Files.isDirectory(path.resolve(".codex"))||Files.isDirectory(path.resolve(".pi"));
            if(isHome)ChatEngine.addHome(extra,path);else extra.add(new ChatEngine.Root("Custom",path));
            List<ChatEngine.Root> unique=ChatEngine.unique(extra);extra.clear();extra.addAll(unique);saveRoots();
            scan();
        }catch(Exception e){error(e);}
    }
    void scan(){scan(true);}
    void scan(boolean manual){
        if(busy)return;
        if(manual)select("Chats");
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
                ChatEngine.Update result=engine.scan(roots,stopped::get,this::publish,exclusions);engine.save(data);engine.saveIndex(data.resolveSibling("scan-index.tsv"));return result;
            }
            @Override protected void process(List<ChatEngine.Update> updates){show(updates.get(updates.size()-1));}
            @Override protected void done(){
                try{ChatEngine.Update u=get();completed=true;status.setText(stopped.get()?"Stopped. Finished chats were kept.":u.added()==0?"No new requests.":u.added()+(u.added()==1?" new request":" new requests")+" imported.");refreshChats();refreshLibrary();}
                catch(Exception e){status.setText("Import could not finish. Try again to save what was read.");if(manual)error(e);}
                finally{busy=false;setTaskControls(false);}
                if(quitRequested)quit();
                else if(completed&&!manual&&!stopped.get())workspace.analyze(false);
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
    void show(ChatEngine.Update update){status.setText(update.status());}
    void refreshLibrary(){workspace.refresh();}
    void setTaskControls(boolean active){
        scan.setEnabled(!active);choose.setEnabled(!active);updateChatButtons();cancel.setEnabled(active);cancel.setVisible(active);progress.setVisible(active);progress.setIndeterminate(active);
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
            var image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Ui.GREEN);g.fillOval(1,1,30,30);g.setColor(Color.WHITE);g.setStroke(new BasicStroke(3));g.drawOval(8,8,16,16);g.dispose();
            PopupMenu menu=new PopupMenu();MenuItem open=new MenuItem("Open Loop"),quit=new MenuItem("Quit");trayPause=new MenuItem(workspace.paused()?"Resume":"Pause");menu.add(open);menu.add(trayPause);menu.addSeparator();menu.add(quit);
            open.addActionListener(e->SwingUtilities.invokeLater(this::reveal));trayPause.addActionListener(e->SwingUtilities.invokeLater(workspace::togglePause));quit.addActionListener(e->SwingUtilities.invokeLater(this::quit));
            tray=new TrayIcon(image,"Loop",menu);tray.setImageAutoSize(true);tray.addActionListener(e->SwingUtilities.invokeLater(this::reveal));SystemTray.getSystemTray().add(tray);
        }catch(Exception e){tray=null;}
    }
    void reveal(){frame.setVisible(true);frame.setState(Frame.NORMAL);frame.toFront();}
    void quit(){
        workspace.autosave();
        if(busy){quitRequested=true;stopped.set(true);workspace.interrupt();status.setText("Stopping, then quitting…");return;}
        workspace.timer.stop();if(tray!=null)SystemTray.getSystemTray().remove(tray);frame.dispose();
        try{if(instanceLock!=null)instanceLock.release();if(instanceChannel!=null)instanceChannel.close();}catch(IOException ignored){}
        System.exit(0);
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
            if(instanceLock==null){JOptionPane.showMessageDialog(null,"Loop is already running. Open it from the tray, or quit it there before restarting.");instanceChannel.close();return;}
        }catch(Exception e){JOptionPane.showMessageDialog(null,"Could not open the Loop workspace: "+e.getMessage());return;}
        SwingUtilities.invokeLater(()->{Ui.install();instance=new Loop();});
    }
}
