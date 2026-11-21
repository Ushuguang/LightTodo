package com.lighttodo.app.util;

import java.util.Calendar;
import java.util.Locale;

/** 把时间戳转成「今天 18:00」「明天 09:00」「3月20日 周三 09:00」这种中文文本。 */
public final class TimeText {

    private TimeText() {
    }

    public static boolean isToday(long millis) {
        if (millis <= 0) return false;
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(millis);
        b.setTimeInMillis(System.currentTimeMillis());
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isTomorrow(long millis) {
        if (millis <= 0) return false;
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(millis);
        b.add(Calendar.DAY_OF_YEAR, 1);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isYesterday(long millis) {
        if (millis <= 0) return false;
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(millis);
        b.add(Calendar.DAY_OF_YEAR, -1);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** 完整的日期时间文本。 */
    public static String full(long millis) {
        if (millis <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        int h = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        String week = week(c.get(Calendar.DAY_OF_WEEK));
        return m + "月" + d + "日 " + week + " " + pad(h) + ":" + pad(min);
    }

    /** 相对口语化的日期时间文本：今天 / 明天 / 昨天 / 3月20日。 */
    public static String friendly(long millis) {
        if (millis <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int h = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        String time = pad(h) + ":" + pad(min);
        if (isToday(millis)) return "今天 " + time;
        if (isTomorrow(millis)) return "明天 " + time;
        if (isYesterday(millis)) return "昨天 " + time;
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        return m + "月" + d + "日 " + time;
    }

    /** 只取日期部分，用于日期选择器回填。 */
    public static int[] dateParts(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
    }

    public static int[] timeParts(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
    }

    private static String week(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return "周一";
            case Calendar.TUESDAY:
                return "周二";
            case Calendar.WEDNESDAY:
                return "周三";
            case Calendar.THURSDAY:
                return "周四";
            case Calendar.FRIDAY:
                return "周五";
            case Calendar.SATURDAY:
                return "周六";
            default:
                return "周日";
        }
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
