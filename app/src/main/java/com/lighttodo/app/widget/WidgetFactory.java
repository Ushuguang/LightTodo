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

    /**
     * 独占整行的一条任务占多高（dp）。
     *
     * <p>这几个数字都是用 Robolectric 把小组件<b>真正渲染一遍</b>量出来的，不是拍脑袋估的
     * （以前估的是 32/44/78，实际是 29/45/68，每行差几个 dp，滚几行就少显示一整行）。
     * 实测值 + 1dp 余量，防真机字体和沙箱有差异。改布局时必须同步改这里，
     * WidgetSpaceUsageTest 里的「布局实测高度和常量一致」会盯着。</p>
     */
    public static final int FULL_ROW_HEIGHT_DP = 30;
    /** 并排一行的两条任务占多高（dp）：上面标题、下面分组，两行文字。 */
    public static final int PAIR_ROW_HEIGHT_DP = 46;
    /** 「分组概览」里一行分组占多高（dp）。 */
    public static final int GROUP_ROW_HEIGHT_DP = 31;
    /**
     * 小组件被拖得很矮时用的「精简行」高度（dp）：只有一行标题、不显示副标题。
     *
     * <p>实测（带 App 主题渲染，文字设了 includeFontPadding=false）：
     * 11sp 文字行高 + 上下各 1dp 内边距 ≈ 21dp。取 21dp ——
     * 宁可少显示一行，也不能把最后一行裁掉。
     * （沙箱字体的行高比真机偏大，真机上会稍微宽松一点。）</p>
     */
    public static final int COMPACT_ROW_HEIGHT_DP = 21;

    /**
     * 小组件被拖到最小（60dp 以下）时用的「极简行」高度（dp）。
     *
     * <p>实测（includeFontPadding=false）：10sp 文字行高 ≈ 18dp，取 18dp。</p>
     */
    public static final int TINY_ROW_HEIGHT_DP = 18;

    /** 低于这个高度（dp）就用极简行（比紧凑行还矮）。 */
    public static final int TINY_HEIGHT_DP = 60;

    /** 行样式：正常（标题 + 副标题）。 */
    public static final int LEVEL_NORMAL = 0;
    /** 行样式：紧凑（只有标题，11sp）。 */
    public static final int LEVEL_COMPACT = 1;
    /** 行样式：极简（只有标题，10sp，圆点更小）。 */
    public static final int LEVEL_TINY = 2;
    /**
     * 除任务列表以外的固定高度（dp）：上下内边距 + 标题栏 + 分隔线。
     * 实测 46dp（内边距 8+8、标题栏 20.5、分隔线 6+1、任务区上边距 2）。
     */
    public static final int CHROME_HEIGHT_DP = 47;
    /**
     * 小组件很小时用的紧凑标题栏高度（dp）：省掉分隔线、标题字号也调小。
     * 不然一个只放得下一行任务的小组件，标题栏就吃掉一大半，看着全是空白。
     *
     * <p>实测（带 App 主题渲染，跟真机一致）：上下内边距 16dp + 标题栏 20.5dp
     * + 任务区上边距 2dp ≈ 39dp。</p>
     */
    public static final int COMPACT_CHROME_HEIGHT_DP = 39;
    /** 低于这个高度（dp）就切到紧凑模式。 */
    public static final int COMPACT_HEIGHT_DP = 100;
    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 12;

    /** 两栏布局需要的最小宽度（dp）：再窄的话任务标题就挤成一条了。 */
    public static final int TWO_COLUMN_MIN_WIDTH_DP = 240;
    /** 任务标题字号（sp），要和 widget_item.xml / widget_row_pair.xml 里的 13sp 对齐。 */
    private static final float TASK_TITLE_SP = 13f;
    /** 副标题（分组名 / 截止时间）的字号。 */
    private static final float TASK_SUB_SP = 10f;
    /** 小组件左右内边距（dp），widget_task_list.xml 里是 12dp × 2。 */
    /** 布局根节点的左右内边距（res/layout/widget_task_list.xml 里是 8dp）。
     *  这里必须和真实布局一致：预留得比实际大，就会白白浪费半栏宽度，
     *  本该并排的两条被迫各占一整行。 */
    private static final float WIDGET_PADDING_DP = 8f;
    /** 并排两栏之间的间距（dp）。 */
    private static final float COLUMN_GAP_DP = 10f;
    /** 一栏里除标题外还要占的地方：勾选圈 14 + 间距 6 + 色点 8 + 间距 5。 */
    /** 半栏里除文字以外要占的位置：勾选圆圈 14dp + 与文字的间距 6dp。 */
    private static final float ROW_CHROME_DP = 20f;
    /** 字体渲染误差的余量（dp）。 */
    /** 留给字体渲染误差的余量（dp）。宁可少并排一行，也不要出现省略号。 */
    private static final float FIT_SAFETY_DP = 6f;

    /** 拿不到尺寸时的兜底值。 */
    private static final int DEFAULT_MIN_WIDTH = 180;

    private WidgetFactory() {
    }

    /** 根据小组件高度估算能放下的整行任务条数。 */
    public static int rowsForHeight(int minHeightDp) {
        int rows = (minHeightDp - chromeHeightDp(minHeightDp)) / GROUP_ROW_HEIGHT_DP;
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
        return fitsInColumn(context, title, "", minWidthDp);
    }

    /**
     * 判断一条任务能不能放进半栏里（和另一条并排）。
     *
     * <p>主标题和副标题都要放得下：副标题是分组名或截止时间，
     * 有时候反而比主标题还长（例如「截止 11-25 14:30」），只看主标题会溢出。</p>
     */
    public static boolean fitsInColumn(Context context, String title, String sub, int minWidthDp) {
        if (title == null || title.isEmpty()) return true;
        if (minWidthDp < TWO_COLUMN_MIN_WIDTH_DP) return false;
        if (context == null) return false;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float titlePx = textWidthPx(context, title, TASK_TITLE_SP, dm);
        float subPx = textWidthPx(context, sub, TASK_SUB_SP, dm);
        float columnDp = (minWidthDp - WIDGET_PADDING_DP - COLUMN_GAP_DP) / 2f;
        float availableDp = columnDp - ROW_CHROME_DP - FIT_SAFETY_DP;
        return Math.max(titlePx, subPx) <= availableDp * dm.density;
    }

    /** 量一段文字大概有多宽（px）：字体实测和按字数估算取大的那个，判断偏保守。 */
    private static float textWidthPx(Context context, String text, float sp, DisplayMetrics dm) {
        if (text == null || text.isEmpty()) return 0f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(sp * dm.scaledDensity);
        float measured = paint.measureText(text);
        float estimated = estimateEm(text) * sp * dm.scaledDensity;
        return Math.max(measured, estimated);
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

    /**
     * 排版结果里的一行：左边必有一条任务，右边有的话就是并排的第二条。
     */
    public static class RowPlan {
        public final TaskRow left;
        /** 并排的右边那条；null 表示这一行只有 {@link #left} 一条，独占整行。 */
        public final TaskRow pairRight;
        /** 这一行的样式：{@link #LEVEL_NORMAL} / {@link #LEVEL_COMPACT} / {@link #LEVEL_TINY}。 */
        public final int level;

        public RowPlan(TaskRow left, TaskRow pairRight) {
            this(left, pairRight, LEVEL_NORMAL);
        }

        public RowPlan(TaskRow left, TaskRow pairRight, int level) {
            this.left = left;
            this.pairRight = pairRight;
            this.level = level;
        }
    }

    /**
     * 算出来这个高度能排下几行任务。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>放得开的两条并排一行（占 42dp），放不开的一条独占整行（占 28dp）；</li>
     *   <li>每加一行就按它实际占的高度累加，装不下就停 —— 这样最后一行永远不会被裁掉；</li>
     *   <li><b>降级</b>：两条本来能并排，但剩下的高度不够并排时，改成让其中一条独占整行
     *       继续往下排 —— 这样不会因为「差一点点放不下并排」而白白空掉一格。</li>
     * </ol>
     */
    public static List<RowPlan> planRows(Context context, int minHeightDp, int minWidthDp,
                                          List<TaskRow> rows) {
        return planRows(context, minHeightDp, minWidthDp, rows, chromeHeightDp(minHeightDp));
    }

    /** 算出来这个高度能排下几行任务（标题栏占多少高度由调用方给）。 */
    public static List<RowPlan> planRows(Context context, int minHeightDp, int minWidthDp,
                                          List<TaskRow> rows, int chromeDp) {
        List<RowPlan> plan = new ArrayList<>();
        // 标题栏比正常矮，说明组件被拖小了：这时改用更矮的行样式，
        // 一条只占 19dp（紧凑）或 16dp（极简），普通行要 30dp ——
        // 这样连 60dp 高的小组件也能真正显示一条任务，而不是空着。
        int level = levelForHeight(minHeightDp);
        boolean compact = level != LEVEL_NORMAL;
        int rowDp = rowHeightDpForLevel(level);
        int availableDp = Math.max(0, minHeightDp - chromeDp);
        int usedDp = 0;
        int index = 0;
        while (index < rows.size()) {
            TaskRow current = rows.get(index);
            TaskRow next = index + 1 < rows.size() ? rows.get(index + 1) : null;
            // 精简行不显示副标题，宽度只按标题算。
            boolean currentFits = compact
                    ? fitsInColumn(context, current.title, minWidthDp)
                    : fitsInColumn(context, current.title, current.sub, minWidthDp);
            boolean nextFits = next != null && (compact
                    ? fitsInColumn(context, next.title, minWidthDp)
                    : fitsInColumn(context, next.title, next.sub, minWidthDp));
            // 组件很矮时不做并排：并排一行要 46dp，矮组件里并排等于什么都放不下。
            boolean pair = !compact && currentFits && nextFits;

            if (pair) {
                if (usedDp + PAIR_ROW_HEIGHT_DP <= availableDp) {
                    plan.add(new RowPlan(current, next));
                    usedDp += PAIR_ROW_HEIGHT_DP;
                    index += 2;
                    continue;
                }
                // 并排放不下了，退化成一条整行（更矮），放得下就继续
                if (usedDp + rowDp > availableDp) break;
                plan.add(new RowPlan(current, null, level));
                usedDp += rowDp;
                index += 1;
                continue;
            }

            if (usedDp + rowDp > availableDp) break;
            plan.add(new RowPlan(current, null, level));
            usedDp += rowDp;
            index += 1;
        }
        return plan;
    }

    /** 间隙的最大高度（dp）：32 + 16 + 8 + 4 + 2 这些块能拼出来的上限。 */
    private static final int GAP_MAX_DP = 62;
    /** 二进制块的dp 数，和 res/layout/widget_gap.xml 里那五个块一一对应。 */
    private static final int[] GAP_BLOCKS_DP = {32, 16, 8, 4, 2};
    /** 对应的 view id。 */
    private static final int[] GAP_BLOCK_IDS = {
            R.id.g32, R.id.g16, R.id.g8, R.id.g4, R.id.g2};

    /**
     * 造一条指定高度的弹性空隙。
     *
     * <p>高度用二进制块拼出来：32 + 16 + 8 + 4 + 2 = 62dp，
     * 任意 0~62dp 都能拼出来，误差不超过 1dp。</p>
     */
    private static RemoteViews buildGap(Context context, int gapDp) {
        RemoteViews gap = new RemoteViews(context.getPackageName(), R.layout.widget_gap);
        int rest = gapDp;
        for (int i = 0; i < GAP_BLOCKS_DP.length; i++) {
            boolean on = rest >= GAP_BLOCKS_DP[i];
            gap.setViewVisibility(GAP_BLOCK_IDS[i], on ? View.VISIBLE : View.GONE);
            if (on) rest -= GAP_BLOCKS_DP[i];
        }
        return gap;
    }

    /** 小组件太矮时切到紧凑模式：标题栏别占太多地方。 */
    public static boolean isCompact(int minHeightDp) {
        return minHeightDp < COMPACT_HEIGHT_DP;
    }

    /** 当前尺寸下，标题栏（外加内边距那些）占掉多少高度。 */
    public static int chromeHeightDp(int minHeightDp) {
        return isCompact(minHeightDp) ? COMPACT_CHROME_HEIGHT_DP : CHROME_HEIGHT_DP;
    }

    /**
     * 把小组件切成「紧凑模式」：矮的时候别让标题栏占掉一半。
     *
     * <p>做法：分隔线藏掉（省 7dp）、标题和计数字号调小（再省 3dp）。</p>
     */
    private static void applyCompact(RemoteViews views, int minHeightDp) {
        if (!isCompact(minHeightDp)) return;
        views.setViewVisibility(R.id.w_divider, View.GONE);
        views.setTextViewTextSize(R.id.w_title, android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
        views.setTextViewTextSize(R.id.w_count, android.util.TypedValue.COMPLEX_UNIT_SP, 10f);
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

        applyCompact(views, minHeightDp);

        // 真正能放多高的任务区（扣掉标题栏、分隔线、底部「还有 N 项」和内边距）
        int availableDp = Math.max(0, minHeightDp - chromeHeightDp(minHeightDp));

        views.removeAllViews(R.id.w_list);

        // 排版：逐条判断放得开的两条并排一行，放不开的一条独占整行。
        List<RowPlan> plan = planRows(context, minHeightDp, minWidthDp, rows);
        int shown = 0;

        // 高度排不满时，把剩下的空间匀成行与行之间的间隙，让内容均匀铺满整个区域，
        // 而不是全部堆在顶部、下面空一大块（这正是用户说的「空间利用率不高」）。
        //
        // 间隙高度按「剩余空间 ÷ 间隙数」精确算出来，而不是固定几档：
        // 固定档位要么留一大块空白，要么超出被裁。每一行额外留 2px 安全余量，
        // 保证真机字体比测试环境大一点时也不会把最后一行顶出去。
        // 间隙高度 = 剩下的空间 ÷ 间隙条数（向下取整到 dp），最多 62dp。
        // 行高用的是「实测值」（比预算常量少 1dp），这样算出来的间隙偏保守，
        // 真机字体比测试环境大一点时也不会把最后一行顶出去。
        int rowsDp = 0;
        for (RowPlan rp : plan) {
            rowsDp += rowHeightDp(rp) - 1;
        }
        int gaps = plan.size() - 1;
        int gapDp = 0;
        if (gaps > 0) {
            gapDp = (availableDp - rowsDp) / gaps;
            if (gapDp > GAP_MAX_DP) gapDp = GAP_MAX_DP;
            if (gapDp < 0) gapDp = 0;
        }

        int rowIndex = 0;
        for (RowPlan rp : plan) {
            if (gapDp > 0 && rowIndex > 0) {
                views.addView(R.id.w_list, buildGap(context, gapDp));
            }
            if (rp.pairRight != null) {
                views.addView(R.id.w_list, buildPairRow(context, rp.left, rp.pairRight));
                shown += 2;
            } else if (rp.level == LEVEL_TINY) {
                views.addView(R.id.w_list, buildTinyRow(context, rp.left));
                shown += 1;
            } else if (rp.level == LEVEL_COMPACT) {
                views.addView(R.id.w_list, buildCompactRow(context, rp.left));
                shown += 1;
            } else {
                views.addView(R.id.w_list, buildFullRow(context, rp.left));
                shown += 1;
            }
            rowIndex++;
        }

        // 最后一行下面还剩多少空间？再补一段间隙吃掉，
        // 否则组件底部会空出一截白（用户说的「空间利用率不高」）。
        int restDp = availableDp - rowsDp - gaps * gapDp;
        if (restDp > 0) {
            views.addView(R.id.w_list, buildGap(context, Math.min(restDp, GAP_MAX_DP)));
        }

        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.w_empty, View.VISIBLE);
            views.setTextViewText(R.id.w_empty, emptyText);
        } else {
            views.setViewVisibility(R.id.w_empty, View.GONE);
        }
        // 标题栏右侧显示「已显示 / 总数」：原来这行信息放在底部，会白白占掉 22dp，
        // 相当于少显示一行任务。现在合并到标题栏，空间全留给任务。
        views.setTextViewText(R.id.w_count, context.getString(
                R.string.widget_shown_count, shown, totalCount));
        if (moreIntent != null) views.setOnClickPendingIntent(R.id.w_header, moreIntent);
        return views;
    }

    /** 这一行实际占多高（dp）。 */
    private static int rowHeightDp(RowPlan rp) {
        if (rp.level != LEVEL_NORMAL) return rowHeightDpForLevel(rp.level);
        return (rp.pairRight != null) ? PAIR_ROW_HEIGHT_DP : FULL_ROW_HEIGHT_DP;
    }

    /** 这个高度该用哪一档的行样式。 */
    public static int levelForHeight(int minHeightDp) {
        if (minHeightDp <= TINY_HEIGHT_DP) return LEVEL_TINY;
        if (minHeightDp < COMPACT_HEIGHT_DP) return LEVEL_COMPACT;
        return LEVEL_NORMAL;
    }

    /** 某一档的行占多高（dp）。 */
    private static int rowHeightDpForLevel(int level) {
        if (level == LEVEL_TINY) return TINY_ROW_HEIGHT_DP;
        if (level == LEVEL_COMPACT) return COMPACT_ROW_HEIGHT_DP;
        return FULL_ROW_HEIGHT_DP;
    }

    /**
     * 精简行：组件被拖得很矮时用，只有一行标题 + 左侧圆点（约 19dp），
     * 不显示副标题 —— 这样 60dp 高的小组件也能真正显示一条任务。
     */
    private static RemoteViews buildCompactRow(Context context, TaskRow item) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_item_compact);
        int color = ContextCompat.getColor(context, Category.colorRes(item.category));
        row.setTextViewText(R.id.w_item_title, item.title);
        row.setTextColor(R.id.w_item_title, color);
        row.setImageViewResource(R.id.w_item_icon, Category.ringRes(item.category));
        row.setOnClickPendingIntent(R.id.w_item_root, toggleTask(context, item.groupId, item.taskId));
        return row;
    }

    /** 极简行：组件被拖到最小的时候用，圆点 + 一行 10sp 的标题。 */
    private static RemoteViews buildTinyRow(Context context, TaskRow item) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_item_tiny);
        int color = ContextCompat.getColor(context, Category.colorRes(item.category));
        row.setTextViewText(R.id.w_item_title, item.title);
        row.setTextColor(R.id.w_item_title, color);
        row.setImageViewResource(R.id.w_item_icon, Category.ringRes(item.category));
        row.setOnClickPendingIntent(R.id.w_item_root, toggleTask(context, item.groupId, item.taskId));
        return row;
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

    /**
     * 「自定义清单」：按用户选的范围显示任务。
     *
     * <p>范围有三种：全部任务、某个分组、某个四象限分类。用户在添加到桌面时选好，
     * 之后这个卡片就固定按这个范围显示；同一个卡片可以在桌面上叠好几个，各看各的。</p>
     */
    public static RemoteViews buildCustomList(Context context, int widgetId,
                                               int minHeightDp, int minWidthDp) {
        TodoStore store = TodoStore.get(context);
        int scope = WidgetConfig.getScopeType(context, widgetId);
        String groupId = WidgetConfig.getGroup(context, widgetId);
        int category = WidgetConfig.getCategory(context, widgetId);

        List<TaskRow> rows = new ArrayList<>();
        for (Task t : store.allPendingTasks()) {
            Group g = groupOf(store, t);
            if (scope == WidgetConfig.SCOPE_GROUP) {
                if (g == null || !g.id.equals(groupId)) continue;
            } else if (scope == WidgetConfig.SCOPE_CATEGORY) {
                if (Category.of(t) != category) continue;
            }
            rows.add(new TaskRow(t.title, subOf(g == null ? "" : g.name, t),
                    g == null ? "" : g.id, t.id, Category.of(t)));
        }

        String title;
        Group scopeGroup = scope == WidgetConfig.SCOPE_GROUP ? store.findGroup(groupId) : null;
        if (scopeGroup != null) {
            title = scopeGroup.name;
        } else if (scope == WidgetConfig.SCOPE_CATEGORY) {
            title = context.getString(Category.nameRes(category));
        } else {
            title = context.getString(R.string.widget_list_title);
        }
        return buildTaskList(context, minHeightDp, minWidthDp, title,
                context.getString(R.string.widget_pending_count, rows.size()),
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
        applyCompact(views, minHeightDp);

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

        // 分组不够多、但小组件很高时，行与行之间拉开距离把空间用掉（别空一大块）
        if (!groups.isEmpty()) {
            int availableDp = Math.max(0, minHeightDp - chromeHeightDp(minHeightDp));
            int usedDp = idx * GROUP_ROW_HEIGHT_DP;
            int gaps = idx - 1;
            int gapDp = 0;
            if (gaps > 0) {
                int spare = availableDp - usedDp;
                if (spare > 0) {
                    gapDp = spare / gaps;
                    if (gapDp > GAP_MAX_DP) gapDp = GAP_MAX_DP;
                }
            }
            if (gapDp > 0) {
                // 重新插一遍，中间夹上间隙
                views.removeAllViews(R.id.w_list);
                int n = 0;
                for (Group g : groups) {
                    if (n >= idx) break;
                    if (n > 0) views.addView(R.id.w_list, buildGap(context, gapDp));
                    RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_group_item);
                    row.setTextViewText(R.id.w_item_name, g.name);
                    row.setTextViewText(R.id.w_item_count, context.getString(
                            R.string.widget_group_pending, g.pendingCount(), g.tasks.size()));
                    row.setOnClickPendingIntent(R.id.w_item_root, openGroup(context, g.id));
                    views.addView(R.id.w_list, row);
                    n++;
                }
            }
        }

        if (groups.isEmpty()) {
            views.setViewVisibility(R.id.w_list, View.GONE);
            views.setViewVisibility(R.id.w_empty, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.w_list, View.VISIBLE);
            views.setViewVisibility(R.id.w_empty, View.GONE);
        }
        views.setTextViewText(R.id.w_count, context.getString(
                R.string.widget_shown_groups, Math.min(idx, groups.size()), groups.size()));
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
        applyCompact(views, minHeightDp);

        views.removeAllViews(R.id.w_list);
        // 矮的时候放不下 4 行就少放几行，绝不让最后一行被裁掉
        int availableDp = Math.max(0, minHeightDp - chromeHeightDp(minHeightDp));
        int maxRows = Math.max(0, availableDp / GROUP_ROW_HEIGHT_DP);
        int shown = 0;
        for (int cat = 0; cat < Category.COUNT && shown < maxRows; cat++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_category_item);
            row.setTextViewText(R.id.w_item_name, context.getString(Category.nameRes(cat)));
            row.setTextViewText(R.id.w_item_count, context.getString(
                    R.string.widget_group_pending, counts[cat], countIn(all, cat)));
            row.setImageViewResource(R.id.w_item_dot, Category.dotRes(cat));
            row.setOnClickPendingIntent(R.id.w_item_root, openCategoryTab(context));
            views.addView(R.id.w_list, row);
            shown++;
        }

        // 高度富余时，行与行之间拉开距离把空间用掉（别空一大块）
        if (shown > 1) {
            int usedDp = shown * GROUP_ROW_HEIGHT_DP;
            int gaps = shown - 1;
            int gapDp = 0;
            int spare = availableDp - usedDp;
            if (spare > 0) {
                gapDp = spare / gaps;
                if (gapDp > GAP_MAX_DP) gapDp = GAP_MAX_DP;
            }
            if (gapDp > 0) {
                views.removeAllViews(R.id.w_list);
                int n = 0;
                for (int cat = 0; cat < Category.COUNT && n < shown; cat++) {
                    if (n > 0) views.addView(R.id.w_list, buildGap(context, gapDp));
                    RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_category_item);
                    row.setTextViewText(R.id.w_item_name, context.getString(Category.nameRes(cat)));
                    row.setTextViewText(R.id.w_item_count, context.getString(
                            R.string.widget_group_pending, counts[cat], countIn(all, cat)));
                    row.setImageViewResource(R.id.w_item_dot, Category.dotRes(cat));
                    row.setOnClickPendingIntent(R.id.w_item_root, openCategoryTab(context));
                    views.addView(R.id.w_list, row);
                    n++;
                }
            }
        }
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
