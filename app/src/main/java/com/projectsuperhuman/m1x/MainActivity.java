package com.projectsuperhuman.m1x;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.util.Locale;

public class MainActivity extends Activity {
    protected WebView webView;
    protected ScaleBridge scaleBridge;

    private boolean isSystemDarkMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyNativeAppearance(FrameLayout root, WebView view) {
        boolean dark = isSystemDarkMode();
        int bg = dark ? Color.rgb(9, 19, 31) : Color.rgb(248, 250, 252);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        root.setBackgroundColor(bg);
        view.setBackgroundColor(bg);

        if (Build.VERSION.SDK_INT >= 23) {
            int flags = 0;
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26 && !dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        // Android 15 / targetSdk 35 enforces edge-to-edge. Keep the page below the
        // real status-bar inset while deliberately leaving the bottom untouched.
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        applyNativeAppearance(root, webView);

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
            } else {
                top = insets.getSystemWindowInsetTop();
            }
            root.setPadding(0, top, 0, 0);
            return insets;
        });
        root.requestApplyInsets();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDefaultTextEncodingName("utf-8");
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Make CSS prefers-color-scheme agree with Android on WebView versions that
        // support force-dark. The JS bridge below is still the authoritative signal.
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                s.setForceDark(isSystemDarkMode() ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
            } catch (Throwable ignored) {}
        }

        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url){
                super.onPageFinished(view,url);
                view.evaluateJavascript("(function(){[['psh-clinical-body-upgrade-script','clinical_body_upgrade.js'],['psh-bp-native-upgrade-script','bp_native_upgrade.js'],['psh-settings-upgrade-script','settings_upgrade.js'],['psh-safe-area-fix-script','safe_area_fix.js']].forEach(function(x){if(document.getElementById(x[0]))return;var s=document.createElement('script');s.id=x[0];s.src=x[1];document.head.appendChild(s);});})()",null);
            }
        });
        scaleBridge = new ScaleBridge(this);
        webView.addJavascriptInterface(scaleBridge, "SuperhumanBLE");
        webView.addJavascriptInterface(new AppBridge(), "SuperhumanApp");
        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (scaleBridge != null) scaleBridge.stopScaleScan();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (scaleBridge != null && requestCode == ScaleBridge.REQ_BLE) scaleBridge.onPermissionResult();
    }

    public final class AppBridge {
        @JavascriptInterface public String getVersionName(){
            try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}
        }

        @JavascriptInterface public boolean isDarkMode(){
            return isSystemDarkMode();
        }
    }

    public static class ScaleBridge {
        static final int REQ_BLE = 4127;
        private final MainActivity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private BluetoothLeScanner scanner;
        private ScanCallback callback;
        private String pendingMac = "";
        private boolean discoverAll = false;

        ScaleBridge(MainActivity a){ activity=a; }

        @JavascriptInterface public String capability(){
            BluetoothManager bm=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad=bm==null?null:bm.getAdapter();
            if(ad==null)return "unsupported";
            if(!ad.isEnabled())return "bluetooth_off";
            return "ready";
        }

        @JavascriptInterface public void startScaleScan(String mac, boolean discover){
            pendingMac=normalizeMac(mac); discoverAll=discover;
            activity.runOnUiThread(() -> ensurePermissionsAndStart());
        }

        @JavascriptInterface public void stopScaleScan(){ activity.runOnUiThread(this::stopInternal); }

        void onPermissionResult(){
            if(hasPermissions()) startInternal(); else sendStatus("permission_denied","Bluetooth scan permission was not granted.");
        }

        private void ensurePermissionsAndStart(){
            if(!hasPermissions()){
                if(Build.VERSION.SDK_INT>=31){
                    activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ_BLE);
                } else if(Build.VERSION.SDK_INT>=23){
                    activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_BLE);
                }
                sendStatus("permission_requested","Allow Nearby devices / Bluetooth access so Project Superhuman can hear the scale.");
                return;
            }
            startInternal();
        }

        private boolean hasPermissions(){
            if(Build.VERSION.SDK_INT>=31){
                return activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED &&
                        activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
            }
            return Build.VERSION.SDK_INT<23 || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        }

        private void startInternal(){
            try{
                BluetoothManager bm=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter ad=bm==null?null:bm.getAdapter();
                if(ad==null){sendStatus("unsupported","This phone does not expose Bluetooth LE.");return;}
                if(!ad.isEnabled()){sendStatus("bluetooth_off","Turn Bluetooth on, then scan again.");return;}
                scanner=ad.getBluetoothLeScanner();
                if(scanner==null){sendStatus("scanner_unavailable","Bluetooth scanner is unavailable right now.");return;}
                stopInternal();
                callback=new ScanCallback(){
                    @Override public void onScanResult(int callbackType, ScanResult result){ onAdvertisement(result); }
                    @Override public void onScanFailed(int errorCode){ sendStatus("scan_failed","BLE scan failed ("+errorCode+")"); }
                };
                ScanSettings settings=new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                scanner.startScan(null,settings,callback);
                sendStatus("scanning", discoverAll?"Scanning nearby BLE devices. Step on the scale now.":"Listening for your saved scale. Step on it now.");
                handler.postDelayed(() -> { stopInternal(); sendStatus("timeout","Scan finished. If nothing appeared, wake the scale and try again."); }, 12000);
            }catch(SecurityException e){sendStatus("permission_denied","Android blocked the Bluetooth scan. Check app permissions.");}
            catch(Exception e){sendStatus("scan_failed","Could not start Bluetooth scan.");}
        }

        private void stopInternal(){
            handler.removeCallbacksAndMessages(null);
            if(scanner!=null && callback!=null){try{scanner.stopScan(callback);}catch(Exception ignored){}}
            callback=null;
        }

        private void onAdvertisement(ScanResult r){
            try{
                BluetoothDevice d=r.getDevice(); String mac=d==null?"":normalizeMac(d.getAddress());
                if(!discoverAll && !pendingMac.isEmpty() && !pendingMac.equals(mac)) return;
                ScanRecord rec=r.getScanRecord(); byte[] raw=rec==null?null:rec.getBytes();
                JSONObject j=new JSONObject();
                String name=""; try{name=d==null?"":d.getName();}catch(SecurityException ignored){}
                j.put("name",name==null?"":name); j.put("mac",mac); j.put("rssi",r.getRssi());
                j.put("raw",toHex(raw)); j.put("parser","raw_ble"); j.put("okok",looksOkok(raw,name));
                sendEvent("onAdvertisement",j.toString());
            }catch(Exception ignored){}
        }

        private boolean looksOkok(byte[] raw,String name){
            if(name!=null && name.toLowerCase(Locale.ROOT).contains("okok"))return true;
            if(raw==null)return false;
            String h=toHex(raw); return h.contains("FFF0")||h.length()>40;
        }
        private String normalizeMac(String m){return m==null?"":m.trim().toUpperCase(Locale.ROOT);}
        private String toHex(byte[] b){if(b==null)return "";StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02X",x&255));return s.toString();}
        private void sendStatus(String code,String message){
            try{JSONObject j=new JSONObject();j.put("code",code);j.put("message",message);sendEvent("onStatus",j.toString());}catch(Exception ignored){}
        }
        private void sendEvent(String method,String json){
            activity.runOnUiThread(() -> { if(activity.webView!=null) activity.webView.evaluateJavascript("window.ProjectSuperhumanScale&&ProjectSuperhumanScale."+method+"("+JSONObject.quote(json)+");",null); });
        }
    }
}
