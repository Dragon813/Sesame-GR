package io.github.Dragon813.sesame.hook;

import android.graphics.Bitmap;

/** Android bitmap adapter for the offline-testable puzzle matcher. */
final class PuzzleSliderMatcher {
    private PuzzleSliderMatcher() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    static Result estimate(
            Bitmap bitmap,
            float sliderScreenY,
            int bitmapScreenTop,
            long timeoutMs) {
        return estimate(bitmap, sliderScreenY, bitmapScreenTop, timeoutMs, -1);
    }

    static Result fromCore(PuzzleSliderMatcherCore.Result result) {
        if (result == null) return null;
        return new Result(result.success, result.displacement, result.peak, result.bestScore,
                result.method, result.searchTravel, result.elapsedMs, result.error,
                result.sourceLeft, result.sourceTop, result.targetLeft, result.targetTop,
                result.templateWidth, result.templateHeight);
    }

    static Result estimate(
            Bitmap bitmap,
            float sliderScreenY,
            int bitmapScreenTop,
            long timeoutMs,
            int sourceLeftOverride) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return Result.failure("invalid bitmap", 0L);
        }

        PuzzleSliderMatcherCore.Result coreResult = PuzzleSliderMatcherCore.estimateOptimized(
                bitmap.getWidth(),
                bitmap.getHeight(),
                sliderScreenY,
                bitmapScreenTop,
                (left, top, width, height, pixels) ->
                        bitmap.getPixels(pixels, 0, width, left, top, width, height),
                timeoutMs,
                sourceLeftOverride);
        return new Result(
                coreResult.success,
                coreResult.displacement,
                coreResult.peak,
                coreResult.bestScore,
                coreResult.method,
                coreResult.searchTravel,
                coreResult.elapsedMs,
                coreResult.error,
                coreResult.sourceLeft,
                coreResult.sourceTop,
                coreResult.targetLeft,
                coreResult.targetTop,
                coreResult.templateWidth,
                coreResult.templateHeight);
    }

    static final class Result {
        final boolean success;
        final int displacement;
        final int peak;
        final float bestScore;
        final String method;
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

        private static Result failure(String error, long elapsedMs) {
            return new Result(
                    false, 0, 0, 0f, "", 0, elapsedMs, error,
                    -1, -1, -1, -1, 0, 0);
        }
    }
}
