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
    interface Model {String complete(String instructions,String input)throws Exception;}
    static Model connect(Config config){config.validate();return (instructions,input)->request(config,instructions,input);}
    static Map<String,Object> body(Config c,String system,String input){
        Map<String,Object> out=new LinkedHashMap<>();out.put("model",c.model());
        if(c.provider()==Provider.CLAUDE){out.put("system",system);out.put("max_tokens",16000);out.put("messages",List.of(Map.of("role","user","content",input)));}
        else{out.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",input)));out.put(c.provider()==Provider.OPENAI?"max_completion_tokens":"max_tokens",16000);}
        return out;
    }
    static String request(Config config,String system,String input)throws Exception{
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(config.endpoint())).timeout(Duration.ofSeconds(120)).header("Content-Type","application/json");
        if(config.provider()==Provider.CLAUDE)builder.header("x-api-key",config.key()).header("anthropic-version","2023-06-01");
        else builder.header("Authorization","Bearer "+config.key());
        if(config.provider()==Provider.OPENROUTER)builder.header("X-Title","Loop Desktop");
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<InputStream> response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(Json.write(body(config,system,input)))).build(),HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;try(InputStream stream=response.body()){bytes=stream.readNBytes(2*1024*1024+1);}
        if(bytes.length>2*1024*1024)throw new IOException("Provider response exceeded 2 MB.");
        if(response.statusCode()/100!=2){String reason=switch(response.statusCode()){case 401,403->"Check API key and model access.";case 429->"Rate limit or credit limit reached. Try later.";default->"Check endpoint and model ID in Settings.";};throw new IOException(config.provider()+" returned HTTP "+response.statusCode()+". "+reason);}
        return decode(config.provider(),new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
    }
    static String decode(Provider provider,String json)throws IOException{
        Map<?,?> out=Json.map(Json.parse(json));StringBuilder result=new StringBuilder();
        if(provider==Provider.CLAUDE){Object blocks=out.get("content");if(blocks instanceof List)for(Object b:(List<?>)blocks){Map<?,?> block=Json.map(b);if("text".equals(block.get("type")))result.append(Json.text(block.get("text")));}}
        else{Object choices=out.get("choices");if(choices instanceof List&&!((List<?>)choices).isEmpty()){Map<?,?> choice=Json.map(((List<?>)choices).get(0));result.append(Json.text(Json.map(choice.get("message")).get("content")));}}
        if(result.toString().isBlank())throw new IOException("Provider returned no text. Select a text-capable model.");return result.toString();
    }
}
