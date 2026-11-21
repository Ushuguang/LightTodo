package com.lighttodo.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import com.lighttodo.app.util.MiuiWidget;

/**
 * 小米小部件（MIUI Widget）的公共基类。
 *
 * <p>小米去掉了原生的定时刷新，改成「曝光刷新」：用户滑动到有小组件的页面时，
 * 系统才会广播 {@code miui.appwidget.action.APPWIDGET_UPDATE} 让小组件刷新一次。</p>
 *
 * <p>这里统一把这个广播转成标准的 {@code onUpdate}，各小组件只要实现 {@code onUpdate}
 * 就能同时吃下「系统定时刷新」和「小米曝光刷新」两种触发。</p>
 *
 * <p>另外小米规范要求小组件跑在 {@code :widgetProvider} 独立进程，这个进程里
 * 只能做「准备内容 + 刷新界面」的事，不能去拉起主进程，也不能 fork。</p>
 */
public abstract class MiuiWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && MiuiWidget.ACTION_EXPOSURE_REFRESH.equals(intent.getAction())) {
            int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (ids != null && ids.length > 0) {
                onUpdate(context, AppWidgetManager.getInstance(context), ids);
            }
            return;
        }
        super.onReceive(context, intent);
    }
}
