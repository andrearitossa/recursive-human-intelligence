import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * API keys in the operating system's own protection, with no dependencies:
 * Windows DPAPI, macOS Keychain, and the Linux Secret Service (secret-tool).
 * Keys travel over stdin, so they never appear in a process list.
 */
final class KeyStore {
    private static final String OS=System.getProperty("os.name").toLowerCase(Locale.ROOT);
    static final boolean WINDOWS=OS.contains("win"),MAC=OS.contains("mac");
    private static final String SERVICE="RHI";
    static boolean available(){
        if(WINDOWS)return true;
        return run(MAC?List.of("which","security"):List.of("which","secret-tool"),"")!=null;
    }
    static String name(){return WINDOWS?"Windows":MAC?"your Keychain":"your system keyring";}
    private static Path file(String account){return Path.of(System.getProperty("user.home"),".loop","keys",account+".dpapi");}
    static boolean save(String account,String key){
        if(key.contains("\"")||key.contains("\n"))return false;
        if(WINDOWS){
            String out=run(List.of("powershell.exe","-NoProfile","-NonInteractive","-Command","Add-Type -AssemblyName System.Security;$k=[Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd());[Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($k,$null,'CurrentUser')))"),key);
            if(out==null||out.isBlank())return false;
            try{Files.createDirectories(file(account).getParent());Files.writeString(file(account),out.strip());return true;}catch(IOException e){return false;}
        }
        if(MAC)return run(List.of("security","-i"),"add-generic-password -U -a \""+account+"\" -s \""+SERVICE+"\" -w \""+key+"\"\n")!=null;
        return run(List.of("secret-tool","store","--label=RHI API key","service",SERVICE,"account",account),key)!=null;
    }
    static String load(String account){
        String out;
        if(WINDOWS){
            if(!Files.exists(file(account)))return "";
            try{out=run(List.of("powershell.exe","-NoProfile","-NonInteractive","-Command","Add-Type -AssemblyName System.Security;$b=[Convert]::FromBase64String([Console]::In.ReadToEnd());[Console]::Out.Write([Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($b,$null,'CurrentUser')))"),Files.readString(file(account)));}catch(IOException e){return "";}
        }else if(MAC)out=run(List.of("security","find-generic-password","-a",account,"-s",SERVICE,"-w"),"");
        else out=run(List.of("secret-tool","lookup","service",SERVICE,"account",account),"");
        return out==null?"":out.strip();
    }
    static void delete(String account){
        if(WINDOWS){try{Files.deleteIfExists(file(account));}catch(IOException ignored){}return;}
        if(MAC)run(List.of("security","delete-generic-password","-a",account,"-s",SERVICE),"");
        else run(List.of("secret-tool","clear","service",SERVICE,"account",account),"");
    }
    /** Stdout on success, null on any failure. A store that cannot be reached must never break the app. */
    private static String run(List<String> command,String stdin){
        try{
            Process p=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try(OutputStream in=p.getOutputStream()){in.write(stdin.getBytes(StandardCharsets.UTF_8));}
            byte[] out=p.getInputStream().readAllBytes();
            if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();return null;}
            return p.exitValue()==0?new String(out,StandardCharsets.UTF_8):null;
        }catch(Exception e){return null;}
    }
    private KeyStore(){}
}
