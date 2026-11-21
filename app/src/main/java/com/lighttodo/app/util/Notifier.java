package com.lighttodo.app.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.lighttodo.app.GroupActivity;
import com.lighttodo.app.R;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.reminder.ReminderReceiver;

/** 系统通知：到点提醒 + 通知上的「完成」快捷操作。 */
public final class Notifier {

    public static final String CHANNEL_ID = "reminders";

    private Notifier() {
    }

    /** 建立通知渠道（Android 8.0 以后必须有渠道）。 */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.channel_reminder_desc));
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    /** 通知权限是否已 granted（Android 13 以下默认有）。 */
    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 发出「该完成某件事了」的提醒。
     *
     * @return 这条通知有没有带上超级岛 / 焦点通知参数
     */
    public static boolean showReminder(Context context, Task task, String groupId, String groupName) {
        ensureChannel(context);
        if (!canNotify(context)) return false;

        Intent open = new Intent(context, GroupActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(GroupActivity.EXTRA_GROUP_ID, groupId == null ? "" : groupId);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        StringBuilder text = new StringBuilder();
        if (groupName != null && !groupName.isEmpty()) text.append(groupName);
        if (task.hasDue()) {
            if (text.length() > 0) text.append(" · ");
            text.append("截止 ").append(TimeText.full(task.dueAt));
        }
        if (text.length() == 0) text.append("到点啦");

        Intent complete = new Intent(context, ReminderReceiver.class);
        complete.setAction(ReminderReceiver.ACTION_COMPLETE);
        complete.putExtra(ReminderReceiver.EXTRA_GROUP_ID, groupId == null ? "" : groupId);
        complete.putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id);
        PendingIntent completeIntent = PendingIntent.getBroadcast(context, task.id.hashCode() + 7,
                complete, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notify_title, task.title))
                .setContentText(text.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setWhen(task.remindAt > 0 ? task.remindAt : System.currentTimeMillis())
                .addAction(R.drawable.ic_check_white, context.getString(R.string.action_complete),
                        completeIntent);

        // 小米澎湃系统：能上岛就上岛（上不了就还是普通通知，不影响送达）
        Notification notification = builder.build();
        boolean island = IslandFocus.applyTo(notification, context,
                task.title, text.toString(), groupName, completeIntent);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(task.id.hashCode(), notification);
        }
        return island;
    }
}
