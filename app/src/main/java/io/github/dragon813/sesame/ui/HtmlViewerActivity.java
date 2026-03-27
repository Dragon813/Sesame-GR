package io.github.dragon813.sesame.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import java.io.File;

import io.github.dragon813.sesame.R;
import io.github.dragon813.sesame.util.FileUtil;
import io.github.dragon813.sesame.util.ToastUtil;

public class HtmlViewerActivity extends BaseActivity {
    MyWebView mWebView;
    ProgressBar pgb;
    Uri uri;
    Boolean canClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_viewer);
        setBaseSubtitleTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));

        mWebView = findViewById(R.id.mwv_webview);
        pgb = findViewById(R.id.pgb_webview);

        mWebView.setWebChromeClient(
                new WebChromeClient() {
                    @SuppressLint("WrongConstant")
                    @Override
                    public void onProgressChanged(WebView view, int progress) {
                        pgb.setProgress(progress);
                        if (progress < 100) {
                            setBaseSubtitle("Loading...");
                            pgb.setVisibility(View.VISIBLE);
                        } else {
                            setBaseSubtitle(mWebView.getTitle());
                            pgb.setVisibility(View.GONE);
                        }
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        WebSettings settings = mWebView.getSettings();
        if (intent != null) {
            if (intent.getBooleanExtra("nextLine", true)) {
                settings.setTextZoom(85);
                settings.setUseWideViewPort(false);
            } else {
                settings.setTextZoom(100);
                settings.setUseWideViewPort(true);
            }
            uri = intent.getData();
            if (uri != null) {
                mWebView.loadUrl(uri.toString());
            }
            canClear = intent.getBooleanExtra("canClear", false);
            return;
        }
        settings.setTextZoom(100);
        settings.setUseWideViewPort(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 1, getString(R.string.export_file));
        if (canClear) {
            menu.add(0, 2, 2, getString(R.string.clear_file));
        }
        menu.add(0, 3, 3, getString(R.string.open_with_other_browser));
        menu.add(0, 4, 4, getString(R.string.copy_the_url));
        menu.add(0, 5, 5, getString(R.string.scroll_to_top));
        menu.add(0, 6, 6, getString(R.string.scroll_to_bottom));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                if (uri != null) {
                    String path = uri.getPath();
                    if (path != null) {
                        File exportFile = FileUtil.exportFile(new File(path));
                        if (exportFile != null) {
                            ToastUtil.show(this, "文件已导出到: " + exportFile.getPath());
                        }
                    }
                }
                break;

            case 2:
                if (uri != null) {
                    String path = uri.getPath();
                    if (path != null) {
                        File file = new File(path);
                        if (FileUtil.clearFile(file)) {
                            ToastUtil.show(this, "文件已清空");
                            mWebView.reload();
                        }
                    }
                }
                break;

            case 3:
                if (uri != null) {
                    String scheme = uri.getScheme();
                    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } else if ("file".equalsIgnoreCase(scheme)) {
                        ToastUtil.show(this, "该文件不支持用浏览器打开");
                    } else {
                        ToastUtil.show(this, "不支持用浏览器打开");
                    }
                }
                break;

            case 4:
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(null, mWebView.getUrl()));
                ToastUtil.show(this, getString(R.string.copy_success));
                break;

            case 5:
                mWebView.scrollTo(0, 0);
                break;

            case 6:
                mWebView.scrollToBottom();
                break;
        }
        return true;
    }
}
