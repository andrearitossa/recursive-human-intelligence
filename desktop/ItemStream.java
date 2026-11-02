import java.util.*;
import java.util.function.Consumer;

/** Hands over each complete object of a {"key":[{...},{...}]} answer as soon as its closing brace arrives. */
final class ItemStream {
    private final Consumer<Map<?,?>> out;
    private final StringBuilder current=new StringBuilder();
    private int depth;boolean inString,escaped;
    ItemStream(Consumer<Map<?,?>> out){this.out=out;}
    void accept(String text){
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(depth>=2)current.append(c);
            if(inString){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')inString=false;continue;}
            if(c=='"'&&depth>0)inString=true;
            else if(c=='{'){depth++;if(depth==2){current.setLength(0);current.append(c);}}
            else if(c=='}'){depth--;if(depth==1){try{out.accept(Json.map(Json.parse(current.toString())));}catch(IllegalArgumentException ignored){}}}
        }
    }
}
