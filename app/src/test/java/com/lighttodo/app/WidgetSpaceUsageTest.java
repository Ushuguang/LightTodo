package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.widget.WidgetFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * 小组件的空间利用率：给定高度能排下几行任务。
 *
 * <p>用户反馈过「分配 4 个格子，任务只占 2 个格子」。这组测试就是守住这次的修复：
 * 高度预算要按实测值算，并且放不下并排时要能降级成整行继续排，不浪费空间。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
public class WidgetSpaceUsageTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    /** 造一批短标题的任务（能并排）。 */
    private List<WidgetFactory.TaskRow> shortTasks(int n) {
        List<WidgetFactory.TaskRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new WidgetFactory.TaskRow("任务" + i, "工作", "g1", "t" + i, 2));
        }
        return rows;
    }

    /** 造一批长标题的任务（放不下半栏，只能独占整行）。 */
    private List<WidgetFactory.TaskRow> longTasks(int n) {
        List<WidgetFactory.TaskRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new WidgetFactory.TaskRow("这是一条很长很长很长很长很长的任务标题" + i,
                    "工作", "g1", "t" + i, 2));
        }
        return rows;
    }

    private int shownCount(List<WidgetFactory.RowPlan> plan) {
        int n = 0;
        for (WidgetFactory.RowPlan rp : plan) {
            n += (rp.pairRight != null) ? 2 : 1;
        }
        return n;
    }

    @Test
    public void 四个格子能排下三行并排() {
        // 4 格 ≈ 192dp：chrome 47dp → 可用 145dp → 并排行 46dp 能排 3 行 = 6 条
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, 192, 320, shortTasks(20));
        assertEquals("4 格应该能显示 6 条任务", 6, shownCount(plan));
        assertEquals("应该排成 3 行并排", 3, plan.size());
    }

    @Test
    public void 三个格子能排下两行并排() {
        // 3 格 ≈ 144dp：可用 97dp → 并排行 46dp 能排 2 行 = 4 条
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, 144, 320, shortTasks(20));
        assertEquals("3 格应该能显示 4 条任务", 4, shownCount(plan));
    }

    @Test
    public void 五个格子能排下四行() {
        // 5 格 ≈ 240dp：可用 193dp → 并排 46dp 排 4 行 = 8 条
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, 240, 320, shortTasks(20));
        assertEquals("5 格应该能显示 8 条任务", 8, shownCount(plan));
    }

    @Test
    public void 放不下并排时会降级成整行继续排() {
        // 找一个「并排放不下、但整行放得下」的高度：
        // 可用高度介于一条并排(46)和两条并排(92)之间，但容得下一条整行(30)
        int height = 47 + 46 + 30 + 1;   // 可用 77dp
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, height, 320, shortTasks(20));
        assertTrue("应该至少排下 2 条", shownCount(plan) >= 2);
        // 第一行是并排，第二行降级成整行
        assertTrue("第一行应该是并排", plan.get(0).pairRight != null);
        if (plan.size() > 1) {
            assertEquals("第二行应该降级成独占整行", null, plan.get(1).pairRight);
        }
    }

    @Test
    public void 标题长时按整行排() {
        // 4 格、长标题 → 每条独占 30dp：可用 145dp → 4 行 = 4 条
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, 192, 320, longTasks(20));
        assertEquals("长标题时 4 格应该显示 4 条", 4, shownCount(plan));
        for (WidgetFactory.RowPlan rp : plan) {
            assertEquals("长标题不该并排", null, rp.pairRight);
        }
    }

    @Test
    public void 排出来的高度不会超出分配到的高度() {
        // 不管怎么排，用掉的高度都不能超过可用高度，否则最后一行会被裁掉
        int[] heights = {110, 144, 192, 240, 288, 360};
        for (int h : heights) {
            List<WidgetFactory.RowPlan> plan =
                    WidgetFactory.planRows(context, h, 320, shortTasks(30));
            int used = 0;
            for (WidgetFactory.RowPlan rp : plan) {
                used += (rp.pairRight != null) ? 46 : 30;
            }
            assertTrue("高度 " + h + " dp 排版超了：用了 " + used,
                    used <= h - 47);
        }
    }

    @Test
    public void 任务不够时不会硬凑() {
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, 192, 320, shortTasks(3));
        assertEquals("只有 3 条任务就只显示 3 条", 3, shownCount(plan));
    }

    @Test
    public void 没有任务时不报错() {
        List<WidgetFactory.RowPlan> plan =
                WidgetFactory.planRows(context, 192, 320, new ArrayList<>());
        assertEquals(0, plan.size());
    }

    /**
     * 布局高度必须和 WidgetFactory 里写死的常量对得上。
     *
     * <p>以前这些数字是拍脑袋估的（并排行估 44dp、实际 48dp），
     * 结果每个格子都白占一点、越滚越少。这个测试把布局真正渲染一遍量出来，
     * 以后谁改了布局忘了同步常量，这里就会红。</p>
     */
    @Test
    public void 布局实测高度和常量一致() {
        android.content.Context themed =
                new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
        float density = context.getResources().getDisplayMetrics().density;

        // 直接把真实渲染出来的小组件量一遍：这才是用户看到的尺寸
        com.lighttodo.app.store.TodoStore.resetForTest(context);
        com.lighttodo.app.store.TodoStore store = com.lighttodo.app.store.TodoStore.get(context);
        com.lighttodo.app.model.Group g = store.addGroup("工作");
        store.addTask(g.id, "短标题", "");
        store.addTask(g.id, "另一条", "");

        int heightDp = 192;
        int widthDp = 280;
        android.widget.RemoteViews views =
                com.lighttodo.app.widget.WidgetFactory.buildTodoList(context, heightDp, widthDp);
        android.view.View root = apply(views);
        int heightPx = Math.round(heightDp * density);
        int widthPx = Math.round(widthDp * density);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(widthPx,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx,
                        android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, widthPx, heightPx);

        android.widget.LinearLayout list = root.findViewById(R.id.w_list);
        assertNotNull("任务区没渲染出来", list);
        assertTrue("应该有任务行", list.getChildCount() > 0);

        // 任务区之外占掉的高度 = chrome（内边距 + 标题栏 + 分隔线 + 底部「更多」）
        int chromePx = heightPx - list.getMeasuredHeight();
        android.view.View hv = root.findViewById(R.id.w_header);
        android.view.View dv = root.findViewById(R.id.w_divider);
        int chromeReal = dp(chromePx, density);
        int pairReal = dp(list.getChildAt(0).getMeasuredHeight(), density);

        // 第一行是并排（两条短标题放得下半栏）
        android.view.View firstRow = list.getChildAt(0);
        int pairReal3 = dp(firstRow.getMeasuredHeight(), density);

        // 宽度不够并排（TWO_COLUMN_MIN_WIDTH_DP = 240dp）→ 每条独占整行
        int narrowDp = 180;
        int narrowPx = Math.round(narrowDp * density);
        views = com.lighttodo.app.widget.WidgetFactory.buildTodoList(context, heightDp, narrowDp);
        root = apply(views);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(narrowPx,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx,
                        android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, narrowPx, heightPx);
        list = root.findViewById(R.id.w_list);
        assertNotNull(list);
        assertTrue("应该有任务行", list.getChildCount() > 0);
        int fullReal = dp(list.getChildAt(0).getMeasuredHeight(), density);
        android.view.View fv = list.getChildAt(0);
        android.view.View fTitle = fv.findViewById(R.id.w_item_title);
        android.view.View fSub = fv.findViewById(R.id.w_item_sub);
        android.view.View pTitle = fv.findViewById(R.id.w_left_title);
        String shape = (fv.findViewById(R.id.w_item_title) != null) ? "整行" : "并排";
        // 常量应该等于「实测值 + 1dp 余量」：宁可少显示一行，也不能把最后一行裁掉
        assertEquals("chrome 常量要跟着布局改（实测 " + chromeReal + "dp）",
                chromeReal + 1, WidgetFactory.CHROME_HEIGHT_DP);
        assertEquals("并排行常量要跟着布局改（实测 " + pairReal3 + "dp）",
                pairReal3 + 1, WidgetFactory.PAIR_ROW_HEIGHT_DP);
        assertEquals("整行常量要跟着布局改（实测 " + fullReal + "dp）",
                fullReal + 1, WidgetFactory.FULL_ROW_HEIGHT_DP);

        // 被拖到最矮的时候用「极简行」：60dp 高也要能显示一条任务
        int shortDp = 60;
        int shortPx = Math.round(shortDp * density);
        views = com.lighttodo.app.widget.WidgetFactory.buildTodoList(context, shortDp, 240);
        root = apply(views);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(480,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(shortPx,
                        android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 480, shortPx);
        list = root.findViewById(R.id.w_list);
        assertNotNull(list);
        assertTrue("60dp 高的小组件应该也能显示一条任务", list.getChildCount() > 0);
        int tinyReal = dp(list.getChildAt(0).getMeasuredHeight(), density);
        android.view.View t0 = list.getChildAt(0);
        android.widget.TextView tt = t0.findViewById(R.id.w_item_title);
        System.out.println("DBG tiny child0 h=" + t0.getMeasuredHeight() + " kids="
                + ((android.view.ViewGroup) t0).getChildCount()
                + " textSize=" + (tt == null ? -1 : tt.getTextSize())
                + " listKids=" + list.getChildCount()
                + " listH=" + list.getMeasuredHeight());
        // 字体渲染在不同环境会有 1~2dp 抖动，常量取偏大的那个：
        // 常量必须 ≥ 实测值（不能裁行），且相差不超过 3dp（别浪费空间）。
        assertTrue("极简行常量不能小于实测值（实测 " + tinyReal
                        + "dp / 常量 " + WidgetFactory.TINY_ROW_HEIGHT_DP + "dp）",
                WidgetFactory.TINY_ROW_HEIGHT_DP >= tinyReal);
        assertTrue("极简行常量要跟着布局改（实测 " + tinyReal + "dp）",
                Math.abs(WidgetFactory.TINY_ROW_HEIGHT_DP - tinyReal) <= 3);

        // 底部不该空出一大片：任务区 + chrome 应该把组件高度占满
        int listReal = dp(list.getMeasuredHeight(), density);
        int chromeShort = dp(shortPx - list.getMeasuredHeight(), density);
        assertTrue("任务区应该至少能放下一条极简行（实测 " + listReal + "dp）",
                listReal >= WidgetFactory.TINY_ROW_HEIGHT_DP - 1);
        assertTrue("60dp 高的组件底部不该空出一大片（任务区 " + listReal + "dp / chrome "
                        + chromeShort + "dp）",
                shortDp - listReal - chromeShort <= 2);

        // 中等高度（90dp）用紧凑行
        int midDp = 90;
        int midPx = Math.round(midDp * density);
        views = com.lighttodo.app.widget.WidgetFactory.buildTodoList(context, midDp, 240);
        root = apply(views);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(480,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(midPx,
                        android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 480, midPx);
        list = root.findViewById(R.id.w_list);
        int compactReal = dp(list.getChildAt(0).getMeasuredHeight(), density);
        assertTrue("紧凑行常量不能小于实测值（实测 " + compactReal
                        + "dp / 常量 " + WidgetFactory.COMPACT_ROW_HEIGHT_DP + "dp）",
                WidgetFactory.COMPACT_ROW_HEIGHT_DP >= compactReal);
        assertTrue("紧凑行常量要跟着布局改（实测 " + compactReal + "dp）",
                Math.abs(WidgetFactory.COMPACT_ROW_HEIGHT_DP - compactReal) <= 3);
        assertTrue("紧凑模式 chrome 常量要跟着布局改（实测 " + chromeShort + "dp）",
                Math.abs(WidgetFactory.COMPACT_CHROME_HEIGHT_DP - chromeShort) <= 3);
    }

    /**
     * RemoteViews.apply 是 @hide 方法，用反射调起来做真实渲染测量。
     *
     * <p>注意：必须传<b>带 App 主题</b>的 context —— apply() 内部就是用这个 context 去 inflate 布局的。
     * 传 Application context 会用默认主题渲染，文字行高比真机多 5dp，测出来的数就不准了。</p>
     */
    private android.view.View apply(android.widget.RemoteViews views) {
        try {
            android.content.Context themed =
                    new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
            android.widget.FrameLayout parent = new android.widget.FrameLayout(themed);
            java.lang.reflect.Method m = android.widget.RemoteViews.class
                    .getDeclaredMethod("apply", Context.class, android.view.ViewGroup.class);
            m.setAccessible(true);
            return (android.view.View) m.invoke(views, themed, parent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** wrap_content 的行按自然高度测量；match_parent 的整块布局按固定高度测量。 */
    private void measure(android.view.View v) {
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(560,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800,
                        android.view.View.MeasureSpec.UNSPECIFIED));
    }

    private int marginTop(android.view.View v) {
        if (!(v.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams)) return 0;
        return ((android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin;
    }

    private int dp(int px, float density) {
        return Math.round(px / density);
    }

    /**
     * 高度排不满时，剩下的空间要被用起来 —— 内容要铺到任务区底部，
     * 而不是全部堆在顶部、下面空一大块（这正是用户说的「空间利用率不高」）。
     *
     * <p>实现方式：行与行之间插一条 3dp / 8dp 的弹性间隙，把多出来的空间吃掉。</p>
     */
    @Test
    public void 剩下的空间会铺满而不是堆在顶部() throws Exception {
        float density = context.getResources().getDisplayMetrics().density;

        // 先放几条任务，否则任务区是空的
        com.lighttodo.app.store.TodoStore.resetForTest(context);
        com.lighttodo.app.store.TodoStore store = com.lighttodo.app.store.TodoStore.get(context);
        for (com.lighttodo.app.model.Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
        com.lighttodo.app.model.Group g = store.addGroup("工作");
        store.addTask(g.id, "整理本周会议纪要", "");
        store.addTask(g.id, "把周报发到群里", "");
        store.addTask(g.id, "约客户确认方案", "");
        store.addTask(g.id, "回复邮件", "");
        store.addTask(g.id, "订机票", "");
        store.addTask(g.id, "写周报", "");
        store.addTask(g.id, "交房租", "");

        // 3 / 4 / 5 / 6 格都要铺满
        assertBottomFilled(144, 280, density);
        assertBottomFilled(192, 280, density);
        assertBottomFilled(240, 280, density);
        assertBottomFilled(288, 280, density);
    }

    /** 把任务区底部剩下的空白控制在 8dp 以内。 */
    private void assertBottomFilled(int heightDp, int widthDp, float density) {
        android.widget.RemoteViews views =
                com.lighttodo.app.widget.WidgetFactory.buildTodoList(context, heightDp, widthDp);
        android.view.View root = apply(views);
        int heightPx = Math.round(heightDp * density);
        int widthPx = Math.round(widthDp * density);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(widthPx,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx,
                        android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, widthPx, heightPx);

        android.widget.LinearLayout list = root.findViewById(R.id.w_list);
        assertNotNull("任务区没渲染出来", list);
        assertTrue(heightDp + "dp 高度应该有任务行", list.getChildCount() > 1);

        // 间隙不能是 0 高（那等于没插），也不能高到把内容顶出去
        int gapCount = 0;
        int gapDpSum = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            android.view.View v = list.getChildAt(i);
            if (v.getId() != R.id.w_gap) continue;   // 任务行
            gapCount++;
            int h = v.getMeasuredHeight();
            assertTrue("间隙高度应该大于 0，实际 " + h + "px", h > 0);
            assertTrue("间隙高度最多 62dp，实际 " + dp(h, density) + "dp",
                    h <= Math.round(62 * density));
            gapDpSum += dp(h, density);
        }
        if (list.getChildCount() > 2) {
            assertTrue("行与行之间应该插了间隙", gapCount > 0);
        }

        // 最后一行（间隙不算内容）应该贴近任务区底部
        android.view.View last = null;
        for (int i = list.getChildCount() - 1; i >= 0; i--) {
            android.view.View v = list.getChildAt(i);
            if (v.getId() == R.id.w_gap) continue;   // 跳过间隙
            last = v;
            break;
        }
        assertNotNull("没找到任务行", last);
        int gapPx = list.getMeasuredHeight() - last.getBottom();
        assertTrue(heightDp + "dp 高时底部却空着 " + gapPx + "px（"
                        + dp(gapPx, density) + "dp）",
                gapPx <= Math.round(8 * density));   // dp(px, density) 是 px→dp，这里要的是 px
    }
}
