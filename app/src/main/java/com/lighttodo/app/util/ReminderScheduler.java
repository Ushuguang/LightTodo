package com.lighttodo.app.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;

/**
 * 提醒闹钟的注册与取消。
 *
 * <p>优先用 {@code setAlarmClock}，它是系统里最可靠的一种闹钟：不受 Doze 省电模式限制，
 * 设备息屏也会准时触发（国产 ROM 的后台清理对它的影响最小）。拿不到精确闹钟权限时
 * 退回到 {@code setAndAllowWhileIdle}，保证「可能晚一点，但不会完全不响」。</p>
 */
public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    private ReminderScheduler() {
    }

    /** 给一条任务登记提醒。 */
    public static void schedule(Context context, String groupId, Task task) {
        if (task == null || !task.hasReminder()) return;
        if (task.done) {
            cancel(context, task.id);
            return;
        }
        if (task.remindAt <= System.currentTimeMillis()) {
            // 已经过去的时间点不登记，等于不提醒
            cancel(context, task.id);
            return;
        }
        AlarmManager manager = alarmManager(context);
        if (manager == null) return;

        PendingIntent operation = firePendingIntent(context, groupId, task.id);
        try {
            if (canScheduleExact(manager)) {
                manager.setAlarmClock(
                        new AlarmManager.AlarmClockInfo(task.remindAt, showPendingIntent(context)),
                        operation);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.remindAt, operation);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "没有精确闹钟权限，退回到非精确闹钟", e);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.remindAt, operation);
        }
    }

    /** 取消一条任务的提醒。 */
    public static void cancel(Context context, String taskId) {
        AlarmManager manager = alarmManager(context);
        if (manager == null) return;
        PendingIntent operation = firePendingIntent(context, "", taskId);
        manager.cancel(operation);
        operation.cancel();
    }

    /** 重新登记所有还没触发过的提醒（开机、应用启动后调用）。 */
    public static void rescheduleAll(Context context) {
        TodoStore store = TodoStore.get(context);
        for (Group group : store.groups()) {
            for (Task task : group.tasks) {
                if (task.hasReminder() && !task.done && !task.remindFired
                        && task.remindAt > System.currentTimeMillis()) {
                    schedule(context, group.id, task);
                }
            }
        }
    }

    /** 还有多少条提醒在等着触发。 */
    public static int pendingReminderCount(TodoStore store) {
        int n = 0;
        long now = System.currentTimeMillis();
        for (Group group : store.groups()) {
            for (Task task : group.tasks) {
                if (task.hasReminder() && !task.done && !task.remindFired && task.remindAt > now) {
                    n++;
                }
            }
        }
        return n;
    }

    public static boolean canScheduleExact(AlarmManager manager) {
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return manager.canScheduleExactAlarms();
        }
        return true;
    }

    public static boolean canScheduleExact(Context context) {
        return canScheduleExact(alarmManager(context));
    }

    private static AlarmManager alarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent firePendingIntent(Context context, String groupId, String taskId) {
        Intent intent = new Intent(context, com.lighttodo.app.reminder.ReminderReceiver.class);
        intent.setAction(com.lighttodo.app.reminder.ReminderReceiver.ACTION_FIRE);
        intent.putExtra(com.lighttodo.app.reminder.ReminderReceiver.EXTRA_GROUP_ID, groupId);
        intent.putExtra(com.lighttodo.app.reminder.ReminderReceiver.EXTRA_TASK_ID, taskId);
        // 同一个任务始终复用同一个 PendingIntent，重新登记时才能覆盖掉旧的
        return PendingIntent.getBroadcast(context, taskId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showPendingIntent(Context context) {
        Intent intent = new Intent(context, com.lighttodo.app.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
