package io.github.Dragon813.sesame.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.github.Dragon813.sesame.R;
import io.github.Dragon813.sesame.data.ConfigV2;
import io.github.Dragon813.sesame.data.Model;
import io.github.Dragon813.sesame.data.modelFieldExt.IntegerModelField;
import io.github.Dragon813.sesame.model.normal.base.BaseModel;
import io.github.Dragon813.sesame.util.FileUtil;
import io.github.Dragon813.sesame.util.LanguageUtil;
import io.github.Dragon813.sesame.util.Log;
import io.github.Dragon813.sesame.util.ToastUtil;
import io.github.Dragon813.sesame.util.idMap.UserIdMap;

public class HtmlViewerActivity extends BaseActivity {
    MyWebView mWebView;
    ProgressBar pgb;
    Uri uri;
    Boolean canClear;

    /** 记录详情自动追加滚动定时任务 */
    private final Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private Runnable autoScrollRunnable;
    private boolean autoScrollToBottom;
    /** 已展示到文件中的字节位置，-1=尚未就绪 */
    private long logReadOffset = -1;
    /** 文件末尾不足一行的字节，等待下次追加 */
    private byte[] logPendingBytes;

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
                            if (autoScrollToBottom) {
                                // 整页加载完成：以此为追加基线，后续只追加新增内容
                                resetLogReadOffset();
                            }
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
            canClear = intent.getBooleanExtra("canClear", false);
            autoScrollToBottom = intent.getBooleanExtra("autoScrollToBottom", false);
            if (autoScrollToBottom) {
                // 追加日志内容依赖 evaluateJavascript，仅对记录详情开启 JS
                settings.setJavaScriptEnabled(true);
            }
            if (uri != null) {
                mWebView.loadUrl(uri.toString());
            }
            startAutoScroll();
            return;
        }
        settings.setTextZoom(100);
        settings.setUseWideViewPort(true);
    }

    @Override
    protected void onPause() {
        stopAutoScroll();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopAutoScroll();
        super.onDestroy();
    }

    private void startAutoScroll() {
        stopAutoScroll();
        logReadOffset = -1;
        logPendingBytes = null;
        if (!autoScrollToBottom) {
            return;
        }
        int seconds = getAutoScrollSeconds();
        if (seconds <= 0) {
            return;
        }
        // 定时只把新增的完整日志行追加到页面末尾，不再整页重刷
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                appendNewLogContent();
                autoScrollHandler.postDelayed(this, seconds * 1000L);
            }
        };
        autoScrollHandler.postDelayed(autoScrollRunnable, seconds * 1000L);
    }

    private void stopAutoScroll() {
        if (autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
            autoScrollRunnable = null;
        }
    }

    /** 整页加载完成后，记录当前文件末尾作为追加起点 */
    private void resetLogReadOffset() {
        logPendingBytes = null;
        if (uri == null) {
            logReadOffset = -1;
            return;
        }
        File file = new File(uri.getPath());
        logReadOffset = file.exists() ? file.length() : -1;
    }

    /** 读取并追加文件新增的完整日志行 */
    private void appendNewLogContent() {
        if (uri == null) {
            return;
        }
        if (logReadOffset < 0) {
            // 未收到加载完成回调时的兜底：以当前文件末尾作为基线，下一轮开始追加
            resetLogReadOffset();
            return;
        }
        File file = new File(uri.getPath());
        long length = file.exists() ? file.length() : 0;
        // 文件被清空或截断（如手动清空记录）：回到整页刷新
        if (length < logReadOffset) {
            logReadOffset = -1;
            logPendingBytes = null;
            mWebView.reload();
            return;
        }
        if (length == logReadOffset) {
            return;
        }
        String text = readNewCompleteLines(file, length);
        if (text.isEmpty()) {
            return;
        }
        String script = "(function(){var t=" + JSONObject.quote(text)
                + ";var p=document.querySelector('pre');"
                + "if(!p){p=document.createElement('pre');document.body.appendChild(p);}"
                + "p.appendChild(document.createTextNode(t));})()";
        mWebView.evaluateJavascript(script, null);
        // 文本追加后布局延后生效，稍后再滚动到底部
        autoScrollHandler.postDelayed(mWebView::scrollToBottom, 100);
    }

    /**
     * 从 logReadOffset 读到文件末尾，只返回以换行结尾的完整内容；
     * 末尾不足一行的字节缓存到下次，避免出现半行或半个多字节字符。
     */
    private String readNewCompleteLines(File file, long length) {
        byte[] chunk = new byte[(int) (length - logReadOffset)];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(logReadOffset);
            raf.readFully(chunk);
        } catch (Exception e) {
            Log.printStackTrace(e);
            return "";
        }
        logReadOffset = length;
        byte[] data;
        if (logPendingBytes == null || logPendingBytes.length == 0) {
            data = chunk;
        } else {
            data = new byte[logPendingBytes.length + chunk.length];
            System.arraycopy(logPendingBytes, 0, data, 0, logPendingBytes.length);
            System.arraycopy(chunk, 0, data, logPendingBytes.length, chunk.length);
            logPendingBytes = null;
        }
        int lastNewline = -1;
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == '\n') {
                lastNewline = i;
                break;
            }
        }
        if (lastNewline < 0) {
            logPendingBytes = data;
            return "";
        }
        logPendingBytes = Arrays.copyOfRange(data, lastNewline + 1, data.length);
        return new String(data, 0, lastNewline + 1, StandardCharsets.UTF_8);
    }

    /**
     * 读取「记录自动刷新滚动间隔(秒)」配置。
     * 主进程尚未加载过配置时（例如刚启动就直接查看记录），按当前账号或默认账号初始化一次。
     */
    private int getAutoScrollSeconds() {
        IntegerModelField field = BaseModel.getLogAutoScrollSeconds();
        if (field == null) {
            try {
                Model.initAllModel();
                ConfigV2.load(UserIdMap.getCurrentUid());
            } catch (Throwable ignored) {
            }
            field = BaseModel.getLogAutoScrollSeconds();
        }
        Integer value = field == null ? null : field.getValue();
        return value == null ? 0 : Math.max(0, value);
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
