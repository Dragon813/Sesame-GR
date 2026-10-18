package io.github.lazyimmortal.sesame.mtop.intervallimit;

import io.github.lazyimmortal.sesame.rpc.intervallimit.DefaultIntervalLimit;
import io.github.lazyimmortal.sesame.rpc.intervallimit.FixedOrRangeIntervalLimit;
import io.github.lazyimmortal.sesame.rpc.intervallimit.IntervalLimit;
import io.github.lazyimmortal.sesame.util.TimeUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTOP 接口限流表。
 * 与 RpcIntervalLimit 结构一致，但独立存储，互不影响。
 */
public class MtopIntervalLimit {

    private static final IntervalLimit DEFAULT_LIMIT = new DefaultIntervalLimit(50);

    private static final Map<String, IntervalLimit> MTOP_LIMIT_MAP = new ConcurrentHashMap<>();

    public static synchronized void addIntervalLimit(String apiName, Integer interval) {
        MTOP_LIMIT_MAP.put(apiName, new DefaultIntervalLimit(interval));
    }

    public static synchronized void addIntervalLimit(String apiName, String fixedOrRange) {
        MTOP_LIMIT_MAP.put(apiName, new FixedOrRangeIntervalLimit(fixedOrRange, 0, Integer.MAX_VALUE));
    }

    public static synchronized void addIntervalLimit(String apiName, IntervalLimit intervalLimit) {
        MTOP_LIMIT_MAP.put(apiName, intervalLimit);
    }

    public static synchronized void updateIntervalLimit(String apiName, Integer interval) {
        MTOP_LIMIT_MAP.put(apiName, new DefaultIntervalLimit(interval));
    }

    public static synchronized void updateIntervalLimit(String apiName, IntervalLimit intervalLimit) {
        MTOP_LIMIT_MAP.put(apiName, intervalLimit);
    }

    public static synchronized void clearIntervalLimit() {
        MTOP_LIMIT_MAP.clear();
    }

    public static void enterIntervalLimit(String apiName) {
        IntervalLimit intervalLimit = MTOP_LIMIT_MAP.get(apiName);
        if (intervalLimit == null) {
            intervalLimit = DEFAULT_LIMIT;
        }
        synchronized (intervalLimit) {
            long sleep = intervalLimit.getInterval() - System.currentTimeMillis() + intervalLimit.getTime();
            if (sleep > 0) {
                TimeUtil.sleep(sleep);
            }
            intervalLimit.setTime(System.currentTimeMillis());
        }
    }
}
