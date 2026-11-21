package com.lighttodo.app.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.lighttodo.app.util.ReminderScheduler;

/** 开机 / 应用更新 / 系统时间变化后，重新登记提醒并刷新小组件。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            ReminderScheduler.rescheduleAll(context);
            TodoWidgets.refreshAll(context);
        }
    }
}
