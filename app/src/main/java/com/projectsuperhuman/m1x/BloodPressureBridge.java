package com.projectsuperhuman.m1x;

import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BloodPressureBridge {
    static final int REQ_BP_CAMERA = 9631;
    private final MainActivitx activity;
    private Uri captureUri;
    private File captureFile;

    BloodPressureBridge(MainActivitx activity){ this.activity=activity; }

    @JavascriptInterface public String capability(){ return "native_camera_mlkit"; }

    @JavascriptInterface public void capture(){
        activity.runOnUiThread(() -> launchCamera());
    }

    private void launchCamera(){
        try{
            File dir=new File(activity.getCacheDir(),"bp-camera");
            if(!dir.exists())dir.mkdirs();
            captureFile=new File(dir,"bp_"+System.currentTimeMillis()+".jpg");
            captureUri=FileProvider.getUriForFile(activity,activity.getPackageName()+".fileprovider",captureFile);
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,captureUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent,REQ_BP_CAMERA);
            emitStatus("camera_opened","Camera opened. Fill the frame with the monitor display.");
        }catch(Exception e){ emitError("Could not open the camera."); }
    }

    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=REQ_BP_CAMERA)return false;
        if(resultCode!=android.app.Activity.RESULT_OK || captureUri==null){emitStatus("cancelled","Camera capture cancelled.");return true;}
        emitStatus("reading","Reading blood-pressure display…");
        try{
            InputImage image=InputImage.fromFilePath(activity,captureUri);
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(text -> { try{emitResult(parse(text));}finally{recognizer.close();cleanup();} })
                    .addOnFailureListener(err -> { recognizer.close();emitError("Could not read the monitor display. Try a closer, straighter photo.");cleanup(); });
        }catch(Exception e){emitError("Could not process the captured image.");cleanup();}
        return true;
    }

    private JSONObject parse(Text text){
        JSONObject out=new JSONObject();
        try{
            String raw=text==null?"":text.getText();
            out.put("rawText",raw);
            List<ReadingLine> lines=new ArrayList<>();
            if(text!=null) for(Text.TextBlock b:text.getTextBlocks()) for(Text.Line l:b.getLines()){
                android.graphics.Rect box=l.getBoundingBox();
                lines.add(new ReadingLine(l.getText(), box==null?0:box.centerY()));
            }
            Collections.sort(lines, Comparator.comparingInt(a->a.y));
            Integer sys=labelled(lines,"sys","systolic"), dia=labelled(lines,"dia","diastolic"), pulse=labelled(lines,"pul","pulse","heart rate","hr");
            List<Integer> nums=new ArrayList<>();
            Matcher m=Pattern.compile("(?<!\\d)(\\d{2,3})(?!\\d)").matcher(raw.replace('/',' '));
            while(m.find()){
                int v=Integer.parseInt(m.group(1));
                if(v>=20&&v<=300)nums.add(v);
            }
            if(sys==null||dia==null||pulse==null){
                List<Integer> plausible=new ArrayList<>();
                for(Integer n:nums)if(n>=30&&n<=260)plausible.add(n);
                if(sys==null){for(Integer n:plausible)if(n>=80&&n<=260){sys=n;break;}}
                if(dia==null && sys!=null){for(Integer n:plausible)if(n>=40&&n<=150&&n<sys){dia=n;break;}}
                if(pulse==null){for(int i=plausible.size()-1;i>=0;i--){int n=plausible.get(i);if(n>=30&&n<=220 && (dia==null||n!=dia) && (sys==null||n!=sys)){pulse=n;break;}}}
            }
            boolean plausible=sys!=null&&dia!=null&&sys>=70&&sys<=280&&dia>=35&&dia<=180&&sys>dia;
            out.put("systolic",sys==null?JSONObject.NULL:sys);
            out.put("diastolic",dia==null?JSONObject.NULL:dia);
            out.put("pulse",pulse==null?JSONObject.NULL:pulse);
            out.put("confidence",plausible?(pulse!=null?0.92:0.82):0.45);
            out.put("ok",plausible);
        }catch(Exception ignored){}
        return out;
    }

    private Integer labelled(List<ReadingLine> lines,String... labels){
        Pattern number=Pattern.compile("(?<!\\d)(\\d{2,3})(?!\\d)");
        for(int i=0;i<lines.size();i++){
            String low=lines.get(i).text.toLowerCase(); boolean hit=false;
            for(String s:labels)if(low.contains(s)){hit=true;break;}
            if(!hit)continue;
            for(int j=i;j<=Math.min(lines.size()-1,i+2);j++){
                Matcher m=number.matcher(lines.get(j).text);
                while(m.find()){int v=Integer.parseInt(m.group(1));if(v>=20&&v<=300)return v;}
            }
        }
        return null;
    }

    private void emitResult(JSONObject payload){
        activity.runOnUiThread(() -> activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeResult("+JSONObject.quote(payload.toString())+");",null));
    }
    private void emitStatus(String code,String message){
        try{JSONObject j=new JSONObject();j.put("code",code);j.put("message",message);activity.runOnUiThread(() -> activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeStatus("+JSONObject.quote(j.toString())+");",null));}catch(Exception ignored){}
    }
    private void emitError(String message){
        activity.runOnUiThread(() -> activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeError("+JSONObject.quote(message)+");",null));
    }
    private void cleanup(){try{if(captureFile!=null&&captureFile.exists())captureFile.delete();}catch(Exception ignored){}captureFile=null;captureUri=null;}
    private static final class ReadingLine{final String text;final int y;ReadingLine(String t,int y){this.text=t==null?"":t;this.y=y;}}
}
