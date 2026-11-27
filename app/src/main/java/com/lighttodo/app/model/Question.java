package com.lighttodo.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条「我不会的题」。
 *
 * <p>文字随便写，图片可以拍照也可以从相册选，存的是应用私有目录里的文件名
 * （{@code files/photos/xxx.jpg}），所以卸载应用会一起清掉，也不会上传到任何地方。</p>
 */
public class Question {

    public String id = "";
    /** 属于哪个题目分类。 */
    public String categoryId = "";
    /** 题目的简短描述，比如「第 3 题积分变换」。 */
    public String title = "";
    /** 详细文字：题目原文、我的想法、老师上次讲过什么…… */
    public String note = "";
    /** 配图文件名列表（不含目录）。 */
    public List<String> images = new ArrayList<>();
    public long createdAt;
    /** 已经问过老师了就打勾；没问过的排在前面。 */
    public boolean asked;

    public Question() {
    }

    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("categoryId", categoryId);
        o.put("title", title);
        o.put("note", note);
        JSONArray arr = new JSONArray();
        if (images != null) {
            for (String name : images) arr.put(name);
        }
        o.put("images", arr);
        o.put("createdAt", createdAt);
        o.put("asked", asked);
        return o;
    }

    public static Question fromJson(JSONObject o) {
        if (o == null) return null;
        Question q = new Question();
        q.id = o.optString("id", "");
        q.categoryId = o.optString("categoryId", "");
        q.title = o.optString("title", "");
        q.note = o.optString("note", "");
        JSONArray arr = o.optJSONArray("images");
        q.images = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.optString(i, "");
                if (!name.isEmpty()) q.images.add(name);
            }
        }
        q.createdAt = o.optLong("createdAt", 0L);
        q.asked = o.optBoolean("asked", false);
        return q;
    }
}
