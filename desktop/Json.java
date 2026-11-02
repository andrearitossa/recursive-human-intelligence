import java.util.*;

/** Small JSON reader for JSONL transcripts; no execution or external dependencies. */
final class Json {
    private final String source; private int pos;
    private Json(String source) { this.source = source; }
    static Object parse(String text) {
        Json reader = new Json(text);
        Object value = reader.value(0); reader.space();
        if (reader.pos != text.length()) throw new IllegalArgumentException("Trailing JSON");
        return value;
    }
    void space() { while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++; }
    char take() { if (pos >= source.length()) throw new IllegalArgumentException("Incomplete JSON"); return source.charAt(pos++); }
    Object value(int depth) {
        if(depth > 100) throw new IllegalArgumentException("JSON too deep");
        space(); char c = take();
        if(c == '"') return string();
        if(c == '{') {
            Map<String,Object> out = new LinkedHashMap<>(); space();
            if(pos < source.length() && source.charAt(pos)=='}') {pos++;return out;}
            while(true) {
                space(); if(take()!='"') throw new IllegalArgumentException("Expected key");
                String key=string(); space(); if(take()!=':') throw new IllegalArgumentException("Expected colon");
                out.put(key,value(depth+1));space();c=take(); if(c=='}')return out;
                if(c!=',')throw new IllegalArgumentException("Expected comma");
            }
        }
        if(c=='[') {
            List<Object> out=new ArrayList<>();space();
            if(pos<source.length()&&source.charAt(pos)==']'){pos++;return out;}
            while(true){out.add(value(depth+1));space();c=take();if(c==']')return out;if(c!=',')throw new IllegalArgumentException("Expected comma");}
        }
        int start=pos-1;while(pos<source.length()&&",]} \r\n\t".indexOf(source.charAt(pos))<0)pos++;
        String token=source.substring(start,pos);
        if(token.equals("null"))return null;if(token.equals("true"))return true;if(token.equals("false"))return false;
        return Double.valueOf(token);
    }
    String string() {
        StringBuilder out=new StringBuilder();
        while(true){char c=take();if(c=='"')return out.toString();if(c<' ')throw new IllegalArgumentException("Control character");
            if(c=='\\'){c=take();switch(c){
                case '"':case '\\':case '/':out.append(c);break;
                case 'b':out.append('\b');break;case 'f':out.append('\f');break;
                case 'n':out.append('\n');break;case 'r':out.append('\r');break;case 't':out.append('\t');break;
                case 'u':if(pos+4>source.length())throw new IllegalArgumentException("Unicode escape");out.append((char)Integer.parseInt(source.substring(pos,pos+4),16));pos+=4;break;
                default:throw new IllegalArgumentException("Invalid escape");
            }}else out.append(c);
        }
    }
    static String write(Object value) {
        if(value==null)return "null";
        if(value instanceof String){StringBuilder b=new StringBuilder("\"");for(char c:((String)value).toCharArray()){switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();}
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map){List<String> parts=new ArrayList<>();((Map<?,?>)value).forEach((k,v)->parts.add(write(k.toString())+":"+write(v)));return "{"+String.join(",",parts)+"}";}
        if(value instanceof Iterable){List<String> parts=new ArrayList<>();for(Object item:(Iterable<?>)value)parts.add(write(item));return "["+String.join(",",parts)+"]";}
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    static Map<?,?> map(Object object) {return object instanceof Map ? (Map<?,?>)object : Collections.emptyMap();}
    static String text(Object object) {return object instanceof String ? (String)object : "";}
}
