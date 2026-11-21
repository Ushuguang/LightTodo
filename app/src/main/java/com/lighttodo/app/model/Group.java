package com.lighttodo.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 一个任务分组。分组不会因为任务全部完成而消失。 */
public class Group {

    public String id = "";
    public String name = "";
    public long createdAt;
    public final List<Task> tasks = new ArrayList<>();

    public Group() {
    }

    public Group(String id, String name, long createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    /** 未完成的任务（保持排序）。 */
    public List<Task> pendingTasks() {
        List<Task> out = new ArrayList<>();
        for (Task t : sortedTasks()) {
            if (!t.done) out.add(t);
        }
        return out;
    }

    public List<Task> doneTasks() {
        List<Task> out = new ArrayList<>();
        for (Task t : sortedTasks()) {
            if (t.done) out.add(t);
        }
        return out;
    }

    /** 未完成任务在前，已完成任务沉底；同状态内按 order 升序。 */
    public List<Task> sortedTasks() {
        List<Task> copy = new ArrayList<>(tasks);
        Collections.sort(copy, new Comparator<Task>() {
            @Override
            public int compare(Task a, Task b) {
                if (a.done != b.done) return a.done ? 1 : -1;
                int c = Long.compare(a.order, b.order);
                if (c != 0) return c;
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        return copy;
    }

    public Task findTask(String taskId) {
        for (Task t : tasks) {
            if (t.id.equals(taskId)) return t;
        }
        return null;
    }

    public int pendingCount() {
        int n = 0;
        for (Task t : tasks) {
            if (!t.done) n++;
        }
        return n;
    }

    public int doneCount() {
        return tasks.size() - pendingCount();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("createdAt", createdAt);
        JSONArray arr = new JSONArray();
        for (Task t : tasks) arr.put(t.toJson());
        o.put("tasks", arr);
        return o;
    }

    public static Group fromJson(JSONObject o) {
        if (o == null) return null;
        Group g = new Group(o.optString("id", ""), o.optString("name", ""), o.optLong("createdAt", 0L));
        JSONArray arr = o.optJSONArray("tasks");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Task t = Task.fromJson(arr.optJSONObject(i));
                if (t != null && t.id != null && !t.id.isEmpty()) g.tasks.add(t);
            }
        }
        return g;
    }
}
