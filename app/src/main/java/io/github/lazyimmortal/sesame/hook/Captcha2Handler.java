package io.github.lazyimmortal.sesame.hook;
/**
 * 验证码处理器 - 处理目标应用验证码页面
 * 使用精简版框架自动识别和处理各种验证码场景
 */
public class Captcha2Handler extends BaseCaptchaHandler implements SimplePageManager.ActivityFocusHandler {
    
    /**
     * 获取滑动路径在 DataStore 中的存储 key
     */
    @Override
    public String getSlidePathKey() {
        return "slide_path_API";
    }
    
}