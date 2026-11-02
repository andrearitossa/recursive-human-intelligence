import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Provider-specific wire formats behind one completion interface. */
final class Models {
    enum Provider {
        OPENAI("OpenAI","https://api.openai.com/v1/chat/completions","OPENAI_API_KEY"),
        CLAUDE("Claude","https://api.anthropic.com/v1/messages","ANTHROPIC_API_KEY"),
        OPENROUTER("OpenRouter","https://openrouter.ai/api/v1/chat/completions","OPENROUTER_API_KEY");
        final String label,endpoint,keyEnv;
        Provider(String label,String endpoint,String keyEnv){this.label=label;this.endpoint=endpoint;this.keyEnv=keyEnv;}
        public String toString(){return label;}
    }
    record Config(Provider provider,String endpoint,String model,String key) {
        void validate(){
            if(model.isBlank())throw new IllegalArgumentException("Enter a model ID in Settings.");
            URI uri=URI.create(endpoint);
            String host=uri.getHost();if(host==null)throw new IllegalArgumentException("Enter a complete API endpoint URL.");
            boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(host);
            if(!"https".equals(uri.getScheme())&&!(local&&"http".equals(uri.getScheme())))throw new IllegalArgumentException("Use HTTPS, or HTTP on localhost.");
            if(uri.getUserInfo()!=null)throw new IllegalArgumentException("Do not put credentials in the endpoint.");
            if(key.isBlank())throw new IllegalArgumentException("Enter a session API key or set "+provider.keyEnv+" before launch.");
        }
    }
    interface Model {
        String complete(String instructions,String input)throws Exception;
        /** Sends text as it arrives. A model that cannot stream hands it all over at the end. */
        default String stream(String instructions,String input,java.util.function.Consumer<String> onText)throws Exception{String t=complete(instructions,input);onText.accept(t);return t;}
    }
    static Model connect(Config config){
        config.validate();
        return new Model(){
            @Override public String complete(String instructions,String input)throws Exception{return request(config,instructions,input,null);}
            @Override public String stream(String instructions,String input,java.util.function.Consumer<String> onText)throws Exception{return request(config,instructions,input,onText);}
        };
    }
    static Map<String,Object> body(Config c,String system,String input){
        Map<String,Object> out=new LinkedHashMap<>();out.put("model",c.model());
        if(c.provider()==Provider.CLAUDE){out.put("system",system);out.put("max_tokens",16000);out.put("messages",List.of(Map.of("role","user","content",input)));}
        else{out.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",input)));out.put(c.provider()==Provider.OPENAI?"max_completion_tokens":"max_tokens",16000);}
        return out;
    }
    static String request(Config config,String system,String input,java.util.function.Consumer<String> onText)throws Exception{
        long started=System.currentTimeMillis();String host=URI.create(config.endpoint()).getHost();
        Activity.log("→ "+config.provider()+" "+config.model()+" @ "+host+"  "+size(system.length()+input.length())+(onText!=null?"  streaming":""));
        try{
            HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(config.endpoint())).timeout(Duration.ofSeconds(240)).header("Content-Type","application/json");
            if(config.provider()==Provider.CLAUDE)builder.header("x-api-key",config.key()).header("anthropic-version","2023-06-01");
            else builder.header("Authorization","Bearer "+config.key());
            if(config.provider()==Provider.OPENROUTER)builder.header("X-Title","RHI Desktop");
            Map<String,Object> body=body(config,system,input);
            if(onText!=null){body.put("stream",true);if(config.provider()==Provider.OPENAI)body.put("stream_options",Map.of("include_usage",true));}
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<InputStream> response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(),HttpResponse.BodyHandlers.ofInputStream());
            String text;long[] usage={-1,-1};
            if(response.statusCode()/100!=2){
                try(InputStream stream=response.body()){stream.readNBytes(4096);}
                String reason=switch(response.statusCode()){case 401,403->"Check API key and model access.";case 429->"Rate limit or credit limit reached. Try later.";default->"Check endpoint and model ID in Settings.";};
                throw new IOException(config.provider()+" returned HTTP "+response.statusCode()+". "+reason);
            }
            if(response.headers().firstValue("content-type").orElse("").contains("event-stream")&&onText!=null){
                StringBuilder all=new StringBuilder();
                try(BufferedReader lines=new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8))){
                    String line;
                    while((line=lines.readLine())!=null){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedException("Stopped");
                        String delta=delta(config.provider(),line,usage);if(delta.isEmpty())continue;
                        if(all.length()==0)Activity.log("… first text after "+(System.currentTimeMillis()-started)/100/10.0+"s");all.append(delta);if(all.length()>2*1024*1024)throw new IOException("Provider response exceeded 2 MB.");onText.accept(delta);
                    }
                }catch(IOException e){if(Thread.currentThread().isInterrupted())throw new InterruptedException("Stopped");throw e;}
                text=all.toString();if(text.isBlank())throw new IOException("Provider returned no text. Select a text-capable model.");
            }else{
                byte[] bytes;try(InputStream stream=response.body()){bytes=stream.readNBytes(2*1024*1024+1);}
                if(bytes.length>2*1024*1024)throw new IOException("Provider response exceeded 2 MB.");
                String raw=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);readUsage(Json.map(Json.parse(raw)),usage);
                text=decode(config.provider(),raw);if(onText!=null)onText.accept(text);
            }
            Activity.log("← "+response.statusCode()+"  "+size(text.length())+"  "+(System.currentTimeMillis()-started)/100/10.0+"s"+(usage[0]>=0?"  tokens "+usage[0]+" in / "+Math.max(0,usage[1])+" out":""));
            return text;
        }catch(InterruptedException e){Activity.log("■ stopped after "+(System.currentTimeMillis()-started)/1000+"s");throw e;}
        catch(Exception e){Activity.log("✕ "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));throw e;}
    }
    private static String size(int chars){return chars<1000?chars+" chars":chars/1000+"k chars";}
    /** One line of a server-sent stream, in either wire format. */
    static String delta(Provider provider,String line,long[] usage){
        if(!line.startsWith("data:"))return "";String data=line.substring(5).strip();if(data.isEmpty()||data.equals("[DONE]"))return "";
        Map<?,?> event;try{event=Json.map(Json.parse(data));}catch(IllegalArgumentException e){return "";}
        readUsage(event,usage);if(event.get("message") instanceof Map<?,?> m)readUsage(m,usage);
        if(provider==Provider.CLAUDE)return "content_block_delta".equals(event.get("type"))?Json.text(Json.map(event.get("delta")).get("text")):"";
        if(event.get("choices") instanceof List<?> c&&!c.isEmpty())return Json.text(Json.map(Json.map(c.get(0)).get("delta")).get("content"));
        return "";
    }
    private static void readUsage(Map<?,?> event,long[] usage){
        if(!(event.get("usage") instanceof Map<?,?> u))return;
        for(String key:List.of("prompt_tokens","input_tokens"))if(u.get(key) instanceof Number n)usage[0]=n.longValue();
        for(String key:List.of("completion_tokens","output_tokens"))if(u.get(key) instanceof Number n)usage[1]=n.longValue();
    }
    static String decode(Provider provider,String json)throws IOException{
        Map<?,?> out=Json.map(Json.parse(json));StringBuilder result=new StringBuilder();
        if(provider==Provider.CLAUDE){Object blocks=out.get("content");if(blocks instanceof List)for(Object b:(List<?>)blocks){Map<?,?> block=Json.map(b);if("text".equals(block.get("type")))result.append(Json.text(block.get("text")));}}
        else{Object choices=out.get("choices");if(choices instanceof List&&!((List<?>)choices).isEmpty()){Map<?,?> choice=Json.map(((List<?>)choices).get(0));result.append(Json.text(Json.map(choice.get("message")).get("content")));}}
        if(result.toString().isBlank())throw new IOException("Provider returned no text. Select a text-capable model.");return result.toString();
    }
}
