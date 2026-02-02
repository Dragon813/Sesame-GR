
package io.github.lazyimmortal.sesame.hook;

import io.github.lazyimmortal.sesame.util.Log;
import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP服务管理器（单例模式）
 * 对应原Kotlin的ModuleHttpServerManager，负责控制HTTP服务的启动/停止，仅允许主进程启动
 */
public class ModuleHttpServerManager {
    // 日志标签（对应Kotlin的const val）
    private static final String TAG = "ModuleHttpServerManager";
    // 单例实例（Java饿汉式单例，保证线程安全）
    private static final ModuleHttpServerManager INSTANCE = new ModuleHttpServerManager();
    // 持有HTTP服务器实例（对应Kotlin的var server: ModuleHttpServer?）
    private ModuleHttpServer server;
    
    /**
     * 私有构造方法（禁止外部实例化，保证单例）
     */
    private ModuleHttpServerManager() {}
    
    /**
     * 获取单例实例
     * @return ModuleHttpServerManager唯一实例
     */
    public static ModuleHttpServerManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 启动服务（如果尚未启动，同步方法保证线程安全）
     * @param port 监听端口
     * @param secretToken 鉴权令牌
     * @param currentProcessName 当前进程名
     * @param mainProcessName 主进程包名
     */
    public synchronized void startIfNeeded(int port, String secretToken,
                                           String currentProcessName, String mainProcessName) {
        // 1. 安全检查：仅主进程允许启动，避免多进程抢占端口
        if (currentProcessName == null || mainProcessName == null ||
            !currentProcessName.equals(mainProcessName)) {
            return;
        }
        
        // 2. 检查服务是否已运行，已运行则跳过
        if (server != null && server.isAlive()) {
            return;
        }
        
        // 3. 启动逻辑：先停旧服务，再启新服务
        try {
            stop(); // 先停止旧实例（如果存在）
            
            // 创建并启动新服务器
            ModuleHttpServer newServer = new ModuleHttpServer(port, secretToken);
            // 启动NanoHTTPD：参数1=读取超时，参数2=是否异步启动
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            
            server = newServer;
            // 原Kotlin中注释的日志可根据需要启用
            Log.record("服务启动🚀HTTP: http://127.0.0.1:" + port);
            Log.record("标记令牌🔑Token: " + secretToken);
            
        } catch (Exception e) {
            Log.printStackTrace("HTTP 服务启动失败", e);
        }
    }
    
    /**
     * 停止服务（同步方法保证线程安全）
     */
    public synchronized void stop() {
        try {
            if (server != null) {
                server.stop();
                server = null;
                Log.record("HTTP 服务已停止");
            }
        } catch (Exception e) {
            Log.printStackTrace("停止服务异常", e);
        }
    }
}