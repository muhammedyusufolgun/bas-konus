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
import android.widget.TextView;
import android.graphics.Color;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
 private static final int PORT=39571, RATE=16000;
 private final AtomicBoolean talking=new AtomicBoolean(false);
 private volatile boolean listening=true;
 private volatile String peer="";
 private TextView status, contact;
 private EditText ip;
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
  Button pick=new Button(this);pick.setText("Rehberden kişi seç");root.addView(pick);
  contact=new TextView(this);contact.setText("Kişi seçilmedi");root.addView(contact);
  ip=new EditText(this);ip.setSingleLine(true);ip.setHint("Karşı telefonun yerel IP adresi (ör. 192.168.1.20)");ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT);root.addView(ip);
  Button connect=new Button(this);connect.setText("Hedefi ayarla");root.addView(connect);
  status=new TextView(this);status.setText("Hazır değil");status.setPadding(0,15,0,15);root.addView(status);
  Button talk=new Button(this);talk.setText("BASILI TUT VE KONUŞ");talk.setTextColor(Color.WHITE);talk.setTextSize(20);talk.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(2,132,199)));root.addView(talk,new LinearLayout.LayoutParams(-1,200));
  setContentView(root);
  SharedPreferences prefs=getSharedPreferences("connection",MODE_PRIVATE);
  String savedHost=prefs.getString("peer","");
  if(!savedHost.isEmpty()){ip.setText(savedHost);peer=savedHost;status.setText("Hedef kayıtlı: "+peer);}
  WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
  if(wifi!=null&&wifi.getConnectionInfo()!=null){info.append("\\nBu cihazın yerel IP adresi: "+Formatter.formatIpAddress(wifi.getConnectionInfo().getIpAddress()));}
  pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_PICK,ContactsContract.Contacts.CONTENT_URI);startActivityForResult(i,REQ_CONTACT);});
  connect.setOnClickListener(v->{String host=ip.getText().toString().trim();try{InetAddress a=InetAddress.getByName(host);if(a.isAnyLocalAddress()||a.isMulticastAddress())throw new Exception();peer=a.getHostAddress();prefs.edit().putString("peer",peer).apply();status.setText("Hedef: "+peer);}catch(Exception e){peer="";status.setText("Geçerli IP girin");}});
  talk.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){startTalk();return true;}if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){stopTalk();return true;}return true;});
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
  startListening();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==REQ_CONTACT&&result==RESULT_OK&&data!=null){Uri uri=data.getData();if(uri!=null){try(Cursor c=getContentResolver().query(uri,new String[]{ContactsContract.Contacts.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())contact.setText("Kişi: "+c.getString(0));}}}}
 private void startListening(){receiver=new Thread(()->{
  try{socket=new DatagramSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(PORT));socket.setSoTimeout(1000);
   int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
   AudioTrack track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096),AudioTrack.MODE_STREAM);
   track.play();byte[] buf=new byte[1400];
   while(listening){try{DatagramPacket packet=new DatagramPacket(buf,buf.length);socket.receive(packet);
    if(!peer.isEmpty()&&packet.getAddress().getHostAddress().equals(peer)&&!talking.get())track.write(packet.getData(),packet.getOffset(),packet.getLength());
   }catch(SocketTimeoutException ignored){}catch(Exception e){if(listening)show("Alım hatası: "+e.getMessage());}}
   track.stop();track.release();
  }catch(Exception e){show("Dinleme başlatılamadı: "+e.getMessage());}finally{if(socket!=null)socket.close();}
 });receiver.start();}
 private void startTalk(){if(peer.isEmpty()){show("Önce karşı telefonun IP adresini girin");return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}
  if(!talking.compareAndSet(false,true))return;show("Konuşuyorsunuz...");
  sender=new Thread(()->{AudioRecord rec=null;DatagramSocket out=null;try{
   int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   rec=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));
   if(rec.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Mikrofon açılamadı");
   synchronized(lock){recorder=rec;}out=new DatagramSocket();InetAddress address=InetAddress.getByName(peer);byte[] data=new byte[640];rec.startRecording();
   while(talking.get()){int n=rec.read(data,0,data.length);if(n>0)out.send(new DatagramPacket(data,n,address,PORT));}
  }catch(Exception e){show("Gönderim hatası: "+e.getMessage());}finally{talking.set(false);if(rec!=null){try{if(rec.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)rec.stop();}catch(Exception ignored){}rec.release();}synchronized(lock){recorder=null;}if(out!=null)out.close();show("Dinlemede");}});
  sender.start();
 }
 private void stopTalk(){talking.set(false);}
 private void show(String s){runOnUiThread(()->{if(!isFinishing())status.setText(s);});}
 @Override protected void onDestroy(){listening=false;stopTalk();if(socket!=null)socket.close();super.onDestroy();}
}
