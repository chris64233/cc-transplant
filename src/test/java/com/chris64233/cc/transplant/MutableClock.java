package com.chris64233.cc.transplant;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 测试用可控时钟：测试可显式推进时间以触发邀约过期，同时不触碰
 * 工作流脚本中受限的系统时钟调用。
 */
public class MutableClock extends Clock {

    private Instant current;

    public MutableClock(Instant initial) {
        this.current = initial;
    }

    public void setInstant(Instant instant) {
        this.current = instant;
    }

    public void advanceSeconds(long seconds) {
        this.current = current.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return current;
    }
}
