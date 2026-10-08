package id.biztech.kasirhub;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class HttpUtil {
    public static String get(String url) throws Exception { return request("GET", url, null); }
    public static String post(String url, String json) throws Exception { return request("POST", url, json); }
    public static String put(String url, String json) throws Exception { return request("PUT", url, json); }
    public static String delete(String url) throws Exception { return request("DELETE", url, null); }
    private static String request(String method, String url, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(6000); c.setReadTimeout(12000); c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        if (json != null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); try(OutputStream o=c.getOutputStream()){o.write(json.getBytes(StandardCharsets.UTF_8));} }
        int code=c.getResponseCode(); InputStream in=(code>=200&&code<400)?c.getInputStream():c.getErrorStream();
        String body=new String(readAll(in), StandardCharsets.UTF_8); c.disconnect();
        if(code<200||code>=400) throw new IOException("HTTP "+code+" "+body); return body;
    }
    private static byte[] readAll(InputStream in) throws IOException { if(in==null)return new byte[0]; ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] x=new byte[8192]; int n; while((n=in.read(x))>0)b.write(x,0,n); return b.toByteArray(); }
    private HttpUtil() {}
}