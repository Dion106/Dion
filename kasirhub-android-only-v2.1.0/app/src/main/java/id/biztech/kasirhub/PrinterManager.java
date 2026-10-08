package id.biztech.kasirhub;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class PrinterManager {
    private final Context ctx; private final AppConfig cfg;
    public PrinterManager(Context c){ctx=c;cfg=new AppConfig(c);}
    public boolean print(String title,String orderNo,String table,JSONArray items,double total){
        try{
            StringBuilder s=new StringBuilder();
            s.append("\u001B@");
            s.append("\u001Ba\u0001");
            s.append("BLUE OCEAN VIEW\n").append(title).append("\n");
            s.append("\u001Ba\u0000").append("Order: ").append(orderNo).append("\nMeja : ").append(table).append("\n");
            s.append("------------------------------\n");
            for(int i=0;i<items.length();i++){JSONObject it=items.getJSONObject(i);s.append(it.optInt("qty",1)).append(" x ").append(it.optString("name")).append("\n");}
            s.append("------------------------------\n");
            s.append("TOTAL Rp ").append(String.format(Locale.US,"%,.0f",total).replace(',','.')).append("\n\n\n");
            s.append("\u001DV\u0000");
            byte[] data=s.toString().getBytes(StandardCharsets.UTF_8);
            if(!cfg.printerHost().isEmpty()){
                try(Socket sock=new Socket(cfg.printerHost(),cfg.printerPort());OutputStream out=sock.getOutputStream()){out.write(data);out.flush();}
                return true;
            }
            String mac=cfg.bluetoothMac(); if(mac.isEmpty()) return false;
            BluetoothAdapter ba=BluetoothAdapter.getDefaultAdapter(); if(ba==null||!ba.isEnabled()) return false;
            if(android.os.Build.VERSION.SDK_INT>=31 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) return false;
            BluetoothDevice d=ba.getRemoteDevice(mac);
            BluetoothSocket bs=d.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
            bs.connect(); OutputStream out=bs.getOutputStream(); out.write(data); out.flush(); out.close(); bs.close(); return true;
        }catch(Exception e){return false;}
    }
}