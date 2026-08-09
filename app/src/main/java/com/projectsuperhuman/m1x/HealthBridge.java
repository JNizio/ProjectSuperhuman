package com.projectsuperhuman.m1x;

import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeRangeFilter;
import android.health.connect.datatypes.SleepSessionRecord;
import android.os.Build;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executor;

public class HealthBridge extends MainActivitx {
    private HealthJs healthJs;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        healthJs=new HealthJs();
        webView.addJavascriptInterface(healthJs,"SuperhumanHealth");
    }

    public final class HealthJs {
        @JavascriptInterface public String getHealthConnectStatus(){
            try{
                JSONObject j=new JSONObject();
                if(Build.VERSION.SDK_INT<34){j.put("available",false);j.put("reason","requires_android_14");return j.toString();}
                HealthConnectManager m=(HealthConnectManager)getSystemService("health_connect");
                if(m==null){j.put("available",false);j.put("reason","service_unavailable");return j.toString();}
                j.put("available",true);j.put("permission",checkSelfPermission("android.permission.health.READ_SLEEP")==PackageManager.PERMISSION_GRANTED);return j.toString();
            }catch(Exception e){return "{\"available\":false,\"reason\":\"bridge_error\"}";}
        }
        @JavascriptInterface public void requestSleepPermission(){
            if(Build.VERSION.SDK_INT>=34)runOnUiThread(() -> requestPermissions(new String[]{"android.permission.health.READ_SLEEP"},8431));
            else emit("onHealthError","Health Connect requires Android 14+");
        }
        @JavascriptInterface public void syncSleep(){
            if(Build.VERSION.SDK_INT<34){emit("onHealthError","Health Connect requires Android 14+");return;}
            if(checkSelfPermission("android.permission.health.READ_SLEEP")!=PackageManager.PERMISSION_GRANTED){emit("onHealthError","Sleep permission is not granted yet.");return;}
            HealthConnectManager m=(HealthConnectManager)getSystemService("health_connect");
            if(m==null){emit("onHealthError","Health Connect service unavailable.");return;}
            emitNoArg("beginSync");
            Instant end=Instant.now(), start=end.minus(30,ChronoUnit.DAYS);
            ReadRecordsRequestUsingFilters<SleepSessionRecord> req=new ReadRecordsRequestUsingFilters.Builder<>(SleepSessionRecord.class)
                    .setTimeRangeFilter(TimeRangeFilter.between(start,end)).setPageSize(200).build();
            Executor ex=getMainExecutor();
            m.readRecords(req,ex,new OutcomeReceiver<ReadRecordsResponse<SleepSessionRecord>,Exception>(){
                @Override public void onResult(ReadRecordsResponse<SleepSessionRecord> response){
                    for(SleepSessionRecord r:response.getRecords()){
                        emitRaw("onSleepRecord", r.getStartTime().toEpochMilli()+","+r.getEndTime().toEpochMilli());
                        for(SleepSessionRecord.Stage s:r.getStages()) emitRaw("onSleepStage",r.getStartTime().toEpochMilli()+","+s.getStartTime().toEpochMilli()+","+s.getEndTime().toEpochMilli()+","+s.getType());
                    }
                    emitNoArg("endSync");
                }
                @Override public void onError(Exception error){emit("onHealthError","Health Connect sleep read failed.");emitNoArg("endSync");}
            });
        }
    }
    private void emit(String method,String text){runOnUiThread(() -> webView.evaluateJavascript("window.ProjectSuperhumanHealth&&ProjectSuperhumanHealth."+method+"("+JSONObject.quote(text)+");",null));}
    private void emitNoArg(String method){runOnUiThread(() -> webView.evaluateJavascript("window.ProjectSuperhumanHealth&&ProjectSuperhumanHealth."+method+"();",null));}
    private void emitRaw(String method,String args){runOnUiThread(() -> webView.evaluateJavascript("window.ProjectSuperhumanHealth&&ProjectSuperhumanHealth."+method+"("+args+");",null));}
}
