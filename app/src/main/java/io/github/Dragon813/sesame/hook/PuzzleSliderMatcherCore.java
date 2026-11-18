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

    /** Runtime pipeline: bounded texture consensus, then legacy edges with remaining budget. */
    static Result estimateOptimized(int width, int height, float sliderY, int screenTop,
            PixelReader reader, long timeoutMs, int sourceLeft) {
        long started = System.nanoTime();
        if (timeoutMs <= 0) return Result.failure("matching timed out", 0);
        Result texture = PuzzleTextureMatcherCore.estimate(width, height, sliderY, screenTop,
                reader, Math.min(1200L, timeoutMs / 2), sourceLeft);
        long used = elapsedMs(started);
        if (used >= timeoutMs || Thread.currentThread().isInterrupted()) {
            return Result.failure("matching timed out", used);
        }
        Result result = texture != null ? texture : estimate(width, height, sliderY, screenTop,
                reader, timeoutMs - used, sourceLeft);
        long remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && !Thread.currentThread().isInterrupted()) {
            Result compact = PuzzleTextureMatcherCore.estimateCompact(width, height, sliderY, screenTop,
                    reader, remaining, sourceLeft);
            if (compact != null) result = compact;
        }
        remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && !Thread.currentThread().isInterrupted()) {
            Result narrow = PuzzleTextureMatcherCore.estimateNarrow(width, height, sliderY, screenTop,
                    reader, remaining, sourceLeft);
            if (narrow != null) result = narrow;
        }
        remaining = timeoutMs - elapsedMs(started);
        if (!result.success && remaining > 0 && !Thread.currentThread().isInterrupted()) {
            Result localized = PuzzleTextureMatcherCore.estimateLocalized(width, height, sliderY, screenTop,
                    reader, remaining, sourceLeft);
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
        long started = System.nanoTime();
        Result initial = estimateRange(bitmapWidth, bitmapHeight, sliderScreenY, bitmapScreenTop,
                pixelReader, timeoutMs, sourceLeftOverride, false);
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
                pixelReader, remainingMs, sourceLeftOverride, true);
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
            PixelReader pixelReader, long timeoutMs, int sourceLeftOverride, boolean extendedRange) {
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
        int templateHeight = Math.max(72, Math.round(TEMPLATE_SIZE * scaleY));
        int sourceTop = sliderY + Math.round(SOURCE_TOP_FROM_SLIDER * scaleY);
        int sourceBottom = sliderY + Math.round(SOURCE_BOTTOM_FROM_SLIDER * scaleY);
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
