package io.github.lazyimmortal.sesame.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import io.github.lazyimmortal.sesame.util.WatermarkUtil;

/**
 * 全局水印View
 * 在整个屏幕上以倾斜方式重复显示水印文本
 */
public class WatermarkView extends View {
    
    private Paint paint;
    private String watermarkText;
    private int watermarkAlpha;
    private float watermarkRotation;
    private int watermarkTextSize;
    
    private static final int HORIZONTAL_SPACING = 200; // 水印水平间距
    private static final int VERTICAL_SPACING = 200;   // 水印垂直间距
    
    public WatermarkView(Context context) {
        super(context);
        init();
    }
    
    public WatermarkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public WatermarkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 从native获取水印配置
        watermarkText = WatermarkUtil.getWatermarkText();
        watermarkAlpha = WatermarkUtil.getWatermarkAlpha();
        watermarkRotation = WatermarkUtil.getWatermarkRotation();
        watermarkTextSize = WatermarkUtil.getWatermarkTextSize();
        
        // 初始化画笔
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF888888); // 灰色
        paint.setAlpha(watermarkAlpha);
        paint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                watermarkTextSize,
                getResources().getDisplayMetrics()
        ));
        paint.setStyle(Paint.Style.FILL);
        
        // 设置View为透明，不阻挡触摸事件
        setBackgroundColor(0x00000000);
        setClickable(false);
        setFocusable(false);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (watermarkText == null || watermarkText.isEmpty()) {
            return;
        }
        
        int width = getWidth();
        int height = getHeight();
        
        // 计算文本尺寸
        Rect textBounds = new Rect();
        paint.getTextBounds(watermarkText, 0, watermarkText.length(), textBounds);
        int textWidth = textBounds.width();
        int textHeight = textBounds.height();
        
        // 保存画布状态
        canvas.save();
        
        // 计算需要绘制的行列数（考虑旋转后的空间）
        int rows = (int) Math.ceil(height / (double) VERTICAL_SPACING) + 3;
        int cols = (int) Math.ceil(width / (double) HORIZONTAL_SPACING) + 3;
        
        // 绘制水印
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                canvas.save();
                
                // 计算水印位置
                float x = col * HORIZONTAL_SPACING;
                float y = row * VERTICAL_SPACING;
                
                // 移动到绘制位置
                canvas.translate(x, y);
                
                // 旋转画布
                canvas.rotate(watermarkRotation);
                
                // 绘制水印文本
                canvas.drawText(watermarkText, 0, 0, paint);
                
                canvas.restore();
            }
        }
        
        // 恢复画布状态
        canvas.restore();
    }
    
    /**
     * 刷新水印（如果需要动态更新水印内容）
     */
    public void refreshWatermark() {
        watermarkText = WatermarkUtil.getWatermarkText();
        watermarkAlpha = WatermarkUtil.getWatermarkAlpha();
        watermarkRotation = WatermarkUtil.getWatermarkRotation();
        watermarkTextSize = WatermarkUtil.getWatermarkTextSize();
        
        paint.setAlpha(watermarkAlpha);
        paint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                watermarkTextSize,
                getResources().getDisplayMetrics()
        ));
        
        invalidate();
    }
    
    /**
     * 设置水印是否可见
     */
    public void setWatermarkVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
    }
}
