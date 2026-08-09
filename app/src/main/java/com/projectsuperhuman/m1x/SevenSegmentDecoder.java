package com.projectsuperhuman.m1x;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Purpose-built seven-segment LCD reader. Clean-room implementation inspired by
 * the general segment-sampling approach used by open-source seven-segment OCR tools.
 */
final class SevenSegmentDecoder {
    static final class Result {
        final int sys,dia,pulse; final double score; final String debug;
        Result(int s,int d,int p,double sc,String dbg){sys=s;dia=d;pulse=p;score=sc;debug=dbg;}
        boolean valid(){return sys>=70&&sys<=280&&dia>=35&&dia<=180&&sys>dia&&pulse>=30&&pulse<=220;}
    }
    private static final int[] DIGIT_MASK={0x77,0x24,0x5D,0x6D,0x2E,0x6B,0x7B,0x25,0x7F,0x6F};

    static Result decode(Bitmap src){
        if(src==null)return null;
        List<Result> all=new ArrayList<>();
        float[][] crops={{.12f,.08f,.76f,.78f},{.18f,.10f,.66f,.74f},{.22f,.14f,.58f,.68f},{.27f,.16f,.50f,.64f},{.32f,.18f,.43f,.60f}};
        for(float[] c:crops){Bitmap b=crop(src,c[0],c[1],c[2],c[3]);if(b==null)continue;for(int bias:new int[]{-24,-10,0,12,24}){Result r=decodeCrop(b,bias);if(r!=null)all.add(r);}if(b!=src)b.recycle();}
        Result best=null;for(Result r:all)if(r.valid()&&(best==null||r.score>best.score))best=r;
        return best;
    }

    private static Result decodeCrop(Bitmap b,int bias){
        int w=b.getWidth(),h=b.getHeight();if(w<120||h<160)return null;
        int[] gray=new int[w*h];long sum=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=b.getPixel(x,y);int g=(Color.red(p)*30+Color.green(p)*59+Color.blue(p)*11)/100;gray[y*w+x]=g;sum+=g;}
        int mean=(int)(sum/(w*(long)h));int threshold=Math.max(35,Math.min(220,mean+bias-25));
        // BP monitors commonly place SYS / DIA / PULSE vertically. Test slightly overlapping bands.
        int[][] bands={{8,39},{31,66},{58,93}};
        Row[] rows=new Row[3];for(int i=0;i<3;i++)rows[i]=decodeRow(gray,w,h,bands[i][0]*h/100,bands[i][1]*h/100,threshold);
        if(rows[0]==null||rows[1]==null||rows[2]==null)return null;
        int s=rows[0].value,d=rows[1].value,p=rows[2].value;if(s<0||d<0||p<0)return null;
        double sc=rows[0].score+rows[1].score+rows[2].score;
        if(s>=90&&s<=180)sc+=12;if(d>=50&&d<=110)sc+=12;if(p>=45&&p<=130)sc+=10;if(s>d&&s-d>=20&&s-d<=100)sc+=12;
        return new Result(s,d,p,sc,"7seg t="+threshold+" rows="+rows[0].debug+"/"+rows[1].debug+"/"+rows[2].debug);
    }

    private static Row decodeRow(int[] g,int w,int h,int y0,int y1,int thr){
        y0=Math.max(0,y0);y1=Math.min(h,y1);if(y1-y0<20)return null;
        // Ignore left label area; search central/right part for large LCD digits.
        int sx=(int)(w*.22),ex=(int)(w*.94),rh=y1-y0;int[] ink=new int[ex-sx];
        for(int x=sx;x<ex;x++){int n=0;for(int y=y0;y<y1;y++)if(g[y*w+x]<thr)n++;ink[x-sx]=n;}
        int colMin=Math.max(2,(int)(rh*.055));List<int[]> runs=new ArrayList<>();int st=-1;
        for(int i=0;i<ink.length;i++){boolean on=ink[i]>=colMin;if(on&&st<0)st=i;if((!on||i==ink.length-1)&&st>=0){int en=on&&i==ink.length-1?i:i-1;if(en-st>=2)runs.add(new int[]{st+sx,en+sx});st=-1;}}
        // Merge pieces separated by small gaps because seven-segment vertical bars are disconnected at the middle.
        List<int[]> merged=new ArrayList<>();for(int[] r:runs){if(merged.isEmpty())merged.add(r);else{int[] q=merged.get(merged.size()-1);int gap=r[0]-q[1]-1;if(gap<=Math.max(4,(y1-y0)/16))q[1]=r[1];else merged.add(r);}}
        // Candidate digit clusters should be tall enough and not tiny label glyphs. Evaluate 2- and 3-digit windows from right to left.
        Row best=null;for(int count:new int[]{3,2})for(int start=Math.max(0,merged.size()-6);start+count<=merged.size();start++){
            int left=merged.get(start)[0],right=merged.get(start+count-1)[1];int width=right-left+1;if(width<(y1-y0)*.55||width>(y1-y0)*2.8)continue;
            int slotW=width/count;StringBuilder digits=new StringBuilder();double score=0;boolean ok=true;
            for(int k=0;k<count;k++){int dx0=left+k*slotW,dx1=k==count-1?right:left+(k+1)*slotW-1;Digit d=readDigit(g,w,y0,y1,dx0,dx1,thr);if(d.value<0){ok=false;break;}digits.append(d.value);score+=d.score;}
            if(!ok)continue;int value;try{value=Integer.parseInt(digits.toString());}catch(Exception e){continue;}if(value<20||value>300)continue;
            score+=count==3?5:3;Row row=new Row(value,score,digits+"@"+left+":"+right);if(best==null||row.score>best.score)best=row;
        }
        return best;
    }

    private static Digit readDigit(int[] g,int w,int y0,int y1,int x0,int x1,int thr){
        int dw=x1-x0+1,dh=y1-y0;if(dw<8||dh<18)return new Digit(-1,0);
        // Seven canonical zones: top, upper-right, lower-right, bottom, lower-left, upper-left, middle.
        float[][] z={{.20f,.03f,.80f,.19f},{.70f,.13f,.97f,.48f},{.70f,.52f,.97f,.88f},{.20f,.81f,.80f,.98f},{.03f,.52f,.30f,.88f},{.03f,.13f,.30f,.48f},{.20f,.43f,.80f,.60f}};
        double[] ratio=new double[7];for(int i=0;i<7;i++)ratio[i]=darkRatio(g,w,y0,y1,x0,x1,thr,z[i]);
        int mask=0;for(int i=0;i<7;i++)if(ratio[i]>.23)mask|=1<<(6-i);
        int best=-1,dist=99;double confidence=0;for(int d=0;d<10;d++){int hd=Integer.bitCount(mask^DIGIT_MASK[d]);if(hd<dist){dist=hd;best=d;}}
        if(dist>2)return new Digit(-1,0);
        for(double r:ratio)confidence+=Math.abs(r-.23);confidence=confidence/7.0*16+(2-dist)*7;
        return new Digit(best,confidence);
    }

    private static double darkRatio(int[] g,int w,int y0,int y1,int x0,int x1,int thr,float[] z){
        int dw=x1-x0+1,dh=y1-y0;int xa=x0+(int)(dw*z[0]),ya=y0+(int)(dh*z[1]),xb=x0+(int)(dw*z[2]),yb=y0+(int)(dh*z[3]);xa=Math.max(x0,xa);xb=Math.min(x1,xb);ya=Math.max(y0,ya);yb=Math.min(y1-1,yb);int n=0,total=0;for(int y=ya;y<=yb;y++)for(int x=xa;x<=xb;x++){total++;if(g[y*w+x]<thr)n++;}return total==0?0:n/(double)total;
    }

    private static Bitmap crop(Bitmap b,float xf,float yf,float wf,float hf){int x=Math.max(0,(int)(b.getWidth()*xf)),y=Math.max(0,(int)(b.getHeight()*yf));int w=Math.min(b.getWidth()-x,Math.max(1,(int)(b.getWidth()*wf))),h=Math.min(b.getHeight()-y,Math.max(1,(int)(b.getHeight()*hf)));try{return Bitmap.createBitmap(b,x,y,w,h);}catch(Exception e){return null;}}
    private static final class Digit{final int value;final double score;Digit(int v,double s){value=v;score=s;}}
    private static final class Row{final int value;final double score;final String debug;Row(int v,double s,String d){value=v;score=s;debug=d;}}
}