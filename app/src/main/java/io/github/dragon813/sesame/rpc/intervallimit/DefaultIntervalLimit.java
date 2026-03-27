package io.github.dragon813.sesame.rpc.intervallimit;

import lombok.Data;

@Data
public class DefaultIntervalLimit implements IntervalLimit {

    private final Integer interval;

    private Long time = 0L;

    public DefaultIntervalLimit(Integer interval) {
        this.interval = interval;
    }

}