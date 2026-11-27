package com.lighttodo.app.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.AutoCategoryScheduler;
import com.lighttodo.app.util.Notifier;

/**
 * 「自动变分类」闹钟到点后在这里执行：把任务改成目标分类，
 * 如果后面还有时间点，再挂下一个闹钟；同时发一条通知告诉用户改了。
 */
public class AutoCategoryReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoCategoryReceiver";

    public static final String ACTION_FIRE = "com.lighttodo.app.action.AUTO_CATEGORY_FIRE";

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_TASK_ID = "task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_FIRE.equals(intent.getAction())) return;

        String groupId = intent.getStringExtra(EXTRA_GROUP_ID);
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        if (taskId == null) return;

        TodoStore store = TodoStore.get(context);
        Group group = groupId == null ? null : store.findGroup(groupId);
        Task task = group == null ? null : group.findTask(taskId);
        if (task == null) {
            Log.w(TAG, "自动变分类对应的任务已不存在，忽略");
            return;
        }
        // 已经做完的任务不用再改分类了
        if (task.done) {
            AutoCategoryScheduler.cancel(context, taskId);
            return;
        }

        int n = store.applyDueAutoSteps(groupId, taskId, System.currentTimeMillis());
        if (n <= 0) return;

        // 后面还有时间点的话，接着挂下一个闹钟
        AutoCategoryScheduler.schedule(context, groupId, task);
        Notifier.showAutoCategory(context, task, groupId, group == null ? "" : group.name);
    }
}
