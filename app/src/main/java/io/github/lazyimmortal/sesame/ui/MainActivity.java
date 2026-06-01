package io.github.lazyimmortal.sesame.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.AppConfig;
import io.github.lazyimmortal.sesame.data.RunType;
import io.github.lazyimmortal.sesame.data.ViewAppInfo;
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.entity.FriendWatch;
import io.github.lazyimmortal.sesame.entity.UserEntity;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.LanguageUtil;
import io.github.lazyimmortal.sesame.util.LibraryUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.PermissionUtil;
import io.github.lazyimmortal.sesame.util.Statistics;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.ToastUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

public class MainActivity extends BaseActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean hasPermissions = false;
    private boolean isBackground = false;
    private boolean isClick = false;

    private TextView[][] statCells = new TextView[5][3]; // [dataType][day/month/year]
    private TextView tvVersion, tvApi, tvStatus;
    private TextView tvModuleStatus, tvInfoVersion, tvSdkApi, tvDevice, tvArch;
    private ImageView ivStatusIcon;
    private Handler viewHandler;
    private Runnable titleRunner;

    private String[] userNameArray = {"默认"};
    private UserEntity[] userEntityArray = {null};

    private LinearLayout navHome, navLogs, navSettings, navOther;
    private ImageView navHomeIcon, navLogsIcon, navSettingsIcon, navOtherIcon;
    private TextView navHomeText, navLogsText, navSettingsText, navOtherText;
    private ScrollView tabHome, tabLogs, tabSettings, tabOther;
    private LinearLayout containerUserConfigs;

    @Override
    public void onContentChanged() {
        // 不调用 super，因为主页没有 Toolbar
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 统计表格
        statCells[0][0] = findViewById(R.id.tv_stat_collected_day);
        statCells[0][1] = findViewById(R.id.tv_stat_collected_month);
        statCells[0][2] = findViewById(R.id.tv_stat_collected_year);
        statCells[1][0] = findViewById(R.id.tv_stat_helped_day);
        statCells[1][1] = findViewById(R.id.tv_stat_helped_month);
        statCells[1][2] = findViewById(R.id.tv_stat_helped_year);
        statCells[2][0] = findViewById(R.id.tv_stat_watered_day);
        statCells[2][1] = findViewById(R.id.tv_stat_watered_month);
        statCells[2][2] = findViewById(R.id.tv_stat_watered_year);
        statCells[3][0] = findViewById(R.id.tv_stat_wateredcount_day);
        statCells[3][1] = findViewById(R.id.tv_stat_wateredcount_month);
        statCells[3][2] = findViewById(R.id.tv_stat_wateredcount_year);
        statCells[4][0] = findViewById(R.id.tv_stat_wateringcount_day);
        statCells[4][1] = findViewById(R.id.tv_stat_wateringcount_month);
        statCells[4][2] = findViewById(R.id.tv_stat_wateringcount_year);

        tvVersion = findViewById(R.id.tv_version);
        tvApi = findViewById(R.id.tv_api);
        tvStatus = findViewById(R.id.tv_status);
        ivStatusIcon = findViewById(R.id.iv_status_icon);

        // 信息卡片
        tvModuleStatus = findViewById(R.id.tv_module_status);
        tvInfoVersion = findViewById(R.id.tv_info_version);
        tvSdkApi = findViewById(R.id.tv_sdk_api);
        tvDevice = findViewById(R.id.tv_device);
        tvArch = findViewById(R.id.tv_arch);

        // 显示版本号和 API
        String ver = ViewAppInfo.getAppVersion();
        try {
            int vc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            tvVersion.setText(ver + " (" + vc + ")");
        } catch (Exception e) {
            tvVersion.setText(ver);
        }
        ViewAppInfo.checkRunType();
        tvApi.setText("API " + ViewAppInfo.checkLspApi());

        // 信息卡片数据
        tvInfoVersion.setText(ver);
        tvSdkApi.setText(String.valueOf(Build.VERSION.SDK_INT));
        tvDevice.setText(Build.MODEL);
        tvArch.setText(Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "arm64-v8a");
        initBottomNavigation();
        updateStatusLabel(ViewAppInfo.getRunType());

        viewHandler = new Handler(Looper.getMainLooper());
        titleRunner = () -> updateStatusLabel(RunType.DISABLE);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.i("view broadcast action:" + action + " intent:" + intent);
                if (action != null) {
                    switch (action) {
                        case "io.github.lazyimmortal.sesame.status":
                            if (RunType.DISABLE == ViewAppInfo.getRunType()) {
                                updateStatusLabel(RunType.PACKAGE);
                            }
                            viewHandler.removeCallbacks(titleRunner);
                            if (isClick) {
                                ToastUtil.show(context, "芝麻粒加载状态正常");
                                isClick = false;
                            }
                            break;
                        case "io.github.lazyimmortal.sesame.update":
                            Statistics.load();
                            updateStatisticsTable();
                            break;
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("io.github.lazyimmortal.sesame.status");
        intentFilter.addAction("io.github.lazyimmortal.sesame.update");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, intentFilter);
        }

        SharedPreferences prefs = getSharedPreferences("sesame_prefs", MODE_PRIVATE);
        String savedVersion = prefs.getString("last_dialog_version", "");
        boolean dontShow = prefs.getBoolean("dont_show_dialog", false);
        String currentVersion = ViewAppInfo.getAppVersion();

        // 只在版本变更或首次启动时显示弹窗
        if (!dontShow || !currentVersion.equals(savedVersion)) {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_startup_tips, null);
            CheckBox cbNoMore = dialogView.findViewById(R.id.cb_no_more);
            TextView tvDialogMsg = dialogView.findViewById(R.id.dialog_message);
            tvDialogMsg.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.tips);
            builder.setView(dialogView);
            builder.setPositiveButton(R.string.btn_understood, (dialog, which) -> {
                if (cbNoMore.isChecked()) {
                    prefs.edit()
                            .putBoolean("dont_show_dialog", true)
                            .putString("last_dialog_version", currentVersion)
                            .apply();
                }
                dialog.dismiss();
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(ContextCompat.getColor(this, R.color.miuix_button_text_text));
            }
        }
    }

    private void initBottomNavigation() {
        navHome = findViewById(R.id.nav_home);
        navLogs = findViewById(R.id.nav_logs);
        navSettings = findViewById(R.id.nav_settings);
        navOther = findViewById(R.id.nav_other);

        navHomeIcon = (ImageView) ((ViewGroup) navHome).getChildAt(0);
        navLogsIcon = (ImageView) ((ViewGroup) navLogs).getChildAt(0);
        navSettingsIcon = (ImageView) ((ViewGroup) navSettings).getChildAt(0);
        navOtherIcon = (ImageView) ((ViewGroup) navOther).getChildAt(0);

        navHomeText = (TextView) ((ViewGroup) navHome).getChildAt(1);
        navLogsText = (TextView) ((ViewGroup) navLogs).getChildAt(1);
        navSettingsText = (TextView) ((ViewGroup) navSettings).getChildAt(1);
        navOtherText = (TextView) ((ViewGroup) navOther).getChildAt(1);

        tabHome = findViewById(R.id.tab_home);
        tabLogs = findViewById(R.id.tab_logs);
        tabSettings = findViewById(R.id.tab_settings);
        tabOther = findViewById(R.id.tab_other);
        containerUserConfigs = findViewById(R.id.container_user_configs);

        // 设置Tab - 开关
        Switch swHideIcon = findViewById(R.id.sw_hide_icon);
        Switch swChinese = findViewById(R.id.sw_chinese);
        if (swHideIcon != null) {
            int state = getPackageManager().getComponentEnabledSetting(
                    new ComponentName(this, getClass().getCanonicalName() + "Alias"));
            swHideIcon.setChecked(state > PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            swHideIcon.setOnCheckedChangeListener((btn, checked) -> {
                int s = checked ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                getPackageManager().setComponentEnabledSetting(
                        new ComponentName(MainActivity.this, MainActivity.class.getCanonicalName() + "Alias"),
                        s, PackageManager.DONT_KILL_APP);
            });
        }
        if (swChinese != null) {
            swChinese.setChecked(AppConfig.INSTANCE.getLanguageSimplifiedChinese());
            swChinese.setOnCheckedChangeListener((btn, checked) -> {
                AppConfig.INSTANCE.setLanguageSimplifiedChinese(checked);
                if (AppConfig.save()) {
                    LanguageUtil.setLocal(this);
                    recreate();
                }
            });
        }

        // 日志Tab - 记录开关
        Switch swForestLog = findViewById(R.id.sw_forest_log);
        Switch swFarmLog = findViewById(R.id.sw_farm_log);
        Switch swOtherLog = findViewById(R.id.sw_other_log);
        Switch swAllRecord = findViewById(R.id.sw_all_record);
        Switch swDebugLog = findViewById(R.id.sw_debug_log);
        Switch swErrorLog = findViewById(R.id.sw_error_log);
        Switch swRuntimeLog = findViewById(R.id.sw_runtime_log);

        // 日志开关 — SystemProperty 共享（跨ClassLoader安全）
        if (swForestLog != null) {
            swForestLog.setChecked(Log.isLogOn("log_forest"));
            swForestLog.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_forest", c);
                if (!c) clearLogFile(FileUtil.getForestLogFile());
            });
        }
        if (swFarmLog != null) {
            swFarmLog.setChecked(Log.isLogOn("log_farm"));
            swFarmLog.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_farm", c);
                if (!c) clearLogFile(FileUtil.getFarmLogFile());
            });
        }
        if (swOtherLog != null) {
            swOtherLog.setChecked(Log.isLogOn("log_other"));
            swOtherLog.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_other", c);
                if (!c) clearLogFile(FileUtil.getOtherLogFile());
            });
        }
        if (swAllRecord != null) {
            swAllRecord.setChecked(Log.isLogOn("log_all"));
            swAllRecord.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_all", c);
                if (!c) clearLogFile(FileUtil.getRecordLogFile());
            });
        }
        if (swDebugLog != null) {
            swDebugLog.setChecked(Log.isLogOn("log_debug"));
            swDebugLog.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_debug", c);
                if (!c) clearLogFile(FileUtil.getDebugLogFile());
            });
        }
        if (swErrorLog != null) {
            swErrorLog.setChecked(Log.isLogOn("log_error"));
            swErrorLog.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_error", c);
                if (!c) clearLogFile(FileUtil.getErrorLogFile());
            });
        }
        if (swRuntimeLog != null) {
            swRuntimeLog.setChecked(Log.isLogOn("log_runtime"));
            swRuntimeLog.setOnCheckedChangeListener((b, c) -> {
                Log.saveSwitchState("log_runtime", c);
                if (!c) clearLogFile(FileUtil.getRuntimeLogFile());
            });
        }

        switchToTab(0);
    }

    public void onNavClick(View v) {
        int id = v.getId();
        if (id == R.id.nav_home) switchToTab(0);
        else if (id == R.id.nav_logs) switchToTab(1);
        else if (id == R.id.nav_settings) switchToTab(2);
        else if (id == R.id.nav_other) switchToTab(3);
    }

    private void switchToTab(int tab) {
        tabHome.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        tabLogs.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        tabOther.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);

        int active = 0xFF000000;
        int inactive = ContextCompat.getColor(this, R.color.miuix_on_background_variant);

        navHomeIcon.setColorFilter(tab == 0 ? active : inactive);
        navLogsIcon.setColorFilter(tab == 1 ? active : inactive);
        navSettingsIcon.setColorFilter(tab == 2 ? active : inactive);
        navOtherIcon.setColorFilter(tab == 3 ? active : inactive);

        navHomeText.setTextColor(tab == 0 ? active : inactive);
        navLogsText.setTextColor(tab == 1 ? active : inactive);
        navSettingsText.setTextColor(tab == 2 ? active : inactive);
        navOtherText.setTextColor(tab == 3 ? active : inactive);
    }

    private void updateStatusLabel(RunType runType) {
        String statusText;
        int textColor;
        int iconRes;
        switch (runType) {
            case DISABLE:
                statusText = "未激活";
                textColor = 0xFF757575;
                iconRes = 0;
                break;
            case MODEL:
                statusText = "已激活";
                textColor = 0xFF2E7D32;
                iconRes = R.drawable.ic_check_circle;
                break;
            case PACKAGE:
                statusText = "加载中";
                textColor = 0xFFE65100;
                iconRes = 0;
                break;
            default:
                statusText = "未知";
                textColor = 0xFF757575;
                iconRes = 0;
        }
        tvStatus.setText(statusText);
        tvStatus.setTextColor(textColor);
        tvModuleStatus.setText(statusText);

        if (iconRes != 0) {
            ivStatusIcon.setImageResource(iconRes);
            ivStatusIcon.setVisibility(View.VISIBLE);
        } else {
            ivStatusIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasPermissions) {
            if (!hasFocus) {
                isBackground = true;
                return;
            }
            isBackground = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (isBackground) return;
                    hasPermissions = PermissionUtil.checkOrRequestFilePermissions(MainActivity.this);
                    if (hasPermissions) {
                        onResume();
                        return;
                    }
                    Toast.makeText(MainActivity.this, "未获取文件读写权限", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this, 2000);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions) {
            if (RunType.DISABLE == ViewAppInfo.getRunType()) {
                viewHandler.postDelayed(titleRunner, 3000);
                try {
                    sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.status"));
                } catch (Throwable th) {
                    Log.i("view sendBroadcast status err:");
                    Log.printStackTrace(th);
                }
            }
            try {
                List<String> userNameList = new ArrayList<>();
                List<UserEntity> userEntityList = new ArrayList<>();
                File[] configFiles = FileUtil.CONFIG_DIRECTORY_FILE.listFiles();
                if (configFiles != null) {
                    for (File configDir : configFiles) {
                        if (configDir.isDirectory()) {
                            String userId = configDir.getName();
                            UserIdMap.loadSelf(userId);
                            UserEntity userEntity = UserIdMap.get(userId);
                            String userName;
                            if (userEntity == null) userName = userId;
                            else userName = userEntity.getShowName() + ": " + userEntity.getAccount();
                            userNameList.add(userName);
                            userEntityList.add(userEntity);
                        }
                    }
                }
                userNameList.add(0, "默认");
                userEntityList.add(0, null);
                userNameArray = userNameList.toArray(new String[0]);
                userEntityArray = userEntityList.toArray(new UserEntity[0]);
            } catch (Exception e) {
                userNameArray = new String[]{"默认"};
                userEntityArray = new UserEntity[]{null};
                Log.printStackTrace(e);
            }
            try {
                Statistics.load();
                Statistics.updateDay(Calendar.getInstance());
                updateStatisticsTable();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            populateUserConfigs();
        }
    }

    /**
     * 动态生成用户配置列表到设置Tab
     */
    private void populateUserConfigs() {
        containerUserConfigs.removeAllViews();
        for (int i = 1; i < userNameArray.length; i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setText(userNameArray[i]);
            btn.setTextColor(ContextCompat.getColor(this, R.color.miuix_on_background));
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.miuix_text_body1));
            btn.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            btn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            btn.setAllCaps(false);
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.miuix_button_text_bg));
            int pad = (int) getResources().getDimension(R.dimen.miuix_spacing_16);
            btn.setPadding(pad, pad, pad, pad);
            btn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setOnClickListener(v -> goSettingActivity(idx));

            // divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.miuix_divider_line));
            LinearLayout.LayoutParams dp = (LinearLayout.LayoutParams) divider.getLayoutParams();
            dp.setMarginStart(pad);

            containerUserConfigs.addView(divider);
            containerUserConfigs.addView(btn);
        }
    }

    public void onConfigClick(View v) {
        if (v.getId() == R.id.btn_default_config) {
            goSettingActivity(0);
        }
    }

    private void clearLogFile(File file) {
        try {
            if (file != null && file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {}
    }

    /**
     * 将统计数据显示到表格中
     */
    private void updateStatisticsTable() {
        // 数据顺序: collected, helped, watered, wateredcount, wateringcount
        // 时间顺序: day, month, year
        Statistics.DataType[] dts = {
            Statistics.DataType.COLLECTED, Statistics.DataType.HELPED,
            Statistics.DataType.WATERED, Statistics.DataType.WATEREDCOUNT,
            Statistics.DataType.WATERINGCOUNT
        };
        Statistics.TimeType[] tts = {
            Statistics.TimeType.DAY, Statistics.TimeType.MONTH, Statistics.TimeType.YEAR
        };
        for (int d = 0; d < dts.length; d++) {
            for (int t = 0; t < tts.length; t++) {
                int val = Statistics.getData(tts[t], dts[d]);
                statCells[d][t].setText(String.valueOf(val));
            }
        }
    }

    @SuppressLint("NonConstantResourceId")
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btn_friend_watch) {
            Intent fwIt = new Intent(this, ListViewerActivity.class);
            fwIt.putExtra("title", getString(R.string.friend_watch));
            startActivity(fwIt);
        } else if (id == R.id.btn_extensions) {
            startActivity(new Intent(this, ExtensionsActivity.class));
        } else if (id == R.id.btn_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }
    }

    @SuppressLint("NonConstantResourceId")
    public void onLogClick(View v) {
        int id = v.getId();
        String filePath = null;
        String fileName = null;

        if (id == R.id.tv_forest_log) {
            filePath = FileUtil.getForestLogFile().getAbsolutePath();
            fileName = "森林记录";
        } else if (id == R.id.tv_farm_log) {
            filePath = FileUtil.getFarmLogFile().getAbsolutePath();
            fileName = "庄园记录";
        } else if (id == R.id.tv_other_log) {
            filePath = FileUtil.getOtherLogFile().getAbsolutePath();
            fileName = "其他记录";
        } else if (id == R.id.tv_all_record) {
            filePath = FileUtil.getRecordLogFile().getAbsolutePath();
            fileName = "全部记录";
        } else if (id == R.id.tv_debug_log) {
            filePath = FileUtil.getDebugLogFile().getAbsolutePath();
            fileName = "抓包记录";
        } else if (id == R.id.tv_error_log) {
            filePath = FileUtil.getErrorLogFile().getAbsolutePath();
            fileName = "异常日志";
        } else if (id == R.id.tv_runtime_log) {
            filePath = FileUtil.getRuntimeLogFile().getAbsolutePath();
            fileName = "运行日志";
        } else {
            return;
        }

        Intent it = new Intent(this, LogDetailActivity.class);
        it.putExtra("filePath", filePath);
        it.putExtra("fileName", fileName);
        startActivity(it);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void selectSettingUid() {
        AtomicBoolean selected = new AtomicBoolean(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请选择配置");
        builder.setItems(userNameArray, (dialog, which) -> {
            selected.set(true);
            dialog.dismiss();
            goSettingActivity(which);
        });
        builder.setOnDismissListener(dialog -> selected.set(true));
        builder.setPositiveButton("返回", (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.miuix_button_text_text));
        }
        int length = userNameArray.length;
        if (length > 0 && length < 3) {
            new Thread(() -> {
                TimeUtil.sleep(800);
                if (!selected.get()) {
                    alertDialog.dismiss();
                    goSettingActivity(length - 1);
                }
            }).start();
        }
    }

    private void goSettingActivity(int index) {
        UserEntity userEntity = userEntityArray[index];
        boolean isNewUI = AppConfig.INSTANCE.getNewUI() && !"TEST".equals(ViewAppInfo.getAppVersion())
                && LibraryUtil.loadLibrary("sesame");
        Intent intent = new Intent(this, isNewUI ? NewSettingsActivity.class : SettingsActivity.class);
        if (userEntity != null) {
            intent.putExtra("userId", userEntity.getUserId());
            intent.putExtra("userName", userEntity.getShowName());
        } else {
            intent.putExtra("userName", userNameArray[index]);
        }
        startActivity(intent);
    }
}
