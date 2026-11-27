package com.lighttodo.app.store;

import android.content.Context;
import android.util.Log;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.util.ReminderScheduler;
import com.lighttodo.app.widget.TodoWidgets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 本地存储：全部数据保存在应用私有目录的 todos.json 中，不联网、不上传。
 */
public class TodoStore {

    private static final String TAG = "TodoStore";
    private static final String FILE_NAME = "todos.json";

    private static TodoStore sInstance;

    private final Context appContext;
    private final List<Group> groups = new ArrayList<>();
    /** 全局递增的任务排序号，保证新任务落在分组末尾。 */
    private long orderSeq = 0L;

    public static synchronized TodoStore get(Context context) {
        if (sInstance == null) {
            sInstance = new TodoStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /** 仅供单元测试使用：丢弃缓存实例，重新从磁盘读取。 */
    @androidx.annotation.VisibleForTesting
    public static synchronized void resetForTest(Context context) {
        sInstance = new TodoStore(context.getApplicationContext());
    }

    private TodoStore(Context context) {
        this.appContext = context;
        load();
    }

    // ---------------------------------------------------------------- 读

    public List<Group> groups() {
        return Collections.unmodifiableList(groups);
    }

    public Group findGroup(String groupId) {
        if (groupId == null) return null;
        for (Group g : groups) {
            if (g.id.equals(groupId)) return g;
        }
        return null;
    }

    public int groupCount() {
        return groups.size();
    }

    public int totalPending() {
        int n = 0;
        for (Group g : groups) n += g.pendingCount();
        return n;
    }

    public int totalTasks() {
        int n = 0;
        for (Group g : groups) n += g.tasks.size();
        return n;
    }

    /** 所有分组中未完成的任务，按分组顺序、组内顺序排列。 */
    public List<Task> allPendingTasks() {
        List<Task> out = new ArrayList<>();
        for (Group g : groups) out.addAll(g.pendingTasks());
        return out;
    }

    // ---------------------------------------------------------------- 写

    public Group addGroup(String name) {
        String n = name == null ? "" : name.trim();
        Group g = new Group(UUID.randomUUID().toString(), n, System.currentTimeMillis());
        groups.add(g);
        commit();
        return g;
    }

    public boolean renameGroup(String groupId, String name) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return false;
        g.name = n;
        commit();
        return true;
    }

    public boolean deleteGroup(String groupId) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        for (Task t : g.tasks) {
            ReminderScheduler.cancel(appContext, t.id);
        }
        groups.remove(g);
        commit();
        return true;
    }

    public Task addTask(String groupId, String title, String note) {
        return addTask(groupId, title, note, 0L, 0L);
    }

    /** 新建任务，可带截止时间（dueAt）和提醒时间（remindAt），0 表示不设。 */
    public Task addTask(String groupId, String title, String note, long dueAt, long remindAt) {
        return addTask(groupId, title, note, dueAt, remindAt, false, false);
    }

    /** 新建任务，同时指定四象限分类。 */
    public Task addTask(String groupId, String title, String note, long dueAt, long remindAt,
                         boolean important, boolean urgent) {
        Group g = findGroup(groupId);
        if (g == null) return null;
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) return null;
        Task task = new Task(
                UUID.randomUUID().toString(),
                t,
                note == null ? "" : note.trim(),
                false,
                System.currentTimeMillis(),
                0L,
                ++orderSeq);
        task.dueAt = dueAt;
        task.remindAt = remindAt;
        task.important = important;
        task.urgent = urgent;
        g.tasks.add(task);
        commit();
        ReminderScheduler.schedule(appContext, groupId, task);
        return task;
    }

    public boolean updateTask(String groupId, String taskId, String title, String note) {
        return updateTask(groupId, taskId, title, note, -1L, -1L);
    }

    /** 更新任务。dueAt / remindAt 传 -1 表示不动这一项，0 表示清除。 */
    public boolean updateTask(String groupId, String taskId, String title, String note,
                              long dueAt, long remindAt) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        Task task = g.findTask(taskId);
        if (task == null) return false;
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) return false;
        task.title = t;
        task.note = note == null ? "" : note.trim();
        if (dueAt >= 0) task.dueAt = dueAt;
        if (remindAt >= 0) {
            task.remindAt = remindAt;
            task.remindFired = false;
        }
        commit();
        ReminderScheduler.schedule(appContext, groupId, task);
        return true;
    }

    /**
     * 设置一条任务的「自动变分类」时间点链条（整体覆盖）。
     *
     * @param steps 新的链条，按时间先后排列；传空表示取消自动变分类
     */
    public boolean setAutoSteps(String groupId, String taskId,
                                java.util.List<com.lighttodo.app.model.AutoCategoryStep> steps) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        Task task = g.findTask(taskId);
        if (task == null) return false;
        task.autoSteps = new java.util.ArrayList<>();
        if (steps != null) {
            for (com.lighttodo.app.model.AutoCategoryStep step : steps) {
                if (step != null && step.at > 0) task.autoSteps.add(step);
            }
            java.util.Collections.sort(task.autoSteps,
                    new java.util.Comparator<com.lighttodo.app.model.AutoCategoryStep>() {
                        @Override
                        public int compare(com.lighttodo.app.model.AutoCategoryStep a,
                                           com.lighttodo.app.model.AutoCategoryStep b) {
                            return Long.compare(a.at, b.at);
                        }
                    });
        }
        commit();
        com.lighttodo.app.util.AutoCategoryScheduler.schedule(appContext, groupId, task);
        return true;
    }

    /**
     * 把到点的「自动变分类」执行掉：按时间先后依次应用，最终分类取最后一个到点的目标。
     *
     * <p>返回实际发生变化的步数（0 表示这次没有到点的）。已执行的点会被移除，
     * 还没到的点继续留着。</p>
     */
    public int applyDueAutoSteps(String groupId, String taskId, long now) {
        Group g = findGroup(groupId);
        if (g == null) return 0;
        Task task = g.findTask(taskId);
        if (task == null || task.autoSteps == null || task.autoSteps.isEmpty()) return 0;

        java.util.List<com.lighttodo.app.model.AutoCategoryStep> due = new java.util.ArrayList<>();
        for (com.lighttodo.app.model.AutoCategoryStep step : task.autoSteps) {
            if (step.at <= now) due.add(step);
        }
        if (due.isEmpty()) return 0;
        java.util.Collections.sort(due,
                new java.util.Comparator<com.lighttodo.app.model.AutoCategoryStep>() {
                    @Override
                    public int compare(com.lighttodo.app.model.AutoCategoryStep a,
                                       com.lighttodo.app.model.AutoCategoryStep b) {
                        return Long.compare(a.at, b.at);
                    }
                });

        com.lighttodo.app.model.AutoCategoryStep last = due.get(due.size() - 1);
        task.important = last.important;
        task.urgent = last.urgent;
        task.autoSteps.removeAll(due);
        commit();
        return due.size();
    }

    /** 勾选/取消勾选。勾选后任务只是沉到列表底部，分组本身不会消失。 */
    public boolean setTaskDone(String groupId, String taskId, boolean done) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        Task task = g.findTask(taskId);
        if (task == null) return false;
        task.done = done;
        task.doneAt = done ? System.currentTimeMillis() : 0L;
        commit();
        // 做完了就取消提醒；取消完成后如果提醒时间还在未来，重新登记回来
        if (done) {
            ReminderScheduler.cancel(appContext, taskId);
            // 做完的任务不用再自动变分类了
            com.lighttodo.app.util.AutoCategoryScheduler.cancel(appContext, taskId);
        } else {
            task.remindFired = false;
            ReminderScheduler.schedule(appContext, groupId, task);
            com.lighttodo.app.util.AutoCategoryScheduler.schedule(appContext, groupId, task);
        }
        return true;
    }

    public boolean toggleTask(String groupId, String taskId) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        Task task = g.findTask(taskId);
        if (task == null) return false;
        return setTaskDone(groupId, taskId, !task.done);
    }

    public boolean deleteTask(String groupId, String taskId) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        Task task = g.findTask(taskId);
        if (task == null) return false;
        g.tasks.remove(task);
        commit();
        ReminderScheduler.cancel(appContext, taskId);
        com.lighttodo.app.util.AutoCategoryScheduler.cancel(appContext, taskId);
        return true;
    }

    /** 标记提醒已经通知过，避免重启后重复提醒。 */
    public boolean markReminderFired(String groupId, String taskId) {
        Group g = findGroup(groupId);
        if (g == null) return false;
        Task task = g.findTask(taskId);
        if (task == null) return false;
        task.remindFired = true;
        save();
        return true;
    }

    /** 找出这条任务属于哪个分组。 */
    public Group groupOfTask(Task task) {
        if (task == null) return null;
        for (Group g : groups) {
            if (g.findTask(task.id) != null) return g;
        }
        return null;
    }

    /** 统计四个分类里各有多少条未完成任务。 */
    public int[] categoryPendingCounts() {
        int[] counts = new int[4];
        for (Group g : groups) {
            for (Task t : g.tasks) {
                if (!t.done) counts[com.lighttodo.app.model.Category.of(t)]++;
            }
        }
        return counts;
    }

    /** 落盘并刷新桌面小组件。 */
    public void commit() {
        save();
        TodoWidgets.refreshAll(appContext);
    }

    // ---------------------------------------------------------------- 持久化

    private File dataFile() {
        return new File(appContext.getFilesDir(), FILE_NAME);
    }

    private void load() {
        File f = dataFile();
        if (!f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            String text = new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return;

            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                Group g = Group.fromJson(arr.optJSONObject(i));
                if (g != null && g.id != null && !g.id.isEmpty()) {
                    groups.add(g);
                    for (Task t : g.tasks) {
                        if (t.order > orderSeq) orderSeq = t.order;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "读取本地数据失败，已按空数据启动", e);
        }
    }

    private void save() {
        File f = dataFile();
        File tmp = new File(appContext.getFilesDir(), FILE_NAME + ".tmp");
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) arr.put(g.toJson());
            byte[] data = arr.toString().getBytes(StandardCharsets.UTF_8);

            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.flush();
                out.getFD().sync();
            }
            if (f.exists() && !f.delete()) {
                Log.w(TAG, "旧数据文件删除失败");
            }
            if (!tmp.renameTo(f)) {
                Log.w(TAG, "数据文件重命名失败");
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "保存本地数据失败", e);
        }
    }
}
