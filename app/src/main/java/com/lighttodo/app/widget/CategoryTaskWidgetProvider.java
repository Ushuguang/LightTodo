package com.lighttodo.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/** 「某个类的任务」小组件：加到桌面时选一个象限，可以叠多个、各看各的。 */
public class CategoryTaskWidgetProvider extends MiuiWidgetProvider {

    private static final int DEFAULT_MIN_HEIGHT = 110;
    private static final int DEFAULT_MIN_WIDTH = 180;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, WidgetFactory.buildCategoryTasks(
                    context, id, minHeight(appWidgetManager, id), minWidth(appWidgetManager, id)));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                         int appWidgetId, Bundle newOptions) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetFactory.buildCategoryTasks(
                context, appWidgetId, minHeight(appWidgetManager, appWidgetId),
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
