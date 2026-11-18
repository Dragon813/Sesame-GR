package io.github.Dragon813.sesame.hook;

import java.util.ArrayList;
import java.util.List;

/** Narrow fallback for a dark, curved source contour with its upper section occluded. */
final class PuzzleOccludedContourMatcher {
    private static final class Point {
        int x,y; float gx,gy;
        Point(int x,int y,float gx,float gy){this.x=x;this.y=y;this.gx=gx;this.gy=gy;}
    }
    static PuzzleSliderMatcherCore.Result estimate(int width,int height,float sliderY,int screenTop,
            PuzzleSliderMatcherCore.PixelReader reader,long budgetMs,int sourceOverride) {
        long started=System.nanoTime(),deadline=started+budgetMs*1000000L;
        float sx=width/1264f,sy=height/2780f;
        int source=sourceOverride>=0?sourceOverride:Math.round(166*sx),tw=Math.max(72,Math.round(180*sx)),th=Math.max(72,Math.round(180*sy));
        int top=Math.round(sliderY-screenTop)+Math.round(-707*sy),bottom=Math.round(sliderY-screenTop)+Math.round(-287*sy);
        int left=Math.max(1,source-2),roiTop=Math.max(1,top-Math.round(34*sy));
        int w=Math.min(width-1,source+tw+Math.round(800*sx)+2)-left;
        int h=Math.min(height-1,bottom+th+Math.round(34*sy))-roiTop;
        if(budgetMs<=0||source<3||w<=tw||h<=th||(long)w*h>4000000)return null;
        int[] colors=new int[w*h];try{reader.read(left,roiTop,w,h,colors);}catch(Exception e){return null;}
        float[] gray=new float[colors.length],gx=new float[colors.length],gy=new float[colors.length],mag=new float[colors.length];
        for(int i=0;i<colors.length;i++){int c=colors[i];gray[i]=(77*((c>>>16)&255)+150*((c>>>8)&255)+29*(c&255))/256f;}
        int r=Math.max(2,Math.round(2*sx));
        for(int y=r;y<h-r;y++){
          if(System.nanoTime()>=deadline||Thread.currentThread().isInterrupted())return null;
          for(int x=r;x<w-r;x++){
            int i=y*w+x;float xx=gray[i+r]-gray[i-r],yy=gray[i+r*w]-gray[i-r*w];
            float m=(float)Math.sqrt(xx*xx+yy*yy);mag[i]=m;if(m>0){gx[i]=xx/m;gy[i]=yy/m;}
        }
        }
        boolean[] seen=new boolean[colors.length];int[] queue=new int[tw*h];
        int x0=source-left,x1=x0+tw,y0=Math.max(r,top-roiTop),y1=Math.min(h-r,bottom-roiTop+th);
        float best=0,second=0;int bestDx=0,bestDy=0,bx0=0,by0=0,bx1=0,by1=0;
        float[] scores=new float[Math.round(800*sx)+1];
        for(int y=y0;y<y1;y++){
          if(System.nanoTime()>=deadline||Thread.currentThread().isInterrupted())return null;
          for(int x=x0;x<x1;x++){
            int idx=y*w+x;if(seen[idx]||gray[idx]>=100)continue;
            int head=0,tail=0;queue[tail++]=idx;seen[idx]=true;
            int minX=x,maxX=x,minY=y,maxY=y;double sumX=0,sumY=0,sumXX=0,sumYY=0,sumXY=0;
            while(head<tail){int i=queue[head++],xx=i%w,yy=i/w;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
                sumX+=xx;sumY+=yy;sumXX+=xx*xx;sumYY+=yy*yy;sumXY+=xx*yy;
                for(int n:new int[]{i-1,i+1,i-w,i+w}){int nx=n%w,ny=n/w;if(nx>=x0&&nx<x1&&ny>=y0&&ny<y1&&!seen[n]&&gray[n]<100){seen[n]=true;queue[tail++]=n;}}
            }
            int bw=maxX-minX+1,bh=maxY-minY+1;double fill=tail/(double)(bw*bh);
            double vx=sumXX/tail-Math.pow(sumX/tail,2),vy=sumYY/tail-Math.pow(sumY/tail,2),cov=sumXY/tail-sumX*sumY/(tail*(double)tail);
            double curvature=(vx*vy-cov*cov)/Math.max(1,(vx+vy)*(vx+vy));
            if(bw<40*sx||bw>tw-4||bh<70*sy||bh>th||fill<.03||fill>.18||curvature<.003||minX<=x0+1||maxX>=x1-2||minY<=y0+1||maxY>=y1-2)continue;
            // The upper quarter may be covered by help/refresh controls. Keep
            // the lower bend; straight edges fail the covariance curvature gate.
            List<Point> points=new ArrayList<>();int step=Math.max(2,Math.round(3*sx));
            for(int k=0;k<tail;k++){int i=queue[k],xx=i%w,yy=i/w;if(yy<minY+bh/4||xx%step!=0||yy%step!=0||mag[i]<70)continue;points.add(new Point(xx,yy,gx[i],gy[i]));}
            if(points.size()<18)continue;
            int minDx=Math.round(250*sx),maxDx=Math.min(scores.length-1,w-maxX-r-2),dyLimit=Math.max(2,Math.round(6*sy));
            for(int dx=minDx;dx<=maxDx;dx++){
                if(System.nanoTime()>=deadline||Thread.currentThread().isInterrupted())return null;
                for(int dy=-dyLimit;dy<=dyLimit;dy++){
                    float total=0;int support=0;
                    for(Point p:points){float hit=0;int cx=p.x+dx,cy=p.y+dy;
                        for(int yy=cy-2;yy<=cy+2;yy++)for(int xx=cx-2;xx<=cx+2;xx++){
                            int j=yy*w+xx; if(j<0||j>=mag.length||mag[j]<45)continue;
                            float dot=Math.abs(p.gx*gx[j]+p.gy*gy[j]);if(dot>=.90f)hit=Math.max(hit,dot*Math.min(1,mag[j]/70));
                        }
                        total+=hit;if(hit>=.8f)support++;
                    }
                    float score=total/points.size();if(support<points.size()*.80)continue;
                    scores[dx]=Math.max(scores[dx],score);
                    if(score>best){best=score;bestDx=dx;bestDy=dy;bx0=minX;by0=minY;bx1=maxX;by1=maxY;}
                }
            }
        }
        }
        for(int dx=0;dx<scores.length;dx++)if(Math.abs(dx-bestDx)>Math.round(12*sx))second=Math.max(second,scores[dx]);
        // Require high oriented-edge coverage AND a large independent-peak gap.
        // This is a new fallback; legacy scores, tolerances and branches stay intact.
        if(best<.82f||best-second<.20f||Math.abs(bestDy)>Math.round(6*sy)
                ||bestDx<=Math.round(250*sx)+2||bestDx>=Math.round(800*sx)-2)return null;
        return PuzzleSliderMatcherCore.Result.success(bestDx,bestDx,best,"occluded-contour-consensus",Math.round(800*sx),(System.nanoTime()-started)/1000000,
            left+bx0,roiTop+by0,left+bx0+bestDx,roiTop+by0+bestDy,bx1-bx0+1,by1-by0+1);
    }
}
