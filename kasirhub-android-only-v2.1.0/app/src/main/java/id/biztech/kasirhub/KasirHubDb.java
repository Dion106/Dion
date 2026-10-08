package id.biztech.kasirhub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class KasirHubDb extends SQLiteOpenHelper {
    private static final int VERSION = 1;

    public KasirHubDb(Context c) { super(c, "kasirhub_local.db", null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price REAL NOT NULL,station TEXT,category TEXT,description TEXT,emoji TEXT,image_file TEXT,active INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE tables_local(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT UNIQUE,name TEXT NOT NULL,capacity INTEGER DEFAULT 2,area TEXT,active INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE,password TEXT,role TEXT,display_name TEXT,active INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY AUTOINCREMENT,order_no TEXT UNIQUE,track_token TEXT UNIQUE,table_code TEXT,customer_name TEXT,customer_phone TEXT,payment_method TEXT,payment_status TEXT DEFAULT 'pending',order_status TEXT DEFAULT 'new',notes TEXT,total REAL,created_at TEXT,updated_at TEXT,cashier_seen INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE order_items(id INTEGER PRIMARY KEY AUTOINCREMENT,order_no TEXT,product_id INTEGER,name TEXT,price REAL,qty INTEGER,station TEXT,note TEXT)");
        db.execSQL("CREATE TABLE print_jobs(id INTEGER PRIMARY KEY AUTOINCREMENT,order_no TEXT,station TEXT,status TEXT DEFAULT 'pending',attempts INTEGER DEFAULT 0,error TEXT,UNIQUE(order_no,station))");
        db.execSQL("CREATE TABLE reservations(id INTEGER PRIMARY KEY AUTOINCREMENT,table_code TEXT,customer_name TEXT,customer_phone TEXT,reserve_date TEXT,reserve_time TEXT,pax INTEGER,status TEXT DEFAULT 'booked',notes TEXT,created_at TEXT)");
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
        seed(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {}

    private String now(){ return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }

    private void seed(SQLiteDatabase db){
        product(db,"Ayam Lalapan",32000,"kitchen","Makanan","Ayam goreng, sambal dan lalapan","🍗");
        product(db,"Mie Goreng Kampung",26000,"kitchen","Makanan","Mie goreng khas kampung","🍜");
        product(db,"Nasi Goreng Kampung",28000,"kitchen","Makanan","Nasi goreng dengan bumbu rempah","🍳");
        product(db,"Es Teh",8000,"bar","Minuman","Teh manis dingin","🧋");
        product(db,"Jus Nanas",15000,"bar","Minuman","Jus nanas segar","🍍");
        product(db,"Kopi Susu",18000,"bar","Minuman","Kopi susu creamy","☕");
        product(db,"Gantungan Kunci Wakatobi",25000,"souvenir","Souvenir","Oleh-oleh Wakatobi","🔑");
        product(db,"Kaos Wakatobi",120000,"souvenir","Souvenir","Kaos souvenir Wakatobi","👕");
        table(db,"M01","Meja 1",2,"Indoor");
        table(db,"M02","Meja 2",2,"Indoor");
        user(db,"manager","123456","manager","Manager");
        user(db,"kasir","123456","cashier","Kasir");
        user(db,"dapur","123456","kitchen","Dapur");
        user(db,"bar","123456","bar","Mini Bar");
        user(db,"souvenir","123456","souvenir","Souvenir");
        setting(db,"business_name","BLUE OCEAN VIEW WAKATOBI");
        setting(db,"whatsapp","628000000000");
    }

    private void product(SQLiteDatabase db,String n,double p,String s,String c,String d,String e){
        ContentValues v=new ContentValues(); v.put("name",n); v.put("price",p); v.put("station",s); v.put("category",c); v.put("description",d); v.put("emoji",e); db.insert("products",null,v);
    }
    private void table(SQLiteDatabase db,String code,String name,int cap,String area){
        ContentValues v=new ContentValues(); v.put("code",code); v.put("name",name); v.put("capacity",cap); v.put("area",area); db.insert("tables_local",null,v);
    }
    private void user(SQLiteDatabase db,String u,String pw,String role,String display){
        ContentValues v=new ContentValues(); v.put("username",u); v.put("password",pw); v.put("role",role); v.put("display_name",display); db.insert("users",null,v);
    }
    private void setting(SQLiteDatabase db,String k,String val){ContentValues v=new ContentValues();v.put("k",k);v.put("v",val);db.insert("settings",null,v);}

    public synchronized JSONObject login(String u,String p){
        JSONObject o=new JSONObject(); Cursor c=getReadableDatabase().rawQuery("SELECT role,display_name FROM users WHERE username=? AND password=? AND active=1",new String[]{u,p});
        try{if(c.moveToFirst()){o.put("success",true);o.put("role",c.getString(0));o.put("display_name",c.getString(1));}else o.put("success",false);}catch(Exception ignored){}finally{c.close();} return o;
    }

    public synchronized JSONArray products(){
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT id,name,price,station,category,description,emoji,image_file FROM products WHERE active=1 ORDER BY category,name",null);
        try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getInt(0));o.put("name",c.getString(1));o.put("price",c.getDouble(2));o.put("station",c.getString(3));o.put("category",c.getString(4));o.put("description",c.getString(5));o.put("emoji",c.getString(6));o.put("image_file",c.getString(7));a.put(o);}}catch(Exception ignored){}finally{c.close();}return a;
    }

    public synchronized JSONArray tables(){
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT id,code,name,capacity,area,active FROM tables_local ORDER BY id",null);
        try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getInt(0));o.put("code",c.getString(1));o.put("name",c.getString(2));o.put("capacity",c.getInt(3));o.put("area",c.getString(4));o.put("active",c.getInt(5));a.put(o);}}catch(Exception ignored){}finally{c.close();}return a;
    }

    public synchronized JSONObject table(String code){
        JSONObject o=new JSONObject(); Cursor c=getReadableDatabase().rawQuery("SELECT id,code,name,capacity,area,active FROM tables_local WHERE code=?",new String[]{code});
        try{if(c.moveToFirst()){o.put("id",c.getInt(0));o.put("code",c.getString(1));o.put("name",c.getString(2));o.put("capacity",c.getInt(3));o.put("area",c.getString(4));o.put("active",c.getInt(5));}}catch(Exception ignored){}finally{c.close();}return o;
    }

    public synchronized long saveProduct(JSONObject b){
        ContentValues v=new ContentValues();v.put("name",b.optString("name"));v.put("price",b.optDouble("price"));v.put("station",b.optString("station","kitchen"));v.put("category",b.optString("category","Lainnya"));v.put("description",b.optString("description"));v.put("emoji",b.optString("emoji","🍽️"));v.put("image_file",b.optString("image_file"));long id=b.optLong("id",0);
        SQLiteDatabase db=getWritableDatabase(); if(id>0){db.update("products",v,"id=?",new String[]{String.valueOf(id)});return id;} return db.insert("products",null,v);
    }

    public synchronized long saveTable(JSONObject b){
        ContentValues v=new ContentValues();v.put("code",b.optString("code"));v.put("name",b.optString("name"));v.put("capacity",b.optInt("capacity",2));v.put("area",b.optString("area","Indoor"));v.put("active",b.optInt("active",1));long id=b.optLong("id",0);SQLiteDatabase db=getWritableDatabase();
        if(id>0){db.update("tables_local",v,"id=?",new String[]{String.valueOf(id)});return id;}return db.insertOrThrow("tables_local",null,v);
    }

    public synchronized JSONObject createOrder(JSONObject b){
        JSONObject out=new JSONObject(); String orderNo="KH-"+new SimpleDateFormat("yyyyMMdd-HHmmssSSS",Locale.US).format(new Date()); String token=UUID.randomUUID().toString(); double total=0;
        try{
            JSONArray items=b.optJSONArray("items"); if(items==null||items.length()==0)throw new IllegalArgumentException("Keranjang kosong");
            for(int i=0;i<items.length();i++){JSONObject it=items.getJSONObject(i);total+=it.optDouble("price")*Math.max(1,it.optInt("qty",1));}
            ContentValues v=new ContentValues();v.put("order_no",orderNo);v.put("track_token",token);v.put("table_code",b.optString("table_code"));v.put("customer_name",b.optString("customer_name"));v.put("customer_phone",b.optString("customer_phone"));v.put("payment_method",b.optString("payment_method","cashier"));v.put("total",total);v.put("notes",b.optString("notes"));v.put("created_at",now());v.put("updated_at",now());getWritableDatabase().insertOrThrow("orders",null,v);
            for(int i=0;i<items.length();i++){JSONObject it=items.getJSONObject(i);ContentValues iv=new ContentValues();iv.put("order_no",orderNo);iv.put("product_id",it.optInt("product_id"));iv.put("name",it.optString("name"));iv.put("price",it.optDouble("price"));iv.put("qty",Math.max(1,it.optInt("qty",1)));iv.put("station",it.optString("station","kitchen"));iv.put("note",it.optString("note"));getWritableDatabase().insert("order_items",null,iv);}
            out.put("success",true);out.put("order_no",orderNo);out.put("track_token",token);out.put("total",total);
        }catch(Exception e){try{out.put("success",false).put("error",e.getMessage());}catch(Exception ignored){}}
        return out;
    }

    private JSONObject orderObj(String no,boolean withItems){
        JSONObject o=new JSONObject();Cursor c=getReadableDatabase().rawQuery("SELECT order_no,track_token,table_code,customer_name,customer_phone,payment_method,payment_status,order_status,notes,total,created_at,updated_at,cashier_seen FROM orders WHERE order_no=?",new String[]{no});
        try{if(!c.moveToFirst())return o;o.put("order_no",c.getString(0));o.put("track_token",c.getString(1));o.put("table_code",c.getString(2));o.put("customer_name",c.getString(3));o.put("customer_phone",c.getString(4));o.put("payment_method",c.getString(5));o.put("payment_status",c.getString(6));o.put("order_status",c.getString(7));o.put("notes",c.getString(8));o.put("total",c.getDouble(9));o.put("created_at",c.getString(10));o.put("updated_at",c.getString(11));o.put("cashier_seen",c.getInt(12));if(withItems)o.put("items",items(no,null));}catch(Exception ignored){}finally{c.close();}return o;
    }

    private JSONArray items(String no,String station){
        JSONArray a=new JSONArray();String q="SELECT product_id,name,price,qty,station,note FROM order_items WHERE order_no=?"+(station==null?"":" AND station=?");String[] args=station==null?new String[]{no}:new String[]{no,station};Cursor c=getReadableDatabase().rawQuery(q,args);
        try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("product_id",c.getInt(0));o.put("name",c.getString(1));o.put("price",c.getDouble(2));o.put("qty",c.getInt(3));o.put("station",c.getString(4));o.put("note",c.getString(5));a.put(o);}}catch(Exception ignored){}finally{c.close();}return a;
    }

    public synchronized JSONArray ordersFor(String station,boolean cashierUnseen){
        JSONArray a=new JSONArray();String q;String[] args;
        if(cashierUnseen){q="SELECT order_no FROM orders WHERE cashier_seen=0 ORDER BY id DESC";args=new String[0];}
        else if(station==null||station.isEmpty()||"all".equals(station)){q="SELECT order_no FROM orders ORDER BY id DESC LIMIT 100";args=new String[0];}
        else{q="SELECT DISTINCT o.order_no FROM orders o JOIN order_items i ON i.order_no=o.order_no WHERE i.station=? ORDER BY o.id DESC LIMIT 100";args=new String[]{station};}
        Cursor c=getReadableDatabase().rawQuery(q,args);try{while(c.moveToNext())a.put(orderObj(c.getString(0),true));}catch(Exception ignored){}finally{c.close();}return a;
    }

    public synchronized JSONObject orderStatusByToken(String token){
        Cursor c=getReadableDatabase().rawQuery("SELECT order_no FROM orders WHERE track_token=?",new String[]{token});try{if(c.moveToFirst())return orderObj(c.getString(0),true);}finally{c.close();}return new JSONObject();
    }

    public synchronized void cashierSeen(String no){ContentValues v=new ContentValues();v.put("cashier_seen",1);v.put("updated_at",now());getWritableDatabase().update("orders",v,"order_no=?",new String[]{no});}
    public synchronized void payment(String no,String method){ContentValues v=new ContentValues();v.put("payment_status","paid");v.put("payment_method",method);v.put("order_status","processing");v.put("updated_at",now());getWritableDatabase().update("orders",v,"order_no=?",new String[]{no});createPrintJobs(no);}
    public synchronized void station(String no,String station,String status){ContentValues v=new ContentValues();v.put("order_status","done".equals(status)?"done":"processing");v.put("updated_at",now());getWritableDatabase().update("orders",v,"order_no=?",new String[]{no});}

    private void createPrintJobs(String no){
        SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT DISTINCT station FROM order_items WHERE order_no=?",new String[]{no});
        try{while(c.moveToNext()){ContentValues v=new ContentValues();v.put("order_no",no);v.put("station",c.getString(0));v.put("status","pending");db.insertWithOnConflict("print_jobs",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}finally{c.close();}
        ContentValues k=new ContentValues();k.put("order_no",no);k.put("station","cashier");k.put("status","pending");db.insertWithOnConflict("print_jobs",null,k,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized JSONArray claimPrintJobs(String station){
        JSONArray a=new JSONArray();Cursor c=getReadableDatabase().rawQuery("SELECT id,order_no FROM print_jobs WHERE station=? AND status='pending' ORDER BY id LIMIT 5",new String[]{station});
        try{while(c.moveToNext()){int id=c.getInt(0);String no=c.getString(1);ContentValues v=new ContentValues();v.put("status","printing");v.put("attempts",1);getWritableDatabase().update("print_jobs",v,"id=?",new String[]{String.valueOf(id)});JSONObject j=orderObj(no,true);j.put("job_id",id);j.put("items",items(no,"cashier".equals(station)?null:station));a.put(j);}}catch(Exception ignored){}finally{c.close();}return a;
    }

    public synchronized void ackPrint(int id,boolean ok,String err){ContentValues v=new ContentValues();v.put("status",ok?"printed":"pending");v.put("error",err);getWritableDatabase().update("print_jobs",v,"id=?",new String[]{String.valueOf(id)});}
    public synchronized JSONObject dashboard(){JSONObject o=new JSONObject();String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(total),0) FROM orders WHERE created_at LIKE ? AND payment_status='paid'",new String[]{today+"%"});try{if(c.moveToFirst()){o.put("orders",c.getInt(0));o.put("revenue",c.getDouble(1));}}catch(Exception ignored){}finally{c.close();}return o;}
    public synchronized String reportCsv(){StringBuilder s=new StringBuilder("order_no,table,customer,payment_status,total,created_at\n");Cursor c=getReadableDatabase().rawQuery("SELECT order_no,table_code,customer_name,payment_status,total,created_at FROM orders ORDER BY id DESC",null);try{while(c.moveToNext())s.append(csv(c.getString(0))).append(',').append(csv(c.getString(1))).append(',').append(csv(c.getString(2))).append(',').append(csv(c.getString(3))).append(',').append(String.format(Locale.US,"%.0f",c.getDouble(4))).append(',').append(csv(c.getString(5))).append('\n');}finally{c.close();}return s.toString();}
    private String csv(String s){return s==null?"":"\""+s.replace("\"","\"\"")+"\"";}
    public synchronized String setting(String k){Cursor c=getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?",new String[]{k});try{if(c.moveToFirst())return c.getString(0);}finally{c.close();}return "";}
    public synchronized void setting(String k,String v){ContentValues x=new ContentValues();x.put("k",k);x.put("v",v);getWritableDatabase().insertWithOnConflict("settings",null,x,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized long reservation(JSONObject b){ContentValues v=new ContentValues();v.put("table_code",b.optString("table_code"));v.put("customer_name",b.optString("customer_name"));v.put("customer_phone",b.optString("customer_phone"));v.put("reserve_date",b.optString("reserve_date"));v.put("reserve_time",b.optString("reserve_time"));v.put("pax",b.optInt("pax",2));v.put("notes",b.optString("notes"));v.put("created_at",now());return getWritableDatabase().insert("reservations",null,v);}
}
