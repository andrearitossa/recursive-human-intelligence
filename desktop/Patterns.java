import java.util.*;
import java.util.regex.Pattern;

/** Step 1: local, model-free grouping of requests by how the user works and what it is about. */
final class Patterns {
    enum Mode {
        BUILD("Build"),FIX("Fix"),UNDERSTAND("Understand"),CHECK("Check"),PLAN("Plan"),RESEARCH("Research"),CORRECT("Correct"),STEER("Nudge");
        final String label;Mode(String label){this.label=label;}
    }
    record Item(String id,ChatEngine.Request request,Mode mode) {}
    static final class Group {
        String label="",description="";boolean friction;final List<Item> items=new ArrayList<>();final Set<String> sessions=new HashSet<>();final EnumMap<Mode,Integer> modes=new EnumMap<>(Mode.class);
        final List<Item> examples=new ArrayList<>();
        void add(Item i){items.add(i);sessions.add(i.request().session());modes.merge(i.mode(),1,Integer::sum);}
        Mode dominant(){if(friction||FRICTION_LABEL.equals(label))return Mode.CORRECT;return modes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Mode.BUILD);}
    }
    record Result(List<Group> groups,Map<Mode,Integer> modes,int requests,int sessions,int unclustered) {}

    private static final Pattern CORRECT=Pattern.compile("^(no\\b|nope|not\\b|wrong|still\\b|again\\b|that'?s not|this is not|it (doesn'?t|does not|didn'?t)|you (didn'?t|forgot|missed)|why did you|non\\b|ancora\\b)|\\b(still (not|broken|fails?|failing|wrong)|doesn'?t work|not what i)");
    private static final Pattern STEER=Pattern.compile("^(status|continue|go on|go ahead|ok|okay|yes|yep|proceed|next|do it|sure|thanks|and\\?|si\\b|sì\\b|vai\\b|continua)");
    private static final Pattern FIX=Pattern.compile("\\b(fix|bug|error|errors|fail|fails|failed|failing|broken|crash\\w*|not working|issue|exception|traceback|errore|sistema)\\b");
    private static final Pattern PLAN=Pattern.compile("\\b(plan|design|propose|proposal|architecture|strategy|brainstorm|idea|ideas|approach|roadmap)\\b");
    private static final Pattern CHECK=Pattern.compile("\\b(review|check|verify|validate|audit|test|tests|testing|ensure|quality)\\b");
    private static final Pattern RESEARCH=Pattern.compile("\\b(research|search|look up|compare|find out|investigate|benchmark|sources?)\\b");
    private static final Pattern ASK=Pattern.compile("^(what|why|how|when|where|which|who|is|are|does|do|can you explain|explain|help me understand|cosa|come|perch[eé])\\b|\\b(explain|help me understand)\\b");
    static Mode mode(String raw){
        String t=raw.strip().toLowerCase(Locale.ROOT);String head=t.substring(0,Math.min(300,t.length()));int words=head.split("\\s+").length;
        if(CORRECT.matcher(head).find())return Mode.CORRECT;
        if(words<=6&&(STEER.matcher(head).find()||!head.contains(" ")))return Mode.STEER;
        if(ASK.matcher(head).find()&&t.length()<400)return Mode.UNDERSTAND;
        if(FIX.matcher(head).find())return Mode.FIX;
        if(PLAN.matcher(head).find())return Mode.PLAN;
        if(RESEARCH.matcher(head).find())return Mode.RESEARCH;
        if(CHECK.matcher(head).find())return Mode.CHECK;
        if(t.endsWith("?")&&t.length()<400)return Mode.UNDERSTAND;
        return Mode.BUILD;
    }
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(("the and for that this with you your are was were have has had not but can could would should will just like want need make use using used get got see let please also now then than from into out about what when where which while how why who all any some more most very really only one two there here they them their its it's i'm don't dont does did done been being our ours his her she him over under after before again still each both such same other into onto per via yes okay thanks sure going know think try trying look looks way thing things something anything everything give take put run work works working working ensure check add create implement build fix test should must may might able new first last next good great simple better best actually currently already right well much many lot lets kind sort maybe etc "+
        "che per con non una uno gli del della delle dei nel nella sul sulla come sono questo questa quello anche piu più perche perché fare devo puoi voglio ogni tutti tutto").split(" ")));
    private static String body(String raw){
        String t=raw.replaceAll("(?s)```.*?```"," ").replaceAll("https?://\\S+"," ").replaceAll("\\S*[/\\\\]\\S*"," ").toLowerCase(Locale.ROOT);
        return t.substring(0,Math.min(800,t.length())); // Long pastes are mostly material, not the request.
    }
    static List<String> tokens(String raw){
        List<String> out=new ArrayList<>();
        for(String w:body(raw).split("[^\\p{L}0-9+#-]+")){w=w.replaceAll("^[-+]+|[-+]+$","");if(w.length()<3||w.length()>24||STOP.contains(w)||w.matches(".*\\d.*"))continue;out.add(w);}
        return out;
    }
    static final String FRICTION_LABEL="frustration · corrections · repeats";
    private static final Pattern FRICTION=Pattern.compile("\\b(dumb|stupid|fucking|fuck|wtf|idiot|i told you|i said|as i said|again and again|still (not|broken|wrong|failing)|not working|doesn'?t work|you (forgot|missed|didn'?t)|why did you|that'?s not what|just (open|run|do|trigger|paste|stop)|hate|annoying|confusing)\\b");
    /** Requests with replayed copies from forked or resumed chats removed. */
    static List<Item> distinct(Map<String,ChatEngine.Request> requests){
        List<Item> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(var e:requests.entrySet())if(seen.add(e.getValue().text().strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT)))out.add(new Item(e.getKey(),e.getValue(),mode(e.getValue().text())));
        return out;
    }
    private static final Set<String> GRAMMAR=Set.of("i","a","an","to","in","of","on","at","is","or","as","be","by","we","my","me","so","if","do","would","like","point","terms");
    /** How they phrase things, measured locally: wording retyped across chats is a standing instruction nobody saved. */
    static Map<String,Object> signals(Map<String,ChatEngine.Request> requests){
        List<Item> items=distinct(requests);int shortOnes=0;List<Integer> lengths=new ArrayList<>();Map<String,Set<String>> phrases=new HashMap<>();
        for(Item i:items){
            String text=i.request().text().strip().toLowerCase(Locale.ROOT);String[] words=text.split("\\s+");lengths.add(words.length);if(words.length<=6)shortOnes++;
            String[] clean=text.substring(0,Math.min(600,text.length())).replaceAll("[^\\p{L}' ]+"," | ").split("\\s+");
            for(int n=3;n<=5;n++)for(int w=0;w+n<=clean.length;w++){
                List<String> gram=Arrays.asList(clean).subList(w,w+n);if(gram.contains("|")||STOP.contains(gram.get(0))||STOP.contains(gram.get(n-1))||GRAMMAR.contains(gram.get(0))||GRAMMAR.contains(gram.get(n-1)))continue;
                phrases.computeIfAbsent(String.join(" ",gram),k->new HashSet<>()).add(i.request().session());
            }
        }
        Collections.sort(lengths);
        List<Map.Entry<String,Set<String>>> top=new ArrayList<>(phrases.entrySet());top.removeIf(e->e.getValue().size()<4);top.sort((a,b)->b.getValue().size()-a.getValue().size());
        List<String> repeated=new ArrayList<>();
        // A phrase inside a longer one already listed adds nothing.
        for(var e:top){if(repeated.size()==25)break;if(repeated.stream().noneMatch(r->r.contains(e.getKey())))repeated.add(e.getKey()+" ("+e.getValue().size()+" chats)");}
        Map<String,Object> out=new LinkedHashMap<>();out.put("requests",items.size());out.put("medianWords",lengths.isEmpty()?0:lengths.get(lengths.size()/2));out.put("percentSixWordsOrFewer",items.isEmpty()?0:100*shortOnes/items.size());out.put("phrasesRetypedAcrossChats",repeated);return out;
    }
    static Result analyze(Map<String,ChatEngine.Request> requests){
        List<Item> items=new ArrayList<>();Map<Mode,Integer> modes=new EnumMap<>(Mode.class);Set<String> sessions=new HashSet<>();
        // A forked or resumed chat replays old messages. Counted once, or a copy would look like recurrence.
        Set<String> seen=new HashSet<>();
        for(var e:requests.entrySet()){if(!seen.add(e.getValue().text().strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT)))continue;Mode m=mode(e.getValue().text());items.add(new Item(e.getKey(),e.getValue(),m));modes.merge(m,1,Integer::sum);sessions.add(e.getValue().session());}
        // Words are scored by how many chats use them, so one long chat cannot define a topic.
        List<List<String>> tokens=new ArrayList<>();Map<String,Set<String>> df=new HashMap<>();
        for(Item i:items){List<String> t=tokens(i.request().text());tokens.add(t);for(String w:t)df.computeIfAbsent(w,k->new HashSet<>()).add(i.request().session());}
        Map<String,Integer> vocab=new HashMap<>();List<String> words=new ArrayList<>();double max=Math.max(30,sessions.size()*.25); // A small history must keep its topic words.
        for(var e:new TreeMap<>(df).entrySet())if(e.getValue().size()>=2&&e.getValue().size()<=max){vocab.put(e.getKey(),words.size());words.add(e.getKey());}
        List<Map<Integer,Double>> vectors=new ArrayList<>();List<Item> usable=new ArrayList<>();
        for(int n=0;n<items.size();n++){
            Map<Integer,Double> v=new HashMap<>();for(String w:tokens.get(n)){Integer k=vocab.get(w);if(k!=null)v.merge(k,1.0,Double::sum);}
            if(v.size()<3)continue;
            double norm=0;for(var x:v.entrySet()){double w=(1+Math.log(x.getValue()))*Math.log(1+sessions.size()/(double)df.get(words.get(x.getKey())).size());x.setValue(w);norm+=w*w;}
            norm=Math.sqrt(norm);for(var x:v.entrySet())x.setValue(x.getValue()/norm);
            vectors.add(v);usable.add(items.get(n));
        }
        List<Group> groups=new ArrayList<>(cluster(usable,vectors,words));
        // Friction is about how a chat went, not its subject, so topic clustering scatters it.
        Group friction=new Group();friction.label=FRICTION_LABEL;
        for(Item i:items)if(i.request().text().length()<=400&&FRICTION.matcher(i.request().text().toLowerCase(Locale.ROOT)).find())friction.add(i);
        if(friction.sessions.size()>=3){
            Map<String,Integer> perSession=new HashMap<>();List<Item> latest=new ArrayList<>(friction.items);Collections.reverse(latest);
            for(Item i:latest)if(perSession.merge(i.request().session(),1,Integer::sum)==1&&friction.examples.size()<20)friction.examples.add(i);
            groups.add(friction);groups.sort((a,b)->b.sessions.size()-a.sessions.size());
        }
        int clustered=groups.stream().mapToInt(g->g.items.size()).sum();
        return new Result(groups,modes,items.size(),sessions.size(),items.size()-clustered);
    }
    static final double MIN_SIMILARITY=0.26;
    private static double dot(Map<Integer,Double> v,double[] c){double s=0;for(var x:v.entrySet())s+=x.getValue()*c[x.getKey()];return s;}
    private static List<Group> cluster(List<Item> items,List<Map<Integer,Double>> vectors,List<String> words){
        int n=items.size();if(n<6||words.isEmpty())return List.of();
        int k=Math.min(60,Math.max(3,(int)Math.sqrt(n)));
        // Deterministic k-means++ on the unit sphere: the same history always draws the same map.
        Random random=new Random(42);List<double[]> centers=new ArrayList<>();double[] best=new double[n];Arrays.fill(best,-1);
        int pick=random.nextInt(n);
        for(int c=0;c<k;c++){
            double[] center=new double[words.size()];vectors.get(pick).forEach((i,w)->center[i]=w);centers.add(center);
            double total=0;for(int i=0;i<n;i++){best[i]=Math.max(best[i],dot(vectors.get(i),center));total+=1-best[i];}
            double r=random.nextDouble()*total;for(int i=0;i<n;i++){r-=1-best[i];if(r<=0){pick=i;break;}}
        }
        int[] assign=new int[n];
        for(int round=0;round<20;round++){
            boolean moved=false;
            for(int i=0;i<n;i++){int top=0;double score=-1;for(int c=0;c<k;c++){double s=dot(vectors.get(i),centers.get(c));if(s>score){score=s;top=c;}}if(assign[i]!=top||round==0){moved|=assign[i]!=top;assign[i]=top;}}
            if(!moved&&round>0)break;
            for(double[] c:centers)Arrays.fill(c,0);
            for(int i=0;i<n;i++){double[] c=centers.get(assign[i]);for(var x:vectors.get(i).entrySet())c[x.getKey()]+=x.getValue();}
            for(double[] c:centers){double norm=0;for(double w:c)norm+=w*w;norm=Math.sqrt(norm);if(norm>0)for(int i=0;i<c.length;i++)c[i]/=norm;}
        }
        List<Group> out=new ArrayList<>();
        for(int c=0;c<k;c++){
            Group g=new Group();List<Integer> members=new ArrayList<>();double[] center=centers.get(c);
            // Only requests really resembling the center belong: k-means otherwise forces every stray in somewhere.
            for(int i=0;i<n;i++)if(assign[i]==c&&dot(vectors.get(i),center)>=MIN_SIMILARITY){g.add(items.get(i));members.add(i);}
            if(g.sessions.size()<3)continue; // Recurrence is the point: one-off topics are not patterns.
            Integer[] order=new Integer[words.size()];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,(x,y)->Double.compare(center[y],center[x]));
            g.label=words.get(order[0])+" · "+words.get(order[1])+" · "+words.get(order[2]);
            // Examples sit near the center and come from different chats.
            members.sort((x,y)->Double.compare(dot(vectors.get(y),center),dot(vectors.get(x),center)));
            Map<String,Integer> perSession=new HashMap<>();
            for(int i:members){Item it=items.get(i);if(perSession.merge(it.request().session(),1,Integer::sum)<=2&&g.examples.size()<14)g.examples.add(it);}
            out.add(g);
        }
        out.sort((a,b)->b.sessions.size()-a.sessions.size());return out;
    }
    private Patterns(){}
}
