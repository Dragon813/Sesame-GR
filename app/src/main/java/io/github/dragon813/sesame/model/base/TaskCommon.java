package io.github.dragon813.sesame.model.base;

import io.github.dragon813.sesame.model.normal.base.BaseModel;
import io.github.dragon813.sesame.util.TimeUtil;

public class TaskCommon {

    public static volatile Boolean IS_ENERGY_TIME = false;

    public static volatile Boolean IS_AFTER_6AM = false;

    public static volatile Boolean IS_AFTER_8AM = false;

    public static void update() {
        long currentTimeMillis = System.currentTimeMillis();
        IS_ENERGY_TIME = TimeUtil.checkInTimeRange(currentTimeMillis, BaseModel.getEnergyTime().getValue());
        IS_AFTER_6AM = TimeUtil.isAfterOrCompareTimeStr(currentTimeMillis, "0600");
        IS_AFTER_8AM = TimeUtil.isAfterOrCompareTimeStr(currentTimeMillis, "0800");
    }

}
