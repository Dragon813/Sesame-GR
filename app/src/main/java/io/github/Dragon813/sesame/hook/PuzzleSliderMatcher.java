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

    /** 由历史记录/差异兜底直接构造的位移结果。 */
    static Result synthetic(int displacement, String method, float score,
                            int sourceLeft, int sourceTop, int templateWidth, int templateHeight) {
        return new Result(true, displacement, displacement, score, method, 0, 0L, "",
                sourceLeft, sourceTop, sourceLeft + displacement, sourceTop,
                templateWidth, templateHeight);
    }

    /** 检测左侧源拼图块包围盒，返回 {left, top, right, bottom}；检测不到返回 null。 */
    static int[] detectPiece(Bitmap bitmap, float sliderScreenY, int screenTop, int sourceLeft) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        PuzzleSliderMatcherCore.Piece piece = PuzzleSliderMatcherCore.detectPiece(
                bitmap.getWidth(), bitmap.getHeight(), sliderScreenY, screenTop,
                (left, top, width, height, pixels) ->
                        bitmap.getPixels(pixels, 0, width, left, top, width, height),
                sourceLeft);
        return piece == null ? null
                : new int[]{piece.left, piece.top, piece.right, piece.bottom};
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
