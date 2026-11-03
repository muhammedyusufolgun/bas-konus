package tr.olgun.baskonus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/** LAN-only, user-approved ephemeral ECDH pairing; never asks for a password. */
public final class PairingCoordinator {
 public interface Listener { void paired(String host,byte[] key,String identity); void message(String text); }
 private static final int PORT=39573;
 private final Activity activity;
 private final Listener listener;
 private final ConcurrentHashMap<String,Pending> pending=new ConcurrentHashMap<>();
 private volatile DatagramSocket socket;
 private volatile boolean running;
 private Thread thread;
 private static final class Pending {
  PairingExchange exchange;
  byte[] remoteKey,remoteNonce,session;
  String remoteIdentity,host,requestId;
  boolean initiator,approved;
  long created=SystemClock.elapsedRealtime();
 }
 public PairingCoordinator(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
 private static String enc(byte[] bytes){return Base64.encodeToString(bytes,Base64.NO_WRAP|Base64.URL_SAFE);}
 private static byte[] dec(String s){return Base64.decode(s,Base64.NO_WRAP|Base64.URL_SAFE);}
 private static String identity() throws Exception {return enc(DeviceIdentity.getOrCreatePublicKey().getEncoded());}
 private static String fingerprint(byte[] key)throws Exception{
  byte[] hash=MessageDigest.getInstance("SHA-256").digest(key);
  StringBuilder b=new StringBuilder();
  for(int i=0;i<16;i++){if(i>0&&i%2==0)b.append("-");b.append(String.format(java.util.Locale.ROOT,"%02X",hash[i]&255));}
  return b.toString();
 }
 private static boolean first(byte[] a,byte[] b){
  for(int i=0;i<Math.min(a.length,b.length);i++){int x=a[i]&255,y=b[i]&255;if(x!=y)return x<y;}
  return a.length<b.length;
 }
 private String sas(Pending p)throws Exception{
  byte[] hash=MessageDigest.getInstance("SHA-256").digest(p.session);
  return String.format(java.util.Locale.ROOT,"%02X-%02X-%02X",hash[0]&255,hash[1]&255,hash[2]&255);
 }
 public void start(){
  if(running)return;running=true;
  thread=new Thread(()->{
   try(DatagramSocket ds=new DatagramSocket(null)){
    ds.setReuseAddress(true);ds.bind(new InetSocketAddress(PORT));ds.setSoTimeout(1000);socket=ds;
    while(running){
     try{
      byte[] buf=new byte[2048];DatagramPacket packet=new DatagramPacket(buf,buf.length);ds.receive(packet);
      String msg=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8);
      String host=packet.getAddress().getHostAddress();
      handle(host,msg);
     }catch(java.net.SocketTimeoutException ignored){}catch(Exception e){listener.message("Eşleşme paketi reddedildi");}
    }
   }catch(Exception e){listener.message("Eşleşme dinleyicisi başlatılamadı");}
  },"pairing-coordinator");thread.start();
 }
 public void stop(){running=false;if(socket!=null)socket.close();if(thread!=null)thread.interrupt();}
 private void send(String host,String msg)throws Exception{
  byte[] data=msg.getBytes(StandardCharsets.UTF_8);
  try(DatagramSocket ds=new DatagramSocket()){ds.send(new DatagramPacket(data,data.length,InetAddress.getByName(host),PORT));}
 }
 public void request(String host){
  new Thread(()->{
   try{
    Pending p=new Pending();p.initiator=true;p.host=host;p.exchange=new PairingExchange();
    byte[] nonce=p.exchange.nonce();p.requestId=enc(nonce);
    pending.put(host,p);
    send(host,"BKPAIR1|REQ|"+p.requestId+"|"+enc(p.exchange.publicKey())+"|"+enc(nonce)+"|"+identity());
    listener.message("Eşleşme isteği gönderildi: "+host);
   }catch(Exception e){listener.message("İstek gönderilemedi: "+e.getMessage());}
  },"pairing-request").start();
 }
 private void handle(String host,String message)throws Exception{
  String[] x=message.split("\\|",-1);
  if(x.length<3||!"BKPAIR1".equals(x[0])||x[2].length()>100)return;
  String type=x[1],id=x[2];
  if("REQ".equals(type)&&x.length==6){
   if(pending.containsKey(host))return;
   Pending p=new Pending();p.host=host;p.requestId=id;p.exchange=new PairingExchange();
   p.remoteKey=dec(x[3]);p.remoteNonce=dec(x[4]);p.remoteIdentity=fingerprint(dec(x[5]));
   if(!id.equals(enc(p.remoteNonce)))return;
   p.session=p.exchange.derive(p.remoteKey,p.remoteNonce,first(p.exchange.publicKey(),p.remoteKey));
   pending.put(host,p);
   activity.runOnUiThread(()->new AlertDialog.Builder(activity)
    .setTitle("Bas-Konuş eşleşme isteği")
    .setMessage("Cihaz: "+host+"\nKimlik: "+p.remoteIdentity+"\nGüvenlik karşılaştırma kodu: "+safeSas(p)+"\nKodu karşı telefondakiyle karşılaştırın. Eşleşmeyi kabul ediyor musunuz?")
    .setNegativeButton("Reddet",(d,w)->{pending.remove(host,p);reject(host,id);})
    .setPositiveButton("Onayla",(d,w)->{
     p.approved=true;
     new Thread(()->{try{send(host,"BKPAIR1|OFFER|"+id+"|"+enc(p.exchange.publicKey())+"|"+enc(p.exchange.nonce())+"|"+identity());}catch(Exception e){listener.message("Onay gönderilemedi");}}).start();
    }).setCancelable(false).show());
  }else if("OFFER".equals(type)&&x.length==6){
   Pending p=pending.get(host);
   if(p==null||!p.initiator||!p.requestId.equals(id)||SystemClock.elapsedRealtime()-p.created>120000)return;
   p.remoteKey=dec(x[3]);p.remoteNonce=dec(x[4]);p.remoteIdentity=fingerprint(dec(x[5]));
   p.session=p.exchange.derive(p.remoteKey,p.remoteNonce,first(p.exchange.publicKey(),p.remoteKey));
   activity.runOnUiThread(()->new AlertDialog.Builder(activity)
    .setTitle("Eşleşmeyi onayla")
    .setMessage("Cihaz: "+host+"\nKimlik: "+p.remoteIdentity+"\nGüvenlik karşılaştırma kodu: "+safeSas(p)+"\nKarşı cihazdaki kodla aynıysa onaylayın.")
    .setNegativeButton("Reddet",(d,w)->{pending.remove(host,p);reject(host,id);})
    .setPositiveButton("Onayla",(d,w)->{
     p.approved=true;
     new Thread(()->{try{send(host,"BKPAIR1|ACCEPT|"+id);}catch(Exception e){listener.message("Onay gönderilemedi");}}).start();
    }).setCancelable(false).show());
  }else if("ACCEPT".equals(type)&&x.length==3){
   Pending p=pending.get(host);
   if(p==null||p.initiator||!p.approved||!p.requestId.equals(id))return;
   commit(p);send(host,"BKPAIR1|CONFIRM|"+id);pending.remove(host,p);
  }else if("CONFIRM".equals(type)&&x.length==3){
   Pending p=pending.get(host);
   if(p==null||!p.initiator||!p.approved||!p.requestId.equals(id))return;
   commit(p);pending.remove(host,p);
  }else if("REJECT".equals(type)){pending.remove(host);listener.message("Eşleşme reddedildi");}
 }
 private String safeSas(Pending p){try{return sas(p);}catch(Exception e){return "Hata";}}
 private void reject(String host,String id){new Thread(()->{try{send(host,"BKPAIR1|REJECT|"+id);}catch(Exception ignored){}}).start();}
 private void commit(Pending p){
  try{
   String known=activity.getSharedPreferences("trusted_peer_hosts",Activity.MODE_PRIVATE).getString(p.host,"");
   if(!known.isEmpty()&&!known.equals(p.remoteIdentity)){listener.message("Cihaz kimliği değişti; eşleşme reddedildi");return;}
   activity.getSharedPreferences("trusted_peer_hosts",Activity.MODE_PRIVATE).edit().putString(p.host,p.remoteIdentity).apply();
   listener.paired(p.host,p.session.clone(),p.remoteIdentity);
  }catch(Exception e){listener.message("Eşleşme kaydedilemedi");}
 }
}
