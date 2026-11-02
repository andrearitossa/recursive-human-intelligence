import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Trims values and removes blank rows while preserving the file's delimiter and quoting. */
final class Csv {
    static final long MAX_FILE=10L*1024*1024;

    /** Excel on many Windows locales saves ANSI text, so UTF-8 cannot be assumed. */
    static String read(Path file)throws IOException{
        if(Files.size(file)>MAX_FILE)throw new IOException("Choose a CSV smaller than 10 MB.");
        byte[] bytes=Files.readAllBytes(file);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException notUtf8){return new String(bytes,Charset.forName("windows-1252"));}
    }
    /** Picks the candidate occurring most outside quotes on the first non-blank line. */
    static char delimiter(String source){
        char best=',';int bestCount=0;
        for(char candidate:new char[]{',',';','\t'}){
            int count=0;boolean quoted=false,seen=false;
            for(int i=0;i<source.length();i++){char c=source.charAt(i);
                if(c=='"')quoted=!quoted;
                else if(!quoted&&(c=='\n'||c=='\r')){if(seen)break;}
                else{if(!Character.isWhitespace(c))seen=true;if(!quoted&&c==candidate)count++;}
            }
            if(count>bestCount){best=candidate;bestCount=count;}
        }
        return best;
    }
    static String clean(String source){
        if(source.startsWith("\uFEFF"))source=source.substring(1);
        char delimiter=delimiter(source);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<source.length();i++){char c=source.charAt(i);
            if(c=='"'){if(quoted&&i+1<source.length()&&source.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}
            else if(c==delimiter&&!quoted){row.add(cell.toString().strip());cell.setLength(0);}
            else if((c=='\r'||c=='\n')&&!quoted){if(c=='\r'&&i+1<source.length()&&source.charAt(i+1)=='\n')i++;row.add(cell.toString().strip());rows.add(row);row=new ArrayList<>();cell.setLength(0);}
            else cell.append(c);
        }
        if(quoted)throw new IllegalArgumentException("CSV contains an unclosed quoted field.");
        if(cell.length()>0||!row.isEmpty()){row.add(cell.toString().strip());rows.add(row);}
        List<String> out=new ArrayList<>();
        for(List<String> values:rows){
            if(values.stream().allMatch(String::isBlank))continue;
            List<String> encoded=new ArrayList<>();
            for(String value:values)encoded.add(value.indexOf(delimiter)>=0||value.indexOf('"')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0?'"'+value.replace("\"","\"\"")+'"':value);
            out.add(String.join(String.valueOf(delimiter),encoded));
        }
        return String.join("\r\n",out);
    }
}
