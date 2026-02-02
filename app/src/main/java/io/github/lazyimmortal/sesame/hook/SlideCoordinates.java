
package io.github.lazyimmortal.sesame.hook;

import lombok.Getter;
@Getter
class SlideCoordinates {
    private final float startX;
    private final float startY;
    private final float endX;
    private final float endY;
    
    public SlideCoordinates(float startX, float startY, float endX, float endY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }
    
}