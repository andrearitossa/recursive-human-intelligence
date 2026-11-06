import java.util.*;
import java.util.regex.*;

/** Enough Markdown for agent transcripts, as escaped HTML. Images become links, so nothing is ever fetched. */
final class Markdown {
    private static final Pattern FENCE=Pattern.compile("^\\s*(```|~~~).*"),HEADING=Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$"),RULE=Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$"),ITEM=Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$"),QUOTE=Pattern.compile("^\\s*>\\s?(.*)$"),CELLS=Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");
    private static final Pattern LINK=Pattern.compile("!?\\[([^\\]]*)\\]\\((https?://[^\\s)]+)\\)"),BOLD=Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__"),ITALIC=Pattern.compile("(?<![\\w*])\\*([^*\\s](?:[^*]*[^*\\s])?)\\*(?![\\w*])|(?<![\\w_])_([^_\\s](?:[^_]*[^_\\s])?)_(?![\\w_])"),STRIKE=Pattern.compile("~~(.+?)~~");
    static String escape(String t){return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static String inline(String text){
        StringBuilder out=new StringBuilder();String[] parts=text.split("`",-1);boolean closed=parts.length%2==1;
        for(int i=0;i<parts.length;i++){
            boolean code=i%2==1&&(closed||i<parts.length-1);
            if(code)out.append("<code>").append(escape(parts[i])).append("</code>");
            else{if(i%2==1)out.append('`');out.append(spans(escape(parts[i])));}
        }
        return out.toString();
    }
    private static String spans(String t){
        t=LINK.matcher(t).replaceAll("<a href=\"$2\">$1</a>");
        t=BOLD.matcher(t).replaceAll(m->Matcher.quoteReplacement("<b>"+(m.group(1)!=null?m.group(1):m.group(2))+"</b>"));
        t=ITALIC.matcher(t).replaceAll(m->Matcher.quoteReplacement("<i>"+(m.group(1)!=null?m.group(1):m.group(2))+"</i>"));
        return STRIKE.matcher(t).replaceAll("<s>$1</s>");
    }
    private static List<String> cells(String line){String t=line.strip();if(t.startsWith("|"))t=t.substring(1);if(t.endsWith("|"))t=t.substring(0,t.length()-1);return Arrays.asList(t.split("(?<!\\\\)\\|",-1));}
    static String toHtml(String md){
        String[] lines=md.replace("\r\n","\n").split("\n",-1);StringBuilder out=new StringBuilder();List<String> para=new ArrayList<>();Deque<String> lists=new ArrayDeque<>();Deque<Integer> depth=new ArrayDeque<>();
        for(int i=0;i<lines.length;i++){
            String line=lines[i];Matcher m;
            if(FENCE.matcher(line).matches()){
                flush(para,out);close(lists,depth,out,-1);String mark=line.strip().substring(0,3);StringBuilder code=new StringBuilder();
                for(i++;i<lines.length&&!lines[i].strip().startsWith(mark);i++)code.append(escape(lines[i])).append('\n');
                out.append("<pre>").append(code).append("</pre>");continue;
            }
            if(line.isBlank()){flush(para,out);continue;}
            if(line.contains("|")&&i+1<lines.length&&CELLS.matcher(lines[i+1]).matches()&&lines[i+1].contains("-")){
                flush(para,out);close(lists,depth,out,-1);out.append("<table cellspacing=0 cellpadding=4 border=1><tr>");for(String c:cells(line))out.append("<th align=left>").append(inline(c.strip())).append("</th>");out.append("</tr>");
                for(i+=2;i<lines.length&&lines[i].contains("|")&&!lines[i].isBlank();i++){out.append("<tr>");for(String c:cells(lines[i]))out.append("<td>").append(inline(c.strip())).append("</td>");out.append("</tr>");}
                i--;out.append("</table>");continue;
            }
            if((m=HEADING.matcher(line)).matches()){flush(para,out);close(lists,depth,out,-1);int n=Math.min(6,m.group(1).length()+2);out.append("<h").append(n).append('>').append(inline(m.group(2))).append("</h").append(n).append('>');continue;}
            if(RULE.matcher(line).matches()){flush(para,out);close(lists,depth,out,-1);out.append("<hr>");continue;}
            if((m=ITEM.matcher(line)).matches()){
                flush(para,out);int indent=m.group(1).length();String tag=Character.isDigit(m.group(2).charAt(0))?"ol":"ul";
                close(lists,depth,out,indent);
                if(depth.isEmpty()||depth.peek()<indent||!lists.peek().equals(tag)){if(!depth.isEmpty()&&depth.peek()==indent)out.append("</").append(lists.pop()).append('>');if(!depth.isEmpty()&&depth.peek()==indent)depth.pop();out.append('<').append(tag).append('>');lists.push(tag);depth.push(indent);}
                out.append("<li>").append(inline(m.group(3))).append("</li>");continue;
            }
            if((m=QUOTE.matcher(line)).matches()){flush(para,out);close(lists,depth,out,-1);out.append("<blockquote>").append(inline(m.group(1))).append("</blockquote>");continue;}
            if(!lists.isEmpty()&&line.startsWith(" ")){out.append("<p>").append(inline(line.strip())).append("</p>");continue;}
            close(lists,depth,out,-1);para.add(inline(line.strip()));
        }
        flush(para,out);close(lists,depth,out,-1);return out.toString();
    }
    private static void flush(List<String> para,StringBuilder out){if(para.isEmpty())return;out.append("<p>").append(String.join("<br>",para)).append("</p>");para.clear();}
    private static void close(Deque<String> lists,Deque<Integer> depth,StringBuilder out,int to){while(!depth.isEmpty()&&depth.peek()>to){depth.pop();out.append("</").append(lists.pop()).append('>');}}
    /** One line of plain text for card summaries. */
    static String plain(String md){
        return md.replaceAll("(?ms)^\\s*```.*?^\\s*```","").replaceAll("(?m)^\\s*\\|.*$","").replaceAll("(?m)^\\s*(#{1,6}\\s+|[-*+]\\s+|>\\s?)","").replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)","$1").replaceAll("\\*\\*|__|~~|`","").replaceAll("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])","$1").replaceAll("\\s+"," ").strip();
    }
    private Markdown(){}
}
