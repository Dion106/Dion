package id.biztech.kasirhub;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import fi.iki.elonen.NanoHTTPD;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private AppConfig cfg; private LocalHubServer server; private WebView web; private TextView status,ip;
    private ValueCallback<Uri[]> fileCallback;
    @Override public void onCreate(Bundle b){super.onCreate(b);cfg=new AppConfig(this);requestPerms();
        String deepTable=getIntent().getData()==null?null:getIntent().getData().getQueryParameter("table");
        String deepHub=getIntent().getData()==null?null:getIntent().getData().getQueryParameter("hub");
        if(deepTable!=null&&!deepTable.isEmpty()){if(deepHub!=null&&!deepHub.isEmpty())cfg.hubUrl("http://"+deepHub);cfg.mode("customer");openCustomer(deepTable);return;}
        setContentView(R.layout.activity_main);setup();
    }
    private void requestPerms(){if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},100);if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.BLUETOOTH_CONNECT},101);}
    private void setup(){
        Spinner mode=findViewById(R.id.mode),station=findViewById(R.id.station); mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"MODE: Local Server (HP Server)","MODE: Station / Client","MODE: Customer"})); station.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"cashier","kitchen","bar","souvenir","manager"}));
        EditText hub=findViewById(R.id.hubUrl),ph=findViewById(R.id.printerHost),pp=findViewById(R.id.printerPort),bm=findViewById(R.id.bluetoothMac);status=findViewById(R.id.status);ip=findViewById(R.id.ip);
        hub.setText(cfg.hubUrl());ph.setText(cfg.printerHost());pp.setText(String.valueOf(cfg.printerPort()));bm.setText(cfg.bluetoothMac());
        String local=getLocalIp()+":"+cfg.port();ip.setText("Local Server: http://"+local);findViewById(R.id.roleHelp).setText("Gunakan hotspot/Wi-Fi lokal. HP Server menjadi pusat database tanpa WordPress/cloud.");
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){hub.setEnabled(pos!=0);station.setEnabled(pos!=0);findViewById(R.id.printerHost).setEnabled(pos!=2);findViewById(R.id.printerPort).setEnabled(pos!=2);findViewById(R.id.bluetoothMac).setEnabled(pos!=2);}public void onNothingSelected(AdapterView<?> p){}});
        findViewById(R.id.save).setOnClickListener(v->{int pos=mode.getSelectedItemPosition();cfg.mode(pos==0?"hub":pos==1?"station":"customer");cfg.station(String.valueOf(station.getSelectedItem()));cfg.hubUrl(hub.getText().toString());cfg.printerHost(ph.getText().toString());try{cfg.printerPort(Integer.parseInt(pp.getText().toString().trim()));}catch(Exception ignored){}cfg.bluetoothMac(bm.getText().toString());startServerIfNeeded();startAlarm();status.setText("Aktif • "+cfg.mode()+" • "+cfg.station());});
        findViewById(R.id.open).setOnClickListener(v->openConfigured());
        findViewById(R.id.copy).setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("KasirHub URL","http://"+getLocalIp()+":"+cfg.port()));Toast.makeText(this,"URL server disalin",Toast.LENGTH_SHORT).show();});
        startServerIfNeeded(); startAlarm();
    }
    private void startServerIfNeeded(){try{if("hub".equals(cfg.mode())||"customer".equals(cfg.mode())&&cfg.hubUrl().contains("127.0.0.1")){if(server==null){server=new LocalHubServer(this,cfg.port());server.start(NanoHTTPD.SOCKET_READ_TIMEOUT,false);}}}catch(Exception e){if(status!=null)status.setText("Gagal menjalankan server: "+e.getMessage());}}
    private void startAlarm(){try{Intent i=new Intent(this,AlarmService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception ignored){}}
    private void openConfigured(){if("hub".equals(cfg.mode()))openWeb("http://127.0.0.1:"+cfg.port()+"/login");else if("customer".equals(cfg.mode()))openWeb(cfg.hubUrl()+"/order?table=M01");else openWeb(cfg.hubUrl()+"/login");}
    private void openCustomer(String table){startServerIfNeeded();openWeb((cfg.mode().equals("hub")?"http://127.0.0.1:"+cfg.port():cfg.hubUrl())+"/order?table="+encode(table));}
    private void openWeb(String url){web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowContentAccess(true);web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> cb,FileChooserParams p){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,200);return true;}});setContentView(web);web.loadUrl(url);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==200&&fileCallback!=null){Uri[] r=null;if(res==RESULT_OK&&data!=null&&data.getData()!=null)r=new Uri[]{data.getData()};fileCallback.onReceiveValue(r);fileCallback=null;}}
    private String encode(String s){ try { return URLEncoder.encode(s,"UTF-8"); } catch(Exception e){ return s; } }
    private String getLocalIp(){try{for(Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();e.hasMoreElements();){NetworkInterface n=e.nextElement();for(Enumeration<InetAddress> a=n.getInetAddresses();a.hasMoreElements();){InetAddress x=a.nextElement();if(!x.isLoopbackAddress()&&x instanceof Inet4Address)return x.getHostAddress();}}}catch(Exception ignored){}return "127.0.0.1";}
    @Override protected void onDestroy(){if(server!=null)server.stop();super.onDestroy();}
}