package com.lighttodo.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一个题目分类（比如「高等数学」「线性代数」「C 语言」）。
 *
 * <p>和待办的分组是两套独立的东西：待办管「要做的事」，题目管「攒着要问老师的问题」。</p>
 */
public class QuestionCategory {

    public String id = "";
    public String name = "";
    public long createdAt;

    public QuestionCategory() {
    }

    public QuestionCategory(String id, String name, long createdAt) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.createdAt = createdAt;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("createdAt", createdAt);
        return o;
    }

    public static QuestionCategory fromJson(JSONObject o) {
        if (o == null) return null;
        return new QuestionCategory(
                o.optString("id", ""),
                o.optString("name", ""),
                o.optLong("createdAt", 0L));
    }
}
