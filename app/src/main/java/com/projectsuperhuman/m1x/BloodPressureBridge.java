package com.projectsuperhuman.m1x;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;

public final class BloodPressureBridge {
    static final int REQ_BP_CAMERA=9631;
    private final MainActivitx activity;
    private Uri captureUri; private File captureFile;
    BloodPressureBridge(MainActivitx a){activity=a;}

    @JavascriptInterface public String capability(){return "native_camera_seven_segment";}
    @JavascriptInterface public void capture(){activity.runOnUiThread(this::launchCamera);}

    private void launchCamera(){
        try{
            File dir=new File(activity.getCacheDir(),"bp-camera");if(!dir.exists())dir.mkdirs();
            captureFile=new File(dir,"bp_"+System.currentTimeMillis()+".jpg");
            captureUri=FileProvider.getUriForFile(activity,activity.getPackageName()+".fileprovider",captureFile);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,captureUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(i,REQ_BP_CAMERA);
            emitStatus("camera_opened","Fill the frame with the LCD display and keep it straight.");
        }catch(Exception e){emitError("Could not open the camera.");}
    }

    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=REQ_BP_CAMERA)return false;
        if(resultCode!=android.app.Activity.RESULT_OK||captureFile==null){emitStatus("cancelled","Camera capture cancelled.");return true;}
        emitStatus("reading","Reading the seven-segment LCD…");
        new Thread(()->{
            try{
                Bitmap b=decodeSampled(captureFile,2200);
                SevenSegmentDecoder.Result r=SevenSegmentDecoder.decode(b);
                if(b!=null&&!b.isRecycled())b.recycle();
                emitResult(resultJson(r));
            }catch(Exception e){emitError("Could not analyse the LCD display.");}
            finally{cleanup();}
        },"psh-bp-7seg").start();
        return true;
    }

    private Bitmap decodeSampled(File f,int target){
        BitmapFactory.Options bo=new BitmapFactory.Options();bo.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),bo);
        int sample=1,max=Math.max(bo.outWidth,bo.outHeight);while(max/sample>target*1.35)sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getAbsolutePath(),o);
    }

    private JSONObject resultJson(SevenSegmentDecoder.Result r){
        JSONObject j=new JSONObject();try{
            boolean ok=r!=null&&r.valid();j.put("ok",ok);j.put("method","seven_segment_lcd");
            j.put("systolic",ok?r.sys:JSONObject.NULL);j.put("diastolic",ok?r.dia:JSONObject.NULL);j.put("pulse",ok?r.pulse:JSONObject.NULL);
            j.put("confidence",ok?Math.min(.98,.72+r.score/500.0):.15);j.put("debug",r==null?"no segment pattern found":r.debug);
        }catch(Exception ignored){}return j;
    }

    private void emitResult(JSONObject p){activity.runOnUiThread(()->activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeResult("+JSONObject.quote(p.toString())+");",null));}
    private void emitStatus(String code,String msg){try{JSONObject j=new JSONObject();j.put("code",code);j.put("message",msg);activity.runOnUiThread(()->activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeStatus("+JSONObject.quote(j.toString())+");",null));}catch(Exception ignored){}}
    private void emitError(String msg){activity.runOnUiThread(()->activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeError("+JSONObject.quote(msg)+");",null));}
    private void cleanup(){try{if(captureFile!=null&&captureFile.exists())captureFile.delete();}catch(Exception ignored){}captureFile=null;captureUri=null;}
}