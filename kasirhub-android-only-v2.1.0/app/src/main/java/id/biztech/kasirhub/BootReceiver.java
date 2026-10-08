package id.biztech.kasirhub;
import android.content.*;import android.os.Build;
public class BootReceiver extends BroadcastReceiver{
    @Override public void onReceive(Context c,Intent i){Intent s=new Intent(c,AlarmService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);}
}