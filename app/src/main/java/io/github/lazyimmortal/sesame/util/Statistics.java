package io.github.lazyimmortal.sesame.util;

import android.content.Context;

import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.File;
import java.util.Calendar;

import io.github.lazyimmortal.sesame.R;
import lombok.Data;

@Data
public class Statistics {
    
    private static final String TAG = Statistics.class.getSimpleName();
    
    public static final Statistics INSTANCE = new Statistics();
    
    private TimeStatistics year = new TimeStatistics();
    private TimeStatistics month = new TimeStatistics();
    private TimeStatistics day = new TimeStatistics();
    
    public static void addData(DataType dt, int i) {
        Statistics stat = INSTANCE;
        switch (dt) {
            case COLLECTED:
                stat.day.collected += i;
                stat.month.collected += i;
                stat.year.collected += i;
                break;
            case HELPED:
                stat.day.helped += i;
                stat.month.helped += i;
                stat.year.helped += i;
                break;
            case WATERED:
                stat.day.watered += i;
                stat.month.watered += i;
                stat.year.watered += i;
                break;
            case WATEREDCOUNT:
                stat.day.wateredcount += i;
                stat.month.wateredcount += i;
                stat.year.wateredcount += i;
                break;
            case WATERINGCOUNT:
                stat.day.wateringcount += i;
                stat.month.wateringcount += i;
                stat.year.wateringcount += i;
                break;
        }
    }
    
    public static int getData(TimeType tt, DataType dt) {
        Statistics stat = INSTANCE;
        int data = 0;
        TimeStatistics ts = null;
        switch (tt) {
            case YEAR:
                ts = stat.year;
                break;
            case MONTH:
                ts = stat.month;
                break;
            case DAY:
                ts = stat.day;
                break;
        }
        if (ts != null) {
            switch (dt) {
                case TIME:
                    data = ts.time;
                    break;
                case COLLECTED:
                    data = ts.collected;
                    break;
                case HELPED:
                    data = ts.helped;
                    break;
                case WATERED:
                    data = ts.watered;
                    break;
                case WATEREDCOUNT:
                    data = ts.wateredcount;
                    break;
                case WATERINGCOUNT:
                    data = ts.wateringcount;
                    break;
            }
        }
        return data;
    }
    
    public static String getText() {
        
        StringBuilder table = new StringBuilder();
        // 添加表头
        table.append("今年  收: ").append(getData(TimeType.YEAR, DataType.COLLECTED)).append(" 帮: ").append(getData(TimeType.YEAR, DataType.HELPED)).append(" 浇: ").append(getData(TimeType.YEAR, DataType.WATERED));
        table.append("\n今月  收: ").append(getData(TimeType.MONTH, DataType.COLLECTED)).append(" 帮: ").append(getData(TimeType.MONTH, DataType.HELPED)).append(" 浇: ").append(getData(TimeType.MONTH, DataType.WATERED));
        table.append("\n今日  收: ").append(getData(TimeType.DAY, DataType.COLLECTED)).append(" 帮: ").append(getData(TimeType.DAY, DataType.HELPED)).append(" 浇: ").append(getData(TimeType.DAY, DataType.WATERED));
        table.append("\n被水次数  日: ").append(getData(TimeType.DAY, DataType.WATEREDCOUNT)).append(" 月: ").append(getData(TimeType.MONTH, DataType.WATEREDCOUNT)).append(" 年: ").append(getData(TimeType.YEAR, DataType.WATEREDCOUNT));
        table.append("\n浇水次数  日: ").append(getData(TimeType.DAY, DataType.WATERINGCOUNT)).append(" 月: ").append(getData(TimeType.MONTH, DataType.WATERINGCOUNT)).append(" 年: ").append(getData(TimeType.YEAR, DataType.WATERINGCOUNT));
        return table.toString();
    }
    
    public static String getText(Context context) {
        return getText(context.getString(R.string.year), context.getString(R.string.month), context.getString(R.string.day), context.getString(R.string.collected), context.getString(R.string.helped), context.getString(R.string.watered), context.getString(R.string.wateredcount),
                context.getString(R.string.wateringcount));
    }
    
    public static String getText(String year, String month, String day, String collected, String helped, String watered, String wateredcount, String wateringcount) {
        return year + "  " + collected + ": " + getData(TimeType.YEAR, DataType.COLLECTED) + " " + helped + ": " + getData(TimeType.YEAR, DataType.HELPED) + " " + watered + ": " + getData(TimeType.YEAR, DataType.WATERED) +
               "\n" + month + "  " + collected + ": " + getData(TimeType.MONTH, DataType.COLLECTED) + " " + helped + ": " + getData(TimeType.MONTH, DataType.HELPED) + " " + watered + ": " + getData(TimeType.MONTH, DataType.WATERED) +
               "\n" + day + "  " + collected + ": " + getData(TimeType.DAY, DataType.COLLECTED) + " " + helped + ": " + getData(TimeType.DAY, DataType.HELPED) + " " + watered + ": " + getData(TimeType.DAY, DataType.WATERED) +
               "\n" + wateredcount + "  " + "日" + ": " + getData(TimeType.DAY, DataType.WATEREDCOUNT) + "; " + "月" + ": " + getData(TimeType.MONTH, DataType.WATEREDCOUNT) + ";  " + "年" + ": " + getData(TimeType.YEAR, DataType.WATEREDCOUNT) +
               "\n" + wateringcount + "  " + "日" + ": " + getData(TimeType.DAY, DataType.WATERINGCOUNT) + ";  " + "月" + ": " + getData(TimeType.MONTH, DataType.WATERINGCOUNT) + ";  " + "年" + ": " + getData(TimeType.YEAR, DataType.WATERINGCOUNT);
    }
    
    public static synchronized Statistics load() {
        try {
            File statisticsFile = FileUtil.getStatisticsFile();
            if (statisticsFile.exists()) {
                String json = FileUtil.readFromFile(statisticsFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = JsonUtil.toFormatJsonString(INSTANCE);
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "重新格式化 statistics.json");
                    Log.system(TAG, "重新格式化 statistics.json");
                    FileUtil.write2File(formatted, statisticsFile);
                }
            }
            else {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
                Log.i(TAG, "初始化 statistics.json");
                Log.system(TAG, "初始化 statistics.json");
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), statisticsFile);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "统计文件格式有误，已重置统计文件");
            Log.system(TAG, "统计文件格式有误，已重置统计文件");
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatisticsFile());
            }
            catch (JsonMappingException e) {
                Log.printStackTrace(TAG, e);
            }
        }
        return INSTANCE;
    }
    
    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
        }
        catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }
    
    public static synchronized void save() {
        save(Calendar.getInstance());
    }
    
    public static synchronized void save(Calendar nowCalendar) {
        if (updateDay(nowCalendar)) {
            Log.system(TAG, "重置 statistics.json");
        }
        else {
            Log.system(TAG, "保存 statistics.json");
        }
        FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatisticsFile());
    }
    
    public static Boolean updateDay(Calendar nowCalendar) {
        int ye = nowCalendar.get(Calendar.YEAR);
        int mo = nowCalendar.get(Calendar.MONTH) + 1;
        int da = nowCalendar.get(Calendar.DAY_OF_MONTH);
        if (ye != INSTANCE.year.time) {
            INSTANCE.year.reset(ye);
            INSTANCE.month.reset(mo);
            INSTANCE.day.reset(da);
        }
        else if (mo != INSTANCE.month.time) {
            INSTANCE.month.reset(mo);
            INSTANCE.day.reset(da);
        }
        else if (da != INSTANCE.day.time) {
            INSTANCE.day.reset(da);
        }
        else {
            return false;
        }
        return true;
    }
    
    public enum TimeType {
        YEAR, MONTH, DAY
    }
    
    public enum DataType {
        TIME, COLLECTED, HELPED, WATERED, WATEREDCOUNT, WATERINGCOUNT
    }
    
    @Data
    public static class TimeStatistics {
        int time;
        int collected, helped, watered, wateredcount, wateringcount;
        
        public TimeStatistics() {
        }
        
        TimeStatistics(int i) {
            reset(i);
        }
        
        public void reset(int i) {
            time = i;
            collected = 0;
            helped = 0;
            watered = 0;
            wateredcount = 0;
            wateringcount = 0;
        }
    }
    
}
