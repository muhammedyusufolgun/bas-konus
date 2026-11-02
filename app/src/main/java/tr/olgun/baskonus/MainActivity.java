package tr.olgun.baskonus;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
 private static final int PORT=39571, DISCOVERY_PORT=39572, RATE=16000;
 private DatagramSocket discoverySocket;
 private Thread discoveryThread;
 private final AtomicBoolean talking=new AtomicBoolean(false);
 private final AtomicBoolean transmitting=new AtomicBoolean(false);
 private volatile boolean listening=true;
 private volatile String peer="";
 private volatile java.util.List<String> peers=java.util.Collections.emptyList();
 private TextView status, contact;
 private EditText ip, secretInput;
 private volatile byte[] sessionKey;
 private volatile long lastReceivedAt=0;
 private TextView activity;
 private volatile boolean speakerEnabled=true;
 private AudioManager audioManager;
 private final java.util.Set<String> verifiedPeers=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
 private static final byte[] PING=new byte[]{66,75,80,49};
 private static final byte[] PONG=new byte[]{66,75,80,50};
 private final java.util.LinkedHashMap<String,Boolean> seenNonces=new java.util.LinkedHashMap<String,Boolean>(512,0.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<String,Boolean> e){return size()>2048;}};
 private final SecureRandom secureRandom=new SecureRandom();
 private DatagramSocket socket;
 private Thread receiver, sender;
 private AudioRecord recorder;
 private final Object lock=new Object();
 private static final int REQ_AUDIO=11, REQ_CONTACT=12;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setStatusBarColor(Color.rgb(3,72,119));
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,35,32,30);root.setBackgroundColor(Color.rgb(248,250,252));
  TextView title=new TextView(this);title.setText("BAS-KONUŞ");title.setTextSize(26);title.setTextColor(Color.rgb(2,132,199));title.setGravity(Gravity.CENTER);root.addView(title);
  TextView info=new TextView(this);info.setText("İlk sürüm: Aynı ağdaki cihazlar için doğrudan ses bağlantısı. Her iki telefonda uygulama açık olmalıdır.");info.setPadding(0,20,0,20);root.addView(info);
  audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
  Button speaker=new Button(this);speaker.setText("Ses çıkışı: Hoparlör");root.addView(speaker);
  speaker.setOnClickListener(v->{speakerEnabled=!speakerEnabled;applyAudioRoute();speaker.setText(speakerEnabled?"Ses çıkışı: Hoparlör":"Ses çıkışı: Ahize");});
  Button pick=new Button(this);pick.setText("Rehberden kişi seç");root.addView(pick);
  contact=new TextView(this);contact.setText("Kişi seçilmedi");root.addView(contact);
  ip=new EditText(this);ip.setSingleLine(true);ip.setHint("Alıcı IP adresleri, virgülle ayırın (ör. 192.168.1.20,192.168.1.21)");ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT);root.addView(ip);
  secretInput=new EditText(this);secretInput.setSingleLine(true);secretInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);secretInput.setHint("Her iki telefonda aynı güçlü eşleştirme anahtarı (en az 16 karakter)");root.addView(secretInput);
  Button scan=new Button(this);scan.setText("Yerel ağda cihaz ara");root.addView(scan);
  scan.setOnClickListener(v->scanPeers());
  Button test=new Button(this);test.setText("Eşleştirme bağlantısını doğrula");root.addView(test);
  test.setOnClickListener(v->testPeers());
  Button connect=new Button(this);connect.setText("Hedefi ayarla");root.addView(connect);
  activity=new TextView(this);activity.setText("Son ses alımı: Henüz yok");activity.setPadding(0,8,0,8);root.addView(activity);
  status=new TextView(this);status.setText("Hazır değil");status.setPadding(0,15,0,15);root.addView(status);
  Button talk=new Button(this);talk.setText("BASILI TUT VE KONUŞ");talk.setTextColor(Color.WHITE);talk.setTextSize(20);talk.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(2,132,199)));root.addView(talk,new LinearLayout.LayoutParams(-1,200));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
  SharedPreferences prefs=getSharedPreferences("connection",MODE_PRIVATE);
  String savedHost=prefs.getString("peer","");
  // Do not persist the shared secret in plaintext. Re-enter after each launch.
  if(!savedHost.isEmpty()){ip.setText(savedHost);peer=savedHost;status.setText("Kayıtlı hedefler: "+peer+" (anahtarı tekrar girin)");}
  WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
  if(wifi!=null&&wifi.getConnectionInfo()!=null){info.append("\nBu cihazın yerel IP adresi: "+Formatter.formatIpAddress(wifi.getConnectionInfo().getIpAddress()));}
  pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_PICK,ContactsContract.Contacts.CONTENT_URI);startActivityForResult(i,REQ_CONTACT);});
  connect.setOnClickListener(v->{if(talking.get()){show("Konuşurken hedef değiştirilemez");return;}String host=ip.getText().toString().trim();try{String secret=secretInput.getText().toString();if(secret.length()<16){status.setText("Eşleştirme anahtarı en az 16 karakter olmalı");return;}byte[] key=deriveKey(secret);java.util.LinkedHashSet<String> addresses=new java.util.LinkedHashSet<>();
  for(String raw:host.split(",")){String h=raw.trim();if(h.isEmpty())continue;
   if(!h.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))throw new Exception("IPv4 required");
   InetAddress a=InetAddress.getByName(h);
   if(a.isAnyLocalAddress()||a.isMulticastAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||!(a instanceof Inet4Address))throw new Exception("Invalid address");
   addresses.add(a.getHostAddress());if(addresses.size()>20)throw new Exception("Too many recipients");
  }
  if(addresses.isEmpty())throw new Exception("No recipients");
  peers=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(addresses));
  peer=String.join(",",addresses);sessionKey=key;
  verifiedPeers.clear();synchronized(seenNonces){seenNonces.clear();}
  prefs.edit().putString("peer",peer).apply();status.setText("Hazır: "+peers.size()+" alıcı");}catch(Exception e){peer="";peers=java.util.Collections.emptyList();sessionKey=null;status.setText("Geçerli IPv4 adreslerini ve anahtarı girin");}});
  talk.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){startTalk();return true;}if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){stopTalk();return true;}return true;});
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
  startListening();
  startDiscoveryResponder();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==REQ_CONTACT&&result==RESULT_OK&&data!=null){Uri uri=data.getData();if(uri!=null){try(Cursor c=getContentResolver().query(uri,new String[]{ContactsContract.Contacts.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())contact.setText("Kişi: "+c.getString(0));}}}}
 private void startDiscoveryResponder(){
  discoveryThread=new Thread(()->{
   try(DatagramSocket ds=new DatagramSocket(null)){
    ds.setReuseAddress(true);ds.bind(new InetSocketAddress(DISCOVERY_PORT));
    ds.setSoTimeout(1000);discoverySocket=ds;
    byte[] buf=new byte[128];
    while(listening){
     try{
      DatagramPacket p=new DatagramPacket(buf,buf.length);ds.receive(p);
      if("BASKONUS_DISCOVER_V1".equals(new String(p.getData(),p.getOffset(),p.getLength(),"UTF-8"))){
       byte[] reply="BASKONUS_HERE_V1".getBytes("UTF-8");
       ds.send(new DatagramPacket(reply,reply.length,p.getAddress(),p.getPort()));
      }
     }catch(SocketTimeoutException ignored){}catch(Exception e){if(listening)show("Keşif yanıtı hatası");}
    }
   }catch(Exception e){if(listening)show("Keşif portu açılamadı");}
  },"discovery-responder");discoveryThread.start();
 }
 private void scanPeers(){
  show("Yerel ağda cihazlar aranıyor...");
  new Thread(()->{
   java.util.LinkedHashSet<String> found=new java.util.LinkedHashSet<>();
   try(DatagramSocket ds=new DatagramSocket()){
    ds.setBroadcast(true);ds.setSoTimeout(450);
    byte[] query="BASKONUS_DISCOVER_V1".getBytes("UTF-8");
    java.util.ArrayList<InetAddress> destinations=new java.util.ArrayList<>();
    destinations.add(InetAddress.getByName("255.255.255.255"));
    java.util.Enumeration<NetworkInterface> ifaces=NetworkInterface.getNetworkInterfaces();
    while(ifaces.hasMoreElements()){
     NetworkInterface ni=ifaces.nextElement();
     if(!ni.isUp()||ni.isLoopback())continue;
     for(InterfaceAddress ia:ni.getInterfaceAddresses()){
      if(ia.getBroadcast()!=null)destinations.add(ia.getBroadcast());
     }
    }
    for(InetAddress dest:destinations)ds.send(new DatagramPacket(query,query.length,dest,DISCOVERY_PORT));
    long until=android.os.SystemClock.elapsedRealtime()+2500;
    byte[] buf=new byte[128];
    while(android.os.SystemClock.elapsedRealtime()<until){
     try{DatagramPacket response=new DatagramPacket(buf,buf.length);ds.receive(response);
      if("BASKONUS_HERE_V1".equals(new String(response.getData(),response.getOffset(),response.getLength(),"UTF-8"))){
       found.add(response.getAddress().getHostAddress());
      }
     }catch(SocketTimeoutException ignored){}
    }
    runOnUiThread(()->{
     if(found.isEmpty()){status.setText("Cihaz bulunamadı. Aynı Wi-Fi ağı ve cihazların açık olması gerekir.");return;}
     String current=ip.getText().toString().trim();
     java.util.LinkedHashSet<String> all=new java.util.LinkedHashSet<>();
     for(String x:current.split(",")){if(!x.trim().isEmpty())all.add(x.trim());}
     all.addAll(found);ip.setText(String.join(",",all));
     status.setText(found.size()+" cihaz bulundu. Ortak anahtarı girip Hedefi ayarla seçin.");
    });
   }catch(Exception e){show("Ağ taraması başarısız: "+e.getMessage());}
  },"discovery-scan").start();
 }
 private void testPeers(){
  if(sessionKey==null||peers.isEmpty()){show("Önce hedefleri ve ortak anahtarı ayarlayın");return;}
  if(talking.get()){show("Konuşma sırasında doğrulama yenilenemez");return;}
  verifiedPeers.clear();
  new Thread(()->{
   try{
    DatagramSocket ds=socket;
    if(ds==null||ds.isClosed())throw new IOException("Dinleme soketi hazır değil");
    byte[] request=encrypt(PING,PING.length);
    for(String host:peers){
     InetAddress address=InetAddress.getByName(host);
     ds.send(new DatagramPacket(request,request.length,address,PORT));
    }
    show("Şifreli doğrulama gönderildi. Her iki cihazda hedef ve aynı anahtar ayarlı olmalı.");
   }catch(Exception e){show("Doğrulama gönderilemedi: "+e.getMessage());}
  },"verify-peers").start();
 }
 private void startListening(){receiver=new Thread(()->{
  try{socket=new DatagramSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(PORT));socket.setSoTimeout(1000);
   int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
   AudioTrack track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096),AudioTrack.MODE_STREAM);
   applyAudioRoute();track.play();byte[] buf=new byte[1400];
   while(listening){try{DatagramPacket packet=new DatagramPacket(buf,buf.length);socket.receive(packet);
    if(!peers.isEmpty()&&sessionKey!=null&&peers.contains(packet.getAddress().getHostAddress())){try{byte[] plain=decrypt(packet.getData(),packet.getOffset(),packet.getLength());if(Arrays.equals(plain,PING)){verifiedPeers.add(packet.getAddress().getHostAddress());byte[] reply=encrypt(PONG,PONG.length);socket.send(new DatagramPacket(reply,reply.length,packet.getAddress(),packet.getPort()));continue;}if(Arrays.equals(plain,PONG)){verifiedPeers.add(packet.getAddress().getHostAddress());show("Anahtar doğrulandı: "+verifiedPeers.size()+"/"+peers.size()+" cihaz");continue;}if(talking.get()||plain.length!=640||!verifiedPeers.contains(packet.getAddress().getHostAddress()))continue;track.write(plain,0,plain.length);long now=android.os.SystemClock.elapsedRealtime();if(now-lastReceivedAt>1500){lastReceivedAt=now;runOnUiThread(()->activity.setText("Ses alınıyor: "+packet.getAddress().getHostAddress()));}}catch(Exception ignored){}}
   }catch(SocketTimeoutException ignored){}catch(Exception e){if(listening)show("Alım hatası: "+e.getMessage());}}
   track.stop();track.release();
  }catch(Exception e){show("Dinleme başlatılamadı: "+e.getMessage());}finally{if(socket!=null)socket.close();}
 });receiver.start();}
 private void applyAudioRoute(){if(audioManager==null)return;audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);audioManager.setSpeakerphoneOn(speakerEnabled);}
 private void startTalk(){if(peers.isEmpty()||sessionKey==null){show("Önce IP ve eşleştirme anahtarını ayarlayın");return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}
  if(verifiedPeers.isEmpty()){show("Önce eşleştirme bağlantısını doğrulayın");return;}
  if(!transmitting.compareAndSet(false,true))return;
  applyAudioRoute();
  talking.set(true);show("Konuşuyorsunuz...");
  sender=new Thread(()->{AudioRecord rec=null;DatagramSocket out=null;try{
   int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   rec=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));
   if(rec.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Mikrofon açılamadı");
   synchronized(lock){recorder=rec;}out=new DatagramSocket();java.util.List<InetAddress> targets=new java.util.ArrayList<>();for(String host:peers)if(verifiedPeers.contains(host))targets.add(InetAddress.getByName(host));byte[] data=new byte[640];rec.startRecording();
   while(talking.get()){int n=rec.read(data,0,data.length);if(n>0){byte[] encrypted=encrypt(data,n);for(InetAddress address:targets)out.send(new DatagramPacket(encrypted,encrypted.length,address,PORT));}}
  }catch(Exception e){show("Gönderim hatası: "+e.getMessage());}finally{talking.set(false);if(rec!=null){try{if(rec.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)rec.stop();}catch(Exception ignored){}rec.release();}synchronized(lock){recorder=null;}if(out!=null)out.close();transmitting.set(false);show("Dinlemede");}});
  sender.start();
 }
 private byte[] deriveKey(String password)throws Exception {
  // A fixed application-domain salt is not a substitute for a unique pairing exchange.
  PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),"BasKonus-LAN-v2".getBytes("UTF-8"),120000,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
  finally{spec.clearPassword();}
 }
 private byte[] encrypt(byte[] input,int length)throws Exception {
  byte[] nonce=new byte[12];secureRandom.nextBytes(nonce);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(sessionKey,"AES"),new GCMParameterSpec(128,nonce));
  byte[] ciphertext=cipher.doFinal(input,0,length);
  byte[] packet=new byte[nonce.length+ciphertext.length];System.arraycopy(nonce,0,packet,0,nonce.length);System.arraycopy(ciphertext,0,packet,nonce.length,ciphertext.length);return packet;
 }
 private byte[] decrypt(byte[] input,int offset,int length)throws Exception {
  if(length<29)throw new IOException("Invalid packet");
  byte[] nonce=Arrays.copyOfRange(input,offset,offset+12);
  String nonceId=java.util.Base64.getEncoder().encodeToString(nonce);
  synchronized(seenNonces){if(seenNonces.containsKey(nonceId))throw new IOException("Replay packet");}
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(sessionKey,"AES"),new GCMParameterSpec(128,nonce));
  byte[] plain=cipher.doFinal(input,offset+12,length-12);
  synchronized(seenNonces){if(seenNonces.containsKey(nonceId))throw new IOException("Replay packet");seenNonces.put(nonceId,true);}
  return plain;
 }
 private void stopTalk(){talking.set(false);synchronized(lock){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}}
 private void show(String s){runOnUiThread(()->{if(!isFinishing())status.setText(s);});}
 @Override protected void onDestroy(){listening=false;stopTalk();if(socket!=null)socket.close();if(discoverySocket!=null)discoverySocket.close();if(audioManager!=null){audioManager.setSpeakerphoneOn(false);audioManager.setMode(AudioManager.MODE_NORMAL);}super.onDestroy();}
}
