package io.github.lazyimmortal.sesame.hook;

import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesame.util.Log;
import java.util.HashMap;
import java.util.Collections;

/**
 * OAuth2 授权码服务助手类
 * 用于调用目标应用的 OpenAuthExtension.getAuthCode 方法
 */
public class AuthCodeHelper {
    private static final String TAG = "Oauth2AuthCodeHelper";
    private static ClassLoader classLoader;
    
    /**
     * 初始化 Oauth2AuthCodeHelper
     * @param loader 应用类加载器
     */
    public static void init(ClassLoader loader) {
        classLoader = loader;
        Log.record("Oauth2AuthCodeHelper 初始化完成");
    }
    
    /**
     * 主动调用获取授权码
     * 通过反射调用 Oauth2AuthCodeService.getAuthSkipResult 方法获取授权码
     *
     * @param appId 应用ID
     * @return code，失败返回null
     */
    public static String getAuthCode(String appId) {
        try {
            if (classLoader == null) {
                Log.error("Oauth2AuthCodeHelper 未初始化，请先调用 init 方法");
                return null;
            }
            
            // 1. 获取并实例化 Oauth2AuthCodeServiceImpl 类
            Class<?> oauth2AuthCodeServiceImplClass = XposedHelpers.findClass(
                    "com.alibaba.ariver.rpc.biz.proxy.Oauth2AuthCodeServiceImpl",
                    classLoader
            );
            Object oauth2AuthCodeServiceImpl = XposedHelpers.newInstance(oauth2AuthCodeServiceImplClass);
            
            // 2. 获取并实例化 AuthSkipRequestModel 类
            Class<?> authSkipRequestModelClass = XposedHelpers.findClass(
                    "com.alibaba.ariver.permission.openauth.model.request.AuthSkipRequestModel",
                    classLoader
            );
            Object authSkipRequestModel = XposedHelpers.newInstance(authSkipRequestModelClass);
            
            // 3. 设置 AuthSkipRequestModel 的参数
            XposedHelpers.callMethod(authSkipRequestModel, "setAppId", appId);
            XposedHelpers.callMethod(
                    authSkipRequestModel,
                    "setCurrentPageUrl",
                    "https://" + appId + ".hybrid.alipay-eco.com/index.html"
            );
            XposedHelpers.callMethod(authSkipRequestModel, "setFromSystem", "mobilegw_android");
            // Java 中 List.of 是不可变列表，对应 Kotlin 的 listOf
            XposedHelpers.callMethod(authSkipRequestModel, "setScopeNicks", Collections.singletonList("auth_base"));
            XposedHelpers.callMethod(
                    authSkipRequestModel,
                    "setState",
                    "QnJpbmcgc21hbGwgYW5kIGJlYXV0aWZ1bCBjaGFuZ2VzIHRvIHRoZSB3b3JsZA=="
            );
            XposedHelpers.callMethod(authSkipRequestModel, "setIsvAppId", "");
            XposedHelpers.callMethod(authSkipRequestModel, "setExtInfo", new HashMap<String, String>());
            
            // 构建并设置 appExtInfo 参数
            HashMap<String, String> appExtInfo = new HashMap<>();
            appExtInfo.put("channel", "tinyapp");
            appExtInfo.put("clientAppId", appId);
            XposedHelpers.callMethod(authSkipRequestModel, "setAppExtInfo", appExtInfo);
            
            // 4. 调用 getAuthSkipResult 方法获取授权结果
            Object authSkipResult = XposedHelpers.callMethod(
                    oauth2AuthCodeServiceImpl,
                    "getAuthSkipResult",
                    "AP",
                    null,
                    authSkipRequestModel
            );
            
            // 5. 解析返回结果中的授权码
            if (authSkipResult != null) {
                Object authExecuteResult = XposedHelpers.callMethod(authSkipResult, "getAuthExecuteResult");
                if (authExecuteResult != null) {
                    Object authCodeObj = XposedHelpers.callMethod(authExecuteResult, "getAuthCode");
                    return authCodeObj instanceof String ? (String) authCodeObj : null;
                }
            }
            
            return null;
        } catch (Throwable e) {
            Log.printStackTrace(TAG+"主动调用获取授权码失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 私有化构造方法，避免类被实例化（对应 Kotlin 的 object 单例）
     */
    private AuthCodeHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}