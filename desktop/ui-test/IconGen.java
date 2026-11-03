import javax.imageio.ImageIO;import java.io.*;import java.nio.*;
/** Writes desktop/icon/rhi.png and rhi.ico. Run from desktop/. */
public class IconGen{public static void main(String[] a)throws Exception{ImageIO.write(Ui.icon(512),"png",new File("icon/rhi.png"));int[] s={16,24,32,48,64,128,256};byte[][] p=new byte[s.length][];for(int i=0;i<s.length;i++){var b=new ByteArrayOutputStream();ImageIO.write(Ui.icon(s[i]),"png",b);p[i]=b.toByteArray();}
ByteBuffer h=ByteBuffer.allocate(6+16*s.length).order(ByteOrder.LITTLE_ENDIAN);h.putShort((short)0).putShort((short)1).putShort((short)s.length);int off=h.capacity();for(int i=0;i<s.length;i++){h.put((byte)(s[i]%256)).put((byte)(s[i]%256)).put((byte)0).put((byte)0).putShort((short)1).putShort((short)32).putInt(p[i].length).putInt(off);off+=p[i].length;}
try(var o=new FileOutputStream("icon/rhi.ico")){o.write(h.array());for(byte[] x:p)o.write(x);}}}
