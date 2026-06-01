package io.github.lazyimmortal.sesame.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.ViewAppInfo;
import io.github.lazyimmortal.sesame.util.LanguageUtil;

public class BaseActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private WatermarkView watermarkView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewAppInfo.init(getApplicationContext());
        // MIUIX: 250ms fade-in/fade-out transition for all activities
        overridePendingTransition(R.anim.miuix_fade_in, R.anim.miuix_fade_out);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        addWatermark();
    }

    /**
     * MIUIX: Override finish() to apply fade-out transition
     */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.miuix_fade_in, R.anim.miuix_fade_out);
    }

    /**
     * 添加全局水印
     */
    private void addWatermark() {
        try {
            ViewGroup rootView = findViewById(android.R.id.content);
            if (rootView != null && watermarkView == null) {
                watermarkView = new WatermarkView(this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                rootView.addView(watermarkView, params);
            }
        } catch (Exception e) {
            // 静默处理，不影响主要功能
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        toolbar = findViewById(R.id.x_toolbar);
        if (toolbar != null) {
            toolbar.setTitle(getBaseTitle());
            toolbar.setSubtitle(getBaseSubtitle());
            setSupportActionBar(toolbar);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageUtil.setLocal(newBase));
    }

    public String getBaseTitle() {
        return ViewAppInfo.getAppTitle();
    }

    public String getBaseSubtitle() {
        return null;
    }

    public void setBaseTitle(String title) {
        if (toolbar != null) toolbar.setTitle(title);
    }

    public void setBaseSubtitle(String subTitle) {
        if (toolbar != null) toolbar.setSubtitle(subTitle);
    }

    public void setBaseTitleTextColor(int color) {
        if (toolbar != null) toolbar.setTitleTextColor(color);
    }

    public void setBaseSubtitleTextColor(int color) {
        if (toolbar != null) toolbar.setSubtitleTextColor(color);
    }

}
