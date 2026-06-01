package io.github.lazyimmortal.sesame.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.util.FileUtil;

public class LogDetailActivity extends AppCompatActivity {

    private LinearLayout container;
    private String currentFilePath;
    private String currentFileName;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentFilePath = getIntent().getStringExtra("filePath");
        currentFileName = getIntent().getStringExtra("fileName");

        int bgColor = ContextCompat.getColor(this, R.color.miuix_background);
        int textColor = ContextCompat.getColor(this, R.color.miuix_on_background);
        int iconColor = ContextCompat.getColor(this, R.color.miuix_on_background_variant);
        int timeColor = ContextCompat.getColor(this, R.color.miuix_on_background_variant);
        int cardBgColor = ContextCompat.getColor(this, R.color.miuix_surface);
        int pad = (int) getResources().getDimension(R.dimen.miuix_spacing_16);
        int pad12 = (int) getResources().getDimension(R.dimen.miuix_spacing_12);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bgColor);
        scroll.setPadding(pad, 0, pad, pad);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, (int) (24 * getResources().getDisplayMetrics().density), 0, 0);

        // ========== 状态栏占位 ==========
        View statusBar = new View(this);
        statusBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (24 * getResources().getDisplayMetrics().density)));
        root.addView(statusBar);

        // ========== 标题行（大标题 + 右上角按钮）==========
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, (int) getResources().getDimension(R.dimen.miuix_spacing_24), 0, 20);

        // 大标题
        TextView bigTitle = new TextView(this);
        bigTitle.setText(currentFileName != null ? currentFileName : "日志");
        bigTitle.setTextSize(34);
        bigTitle.setTextColor(textColor);
        bigTitle.setTypeface(bigTitle.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleRow.addView(bigTitle, titleParams);

        // 导出按钮（与配置页一致）
        ImageView btnExport = new ImageView(this);
        btnExport.setImageResource(android.R.drawable.ic_menu_upload);
        btnExport.setColorFilter(iconColor);
        btnExport.setPadding(5, 5, 5, 5);
        btnExport.setOnClickListener(v -> exportFile());
        titleRow.addView(btnExport);

        // 清空按钮（与配置页一致）
        ImageView btnClear = new ImageView(this);
        btnClear.setImageResource(android.R.drawable.ic_menu_delete);
        btnClear.setColorFilter(iconColor);
        btnClear.setPadding(5, 5, 5, 5);
        btnClear.setOnClickListener(v -> clearFile());
        titleRow.addView(btnClear);

        root.addView(titleRow);

        // ========== 日志列表容器 ==========
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        root.addView(container);

        scroll.addView(root);
        setContentView(scroll);

        if (currentFilePath != null) {
            loadLogFileAsync(currentFilePath);
        }
    }

    private void exportFile() {
        if (currentFilePath == null) return;
        File exportFile = FileUtil.exportFile(new File(currentFilePath));
        if (exportFile != null) {
            Toast.makeText(this, "已导出: " + exportFile.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFile() {
        if (currentFilePath == null) return;
        File file = new File(currentFilePath);
        if (FileUtil.clearFile(file)) {
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
            container.removeAllViews();
        }
    }

    /**
     * 异步加载日志，避免大文件卡死 UI
     */
    private void loadLogFileAsync(String path) {
        new Thread(() -> {
            List<LogEntry> entries = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < 500) { // 限制500行防止卡死
                    entries.add(parseLine(line));
                    count++;
                }
            } catch (Exception e) {
                entries.add(new LogEntry("", "读取失败: " + e.getMessage()));
            }

            final List<LogEntry> finalEntries = entries;
            mainHandler.post(() -> renderLogCards(finalEntries));
        }).start();
    }

    private LogEntry parseLine(String line) {
        String time = "";
        String content = line;

        // 匹配时间戳
        if (line.length() > 12 && line.charAt(2) == ':' && line.charAt(5) == ':') {
            int spaceIdx = line.indexOf(' ');
            if (spaceIdx > 0 && spaceIdx < 20) {
                time = line.substring(0, spaceIdx);
                content = line.substring(spaceIdx + 1).trim();
            }
        } else if (line.length() > 19 && line.charAt(4) == '-' && line.charAt(7) == '-') {
            int spaceIdx = line.indexOf(' ', 11);
            if (spaceIdx > 0) {
                time = line.substring(0, spaceIdx);
                content = line.substring(spaceIdx + 1).trim();
            }
        }
        return new LogEntry(time, content);
    }

    private void renderLogCards(List<LogEntry> entries) {
        int cardBgColor = ContextCompat.getColor(this, R.color.miuix_surface);
        int textColor = ContextCompat.getColor(this, R.color.miuix_on_background);
        int timeColor = ContextCompat.getColor(this, R.color.miuix_on_background_variant);
        int pad12 = (int) getResources().getDimension(R.dimen.miuix_spacing_12);
        int margin8 = (int) getResources().getDimension(R.dimen.miuix_spacing_8);

        for (LogEntry entry : entries) {
            // 卡片
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(ContextCompat.getDrawable(this, R.drawable.miuix_card_bg));
            card.setPadding(pad12, pad12, pad12, pad12);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, margin8);
            card.setLayoutParams(params);

            // 时间
            if (!entry.time.isEmpty()) {
                TextView tvTime = new TextView(this);
                tvTime.setText(entry.time);
                tvTime.setTextSize(12);
                tvTime.setTextColor(timeColor);
                tvTime.setPadding(0, 0, 0, 4);
                card.addView(tvTime);
            }

            // 内容
            TextView tvContent = new TextView(this);
            tvContent.setText(entry.content);
            tvContent.setTextSize(14);
            tvContent.setTextColor(textColor);
            tvContent.setLineSpacing(4, 1);
            card.addView(tvContent);

            container.addView(card);
        }
    }

    static class LogEntry {
        String time;
        String content;
        LogEntry(String time, String content) {
            this.time = time;
            this.content = content;
        }
    }
}
