package com.lighttodo.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** 一条待办任务。 */
public class Task {

    public String id = "";
    public String title = "";
    public String note = "";
    public boolean done;
    public long createdAt;
    public long doneAt;
    /** 同组内的稳定排序号，新建时递增；完成后仍保留原位置依据。 */
    public long order;
    /** 截止时间（毫秒时间戳），0 表示没有设。 */
    public long dueAt;
    /** 提醒时间（毫秒时间戳），0 表示不提醒。到点会发一条系统通知。 */
    public long remindAt;
    /** 这条提醒是否已经通知过了，避免重启后重复提醒。 */
    public boolean remindFired;
    /** 是否重要（配合 urgent 组成四象限分类）。 */
    public boolean important;
    /** 是否紧急。 */
    public boolean urgent;
    /**
     * 「自动变分类」的时间点链条，按时间先后依次生效。
     *
     * <p>为空表示这条任务不会自动改分类。</p>
     */
    public java.util.List<AutoCategoryStep> autoSteps = new java.util.ArrayList<AutoCategoryStep>();

    /** 下一个还没到的时间点（用来挂闹钟）；没有就返回 0。 */
    public long nextAutoAt() {
        long best = 0L;
        for (AutoCategoryStep s : autoSteps) {
            if (s.at > 0 && (best == 0L || s.at < best)) best = s.at;
        }
        return best;
    }

    public Task() {
    }

    public Task(String id, String title, String note, boolean done, long createdAt, long doneAt, long order) {
        this.id = id;
        this.title = title;
        this.note = note == null ? "" : note;
        this.done = done;
        this.createdAt = createdAt;
        this.doneAt = doneAt;
        this.order = order;
    }

    public boolean hasDue() {
        return dueAt > 0;
    }

    public boolean hasReminder() {
        return remindAt > 0;
    }

    /** 是否已经过了截止时间且还没完成。 */
    public boolean isOverdue(long now) {
        return dueAt > 0 && !done && dueAt < now;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("note", note);
        o.put("done", done);
        o.put("createdAt", createdAt);
        o.put("doneAt", doneAt);
        o.put("order", order);
        o.put("dueAt", dueAt);
        o.put("remindAt", remindAt);
        o.put("remindFired", remindFired);
        o.put("important", important);
        o.put("urgent", urgent);
        if (autoSteps != null && !autoSteps.isEmpty()) {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (AutoCategoryStep step : autoSteps) arr.put(step.toJson());
            o.put("autoSteps", arr);
        }
        return o;
    }

    public static Task fromJson(JSONObject o) {
        if (o == null) return null;
        Task t = new Task();
        t.id = o.optString("id", "");
        t.title = o.optString("title", "");
        t.note = o.optString("note", "");
        t.done = o.optBoolean("done", false);
        t.createdAt = o.optLong("createdAt", 0L);
        t.doneAt = o.optLong("doneAt", 0L);
        t.order = o.optLong("order", 0L);
        t.dueAt = o.optLong("dueAt", 0L);
        t.remindAt = o.optLong("remindAt", 0L);
        t.remindFired = o.optBoolean("remindFired", false);
        t.important = o.optBoolean("important", false);
        t.urgent = o.optBoolean("urgent", false);
        org.json.JSONArray arr = o.optJSONArray("autoSteps");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                AutoCategoryStep step = AutoCategoryStep.fromJson(arr.optJSONObject(i));
                if (step != null && step.at > 0) t.autoSteps.add(step);
            }
        }
        return t;
    }
}
