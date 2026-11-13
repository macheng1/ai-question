package com.aiquestion.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Business schedules use Beijing wall-clock time, independent of the host/JVM default zone. */
public final class BusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
