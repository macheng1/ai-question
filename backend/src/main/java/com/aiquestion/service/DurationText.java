package com.aiquestion.service;

/** Shared display format for report and spreadsheet learning durations. */
public final class DurationText {
  private DurationText() {}
  public static String hours(double hours) {
    if (!Double.isFinite(hours) || hours < 0) return "—";
    long seconds = (long) Math.floor(hours * 3600 + 1e-7);
    if (seconds == 0) return hours > 0 ? "不足1秒" : "0秒";
    return (seconds >= 3600 ? seconds / 3600 + "小时" : "")
        + (seconds / 60 % 60 > 0 ? seconds / 60 % 60 + "分" : "")
        + (seconds % 60 > 0 ? seconds % 60 + "秒" : "");
  }

}
