import java.awt.*;
import java.util.List;

/** Matches the system title bar to the app. Windows 11 only; elsewhere the normal bar simply stays. */
final class TitleBar {
    private static final String SCRIPT="""
        Add-Type 'using System;using System.Runtime.InteropServices;public class RhiDwm{[DllImport("dwmapi.dll")]public static extern int DwmSetWindowAttribute(IntPtr h,int a,ref int v,int s);}';
        $h=[IntPtr]::Zero;for($i=0;$i -lt 20 -and $h -eq [IntPtr]::Zero;$i++){$h=(Get-Process -Id PID).MainWindowHandle;if($h -eq [IntPtr]::Zero){Start-Sleep -Milliseconds 150}}
        $c=0x00F2F7FA;$t=0x001A161C;$b=0x00DADEE1;
        $r=[RhiDwm]::DwmSetWindowAttribute($h,35,[ref]$c,4);[void][RhiDwm]::DwmSetWindowAttribute($h,36,[ref]$t,4);[void][RhiDwm]::DwmSetWindowAttribute($h,34,[ref]$b,4);
        [Console]::Out.Write($r)
        """;
    static void style(){
        if(!KeyStore.WINDOWS)return;
        Thread t=new Thread(()->{
            try{
                Process p=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",java.util.Base64.getEncoder().encodeToString(SCRIPT.replace("PID",Long.toString(ProcessHandle.current().pid())).getBytes(java.nio.charset.StandardCharsets.UTF_16LE))).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                p.getOutputStream().close();
                String out=new String(p.getInputStream().readAllBytes()).strip();
                Activity.log(out.endsWith("0")?"Title bar styled":"Title bar left as is (needs Windows 11)");
            }catch(Exception e){Activity.log("Title bar left as is");}
        },"title-bar");t.setDaemon(true);t.start();
    }
    private TitleBar(){}
}
