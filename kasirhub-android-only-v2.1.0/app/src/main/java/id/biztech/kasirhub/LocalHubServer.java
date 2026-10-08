package id.biztech.kasirhub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LocalHubServer extends NanoHTTPD {
    private final Context ctx;
    private final KasirHubDb db;
    private final AppConfig cfg;

    public LocalHubServer(Context c, int port) {
        super("0.0.0.0", port);
        ctx = c;
        db = new KasirHubDb(c);
        cfg = new AppConfig(c);
    }

    private Response json(Object value) {
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", String.valueOf(value));
    }

    private Response html(String value) {
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", value);
    }

    private JSONObject body(IHTTPSession session) throws Exception {
        Map<String,String> files = new HashMap<>();
        session.parseBody(files);
        return new JSONObject(files.getOrDefault("postData", "{}"));
    }

    @Override public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            Method method = session.getMethod();

            if ("/".equals(uri) || "/home".equals(uri) || "/login".equals(uri)) return html(loginPage());
            if ("/manager".equals(uri)) return html(managerPage());
            if ("/station".equals(uri)) return html(stationPage(session.getParms().getOrDefault("station", "cashier")));
            if ("/order".equals(uri)) return html(customerPage(session.getParms().getOrDefault("table", "M01")));
            if ("/track".equals(uri)) return html(trackPage(session.getParms().getOrDefault("token", "")));

            if (uri.startsWith("/media/product/")) return productImage(uri);
            if ("/qr.png".equals(uri)) return qrPng(session.getParms().getOrDefault("table", "M01"));

            if ("/api/login".equals(uri) && method == Method.POST) {
                JSONObject b = body(session);
                return json(db.login(b.optString("username"), b.optString("password")));
            }
            if ("/api/products".equals(uri) && method == Method.GET) return json(db.products());
            if ("/api/products".equals(uri) && method == Method.POST) {
                JSONObject b = body(session);
                long id = db.saveProduct(b);
                String image = b.optString("image_base64", "");
                if (!image.isEmpty()) saveImage((int) id, image);
                return json(new JSONObject().put("success", true).put("id", id));
            }
            if ("/api/tables".equals(uri) && method == Method.GET) return json(db.tables());
            if ("/api/tables".equals(uri) && method == Method.POST) {
                long id = db.saveTable(body(session));
                return json(new JSONObject().put("success", true).put("id", id));
            }
            if ("/api/orders".equals(uri) && method == Method.GET) {
                return json(db.ordersFor(session.getParms().get("station"),
                        "1".equals(session.getParms().get("cashier_unseen"))));
            }
            if ("/api/orders".equals(uri) && method == Method.POST) return json(db.createOrder(body(session)));
            if ("/api/order-status".equals(uri) && method == Method.GET)
                return json(db.orderStatusByToken(session.getParms().getOrDefault("token", "")));

            if (uri.matches("/api/orders/[^/]+/seen") && method == Method.POST) {
                db.cashierSeen(uri.split("/")[3]);
                return json(new JSONObject().put("success", true));
            }
            if (uri.matches("/api/orders/[^/]+/payment") && method == Method.POST) {
                JSONObject b = body(session);
                db.payment(uri.split("/")[3], b.optString("method", "cashier"));
                return json(new JSONObject().put("success", true));
            }
            if (uri.matches("/api/orders/[^/]+/station") && method == Method.POST) {
                JSONObject b = body(session);
                db.station(uri.split("/")[3], b.optString("station", "kitchen"),
                        b.optString("status", "processing"));
                return json(new JSONObject().put("success", true));
            }
            if ("/api/print-jobs".equals(uri) && method == Method.GET)
                return json(db.claimPrintJobs(session.getParms().getOrDefault("station", "cashier")));
            if (uri.matches("/api/print-jobs/\\d+/ack") && method == Method.POST) {
                JSONObject b = body(session);
                db.ackPrint(Integer.parseInt(uri.split("/")[3]), b.optBoolean("success"),
                        b.optString("error"));
                return json(new JSONObject().put("success", true));
            }
            if ("/api/dashboard".equals(uri) && method == Method.GET) return json(db.dashboard());
            if ("/api/reservations".equals(uri) && method == Method.POST)
                return json(new JSONObject().put("success", true).put("id", db.reservation(body(session))));
            if ("/api/settings".equals(uri) && method == Method.GET)
                return json(new JSONObject().put("business_name", db.setting("business_name"))
                        .put("whatsapp", db.setting("whatsapp")));
            if ("/api/settings".equals(uri) && method == Method.POST) {
                JSONObject b = body(session);
                db.setting("business_name", b.optString("business_name"));
                db.setting("whatsapp", b.optString("whatsapp"));
                return json(new JSONObject().put("success", true));
            }
            if ("/report.csv".equals(uri)) {
                Response r = newFixedLengthResponse(Response.Status.OK, "text/csv; charset=UTF-8", db.reportCsv());
                r.addHeader("Content-Disposition", "attachment; filename=kasirhub-laporan.csv");
                return r;
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        } catch (Exception e) {
            try {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=UTF-8",
                        new JSONObject().put("success", false).put("error", String.valueOf(e.getMessage())).toString());
            } catch (Exception ignored) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "KasirHub error");
            }
        }
    }

    private Response productImage(String uri) throws Exception {
        int id = Integer.parseInt(uri.substring("/media/product/".length()));
        File f = new File(ctx.getFilesDir(), "products/" + id + ".jpg");
        if (!f.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        return newChunkedResponse(Response.Status.OK, "image/jpeg", new FileInputStream(f));
    }

    private void saveImage(int id, String base64) throws Exception {
        int comma = base64.indexOf(',');
        if (comma >= 0) base64 = base64.substring(comma + 1);
        byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        File dir = new File(ctx.getFilesDir(), "products");
        if (!dir.exists()) dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, id + ".jpg"))) {
            out.write(data);
        }
    }

    private String localIp() {
        try {
            WifiManager manager = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(manager.getConnectionInfo().getIpAddress());
            if (ip != null && !ip.isEmpty() && !"0.0.0.0".equals(ip)) return ip;
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private Response qrPng(String table) throws Exception {
        String payload = "kasirhub://order?table=" + URLEncoder.encode(table, "UTF-8")
                + "&hub=" + URLEncoder.encode(localIp() + ":" + cfg.port(), "UTF-8");
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640);
        } catch (WriterException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
        Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return newFixedLengthResponse(Response.Status.OK, "image/png",
                new ByteArrayInputStream(out.toByteArray()), out.size());
    }

    private String css() {
        return """
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:Arial,sans-serif;margin:0;background:#f4f8f7;color:#193734}
header{padding:14px 18px;background:#fff;border-bottom:1px solid #e2ebe8;display:flex;justify-content:space-between;align-items:center;position:sticky;top:0;z-index:3}
.wrap{max-width:1100px;margin:auto;padding:14px}
.hero{background:linear-gradient(135deg,#dff0c5,#82bcae);border-radius:18px;padding:20px;margin-bottom:14px}
.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.card,.panel{background:#fff;border:1px solid #e1e9e6;border-radius:16px;padding:12px}
.pimg{width:100%;aspect-ratio:1;object-fit:cover;border-radius:12px;background:#edf5f2}
.pname{font-size:18px;font-weight:700;margin-top:7px}
.price{font-size:17px;font-weight:700;color:#1f6e66}
.btn{border:0;border-radius:10px;padding:11px 14px;background:#2f8b7f;color:#fff;font-weight:700}
.btn.alt{background:#fff;color:#2f8b7f;border:1px solid #2f8b7f}
.input{width:100%;padding:12px;border:1px solid #cedad6;border-radius:10px;margin:6px 0 10px;font-size:16px}
.row{display:flex;justify-content:space-between;gap:10px;align-items:center}
.small{font-size:13px;color:#6b7b78}
.status{background:#edf5f3;padding:10px;border-radius:10px;margin:8px 0}
@media(max-width:700px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.wrap{padding:10px}}
</style>
""";
    }

    private String loginPage() {
        return """
<html><head>""" + css() + """
</head><body><div class="wrap" style="max-width:480px;margin-top:40px">
<div class="hero"><h1>KasirHub POS</h1><div>Login Station • Offline Local</div></div>
<div class="panel">
<input id="u" class="input" placeholder="Username">
<input id="p" class="input" type="password" placeholder="Password">
<button class="btn" style="width:100%" onclick="go()">Masuk</button>
<div id="m" class="small"></div>
</div></div>
<script>
async function go(){
 const r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},
 body:JSON.stringify({username:u.value,password:p.value})}).then(x=>x.json());
 if(!r.success){m.textContent='Username atau password salah';return;}
 location.href=r.role==='manager'?'/manager':'/station?station='+encodeURIComponent(r.role);
}
</script></body></html>
""";
    }

    private String stationPage(String station) {
        String title = "cashier".equals(station) ? "Kasir" :
                "kitchen".equals(station) ? "Dapur" :
                "bar".equals(station) ? "Mini Bar" :
                "souvenir".equals(station) ? "Souvenir" : "Station";
        String unseen = "cashier".equals(station) ? "true" : "false";
        return """
<html><head>""" + css() + """
</head><body><header><b>KasirHub • """ + esc(title) + """</b>
<button class="btn alt" onclick="location.href='/login'">Keluar</button></header>
<div class="wrap"><div class="hero"><h2>""" + esc(title) + """</h2>
<div>Order realtime • alarm aktif untuk order yang belum dilihat.</div></div>
<div id="list" class="grid"></div></div>
<script>
const st='""" + esc(station) + """';
const cash= """ + unseen + """;
let known=new Set();
function beep(){
 try{
  const C=window.AudioContext||window.webkitAudioContext;
  if(!window.ac)window.ac=new C();
  if(ac.state==='suspended')ac.resume();
  const o=ac.createOscillator(),g=ac.createGain();
  o.frequency.value=880;g.gain.value=.12;o.connect(g);g.connect(ac.destination);
  o.start();o.stop(ac.currentTime+.5);
 }catch(e){}
}
async function load(){
 let u=cash?'/api/orders?cashier_unseen=1':'/api/orders?station='+encodeURIComponent(st);
 let a=await fetch(u).then(r=>r.json());
 if(a.length && a.some(x=>!known.has(x.order_no))) beep();
 a.forEach(x=>known.add(x.order_no));
 document.getElementById('list').innerHTML=a.map(o=>{
   let items=o.items.map(i=>i.qty+'× '+i.name).join('<br>');
   let buttons=cash?
     '<button class="btn" onclick="seen(\\''+o.order_no+'\\')">👀 Lihat</button> '+
     '<button class="btn alt" onclick="pay(\\''+o.order_no+'\\',\\''+(o.payment_method||'cashier')+'\\')">✓ Lunas</button>':
     '<button class="btn" onclick="setst(\\''+o.order_no+'\\',\\'processing\\')">Mulai Proses</button> '+
     '<button class="btn alt" onclick="setst(\\''+o.order_no+'\\',\\'done\\')">Selesai</button>';
   return '<div class="card"><div class="row"><b>#'+o.order_no+
   '</b><span class="price">Rp'+Number(o.total).toLocaleString('id-ID')+'</span></div>'+
   '<div class="small">'+o.table_code+' • '+(o.customer_name||'Tamu')+'</div>'+
   '<div class="status">Pembayaran: '+o.payment_status+' • '+o.order_status+'</div>'+
   '<div>'+items+'</div><div style="margin-top:10px">'+buttons+'</div></div>';
 }).join('')||'<div class="panel">Belum ada order.</div>';
}
async function seen(n){await fetch('/api/orders/'+n+'/seen',{method:'POST'});known.add(n);load()}
async function pay(n,m){await seen(n);await fetch('/api/orders/'+n+'/payment',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({method:m})});load()}
async function setst(n,s){await fetch('/api/orders/'+n+'/station',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({station:st,status:s})});load()}
load();setInterval(load,2200);
</script></body></html>
""";
    }

    private String customerPage(String table) {
        JSONObject t = db.table(table);
        return """
<html><head>""" + css() + """
</head><body><header><b>""" + esc(db.setting("business_name")) + """</b>
<span>Meja """ + esc(t.optString("name", table)) + """</span></header>
<div class="wrap"><div class="hero"><div class="small">ORDER DARI MEJA</div>
<h1>""" + esc(t.optString("name", table)) + """</h1><div>""" + esc(t.optString("area", "Indoor")) + """</div></div>
<div class="row"><input id="q" class="input" style="margin-right:8px" placeholder="Cari makanan, minuman, souvenir..." oninput="draw()">
<button class="btn alt" onclick="location.reload()">↻</button></div>
<div id="cats" style="margin:6px 0"></div><div id="plist" class="grid"></div>
<div class="panel" style="margin-top:14px"><h3>Pesanan Anda</h3><div id="cart"></div>
<input id="name" class="input" placeholder="Nama Anda"><input id="phone" class="input" placeholder="No. WhatsApp">
<select id="pay" class="input"><option value="cashier">Bayar di Kasir</option><option value="qris">QRIS</option><option value="transfer">Transfer Bank</option></select>
<button class="btn" style="width:100%;font-size:17px" onclick="send()">Kirim Pesanan</button>
<div id="msg" class="small"></div></div></div>
<script>
let P=[],C=[],cat='Semua';
function draw(){
 let q=(document.getElementById('q').value||'').toLowerCase();
 let cats=['Semua',...new Set(P.map(x=>x.category))];
 document.getElementById('cats').innerHTML=cats.map(c=>'<button class="btn alt" style="margin:3px" onclick="cat='+JSON.stringify(c)+';draw()">'+c+'</button>').join('');
 let a=P.filter(x=>(cat==='Semua'||x.category===cat)&&(!q||x.name.toLowerCase().includes(q)));
 document.getElementById('plist').innerHTML=a.map(p=>{
   let image=p.image_file?'<img class="pimg" src="/media/product/'+p.id+'">':'<div class="pimg" style="display:grid;place-items:center;font-size:60px">'+(p.emoji||'🍽️')+'</div>';
   return '<div class="card">'+image+'<div class="pname">'+p.name+'</div><div class="small">'+(p.description||'')+'</div><div class="price">Rp'+Number(p.price).toLocaleString('id-ID')+'</div><button class="btn alt" style="width:100%" onclick="add('+p.id+')">Pesan</button></div>';
 }).join('');
 document.getElementById('cart').innerHTML=C.map(x=>'<div class="row" style="padding:5px 0"><span>'+x.qty+'× '+x.name+'</span><span>Rp'+(x.qty*x.price).toLocaleString('id-ID')+'</span></div>').join('')||'<div class="small">Belum ada item.</div>';
}
function add(id){let p=P.find(x=>x.id==id),x=C.find(y=>y.product_id==id);x?x.qty++:C.push({product_id:p.id,name:p.name,price:p.price,qty:1,station:p.station,note:''});draw()}
async function send(){
 if(!C.length)return alert('Pilih menu terlebih dahulu');
 let n=document.getElementById('name').value,ph=document.getElementById('phone').value;
 localStorage.setItem('kh_name',n);localStorage.setItem('kh_phone',ph);
 let o=await fetch('/api/orders',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({table_code:'""" + esc(table) + """',customer_name:n,customer_phone:ph,payment_method:document.getElementById('pay').value,items:C})}).then(r=>r.json());
 if(!o.success)return alert(o.error||'Gagal');
 document.getElementById('msg').innerHTML='<b>Order '+o.order_no+' berhasil.</b><br><a href="/track?token='+encodeURIComponent(o.track_token)+'">Lihat status realtime</a>';
 C=[];draw();
}
fetch('/api/products').then(r=>r.json()).then(x=>{P=x;document.getElementById('name').value=localStorage.getItem('kh_name')||'';document.getElementById('phone').value=localStorage.getItem('kh_phone')||'';draw()});
</script></body></html>
""";
    }

    private String trackPage(String token) {
        return """
<html><head>""" + css() + """
</head><body><div class="wrap"><div class="hero"><h2 id="no">Memuat...</h2><div id="tbl">-</div></div>
<div class="panel"><h3 id="st">-</h3><div id="items"></div><div class="row" style="margin-top:14px"><b>Total</b><b id="tot" class="price">Rp0</b></div></div></div>
<script>
const tk='""" + esc(token) + """';
async function load(){
 let o=await fetch('/api/order-status?token='+encodeURIComponent(tk)).then(r=>r.json());
 if(!o.order_no){document.getElementById('st').textContent='Pesanan tidak ditemukan';return}
 document.getElementById('no').textContent='#'+o.order_no;
 document.getElementById('tbl').textContent='Meja '+o.table_code+' • '+(o.customer_name||'Tamu');
 document.getElementById('st').textContent='Pembayaran: '+o.payment_status+' • '+(o.order_status==='new'?'Order Diterima':o.order_status==='processing'?'Sedang Diproses':'Selesai');
 document.getElementById('items').innerHTML=o.items.map(i=>'<div class="row" style="padding:6px 0"><span>'+i.qty+'× '+i.name+'</span><span>Rp'+Number(i.price*i.qty).toLocaleString('id-ID')+'</span></div>').join('');
 document.getElementById('tot').textContent='Rp'+Number(o.total).toLocaleString('id-ID');
}
load();setInterval(load,1500);
</script></body></html>
""";
    }

    private String managerPage() {
        return """
<html><head>""" + css() + """
</head><body><header><b>KasirHub • Manager</b><button class="btn alt" onclick="location.href='/login'">Keluar</button></header>
<div class="wrap"><div class="hero"><h2>Dashboard Manager</h2><div>Produk • Meja • QR • Laporan</div></div>
<div class="grid">
<div class="panel"><h3>Tambah Produk</h3><input id="n" class="input" placeholder="Nama produk"><input id="p" class="input" type="number" placeholder="Harga">
<select id="s" class="input"><option value="kitchen">Dapur</option><option value="bar">Mini Bar</option><option value="souvenir">Souvenir</option></select>
<input id="c" class="input" placeholder="Kategori"><input id="d" class="input" placeholder="Deskripsi">
<input id="f" class="input" type="file" accept="image/*"><button class="btn" onclick="saveP()">Simpan Produk</button></div>
<div class="panel"><h3>Tambah Meja</h3><input id="tc" class="input" placeholder="Kode meja"><input id="tn" class="input" placeholder="Nama meja">
<input id="ta" class="input" type="number" value="2"><input id="tr" class="input" placeholder="Area"><button class="btn" onclick="saveT()">Simpan Meja</button><div id="tl"></div></div>
</div><div class="panel" style="margin-top:14px"><a class="btn" href="/report.csv">Export Laporan</a></div></div>
<script>
function b64(file){return new Promise(r=>{if(!file)return r('');let fr=new FileReader();fr.onload=()=>r(fr.result);fr.readAsDataURL(file)})}
async function saveP(){let img=await b64(document.getElementById('f').files[0]);await fetch('/api/products',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:n.value,price:Number(p.value),station:s.value,category:c.value||'Lainnya',description:d.value,image_base64:img})});alert('Produk disimpan')}
async function saveT(){await fetch('/api/tables',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:tc.value,name:tn.value,capacity:Number(ta.value),area:tr.value||'Indoor'})});alert('Meja disimpan');loadT()}
async function loadT(){let a=await fetch('/api/tables').then(r=>r.json());tl.innerHTML=a.map(x=>'<div class="status"><b>'+x.code+'</b> — '+x.name+' <a href="/qr.png?table='+encodeURIComponent(x.code)+'" target="_blank">QR</a></div>').join('')}
loadT();
</script></body></html>
""";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace(""", "&quot;").replace("'", "&#39;");
    }
}
