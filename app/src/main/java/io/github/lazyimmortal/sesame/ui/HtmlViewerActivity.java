package io.github.lazyimmortal.sesame.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import java.io.File;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.ToastUtil;

public class HtmlViewerActivity extends BaseActivity {
    MyWebView mWebView;
    ProgressBar pgb;
    Uri uri;
    Boolean canClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_viewer);
        setBaseTitle("查看");
        setBaseSubtitleTextColor(android.graphics.Color.WHITE);

        mWebView = findViewById(R.id.mwv_webview);
        pgb = findViewById(R.id.pgb_webview);

        mWebView.setWebChromeClient(new WebChromeClient() {
            @SuppressLint("WrongConstant")
            @Override
            public void onProgressChanged(WebView view, int progress) {
                pgb.setProgress(progress);
                pgb.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        WebSettings settings = mWebView.getSettings();
        if (intent != null) {
            uri = intent.getData();
            if (uri != null) {
                mWebView.loadUrl(uri.toString());
            }
            canClear = intent.getBooleanExtra("canClear", false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 1, getString(R.string.export_file));
        if (canClear) {
            menu.add(0, 2, 2, getString(R.string.clear_file));
        }
        menu.add(0, 3, 3, getString(R.string.open_with_other_browser));
        menu.add(0, 4, 4, getString(R.string.copy_the_url));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                if (uri != null && uri.getPath() != null) {
                    File exportFile = FileUtil.exportFile(new File(uri.getPath()));
                    if (exportFile != null) {
                        ToastUtil.show(this, "已导出: " + exportFile.getPath());
                    }
                }
                break;
            case 2:
                if (uri != null && uri.getPath() != null) {
                    File file = new File(uri.getPath());
                    if (FileUtil.clearFile(file)) {
                        ToastUtil.show(this, "已清空");
                        mWebView.reload();
                    }
                }
                break;
            case 3:
                if (uri != null) {
                    String scheme = uri.getScheme();
                    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } else {
                        ToastUtil.show(this, "不支持浏览器打开");
                    }
                }
                break;
            case 4:
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(null, mWebView.getUrl()));
                ToastUtil.show(this, getString(R.string.copy_success));
                break;
        }
        return true;
    }
}
