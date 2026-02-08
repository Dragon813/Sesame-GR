package io.github.lazyimmortal.sesame.util;

import static io.github.lazyimmortal.sesame.util.idMap.UserIdMap.getShowName;
import android.os.Build;
import android.os.Environment;
import io.github.lazyimmortal.sesame.hook.Toast;
import io.github.lazyimmortal.sesame.model.normal.base.BaseModel;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtil {
    private static final String TAG = FileUtil.class.getSimpleName();
    //路径
    public static final String CONFIG_DIRECTORY_NAME = "sesame";
    public static final File MAIN_DIRECTORY_FILE = getMainDirectoryFile();
    public static final File CONFIG_DIRECTORY_FILE = getConfigDirectoryFile();
    public static final File LOG_DIRECTORY_FILE = getLogDirectoryFile();
    private static File cityCodeFile;
    private static File wuaFile;
    
    // 备份相关配置（可根据需求调整n值，比如n=3则A/B/C循环）
    private static int BACKUP_MAX_COUNT = 5; // 配置读取失败时的默认值
    private static final String BACKUP_DIR_NAME = "bak"; // 备份子目录名
    private static final String BACKUP_FILE_PREFIX = "config_v2_";
    private static final String BACKUP_FILE_EXT = ".json";
    
    /**
     * 从 BaseModel 动态读取备份保留份数（核心修改）
     *
     * @return 配置中的备份份数，失败则返回默认值3
     */
    private static int getBackupMaxCountFromConfig() {
        try {
            //获取 BaseModel 实例（注意：原代码bakupConfigDays有拼写错误，建议修正为backupConfigDays）
            Integer configCount = BaseModel.backupConfigDays.getValue();
            
            //校验配置值有效性（非正数则用默认值）
            if (configCount != null && configCount > 0) {
                return configCount;
            } else {
                Log.error("BaseModel中备份份数配置无效（值：" + configCount + "），使用默认值" + 5);
                return 5;
            }
        } catch (Exception e) {
            // 捕获所有异常（BaseModel实例获取失败/方法调用失败等）
            Log.error("读取BaseModel备份配置失败，使用默认值" + 5+e); // 修复日志拼接问题
            return 5;
        }
    }
    
    /**
     * 获取备份根目录（sesame/bak）
     */
    public static File getBackupDirectoryFile() {
        File mainDir = getMainDirectoryFile();
        File backupDir = new File(mainDir, BACKUP_DIR_NAME);
        if (!backupDir.exists()) {
            backupDir.mkdirs(); // 不存在则创建
        }
        return backupDir;
    }
    
    /**
     * 生成备份文件的后缀（A/B/C...）
     *
     * @param index 索引（0=A,1=B,2=C...）
     */
    private static String getBackupSuffix(int index) {
        return String.valueOf((char) ('A' + index));
    }
    
    /**
     * 检查指定用户当天是否已备份过config_v2
     *
     * @param userId 用户ID（空则为默认用户）
     */
    public static boolean isConfigV2BackedUpTodayForUser(String userId) {
        String safeUserId = StringUtil.isEmpty(userId) ? "default" : userId;
        File backupDir = getBackupDirectoryFile();
        int maxCount = getBackupMaxCountFromConfig(); // 动态获取份数
        
        // 遍历所有可能的备份后缀（A/B/C...）
        for (int i = 0; i < maxCount; i++) {
            String suffix = getBackupSuffix(i);
            File backupFile = new File(backupDir, BACKUP_FILE_PREFIX + safeUserId + "_" + suffix + BACKUP_FILE_EXT);
            if (backupFile.exists() && isFileModifiedToday(backupFile)) {
                return true; // 当天已有备份
            }
        }
        return false;
    }
    
    /**
     * 判断文件是否为当天修改（复用项目日志工具的日期格式化）
     */
    private static boolean isFileModifiedToday(File file) {
        long fileLastModified = file.lastModified();
        // 防护：文件不存在/无修改时间
        if (fileLastModified == 0L) {
            return false;
        }
        // 复用 Log 类的线程安全日期格式化器
        SimpleDateFormat dateFormat = Log.DATE_FORMAT_THREAD_LOCAL.get();
        if (dateFormat == null) {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }
        // 格式化文件修改日期和当前日期
        String fileDate = dateFormat.format(new Date(fileLastModified));
        String todayDate = dateFormat.format(new Date());
        // 对比日期字符串
        return fileDate.equals(todayDate);
    }
    
    /**
     * 找到指定用户下「下一个要使用的备份文件」（按A→B→C顺序，循环覆盖）
     * 核心修改：不再找最早修改的文件，而是按后缀顺序分配
     *
     * @param userId 用户ID（空则为默认用户）
     */
    private static File findNextBackupFileForUser(String userId) {
        String safeUserId = StringUtil.isEmpty(userId) ? "default" : userId;
        File backupDir = getBackupDirectoryFile();
        int maxCount = getBackupMaxCountFromConfig(); // 动态获取份数
        
        // 步骤1：遍历所有后缀（A→B→C），找到第一个「不存在」的文件
        for (int i = 0; i < maxCount; i++) {
            String suffix = getBackupSuffix(i);
            File file = new File(backupDir, BACKUP_FILE_PREFIX + safeUserId + "_" + suffix + BACKUP_FILE_EXT);
            if (!file.exists()) {
                return file; // 找到未使用的后缀，返回该文件
            }
        }
        
        // 步骤2：所有后缀都已使用，找到「修改时间最早」的后缀文件（循环覆盖）
        // （注：这里保留时间排序是为了循环时覆盖最早的，保证A→B→C→A的逻辑）
        File oldestFile = null;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < maxCount; i++) {
            String suffix = getBackupSuffix(i);
            File file = new File(backupDir, BACKUP_FILE_PREFIX + safeUserId + "_" + suffix + BACKUP_FILE_EXT);
            long fileTime = file.lastModified();
            if (fileTime < oldestTime) {
                oldestTime = fileTime;
                oldestFile = file;
            }
        }
        
        // 兜底：理论上不会为空，因为步骤1已确认所有文件都存在
        return oldestFile != null ? oldestFile : new File(backupDir, BACKUP_FILE_PREFIX + safeUserId + "_A" + BACKUP_FILE_EXT);
    }
    
    /**
     * 执行用户config_v2的n天滚动备份（每日一次，按A→B→C顺序循环）
     *
     * @param userId 用户ID（空则为默认用户）
     */
    public static void backupConfigV2WithRolling(String userId) {
        BACKUP_MAX_COUNT = getBackupMaxCountFromConfig();
        // 1. 校验：当天已备份则跳过
        //if (isConfigV2BackedUpTodayForUser(userId)) {
         //   Log.record(FileUtil.class.getSimpleName()+"#用户[" + (StringUtil.isEmpty(userId) ? "default" : userId) + "]当天已备份，跳过");
          //  return;
        //}
        
        // 2. 获取原配置文件
        File originalFile = StringUtil.isEmpty(userId) ? getDefaultConfigV2File() : getConfigV2File(userId);
        if (!originalFile.exists()) {
            Log.error("原配置文件不存在，跳过备份: " + originalFile.getPath());
            return;
        }
        
        // 3. 找到该用户下一个要使用的备份文件（按A→B→C顺序）
        File targetFile = findNextBackupFileForUser(userId); // 替换为新的方法
        
        // 4. 执行备份（覆盖目标文件）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.copy(originalFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Log.record("备份成功🔄配置覆盖滚动" + BACKUP_MAX_COUNT + "次循环#用户:" + (StringUtil.isEmpty(userId) ? "default" : getShowName(getShowName(userId))) + "#备份文件:" + getBackupDirectoryFile().getPath() + "/" + targetFile.getName());
        } catch (IOException e) {
            Log.printStackTrace(FileUtil.class.getSimpleName(), e);
            Log.error("备份失败|用户: " + (StringUtil.isEmpty(userId) ? "default" : userId) + "|原因: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("deprecation")
    private static File getMainDirectoryFile() {
        String storageDirStr = Environment.getExternalStorageDirectory() + File.separator + "Android" + File.separator + "media" + File.separator + ClassUtil.PACKAGE_NAME;
        File storageDir = new File(storageDirStr);
        File mainDir = new File(storageDir, CONFIG_DIRECTORY_NAME);
        if (mainDir.exists()) {
            if (mainDir.isFile()) {
                mainDir.delete();
                mainDir.mkdirs();
            }
        }
        else {
            mainDir.mkdirs();
            /*File oldDirectory = new File(Environment.getExternalStorageDirectory(), CONFIG_DIRECTORY_NAME);
            if (oldDirectory.exists()) {
                File deprecatedFile = new File(oldDirectory, "deprecated");
                if (!deprecatedFile.exists()) {
                    copyFile(oldDirectory, mainDirectory, "config.json");
                    copyFile(oldDirectory, mainDirectory, "friendId.list");
                    copyFile(oldDirectory, mainDirectory, "cooperationId.list");
                    copyFile(oldDirectory, mainDirectory, "reserveId.list");
                    copyFile(oldDirectory, mainDirectory, "statistics.json");
                    copyFile(oldDirectory, mainDirectory, "cityCode.json");
                    try {
                        deprecatedFile.createNewFile();
                    } catch (Throwable ignored) {
                    }
                }
            }*/
        }
        return mainDir;
    }
    
    private static File getLogDirectoryFile() {
        File logDir = new File(MAIN_DIRECTORY_FILE, "log");
        if (logDir.exists()) {
            if (logDir.isFile()) {
                logDir.delete();
                logDir.mkdirs();
            }
        }
        else {
            logDir.mkdirs();
        }
        return logDir;
    }
    
    private static File getConfigDirectoryFile() {
        File configDir = new File(MAIN_DIRECTORY_FILE, "config");
        if (configDir.exists()) {
            if (configDir.isFile()) {
                configDir.delete();
                configDir.mkdirs();
            }
        }
        else {
            configDir.mkdirs();
        }
        return configDir;
    }
    
    public static File getUserConfigDirectoryFile(String userId) {
        File configDir = new File(CONFIG_DIRECTORY_FILE, userId);
        if (configDir.exists()) {
            if (configDir.isFile()) {
                configDir.delete();
                configDir.mkdirs();
            }
        }
        else {
            configDir.mkdirs();
        }
        return configDir;
    }
    
    public static File getDefaultConfigV2File() {
        return new File(MAIN_DIRECTORY_FILE, "config_v2.json");
    }
    
    public static boolean setDefaultConfigV2File(String json) {
        return write2File(json, new File(MAIN_DIRECTORY_FILE, "config_v2.json"));
    }
    
    public static File getConfigV2File(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "config_v2.json");
        if (!file.exists()) {
            File oldFile = new File(CONFIG_DIRECTORY_FILE, "config_v2-" + userId + ".json");
            if (oldFile.exists()) {
                if (write2File(readFromFile(oldFile), file)) {
                    oldFile.delete();
                }
                else {
                    file = oldFile;
                }
            }
        }
        return file;
    }
    
    public static boolean setConfigV2File(String userId, String json) {
        return write2File(json, new File(CONFIG_DIRECTORY_FILE + "/" + userId, "config_v2.json"));
    }
    
    public static File getTokenConfigFile() {
        return new File(MAIN_DIRECTORY_FILE, "token_config.json");
    }
    
    public static boolean setTokenConfigFile(String json) {
        return write2File(json, new File(MAIN_DIRECTORY_FILE, "token_config.json"));
    }
    
    public static File getSelfIdFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "self.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getFriendIdMapFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "friend.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File runtimeInfoFile(String userId) {
        File runtimeInfoFile = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "runtimeInfo.json");
        if (!runtimeInfoFile.exists()) {
            try {
                runtimeInfoFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return runtimeInfoFile;
    }
    
    public static File getCooperationIdMapFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "cooperation.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getVitalityBenefitIdMap(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "vitalityBenefit.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getGameCenterMallItemMap(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "gameCenterMallItem.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getFarmOrnamentsIdMapFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "farmOrnaments.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getMemberBenefitIdMapFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "memberBenefit.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getPromiseSimpleTemplateIdMapFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "promiseSimpleTemplate.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getStatusFile(String userId) {
        File file = new File(CONFIG_DIRECTORY_FILE + "/" + userId, "status.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getStatisticsFile() {
        File statisticsFile = new File(MAIN_DIRECTORY_FILE, "statistics.json");
        if (statisticsFile.exists() && statisticsFile.isDirectory()) {
            statisticsFile.delete();
        }
        if (statisticsFile.exists()) {
            Log.i(TAG, "[statistics]读:" + statisticsFile.canRead() + ";写:" + statisticsFile.canWrite());
        }
        else {
            Log.i(TAG, "statisticsFile.json文件不存在");
        }
        return statisticsFile;
    }
    
    public static File getTreeIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "tree.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getReserveIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "reserve.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAnimalIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "animal.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getMarathonIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "marathon.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getNewAncientTreeIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "newAncientTree.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getPlantSceneIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "PlantScene.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getrpcRequestMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "rpcRequest.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getBeachIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "beach.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getForestHuntIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "ForestHunt.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getMemberCreditSesameTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "MemberCreditSesameTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntForestVitalityTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntForestVitalityTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntForestHuntTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntForestHuntTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntFarmDoFarmTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntFarmDoFarmTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntFarmDrawMachineTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntFarmDrawMachineTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntOceanAntiepTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntOceanAntiepTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntOrchardTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntOrchardTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntStallTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntStallTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntSportsTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntSportsTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getAntMemberTaskListMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "AntMemberTask.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getWalkPathIdMapFile() {
        File file = new File(MAIN_DIRECTORY_FILE, "walkPath.json");
        if (file.exists() && file.isDirectory()) {
            file.delete();
        }
        return file;
    }
    
    public static File getExportedStatisticsFile() {
        String storageDirStr = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + CONFIG_DIRECTORY_NAME;
        File storageDir = new File(storageDirStr);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        File exportedStatisticsFile = new File(storageDir, "statistics.json");
        if (exportedStatisticsFile.exists() && exportedStatisticsFile.isDirectory()) {
            exportedStatisticsFile.delete();
        }
        return exportedStatisticsFile;
    }
    
    public static File getFriendWatchFile() {
        File friendWatchFile = new File(MAIN_DIRECTORY_FILE, "friendWatch.json");
        if (friendWatchFile.exists() && friendWatchFile.isDirectory()) {
            friendWatchFile.delete();
        }
        return friendWatchFile;
    }
    
    public static File getWuaFile() {
        if (wuaFile == null) {
            wuaFile = new File(MAIN_DIRECTORY_FILE, "wua.list");
        }
        return wuaFile;
    }
    
    public static File exportFile(File file) {
        String exportDirStr = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + CONFIG_DIRECTORY_NAME;
        File exportDir = new File(exportDirStr);
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File exportFile = new File(exportDir, file.getName());
        if (exportFile.exists() && exportFile.isDirectory()) {
            exportFile.delete();
        }
        if (FileUtil.copyTo(file, exportFile)) {
            return exportFile;
        }
        return null;
    }
    
    public static File getCityCodeFile() {
        if (cityCodeFile == null) {
            cityCodeFile = new File(MAIN_DIRECTORY_FILE, "cityCode.json");
            if (cityCodeFile.exists() && cityCodeFile.isDirectory()) {
                cityCodeFile.delete();
            }
        }
        return cityCodeFile;
    }
    
    public static File getRuntimeLogFile() {
        File runtimeLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("runtime"));
        if (runtimeLogFile.exists() && runtimeLogFile.isDirectory()) {
            runtimeLogFile.delete();
        }
        if (!runtimeLogFile.exists()) {
            try {
                runtimeLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return runtimeLogFile;
    }
    
    public static File getRecordLogFile() {
        File recordLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("record"));
        if (recordLogFile.exists() && recordLogFile.isDirectory()) {
            recordLogFile.delete();
        }
        if (!recordLogFile.exists()) {
            try {
                recordLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return recordLogFile;
    }
    
    public static File getSystemLogFile() {
        File systemLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("system"));
        if (systemLogFile.exists() && systemLogFile.isDirectory()) {
            systemLogFile.delete();
        }
        if (!systemLogFile.exists()) {
            try {
                systemLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return systemLogFile;
    }
    
    public static File getDebugLogFile() {
        File debugLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("debug"));
        if (debugLogFile.exists() && debugLogFile.isDirectory()) {
            debugLogFile.delete();
        }
        if (!debugLogFile.exists()) {
            try {
                debugLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return debugLogFile;
    }
    
    public static File getForestLogFile() {
        File forestLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("forest"));
        if (forestLogFile.exists() && forestLogFile.isDirectory()) {
            forestLogFile.delete();
        }
        if (!forestLogFile.exists()) {
            try {
                forestLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return forestLogFile;
    }
    
    public static File getFarmLogFile() {
        File farmLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("farm"));
        if (farmLogFile.exists() && farmLogFile.isDirectory()) {
            farmLogFile.delete();
        }
        if (!farmLogFile.exists()) {
            try {
                farmLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return farmLogFile;
    }
    
    public static File getOtherLogFile() {
        File otherLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("other"));
        if (otherLogFile.exists() && otherLogFile.isDirectory()) {
            otherLogFile.delete();
        }
        if (!otherLogFile.exists()) {
            try {
                otherLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return otherLogFile;
    }
    
    public static File getErrorLogFile() {
        File errorLogFile = new File(LOG_DIRECTORY_FILE, Log.getLogFileName("error"));
        if (errorLogFile.exists() && errorLogFile.isDirectory()) {
            errorLogFile.delete();
        }
        if (!errorLogFile.exists()) {
            try {
                errorLogFile.createNewFile();
            }
            catch (Throwable ignored) {
            }
        }
        return errorLogFile;
    }
    
    public static void clearLog() {
        File[] files = LOG_DIRECTORY_FILE.listFiles();
        if (files == null) {
            return;
        }
        SimpleDateFormat sdf = Log.DATE_FORMAT_THREAD_LOCAL.get();
        if (sdf == null) {
            sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }
        String today = sdf.format(new Date());
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(today + ".log")) {
                if (file.length() < 104_857_600) {
                    continue;
                }
            }
            try {
                file.delete();
            }
            catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }
    
    public static String readFromFile(File f) {
        if (!f.exists()) {
            return "";
        }
        if (!f.canRead()) {
            Toast.show(f.getName() + "没有读取权限！", true);
            return "";
        }
        StringBuilder result = new StringBuilder();
        FileReader fr = null;
        try {
            fr = new FileReader(f);
            char[] chs = new char[1024];
            int len;
            while ((len = fr.read(chs)) >= 0) {
                result.append(chs, 0, len);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        finally {
            close(fr);
        }
        return result.toString();
    }
    
    public static boolean write2File(String s, File f) {
        if (f.exists()) {
            if (!f.canWrite()) {
                Toast.show(f.getAbsoluteFile() + "没有写入权限！", true);
                return false;
            }
            if (f.isDirectory()) {
                f.delete();
                f.getParentFile().mkdirs();
            }
        }
        else {
            f.getParentFile().mkdirs();
        }
        boolean success = false;
        FileWriter fw = null;
        try {
            fw = new FileWriter(f);
            fw.write(s);
            fw.flush();
            success = true;
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        close(fw);
        return success;
    }
    
    public static boolean append2File(String s, File f) {
        if (f.exists() && !f.canWrite()) {
            Toast.show(f.getAbsoluteFile() + "没有写入权限！", true);
            return false;
        }
        boolean success = false;
        FileWriter fw = null;
        try {
            fw = new FileWriter(f, true);
            fw.append(s);
            fw.flush();
            success = true;
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        close(fw);
        return success;
    }
    
    public static boolean copyTo(File source, File dest) {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(createFile(dest)).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
            return true;
        }
        catch (IOException e) {
            Log.printStackTrace(e);
        }
        finally {
            try {
                if (inputChannel != null) {
                    inputChannel.close();
                }
            }
            catch (IOException e) {
                Log.printStackTrace(e);
            }
            try {
                if (outputChannel != null) {
                    outputChannel.close();
                }
            }
            catch (IOException e) {
                Log.printStackTrace(e);
            }
        }
        return false;
    }
    
    public static boolean streamTo(InputStream source, OutputStream dest) {
        try {
            byte[] b = new byte[1024];
            int length;
            while ((length = source.read(b)) > 0) {
                dest.write(b, 0, length);
                dest.flush();
            }
            return true;
        }
        catch (IOException e) {
            Log.printStackTrace(e);
        }
        finally {
            try {
                if (source != null) {
                    source.close();
                }
            }
            catch (IOException e) {
                Log.printStackTrace(e);
            }
            try {
                if (dest != null) {
                    dest.close();
                }
            }
            catch (IOException e) {
                Log.printStackTrace(e);
            }
        }
        return false;
    }
    
    public static void close(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static File createFile(File file) {
        if (file.exists() && file.isDirectory()) {
            if (!file.delete()) {
                return null;
            }
        }
        if (!file.exists()) {
            try {
                File parentFile = file.getParentFile();
                if (parentFile != null) {
                    boolean ignore = parentFile.mkdirs();
                }
                if (!file.createNewFile()) {
                    return null;
                }
            }
            catch (Exception e) {
                Log.printStackTrace(e);
                return null;
            }
        }
        return file;
    }
    
    public static File createDirectory(File file) {
        if (file.exists() && file.isFile()) {
            if (!file.delete()) {
                return null;
            }
        }
        if (!file.exists()) {
            try {
                if (!file.mkdirs()) {
                    return null;
                }
            }
            catch (Exception e) {
                Log.printStackTrace(e);
                return null;
            }
        }
        return file;
    }
    
    public static Boolean clearFile(File file) {
        if (file.exists()) {
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(file);
                fileWriter.write("");
                fileWriter.flush();
                return true;
            }
            catch (IOException e) {
                Log.printStackTrace(e);
            }
            finally {
                try {
                    if (fileWriter != null) {
                        fileWriter.close();
                    }
                }
                catch (IOException e) {
                    Log.printStackTrace(e);
                }
            }
        }
        return false;
    }
    
    public static Boolean deleteFile(File file) {
        if (!file.exists()) {
            return false;
        }
        if (file.isFile()) {
            return file.delete();
        }
        File[] files = file.listFiles();
        if (files == null) {
            return file.delete();
        }
        for (File innerFile : files) {
            deleteFile(innerFile);
        }
        return file.delete();
    }
}
