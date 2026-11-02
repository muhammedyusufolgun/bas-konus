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
 private volatile int originalAudioMode=AudioManager.MODE_NORMAL;
 private volatile boolean originalSpeakerphone=false;
 private AudioManager audioManager;
 private Button connectButton;
 private Button talkButton;
 private android.graphics.drawable.GradientDrawable talkShape;
 private final java.util.Set<String> verifiedPeers=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
 private static final byte[] PING=new byte[]{66,75,80,49};
 private static final byte[] PONG=new byte[]{66,75,80,50};
 private final java.util.Map<String,byte[]> pendingChallenges=java.util.Collections.synchronizedMap(new java.util.HashMap<>());
 private volatile long verificationStartedAt=0;
 private volatile String selectedContactName="";

 private final java.util.Map<String,Long> verifiedAt=java.util.Collections.synchronizedMap(new java.util.HashMap<>());
 private static final long VERIFICATION_TTL_MS=300000;
 private final java.util.LinkedHashMap<String,Boolean> seenNonces=new java.util.LinkedHashMap<String,Boolean>(512,0.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<String,Boolean> e){return size()>2048;}};
 private final SecureRandom secureRandom=new SecureRandom();
 private volatile DatagramSocket socket;
 private Thread receiver, sender;
 private AudioRecord recorder;
 private final Object lock=new Object();
 private static final int REQ_AUDIO=11, REQ_CONTACT=12;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setStatusBarColor(Color.rgb(3,72,119));
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,35,32,30);root.setBackgroundColor(Color.rgb(248,250,252));
  TextView title=new TextView(this);title.setText("BAS-KONUŞ");title.setTextSize(26);title.setTextColor(Color.rgb(2,132,199));title.setGravity(Gravity.CENTER);root.addView(title);
  audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
  if(audioManager!=null){originalAudioMode=audioManager.getMode();originalSpeakerphone=audioManager.isSpeakerphoneOn();}
  LinearLayout pickerRow=new LinearLayout(this);pickerRow.setGravity(Gravity.CENTER);root.addView(pickerRow);
  Button pick=new Button(this);pick.setText("Kişi");pick.setTextAllCaps(false);
  pick.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(173,216,250)));
  pickerRow.addView(pick,new LinearLayout.LayoutParams(-2,-2));
  contact=new TextView(this);contact.setText("Kişi seçilmedi");contact.setGravity(Gravity.CENTER);contact.setPadding(0,18,0,18);root.addView(contact);
  ip=new EditText(this);secretInput=new EditText(this);
  Button connect=new Button(this);connectButton=connect;
  activity=new TextView(this);activity.setText("Son ses alımı: Henüz yok");activity.setGravity(Gravity.CENTER);root.addView(activity);
  status=new TextView(this);status.setText("Kişi seçerek yerel ağda eşleştirmeyi başlatın");status.setGravity(Gravity.CENTER);status.setPadding(0,20,0,20);root.addView(status);
  android.widget.FrameLayout center=new android.widget.FrameLayout(this);
  LinearLayout.LayoutParams centerParams=new LinearLayout.LayoutParams(-1,0,1f);root.addView(center,centerParams);
  Button talk=new Button(this);talkButton=talk;talk.setText("BAS\nKONUŞ");talk.setTextAllCaps(false);
  talk.setTextColor(Color.WHITE);talk.setTextSize(25);talk.setElevation(18f);
  talkShape=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(255,177,177),Color.rgb(225,105,105)});
  talkShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);talkShape.setStroke(5,Color.rgb(198,83,83));talk.setBackground(talkShape);
  android.widget.FrameLayout.LayoutParams talkParams=new android.widget.FrameLayout.LayoutParams(250,250,Gravity.CENTER);
  center.addView(talk,talkParams);
  root.setPadding(32,55,32,30);setContentView(root);
  SharedPreferences prefs=getSharedPreferences("connection",MODE_PRIVATE);
  String savedHost=prefs.getString("peer","");
  // Do not persist the shared secret in plaintext. Re-enter after each launch.
  if(!savedHost.isEmpty()){ip.setText(savedHost);peer=savedHost;status.setText("Önce Kişi düğmesinden eşleştirmeyi başlatın");}

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
  byte[] digest=MessageDigest.getInstance("SHA-256").digest(key);
  StringBuilder code=new StringBuilder();for(int i=0;i<4;i++){if(i>0)code.append("-");code.append(String.format(java.util.Locale.ROOT,"%02X",digest[i]));}
  final String checkCode=code.toString();
  new android.app.AlertDialog.Builder(this).setTitle("Eşleştirme güvenlik kodu")
   .setMessage("Her iki telefonda görünen kodun aynı olduğunu karşıdaki kişiyle doğrulayın:\n"+checkCode+"\nBu kod rehber kimliğini tek başına kanıtlamaz.")
   .setPositiveButton("Anladım",null).show();
  verifiedPeers.clear();verifiedAt.clear();pendingChallenges.clear();synchronized(seenNonces){seenNonces.clear();}
  prefs.edit().putString("peer",peer).apply();status.setText("Hazır: "+peers.size()+" alıcı");}catch(Exception e){peer="";peers=java.util.Collections.emptyList();sessionKey=null;status.setText("Geçerli IPv4 adreslerini ve anahtarı girin");}});
  talk.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){startTalk();setTalkColor(talking.get());return true;}if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){stopTalk();setTalkColor(false);return true;}return true;});
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
  startListening();
  startDiscoveryResponder();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==REQ_CONTACT&&result==RESULT_OK&&data!=null){Uri uri=data.getData();if(uri!=null){try(Cursor c=getContentResolver().query(uri,new String[]{ContactsContract.Contacts.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){selectedContactName=c.getString(0);contact.setText("Kişi: "+selectedContactName);askForPairingSecret();}}}}}
 private void setTalkColor(boolean active){
  if(talkShape==null)return;
  talkShape.setColor(active?Color.rgb(152,226,160):Color.rgb(244,152,152));
  talkShape.setStroke(5,active?Color.rgb(54,143,77):Color.rgb(198,83,83));
  talkButton.invalidate();
 }
 private void askForPairingSecret(){
  final EditText keyField=new EditText(this);keyField.setSingleLine(true);
  keyField.setHint("Her iki telefonda aynı anahtar (16+ karakter)");
  keyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
  new android.app.AlertDialog.Builder(this).setTitle("Yerel ağ eşleştirme")
   .setMessage("İki telefonda aynı güçlü anahtarı girin. Rehber adı cihazın kimliğini tek başına doğrulamaz. Aynı Wi-Fi üzerinde yalnızca bir başka cihaz bulunmalıdır.")
   .setView(keyField).setNegativeButton("Vazgeç",null)
   .setPositiveButton("Cihaz ara",(dialog,which)->{
    if(keyField.length()<16){show("Anahtar en az 16 karakter olmalı");return;}
    secretInput.setText(keyField.getText().toString());scanPeers();
   }).show();
 }
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
    int sent=0;
    for(InetAddress dest:destinations){try{ds.send(new DatagramPacket(query,query.length,dest,DISCOVERY_PORT));sent++;}catch(IOException ignored){}}
    if(sent==0)throw new IOException("Yayın paketleri gönderilemedi");
    java.util.HashSet<String> localAddresses=new java.util.HashSet<>();
    java.util.Enumeration<NetworkInterface> localIfaces=NetworkInterface.getNetworkInterfaces();
    while(localIfaces.hasMoreElements()){NetworkInterface ni=localIfaces.nextElement();java.util.Enumeration<InetAddress> own=ni.getInetAddresses();while(own.hasMoreElements())localAddresses.add(own.nextElement().getHostAddress());}
    long until=android.os.SystemClock.elapsedRealtime()+2500;
    byte[] buf=new byte[128];
    while(android.os.SystemClock.elapsedRealtime()<until){
     try{DatagramPacket response=new DatagramPacket(buf,buf.length);ds.receive(response);
      if("BASKONUS_HERE_V1".equals(new String(response.getData(),response.getOffset(),response.getLength(),"UTF-8"))){
       String candidate=response.getAddress().getHostAddress();if(!localAddresses.contains(candidate))found.add(candidate);
      }
     }catch(SocketTimeoutException ignored){}
    }
    runOnUiThread(()->{
     if(found.isEmpty()){status.setText("Cihaz bulunamadı. Aynı Wi-Fi ağı ve cihazların açık olması gerekir.");return;}
     String current=ip.getText().toString().trim();
     java.util.LinkedHashSet<String> all=new java.util.LinkedHashSet<>();
     for(String x:current.split(",")){if(!x.trim().isEmpty())all.add(x.trim());}
     if(found.size()!=1){status.setText("Birden fazla cihaz bulundu. Otomatik eşleştirme için ağda yalnızca bir başka cihaz açık olmalı.");return;}
     all.clear();all.addAll(found);ip.setText(String.join(",",all));
     connectButton.performClick();
     if(sessionKey!=null&&!peers.isEmpty())testPeers();
    });
   }catch(Exception e){show("Ağ taraması başarısız: "+e.getMessage());}
  },"discovery-scan").start();
 }
 private void testPeers(){
  if(sessionKey==null||peers.isEmpty()){show("Önce hedefleri ve ortak anahtarı ayarlayın");return;}
  if(talking.get()){show("Konuşma sırasında doğrulama yenilenemez");return;}
  verifiedPeers.clear();verifiedAt.clear();pendingChallenges.clear();verificationStartedAt=android.os.SystemClock.elapsedRealtime();
  new Thread(()->{
   try{
    DatagramSocket ds=socket;
    if(ds==null||ds.isClosed())throw new IOException("Dinleme soketi hazır değil");
    for(String host:peers){
     byte[] challenge=new byte[16];secureRandom.nextBytes(challenge);
     pendingChallenges.put(host,challenge);
     byte[] plain=new byte[20];System.arraycopy(PING,0,plain,0,4);System.arraycopy(challenge,0,plain,4,16);
     byte[] request=encrypt(plain,plain.length);
     InetAddress address=InetAddress.getByName(host);
     ds.send(new DatagramPacket(request,request.length,address,PORT));
    }
    show("Doğrulama isteği gönderildi; karşı telefonda da Kişi üzerinden eşleştirme yapılmalı.");
   }catch(Exception e){show("Doğrulama gönderilemedi: "+e.getMessage());}
  },"verify-peers").start();
 }
 private void startListening(){receiver=new Thread(()->{
  try{socket=new DatagramSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(PORT));socket.setSoTimeout(1000);
   int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
   AudioTrack track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096),AudioTrack.MODE_STREAM);
   applyAudioRoute();track.play();byte[] buf=new byte[1400];
   while(listening){try{DatagramPacket packet=new DatagramPacket(buf,buf.length);socket.receive(packet);
    if(!peers.isEmpty()&&sessionKey!=null&&peers.contains(packet.getAddress().getHostAddress())){try{byte[] plain=decrypt(packet.getData(),packet.getOffset(),packet.getLength());if(plain.length==20&&Arrays.equals(Arrays.copyOfRange(plain,0,4),PING)){verifiedPeers.add(packet.getAddress().getHostAddress());verifiedAt.put(packet.getAddress().getHostAddress(),android.os.SystemClock.elapsedRealtime());byte[] answer=plain.clone();System.arraycopy(PONG,0,answer,0,4);byte[] reply=encrypt(answer,answer.length);socket.send(new DatagramPacket(reply,reply.length,packet.getAddress(),packet.getPort()));continue;}if(plain.length==20&&Arrays.equals(Arrays.copyOfRange(plain,0,4),PONG)){String host=packet.getAddress().getHostAddress();byte[] expected=pendingChallenges.get(host);if(expected!=null&&android.os.SystemClock.elapsedRealtime()-verificationStartedAt<10000&&MessageDigest.isEqual(expected,Arrays.copyOfRange(plain,4,20))){pendingChallenges.remove(host);verifiedPeers.add(host);verifiedAt.put(host,android.os.SystemClock.elapsedRealtime());show("Anahtar ve yanıt doğrulandı: "+verifiedPeers.size()+"/"+peers.size()+" cihaz");}continue;}if(talking.get()||plain.length<2||plain.length>640||plain.length%2!=0||!isPeerVerified(packet.getAddress().getHostAddress()))continue;track.write(plain,0,plain.length);long now=android.os.SystemClock.elapsedRealtime();if(now-lastReceivedAt>1500){lastReceivedAt=now;runOnUiThread(()->activity.setText("Ses alınıyor: "+packet.getAddress().getHostAddress()));}}catch(Exception ignored){}}
   }catch(SocketTimeoutException ignored){}catch(Exception e){if(listening)show("Alım hatası: "+e.getMessage());}}
   track.stop();track.release();
  }catch(Exception e){show("Dinleme başlatılamadı: "+e.getMessage());}finally{if(socket!=null)socket.close();}
 });receiver.start();}
 private boolean isPeerVerified(String host){Long at=verifiedAt.get(host);return at!=null&&android.os.SystemClock.elapsedRealtime()-at<VERIFICATION_TTL_MS&&verifiedPeers.contains(host);}
 private void applyAudioRoute(){if(audioManager==null)return;audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);audioManager.setSpeakerphoneOn(speakerEnabled);}
 private void startTalk(){if(peers.isEmpty()||sessionKey==null){show("Önce IP ve eşleştirme anahtarını ayarlayın");return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}
  boolean anyVerified=false;for(String host:peers){if(isPeerVerified(host)){anyVerified=true;break;}}
  if(!anyVerified){show("Bağlantı doğrulaması yok veya 5 dakika doldu; yeniden doğrulayın");return;}
  if(!transmitting.compareAndSet(false,true))return;
  applyAudioRoute();
  talking.set(true);show("Konuşuyorsunuz...");
  sender=new Thread(()->{AudioRecord rec=null;DatagramSocket out=null;try{
   int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   rec=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));
   if(rec.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Mikrofon açılamadı");
   synchronized(lock){recorder=rec;}out=new DatagramSocket();java.util.List<InetAddress> targets=new java.util.ArrayList<>();for(String host:peers)if(isPeerVerified(host))targets.add(InetAddress.getByName(host));byte[] data=new byte[640];rec.startRecording();
   while(talking.get()){int n=rec.read(data,0,data.length);if(n>0){byte[] encrypted=encrypt(data,n);int delivered=0;for(InetAddress address:targets){if(isPeerVerified(address.getHostAddress())){out.send(new DatagramPacket(encrypted,encrypted.length,address,PORT));delivered++;}}if(delivered==0){show("Tüm bağlantı doğrulamalarının süresi doldu");break;}}else if(n<0){if(!talking.get())break;throw new IOException("Mikrofon okuma hatası: "+n);}}
  }catch(Exception e){show("Gönderim hatası: "+e.getMessage());}finally{talking.set(false);if(rec!=null){try{if(rec.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)rec.stop();}catch(Exception ignored){}rec.release();}synchronized(lock){recorder=null;}if(out!=null)out.close();transmitting.set(false);}});
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
 private void stopTalk(){boolean wasTalking=talking.getAndSet(false);synchronized(lock){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}if(wasTalking)show("Dinlemede");}
 private void show(String s){runOnUiThread(()->{if(!isFinishing())status.setText(s);});}
 @Override protected void onDestroy(){listening=false;stopTalk();if(socket!=null)socket.close();if(discoverySocket!=null)discoverySocket.close();if(audioManager!=null){audioManager.setSpeakerphoneOn(originalSpeakerphone);audioManager.setMode(originalAudioMode);}super.onDestroy();}
}
