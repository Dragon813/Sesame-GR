package io.github.lazyimmortal.sesame.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import io.github.lazyimmortal.sesame.BuildConfig;
import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.AppConfig;
import io.github.lazyimmortal.sesame.data.ConfigV2;
import io.github.lazyimmortal.sesame.data.Model;
import io.github.lazyimmortal.sesame.data.ModelConfig;
import io.github.lazyimmortal.sesame.data.ModelField;
import io.github.lazyimmortal.sesame.data.ModelFields;
import io.github.lazyimmortal.sesame.data.ModelGroup;
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.ui.dto.ModelDto;
import io.github.lazyimmortal.sesame.ui.dto.ModelFieldInfoDto;
import io.github.lazyimmortal.sesame.ui.dto.ModelFieldShowDto;
import io.github.lazyimmortal.sesame.ui.dto.ModelGroupDto;
import io.github.lazyimmortal.sesame.util.FileUtil;
import io.github.lazyimmortal.sesame.util.IconRegistry;
import io.github.lazyimmortal.sesame.util.JsonUtil;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.ToastUtil;
import io.github.lazyimmortal.sesame.util.idMap.AnimalIdMap;
import io.github.lazyimmortal.sesame.util.idMap.AntDodoTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmChickenChefTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntMemberTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntOceanAntiepTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntOceanFishBlackListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntOrchardDrawTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntOrchardTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntSportsTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.AntStallTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.BeachIdMap;
import io.github.lazyimmortal.sesame.util.idMap.GameCenterMallItemMap;
import io.github.lazyimmortal.sesame.util.idMap.GoldenBeansTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.MonopolyTaskListMap;
import io.github.lazyimmortal.sesame.util.idMap.PlantSceneIdMap;
import io.github.lazyimmortal.sesame.util.idMap.ForestHuntIdMap;
import io.github.lazyimmortal.sesame.util.idMap.CooperationIdMap;
import io.github.lazyimmortal.sesame.util.idMap.FarmOrnamentsIdMap;
import io.github.lazyimmortal.sesame.util.idMap.MarathonIdMap;
import io.github.lazyimmortal.sesame.util.idMap.MemberBenefitIdMap;
import io.github.lazyimmortal.sesame.util.idMap.NewAncientTreeIdMap;
import io.github.lazyimmortal.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.lazyimmortal.sesame.util.idMap.ReserveIdMap;
import io.github.lazyimmortal.sesame.util.idMap.TreeIdMap;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;
import io.github.lazyimmortal.sesame.util.idMap.VitalityBenefitIdMap;
import io.github.lazyimmortal.sesame.util.idMap.PathThemeMapListMap;
import io.github.lazyimmortal.sesame.util.idMap.rpcRequestMap;

public class NewSettingsActivity extends BaseActivity {
    
    private static final Integer EXPORT_REQUEST_CODE = 1;
    
    private static final Integer IMPORT_REQUEST_CODE = 2;
    private WebView webView;
    private Context context;
    private String userId = null;
    private String userName = null;
    private Boolean debug = false;
    
    private final List<ModelDto> tabList = new ArrayList<>();
    
    private final List<ModelGroupDto> groupList = new ArrayList<>();
    
    /* 列表字段序列化结果缓存: 同一列表反复打开时免去整表重新序列化(选择值变更时清空) */
    private final Map<String, String> fieldJsonCache = new HashMap<>();
    
    /* 后台初始化完成信号: 模型/配置/各清单 三段就绪 */
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final CountDownLatch configLatch = new CountDownLatch(1);
    private final CountDownLatch listLatch = new CountDownLatch(1);
    
    @Override
    public String getBaseSubtitle() {
        return getString(R.string.settings);
    }
    
    @SuppressLint({"MissingInflatedId", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = null;
        userName = null;
        //debug = true;
        Intent intent = getIntent();
        if (intent != null) {
            userId = intent.getStringExtra("userId");
            userName = intent.getStringExtra("userName");
            debug = intent.getBooleanExtra("debug", debug);
        }
        //配置与各清单的加载放到后台线程, 避免阻塞界面首屏; JS 通过桥接访问时等待加载完成
        new Thread(this::doInit, "settings-init").start();
        setContentView(R.layout.activity_new_settings);
        if (userName != null) {
            setBaseSubtitle(getString(R.string.settings) + ": " + userName);
        }
        setBaseSubtitleTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
        
        context = this;
        
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setJavaScriptEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        //settings.setPluginsEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setDefaultTextEncodingName(StandardCharsets.UTF_8.name());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 强制在当前 WebView 中加载 url
                Uri requestUrl = request.getUrl();
                String scheme = requestUrl.getScheme();
                assert scheme != null;
                if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("ws") || scheme.equalsIgnoreCase("wss")) {
                    view.loadUrl(requestUrl.toString());
                    return true;
                }
                view.stopLoading();
                ToastUtil.show(context, "Forbidden Scheme:\"" + scheme + "\"");
                return false;
            }
            
        });
        if (debug) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webView.addJavascriptInterface(new WebViewCallback(), "HOOK");
        webView.loadUrl("file:///android_asset/web/index.html");
        webView.requestFocus();
    }
    
    /**
     * 后台初始化: 构建全部模型并加载各清单/配置, 完成后放行桥接调用
     */
    private void doInit() {
        //第一阶段: 模型与页签, 决定首屏能否渲染
        try {
            Model.initAllModel();
            UserIdMap.setCurrentUserId(userId);
            Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
            for (Map.Entry<String, ModelConfig> configEntry : modelConfigMap.entrySet()) {
                ModelConfig modelConfig = configEntry.getValue();
                tabList.add(new ModelDto(configEntry.getKey(), modelConfig.getName(), modelConfig.getIcon(), null));
            }
            for (ModelGroup modelGroup : ModelGroup.values()) {
                groupList.add(new ModelGroupDto(modelGroup.getCode(), modelGroup.getName(), modelGroup.getIcon()));
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        } finally {
            initLatch.countDown();
        }
        //第二阶段: 好友表与配置值, 页签字段渲染时等待
        try {
            UserIdMap.load(userId);
            ConfigV2.load(userId);
        } catch (Throwable t) {
            Log.printStackTrace(t);
        } finally {
            configLatch.countDown();
        }
        //第三阶段: 各选择列表数据, 打开列表时等待
        try {
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
            IconRegistry.load();
            rpcRequestMap.load();
            ForestHuntIdMap.load();
            MemberCreditSesameTaskListMap.load();
            AntForestVitalityTaskListMap.load();
            AntForestHuntTaskListMap.load();
            AntFarmDoFarmTaskListMap.load();
            AntFarmDrawMachineTaskListMap.load();
            AntFarmChickenChefTaskListMap.load();
            AntDodoTaskListMap.load();
            AntOceanAntiepTaskListMap.load();
            AntOceanFishBlackListMap.load();
            AntOrchardTaskListMap.load();
            AntOrchardDrawTaskListMap.load();
            GoldenBeansTaskListMap.load();
            MonopolyTaskListMap.load();
            AntStallTaskListMap.load();
            AntSportsTaskListMap.load();
            AntMemberTaskListMap.load();
            PathThemeMapListMap.load();
        } catch (Throwable t) {
            Log.printStackTrace(t);
        } finally {
            listLatch.countDown();
        }
    }
    
    /**
     * 等待后台初始化完成(在桥接/保存线程调用), 保证模型与清单可用
     */
    private void awaitInit() {
        try {
            initLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 等待配置值加载完成(渲染页签字段时使用)
     */
    private void awaitConfig() {
        try {
            configLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 等待各选择列表数据加载完成(仅打开列表/保存时使用)
     */
    private void awaitLists() {
        try {
            listLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
        else {
            super.onBackPressed();
            save();
        }
    }
    
    public class WebAppInterface {
        @JavascriptInterface
        public void onBackPressed() {
            runOnUiThread(() -> {
                if (webView.canGoBack()) {
                    webView.goBack();
                }
                else {
                    NewSettingsActivity.this.finish();
                }
            });
        }
        
        @JavascriptInterface
        public void onExit() {
            runOnUiThread(NewSettingsActivity.this::finish);
        }
    }
    
    private class WebViewCallback {
        
        @JavascriptInterface
        public String getTabs() {
            awaitInit();
            return JsonUtil.toJsonString(tabList);
        }

        /*@JavascriptInterface
        public String getAllConfig() {
            return JsonUtil.toJsonString(ModelTask.getModelConfigMap());
        }*/
        
        @JavascriptInterface
        public String getBuildInfo() {
            return BuildConfig.APPLICATION_ID + ":" + BuildConfig.VERSION_NAME;
        }
        
        @JavascriptInterface
        public String getUserId() {
            return userId;
        }
        
        @JavascriptInterface
        public String getGroup() {
            awaitInit();
            return JsonUtil.toJsonString(groupList);
        }
        
        @JavascriptInterface
        public String getModelByGroup(String groupCode) {
            awaitInit();
            awaitConfig();
            Collection<ModelConfig> modelConfigCollection = ModelTask.getGroupModelConfig(ModelGroup.getByCode(groupCode)).values();
            List<ModelDto> modelDtoList = new ArrayList<>();
            for (ModelConfig modelConfig : modelConfigCollection) {
                List<ModelFieldShowDto> modelFields = new ArrayList<>();
                for (ModelField<?> modelField : modelConfig.getFields().values()) {
                    modelFields.add(ModelFieldShowDto.toShowDto(modelField));
                }
                modelDtoList.add(new ModelDto(modelConfig.getCode(), modelConfig.getName(), groupCode, modelFields));
            }
            return JsonUtil.toJsonString(modelDtoList);
        }
        
        @JavascriptInterface
        public String setModelByGroup(String groupCode, String modelsValue) {
            awaitInit();
            awaitConfig();
            List<ModelDto> modelDtoList = JsonUtil.parseObject(modelsValue, new TypeReference<List<ModelDto>>() {
            });
            Map<String, ModelConfig> modelConfigSet = ModelTask.getGroupModelConfig(ModelGroup.getByCode(groupCode));
            fieldJsonCache.clear();
            for (ModelDto modelDto : modelDtoList) {
                ModelConfig modelConfig = modelConfigSet.get(modelDto.getModelCode());
                if (modelConfig != null) {
                    List<ModelFieldShowDto> modelFields = modelDto.getModelFields();
                    if (modelFields != null) {
                        for (ModelFieldShowDto newModelField : modelFields) {
                            if (newModelField != null) {
                                ModelField<?> modelField = modelConfig.getModelField(newModelField.getCode());
                                if (modelField != null) {
                                    modelField.setConfigValue(newModelField.getConfigValue());
                                }
                            }
                        }
                    }
                }
            }
            return "SUCCESS";
        }
        
        @JavascriptInterface
        public String getModel(String modelCode) {
            awaitInit();
            awaitConfig();
            ModelConfig modelConfig = ModelTask.getModelConfigMap().get(modelCode);
            if (modelConfig != null) {
                ModelFields modelFields = modelConfig.getFields();
                List<ModelFieldShowDto> list = new ArrayList<>();
                for (ModelField<?> modelField : modelFields.values()) {
                    list.add(ModelFieldShowDto.toShowDto(modelField));
                }
                return JsonUtil.toJsonString(list);
            }
            return null;
        }
        
        @JavascriptInterface
        public String setModel(String modelCode, String fieldsValue) {
            awaitInit();
            awaitConfig();
            ModelConfig modelConfig = ModelTask.getModelConfigMap().get(modelCode);
            if (modelConfig != null) {
                try {
                    ModelFields modelFields = modelConfig.getFields();
                    Map<String, ModelFieldShowDto> map = JsonUtil.parseObject(fieldsValue, new TypeReference<Map<String, ModelFieldShowDto>>() {
                    });
                    fieldJsonCache.clear();
                    for (Map.Entry<String, ModelFieldShowDto> entry : map.entrySet()) {
                        ModelFieldShowDto newModelField = entry.getValue();
                        if (newModelField != null) {
                            ModelField<?> modelField = modelFields.get(entry.getKey());
                            if (modelField != null) {
                                modelField.setConfigValue(newModelField.getConfigValue());
                            }
                        }
                    }
                    return "SUCCESS";
                }
                catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
            return "FAILED";
        }
        
        @JavascriptInterface
        public String getField(String modelCode, String fieldCode) {
            awaitInit();
            awaitConfig();
            awaitLists();
            String cacheKey = modelCode + "\u0000" + fieldCode;
            String cached = fieldJsonCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            ModelConfig modelConfig = ModelTask.getModelConfigMap().get(modelCode);
            if (modelConfig != null) {
                ModelField<?> modelField = modelConfig.getModelField(fieldCode);
                if (modelField != null) {
                    String json = JsonUtil.toJsonString(ModelFieldInfoDto.toInfoDto(modelField));
                    fieldJsonCache.put(cacheKey, json);
                    return json;
                }
            }
            return null;
        }
        
        @JavascriptInterface
        public boolean isListsReady() {
            return initLatch.getCount() == 0 && configLatch.getCount() == 0 && listLatch.getCount() == 0;
        }
        
        @JavascriptInterface
        public String setField(String modelCode, String fieldCode, String fieldValue) {
            awaitInit();
            ModelConfig modelConfig = ModelTask.getModelConfigMap().get(modelCode);
            if (modelConfig != null) {
                try {
                    ModelField<?> modelField = modelConfig.getModelField(fieldCode);
                    if (modelField != null) {
                        modelField.setConfigValue(fieldValue);
                        fieldJsonCache.clear();
                        return "SUCCESS";
                    }
                }
                catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
            return "FAILED";
        }
        
        @JavascriptInterface
        public void Log(String log) {
            //Log.record("设置："+ log);
        }
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 1, "导出配置");
        menu.add(0, 2, 2, "导入配置");
        menu.add(0, 3, 3, "删除配置");
        menu.add(0, 4, 4, "单向好友");
        menu.add(0, 5, 5, "切换至旧UI");
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        awaitInit();
        switch (item.getItemId()) {
            case 1:
                Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                exportIntent.setType("*/*");
                exportIntent.putExtra(Intent.EXTRA_TITLE, "[" + userName + "]-config_v2.json");
                startActivityForResult(exportIntent, EXPORT_REQUEST_CODE);
                break;
            case 2:
                Intent importIntent = new Intent(Intent.ACTION_GET_CONTENT);
                importIntent.addCategory(Intent.CATEGORY_OPENABLE);
                importIntent.setType("*/*");
                importIntent.putExtra(Intent.EXTRA_TITLE, "config_v2.json");
                startActivityForResult(importIntent, IMPORT_REQUEST_CODE);
                break;
            case 3:
                new AlertDialog.Builder(context).setTitle("警告").setMessage("确认删除该配置？").setPositiveButton(R.string.ok, (dialog, id) -> {
                    File userConfigDirectoryFile;
                    if (StringUtil.isEmpty(userId)) {
                        userConfigDirectoryFile = FileUtil.getDefaultConfigV2File();
                    }
                    else {
                        userConfigDirectoryFile = FileUtil.getUserConfigDirectoryFile(userId);
                    }
                    if (FileUtil.deleteFile(userConfigDirectoryFile)) {
                        ToastUtil.show(this, "配置删除成功");
                    }
                    else {
                        ToastUtil.show(this, "配置删除失败");
                    }
                    finish();
                }).setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss()).create().show();
                break;
            case 4:
                ListDialog.show(this, "单向好友列表", AlipayUser.getList(user -> user.getFriendStatus() != 1), SelectModelFieldFunc.newMapInstance(), false, ListDialog.ListType.SHOW);
                break;
            case 5:
                AppConfig.INSTANCE.setNewUI(false);
                if (AppConfig.save()) {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    intent.putExtra("userId", userId);
                    intent.putExtra("userName", userName);
                    finish();
                    startActivity(intent);
                }
                else {
                    ToastUtil.show(this, "切换失败");
                }
                break;
        }
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
                    File configV2File;
                    if (StringUtil.isEmpty(userId)) {
                        configV2File = FileUtil.getDefaultConfigV2File();
                    }
                    else {
                        configV2File = FileUtil.getConfigV2File(userId);
                    }
                    FileInputStream inputStream = new FileInputStream(configV2File);
                    if (FileUtil.streamTo(inputStream, getContentResolver().openOutputStream(data.getData()))) {
                        ToastUtil.show(this, "导出成功！");
                    }
                    else {
                        ToastUtil.show(this, "导出失败！");
                    }
                }
                catch (IOException e) {
                    Log.printStackTrace(e);
                    ToastUtil.show(this, "导出失败！");
                }
            }
        }
        else if (requestCode == IMPORT_REQUEST_CODE) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    File configV2File;
                    if (StringUtil.isEmpty(userId)) {
                        configV2File = FileUtil.getDefaultConfigV2File();
                    }
                    else {
                        configV2File = FileUtil.getConfigV2File(userId);
                    }
                    FileOutputStream outputStream = new FileOutputStream(configV2File);
                    if (FileUtil.streamTo(getContentResolver().openInputStream(data.getData()), outputStream)) {
                        ToastUtil.show(this, "导入成功！");
                        if (!StringUtil.isEmpty(userId)) {
                            try {
                                Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.restart");
                                intent.putExtra("userId", userId);
                                sendBroadcast(intent);
                            }
                            catch (Throwable th) {
                                Log.printStackTrace(th);
                            }
                        }
                        Intent intent = getIntent();
                        finish();
                        startActivity(intent);
                    }
                    else {
                        ToastUtil.show(this, "导入失败！");
                    }
                }
                catch (IOException e) {
                    Log.printStackTrace(e);
                    ToastUtil.show(this, "导入失败！");
                }
            }
        }
    }
    
    private void save() {
        awaitInit();
        awaitConfig();
        awaitLists();
        if (ConfigV2.isModify(userId) && ConfigV2.save(userId, false)) {
            ToastUtil.show(this, "保存成功！");
            if (!StringUtil.isEmpty(userId)) {
                try {
                    Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.restart");
                    intent.putExtra("userId", userId);
                    sendBroadcast(intent);
                }
                catch (Throwable th) {
                    Log.printStackTrace(th);
                }
            }
        }
        if (!StringUtil.isEmpty(userId)) {
            UserIdMap.save(userId);
        }
    }
    
}
