package com.lighttodo.app.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.reminder.AutoCategoryReceiver;
import com.lighttodo.app.store.TodoStore;

import java.util.List;

/**
 * 「自动变分类」的闹钟登记与取消。
 *
 * <p>和 {@link ReminderScheduler} 同一套路：优先用 {@code setAlarmClock}（系统里最可靠的一种，
 * 不受 Doze 省电限制），拿不到精确闹钟权限时退回到 {@code setAndAllowWhileIdle}。</p>
 *
 * <p>一条任务只挂<b>一个</b>闹钟，指向它最近的那个还没到的时间点；到点执行完之后，
 * 如果还有更晚的时间点，会再挂一次。这样「3 天后改 A、5 天后改 B」也能依次生效。</p>
 */
public final class AutoCategoryScheduler {

    private static final String TAG = "AutoCategoryScheduler";

    private AutoCategoryScheduler() {
    }

    /** 给一条任务登记（或者更新）自动变分类的闹钟。 */
    public static void schedule(Context context, String groupId, Task task) {
        if (context == null || task == null) return;
        cancel(context, task.id);
        if (task.done || task.autoSteps == null || task.autoSteps.isEmpty()) return;

        long next = task.nextAutoAt();
        if (next <= 0) return;
        if (next <= System.currentTimeMillis()) {
            // 时间点已经过去了：不在这里改（改由调用方决定），避免在保存时静默改掉分类
            return;
        }

        AlarmManager manager = alarmManager(context);
        if (manager == null) return;
        PendingIntent operation = firePendingIntent(context, groupId, task.id);
        try {
            if (ReminderScheduler.canScheduleExact(manager)) {
                manager.setAlarmClock(
                        new AlarmManager.AlarmClockInfo(next, showPendingIntent(context)),
                        operation);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "没有精确闹钟权限，退回到非精确闹钟", e);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation);
        }
    }

    /** 取消一条任务的自动变分类闹钟。 */
    public static void cancel(Context context, String taskId) {
        if (context == null || taskId == null) return;
        AlarmManager manager = alarmManager(context);
        if (manager == null) return;
        PendingIntent operation = firePendingIntent(context, "", taskId);
        manager.cancel(operation);
        operation.cancel();
    }

    /** 重新登记所有还没到点的自动变分类（开机、应用启动、任务改动后调用）。 */
    public static void rescheduleAll(Context context) {
        if (context == null) return;
        TodoStore store = TodoStore.get(context);
        for (Group group : store.groups()) {
            for (Task task : group.tasks) {
                if (task.autoSteps != null && !task.autoSteps.isEmpty() && !task.done) {
                    schedule(context, group.id, task);
                }
            }
        }
    }

    /**
     * 补做：把到点但还没执行的时间点立刻执行掉。
     *
     * <p>为什么需要：设备关机、应用被杀、系统清理后台之后，闹钟可能没按时触发；
     * 用户下次打开 App 时，这里把错过的时间点补上，不让设置形同虚设。</p>
     *
     * @return 实际改了分类的任务条数
     */
    public static int applyDueSteps(Context context) {
        if (context == null) return 0;
        TodoStore store = TodoStore.get(context);
        long now = System.currentTimeMillis();
        int changed = 0;
        for (Group group : store.groups()) {
            for (Task task : group.tasks) {
                if (task.done || task.autoSteps == null || task.autoSteps.isEmpty()) continue;
                boolean hasDue = false;
                for (com.lighttodo.app.model.AutoCategoryStep step : task.autoSteps) {
                    if (step.at <= now) {
                        hasDue = true;
                        break;
                    }
                }
                if (!hasDue) continue;
                int n = store.applyDueAutoSteps(group.id, task.id, now);
                if (n > 0) {
                    changed++;
                    // 后面还有时间点的话，接着挂下一个闹钟
                    schedule(context, group.id, task);
                    Notifier.showAutoCategory(context, task, group.id, group.name);
                }
            }
        }
        return changed;
    }

    /** 还有多少个时间点排在等着。 */
    public static int pendingStepCount(TodoStore store) {
        if (store == null) return 0;
        int n = 0;
        long now = System.currentTimeMillis();
        for (Group group : store.groups()) {
            for (Task task : group.tasks) {
                if (task.done || task.autoSteps == null) continue;
                for (com.lighttodo.app.model.AutoCategoryStep step : task.autoSteps) {
                    if (step.at > now) n++;
                }
            }
        }
        return n;
    }

    private static AlarmManager alarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent firePendingIntent(Context context, String groupId, String taskId) {
        Intent intent = new Intent(context, AutoCategoryReceiver.class);
        intent.setAction(AutoCategoryReceiver.ACTION_FIRE);
        intent.putExtra(AutoCategoryReceiver.EXTRA_GROUP_ID, groupId == null ? "" : groupId);
        intent.putExtra(AutoCategoryReceiver.EXTRA_TASK_ID, taskId);
        // 同一个任务始终复用同一个 PendingIntent，重新登记时才能覆盖掉旧的
        return PendingIntent.getBroadcast(context, taskId.hashCode() + 31, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showPendingIntent(Context context) {
        Intent intent = new Intent(context, com.lighttodo.app.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
