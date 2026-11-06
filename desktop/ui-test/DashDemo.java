import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Fictional agent sessions for the README: one made-up developer, Maya, and her projects. No real chats. */
public class DashDemo {
    static final double NOW=System.currentTimeMillis()/1000.0;
    static String at(double hoursAgo){return Instant.ofEpochMilli((long)((NOW-hoursAgo*3600)*1000)).toString();}
    static String q(String s){return Json.write(s);}
    /** Live states shown in the demo, by session id; real RHI reads them from running processes. */
    static final Map<String,String> LIVE=new HashMap<>();

    static void claude(Path home,String id,String cwd,String entry,String branch,String title,String status,String[][] turns)throws Exception{
        Path f=home.resolve(".claude/projects/"+cwd.replaceAll("[^A-Za-z0-9]","-")+"/"+id+".jsonl");Files.createDirectories(f.getParent());StringBuilder b=new StringBuilder();
        b.append("{\"type\":\"ai-title\",\"aiTitle\":"+q(title)+"}\n");
        for(String[] t:turns){
            double h=Double.parseDouble(t[0]);
            b.append("{\"type\":\"user\",\"timestamp\":\""+at(h)+"\",\"cwd\":"+q(cwd)+",\"entrypoint\":\""+entry+"\",\"gitBranch\":\""+branch+"\",\"sessionId\":\""+id+"\",\"message\":{\"role\":\"user\",\"content\":"+q(t[1])+"}}\n");
            b.append("{\"type\":\"assistant\",\"timestamp\":\""+at(h-Double.parseDouble(t[3]))+"\",\"message\":{\"role\":\"assistant\",\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":"+q(t[2])+"}]}}\n");
        }
        Files.writeString(f,b.toString());if(status!=null)LIVE.put(id,status);
    }
    static void codex(Path home,String id,String cwd,String origin,String status,String[][] turns)throws Exception{
        Path f=home.resolve(".codex/sessions/2026/11/06/rollout-2026-11-06T09-00-00-"+id+".jsonl");Files.createDirectories(f.getParent());StringBuilder b=new StringBuilder();
        b.append("{\"type\":\"session_meta\",\"timestamp\":\""+at(Double.parseDouble(turns[0][0])+0.01)+"\",\"payload\":{\"id\":\""+id+"\",\"cwd\":"+q(cwd)+",\"originator\":\""+origin+"\"}}\n");
        for(String[] t:turns){
            double h=Double.parseDouble(t[0]);
            b.append("{\"type\":\"event_msg\",\"timestamp\":\""+at(h)+"\",\"payload\":{\"type\":\"task_started\"}}\n");
            b.append("{\"type\":\"response_item\",\"timestamp\":\""+at(h)+"\",\"payload\":{\"type\":\"message\",\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":"+q(t[1])+"}]}}\n");
            b.append("{\"type\":\"response_item\",\"timestamp\":\""+at(h-Double.parseDouble(t[3]))+"\",\"payload\":{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":"+q(t[2])+"}]}}\n");
            b.append("{\"type\":\"event_msg\",\"timestamp\":\""+at(h-Double.parseDouble(t[3]))+"\",\"payload\":{\"type\":\"task_complete\",\"last_agent_message\":"+q(t[2])+"}}\n");
        }
        Files.writeString(f,b.toString());if(status!=null)LIVE.put(id,status);
    }
    static void pi(Path home,String id,String cwd,String[][] turns)throws Exception{
        Path f=home.resolve(".pi/agent/sessions/"+cwd.replace('/','-')+"/2026-11-06T09-00-00_"+id+".jsonl");Files.createDirectories(f.getParent());StringBuilder b=new StringBuilder();
        b.append("{\"type\":\"session\",\"timestamp\":\""+at(Double.parseDouble(turns[0][0])+0.01)+"\",\"cwd\":"+q(cwd)+"}\n");
        for(String[] t:turns){
            double h=Double.parseDouble(t[0]);
            b.append("{\"type\":\"message\",\"timestamp\":\""+at(h)+"\",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":"+q(t[1])+"}]}}\n");
            b.append("{\"type\":\"message\",\"timestamp\":\""+at(h-Double.parseDouble(t[3]))+"\",\"message\":{\"role\":\"assistant\",\"stopReason\":\"stop\",\"content\":[{\"type\":\"text\",\"text\":"+q(t[2])+"}]}}\n");
        }
        Files.writeString(f,b.toString());
    }
    static final String WEBHOOK_REPLY="""
## Stripe webhooks are wired up

I added a signed endpoint at `/api/stripe/webhook` and covered the three events the invoice flow needs:

| Event | What happens |
|---|---|
| `invoice.paid` | Marks the invoice **paid** and emails a receipt |
| `invoice.payment_failed` | Flags it and schedules a retry |
| `customer.subscription.deleted` | Stops future invoices |

Next steps:
1. Add `STRIPE_WEBHOOK_SECRET` to your `.env`
2. Run `stripe listen --forward-to localhost:3000/api/stripe/webhook`
3. Pay a test invoice and check that it turns green

```ts
const event = stripe.webhooks.constructEvent(body, signature, secret);
```

I'm now running the end-to-end tests against the local listener.""";

    /** Writes Maya's sessions into a demo home. Returns the session to open in the sidebar shot. */
    static void seed(Path home)throws Exception{
        if(!home.toString().contains("loop-demo-home"))throw new IllegalStateException("Demo data only goes into a demo home");
        String m="/home/maya/";
        claude(home,"1b9e2c7a-4d10-4f6e-9a51-0c2b7d1e8f01",m+"invoice-app","cli","main","Stripe webhooks for paid invoices","working",new String[][]{
            {"30","Sketch the data model for invoices and customers","Here's a first model with `Invoice`, `LineItem`, and `Customer`.","0.6"},
            {"26","Generate the PDF layout from the Figma export","The PDF template now matches the Figma frame, fonts included.","0.9"},
            {"5","Make the invoice list paginate on the server","Pagination is server-side now, 25 per page.","0.4"},
            {"0.6","Wire up Stripe webhooks so paid invoices update themselves",WEBHOOK_REPLY,"0.05"}});
        codex(home,"5f2a9c11-7b3e-4e8d-a2c4-9d6e1f0b3a77",m+"invoice-app","codex_cli_rs","waiting",new String[][]{
            {"3","Write tests for the PDF export","Added 14 tests for totals, taxes, and page breaks. Two fail on multi-currency invoices: should totals convert, or keep each currency?","0.8"}});
        pi(home,"b71c3e90-2a4f-4c55-8d1e-6f3a9b2c4d58",m+"garden-planner",new String[][]{
            {"50","Add a frost-date lookup by postcode","Frost dates now come from the climate table, cached per postcode.","0.5"},
            {"20","Suggest companion plants on the bed view","Companion suggestions appear when you drop a plant on a bed.","0.7"},
            {"2","Export the season plan as a printable calendar","The calendar export is ready, one page per month.","0.4"}});
        codex(home,"9c4d2e61-3f8a-4b17-b5e2-7a1c0d9e6f23",m+"data-pipeline","codex_vscode","stalled",new String[][]{
            {"70","Move the nightly job from cron to a queue","The nightly job now runs as a queued task with retries.","1.2"},
            {"8","Backfill last month of events into the warehouse","Backfill stopped at day 19: the warehouse rejected rows with an unknown `region`. I paused before retrying.","0.9"}});
        claude(home,"3e7f1a2b-8c9d-4e0f-a1b2-c3d4e5f6a7b8",m+"portfolio-site","claude-vscode","main","Dark mode and case-study pages","done",new String[][]{
            {"100","Add dark mode that follows the system setting","Dark mode follows the system and remembers a manual choice.","0.5"},
            {"28","Write a case-study page template","The case-study template has hero, problem, process, and outcome sections.","0.6"}});
        claude(home,"7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",m+"recipe-api","cli","feature/search","Full-text search for recipes","done",new String[][]{
            {"120","Add full-text search across titles and ingredients","Search uses Postgres full-text with a weighted title.","0.8"},
            {"60","Rank results by how many pantry items match","Results now rank by pantry matches, then by rating.","0.5"}});
        claude(home,"c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f","C:\\Users\\maya\\AppData\\Roaming\\Claude-Data\\scratch-workspaces\\lisbon","claude-desktop","","Plan a long weekend in Lisbon","waiting",new String[][]{
            {"1.5","Plan three relaxed days in Lisbon in March","Day 1 Alfama and the castle, day 2 Belém, day 3 Sintra. Want restaurant picks too?","0.1"}});
        codex(home,"e5f6a7b8-c9d0-4e1f-a2b3-c4d5e6f7a8b9","","Codex Desktop",null,new String[][]{
            {"9","Compare three note-taking apps for a small team","Here's a comparison of sync, pricing, and offline support.","0.2"}});
        codex(home,"f6a7b8c9-d0e1-4f2a-b3c4-d5e6f7a8b9c0","","Codex Desktop",null,new String[][]{
            {"40","Draft a polite follow-up to a client who hasn't paid","Here's a short, friendly follow-up with a clear due date.","0.1"}});
        Path loop=home.resolve(".loop");Files.createDirectories(loop);
        Files.writeString(loop.resolve("agents-pins.tsv"),m+"invoice-app\tInvoice App\n"+m+"garden-planner\tGarden Planner\n");
        Agents.stage=s->{String st=LIVE.get(s.id);if(st!=null){s.status=st;s.live=!st.equals("done");}else{s.status="done";s.live=false;}};
    }
}
