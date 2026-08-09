package com.projectsuperhuman.m1x;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Hybrid BP monitor reader with perspective-corrected LCD detection. */
final class HybridBloodPressureDecoder {
    static final class Result {
        final int sys, dia, pulse;
        final double confidence;
        final String method, debug;
        Result(int s,int d,int p,double c,String m,String dbg){sys=s;dia=d;pulse=p;confidence=c;method=m;debug=dbg;}
        boolean valid(){return sys>=70&&sys<=280&&dia>=35&&dia<=180&&sys>dia&&pulse>=30&&pulse<=220;}
    }
    private static final class Vote {
        final int s,d,p; double score; int count; final String source;
        Vote(int s0,int d0,int p0,double sc,String src){s=s0;d=d0;p=p0;score=sc;count=1;source=src;}
    }

    static Result decode(Bitmap src){
        if(src==null)return null;
        Mat rgba=new Mat();
        try{
            Utils.bitmapToMat(src,rgba);
            if(rgba.empty())return null;
            Mat gray=new Mat();
            if(rgba.channels()==4) Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            else if(rgba.channels()==3) Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGB2GRAY);
            else rgba.copyTo(gray);
            double scale=Math.min(1.0,1800.0/Math.max(gray.cols(),gray.rows()));
            if(scale<0.999) Imgproc.resize(gray,gray,new Size(Math.round(gray.cols()*scale),Math.round(gray.rows()*scale)),0,0,Imgproc.INTER_AREA);

            Mat enhanced=new Mat();
            Imgproc.bilateralFilter(gray,enhanced,7,38,38);
            org.opencv.imgproc.CLAHE clahe=Imgproc.createCLAHE(2.6,new Size(8,8));
            clahe.apply(enhanced,enhanced);

            List<Vote> votes=new ArrayList<>();

            // First preference: find a four-corner LCD region and rectify perspective.
            List<Mat> warped=findPerspectiveDisplays(enhanced);
            int wi=0;
            for(Mat w:warped){
                evaluateAll(w,votes,"warp"+(++wi));
                w.release();
                if(wi>=5)break;
            }

            // Second preference: rectangular ROIs plus fallbacks for monitors where the
            // LCD bezel is weak or partly hidden by glare.
            List<Rect> rois=findDisplayCandidates(enhanced);
            addFallbackRois(rois,enhanced.cols(),enhanced.rows());
            int roiIndex=0;
            for(Rect r:rois){
                if(roiIndex++>=8)break;
                Mat roi=new Mat(enhanced,r);
                evaluateAll(roi,votes,"roi"+roiIndex);
                roi.release();
            }
            gray.release();enhanced.release();
            return chooseConsensus(votes);
        }catch(Throwable t){
            return null;
        }finally{rgba.release();}
    }

    private static void evaluateAll(Mat roi,List<Vote> votes,String prefix){
        evaluateVariant(roi,votes,prefix+"-gray");
        Mat adaptive=new Mat();
        int block=Math.max(15,((Math.min(roi.cols(),roi.rows())/10)|1));
        Imgproc.adaptiveThreshold(roi,adaptive,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,7);
        evaluateVariant(adaptive,votes,prefix+"-adaptive");
        Mat adaptiveInv=new Mat();
        Core.bitwise_not(adaptive,adaptiveInv);
        evaluateVariant(adaptiveInv,votes,prefix+"-adaptiveInv");
        Mat otsu=new Mat();
        Imgproc.threshold(roi,otsu,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
        evaluateVariant(otsu,votes,prefix+"-otsu");
        for(double angle:new double[]{-6.0,-3.0,3.0,6.0}){
            Mat rot=rotate(roi,angle);evaluateVariant(rot,votes,prefix+"-rot"+angle);rot.release();
        }
        adaptive.release();adaptiveInv.release();otsu.release();
    }

    private static void evaluateVariant(Mat m,List<Vote> votes,String source){
        if(m==null||m.empty()||m.cols()<100||m.rows()<120)return;
        Mat use=m,enlarged=null;
        if(Math.max(m.cols(),m.rows())<1000){
            enlarged=new Mat();Imgproc.resize(m,enlarged,new Size(m.cols()*1.7,m.rows()*1.7),0,0,Imgproc.INTER_CUBIC);use=enlarged;
        }
        Bitmap b=null;
        try{
            Mat out=new Mat();
            if(use.channels()==1) Imgproc.cvtColor(use,out,Imgproc.COLOR_GRAY2RGBA); else use.copyTo(out);
            b=Bitmap.createBitmap(out.cols(),out.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(out,b);out.release();
            SevenSegmentDecoder.Result r=SevenSegmentDecoder.decode(b);
            if(r!=null&&r.valid()) addVote(votes,r.sys,r.dia,r.pulse,r.score,source);
        }catch(Throwable ignored){}finally{
            if(b!=null&&!b.isRecycled())b.recycle();
            if(enlarged!=null)enlarged.release();
        }
    }

    private static void addVote(List<Vote> votes,int s,int d,int p,double score,String source){
        for(Vote v:votes){
            if(Math.abs(v.s-s)<=1&&Math.abs(v.d-d)<=1&&Math.abs(v.p-p)<=1){v.count++;v.score+=score;return;}
        }
        votes.add(new Vote(s,d,p,score,source));
    }

    private static Result chooseConsensus(List<Vote> votes){
        if(votes.isEmpty())return null;
        Vote best=null;double bestRank=-1;
        for(Vote v:votes){
            double avg=v.score/Math.max(1,v.count);
            double rank=avg+v.count*52.0;
            if(v.s>=90&&v.s<=180)rank+=12;if(v.d>=50&&v.d<=110)rank+=12;if(v.p>=45&&v.p<=130)rank+=8;
            int pp=v.s-v.d;if(pp>=25&&pp<=90)rank+=9;
            if(best==null||rank>bestRank){best=v;bestRank=rank;}
        }
        // Avoid confident-looking single-pass guesses. Multiple agreeing variants matter most.
        double conf=Math.min(.995,.40+Math.min(6,best.count)*.095+Math.min(.15,bestRank/1400.0));
        return new Result(best.s,best.d,best.p,conf,"opencv_perspective_7seg_consensus","votes="+best.count+" source="+best.source+" rank="+Math.round(bestRank));
    }

    private static List<Mat> findPerspectiveDisplays(Mat gray){
        List<Mat> out=new ArrayList<>();
        Mat edges=new Mat();Imgproc.Canny(gray,edges,35,120);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(9,9));
        Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();
        Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        double frameArea=gray.cols()*(double)gray.rows();
        List<MatOfPoint2f> quads=new ArrayList<>();
        for(MatOfPoint c:contours){
            double area=Math.abs(Imgproc.contourArea(c));
            if(area<frameArea*.035||area>frameArea*.80){c.release();continue;}
            MatOfPoint2f c2=new MatOfPoint2f(c.toArray());
            double peri=Imgproc.arcLength(c2,true);
            MatOfPoint2f approx=new MatOfPoint2f();
            Imgproc.approxPolyDP(c2,approx,peri*.025,true);
            if(approx.total()==4){
                Point[] p=approx.toArray();
                Rect br=Imgproc.boundingRect(new MatOfPoint(p));
                double ar=br.width/(double)Math.max(1,br.height);
                if(ar>.45&&ar<2.4&&br.width>150&&br.height>120) quads.add(new MatOfPoint2f(p));
            }
            approx.release();c2.release();c.release();
        }
        hierarchy.release();edges.release();kernel.release();
        Collections.sort(quads,new Comparator<MatOfPoint2f>(){
            public int compare(MatOfPoint2f a,MatOfPoint2f b){return Double.compare(Math.abs(Imgproc.contourArea(b)),Math.abs(Imgproc.contourArea(a)));}
        });
        int n=0;
        for(MatOfPoint2f q:quads){
            if(n++>=6){q.release();continue;}
            Mat w=warpQuad(gray,q.toArray());if(w!=null&&!w.empty())out.add(w);q.release();
        }
        return out;
    }

    private static Mat warpQuad(Mat src,Point[] pts){
        if(pts==null||pts.length!=4)return null;
        Point tl=null,tr=null,br=null,bl=null;
        double minSum=Double.MAX_VALUE,maxSum=-Double.MAX_VALUE,minDiff=Double.MAX_VALUE,maxDiff=-Double.MAX_VALUE;
        for(Point p:pts){double sum=p.x+p.y,diff=p.y-p.x;if(sum<minSum){minSum=sum;tl=p;}if(sum>maxSum){maxSum=sum;br=p;}if(diff<minDiff){minDiff=diff;tr=p;}if(diff>maxDiff){maxDiff=diff;bl=p;}}
        if(tl==null||tr==null||br==null||bl==null)return null;
        double width=Math.max(dist(br,bl),dist(tr,tl));double height=Math.max(dist(tr,br),dist(tl,bl));
        if(width<120||height<100)return null;
        int W=(int)Math.min(1200,Math.max(240,width));int H=(int)Math.min(1200,Math.max(220,height));
        MatOfPoint2f from=new MatOfPoint2f(tl,tr,br,bl);
        MatOfPoint2f to=new MatOfPoint2f(new Point(0,0),new Point(W-1,0),new Point(W-1,H-1),new Point(0,H-1));
        Mat m=Imgproc.getPerspectiveTransform(from,to);Mat out=new Mat();
        Imgproc.warpPerspective(src,out,m,new Size(W,H),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE,new Scalar(255));
        from.release();to.release();m.release();return out;
    }
    private static double dist(Point a,Point b){double x=a.x-b.x,y=a.y-b.y;return Math.sqrt(x*x+y*y);}

    private static List<Rect> findDisplayCandidates(Mat gray){
        List<Rect> out=new ArrayList<>();
        Mat edges=new Mat();Imgproc.Canny(gray,edges,45,130);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(7,7));
        Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();
        Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        final double area=gray.cols()*(double)gray.rows();
        for(MatOfPoint c:contours){Rect r=Imgproc.boundingRect(c);double a=r.area();double ar=r.width/(double)Math.max(1,r.height);if(a>area*.055&&a<area*.82&&ar>.45&&ar<2.2&&r.width>150&&r.height>150)out.add(expand(r,gray.cols(),gray.rows(),.06));c.release();}
        hierarchy.release();edges.release();kernel.release();
        Collections.sort(out,new Comparator<Rect>(){public int compare(Rect a,Rect b){return Double.compare(b.area(),a.area());}});
        List<Rect> unique=new ArrayList<>();for(Rect r:out){boolean dup=false;for(Rect q:unique)if(iou(r,q)>.72){dup=true;break;}if(!dup)unique.add(r);if(unique.size()>=5)break;}return unique;
    }
    private static void addFallbackRois(List<Rect> rs,int w,int h){float[][] f={{.12f,.06f,.76f,.86f},{.20f,.10f,.62f,.78f},{.26f,.12f,.52f,.74f}};for(float[] a:f){Rect r=new Rect((int)(w*a[0]),(int)(h*a[1]),Math.max(1,(int)(w*a[2])),Math.max(1,(int)(h*a[3])));if(r.x+r.width>w)r.width=w-r.x;if(r.y+r.height>h)r.height=h-r.y;rs.add(r);}}
    private static Rect expand(Rect r,int w,int h,double p){int dx=(int)(r.width*p),dy=(int)(r.height*p);int x=Math.max(0,r.x-dx),y=Math.max(0,r.y-dy);int x2=Math.min(w,r.x+r.width+dx),y2=Math.min(h,r.y+r.height+dy);return new Rect(x,y,Math.max(1,x2-x),Math.max(1,y2-y));}
    private static double iou(Rect a,Rect b){int x1=Math.max(a.x,b.x),y1=Math.max(a.y,b.y),x2=Math.min(a.x+a.width,b.x+b.width),y2=Math.min(a.y+a.height,b.y+b.height);double inter=Math.max(0,x2-x1)*(double)Math.max(0,y2-y1);return inter/(a.area()+b.area()-inter+1e-6);}
    private static Mat rotate(Mat src,double angle){Point c=new Point(src.cols()/2.0,src.rows()/2.0);Mat m=Imgproc.getRotationMatrix2D(c,angle,1.0);Mat out=new Mat();Imgproc.warpAffine(src,out,m,src.size(),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE,new Scalar(255));m.release();return out;}
}
