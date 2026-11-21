package com.lighttodo.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/** 「分组概览」小组件：列出各分组及其未完成数量。 */
public class GroupWidgetProvider extends MiuiWidgetProvider {

    private static final int DEFAULT_MIN_HEIGHT = 110;
    private static final int DEFAULT_MIN_WIDTH = 180;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, WidgetFactory.buildGroupOverview(context, minHeight(appWidgetManager, id), minWidth(appWidgetManager, id)));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                         int appWidgetId, Bundle newOptions) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetFactory.buildGroupOverview(context,
                minHeight(appWidgetManager, appWidgetId), minWidth(appWidgetManager, appWidgetId)));
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
