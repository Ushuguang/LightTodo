package com.lighttodo.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/**
 * 「自定义清单」小组件（小米小部件版）。
 *
 * <p>加到桌面上时会弹配置页，让用户直接选这个卡片要显示什么：
 * 全部任务、某个分组、还是某个四象限分类。</p>
 */
public class CustomListWidgetProvider extends MiuiWidgetProvider {

    private static final int DEFAULT_MIN_HEIGHT = 110;
    private static final int DEFAULT_MIN_WIDTH = 180;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, WidgetFactory.buildCustomList(context, id,
                    minHeight(appWidgetManager, id), minWidth(appWidgetManager, id)));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                         int appWidgetId, Bundle newOptions) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetFactory.buildCustomList(context,
                appWidgetId, minHeight(appWidgetManager, appWidgetId),
                minWidth(appWidgetManager, appWidgetId)));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            WidgetConfig.clear(context, id);
        }
    }

    private int minHeight(AppWidgetManager manager, int id) {
        return manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_MIN_HEIGHT);
    }

    private int minWidth(AppWidgetManager manager, int id) {
        return manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_MIN_WIDTH);
    }
}
