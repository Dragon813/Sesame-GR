package io.github.lazyimmortal.sesame.entity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Constanline
 * @since 2023/08/08
 */
public class FriendWatch extends IdAndName {

    private static final String TAG = FriendWatch.class.getSimpleName();

    private String startTime;

    private int allGet;

    private int weekGet;

    private int dayGet;

    private int monthGet;

    public String headImageUrl;

    public String firstAlphaChar;

    public String bodyText;

    public boolean delete;

    public FriendWatch(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public int compareTo(IdAndName o) {
        FriendWatch another = (FriendWatch) o;
        if (this.weekGet > another.weekGet) {
            return -1;
        } else if (this.weekGet < another.weekGet) {
            return 1;
        }
        return super.compareTo(o);
    }

    /**
     * 解析指定账号的统计文件; 仅按账号分目录读取, 不做旧全局文件迁移
     */
    private static JSONObject readJo(String userId) {
        String body = FileUtil.readFromFile(FileUtil.getFriendWatchFile(userId));
        try {
            if (StringUtil.isEmpty(body)) {
                return new JSONObject();
            }
            return new JSONObject(body);
        } catch (JSONException e) {
            Log.printStackTrace(e);
            return new JSONObject();
        }
    }

    private static void writeJo(String userId, JSONObject jo) {
        FileUtil.write2File(jo.toString(), FileUtil.getFriendWatchFile(userId));
    }

    /**
     * 累计好友统计; name由调用方按来源算好传入(好友=UserIdMap昵称, PK森友=displayName(PK森友)), 为空时回退好友表查询
     */
    public static void friendWatch(String id, int collectedEnergy, String name) {
        try {
            String userId = UserIdMap.getCurrentUid();
            if (StringUtil.isEmpty(userId)) {
                Log.i(TAG, "friendWatch skip: currentUid为空");
                return;
            }
            JSONObject joFriendWatch = readJo(userId);
            //调用方未传名字时回退好友表查询(好友表未加载或已删好友时getMaskName返回null, getShowName兜底返回ID)
            if (StringUtil.isEmpty(name)) {
                name = UserIdMap.getMaskName(id);
                if (StringUtil.isEmpty(name)) {
                    name = UserIdMap.getShowName(id);
                }
            }
            JSONObject joSingle = joFriendWatch.optJSONObject(id);
            if (joSingle == null) {
                joSingle = new JSONObject();
                joSingle.put("name", name);
                joSingle.put("allGet", 0);
                joSingle.put("startTime", TimeUtil.getDateStr());
            } else if (StringUtil.isEmpty(joSingle.optString("name"))) {
                //条目已存在但名字缺失(历史null写入), 补名
                joSingle.put("name", name);
            }
            joSingle.put("dayGet", joSingle.optInt("dayGet", 0) + collectedEnergy);
            joSingle.put("weekGet", joSingle.optInt("weekGet", 0) + collectedEnergy);
            joSingle.put("monthGet", joSingle.optInt("monthGet", 0) + collectedEnergy);
            joSingle.put("allGet", joSingle.optInt("allGet", 0) + collectedEnergy);
            joFriendWatch.put(id, joSingle);
            writeJo(userId, joFriendWatch);
        } catch (Throwable th) {
            Log.i(TAG, "friendWatch err:");
            Log.printStackTrace(TAG, th);
        }
    }

    /**
     * 累计被水/浇水次数; watered=true记录好友浇我(被水), false记录我浇好友(浇水)
     */
    public static void waterFriendWatch(String id, boolean watered) {
        try {
            String userId = UserIdMap.getCurrentUid();
            if (StringUtil.isEmpty(userId)) {
                Log.i(TAG, "waterFriendWatch skip: currentUid为空");
                return;
            }
            JSONObject joFriendWatch = readJo(userId);
            JSONObject joSingle = joFriendWatch.optJSONObject(id);
            if (joSingle == null) {
                //未偷过该好友能量也可能有浇水往来, 新建条目
                joSingle = new JSONObject();
                joSingle.put("name", resolveName(id));
                joSingle.put("allGet", 0);
                joSingle.put("startTime", TimeUtil.getDateStr());
            }
            String field = watered ? "wateredCount" : "wateringCount";
            joSingle.put(field, joSingle.optInt(field, 0) + 1);
            joFriendWatch.put(id, joSingle);
            writeJo(userId, joFriendWatch);
        } catch (Throwable th) {
            Log.i(TAG, "waterFriendWatch err:");
            Log.printStackTrace(TAG, th);
        }
    }

    public static synchronized void save() {
        //数据在friendWatch中已实时落盘, 此处无需再写
    }

    /* 从能量榜响应全量写入好友ta的总能量: 已有条目更新, 未统计的新建条目并尽量回填用户名, 返回写入条数 */
    public static int updateEnergySummation(JSONObject friendsObject) {
        int count = 0;
        try {
            String userId = UserIdMap.getCurrentUid();
            JSONObject joFriendWatch = readJo(userId);
            boolean changed = false;
            String[] rankingKeys = {"friendRanking", "totalDatas"};
            for (String key : rankingKeys) {
                JSONArray ranking = friendsObject.optJSONArray(key);
                if (ranking == null) {
                    continue;
                }
                for (int i = 0; i < ranking.length(); i++) {
                    JSONObject item = ranking.optJSONObject(i);
                    if (item == null || !item.has("energySummation")) {
                        continue;
                    }
                    String fid = item.optString("userId", "");
                    if (fid.isEmpty()) {
                        continue;
                    }
                    JSONObject friend = joFriendWatch.optJSONObject(fid);
                    if (friend == null) {
                        //新条目: 能量各口径从0起步, 名字查好友表回填(查不到兜底ID)
                        friend = new JSONObject();
                        friend.put("name", resolveName(fid));
                        friend.put("startTime", TimeUtil.getDateStr());
                        friend.put("dayGet", 0);
                        friend.put("weekGet", 0);
                        friend.put("monthGet", 0);
                        friend.put("allGet", 0);
                        joFriendWatch.put(fid, friend);
                    } else if (StringUtil.isEmpty(friend.optString("name"))) {
                        //历史无名条目顺带补名
                        friend.put("name", resolveName(fid));
                    }
                    friend.put("totalEnergy", item.optInt("energySummation"));
                    changed = true;
                    count++;
                }
            }
            if (changed) {
                writeJo(userId, joFriendWatch);
            }
        } catch (Throwable t) {
            Log.i(TAG, "updateEnergySummation err:");
            Log.printStackTrace(TAG, t);
        }
        return count;
    }

    private static String resolveName(String id) {
        String name = UserIdMap.getMaskName(id);
        if (StringUtil.isEmpty(name)) {
            name = UserIdMap.getShowName(id);
        }
        return name;
    }

    public static void updateDay() {
        String userId = UserIdMap.getCurrentUid();
        if (StringUtil.isEmpty(userId)) {
            return;
        }
        long last = FileUtil.getFriendWatchFile(userId).lastModified();
        Calendar cNow = Calendar.getInstance();
        boolean newDay;
        boolean newMonth;
        if (last == 0L) {
            newDay = true;
            newMonth = true;
        } else {
            Calendar cLast = Calendar.getInstance();
            cLast.setTimeInMillis(last);
            newDay = cLast.get(Calendar.DAY_OF_YEAR) != cNow.get(Calendar.DAY_OF_YEAR)
                    || cLast.get(Calendar.YEAR) != cNow.get(Calendar.YEAR);
            newMonth = cLast.get(Calendar.MONTH) != cNow.get(Calendar.MONTH)
                    || cLast.get(Calendar.YEAR) != cNow.get(Calendar.YEAR);
        }
        if (!newDay) {
            return;
        }
        //周口径沿用原触发规则: 仅周一清零; 总收在收取时已直接累加, 此处不再滚入
        boolean newWeek = cNow.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
        try {
            JSONObject joFriendWatch = readJo(userId);
            String dateStr = TimeUtil.getDateStr();
            Iterator<String> ids = joFriendWatch.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                JSONObject joSingle = joFriendWatch.getJSONObject(id);
                joSingle.put("dayGet", 0);
                if (newWeek) {
                    joSingle.put("weekGet", 0);
                }
                if (newMonth) {
                    joSingle.put("monthGet", 0);
                }
                if (!joSingle.has("startTime")) {
                    joSingle.put("startTime", dateStr);
                }
                joFriendWatch.put(id, joSingle);
            }
            writeJo(userId, joFriendWatch);
        } catch (Throwable th) {
            Log.i(TAG, "friendWatchNewWeek err:");
            Log.printStackTrace(TAG, th);
        }
    }

    public static synchronized Boolean load() {
        //数据已改为按账号实时读写文件, 无需内存预载
        return true;
    }

    public static synchronized void unload() {
        //数据已改为按账号实时读写文件, 无内存态需清理
    }

    /**
     * 读取指定账号的好友统计列表; userId为空返回空列表
     */
    public static List<FriendWatch> getList(String userId) {
        ArrayList<FriendWatch> list = new ArrayList<>();
        if (StringUtil.isEmpty(userId)) {
            return list;
        }
        try {
            JSONObject joFriendWatch = readJo(userId);
            //加载该账号好友表用于昵称回填(不影响其他账号, load为按userId读文件)
            UserIdMap.load(userId);
            Iterator<String> ids = joFriendWatch.keys();
            boolean needSave = false;
            while (ids.hasNext()) {
                String id = ids.next();
                JSONObject friend = joFriendWatch.optJSONObject(id);
                if (friend == null) {
                    friend = new JSONObject();
                }
                String name = friend.optString("name");
                if (StringUtil.isEmpty(name)) {
                    //历史条目名字缺失, 查该账号好友表回填并落盘; 查不到(已删好友)兜底显示ID
                    name = UserIdMap.getMaskName(id);
                    if (StringUtil.isEmpty(name)) {
                        name = UserIdMap.getShowName(id);
                    }
                    try {
                        friend.put("name", name);
                        needSave = true;
                    } catch (Throwable ignored) {
                    }
                }
                FriendWatch friendWatch = new FriendWatch(id, name);
                friendWatch.startTime = friend.optString("startTime", "无");
                friendWatch.dayGet = friend.optInt("dayGet", 0);
                friendWatch.weekGet = friend.optInt("weekGet", 0);
                friendWatch.monthGet = friend.optInt("monthGet", 0);
                friendWatch.allGet = friend.optInt("allGet", 0);
                //name保持纯用户名(头像右侧第一行+搜索用), 统计内容放bodyText
                String showText = "开始统计时间:" + friendWatch.startTime
                        + "\n日收:" + friendWatch.dayGet + " 周收:" + friendWatch.weekGet
                        + "\n月收:" + friendWatch.monthGet + " 总收:" + friendWatch.allGet;
                //被水次数(wateredCount)/浇水次数(wateringCount), 与Statistics的WATEREDCOUNT/WATERINGCOUNT同口径
                showText = showText + "\n被水次数:" + friend.optInt("wateredCount", 0)
                        + " 浇水次数:" + friend.optInt("wateringCount", 0);
                if (friend.has("totalEnergy")) {
                    //ta的总能量: 每日一次从能量榜更新, 单独一行显示
                    showText = showText + "\nta的总能量:" + friend.optInt("totalEnergy");
                }
                UserEntity userEntity = UserIdMap.get(id);
                if (userEntity != null) {
                    //更改时间(extVersion)/成为好友时间(version)为毫秒时间戳, 单删(isDelete)为true时标记
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                    if (userEntity.getRegisterTime() != null && userEntity.getRegisterTime() > 0) {
                        showText = showText + "\n更改时间:" + sdf.format(new java.util.Date(userEntity.getRegisterTime()));
                    }
                    if (userEntity.getFriendTime() != null && userEntity.getFriendTime() > 0) {
                        showText = showText + " 成为好友:" + sdf.format(new java.util.Date(userEntity.getFriendTime()));
                    }
                    if (Boolean.TRUE.equals(userEntity.getIsDelete())) {
                        friendWatch.delete = true;
                    }
                    friendWatch.headImageUrl = userEntity.getHeadImageUrl();
                    friendWatch.firstAlphaChar = userEntity.getFirstAlphaChar();
                }
                //条目正文上下各空一行, 分割线两侧留空
                friendWatch.bodyText = "\n" + showText + "\n";
                joFriendWatch.put(id, friend);
                list.add(friendWatch);
            }
            if (needSave) {
                //补名后的数据回写文件, 下次直接读取
                writeJo(userId, joFriendWatch);
            }
            //按总收>月收>周收>日收降序排列
            Collections.sort(list, (o1, o2) -> {
                if (o1.allGet != o2.allGet) {
                    return o2.allGet - o1.allGet;
                }
                if (o1.monthGet != o2.monthGet) {
                    return o2.monthGet - o1.monthGet;
                }
                if (o1.weekGet != o2.weekGet) {
                    return o2.weekGet - o1.weekGet;
                }
                return o2.dayGet - o1.dayGet;
            });
        } catch (Throwable t) {
            Log.i(TAG, "FriendWatch getList: ");
            Log.printStackTrace(TAG, t);
        }
        return list;
    }
}