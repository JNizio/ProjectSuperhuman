package com.projectsuperhuman.m1x;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BloodPressureBridge {
    static final int REQ_BP_CAMERA = 9631;
    private final MainActivitx activity;
    private Uri captureUri;
    private File captureFile;

    BloodPressureBridge(MainActivitx activity){ this.activity=activity; }

    @JavascriptInterface public String capability(){ return "native_camera_mlkit_multipass"; }

    @JavascriptInterface public void capture(){ activity.runOnUiThread(this::launchCamera); }

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
            emitStatus("camera_opened","Camera opened. Fill most of the frame with the monitor display.");
        }catch(Exception e){ emitError("Could not open the camera."); }
    }

    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=REQ_BP_CAMERA)return false;
        if(resultCode!=android.app.Activity.RESULT_OK || captureFile==null){emitStatus("cancelled","Camera capture cancelled.");return true;}
        emitStatus("reading","Reading SYS / DIA / pulse with enhanced display OCR…");
        try{
            Bitmap base=decodeSampled(captureFile,2200);
            if(base==null){emitError("Could not decode the camera photo.");cleanup();return true;}
            List<Variant> variants=makeVariants(base);
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            processVariant(recognizer,variants,0,null);
        }catch(Exception e){emitError("Could not process the captured image.");cleanup();}
        return true;
    }

    private void processVariant(TextRecognizer recognizer,List<Variant> variants,int index,ParseCandidate best){
        if(index>=variants.size()){
            try{recognizer.close();}catch(Exception ignored){}
            emitResult(toJson(best));
            for(Variant v:variants)try{if(v.bitmap!=null&&!v.bitmap.isRecycled())v.bitmap.recycle();}catch(Exception ignored){}
            cleanup();
            return;
        }
        Variant v=variants.get(index);
        try{
            recognizer.process(InputImage.fromBitmap(v.bitmap,0))
                    .addOnSuccessListener(text -> {
                        ParseCandidate c=parseOne(text,v.name);
                        ParseCandidate next=best;
                        if(c!=null&&(next==null||c.score>next.score))next=c;
                        processVariant(recognizer,variants,index+1,next);
                    })
                    .addOnFailureListener(err -> processVariant(recognizer,variants,index+1,best));
        }catch(Exception e){processVariant(recognizer,variants,index+1,best);}
    }

    private Bitmap decodeSampled(File f,int target){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
        int sample=1,max=Math.max(bounds.outWidth,bounds.outHeight);while(max/sample>target*1.35)sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getAbsolutePath(),o);
    }

    private List<Variant> makeVariants(Bitmap base){
        List<Variant> out=new ArrayList<>();
        out.add(new Variant("full",fit(base,1600)));
        Bitmap broad=crop(base,.14f,.08f,.72f,.80f);out.add(new Variant("display",enhance(broad,1700,1.65f,6f)));
        Bitmap center=crop(base,.23f,.14f,.57f,.68f);out.add(new Variant("display-close",enhance(center,1800,2.05f,12f)));
        Bitmap portrait=crop(base,.28f,.12f,.48f,.74f);out.add(new Variant("digits",enhance(portrait,1900,2.35f,18f)));
        if(base!=out.get(0).bitmap)try{base.recycle();}catch(Exception ignored){}
        return out;
    }

    private Bitmap crop(Bitmap b,float xf,float yf,float wf,float hf){
        int x=Math.max(0,Math.round(b.getWidth()*xf)),y=Math.max(0,Math.round(b.getHeight()*yf));
        int w=Math.min(b.getWidth()-x,Math.max(1,Math.round(b.getWidth()*wf))),h=Math.min(b.getHeight()-y,Math.max(1,Math.round(b.getHeight()*hf)));
        return Bitmap.createBitmap(b,x,y,w,h);
    }

    private Bitmap fit(Bitmap b,int maxLong){
        int w=b.getWidth(),h=b.getHeight(),longSide=Math.max(w,h);if(longSide<=maxLong)return b.copy(Bitmap.Config.ARGB_8888,false);
        float s=maxLong/(float)longSide;return Bitmap.createScaledBitmap(b,Math.max(1,Math.round(w*s)),Math.max(1,Math.round(h*s)),true);
    }

    private Bitmap enhance(Bitmap src,int maxLong,float contrast,float brightness){
        Bitmap scaled=fit(src,maxLong);if(src!=scaled)try{src.recycle();}catch(Exception ignored){}
        Bitmap out=Bitmap.createBitmap(scaled.getWidth(),scaled.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix sat=new ColorMatrix();sat.setSaturation(0f);
        float t=(-.5f*contrast+.5f)*255f+brightness;
        ColorMatrix cm=new ColorMatrix(new float[]{contrast,0,0,0,t,0,contrast,0,0,t,0,0,contrast,0,t,0,0,0,1,0});sat.postConcat(cm);
        p.setColorFilter(new ColorMatrixColorFilter(sat));c.drawBitmap(scaled,0,0,p);
        try{scaled.recycle();}catch(Exception ignored){}
        return out;
    }

    private ParseCandidate parseOne(Text text,String source){
        if(text==null)return null;
        List<NumberToken> tokens=new ArrayList<>();List<ReadingLine> lines=new ArrayList<>();StringBuilder raw=new StringBuilder();
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
            Rect lb=line.getBoundingBox();lines.add(new ReadingLine(line.getText(),lb==null?0:lb.centerX(),lb==null?0:lb.centerY()));raw.append(line.getText()).append('\n');
            for(Text.Element e:line.getElements()){
                Rect r=e.getBoundingBox();Integer value=toDisplayNumber(e.getText());
                if(value!=null&&value>=20&&value<=300)tokens.add(new NumberToken(value,r==null?0:r.centerX(),r==null?0:r.centerY(),r==null?1:r.height(),e.getText()));
            }
        }
        Collections.sort(tokens,Comparator.comparingInt(a->a.y));tokens=dedupe(tokens);
        Integer labelledSys=nearestLabel(lines,tokens,"sys","systolic"),labelledDia=nearestLabel(lines,tokens,"dia","diastolic"),labelledPulse=nearestLabel(lines,tokens,"pul","pulse","heart rate","hr");
        ParseCandidate best=null;
        if(validPair(labelledSys,labelledDia)){
            int pulse=validPulse(labelledPulse)?labelledPulse:0;best=new ParseCandidate(labelledSys,labelledDia,pulse,pulse>0?138:128,source+"-labels",raw.toString(),tokens.size());
        }
        for(int i=0;i<tokens.size();i++)for(int j=i+1;j<tokens.size();j++)for(int k=j+1;k<tokens.size();k++){
            NumberToken a=tokens.get(i),b=tokens.get(j),c=tokens.get(k);int s=a.value,d=b.value,p=c.value;if(!validPair(s,d)||!validPulse(p))continue;
            double score=80;
            if(s>=90&&s<=180)score+=10;if(d>=50&&d<=110)score+=10;if(p>=45&&p<=130)score+=9;
            int gap=s-d;if(gap>=20&&gap<=100)score+=8;
            float avgH=(a.h+b.h+c.h)/3f,maxH=1;for(NumberToken n:tokens)maxH=Math.max(maxH,n.h);score+=Math.min(18,18*avgH/maxH);
            if(a.y<b.y&&b.y<c.y)score+=8;if((b.y-a.y)>3&&(c.y-b.y)>3)score+=4;
            ParseCandidate cand=new ParseCandidate(s,d,p,score,source+"-stack",raw.toString(),tokens.size());if(best==null||cand.score>best.score)best=cand;
        }
        if(best==null){
            List<NumberToken> large=new ArrayList<>(tokens);large.sort((a,b)->Integer.compare(b.h,a.h));
            for(int i=0;i<large.size();i++)for(int j=0;j<large.size();j++)if(i!=j){int s=large.get(i).value,d=large.get(j).value;if(validPair(s,d)){best=new ParseCandidate(s,d,0,72,source+"-pair",raw.toString(),tokens.size());break;}if(best!=null)break;}
        }
        return best;
    }

    private List<NumberToken> dedupe(List<NumberToken> in){
        List<NumberToken> out=new ArrayList<>();for(NumberToken n:in){boolean dup=false;for(NumberToken x:out)if(x.value==n.value&&Math.abs(x.y-n.y)<10&&Math.abs(x.x-n.x)<28){if(n.h>x.h){out.remove(x);out.add(n);}dup=true;break;}if(!dup)out.add(n);}Collections.sort(out,Comparator.comparingInt(a->a.y));return out;
    }

    private Integer nearestLabel(List<ReadingLine> lines,List<NumberToken> nums,String... labels){
        for(ReadingLine l:lines){String low=l.text.toLowerCase(Locale.ROOT);boolean hit=false;for(String s:labels)if(low.contains(s)){hit=true;break;}if(!hit)continue;NumberToken best=null;double dist=Double.MAX_VALUE;for(NumberToken n:nums){double dy=Math.abs(n.y-l.y),dx=Math.abs(n.x-l.x);double d=dy*2+dx*.25;if(d<dist&&dy<120){dist=d;best=n;}}if(best!=null)return best.value;}return null;
    }

    private Integer toDisplayNumber(String s){
        if(s==null)return null;String t=s.toUpperCase(Locale.ROOT).trim();if(t.length()>8)return null;
        t=t.replace('I','1').replace('L','1').replace('|','1').replace('O','0').replace('Q','0').replace('S','5').replace('B','8').replace('G','6').replace('Z','2');
        t=t.replaceAll("[^0-9]","");if(t.length()<2||t.length()>3)return null;try{return Integer.parseInt(t);}catch(Exception e){return null;}
    }

    private boolean validPair(Integer s,Integer d){return s!=null&&d!=null&&s>=70&&s<=280&&d>=35&&d<=180&&s>d;}
    private boolean validPulse(Integer p){return p!=null&&p>=30&&p<=220;}

    private JSONObject toJson(ParseCandidate c){
        JSONObject out=new JSONObject();try{
            boolean ok=c!=null&&validPair(c.sys,c.dia);out.put("ok",ok);out.put("systolic",c==null?JSONObject.NULL:c.sys);out.put("diastolic",c==null?JSONObject.NULL:c.dia);out.put("pulse",c==null||c.pulse<=0?JSONObject.NULL:c.pulse);
            out.put("confidence",c==null?0.25:Math.min(.98,.55+c.score/300.0));out.put("method",c==null?"none":c.source);out.put("candidateCount",c==null?0:c.tokenCount);out.put("rawText",c==null?"":c.raw);
        }catch(Exception ignored){}return out;
    }

    private void emitResult(JSONObject payload){activity.runOnUiThread(() -> activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeResult("+JSONObject.quote(payload.toString())+");",null));}
    private void emitStatus(String code,String message){try{JSONObject j=new JSONObject();j.put("code",code);j.put("message",message);activity.runOnUiThread(() -> activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeStatus("+JSONObject.quote(j.toString())+");",null));}catch(Exception ignored){}}
    private void emitError(String message){activity.runOnUiThread(() -> activity.webView.evaluateJavascript("window.ProjectSuperhumanBP&&ProjectSuperhumanBP.onNativeError("+JSONObject.quote(message)+");",null));}
    private void cleanup(){try{if(captureFile!=null&&captureFile.exists())captureFile.delete();}catch(Exception ignored){}captureFile=null;captureUri=null;}

    private static final class Variant{final String name;final Bitmap bitmap;Variant(String n,Bitmap b){name=n;bitmap=b;}}
    private static final class NumberToken{final int value,x,y,h;final String raw;NumberToken(int v,int x,int y,int h,String r){value=v;this.x=x;this.y=y;this.h=h;raw=r;}}
    private static final class ReadingLine{final String text;final int x,y;ReadingLine(String t,int x,int y){text=t==null?"":t;this.x=x;this.y=y;}}
    private static final class ParseCandidate{final int sys,dia,pulse,tokenCount;final double score;final String source,raw;ParseCandidate(int s,int d,int p,double sc,String src,String raw,int n){sys=s;dia=d;pulse=p;score=sc;source=src;this.raw=raw;tokenCount=n;}}
}
