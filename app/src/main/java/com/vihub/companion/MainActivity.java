package com.vihub.companion;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String verifiedAsset = "";
    private final ExecutorService mapWorker = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean mapLoading = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String MAP_URL = "https://media-rockstargames-com.akamaized.net/VI/downloads/artwork_wallpapers/Leonida_Map_Poster_Printable.zip";
    private static final String MAP_IMAGE = "https://vihub.invalid/official-map.jpg";
    private static final String HOME = "file:///android_asset/index.html";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xff101018);
        setContentView(web);
        web.setOnApplyWindowInsetsListener((v,i) -> { v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom()); return i; });
        web.requestApplyInsets();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (MAP_IMAGE.equals(r.getUrl().toString())) {
                    try { return new WebResourceResponse("image/jpeg",null,new FileInputStream(new File(getCacheDir(),"leonida-map.jpg"))); }
                    catch(IOException e) { return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0])); }
                }
                if (r.getUrl().toString().startsWith("file:///android_asset/")) return null;
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl(HOME);
    }
    private void reply(String event, Object payload) {
        String js = "window.onNative("+JSONObject.quote(event)+","+payload.toString()+")";
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js,null); });
    }
    private JSONObject obj(String key, String val) { JSONObject o=new JSONObject(); try{o.put(key,val);}catch(Exception ignored){} return o; }
    private void error(String name, Exception e) { reply(name,obj("error",e.getMessage()==null?"Request failed":e.getMessage())); }
    private HttpURLConnection connect(String url) throws Exception {
        URL u=new URL(url); if(!"https".equals(u.getProtocol()))throw new IOException("HTTPS required");
        HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setConnectTimeout(15000);c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent","VI-Hub-Android"); return c;
    }
    private String read(String url) throws Exception {
        HttpURLConnection c=connect(url);
        try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4_000_000)throw new IOException("Response too large");}
            return out.toString("UTF-8");
        } finally {c.disconnect();}
    }
    private static int compareVersions(String a,String b) {
        if(!a.matches("v?\\d+(\\.\\d+){0,2}") || !b.matches("v?\\d+(\\.\\d+){0,2}")) throw new IllegalArgumentException("Use numeric release tags such as v1.0.0");
        String[] x=a.replaceFirst("^v","").split("\\."), y=b.replaceFirst("^v","").split("\\.");
        for(int i=0;i<3;i++){int p=i<x.length?Integer.parseInt(x[i]):0,q=i<y.length?Integer.parseInt(y[i]):0;if(p!=q)return Integer.compare(p,q);}return 0;
    }
    private boolean validRepo(String s){return s.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}")&&!s.endsWith("/.")&&!s.endsWith("/..");}
    private void open(String url){
        Uri u=Uri.parse(url); if(!"https".equals(u.getScheme())||u.getHost()==null)return;
        runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){error("notice",e);}});
    }
    public class Bridge {
        @JavascriptInterface public String config(){JSONObject o=obj("repo",BuildConfig.DEFAULT_REPO);try{o.put("version",BuildConfig.VERSION_NAME);}catch(Exception ignored){}return o.toString();}
        @JavascriptInterface public void browse(String url){open(url);}
        @JavascriptInterface public void loadMap(){
            if(new File(getCacheDir(),"leonida-map.jpg").length()>0){reply("map",obj("state","ready"));return;}
            if(!mapLoading.compareAndSet(false,true))return;
            mapWorker.execute(()->{try{fetchMap();reply("map",obj("state","ready"));}catch(Exception e){error("map",e);}finally{mapLoading.set(false);}});
        }
        @JavascriptInterface public void news(String filter){worker.execute(()->{try{fetchNews(filter);}catch(Exception e){error("news",e);}});}
        @JavascriptInterface public void checkUpdate(String repo){worker.execute(()->{try{
            verifiedAsset=""; if(!validRepo(repo))throw new IOException("Enter a public GitHub repository as owner/repo.");
            JSONObject release=new JSONObject(read("https://api.github.com/repos/"+repo+"/releases/latest"));
            String tag=release.getString("tag_name");JSONObject result=obj("tag",tag);result.put("current",BuildConfig.VERSION_NAME);
            result.put("newer",compareVersions(tag,BuildConfig.VERSION_NAME)>0);
            result.put("notes",release.optString("body", "").substring(0,Math.min(4000,release.optString("body", "").length())));
            JSONArray assets=release.optJSONArray("assets"); if(assets!=null)for(int i=0;i<assets.length();i++){
                JSONObject a=assets.getJSONObject(i);String u=a.optString("browser_download_url");
                if(a.optString("name").equals("VI-Hub.apk") && u.startsWith("https://github.com/"+repo+"/releases/download/")){verifiedAsset=u;break;}}
            result.put("download",!verifiedAsset.isEmpty()); reply("update",result);
        }catch(Exception e){error("update",e);}});}
        @JavascriptInterface public void installUpdate(){worker.execute(()->{try{downloadAndInstall();}catch(Exception e){error("install",e);}});}
    }
    private void fetchMap() throws Exception {
        File raw = new File(getCacheDir(),"leonida-source.tmp");
        File out = new File(getCacheDir(),"leonida-cache.tmp");
        File target = new File(getCacheDir(),"leonida-map.jpg");
        HttpURLConnection c = connect(MAP_URL);
        c.setReadTimeout(60000);
        boolean found=false;
        try {
            try(java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new BufferedInputStream(c.getInputStream()))) {
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    if(entry.isDirectory())continue;
                    String name=entry.getName().toLowerCase(java.util.Locale.ROOT);
                    if(!name.endsWith(".jpg")&&!name.endsWith(".jpeg"))throw new IOException("Unexpected map download format");
                    try(FileOutputStream f=new FileOutputStream(raw)){
                        byte[] bytes=new byte[65536];int n;long size=0;
                        while((n=zip.read(bytes))!=-1){size+=n;if(size>75_000_000L)throw new IOException("Map exceeds size limit");f.write(bytes,0,n);}
                    }
                    found=true;break;
                }
            }
            if(!found)throw new IOException("Official map image not found");
            android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(raw.getPath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>12000||bounds.outHeight>12000)throw new IOException("Invalid map image");
            android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();
            opt.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
            opt.inSampleSize=1;
            while(Math.max(bounds.outWidth,bounds.outHeight)/opt.inSampleSize>4200)opt.inSampleSize*=2;
            android.graphics.Bitmap bmp=android.graphics.BitmapFactory.decodeFile(raw.getPath(),opt);
            if(bmp==null)throw new IOException("Unable to read official map");
            try(FileOutputStream f=new FileOutputStream(out)){
                if(!bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,f))throw new IOException("Unable to save map");
            } finally {bmp.recycle();}
            if(!out.renameTo(target))throw new IOException("Unable to cache map");
        } finally {c.disconnect();raw.delete();out.delete();}
    }
    private void fetchNews(String filter) throws Exception {
        String q="Grand Theft Auto VI";
        if(filter.equals("Official"))q+=" site:rockstargames.com";
        if(filter.equals("Updates"))q+=" updates patches";
        if(filter.equals("Community"))q+=" community game news";
        String raw=read("https://news.google.com/rss/search?q="+Uri.encode(q)+"&hl=en-US&gl=US&ceid=US:en");
        XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);parser.setInput(new StringReader(raw));
        JSONArray rows=new JSONArray();JSONObject row=null;for(int t=parser.getEventType();t!=XmlPullParser.END_DOCUMENT;t=parser.next()){
            if(t==XmlPullParser.START_TAG){String n=parser.getName();if(n.equals("item"))row=new JSONObject();else if(row!=null&&Arrays.asList("title","link","pubDate","source").contains(n))row.put(n,parser.nextText());}
            if(t==XmlPullParser.END_TAG&&parser.getName().equals("item")&&row!=null){rows.put(row);row=null;if(rows.length()>=24)break;}}
        JSONObject result=new JSONObject();result.put("items",rows);result.put("filter",filter);reply("news",result);
    }
    @SuppressWarnings("deprecation") private void downloadAndInstall() throws Exception {
        if(verifiedAsset.isEmpty())throw new IOException("Check for an update first.");
        File file=new File(getCacheDir(),"verified-update.apk");
        if(!getPackageManager().canRequestPackageInstalls()){
            reply("install",obj("error","Android requires you to approve installation from this app. No update has been installed."));
            runOnUiThread(()->startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())))); return;
        }
        reply("install",obj("message","Downloading and checking APK…"));
        HttpURLConnection c=connect(verifiedAsset);c.setInstanceFollowRedirects(false);
        try {
            for(int redirects=0;redirects<5;redirects++){
                int status=c.getResponseCode();if(status<300||status>=400)break;
                String dest=c.getHeaderField("Location");c.disconnect();URL u=new URL(dest);String host=u.getHost();
                if(!"https".equals(u.getProtocol())||!(host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Untrusted download redirect");c=connect(dest);c.setInstanceFollowRedirects(false);
            }
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(file)){byte[] b=new byte[32768];int n;long size=0;while((n=in.read(b))!=-1){size+=n;if(size>150_000_000)throw new IOException("APK exceeds size limit");out.write(b,0,n);}}
            PackageManager pm=getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);PackageInfo current=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES);
            if(candidate==null||!getPackageName().equals(candidate.packageName)||candidate.signatures==null||current.signatures==null||!new HashSet<Signature>(Arrays.asList(current.signatures)).equals(new HashSet<Signature>(Arrays.asList(candidate.signatures)))||candidate.versionCode<=current.versionCode||compareVersions(candidate.versionName,current.versionName)<=0)throw new IOException("APK rejected: package, signature or version does not match.");
        }catch(Exception e){file.delete();throw e;}finally{c.disconnect();}
        runOnUiThread(()->{try{Uri uri=Uri.parse("content://"+getPackageName()+".update/verified-update.apk");Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);reply("install",obj("message","APK verified. Confirm the update in Android."));}catch(Exception e){error("install",e);}});
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.backScreen()",null);}
    @Override protected void onDestroy(){worker.shutdownNow();mapWorker.shutdownNow();if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
