package io.github.Dragon813.sesame.hook;

import java.util.ArrayList;
import java.util.List;

/** Interior texture consensus: the same displacement must explain two patch sizes. */
final class PuzzleTextureMatcherCore {
    private static final class Candidate {
        float score;
        float alternative = -1f;
        int dx, dy, cy, anchor;
        Candidate(float score, int dx, int dy, int cy) {
            this.score = score; this.dx = dx; this.dy = dy; this.cy = cy;
        }
    }

    static PuzzleSliderMatcherCore.Result estimate(int width, int height, float sliderScreenY,
            int screenTop, PuzzleSliderMatcherCore.PixelReader reader, long budgetMs, int sourceOverride) {
        return estimateProfile(width, height, sliderScreenY, screenTop, reader, budgetMs, sourceOverride, false, false, false);
    }

    static PuzzleSliderMatcherCore.Result estimateCompact(int width, int height, float sliderScreenY,
            int screenTop, PuzzleSliderMatcherCore.PixelReader reader, long budgetMs, int sourceOverride) {
        return estimateProfile(width, height, sliderScreenY, screenTop, reader, budgetMs, sourceOverride, true, false, false);
    }

    static PuzzleSliderMatcherCore.Result estimateNarrow(int width, int height, float sliderScreenY,
            int screenTop, PuzzleSliderMatcherCore.PixelReader reader, long budgetMs, int sourceOverride) {
        return estimateProfile(width, height, sliderScreenY, screenTop, reader, budgetMs, sourceOverride, true, true, false);
    }

    static PuzzleSliderMatcherCore.Result estimateLocalized(int width, int height, float sliderScreenY,
            int screenTop, PuzzleSliderMatcherCore.PixelReader reader, long budgetMs, int sourceOverride) {
        return estimateProfile(width, height, sliderScreenY, screenTop, reader, budgetMs, sourceOverride, true, true, true);
    }

    private static PuzzleSliderMatcherCore.Result estimateProfile(int width, int height, float sliderScreenY,
            int screenTop, PuzzleSliderMatcherCore.PixelReader reader, long budgetMs, int sourceOverride,
            boolean compact, boolean narrow, boolean localized) {
        long start = System.nanoTime(), deadline = start + budgetMs * 1_000_000L;
        if (budgetMs <= 0 || width <= 0 || height <= 0 || reader == null) return null;
        float sx = width / 1264f, sy = height / 2780f;
        int source = sourceOverride >= 0 ? sourceOverride : Math.round(166 * sx);
        int sliderY = Math.round(sliderScreenY - screenTop);
        int tw = Math.max(72, Math.round(180 * sx)), th = Math.max(72, Math.round(180 * sy));
        int top = sliderY + Math.round(-707 * sy), bottom = sliderY + Math.round(-287 * sy);
        int left = Math.max(1, source - 2), roiTop = Math.max(1, top - Math.round(34 * sy));
        int right = Math.min(width - 1, source + tw + Math.round(800 * sx) + 2);
        int roiBottom = Math.min(height - 1, bottom + th + Math.round(34 * sy));
        int w = right - left, h = roiBottom - roiTop;
        if (w <= tw || h <= th || w * (long) h > 4_000_000) return null;
        int[] pixels = new int[w * h];
        try { reader.read(left, roiTop, w, h, pixels); }
        catch (Throwable invalid) { return null; }
        float[] gray = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            gray[i] = (77 * ((c >>> 16) & 255) + 150 * ((c >>> 8) & 255) + 29 * (c & 255)) / 256f;
        }
        // Integral-image box high pass isolates texture from smooth sky/brightness gradients.
        double[] sum = new double[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            double row = 0;
            for (int x = 0; x < w; x++) {
                row += gray[y * w + x];
                sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
            }
        }
        int radius = Math.max(2, Math.round(4 * sx));
        float[] detail = new float[pixels.length];
        for (int y = radius; y < h - radius; y++) for (int x = radius; x < w - radius; x++) {
            int x0=x-radius, x1=x+radius+1, y0=y-radius, y1=y+radius+1;
            double mean = (sum[y1*(w+1)+x1]-sum[y0*(w+1)+x1]-sum[y1*(w+1)+x0]+sum[y0*(w+1)+x0]) / ((x1-x0)*(y1-y0));
            detail[y*w+x] = gray[y*w+x] - (float) mean;
        }
        // Thin leaves/stems can be narrower than the compact patch. Keep the
        // same peak separation and multi-anchor confidence requirements.
        int small = Math.max(16, Math.round((narrow ? 16 : compact ? 24 : 40) * sx));
        int large = Math.max(24, Math.round((narrow ? 28 : compact ? 40 : 64) * sx));
        int min = Math.max(large + 8, Math.round(250 * sx));
        int step = Math.max(2, Math.round(3 * sx)), yStep = Math.max(3, Math.round(5 * sy));
        int dyLimit = Math.max(2, Math.round((narrow ? 6 : 4) * sy));
        List<Candidate> candidates = new ArrayList<>();
        int[] anchors = localized ? new int[]{tw/6,tw/4,tw/3,tw*5/12,tw/2,tw*7/12,tw*2/3,tw*3/4,tw*5/6}
                : compact ? new int[]{tw/4, tw/3, tw/2, tw*2/3, tw*3/4} : new int[]{tw/2};
        for (int anchor : anchors) {
        int cx = source - left + anchor;
        int max = Math.min(Math.round(800 * sx), w - cx - large / 2 - radius - 1);
        for (int cy = Math.max(large / 2 + radius, top - roiTop + 30);
             cy < Math.min(h - large / 2 - radius, bottom - roiTop + th / 2); cy += yStep) {
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return null;
            Candidate a = search(gray, detail, w, h, cx, cy, small, min, max, step, dyLimit, 3, deadline);
            if (a == null || a.score < .52f) continue;
            Candidate b = search(gray, detail, w, h, cx, cy, large, min, max, step, dyLimit, 3, deadline);
            if (b == null || b.score < .52f || Math.abs(a.dx-b.dx) > Math.max(5, step*2)) continue;
            // Adjacent rows can all choose the same first peak in a periodic pattern.
            // Require both coarse templates to distinguish their peak from other offsets.
            if (compact && (a.score-a.alternative < .08f || b.score-b.alternative < .08f)) continue;
            a = search(gray, detail, w, h, cx, cy, small, Math.max(min,a.dx-step), Math.min(max,a.dx+step), 1, dyLimit, 2, deadline);
            b = search(gray, detail, w, h, cx, cy, large, Math.max(min,b.dx-step), Math.min(max,b.dx+step), 1, dyLimit, 2, deadline);
            if (a != null && b != null && Math.abs(a.dx-b.dx) <= Math.max(3, Math.round(4*sx))
                    && Math.min(a.score,b.score) >= .57f) {
                Candidate candidate = new Candidate(Math.min(a.score,b.score), b.dx, b.dy, cy);
                candidate.anchor = anchor;
                candidates.add(candidate);
            }
        }
        }
        if (candidates.size() < 2 || System.nanoTime() >= deadline) return null;
        Candidate best = candidates.get(0);
        for (Candidate c : candidates) if(c.score > best.score) best=c;
        int support=0;
        float alternative=0;
        int firstAnchor=Integer.MAX_VALUE, lastAnchor=Integer.MIN_VALUE;
        for(Candidate c:candidates) {
            if(Math.abs(c.dx-best.dx)<=Math.max(5,Math.round(6*sx))) {
                support++;
                if(c.score >= .70f) {
                    firstAnchor=Math.min(firstAnchor,c.anchor);
                    lastAnchor=Math.max(lastAnchor,c.anchor);
                }
            }
            else alternative=Math.max(alternative,c.score);
        }
        int max = Math.min(Math.round(800*sx), w-(source-left+best.anchor)-large/2-radius-1);
        if(support<2 || alternative>best.score-(compact ? .08f : .06f)
                || best.dx<=min+1 || best.dx>=max-1) return null;
        // Smaller windows are less distinctive: require agreement across separated
        // horizontal anchors, not just overlapping rows at one image position.
        if(localized) {
            // Some silhouettes have useful texture in one corner only. Require
            // three non-overlapping high-confidence patches spanning both axes,
            // rather than weakening the existing wide-anchor branches.
            if(best.score < .90f || alternative > best.score - .12f
                    || !hasIndependentTriangle(candidates, best, large, deadline)) return null;
        } else if(compact && (best.score < .75f || firstAnchor==Integer.MAX_VALUE
                || lastAnchor-firstAnchor < tw/3)) return null;
        int sourceY = Math.max(roiTop, Math.min(roiBottom-th, best.cy+roiTop-th/2));
        return PuzzleSliderMatcherCore.Result.success(best.dx,best.dx,best.score,
                localized ? "localized-texture-consensus" : narrow ? "narrow-texture-consensus" : compact ? "compact-texture-consensus" : "interior-texture-consensus",Math.round(800*sx),(System.nanoTime()-start)/1_000_000L,
                source,sourceY,source+best.dx,sourceY+best.dy,tw,th);
    }

    private static boolean hasIndependentTriangle(List<Candidate> candidates, Candidate best, int size, long deadline) {
        List<Candidate> strong = new ArrayList<>();
        for(Candidate c:candidates) {
            if(c.score >= .82f && Math.abs(c.dx-best.dx)<=3 && Math.abs(c.dy-best.dy)<=2) strong.add(c);
        }
        for(int i=0;i<strong.size();i++) for(int j=i+1;j<strong.size();j++) {
            if(System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return false;
            Candidate a=strong.get(i),b=strong.get(j);
            if(!separate(a,b,size)) continue;
            for(int k=j+1;k<strong.size();k++) {
                if(System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return false;
                Candidate c=strong.get(k);
                if(separate(a,c,size) && separate(b,c,size)
                        && Math.max(a.anchor,Math.max(b.anchor,c.anchor))-Math.min(a.anchor,Math.min(b.anchor,c.anchor))>=size
                        && Math.max(a.cy,Math.max(b.cy,c.cy))-Math.min(a.cy,Math.min(b.cy,c.cy))>=size) return true;
            }
        }
        return false;
    }

    private static boolean separate(Candidate a,Candidate b,int size) {
        return Math.abs(a.anchor-b.anchor)>=size || Math.abs(a.cy-b.cy)>=size;
    }

    private static Candidate search(float[] gray,float[] detail,int w,int h,int cx,int cy,int size,
            int min,int max,int dxStep,int dyLimit,int sampleStep,long deadline) {
        int x0=cx-size/2,y0=cy-size/2;
        if(x0<0 || y0-dyLimit<0 || y0+size+dyLimit>=h) return null;
        double sg=0,sd=0,gg=0,dd=0;int n=0;
        double maxRow=0;
        for(int y=0;y<size;y+=sampleStep) {
            double rowEnergy=0;
            for(int x=0;x<size;x+=sampleStep) {
                int i=(y0+y)*w+x0+x;double g=gray[i],d=detail[i];
                sg+=g;sd+=d;gg+=g*g;dd+=d*d;n++;rowEnergy+=d*d;
            }
            maxRow=Math.max(maxRow,rowEnergy);
        }
        double vg=gg-sg*sg/n,vd=dd-sd*sd/n;
        if(vg/n<25 || vd/n<1 || maxRow>dd*.4) return null;
        Candidate best=null;
        float[] scoresByOffset = new float[(max-min)/dxStep+1];
        java.util.Arrays.fill(scoresByOffset, -1f);
        for(int dx=min;dx<=max;dx+=dxStep) for(int dy=-dyLimit;dy<=dyLimit;dy+=1) {
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return null;
            double tg=0,td=0,tgg=0,tdd=0,pg=0,pd=0;
            for(int y=0;y<size;y+=sampleStep) for(int x=0;x<size;x+=sampleStep) {
                int i=(y0+y)*w+x0+x,j=i+dy*w+dx;
                double g=gray[j],d=detail[j];tg+=g;td+=d;tgg+=g*g;tdd+=d*d;
                pg+=gray[i]*g;pd+=detail[i]*d;
            }
            double dg=Math.sqrt(Math.max(0,vg*(tgg-tg*tg/n))),de=Math.sqrt(Math.max(0,vd*(tdd-td*td/n)));
            if(dg<1e-6 || de<1e-6) continue;
            float score=(float)Math.min((pg-sg*tg/n)/dg,(pd-sd*td/n)/de);
            int offsetIndex=(dx-min)/dxStep;
            scoresByOffset[offsetIndex]=Math.max(scoresByOffset[offsetIndex],score);
            if(best==null || score>best.score)best=new Candidate(score,dx,dy,cy);
        }
        if(best != null) {
            for(int i=0;i<scoresByOffset.length;i++) {
                if(Math.abs(min+i*dxStep-best.dx)>Math.max(8,dxStep*3))
                    best.alternative=Math.max(best.alternative,scoresByOffset[i]);
            }
        }
        return best;
    }
}
