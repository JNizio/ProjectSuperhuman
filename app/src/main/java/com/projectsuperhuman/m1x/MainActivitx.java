package com.projectsuperhuman.m1x;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.util.ArrayList;

public class MainActivitx extends MainActivity {
    private static final int FILE_PICKER = 7210;
    private ValueCallback<Uri[]> filePathCallback;
    protected BarcodeBridge barcodeBridge;
    protected BloodPressureBridge bloodPressureBridge;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        webView.setWebChromeClient(new PickerChrome());
        barcodeBridge = new BarcodeBridge(this);
        bloodPressureBridge = new BloodPressureBridge(this);
        webView.addJavascriptInterface(barcodeBridge,"NativeBarcode");
        webView.addJavascriptInterface(bloodPressureBridge,"NativeBP");
    }

    @Override protected void onDestroy(){
        if(barcodeBridge!=null) barcodeBridge.close();
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(barcodeBridge!=null && barcodeBridge.onActivityResult(requestCode,resultCode,data)) return;
        if(bloodPressureBridge!=null && bloodPressureBridge.onActivityResult(requestCode,resultCode,data)) return;
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==FILE_PICKER){
            Uri[] out=null;
            if(resultCode==RESULT_OK && data!=null){
                ArrayList<Uri> list=new ArrayList<>();
                ClipData clip=data.getClipData();
                if(clip!=null){for(int i=0;i<clip.getItemCount();i++)list.add(clip.getItemAt(i).getUri());}
                else if(data.getData()!=null)list.add(data.getData());
                if(!list.isEmpty())out=list.toArray(new Uri[0]);
            }
            if(filePathCallback!=null)filePathCallback.onReceiveValue(out);
            filePathCallback=null;
        }
    }

    private class PickerChrome extends WebChromeClient {
        @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params){
            if(filePathCallback!=null)filePathCallback.onReceiveValue(null);
            filePathCallback=callback;
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(i,FILE_PICKER); return true;
        }
    }
}
