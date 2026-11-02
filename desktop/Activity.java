import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** What RHI is doing, for the logs panel. Sizes and timings only: never keys or chat text. */
final class Activity {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> lines=new ArrayDeque<>();
    private static Consumer<String> listener;
    static synchronized void log(String line){
        String stamped=TIME.format(LocalTime.now())+"  "+line;
        lines.addLast(stamped);if(lines.size()>2000)lines.removeFirst();
        if(listener!=null)listener.accept(stamped);
    }
    static synchronized void listen(Consumer<String> l){listener=l;for(String line:lines)l.accept(line);}
    private Activity(){}
}
