package com.lighttodo.app.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.Notifier;

/** 闹钟到点后在这里发通知；也处理通知上的「完成」按钮。 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    /** 到点触发 */
    public static final String ACTION_FIRE = "com.lighttodo.app.action.REMINDER_FIRE";
    /** 通知上点了「完成」 */
    public static final String ACTION_COMPLETE = "com.lighttodo.app.action.REMINDER_COMPLETE";

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_TASK_ID = "task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ACTION_FIRE.equals(action) && !ACTION_COMPLETE.equals(action)) return;

        String groupId = intent.getStringExtra(EXTRA_GROUP_ID);
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        if (taskId == null) return;

        TodoStore store = TodoStore.get(context);
        Group group = groupId == null ? null : store.findGroup(groupId);
        Task task = group == null ? null : group.findTask(taskId);
        if (task == null) {
            Log.w(TAG, "提醒对应的任务已不存在，忽略");
            return;
        }

        if (ACTION_COMPLETE.equals(action)) {
            store.setTaskDone(groupId, taskId, true);
            return;
        }

        // 已经完成或者提醒已经发过了，就不再打扰
        if (task.done || task.remindFired) return;

        Notifier.showReminder(context, task, groupId, group == null ? "" : group.name);
        store.markReminderFired(groupId, taskId);
    }
}
