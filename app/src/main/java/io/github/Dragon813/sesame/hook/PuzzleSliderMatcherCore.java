package io.github.Dragon813.sesame.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure-Java puzzle displacement matcher shared by Android runtime and offline tests. */
final class PuzzleSliderMatcherCore {
    private static final int REFERENCE_WIDTH = 1264;
    private static final int REFERENCE_HEIGHT = 2780;
    private static final int TEMPLATE_SIZE = 180;
    private static final int SOURCE_LEFT = 166;
    private static final int SOURCE_TOP_FROM_SLIDER = -707;
    private static final int SOURCE_BOTTOM_FROM_SLIDER = -287;
    /* 源拼图块所在左侧条带的宽度，占图片宽度比例。 */
    private static final float PIECE_STRIP_WIDTH_RATIO = 0.17f;
    /* 源拼图块纵向搜索带相对滑块按钮的偏移（参考坐标，随高度缩放）。 */
    private static final int PIECE_BAND_TOP_FROM_SLIDER = -1000;
    private static final int PIECE_BAND_BOTTOM_FROM_SLIDER = -100;
    /* Search-window dimensions, expressed at the reference screenshot width. */
    private static final int SEARCH_MIN_REFERENCE = 250;
    private static final int INITIAL_SEARCH_TRAVEL_REFERENCE = 720;
    private static final int ROI_TRAVEL_REFERENCE = 800;
    private static final float MIN_BEST_SCORE = 0.16f;

    private PuzzleSliderMatcherCore() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    interface PixelReader {
        void read(int left, int top, int width, int height, int[] pixels);
    }

    /** 源拼图块在截图中的包围盒（绝对坐标）。 */
    static final class Piece {
        final int left, top, right, bottom;

        Piece(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }

    /** Runtime pipeline: bounded texture consensus, then legacy edges with remaining budget. */
    static Result estimateOptimized(int width, int height, float sliderY, int screenTop,
            PixelReader reader, long timeoutMs, int sourceLeft) {
        long started = System.nanoTime();
        if (timeoutMs <= 0) return Result.failure("matching timed out", 0);
        // 先用深色轮廓定位源拼图块：纵向位置随机，固定条带会裁掉高处的块。
        Piece piece = detectPiece(width, height, sliderY, screenTop, reader, sourceLeft);
        Result texture = PuzzleTextureMatcherCore.estimate(width, height, sliderY, screenTop,
                reader, Math.min(1200L, timeoutMs / 2), sourceLeft, piece);
        long used = elapsedMs(started);
        if (used >= timeoutMs || Thread.currentThread().isInterrupted()) {
            return Result.failure("matching timed out", used);
        }
        Result result = texture;
        if (result == null) {
            // 雾面/纯色块纹理不足时，退回几何兜底：同形状灰色阴影的中心差。
            result = estimateShadow(width, height, sliderY, screenTop, reader,
                    Math.max(0L, timeoutMs - used), sourceLeft, piece);
        }
        if (result == null) {
            result = estimate(width, height, sliderY, screenTop,
                    reader, timeoutMs - used, sourceLeft, piece);
        }
        long remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && !Thread.currentThread().isInterrupted()) {
            Result compact = PuzzleTextureMatcherCore.estimateCompact(width, height, sliderY, screenTop,
                    reader, remaining, sourceLeft, piece);
            if (compact != null) result = compact;
        }
        remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && !Thread.currentThread().isInterrupted()) {
            Result narrow = PuzzleTextureMatcherCore.estimateNarrow(width, height, sliderY, screenTop,
                    reader, remaining, sourceLeft, piece);
            if (narrow != null) result = narrow;
        }
        remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && !Thread.currentThread().isInterrupted()) {
            Result localized = PuzzleTextureMatcherCore.estimateLocalized(width, height, sliderY, screenTop,
                    reader, remaining, sourceLeft, piece);
            if (localized != null) result = localized;
        }
        remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && result.error.startsWith("ambiguous image match")) {
            Result contour = PuzzleOccludedContourMatcher.estimate(width,height,sliderY,screenTop,reader,remaining,sourceLeft);
            if (contour != null) result = contour;
        }
        long elapsed = elapsedMs(started);
        if (elapsed >= timeoutMs) return Result.failure("matching timed out", elapsed);
        if (!result.success) return Result.failure(result.error, elapsed);
        return Result.success(result.displacement, result.peak, result.bestScore, result.method,
                result.searchTravel, elapsed, result.sourceLeft, result.sourceTop,
                result.targetLeft, result.targetTop, result.templateWidth, result.templateHeight);
    }

    static Result estimate(
            int bitmapWidth,
            int bitmapHeight,
            float sliderScreenY,
            int bitmapScreenTop,
            PixelReader pixelReader,
            long timeoutMs) {
        return estimate(bitmapWidth, bitmapHeight, sliderScreenY, bitmapScreenTop,
                pixelReader, timeoutMs, -1);
    }

    /**
     * Estimates displacement using a caller-provided source X when the current layout gives
     * us a reliable slider position. A negative value keeps the calibrated legacy source.
     */
    static Result estimate(
            int bitmapWidth,
            int bitmapHeight,
            float sliderScreenY,
            int bitmapScreenTop,
            PixelReader pixelReader,
            long timeoutMs,
            int sourceLeftOverride) {
        return estimate(bitmapWidth, bitmapHeight, sliderScreenY, bitmapScreenTop,
                pixelReader, timeoutMs, sourceLeftOverride, null);
    }

    private static Result estimate(
            int bitmapWidth,
            int bitmapHeight,
            float sliderScreenY,
            int bitmapScreenTop,
            PixelReader pixelReader,
            long timeoutMs,
            int sourceLeftOverride,
            Piece piece) {
        long started = System.nanoTime();
        Result initial = estimateRange(bitmapWidth, bitmapHeight, sliderScreenY, bitmapScreenTop,
                pixelReader, timeoutMs, sourceLeftOverride, piece, false);
        int boundary = Math.round(INITIAL_SEARCH_TRAVEL_REFERENCE * bitmapWidth / (float) REFERENCE_WIDTH);
        if ((initial.success && initial.displacement < boundary - 2)
                || initial.error.equals("matching timed out")) {
            return initial;
        }
        long remainingMs = timeoutMs - elapsedMs(started);
        if (remainingMs <= 0) {
            return Result.failure("matching timed out", elapsedMs(started));
        }
        Result extended = estimateRange(bitmapWidth, bitmapHeight, sliderScreenY, bitmapScreenTop,
                pixelReader, remainingMs, sourceLeftOverride, piece, true);
        int outerBoundary = Math.round(ROI_TRAVEL_REFERENCE * bitmapWidth / (float) REFERENCE_WIDTH);
        if (extended.success && extended.displacement < outerBoundary - 2) {
            return Result.success(extended.displacement, extended.peak, extended.bestScore,
                    extended.method + "-extended-range", extended.searchTravel, elapsedMs(started),
                    extended.sourceLeft, extended.sourceTop, extended.targetLeft, extended.targetTop,
                    extended.templateWidth, extended.templateHeight);
        }
        if (extended.error.equals("matching timed out")) {
            return Result.failure("matching timed out", elapsedMs(started));
        }
        return Result.failure(initial.success ? "ambiguous match at search boundary" : initial.error,
                elapsedMs(started));
    }

    private static Result estimateRange(
            int bitmapWidth, int bitmapHeight, float sliderScreenY, int bitmapScreenTop,
            PixelReader pixelReader, long timeoutMs, int sourceLeftOverride, Piece piece,
            boolean extendedRange) {
        long startedNanos = System.nanoTime();
        long deadlineNanos = timeoutMs <= 0L
                ? startedNanos
                : startedNanos + timeoutMs * 1_000_000L;
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || pixelReader == null) {
            return Result.failure("invalid image", elapsedMs(startedNanos));
        }
        if (timedOut(deadlineNanos)) {
            return Result.failure("matching timed out", elapsedMs(startedNanos));
        }

        float scaleX = bitmapWidth / (float) REFERENCE_WIDTH;
        float scaleY = bitmapHeight / (float) REFERENCE_HEIGHT;
        int sliderY = Math.round(sliderScreenY - bitmapScreenTop);
        int sourceLeft = sourceLeftOverride >= 0
                ? sourceLeftOverride
                : Math.round(SOURCE_LEFT * scaleX);
        int templateWidth = Math.max(72, Math.round(TEMPLATE_SIZE * scaleX));
        // 模板按宽度比例取正方形，不再用高度比例（会在 720x1039 上压成 72px 高）。
        int templateHeight = Math.max(72, Math.round(TEMPLATE_SIZE * scaleX));
        int sourceTop = sliderY + Math.round(SOURCE_TOP_FROM_SLIDER * scaleY);
        int sourceBottom = sliderY + Math.round(SOURCE_BOTTOM_FROM_SLIDER * scaleY);
        if (piece != null) {
            sourceLeft = piece.left;
            templateWidth = Math.max(48, piece.width());
            templateHeight = Math.max(48, piece.height());
            sourceTop = piece.top;
            sourceBottom = piece.bottom;
        }
        int maxDx = Math.round((extendedRange ? ROI_TRAVEL_REFERENCE : INITIAL_SEARCH_TRAVEL_REFERENCE) * scaleX);
        int minDx = Math.max(templateWidth + sampleGuard(scaleX),
                Math.round(SEARCH_MIN_REFERENCE * scaleX));
        if (extendedRange) {
            minDx = Math.max(minDx, Math.round((INITIAL_SEARCH_TRAVEL_REFERENCE - 16) * scaleX));
        }

        int roiLeft = Math.max(1, sourceLeft - 2);
        int roiTop = Math.max(1, sourceTop - Math.round(34 * scaleY));
        int roiMaxDx = Math.round(ROI_TRAVEL_REFERENCE * scaleX);
        int roiRight = Math.min(bitmapWidth - 1, sourceLeft + templateWidth + roiMaxDx + 2);
        int roiBottom = Math.min(bitmapHeight - 1,
                sourceBottom + templateHeight + Math.round(34 * scaleY));
        if (roiRight - roiLeft < templateWidth + roiMaxDx
                || roiBottom - roiTop < templateHeight) {
            return Result.failure(
                    "captcha image region is outside snapshot bounds", elapsedMs(startedNanos));
        }

        int edgeWidth = roiRight - roiLeft;
        int edgeHeight = roiBottom - roiTop;
        int[] pixels = new int[edgeWidth * edgeHeight];
        try {
            pixelReader.read(roiLeft, roiTop, edgeWidth, edgeHeight, pixels);
        } catch (Throwable throwable) {
            return Result.failure(
                    "pixel extraction failed: " + throwable.getClass().getSimpleName(),
                    elapsedMs(startedNanos));
        }
        if (timedOut(deadlineNanos)) {
            return Result.failure("matching timed out", elapsedMs(startedNanos));
        }
        float[] rawEdges = buildEdgeMap(pixels, edgeWidth, edgeHeight);
        boolean[] borderRows = removeFullWidthEdges(rawEdges, edgeWidth, edgeHeight);
        float[] edges = spreadEdges(rawEdges, edgeWidth, edgeHeight);
        int sampleStepX = Math.max(3, Math.round(4 * scaleX));
        int sampleStepY = Math.max(3, Math.round(4 * scaleY));
        int sourceStep = Math.max(sampleStepY, Math.round(10 * scaleY));
        int dxStep = Math.max(2, Math.round(4 * scaleX));
        int dyLimit = Math.max(sampleStepY, Math.round(30 * scaleY));

        List<Candidate> candidates = new ArrayList<>();
        for (int absoluteSourceY = sourceTop;
             absoluteSourceY <= sourceBottom;
             absoluteSourceY += sourceStep) {
            if (timedOut(deadlineNanos)) {
                return Result.failure("matching timed out", elapsedMs(startedNanos));
            }
            int localSourceX = sourceLeft - roiLeft;
            int localSourceY = absoluteSourceY - roiTop;
            if (crossesBorder(borderRows, localSourceY, templateHeight)) {
                continue;
            }
            Candidate bestForRow = null;
            for (int dx = minDx; dx <= maxDx; dx += dxStep) {
                if (timedOut(deadlineNanos)) {
                    return Result.failure("matching timed out", elapsedMs(startedNanos));
                }
                for (int dy = -dyLimit; dy <= dyLimit; dy += sampleStepY) {
                    if (crossesBorder(borderRows, localSourceY + dy, templateHeight)) {
                        continue;
                    }
                    float score = normalizedCorrelation(
                            edges, edgeWidth, edgeHeight,
                            localSourceX, localSourceY,
                            localSourceX + dx, localSourceY + dy,
                            templateWidth, templateHeight, sampleStepX, sampleStepY);
                    if (extendedRange && !isReliableCandidate(score, dy, scaleY)) {
                        continue;
                    }
                    if (bestForRow == null || score > bestForRow.score) {
                        bestForRow = new Candidate(
                                score, dx, absoluteSourceY, absoluteSourceY + dy);
                    }
                }
            }
            if (bestForRow != null) {
                candidates.add(bestForRow);
            }
        }
        if (candidates.isEmpty()) {
            return Result.failure("no displacement candidates", elapsedMs(startedNanos));
        }

        Candidate best = candidates.get(0);
        for (Candidate candidate : candidates) {
            if (candidate.score > best.score) {
                best = candidate;
            }
        }
        int peak = minDx;
        double peakScore = Double.NEGATIVE_INFINITY;
        double sigma = Math.max(1.0, 25.0 * scaleX);
        for (int point = minDx; point <= maxDx; point++) {
            if (timedOut(deadlineNanos)) {
                return Result.failure("matching timed out", elapsedMs(startedNanos));
            }
            double clusterScore = 0.0;
            for (Candidate candidate : candidates) {
                // Ignore weak texture coincidences (toolbar/background edges) when clustering
                // row candidates; the captcha contour must repeat with meaningful confidence.
                double weight = Math.max(0.0, candidate.score - 0.22);
                double distance = (candidate.displacement - point) / sigma;
                clusterScore += weight * Math.exp(-(distance * distance) / 2.0);
            }
            if (clusterScore > peakScore) {
                peakScore = clusterScore;
                peak = point;
            }
        }

        int nearLimit = Math.max(1, Math.round(35 * scaleX));
        int displacement;
        String method;
        if (best.score >= 0.52f && Math.abs(best.displacement - peak) <= nearLimit) {
            displacement = best.displacement;
            method = "strong-best";
        } else {
            // A mean can fall between distinct matches and outside the refinement
            // radius of every useful candidate. Seed refinement with an observed
            // candidate supported by the winning cluster instead.
            Candidate clusterBest = null;
            for (Candidate candidate : candidates) {
                if (Math.abs(candidate.displacement - peak) <= nearLimit && candidate.score > 0f) {
                    if (clusterBest == null || candidate.score > clusterBest.score) {
                        clusterBest = candidate;
                    }
                }
            }
            if (clusterBest == null) {
                return Result.failure(
                        "no candidates around cluster peak", elapsedMs(startedNanos));
            }
            best = clusterBest;
            displacement = clusterBest.displacement;
            method = "cluster-best";
        }

        if (best.score < MIN_BEST_SCORE) {
            return Result.failure(String.format(
                    Locale.ROOT, "low confidence %.4f", best.score), elapsedMs(startedNanos));
        }
        if (displacement < minDx || displacement > maxDx) {
            return Result.failure(
                    "displacement outside calibrated range: " + displacement,
                    elapsedMs(startedNanos));
        }
        Candidate refined = refineCandidate(
                edges,
                borderRows,
                edgeWidth,
                edgeHeight,
                sourceLeft - roiLeft,
                sourceTop,
                sourceBottom,
                roiTop,
                displacement,
                minDx,
                maxDx,
                templateWidth,
                templateHeight,
                sampleStepX,
                sampleStepY,
                sourceStep,
                dxStep,
                dyLimit,
                extendedRange,
                scaleY,
                deadlineNanos);
        if (refined != null) {
            displacement = refined.displacement;
            best = refined;
            method += "-pixel-refined";
        }
        if (timedOut(deadlineNanos)) {
            return Result.failure("matching timed out", elapsedMs(startedNanos));
        }
        // Weak correlations that also need a large vertical adjustment commonly match
        // unrelated scenery. Do not promote them just because the photo border was removed.
        if (!isReliableCandidate(best.score, best.targetY - best.sourceY, scaleY)) {
            return Result.failure(String.format(Locale.ROOT,
                    "ambiguous image match score=%.4f verticalOffset=%d",
                    best.score, best.targetY - best.sourceY), elapsedMs(startedNanos));
        }
        int searchTravel = Math.max(maxDx, roiRight - sourceLeft - templateWidth - 2);
        return Result.success(
                displacement,
                peak,
                best.score,
                method,
                searchTravel,
                elapsedMs(startedNanos),
                sourceLeft,
                best.sourceY,
                sourceLeft + displacement,
                best.targetY,
                templateWidth,
                templateHeight);
    }

    /** Refines the coarse 4px search to the best integer X displacement nearby. */
    private static Candidate refineCandidate(
            float[] edges,
            boolean[] borderRows,
            int width,
            int height,
            int sourceX,
            int sourceTop,
            int sourceBottom,
            int roiTop,
            int coarseDisplacement,
            int minDx,
            int maxDx,
            int templateWidth,
            int templateHeight,
            int sampleStepX,
            int sampleStepY,
            int sourceStep,
            int dxStep,
            int dyLimit,
            boolean reliableOnly,
            float scaleY,
            long deadlineNanos) {
        int refineRadius = Math.max(4, dxStep * 2);
        int refineMin = Math.max(minDx, coarseDisplacement - refineRadius);
        int refineMax = Math.min(maxDx, coarseDisplacement + refineRadius);
        Candidate refined = null;
        for (int absoluteSourceY = sourceTop;
             absoluteSourceY <= sourceBottom;
             absoluteSourceY += sourceStep) {
            if (timedOut(deadlineNanos)) {
                return refined;
            }
            int localSourceY = absoluteSourceY - roiTop;
            if (crossesBorder(borderRows, localSourceY, templateHeight)) {
                continue;
            }
            for (int dx = refineMin; dx <= refineMax; dx++) {
                for (int dy = -dyLimit; dy <= dyLimit; dy += sampleStepY) {
                    if (crossesBorder(borderRows, localSourceY + dy, templateHeight)) {
                        continue;
                    }
                    float score = normalizedCorrelation(
                            edges, width, height,
                            sourceX, localSourceY,
                            sourceX + dx, localSourceY + dy,
                            templateWidth, templateHeight, sampleStepX, sampleStepY);
                    if (reliableOnly && !isReliableCandidate(score, dy, scaleY)) {
                        continue;
                    }
                    if (refined == null || score > refined.score) {
                        refined = new Candidate(
                                score, dx, absoluteSourceY, absoluteSourceY + dy);
                    }
                }
            }
        }
        return refined;
    }

    /** 在图片左侧条带里找拼图块的深色轮廓，返回其包围盒；找不到返回 null。 */
    static Piece detectPiece(int width, int height, float sliderScreenY, int screenTop,
            PixelReader reader, int sourceLeftHint) {
        if (reader == null || width <= 0 || height <= 0) {
            return null;
        }
        float scaleX = width / (float) REFERENCE_WIDTH;
        float scaleY = height / (float) REFERENCE_HEIGHT;
        int sliderY = Math.round(sliderScreenY - screenTop);
        int expected = Math.max(40, Math.round(TEMPLATE_SIZE * scaleX));
        int stripLeft = Math.max(1, sourceLeftHint >= 0 ? sourceLeftHint : Math.round(SOURCE_LEFT * scaleX));
        int stripRight = Math.min(width - 2, stripLeft + Math.round(PIECE_STRIP_WIDTH_RATIO * width));
        int bandTop = Math.max(1, sliderY + Math.round(PIECE_BAND_TOP_FROM_SLIDER * scaleY));
        int bandBottom = Math.min(height - 2, sliderY + Math.round(PIECE_BAND_BOTTOM_FROM_SLIDER * scaleY));
        int w = stripRight - stripLeft + 1;
        int h = bandBottom - bandTop + 1;
        if (w < expected / 2 || h < expected / 2 || (long) w * h > 1_000_000) {
            return null;
        }
        int[] pixels = new int[w * h];
        try {
            reader.read(stripLeft, bandTop, w, h, pixels);
        } catch (Throwable invalid) {
            return null;
        }
        boolean[] dark = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int gray = (77 * ((c >>> 16) & 255) + 150 * ((c >>> 8) & 255) + 29 * (c & 255)) >> 8;
            dark[i] = gray < 96;
        }
        // 一次膨胀把抗锯齿造成的断线接起来，避免轮廓被拆成多条细弧。
        boolean[] linked = new boolean[pixels.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                linked[i] = dark[i]
                        || (x > 0 && dark[i - 1]) || (x + 1 < w && dark[i + 1])
                        || (y > 0 && dark[i - w]) || (y + 1 < h && dark[i + w]);
            }
        }
        boolean[] seen = new boolean[pixels.length];
        int[] queue = new int[pixels.length];
        int minW = Math.round(expected * 0.55f), maxW = Math.round(expected * 1.5f);
        int minH = Math.round(expected * 0.55f), maxH = Math.round(expected * 1.5f);
        int bestCost = Integer.MAX_VALUE;
        Piece best = null;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int index = y * w + x;
                if (seen[index]) {
                    continue;
                }
                if (!linked[index]) {
                    seen[index] = true;
                    continue;
                }
                int head = 0, tail = 0;
                queue[tail++] = index;
                seen[index] = true;
                int minX = x, maxX = x, minY = y, maxY = y;
                while (head < tail) {
                    int point = queue[head++];
                    int px = point % w, py = point / w;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    if (px > 0 && !seen[point - 1] && linked[point - 1]) {
                        seen[point - 1] = true;
                        queue[tail++] = point - 1;
                    }
                    if (px + 1 < w && !seen[point + 1] && linked[point + 1]) {
                        seen[point + 1] = true;
                        queue[tail++] = point + 1;
                    }
                    if (py > 0 && !seen[point - w] && linked[point - w]) {
                        seen[point - w] = true;
                        queue[tail++] = point - w;
                    }
                    if (py + 1 < h && !seen[point + w] && linked[point + w]) {
                        seen[point + w] = true;
                        queue[tail++] = point + w;
                    }
                }
                int bw = maxX - minX + 1, bh = maxY - minY + 1;
                if (bw < minW || bw > maxW || bh < minH || bh > maxH) {
                    continue;
                }
                // 上下不贴边（横向贴左边是拼图块被图片边缘裁掉，属于正常情况）。
                if (minY <= 0 || maxY >= h - 1) {
                    continue;
                }
                double fill = tail / (double) (bw * bh);
                if (fill > 0.5) {
                    continue;
                }
                int cost = Math.abs(bw - expected) + Math.abs(bh - expected) + minX / 2;
                if (cost < bestCost) {
                    bestCost = cost;
                    best = new Piece(stripLeft + minX, bandTop + minY, stripLeft + maxX, bandTop + maxY);
                }
            }
        }
        return best;
    }

    /**
     * 几何兜底：目标缺口是与源块同形状、竖向对齐的灰色阴影。纹理证据不足时直接取两者中心差，
     * 不做像素相关，所以对雾面/纯色拼图块同样有效。置信度不够时返回 null 交给旧分支。
     */
    static Result estimateShadow(int width, int height, float sliderScreenY, int screenTop,
            PixelReader reader, long timeoutMs, int sourceLeftHint, Piece piece) {
        if (piece == null || timeoutMs <= 0L) {
            return null;
        }
        long started = System.nanoTime();
        float scaleX = width / (float) REFERENCE_WIDTH;
        float scaleY = height / (float) REFERENCE_HEIGHT;
        int pieceWidth = piece.width(), pieceHeight = piece.height();
        if (pieceWidth < 40 || pieceHeight < 40) {
            return null;
        }
        int minDx = Math.max(pieceWidth + sampleGuard(scaleX), Math.round(SEARCH_MIN_REFERENCE * scaleX));
        int bandMargin = Math.max(3, Math.round(10 * scaleY));
        int bandTop = Math.max(1, piece.top - bandMargin);
        int bandBottom = Math.min(height - 2, piece.bottom + bandMargin);
        int searchLeft = Math.max(1, piece.left + minDx - pieceWidth / 2);
        int searchRight = width - 2;
        int w = searchRight - searchLeft + 1;
        int h = bandBottom - bandTop + 1;
        if (w <= pieceWidth || h < pieceHeight || (long) w * h > 2_000_000) {
            return null;
        }
        int[] pixels = new int[w * h];
        try {
            reader.read(searchLeft, bandTop, w, h, pixels);
        } catch (Throwable invalid) {
            return null;
        }
        if (elapsedMs(started) > timeoutMs) {
            return null;
        }
        boolean[] shadow = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
            int high = Math.max(r, Math.max(g, b)), low = Math.min(r, Math.min(g, b));
            int gray = (77 * r + 150 * g + 29 * b) >> 8;
            // 灰色阴影：低饱和且不是亮白背景（天空/卡片）。
            shadow[i] = high - low <= 40 && gray <= 205;
        }
        boolean[] seen = new boolean[pixels.length];
        int[] queue = new int[pixels.length];
        int minW = Math.round(pieceWidth * 0.7f), maxW = Math.round(pieceWidth * 1.35f);
        int minH = Math.round(pieceHeight * 0.7f), maxH = Math.round(pieceHeight * 1.35f);
        int tolY = Math.max(6, Math.round(pieceHeight * 0.18f));
        int bestCost = Integer.MAX_VALUE;
        int bestLeft = 0, bestRight = 0;
        for (int y = 1; y < h - 1; y++) {
            if (elapsedMs(started) > timeoutMs || Thread.currentThread().isInterrupted()) {
                return null;
            }
            for (int x = 1; x < w - 1; x++) {
                int index = y * w + x;
                if (seen[index]) {
                    continue;
                }
                if (!shadow[index]) {
                    seen[index] = true;
                    continue;
                }
                int head = 0, tail = 0;
                queue[tail++] = index;
                seen[index] = true;
                int minX = x, maxX = x, minY = y, maxY = y;
                while (head < tail) {
                    int point = queue[head++];
                    int px = point % w, py = point / w;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    if (px > 0 && !seen[point - 1] && shadow[point - 1]) {
                        seen[point - 1] = true;
                        queue[tail++] = point - 1;
                    }
                    if (px + 1 < w && !seen[point + 1] && shadow[point + 1]) {
                        seen[point + 1] = true;
                        queue[tail++] = point + 1;
                    }
                    if (py > 0 && !seen[point - w] && shadow[point - w]) {
                        seen[point - w] = true;
                        queue[tail++] = point - w;
                    }
                    if (py + 1 < h && !seen[point + w] && shadow[point + w]) {
                        seen[point + w] = true;
                        queue[tail++] = point + w;
                    }
                }
                int bw = maxX - minX + 1, bh = maxY - minY + 1;
                if (bw < minW || bw > maxW || bh < minH || bh > maxH) {
                    continue;
                }
                // 拼图块只做水平滑动，缺口与源块必须竖向对齐。
                if (Math.abs((bandTop + minY) - piece.top) > tolY) {
                    continue;
                }
                if (minX <= 0 || maxX >= w - 1 || minY <= 0 || maxY >= h - 1) {
                    continue;
                }
                double fill = tail / (double) (bw * bh);
                if (fill < 0.12) {
                    continue;
                }
                int cost = Math.abs(bw - pieceWidth) + Math.abs(bh - pieceHeight)
                        + Math.abs((bandTop + minY) - piece.top);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestLeft = searchLeft + minX;
                    bestRight = searchLeft + maxX;
                }
            }
        }
        if (bestCost == Integer.MAX_VALUE) {
            return null;
        }
        boolean clipped = piece.left <= sourceLeftHint + 2
                || piece.left <= Math.round(SOURCE_LEFT * scaleX) + 2;
        int displacement = clipped
                ? bestRight - piece.right
                : (bestLeft + bestRight) / 2 - (piece.left + piece.right) / 2;
        if (displacement < minDx || displacement > Math.round(ROI_TRAVEL_REFERENCE * scaleX)) {
            return null;
        }
        int travel = Math.round(ROI_TRAVEL_REFERENCE * scaleX);
        return Result.success(displacement, displacement, 0.85f, "shadow-geometry", travel,
                elapsedMs(started), piece.left, piece.top, piece.left + displacement, piece.top,
                pieceWidth, pieceHeight);
    }

    private static int sampleGuard(float scale) {
        return Math.max(2, Math.round(8 * scale));
    }

    private static boolean isReliableCandidate(float score, int verticalOffset, float scaleY) {
        return score >= 0.45f || (score >= 0.30f
                && Math.abs(verticalOffset) <= Math.max(4, Math.round(8 * scaleY)));
    }

    private static float[] buildEdgeMap(int[] pixels, int width, int height) {
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int red = (color >>> 16) & 0xff;
            int green = (color >>> 8) & 0xff;
            int blue = color & 0xff;
            gray[i] = (77 * red + 150 * green + 29 * blue) >>> 8;
        }

        float[] edges = new float[pixels.length];
        for (int y = 1; y < height - 1; y++) {
            int row = y * width;
            for (int x = 1; x < width - 1; x++) {
                int index = row + x;
                int gx = -gray[index - width - 1] + gray[index - width + 1]
                        - 2 * gray[index - 1] + 2 * gray[index + 1]
                        - gray[index + width - 1] + gray[index + width + 1];
                int gy = -gray[index - width - 1] - 2 * gray[index - width]
                        - gray[index - width + 1] + gray[index + width - 1]
                        + 2 * gray[index + width] + gray[index + width + 1];
                edges[index] = (float) Math.sqrt((double) gx * gx + (double) gy * gy);
            }
        }
        return edges;
    }

    private static float[] spreadEdges(float[] edges, int width, int height) {
        float[] spread = new float[edges.length];
        for (int y = 2; y < height - 2; y++) {
            int row = y * width;
            for (int x = 2; x < width - 2; x++) {
                float maximum = 0f;
                for (int offsetY = -2; offsetY <= 2; offsetY++) {
                    int offsetRow = row + offsetY * width;
                    for (int offsetX = -2; offsetX <= 2; offsetX++) {
                        maximum = Math.max(maximum, edges[offsetRow + x + offsetX]);
                    }
                }
                spread[row + x] = maximum;
            }
        }
        return spread;
    }

    /** A photo border shared across nearly the entire search ROI carries no displacement. */
    private static boolean[] removeFullWidthEdges(float[] edges, int width, int height) {
        boolean[] borderRows = new boolean[height];
        for (int y = 2; y < height - 2; y++) {
            int strong = 0;
            for (int x = 2; x < width - 2; x++) {
                if (edges[y * width + x] >= 160f) {
                    strong++;
                }
            }
            borderRows[y] = strong >= (width - 4) * 0.75f;
        }
        for (int y = 2; y < height - 2; y++) {
            if (borderRows[y]) {
                for (int dy = -2; dy <= 2; dy++) {
                    java.util.Arrays.fill(edges, (y + dy) * width, (y + dy + 1) * width, 0f);
                }
            }
        }
        return borderRows;
    }

    private static boolean crossesBorder(boolean[] borderRows, int top, int height) {
        for (int y = Math.max(0, top - 4); y < Math.min(borderRows.length, top + height + 4); y++) {
            if (borderRows[y]) {
                return true;
            }
        }
        return false;
    }

    private static float normalizedCorrelation(
            float[] edges,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int targetX,
            int targetY,
            int templateWidth,
            int templateHeight,
            int stepX,
            int stepY) {
        if (sourceX < 0 || sourceY < 0 || targetX < 0 || targetY < 0
                || sourceX + templateWidth >= width || targetX + templateWidth >= width
                || sourceY + templateHeight >= height || targetY + templateHeight >= height) {
            return -1f;
        }

        double sourceSum = 0.0;
        double targetSum = 0.0;
        double sourceSquareSum = 0.0;
        double targetSquareSum = 0.0;
        double productSum = 0.0;
        int count = 0;
        for (int y = 0; y < templateHeight; y += stepY) {
            int sourceRow = (sourceY + y) * width + sourceX;
            int targetRow = (targetY + y) * width + targetX;
            for (int x = 0; x < templateWidth; x += stepX) {
                float source = edges[sourceRow + x];
                float target = edges[targetRow + x];
                sourceSum += source;
                targetSum += target;
                sourceSquareSum += source * source;
                targetSquareSum += target * target;
                productSum += source * target;
                count++;
            }
        }
        double numerator = productSum - (sourceSum * targetSum / count);
        double sourceVariance = sourceSquareSum - (sourceSum * sourceSum / count);
        double targetVariance = targetSquareSum - (targetSum * targetSum / count);
        double denominator = Math.sqrt(Math.max(0.0, sourceVariance * targetVariance));
        return denominator <= 1e-6 ? -1f : (float) (numerator / denominator);
    }

    private static boolean timedOut(long deadlineNanos) {
        return Thread.currentThread().isInterrupted() || System.nanoTime() >= deadlineNanos;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static final class Candidate {
        private final float score;
        private final int displacement;
        private final int sourceY;
        private final int targetY;

        private Candidate(float score, int displacement, int sourceY, int targetY) {
            this.score = score;
            this.displacement = displacement;
            this.sourceY = sourceY;
            this.targetY = targetY;
        }
    }

    static final class Result {
        final boolean success;
        final int displacement;
        final int peak;
        final float bestScore;
        final String method;
        /** Maximum image travel searched, used as the image-coordinate denominator. */
        final int searchTravel;
        final long elapsedMs;
        final String error;
        final int sourceLeft;
        final int sourceTop;
        final int targetLeft;
        final int targetTop;
        final int templateWidth;
        final int templateHeight;

        private Result(
                boolean success,
                int displacement,
                int peak,
                float bestScore,
                String method,
                int searchTravel,
                long elapsedMs,
                String error,
                int sourceLeft,
                int sourceTop,
                int targetLeft,
                int targetTop,
                int templateWidth,
                int templateHeight) {
            this.success = success;
            this.displacement = displacement;
            this.peak = peak;
            this.bestScore = bestScore;
            this.method = method;
            this.searchTravel = searchTravel;
            this.elapsedMs = elapsedMs;
            this.error = error;
            this.sourceLeft = sourceLeft;
            this.sourceTop = sourceTop;
            this.targetLeft = targetLeft;
            this.targetTop = targetTop;
            this.templateWidth = templateWidth;
            this.templateHeight = templateHeight;
        }

        static Result success(
                int displacement,
                int peak,
                float bestScore,
                String method,
                int searchTravel,
                long elapsedMs,
                int sourceLeft,
                int sourceTop,
                int targetLeft,
                int targetTop,
                int templateWidth,
                int templateHeight) {
            return new Result(
                    true, displacement, peak, bestScore, method, searchTravel, elapsedMs, "",
                    sourceLeft, sourceTop, targetLeft, targetTop, templateWidth, templateHeight);
        }

        private static Result failure(String error, long elapsedMs) {
            return new Result(
                    false, 0, 0, 0f, "", 0, elapsedMs, error,
                    -1, -1, -1, -1, 0, 0);
        }
    }
}
