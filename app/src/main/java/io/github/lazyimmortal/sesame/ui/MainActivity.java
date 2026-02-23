package io.github.lazyimmortal.sesame.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
    
    private TextView tvStatistics;
    
    private Handler viewHandler;
    
    private Runnable titleRunner;
    
    private String[] userNameArray = {"默认"};
    
    private UserEntity[] userEntityArray = {null};
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatistics = findViewById(R.id.tv_statistics);
        ViewAppInfo.checkRunType();
        /*ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setIcon(R.drawable.title_logo);
        }*/
        updateSubTitle(ViewAppInfo.getRunType());
        viewHandler = new Handler(Looper.getMainLooper());
        titleRunner = () -> updateSubTitle(RunType.DISABLE);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.i("view broadcast action:" + action + " intent:" + intent);
                if (action != null) {
                    switch (action) {
                        case "io.github.lazyimmortal.sesame.status":
                            if (RunType.DISABLE == ViewAppInfo.getRunType()) {
                                updateSubTitle(RunType.PACKAGE);
                            }
                            viewHandler.removeCallbacks(titleRunner);
                            if (isClick) {
                                ToastUtil.show(context, "芝麻粒加载状态正常");
                                isClick = false;
                            }
                            break;
                        case "io.github.lazyimmortal.sesame.update":
                            Statistics.load();
                            tvStatistics.setText(Statistics.getText(MainActivity.this));
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
        }
        else {
            registerReceiver(broadcastReceiver, intentFilter);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tips);
        builder.setMessage(R.string.start_message);
        builder.setPositiveButton(R.string.btn_understood, (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.button));
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
                    if (isBackground) {
                        return;
                    }
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
                }
                catch (Throwable th) {
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
                            if (userEntity == null) {
                                userName = userId;
                            }
                            else {
                                userName = userEntity.getShowName() + ": " + userEntity.getAccount();
                            }
                            userNameList.add(userName);
                            userEntityList.add(userEntity);
                        }
                    }
                }
                userNameList.add(0, "默认");
                userEntityList.add(0, null);
                userNameArray = userNameList.toArray(new String[0]);
                userEntityArray = userEntityList.toArray(new UserEntity[0]);
            }
            catch (Exception e) {
                userNameArray = new String[]{"默认"};
                userEntityArray = new UserEntity[]{null};
                Log.printStackTrace(e);
            }
            try {
                Statistics.load();
                Statistics.updateDay(Calendar.getInstance());
                tvStatistics.setText(Statistics.getText(MainActivity.this));
            }
            catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }
    
    @SuppressLint("NonConstantResourceId")
    public void onClick(View v) {
        if (v.getId() == R.id.btn_test) {
            try {
                sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.status"));
                isClick = true;
            }
            catch (Throwable th) {
                Log.i("view sendBroadcast status err:");
                Log.printStackTrace(th);
            }
            return;
        }
        
        String data = "file://";
        if (v.getId() == R.id.btn_forest_log) {
            data += FileUtil.getForestLogFile().getAbsolutePath();
        }
        else if (v.getId() == R.id.btn_farm_log) {
            data += FileUtil.getFarmLogFile().getAbsolutePath();
        }
        else if (v.getId() == R.id.btn_other_log) {
            data += FileUtil.getOtherLogFile().getAbsolutePath();
        }
        else if (v.getId() == R.id.btn_friend_watch) {
            ListDialog.show(this, getString(R.string.friend_watch), FriendWatch.getList(), SelectModelFieldFunc.newMapInstance(), false, ListDialog.ListType.SHOW);
            return;
        }
        else if (v.getId() == R.id.btn_github) {
            //   欢迎自己打包 欢迎大佬pr
            //   项目开源且公益  维护都是自愿
            //   但是如果打包改个名拿去卖钱忽悠小白
            //   那我只能说你妈死了 就当开源项目给你妈烧纸钱了
            data = "https://github.com/Dragon813/Sesame-GR";
        }
        else if (v.getId() == R.id.btn_settings) {
            selectSettingUid();
            return;
        }
        Intent it = new Intent(this, HtmlViewerActivity.class);
        it.setData(Uri.parse(data));
        startActivity(it);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int state = getPackageManager().getComponentEnabledSetting(new ComponentName(this, getClass().getCanonicalName() + "Alias"));
        menu.add(0, 1, 1, R.string.view_record_file);
        menu.add(0, 2, 2, R.string.view_debug_file);
        menu.add(0, 3, 3, R.string.extensions);
        menu.add(0, 4, 4, R.string.view_error_log_file);
        menu.add(0, 5, 5, R.string.export_error_log_file);
        menu.add(0, 6, 6, R.string.view_runtime_log_file);
        menu.add(0, 7, 7, R.string.export_runtime_log_file);
        menu.add(0, 8, 8, R.string.export_the_statistic_file);
        menu.add(0, 9, 9, R.string.import_the_statistic_file);
        menu.add(0, 10, 10, R.string.hide_the_application_icon).setCheckable(true).setChecked(state > PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        menu.add(0, 11, 11, R.string.language_simplified_chinese).setCheckable(true).setChecked(AppConfig.INSTANCE.getLanguageSimplifiedChinese());
        menu.add(0, 12, 12, R.string.settings);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                String recordData = "file://";
                recordData += FileUtil.getRecordLogFile().getAbsolutePath();
                Intent recordIt = new Intent(this, HtmlViewerActivity.class);
                recordIt.setData(Uri.parse(recordData));
                recordIt.putExtra("canClear", true);
                startActivity(recordIt);
                break;
            case 2:
                String debugData = "file://";
                debugData += FileUtil.getDebugLogFile().getAbsolutePath();
                Intent debugIt = new Intent(this, HtmlViewerActivity.class);
                debugIt.setData(Uri.parse(debugData));
                debugIt.putExtra("canClear", true);
                startActivity(debugIt);
                break;
            case 3:
                Intent extend = new Intent(this, ExtensionsActivity.class);
                startActivity(extend);
                break;
            case 4:
                
                String errorData = "file://";
                errorData += FileUtil.getErrorLogFile().getAbsolutePath();
                Intent errorIt = new Intent(this, HtmlViewerActivity.class);
                errorIt.putExtra("nextLine", false);
                errorIt.putExtra("canClear", true);
                errorIt.setData(Uri.parse(errorData));
                startActivity(errorIt);
                break;
            
            case 5:
                File errorLogFile = FileUtil.exportFile(FileUtil.getErrorLogFile());
                if (errorLogFile != null) {
                    ToastUtil.show(this, "文件已导出到: " + errorLogFile.getPath());
                }
                break;
            
            case 6:
                String allData = "file://";
                allData += FileUtil.getRuntimeLogFile().getAbsolutePath();
                Intent allIt = new Intent(this, HtmlViewerActivity.class);
                allIt.putExtra("nextLine", false);
                allIt.putExtra("canClear", true);
                allIt.setData(Uri.parse(allData));
                startActivity(allIt);
                break;
            
            case 7:
                File allLogFile = FileUtil.exportFile(FileUtil.getRuntimeLogFile());
                if (allLogFile != null) {
                    ToastUtil.show(this, "文件已导出到: " + allLogFile.getPath());
                }
                break;
            
            case 8:
                File statisticsFile = FileUtil.exportFile(FileUtil.getStatisticsFile());
                if (statisticsFile != null) {
                    ToastUtil.show(this, "文件已导出到: " + statisticsFile.getPath());
                }
                break;
            
            case 9:
                if (FileUtil.copyTo(FileUtil.getExportedStatisticsFile(), FileUtil.getStatisticsFile())) {
                    tvStatistics.setText(Statistics.getText(MainActivity.this));
                    ToastUtil.show(this, "导入成功！");
                }
                break;
            
            case 10:
                int state = item.isChecked() ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                getPackageManager().setComponentEnabledSetting(new ComponentName(this, getClass().getCanonicalName() + "Alias"), state, PackageManager.DONT_KILL_APP);
                item.setChecked(!item.isChecked());
                break;
            
            case 11:
                AppConfig appConfig = AppConfig.INSTANCE;
                appConfig.setLanguageSimplifiedChinese(!appConfig.getLanguageSimplifiedChinese());
                if (AppConfig.save()) {
                    item.setChecked(!item.isChecked());
                    LanguageUtil.setLocal(this);
                    recreate();
                }
                break;
            
            case 12:
                selectSettingUid();
                break;
        }
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
        // 在 AlertDialog 显示之后获取返回按钮并设置颜色
        Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.button));
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
        boolean isNewUI = AppConfig.INSTANCE.getNewUI() && !"TEST".equals(ViewAppInfo.getAppVersion()) && LibraryUtil.loadLibrary("sesame");
        Intent intent = new Intent(this, isNewUI ? NewSettingsActivity.class : SettingsActivity.class);
        if (userEntity != null) {
            intent.putExtra("userId", userEntity.getUserId());
            intent.putExtra("userName", userEntity.getShowName());
        }
        else {
            intent.putExtra("userName", userNameArray[index]);
        }
        startActivity(intent);
    }
    
    private void updateSubTitle(RunType runType) {
        switch (runType) {
            case DISABLE:
                setBaseTitle(ViewAppInfo.getAppTitle() + "【" + getString(R.string.disable) + "】");
                setBaseTitleTextColor(ContextCompat.getColor(this, R.color.textColorDisable));
                break;
            case MODEL:
                setBaseTitle(ViewAppInfo.getAppTitle() + "【" + getString(R.string.activated) + "】");
                setBaseTitleTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
                break;
            case PACKAGE:
                setBaseTitle(ViewAppInfo.getAppTitle() + "【" + getString(R.string.loading) + "】");
                setBaseTitleTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
                break;
        }
    }
    
}
