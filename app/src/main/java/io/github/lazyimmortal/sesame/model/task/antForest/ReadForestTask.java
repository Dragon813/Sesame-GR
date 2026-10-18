package io.github.lazyimmortal.sesame.model.task.antForest;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import io.github.lazyimmortal.sesame.hook.AlipayMiniMarkHelper;
import io.github.lazyimmortal.sesame.hook.ApplicationHook;
import io.github.lazyimmortal.sesame.hook.AuthCodeHelper;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.Status;
import io.github.lazyimmortal.sesame.util.TimeUtil;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

/**
 * 森林无纸阅读任务: 读免费书攒阅读时长换绿色能量, 当日能量满额后停止
 */
public final class ReadForestTask {
    private static final String APP_ID = "2021003114652763";
    private static final String PREF_NAME = "sesame_forest_read";
    private static final String FINISHED_KEY = "finished_books";
    private static final String DONE_FLAG = "Forest::readForest";
    private static final Random RANDOM = new Random();
    private static final String TAG = "森林阅读📖";
    private static final int TOTAL_FALLBACK = 150;
    private static final String VERSION = "0.2.2410251510.53";

    private ReadForestTask() {
    }

    public static void execute() {
        try {
            Context context = ApplicationHook.getContext();
            if (context == null) {
                Log.record(TAG + "未获取到应用上下文，自动阅读任务失败");
                return;
            }
            if (Status.hasFlagToday(DONE_FLAG)) {
                Log.i(TAG, "自动阅读任务今日已执行，跳过");
                return;
            }
            Log.record(TAG + "开始执行自动阅读任务(多本连读模式)");
            String authCode = AuthCodeHelper.getAuthCode(APP_ID);
            String miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(APP_ID, VERSION);
            if (isEmpty(authCode)) {
                Log.record(TAG + "authCode为空，自动阅读任务失败");
                return;
            }
            if (isEmpty(miniMark)) {
                Log.i(TAG, "miniMark为空，继续尝试登录");
            }
            String loginResponse = ReadForestRpcCall.login(authCode, miniMark, VERSION);
            if (isEmpty(loginResponse)) {
                Log.record(TAG + "登录失败: 返回为空");
                return;
            }
            JSONObject loginJo = new JSONObject(loginResponse);
            if (loginJo.optInt("code") != 2000) {
                Log.record(TAG + "登录失败: " + loginJo.optString("msg"));
                return;
            }
            JSONObject loginData = loginJo.optJSONObject("data");
            if (loginData == null) {
                Log.record(TAG + "登录失败: 缺少token");
                return;
            }
            String token = loginData.optString("token");
            if (isEmpty(token)) {
                Log.record(TAG + "登录失败: token为空");
                return;
            }
            JSONObject finishedBooks = loadFinishedBooks(context, userScope());
            Set<String> triedBooks = new HashSet<>();
            while (true) {
                JSONObject freeBook = selectFreeBook(finishedBooks, ReadForestRpcCall.getIndex(token, VERSION), triedBooks);
                if (freeBook == null) {
                    Log.record(TAG + "首页暂无更多未读免费书籍");
                    return;
                }
                String bookId = freeBook.optString("book_id");
                String bookName = freeBook.optString("name", "未知");
                if (isEmpty(bookId)) {
                    triedBooks.add("");
                    TimeUtil.sleep(2000);
                } else {
                    Boolean bookResult = autoReadBook(bookId, bookName, token, context, finishedBooks);
                    if (Boolean.TRUE.equals(bookResult)) {
                        Status.flagToday(DONE_FLAG, UserIdMap.getCurrentUid());
                        Log.record(TAG + "自动阅读任务完成，已获得今日满额能量");
                        return;
                    } else if (Boolean.FALSE.equals(bookResult)) {
                        Log.record(TAG + "检测到当前无法获得阅读能量，终止任务流程");
                        return;
                    } else {
                        triedBooks.add(bookId);
                        TimeUtil.sleep(2000);
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "execute err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /* 从书城响应中挑选一本免费且未读过(也不在本次已尝试列表中)的书 */
    private static JSONObject selectFreeBook(JSONObject finishedBooks, String indexResponse, Set<String> triedBooks) throws Exception {
        JSONArray dataList;
        if (isEmpty(indexResponse) || (dataList = new JSONObject(indexResponse).optJSONArray("data")) == null) {
            return null;
        }
        for (int i = 0; i < dataList.length(); i++) {
            JSONObject dataJo = dataList.optJSONObject(i);
            if (dataJo == null) {
                continue;
            }
            JSONArray blockResourceList = dataJo.optJSONArray("block_resource");
            if (blockResourceList == null) {
                continue;
            }
            for (int j = 0; j < blockResourceList.length(); j++) {
                JSONObject bookJo = blockResourceList.optJSONObject(j);
                if (bookJo == null) {
                    continue;
                }
                String bookId = bookJo.optString("book_id");
                if ("0.00".equals(bookJo.optString("price")) && !triedBooks.contains(bookId)) {
                    if (!finishedBooks.has(bookId)) {
                        return bookJo;
                    }
                    triedBooks.add(bookId);
                }
            }
        }
        return null;
    }

    /* 逐章阅读一本书: 返回TRUE=今日能量已满, FALSE=此书无法再获得能量(或无法继续), null=全书读完自动换下一本 */
    private static Boolean autoReadBook(String bookId, String bookName, String token, Context context, JSONObject finishedBooks) throws Exception {
        JSONObject chapterData = parseData(ReadForestRpcCall.getChapter(bookId, null, token, VERSION));
        if (chapterData == null) {
            return Boolean.FALSE;
        }
        int lastEnergy = -1;
        int noGainCount = 0;
        while (chapterData != null) {
            String chapterId = chapterData.optString("id");
            String nextId = chapterData.optString("next_id");
            String chapterName = chapterData.optString("name", "未知");
            if (isEmpty(chapterId)) {
                return Boolean.FALSE;
            }
            if (lastEnergy == -1) {
                EnergyProgress firstProgress = readEnergyProgress(token, bookId, chapterId);
                if (!firstProgress.hasData) {
                    lastEnergy = 0;
                } else {
                    if (firstProgress.current >= firstProgress.total) {
                        return Boolean.TRUE;
                    }
                    lastEnergy = firstProgress.current;
                }
            }
            ReadForestRpcCall.updateReadingTime(bookId, chapterId, RANDOM.nextInt(668) + 333, token, VERSION);
            ReadForestRpcCall.setReadRecently(bookId, chapterId, chapterName, token, VERSION);
            TimeUtil.sleep(1000);
            EnergyProgress progress = readEnergyProgress(token, bookId, chapterId);
            int gain = progress.current - lastEnergy;
            if (gain > 0) {
                Log.forest(TAG + "[" + bookName + "]进度(" + progress.current + "/"+ progress.total+")获得能量" + gain + "g");
                noGainCount = 0;
            } else {
                Log.record(TAG+"[" + bookName + "]进度同步: " + progress.current + "/" + progress.total);
                noGainCount++;
            }
            if (progress.current >= progress.total) {
                return Boolean.TRUE;
            }
            if (noGainCount >= 5) {
                Log.record(TAG + "[" + bookName + "]连续5章无能量增加，可能达到此书上限，跳过");
                return Boolean.FALSE;
            }
            if (isEmpty(nextId) || "null".equals(nextId) || "0".equals(nextId)) {
                markFinished(context, finishedBooks, bookId, bookName);
                return null;
            }
            TimeUtil.sleep(RANDOM.nextInt(2002) + 1000);
            chapterData = parseData(ReadForestRpcCall.getChapter(bookId, nextId, token, VERSION));
            if (chapterData == null) {
                return Boolean.FALSE;
            }
            lastEnergy = progress.current;
        }
        return Boolean.FALSE;
    }

    private static EnergyProgress readEnergyProgress(String token, String bookId, String chapterId) throws Exception {
        String response = ReadForestRpcCall.checkEnergy(token, VERSION, bookId, chapterId);
        if (isEmpty(response)) {
            return new EnergyProgress(false, 0, TOTAL_FALLBACK);
        }
        JSONObject dataJo = new JSONObject(response).optJSONObject("data");
        if (dataJo == null) {
            return new EnergyProgress(false, 0, TOTAL_FALLBACK);
        }
        return new EnergyProgress(true, dataJo.optInt("current"), dataJo.optInt("total", TOTAL_FALLBACK));
    }

    private static JSONObject parseData(String response) throws Exception {
        if (isEmpty(response)) {
            return null;
        }
        JSONObject jo = new JSONObject(response);
        if (jo.optInt("code") != 2000) {
            return null;
        }
        return jo.optJSONObject("data");
    }

    private static JSONObject loadFinishedBooks(Context context, String userScope) {
        String stored = "{}";
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, 0);
            String read = sharedPreferences.getString(FINISHED_KEY + "_" + userScope, "{}");
            if (read != null) {
                stored = read;
            }
            return new JSONObject(stored);
        } catch (Exception unused) {
            return new JSONObject();
        }
    }

    /* 记入已读书列表(按账号隔离), 后续自动换书时跳过 */
    private static void markFinished(Context context, JSONObject finishedBooks, String bookId, String bookName) {
        try {
            if (finishedBooks.has(bookId)) {
                return;
            }
            finishedBooks.put(bookId, bookName == null ? "未知" : bookName);
            context.getSharedPreferences(PREF_NAME, 0)
                    .edit()
                    .putString(FINISHED_KEY + "_" + userScope(), finishedBooks.toString())
                    .apply();
        } catch (Exception unused) {
        }
    }

    private static String userScope() {
        String uid = UserIdMap.getCurrentUid();
        return isEmpty(uid) ? "default" : uid.replaceAll("[^0-9A-Za-z_-]", "_");
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    private static final class EnergyProgress {
        final int current;
        final boolean hasData;
        final int total;

        EnergyProgress(boolean hasData, int current, int total) {
            this.hasData = hasData;
            this.current = current;
            this.total = total;
        }
    }
}
