package com.lighttodo.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

/** 统一刷新桌面上的所有小组件。 */
public final class TodoWidgets {

    /** 拿不到尺寸时的兜底值。 */
    private static final int DEFAULT_MIN_HEIGHT = 110;
    private static final int DEFAULT_MIN_WIDTH = 180;

    private TodoWidgets() {
    }

    public static void refreshAll(Context context) {
        refreshTodoList(context);
        refreshGroups(context);
        refreshCategories(context);
        refreshGroupTasks(context);
        refreshCategoryTasks(context);
        // 普通安卓组件版本也要一起刷：两套各自独立地挂在桌面上，
        // 刷新按钮只发一个广播，漏了哪一套哪一套就不更新。
        refreshPlainTodoList(context);
        refreshPlainGroups(context);
        refreshPlainCategories(context);
        refreshPlainGroupTasks(context);
        refreshPlainCategoryTasks(context);
        refreshCustomLists(context);
        refreshPlainCustomLists(context);
    }

    public static void refreshTodoList(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, TodoListWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildTodoList(context,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshGroups(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, GroupWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildGroupOverview(context,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshCategories(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, CategoryWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildCategoryOverview(context,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshGroupTasks(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, GroupTaskWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildGroupTasks(context, id,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshCategoryTasks(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, CategoryTaskWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildCategoryTasks(context, id,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    // ------------------------------------------------------------ 普通安卓组件版本

    public static void refreshPlainTodoList(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, PlainTodoListWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildTodoList(context,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshPlainGroups(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, PlainGroupWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildGroupOverview(context,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshPlainCategories(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, PlainCategoryWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildCategoryOverview(context,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshPlainGroupTasks(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, PlainGroupTaskWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildGroupTasks(context, id,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshPlainCategoryTasks(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, PlainCategoryTaskWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildCategoryTasks(context, id,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    // ------------------------------------------------------------ 自定义清单

    public static void refreshCustomLists(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, CustomListWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildCustomList(context, id,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    public static void refreshPlainCustomLists(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = idsOf(manager, context, PlainCustomListWidgetProvider.class);
        for (int id : ids) {
            manager.updateAppWidget(id, WidgetFactory.buildCustomList(context, id,
                    minHeight(manager, id), minWidth(manager, id)));
        }
    }

    private static int[] idsOf(AppWidgetManager manager, Context context, Class<?> provider) {
        ComponentName name = new ComponentName(context, provider);
        int[] ids = manager.getAppWidgetIds(name);
        return ids == null ? new int[0] : ids;
    }

    private static int minHeight(AppWidgetManager manager, int id) {
        return manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_MIN_HEIGHT);
    }

    private static int minWidth(AppWidgetManager manager, int id) {
        return manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_MIN_WIDTH);
    }
}
