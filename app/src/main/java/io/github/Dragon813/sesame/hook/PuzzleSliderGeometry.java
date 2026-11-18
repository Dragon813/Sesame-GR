package io.github.Dragon813.sesame.hook;

/** Maps a captcha displacement to the slider track in the same rendered pixel space. */
final class PuzzleSliderGeometry {
    private PuzzleSliderGeometry() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    static Mapping map(int imageDisplacement, float trackStart, float trackEnd) {
        if (imageDisplacement < 0
                || Float.isNaN(trackStart) || Float.isInfinite(trackStart) || Float.isNaN(trackEnd) || Float.isInfinite(trackEnd)
                || trackEnd <= trackStart) {
            return Mapping.failure("invalid slider geometry");
        }
        float trackTravel = trackEnd - trackStart;
        // The matcher reads a bitmap drawn at the view's physical pixel size and the
        // gesture coordinates are also physical pixels. Scaling by image/track widths
        // applies a second transform and moves the piece past or short of the gap.
        float touchDistance = Math.min(trackTravel, imageDisplacement);
        return new Mapping(
                true,
                imageDisplacement,
                trackTravel,
                touchDistance,
                trackStart + touchDistance,
                touchDistance < imageDisplacement,
                "");
    }

    static final class Mapping {
        final boolean success;
        final int imageDisplacement;
        final float trackTravel;
        final float touchDistance;
        final float endX;
        final boolean clamped;
        final String error;

        private Mapping(
                boolean success,
                int imageDisplacement,
                float trackTravel,
                float touchDistance,
                float endX,
                boolean clamped,
                String error) {
            this.success = success;
            this.imageDisplacement = imageDisplacement;
            this.trackTravel = trackTravel;
            this.touchDistance = touchDistance;
            this.endX = endX;
            this.clamped = clamped;
            this.error = error;
        }

        private static Mapping failure(String error) {
            return new Mapping(false, 0, 0f, 0f, 0f, false, error);
        }
    }
}
