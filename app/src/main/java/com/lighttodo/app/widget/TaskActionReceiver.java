package com.lighttodo.app.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.lighttodo.app.store.TodoStore;

/** 处理小组件上的「勾选完成 / 取消完成 / 刷新」动作。 */
public class TaskActionReceiver extends BroadcastReceiver {

    public static final String ACTION_TOGGLE_TASK = "com.lighttodo.app.action.TOGGLE_TASK";
    public static final String ACTION_REFRESH_WIDGET = "com.lighttodo.app.action.REFRESH_WIDGET";
    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_TASK_ID = "task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (ACTION_REFRESH_WIDGET.equals(action)) {
            TodoWidgets.refreshAll(context);
            return;
        }

        if (!ACTION_TOGGLE_TASK.equals(action)) return;
        String groupId = intent.getStringExtra(EXTRA_GROUP_ID);
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        if (groupId == null || taskId == null) return;
        // TodoStore 提交时会顺带刷新所有小组件
        TodoStore.get(context).toggleTask(groupId, taskId);
    }
}
