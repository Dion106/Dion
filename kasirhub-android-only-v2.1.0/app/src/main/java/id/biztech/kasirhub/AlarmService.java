package id.biztech.kasirhub;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.util.*;

public class AlarmService extends Service {
    private static final String CH="kasirhub_alerts";
    private Handler handler; private Runnable loop; private ToneGenerator tone; private AppConfig cfg;
    private int notificationId=5021;
    @Override public void onCreate(){
        super.onCreate(); cfg=new AppConfig(this); handler=new Handler(Looper.getMainLooper());
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CH,"KasirHub Order",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Notifikasi order lokal");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
        try{tone=new ToneGenerator(AudioManager.STREAM_ALARM,100);}catch(Exception ignored){}
        Notification n=new NotificationCompat.Builder(this,CH).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("KasirHub POS aktif").setContentText("Memantau order lokal").setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();
        startForeground(notificationId,n);
        loop=this::check; handler.post(loop);
    }
    private void check(){
        boolean shouldBeep=false;
        try{
            if(!cfg.alarmEnabled()){schedule();return;}
            String base="hub".equals(cfg.mode())?"http://127.0.0.1:"+cfg.port():cfg.hubUrl();
            if(base==null||base.isEmpty()){schedule();return;}
            String role=cfg.station();
            JSONArray unseen;
            if("cashier".equals(role)){unseen=new JSONArray(HttpUtil.get(base+"/api/orders?cashier_unseen=1")); shouldBeep=unseen.length()>0;}
            else {unseen=new JSONArray(HttpUtil.get(base+"/api/orders?station="+java.net.URLEncoder.encode(role,"UTF-8")+"&unseen_station=1"));shouldBeep=unseen.length()>0;}
            if(shouldBeep && tone!=null)tone.startTone(ToneGenerator.TONE_PROP_BEEP2,500);
            JSONArray jobs=new JSONArray(HttpUtil.get(base+"/api/print-jobs?station="+java.net.URLEncoder.encode(role,"UTF-8")));
            PrinterManager pm=new PrinterManager(this);
            for(int i=0;i<jobs.length();i++){
                JSONObject j=jobs.getJSONObject(i);boolean ok=pm.print("cashier".equals(role)?"STRUK KASIR":role.toUpperCase(Locale.US),j.optString("order_no"),j.optString("table_code"),j.optJSONArray("items"),j.optDouble("total"));
                JSONObject ack=new JSONObject().put("success",ok).put("error",ok?"":"Printer belum terhubung");
                HttpUtil.post(base+"/api/print-jobs/"+j.optInt("job_id")+"/ack",ack.toString());
            }
        }catch(Exception ignored){}
        schedule();
    }
    private void schedule(){handler.postDelayed(loop,2200);}
    @Override public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){if(handler!=null)handler.removeCallbacksAndMessages(null);try{if(tone!=null)tone.release();}catch(Exception ignored){}super.onDestroy();}
}