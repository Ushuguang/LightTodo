package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.reminder.ReminderReceiver;
import com.lighttodo.app.store.TodoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.List;

/** 截止时间 + 定时提醒的行为验证。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ReminderTest {

    private Context context;
    private TodoStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Android 13+ 默认不给通知权限，测试里先授上，否则通知发不出去
        org.robolectric.shadows.ShadowApplication.getInstance()
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        TodoStore.resetForTest(context);
        store = TodoStore.get(context);
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    private ShadowAlarmManager alarmShadow() {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return shadowOf(manager);
    }

    private boolean hasAlarmAt(long triggerAt) {
        List<ShadowAlarmManager.ScheduledAlarm> alarms = alarmShadow().getScheduledAlarms();
        for (ShadowAlarmManager.ScheduledAlarm alarm : alarms) {
            if (alarm.triggerAtTime == triggerAt) return true;
        }
        return false;
    }

    @Test
    public void 截止时间会保存下来() {
        Group g = store.addGroup("工作");
        long due = System.currentTimeMillis() + 3 * 3600_000L;
        Task t = store.addTask(g.id, "交周报", "", due, 0L);

        Task after = store.findGroup(g.id).findTask(t.id);
        assertEquals(due, after.dueAt);
        assertTrue(after.hasDue());
        assertFalse(after.hasReminder());
    }

    @Test
    public void 设置提醒后会登记闹钟() {
        Group g = store.addGroup("工作");
        long remind = System.currentTimeMillis() + 3600_000L;
        store.addTask(g.id, "写周报", "", 0L, remind);

        assertTrue("应该登记了一个闹钟", hasAlarmAt(remind));
    }

    @Test
    public void 改提醒时间会重新登记闹钟() {
        Group g = store.addGroup("工作");
        long first = System.currentTimeMillis() + 3600_000L;
        long second = System.currentTimeMillis() + 7200_000L;
        Task t = store.addTask(g.id, "写周报", "", 0L, first);

        store.updateTask(g.id, t.id, "写周报", "", -1L, second);

        assertTrue("新时间点应该有闹钟", hasAlarmAt(second));
        assertFalse("旧时间点的闹钟应该被覆盖掉", hasAlarmAt(first));
    }

    @Test
    public void 完成任务会取消提醒() {
        Group g = store.addGroup("工作");
        long remind = System.currentTimeMillis() + 3600_000L;
        Task t = store.addTask(g.id, "写周报", "", 0L, remind);
        assertTrue(hasAlarmAt(remind));

        store.setTaskDone(g.id, t.id, true);

        assertFalse("做完以后不该再提醒", hasAlarmAt(remind));
    }

    @Test
    public void 删除任务会取消提醒() {
        Group g = store.addGroup("工作");
        long remind = System.currentTimeMillis() + 3600_000L;
        Task t = store.addTask(g.id, "写周报", "", 0L, remind);

        store.deleteTask(g.id, t.id);

        assertFalse(hasAlarmAt(remind));
    }

    @Test
    public void 提醒到点会发通知并标记已提醒() {
        Group g = store.addGroup("工作");
        long remind = System.currentTimeMillis() + 3600_000L;
        Task t = store.addTask(g.id, "写周报", "", 0L, remind);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_FIRE);
        intent.putExtra(ReminderReceiver.EXTRA_GROUP_ID, g.id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, t.id);
        new ReminderReceiver().onReceive(context, intent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadow = shadowOf(manager);
        assertEquals("应该发出 1 条通知", 1, shadow.getAllNotifications().size());

        Task after = store.findGroup(g.id).findTask(t.id);
        assertTrue("应该标记为已提醒，避免重复通知", after.remindFired);
    }

    @Test
    public void 已经提醒过的不会再重复通知() {
        Group g = store.addGroup("工作");
        long remind = System.currentTimeMillis() + 3600_000L;
        Task t = store.addTask(g.id, "写周报", "", 0L, remind);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_FIRE);
        intent.putExtra(ReminderReceiver.EXTRA_GROUP_ID, g.id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, t.id);
        ReminderReceiver receiver = new ReminderReceiver();
        receiver.onReceive(context, intent);
        receiver.onReceive(context, intent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadow = shadowOf(manager);
        assertEquals("同样的提醒只发一次", 1, shadow.getAllNotifications().size());
    }

    @Test
    public void 通知上的完成按钮会把任务勾掉() {
        Group g = store.addGroup("工作");
        long remind = System.currentTimeMillis() + 3600_000L;
        Task t = store.addTask(g.id, "写周报", "", 0L, remind);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_COMPLETE);
        intent.putExtra(ReminderReceiver.EXTRA_GROUP_ID, g.id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, t.id);
        new ReminderReceiver().onReceive(context, intent);

        Task after = store.findGroup(g.id).findTask(t.id);
        assertTrue("点了完成就该勾掉", after.done);
    }

    @Test
    public void 逾期判定正确() {
        Group g = store.addGroup("工作");
        long past = System.currentTimeMillis() - 1000L;
        long future = System.currentTimeMillis() + 3600_000L;
        Task overdue = store.addTask(g.id, "过期了", "", past, 0L);
        Task ok = store.addTask(g.id, "没到期", "", future, 0L);

        long now = System.currentTimeMillis();
        assertTrue(overdue.isOverdue(now));
        assertFalse(ok.isOverdue(now));
    }
}
