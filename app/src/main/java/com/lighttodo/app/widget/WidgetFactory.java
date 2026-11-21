package com.lighttodo.app.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.lighttodo.app.GroupActivity;
import com.lighttodo.app.MainActivity;
import com.lighttodo.app.R;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.TimeText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 负责把数据渲染成 RemoteViews。
 *
 * <p>配色全部走资源（values / values-night），由桌面进程按系统深浅色解析，
 * 保证文字和背景始终一致，不会出现浅字压白底的情况。</p>
 *
 * <p>小组件一共有五种：待办清单、分组概览、分类概览、某个组的任务、某个类的任务。
 * 后两种带配置页，每个实例各选各的，可以叠好几个。</p>
 */
public final class WidgetFactory {

    /** 独占整行的一条任务占多高（dp）：上下各 6dp 内边距 + 约 16dp 文字 + 余量。 */
    private static final int FULL_ROW_HEIGHT_DP = 32;
    /** 并排一行的两条任务占多高（dp）：上面标题、下面分组，两行文字。 */
    private static final int PAIR_ROW_HEIGHT_DP = 44;
    /**
     * 除任务列表以外的固定高度（dp）：上下内边距 + 标题栏 + 分隔线 + 底部「还有 N 项」。
     * 实测约 74dp，这里留 4dp 余量，保证最后一行不会被裁掉。
     */
    private static final int CHROME_HEIGHT_DP = 78;
    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 12;

    /** 两栏布局需要的最小宽度（dp）：再窄的话任务标题就挤成一条了。 */
    public static final int TWO_COLUMN_MIN_WIDTH_DP = 240;
    /** 任务标题字号（sp），要和 widget_item.xml / widget_row_pair.xml 里的 13sp 对齐。 */
    private static final float TASK_TITLE_SP = 13f;
    /** 小组件左右内边距（dp），widget_task_list.xml 里是 12dp × 2。 */
    private static final float WIDGET_PADDING_DP = 24f;
    /** 并排两栏之间的间距（dp）。 */
    private static final float COLUMN_GAP_DP = 10f;
    /** 一栏里除标题外还要占的地方：勾选圈 14 + 间距 6 + 色点 8 + 间距 5。 */
    private static final float ROW_CHROME_DP = 33f;
    /** 字体渲染误差的余量（dp）。 */
    private static final float FIT_SAFETY_DP = 6f;

    /** 拿不到尺寸时的兜底值。 */
    private static final int DEFAULT_MIN_WIDTH = 180;

    private WidgetFactory() {
    }

    /** 根据小组件高度估算能放下的整行任务条数。 */
    public static int rowsForHeight(int minHeightDp) {
        int rows = (minHeightDp - CHROME_HEIGHT_DP) / FULL_ROW_HEIGHT_DP;
        if (rows < MIN_ROWS) rows = MIN_ROWS;
        if (rows > MAX_ROWS) rows = MAX_ROWS;
        return rows;
    }

    /**
     * 这一条任务能不能塞进半栏（并排两栏里的其中一栏）。
     *
     * <p>用真实字体量一遍标题宽度：放得下才并排放，放不下（会出省略号）就独占整行。
     * 所以同一个小组件里，长任务整行、短任务两两并排，是混着排的。</p>
     */
    public static boolean fitsInColumn(Context context, String title, int minWidthDp) {
        if (title == null || title.isEmpty()) return true;
        if (minWidthDp < TWO_COLUMN_MIN_WIDTH_DP) return false;
        if (context == null) return false;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(TASK_TITLE_SP * dm.scaledDensity);
        // 字体量出来的宽度可能偏小（字体不同、渲染环境不同），再按字数估一个下限，取大的那个，
        // 保证判断偏保守：宁可整行显示，也不要出现省略号。
        float measured = paint.measureText(title);
        float estimated = estimateEm(title) * TASK_TITLE_SP * dm.scaledDensity;
        float titlePx = Math.max(measured, estimated);
        float columnDp = (minWidthDp - WIDGET_PADDING_DP - COLUMN_GAP_DP) / 2f;
        float availableDp = columnDp - ROW_CHROME_DP - FIT_SAFETY_DP;
        return titlePx <= availableDp * dm.density;
    }

    /** 粗略算一下标题占多少个「字宽」：中文按 1 个字宽，英文数字按 0.55 个。 */
    private static float estimateEm(String text) {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x2E80 && c <= 0x9FFF) {
                em += 1f;            // 中文汉字 + 中文标点
            } else if (c >= 0xFF00 && c <= 0xFF60) {
                em += 1f;            // 全角标点
            } else {
                em += 0.55f;         // 英文 / 数字 / 空格
            }
        }
        return em;
    }

    // ------------------------------------------------------------ 任务列表

    /** 小组件里的一行任务。 */
    public static class TaskRow {
        public final String title;
        public final String sub;
        public final String groupId;
        public final String taskId;
        public final int category;

        public TaskRow(String title, String sub, String groupId, String taskId, int category) {
            this.title = title == null ? "" : title;
            this.sub = sub == null ? "" : sub;
            this.groupId = groupId == null ? "" : groupId;
            this.taskId = taskId == null ? "" : taskId;
            this.category = category;
        }
    }

    /** 通用任务列表小组件：一栏或两栏。 */
    public static RemoteViews buildTaskList(Context context, int minHeightDp, int minWidthDp,
                                            String title, String countText, List<TaskRow> rows,
                                            int totalCount, boolean showAdd,
                                            PendingIntent addIntent, PendingIntent headerIntent,
                                            PendingIntent moreIntent, String emptyText) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task_list);
        views.setTextViewText(R.id.w_title, title);
        views.setTextViewText(R.id.w_count, countText);
        if (headerIntent != null) views.setOnClickPendingIntent(R.id.w_header, headerIntent);
        views.setOnClickPendingIntent(R.id.w_refresh, refreshWidget(context));

        if (showAdd && addIntent != null) {
            views.setViewVisibility(R.id.w_add, View.VISIBLE);
            views.setOnClickPendingIntent(R.id.w_add, addIntent);
        } else {
            views.setViewVisibility(R.id.w_add, View.GONE);
        }

        // 真正能放多高的任务区（扣掉标题栏、分隔线、底部「还有 N 项」和内边距）
        int availableDp = Math.max(0, minHeightDp - CHROME_HEIGHT_DP);

        views.removeAllViews(R.id.w_list);

        // 逐条判断：放得开的两条并排一行，放不开的一条独占整行。
        // 每加一行就按它实际占的高度累加，装不下就停 —— 这样最后一行永远不会被裁掉。
        int shown = 0;
        int usedDp = 0;
        int index = 0;
        while (index < rows.size()) {
            TaskRow current = rows.get(index);
            TaskRow next = index + 1 < rows.size() ? rows.get(index + 1) : null;
            boolean currentFits = fitsInColumn(context, current.title, minWidthDp);
            boolean nextFits = next != null && fitsInColumn(context, next.title, minWidthDp);
            boolean pair = currentFits && nextFits;
            int rowHeight = pair ? PAIR_ROW_HEIGHT_DP : FULL_ROW_HEIGHT_DP;

            if (usedDp + rowHeight > availableDp) break;

            if (pair) {
                views.addView(R.id.w_list, buildPairRow(context, current, next));
                shown += 2;
                index += 2;
            } else {
                views.addView(R.id.w_list, buildFullRow(context, current));
                shown += 1;
                index += 1;
            }
            usedDp += rowHeight;
        }

        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.w_empty, View.VISIBLE);
            views.setTextViewText(R.id.w_empty, emptyText);
            views.setTextViewText(R.id.w_more, context.getString(R.string.widget_open_all));
        } else {
            views.setViewVisibility(R.id.w_empty, View.GONE);
            int rest = Math.max(0, totalCount - shown);
            views.setTextViewText(R.id.w_more, rest > 0
                    ? context.getString(R.string.widget_more_count, rest)
                    : context.getString(R.string.widget_open_all));
        }
        if (moreIntent != null) views.setOnClickPendingIntent(R.id.w_more, moreIntent);
        return views;
    }

    /** 独占整行的一条任务：宽度够，副标题（分组 / 截止）也一起显示。 */
    private static RemoteViews buildFullRow(Context context, TaskRow item) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        int color = ContextCompat.getColor(context, Category.colorRes(item.category));
        row.setTextViewText(R.id.w_item_title, item.title);
        row.setTextColor(R.id.w_item_title, color);
        row.setImageViewResource(R.id.w_item_icon, Category.ringRes(item.category));
        if (item.sub == null || item.sub.isEmpty()) {
            row.setViewVisibility(R.id.w_item_sub, View.GONE);
        } else {
            row.setViewVisibility(R.id.w_item_sub, View.VISIBLE);
            row.setTextViewText(R.id.w_item_sub, item.sub);
        }
        row.setOnClickPendingIntent(R.id.w_item_root, toggleTask(context, item.groupId, item.taskId));
        return row;
    }

    /** 并排一行：左右各一条，两条都放得开才用。分组显示在标题下面。 */
    private static RemoteViews buildPairRow(Context context, TaskRow left, TaskRow right) {
        RemoteViews line = new RemoteViews(context.getPackageName(), R.layout.widget_row_pair);
        paintPairSide(context, line, left, true);
        if (right != null) {
            paintPairSide(context, line, right, false);
            line.setViewVisibility(R.id.w_right_root, View.VISIBLE);
        } else {
            line.setViewVisibility(R.id.w_right_root, View.GONE);
        }
        return line;
    }

    private static void paintPairSide(Context context, RemoteViews line, TaskRow item, boolean left) {
        int titleId = left ? R.id.w_left_title : R.id.w_right_title;
        int subId = left ? R.id.w_left_sub : R.id.w_right_sub;
        int iconId = left ? R.id.w_left_icon : R.id.w_right_icon;
        int rootId = left ? R.id.w_left_root : R.id.w_right_root;
        int color = ContextCompat.getColor(context, Category.colorRes(item.category));
        line.setTextViewText(titleId, item.title);
        line.setTextColor(titleId, color);
        line.setImageViewResource(iconId, Category.ringRes(item.category));
        if (item.sub == null || item.sub.isEmpty()) {
            line.setViewVisibility(subId, View.GONE);
        } else {
            line.setViewVisibility(subId, View.VISIBLE);
            line.setTextViewText(subId, item.sub);
        }
        line.setOnClickPendingIntent(rootId, toggleTask(context, item.groupId, item.taskId));
    }

    // ------------------------------------------------------------ 各个小组件

    /** 「待办清单」：所有分组里未完成的任务。 */
    public static RemoteViews buildTodoList(Context context, int minHeightDp) {
        return buildTodoList(context, minHeightDp, DEFAULT_MIN_WIDTH);
    }

    public static RemoteViews buildTodoList(Context context, int minHeightDp, int minWidthDp) {
        TodoStore store = TodoStore.get(context);
        List<TaskRow> rows = new ArrayList<>();
        for (Task t : store.allPendingTasks()) {
            Group g = groupOf(store, t);
            rows.add(new TaskRow(t.title, subOf(g == null ? "" : g.name, t),
                    g == null ? "" : g.id, t.id, Category.of(t)));
        }
        return buildTaskList(context, minHeightDp, minWidthDp,
                context.getString(R.string.widget_list_title),
                context.getString(R.string.widget_pending_count, store.totalPending()),
                rows, rows.size(), true,
                openApp(context, "add"), openApp(context, null), openApp(context, null),
                context.getString(R.string.widget_empty));
    }

    /** 「分组概览」：各分组及未完成数量。 */
    public static RemoteViews buildGroupOverview(Context context, int minHeightDp) {
        return buildGroupOverview(context, minHeightDp, DEFAULT_MIN_WIDTH);
    }

    public static RemoteViews buildGroupOverview(Context context, int minHeightDp, int minWidthDp) {
        TodoStore store = TodoStore.get(context);
        int maxRows = rowsForHeight(minHeightDp);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_group);
        views.setTextViewText(R.id.w_count, context.getString(
                R.string.widget_group_count, store.groupCount(), store.totalPending()));
        views.setOnClickPendingIntent(R.id.w_header, openApp(context, null));
        views.setOnClickPendingIntent(R.id.w_refresh, refreshWidget(context));

        List<Group> groups = store.groups();
        views.removeAllViews(R.id.w_list);
        int idx = 0;
        for (Group g : groups) {
            if (idx >= maxRows) break;
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_group_item);
            row.setTextViewText(R.id.w_item_name, g.name);
            row.setTextViewText(R.id.w_item_count, context.getString(
                    R.string.widget_group_pending, g.pendingCount(), g.tasks.size()));
            row.setOnClickPendingIntent(R.id.w_item_root, openGroup(context, g.id));
            views.addView(R.id.w_list, row);
            idx++;
        }

        if (groups.isEmpty()) {
            views.setViewVisibility(R.id.w_list, View.GONE);
            views.setViewVisibility(R.id.w_empty, View.VISIBLE);
            views.setTextViewText(R.id.w_more, context.getString(R.string.widget_create_group));
        } else {
            views.setViewVisibility(R.id.w_list, View.VISIBLE);
            views.setViewVisibility(R.id.w_empty, View.GONE);
            int rest = groups.size() - maxRows;
            views.setTextViewText(R.id.w_more, rest > 0
                    ? context.getString(R.string.widget_more_count, rest)
                    : context.getString(R.string.widget_open_all));
        }
        return views;
    }

    /** 「分类概览」：四个象限及各自分类的未完成数量。 */
    public static RemoteViews buildCategoryOverview(Context context, int minHeightDp, int minWidthDp) {
        TodoStore store = TodoStore.get(context);
        int[] counts = store.categoryPendingCounts();
        List<Task> all = store.allPendingTasks();

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_category);
        int totalPending = counts[0] + counts[1] + counts[2] + counts[3];
        views.setTextViewText(R.id.w_count, context.getString(
                R.string.widget_category_count, totalPending));
        views.setOnClickPendingIntent(R.id.w_header, openCategoryTab(context));
        views.setOnClickPendingIntent(R.id.w_refresh, refreshWidget(context));

        views.removeAllViews(R.id.w_list);
        for (int cat = 0; cat < Category.COUNT; cat++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_category_item);
            row.setTextViewText(R.id.w_item_name, context.getString(Category.nameRes(cat)));
            row.setTextViewText(R.id.w_item_count, context.getString(
                    R.string.widget_group_pending, counts[cat], countIn(all, cat)));
            row.setImageViewResource(R.id.w_item_dot, Category.dotRes(cat));
            row.setOnClickPendingIntent(R.id.w_item_root, openCategoryTab(context));
            views.addView(R.id.w_list, row);
        }
        views.setTextViewText(R.id.w_more, context.getString(R.string.widget_open_all));
        views.setOnClickPendingIntent(R.id.w_more, openCategoryTab(context));
        return views;
    }

    /** 「某个组的任务」：只看一个分组，实例之间互不影响。 */
    public static RemoteViews buildGroupTasks(Context context, int widgetId,
                                              int minHeightDp, int minWidthDp) {
        TodoStore store = TodoStore.get(context);
        String groupId = WidgetConfig.getGroup(context, widgetId);
        Group group = groupId == null ? null : store.findGroup(groupId);

        if (group == null) {
            return buildTaskList(context, minHeightDp, minWidthDp,
                    context.getString(R.string.widget_group_task_title_default),
                    context.getString(R.string.widget_pending_count, 0),
                    new ArrayList<TaskRow>(), 0, false, null,
                    openWidgetConfig(context, widgetId, true),
                    openApp(context, null),
                    context.getString(R.string.widget_no_group));
        }

        List<TaskRow> rows = new ArrayList<>();
        for (Task t : group.pendingTasks()) {
            rows.add(new TaskRow(t.title, subOf("", t), group.id, t.id, Category.of(t)));
        }
        return buildTaskList(context, minHeightDp, minWidthDp,
                group.name,
                context.getString(R.string.widget_pending_count, group.pendingCount()),
                rows, rows.size(), true,
                openGroupAdd(context, group.id),
                openWidgetConfig(context, widgetId, true),
                openGroup(context, group.id),
                context.getString(R.string.widget_group_empty));
    }

    /** 「某个类的任务」：只看一个象限，跨分组收集。 */
    public static RemoteViews buildCategoryTasks(Context context, int widgetId,
                                                 int minHeightDp, int minWidthDp) {
        TodoStore store = TodoStore.get(context);
        int category = WidgetConfig.getCategory(context, widgetId);

        if (category < 0 || category >= Category.COUNT) {
            return buildTaskList(context, minHeightDp, minWidthDp,
                    context.getString(R.string.widget_category_task_title_default),
                    context.getString(R.string.widget_pending_count, 0),
                    new ArrayList<TaskRow>(), 0, false, null,
                    openWidgetConfig(context, widgetId, false),
                    openCategoryTab(context),
                    context.getString(R.string.widget_empty));
        }

        List<TaskRow> rows = new ArrayList<>();
        int total = 0;
        for (Group group : store.groups()) {
            for (Task t : group.sortedTasks()) {
                if (Category.of(t) != category) continue;
                total++;
                if (t.done) continue;
                rows.add(new TaskRow(t.title, subOf(group.name, t), group.id, t.id, category));
            }
        }
        return buildTaskList(context, minHeightDp, minWidthDp,
                context.getString(Category.nameRes(category)),
                context.getString(R.string.widget_pending_count, rows.size()),
                rows, rows.size(), true,
                openApp(context, "add"),
                openWidgetConfig(context, widgetId, false),
                openCategoryTab(context),
                context.getString(R.string.widget_empty));
    }

    /** 副标题：所属分组 + （有的话）截止时间。 */
    private static String subOf(String groupName, Task task) {
        StringBuilder sb = new StringBuilder();
        if (groupName != null && !groupName.isEmpty()) sb.append(groupName);
        if (task.hasDue()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(TimeText.friendly(task.dueAt));
        }
        return sb.toString();
    }

    private static int countIn(List<Task> tasks, int category) {
        int n = 0;
        for (Task t : tasks) {
            if (Category.of(t) == category) n++;
        }
        return n;
    }

    private static Group groupOf(TodoStore store, Task task) {
        for (Group g : store.groups()) {
            if (g.findTask(task.id) != null) return g;
        }
        return null;
    }

    // ------------------------------------------------------------ Intent

    public static PendingIntent toggleTask(Context context, String groupId, String taskId) {
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(TaskActionReceiver.ACTION_TOGGLE_TASK);
        intent.putExtra(TaskActionReceiver.EXTRA_GROUP_ID, groupId);
        intent.putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId);
        int requestCode = (groupId + "#" + taskId).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 小组件上的「刷新」按钮。 */
    public static PendingIntent refreshWidget(Context context) {
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(TaskActionReceiver.ACTION_REFRESH_WIDGET);
        return PendingIntent.getBroadcast(context, 909, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent openApp(Context context, String action) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (action != null) intent.setAction(action);
        // 加号按钮和普通入口用不同的 requestCode，避免两者互相覆盖 action。
        int requestCode = (action == null) ? 102 : 101;
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 打开 App 的「分类」页签。 */
    public static PendingIntent openCategoryTab(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(MainActivity.EXTRA_TAB, 1);
        return PendingIntent.getActivity(context, 103, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent openGroup(Context context, String groupId) {
        Intent intent = new Intent(context, GroupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, groupId);
        return PendingIntent.getActivity(context, groupId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 在某个分组里直接新建任务。 */
    public static PendingIntent openGroupAdd(Context context, String groupId) {
        Intent intent = new Intent(context, GroupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(GroupActivity.EXTRA_AUTO_ADD, true);
        return PendingIntent.getActivity(context, (groupId + "#add").hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 打开小组件配置页（换显示哪个分组 / 哪个分类）。 */
    public static PendingIntent openWidgetConfig(Context context, int widgetId, boolean group) {
        Intent intent = new Intent(context, WidgetConfigActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.putExtra(WidgetConfigActivity.EXTRA_MODE,
                group ? WidgetConfigActivity.MODE_GROUP : WidgetConfigActivity.MODE_CATEGORY);
        return PendingIntent.getActivity(context, (widgetId + (group ? "#g" : "#c")).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
