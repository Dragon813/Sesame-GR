package io.github.lazyimmortal.sesame.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.core.content.ContextCompat;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.*;
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SettingsActivity extends BaseActivity {

    private static final Integer EXPORT_REQUEST_CODE = 1;
    private static final Integer IMPORT_REQUEST_CODE = 2;

    private Context context;
    private String userId;
    private String userName;
    private LinearLayout tabList;
    private LinearLayout contentPanel;
    private List<TextView> tabViews = new ArrayList<>();
    private List<ModelFields> tabContentModels = new ArrayList<>();
    private int currentTab = 0;

    @Override
    public void onContentChanged() {
        // 不设置 Toolbar，使用自定义大标题
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = null;
        userName = null;
        Intent intent = getIntent();
        if (intent != null) {
            userId = intent.getStringExtra("userId");
            userName = intent.getStringExtra("userName");
        }
        Model.initAllModel();
        UserIdMap.setCurrentUserId(userId);
        UserIdMap.load(userId);
        CooperationIdMap.load(userId);
        VitalityBenefitIdMap.load(userId);
        GameCenterMallItemMap.load(userId);
        FarmOrnamentsIdMap.load(userId);
        MemberBenefitIdMap.load(userId);
        PromiseSimpleTemplateIdMap.load(userId);
        TreeIdMap.load();
        ReserveIdMap.load();
        AnimalIdMap.load();
        MarathonIdMap.load();
        NewAncientTreeIdMap.load();
        BeachIdMap.load();
        PlantSceneIdMap.load();
        rpcRequestMap.load();
        ForestHuntIdMap.load();
        MemberCreditSesameTaskListMap.load();
        AntForestVitalityTaskListMap.load();
        AntForestHuntTaskListMap.load();
        AntFarmDoFarmTaskListMap.load();
        AntFarmDrawMachineTaskListMap.load();
        AntOceanAntiepTaskListMap.load();
        AntOrchardTaskListMap.load();
        AntStallTaskListMap.load();
        AntSportsTaskListMap.load();
        PathThemeMapListMap.load();
        AntMemberTaskListMap.load();
        ConfigV2.load(userId);
        setContentView(R.layout.activity_settings);

        context = this;
        tabList = findViewById(R.id.tab_list);
        contentPanel = findViewById(R.id.content_panel);

        // 构建自定义 Tab
        Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
        int index = 0;
        int tabTextColor = ContextCompat.getColor(this, R.color.miuix_on_background);
        int tabPaddingH = (int) getResources().getDimension(R.dimen.miuix_spacing_12);
        int tabPaddingV = (int) getResources().getDimension(R.dimen.miuix_spacing_12);
        int density = (int) getResources().getDisplayMetrics().density;

        for (Map.Entry<String, ModelConfig> configEntry : modelConfigMap.entrySet()) {
            ModelConfig modelConfig = configEntry.getValue();
            ModelFields modelFields = modelConfig.getFields();
            tabContentModels.add(modelFields);

            // 创建左侧 Tab 按钮
            final int tabIndex = index;
            TextView tabBtn = new TextView(context);
            tabBtn.setText(modelConfig.getName());
            tabBtn.setTextSize(16);
            tabBtn.setTextColor(tabTextColor);
            tabBtn.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setPadding(tabPaddingH, tabPaddingV, tabPaddingH, tabPaddingV);
            tabBtn.setMinHeight(44 * density);
            tabBtn.setSingleLine(true);
            tabBtn.setOnClickListener(v -> switchTab(tabIndex));

            // 分隔线（非第一项）
            if (index > 0) {
                View divider = new View(context);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(ContextCompat.getColor(context, R.color.miuix_divider_line));
                tabList.addView(divider);
            }

            tabList.addView(tabBtn);
            tabViews.add(tabBtn);
            index++;
        }

        // 默认选中第一个
        if (tabViews.size() > 0) {
            switchTab(0);
        }

        // 绑定大标题右侧按钮
        ImageView btnExport = findViewById(R.id.btn_export_config);
        ImageView btnImport = findViewById(R.id.btn_import_config);
        ImageView btnDelete = findViewById(R.id.btn_delete_config);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> {
                Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                exportIntent.setType("*/*");
                exportIntent.putExtra(Intent.EXTRA_TITLE,
                        "[" + (userName != null ? userName : "默认") + "]-config_v2.json");
                startActivityForResult(exportIntent, EXPORT_REQUEST_CODE);
            });
        }
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> {
                Intent importIntent = new Intent(Intent.ACTION_GET_CONTENT);
                importIntent.addCategory(Intent.CATEGORY_OPENABLE);
                importIntent.setType("*/*");
                importIntent.putExtra(Intent.EXTRA_TITLE, "config_v2.json");
                startActivityForResult(importIntent, IMPORT_REQUEST_CODE);
            });
        }
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("警告")
                        .setMessage(userName != null
                                ? "确认删除用户[" + userName + "]的配置？"
                                : "确认删除默认配置？")
                        .setPositiveButton(R.string.ok, (dialog, id) -> {
                            File userConfigDirectoryFile;
                            if (StringUtil.isEmpty(userId)) {
                                userConfigDirectoryFile = FileUtil.getDefaultConfigV2File();
                            } else {
                                userConfigDirectoryFile = FileUtil.getUserConfigDirectoryFile(userId);
                            }
                            if (FileUtil.deleteFile(userConfigDirectoryFile)) {
                                ToastUtil.show(this, "配置删除成功");
                            } else {
                                ToastUtil.show(this, "配置删除失败");
                            }
                            finish();
                        })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss())
                        .create().show();
            });
        }
    }

    private void switchTab(int index) {
        currentTab = index;

        int primaryColor = ContextCompat.getColor(this, R.color.miuix_primary);
        int textColor = ContextCompat.getColor(this, R.color.miuix_on_background);

        // 更新左侧 Tab 选中状态
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tv = tabViews.get(i);
            if (i == index) {
                tv.setTextColor(primaryColor);
                tv.setBackgroundColor(0x1A3482FF);
            } else {
                tv.setTextColor(textColor);
                tv.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        // 更新右侧内容区
        contentPanel.removeAllViews();
        ModelFields fields = tabContentModels.get(index);
        for (ModelField<?> modelField : fields.values()) {
            View view = modelField.getView(context);
            if (view != null) {
                contentPanel.addView(view);
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        save();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == EXPORT_REQUEST_CODE) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    File configV2File = StringUtil.isEmpty(userId)
                            ? FileUtil.getDefaultConfigV2File()
                            : FileUtil.getConfigV2File(userId);
                    FileInputStream inputStream = new FileInputStream(configV2File);
                    if (FileUtil.streamTo(inputStream, getContentResolver().openOutputStream(data.getData()))) {
                        ToastUtil.show(this, "导出成功！");
                    } else {
                        ToastUtil.show(this, "导出失败！");
                    }
                } catch (IOException e) {
                    Log.printStackTrace(e);
                    ToastUtil.show(this, "导出失败！");
                }
            }
        } else if (requestCode == IMPORT_REQUEST_CODE) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    File configV2File = StringUtil.isEmpty(userId)
                            ? FileUtil.getDefaultConfigV2File()
                            : FileUtil.getConfigV2File(userId);
                    FileOutputStream outputStream = new FileOutputStream(configV2File);
                    if (FileUtil.streamTo(getContentResolver().openInputStream(data.getData()), outputStream)) {
                        ToastUtil.show(this, "导入成功！");
                        if (!StringUtil.isEmpty(userId)) {
                            try {
                                Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.restart");
                                intent.putExtra("userId", userId);
                                sendBroadcast(intent);
                            } catch (Throwable th) {
                                Log.printStackTrace(th);
                            }
                        }
                        Intent intent = getIntent();
                        finish();
                        startActivity(intent);
                    } else {
                        ToastUtil.show(this, "导入失败！");
                    }
                } catch (IOException e) {
                    Log.printStackTrace(e);
                    ToastUtil.show(this, "导入失败！");
                }
            }
        }
    }

    private void save() {
        if (ConfigV2.isModify(userId) && ConfigV2.save(userId, false)) {
            ToastUtil.show(this, "保存成功！");
            if (!StringUtil.isEmpty(userId)) {
                try {
                    Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.restart");
                    intent.putExtra("userId", userId);
                    sendBroadcast(intent);
                } catch (Throwable th) {
                    Log.printStackTrace(th);
                }
            }
        }
        if (!StringUtil.isEmpty(userId)) {
            UserIdMap.save(userId);
        }
    }

}
