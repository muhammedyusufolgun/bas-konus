package tr.olgun.baskonus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Foreground receiver for an approved local-network session. No microphone access. */
public final class BackgroundAudioService extends Service {
 private static final String CHANNEL="bas_konus_background";
 private volatile boolean running;
 private Thread worker;
 private DatagramSocket socket;
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(Build.VERSION.SDK_INT>=26){
   NotificationChannel channel=new NotificationChannel(CHANNEL,"Bas-Konuş bağlantısı",NotificationManager.IMPORTANCE_LOW);
   getSystemService(NotificationManager.class).createNotificationChannel(channel);
  }
  Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
  Notification notification=builder.setContentTitle("Bas-Konuş").setContentText("Arka planda ses alımı etkin")
   .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setOngoing(true).build();
  startForeground(71,notification);
  if(running)return START_STICKY;
  running=true;
  worker=new Thread(this::receive,"background-audio");
  worker.start();
  return START_STICKY;
 }
 private void receive(){
  AudioTrack track=null;
  try{
   SessionVault.Session session=SessionVault.load(this);
   if(session==null)return;
   Set<String> allowed=new HashSet<>(Arrays.asList(session.hosts.split(",")));
   int minimum=AudioTrack.getMinBufferSize(16000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(minimum<=0)return;
   track=new AudioTrack(AudioManager.STREAM_MUSIC,16000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,4096),AudioTrack.MODE_STREAM);
   track.play();
   socket=new DatagramSocket(null);
   socket.setReuseAddress(true);
   socket.bind(new InetSocketAddress(39571));
   socket.setSoTimeout(1000);
   byte[] buf=new byte[1400];
   while(running){
    try{
     DatagramPacket packet=new DatagramPacket(buf,buf.length);
     socket.receive(packet);
     String host=packet.getAddress().getHostAddress();
     if(!allowed.contains(host))continue;
     if(getSharedPreferences("trusted_peer_hosts",MODE_PRIVATE).getString(host,"").isEmpty())continue;
     if(packet.getLength()<29)continue;
     Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
     cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(session.key,"AES"),new GCMParameterSpec(128,packet.getData(),packet.getOffset(),12));
     byte[] audio=cipher.doFinal(packet.getData(),packet.getOffset()+12,packet.getLength()-12);
     if(audio.length<2||audio.length>640||audio.length%2!=0)continue;
     track.write(audio,0,audio.length);
    }catch(java.net.SocketTimeoutException ignored){}catch(Exception ignored){}
   }
  }catch(Exception ignored){}finally{
   if(socket!=null)socket.close();
   if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}
   stopSelf();
  }
 }
 @Override public void onDestroy(){
  running=false;
  if(socket!=null)socket.close();
  if(worker!=null)worker.interrupt();
  super.onDestroy();
 }
}
