package com.lighttodo.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 「自动变分类」链条上的一个时间点。
 *
 * <p>到 {@link #at} 这个时刻，这条任务会自动变成「重要 / 紧急」对应的那个分类。
 * 一条任务可以挂好几个这样的点，按时间先后依次生效：比如
 * 「3 天后变成重要紧急，5 天后变成不重要紧急」，到点时会依次改。</p>
 */
public class AutoCategoryStep {

    /** 到点时间（毫秒时间戳）。 */
    public long at;
    /** 目标分类是否「重要」。 */
    public boolean important;
    /** 目标分类是否「紧急」。 */
    public boolean urgent;

    public AutoCategoryStep() {
    }

    public AutoCategoryStep(long at, boolean important, boolean urgent) {
        this.at = at;
        this.important = important;
        this.urgent = urgent;
    }

    /** 目标分类（四象限里的第几个）。 */
    public int category() {
        if (important && urgent) return Category.IMPORTANT_URGENT;
        if (important) return Category.IMPORTANT_NOT_URGENT;
        if (urgent) return Category.NOT_IMPORTANT_URGENT;
        return Category.NOT_IMPORTANT_NOT_URGENT;
    }

    /** 按四象限分类反推「重要 / 紧急」两个开关。 */
    public static AutoCategoryStep of(long at, int category) {
        boolean important = category == Category.IMPORTANT_URGENT
                || category == Category.IMPORTANT_NOT_URGENT;
        boolean urgent = category == Category.IMPORTANT_URGENT
                || category == Category.NOT_IMPORTANT_URGENT;
        return new AutoCategoryStep(at, important, urgent);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("at", at);
        o.put("important", important);
        o.put("urgent", urgent);
        return o;
    }

    public static AutoCategoryStep fromJson(JSONObject o) {
        if (o == null) return null;
        AutoCategoryStep s = new AutoCategoryStep();
        s.at = o.optLong("at", 0L);
        s.important = o.optBoolean("important", false);
        s.urgent = o.optBoolean("urgent", false);
        return s;
    }
}
