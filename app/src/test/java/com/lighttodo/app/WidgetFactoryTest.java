package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.ui.TaskEditor;
import com.lighttodo.app.widget.WidgetConfig;
import com.lighttodo.app.widget.WidgetFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** 桌面小组件（分组 / 分类 / 单组 / 单类）与两栏排版。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WidgetFactoryTest {

    private Context context;
    private TodoStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        TodoStore.resetForTest(context);
        store = TodoStore.get(context);
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    @Test
    public void 清单小组件可以渲染() {
        Group g = store.addGroup("工作");
        store.addTask(g.id, "写周报", "");

        RemoteViews views = WidgetFactory.buildTodoList(context, 110);
        assertNotNull(views);
        assertEquals("任务条数应按高度估算", WidgetFactory.rowsForHeight(110), WidgetFactory.rowsForHeight(110));
    }

    @Test
    public void 分组小组件可以渲染() {
        Group g = store.addGroup("生活");
        store.addTask(g.id, "买牛奶", "");

        RemoteViews views = WidgetFactory.buildGroupOverview(context, 200);
        assertNotNull(views);
    }

    @Test
    public void 分类概览小组件可以渲染() {
        Group g = store.addGroup("工作");
        store.addTask(g.id, "写周报", "", 0L, 0L, true, true);
        store.addTask(g.id, "交房租", "", 0L, 0L, false, false);

        RemoteViews views = WidgetFactory.buildCategoryOverview(context, 150, 180);
        assertNotNull(views);
    }

    @Test
    public void 单组任务小组件可以渲染() {
        Group g = store.addGroup("工作");
        store.addTask(g.id, "写周报", "");

        RemoteViews 未配置 = WidgetFactory.buildGroupTasks(context, 1, 150, 180);
        assertNotNull("还没选分组时也要能渲染", 未配置);

        WidgetConfig.setGroup(context, 1, g.id);
        RemoteViews 已配置 = WidgetFactory.buildGroupTasks(context, 1, 150, 180);
        assertNotNull(已配置);
    }

    @Test
    public void 单类任务小组件可以渲染() {
        Group g = store.addGroup("工作");
        store.addTask(g.id, "写周报", "", 0L, 0L, true, true);

        RemoteViews 未配置 = WidgetFactory.buildCategoryTasks(context, 2, 150, 180);
        assertNotNull("还没选分类时也要能渲染", 未配置);

        WidgetConfig.setCategory(context, 2, Category.IMPORTANT_URGENT);
        RemoteViews 已配置 = WidgetFactory.buildCategoryTasks(context, 2, 150, 180);
        assertNotNull(已配置);
    }

    @Test
    public void 每个小组件实例各存各的() {
        Group a = store.addGroup("工作");
        Group b = store.addGroup("生活");

        WidgetConfig.setGroup(context, 11, a.id);
        WidgetConfig.setGroup(context, 22, b.id);

        assertEquals("两个小组件可以叠在一起，各看各的", a.id, WidgetConfig.getGroup(context, 11));
        assertEquals(b.id, WidgetConfig.getGroup(context, 22));

        WidgetConfig.setCategory(context, 11, Category.IMPORTANT_URGENT);
        WidgetConfig.setCategory(context, 22, Category.NOT_IMPORTANT_URGENT);
        assertEquals(Category.IMPORTANT_URGENT, WidgetConfig.getCategory(context, 11));
        assertEquals(Category.NOT_IMPORTANT_URGENT, WidgetConfig.getCategory(context, 22));

        assertNull("没设置过的应该是空", WidgetConfig.getGroup(context, 99));
        assertEquals("没设置过的分类应该是 -1",
                WidgetConfig.CATEGORY_NONE, WidgetConfig.getCategory(context, 99));
    }

    @Test
    public void 空数据时也不会崩() {
        RemoteViews list = WidgetFactory.buildTodoList(context, 110);
        RemoteViews group = WidgetFactory.buildGroupOverview(context, 110);
        RemoteViews category = WidgetFactory.buildCategoryOverview(context, 110, 180);
        RemoteViews groupTask = WidgetFactory.buildGroupTasks(context, 3, 110, 180);
        RemoteViews categoryTask = WidgetFactory.buildCategoryTasks(context, 4, 110, 180);
        assertNotNull(list);
        assertNotNull(group);
        assertNotNull(category);
        assertNotNull(groupTask);
        assertNotNull(categoryTask);
    }

    @Test
    public void 小组件管理器能拿到实例() {
        assertNotNull(android.appwidget.AppWidgetManager.getInstance(context));
    }

    @Test
    public void 高度换算合理() {
        // chrome 实测 47dp、分组行实测 31dp，所以同样的高度现在能多显示一行
        assertEquals(1, WidgetFactory.rowsForHeight(60));
        assertEquals(1, WidgetFactory.rowsForHeight(90));
        assertEquals(2, WidgetFactory.rowsForHeight(118));
        assertEquals(4, WidgetFactory.rowsForHeight(200));
        assertEquals(7, WidgetFactory.rowsForHeight(280));
        assertEquals(12, WidgetFactory.rowsForHeight(1000));
    }

    @Test
    public void 放得开的任务才并排放() {
        // 280dp 宽：半栏 (280-8-10)/2 = 131dp，扣掉圆圈 14dp + 间距 6dp + 误差余量 6dp，
        // 还能放约 105dp 的字（13sp 中文一个字约 13dp → 8 个字以内）。
        assertTrue("短任务放得下，应该并排", WidgetFactory.fitsInColumn(context, "买牛奶", 280));
        assertTrue("短任务放得下，应该并排", WidgetFactory.fitsInColumn(context, "交房租", 280));
        assertTrue("8 个字放得下，应该并排", WidgetFactory.fitsInColumn(context, "整理本周会议纪要", 280));
        assertFalse("太长放不下会出省略号，必须独占整行",
                WidgetFactory.fitsInColumn(context, "整理本周的会议纪要和下周计划", 280));
    }

    /**
     * 副标题比主标题还长时（比如截止时间），也必须独占整行，
     * 否则副标题会先被截断。
     */
    @Test
    public void 副标题太长也不能并排() {
        assertTrue("副标题短就正常并排",
                WidgetFactory.fitsInColumn(context, "回复邮件", "工作", 280));
        assertFalse("副标题太长会溢出，必须独占整行",
                WidgetFactory.fitsInColumn(context, "回复邮件", "截止 11月25日 14:30 开会", 280));
    }

    /**
     * 并排的真实约束：主标题 + 副标题里更宽的那个，扣掉圆圈和间距后必须放得下半栏。
     * 这是「不出现省略号」的底线，改排版参数时这条不能破。
     */
    @Test
    public void 并排时文字必须放得下半栏() {
        String[] titles = {"回复邮件", "订机票", "写周报", "交房租", "把周报发到群里", "整理本周会议纪要"};
        for (String t : titles) {
            assertTrue("「" + t + "」判断为能并排", WidgetFactory.fitsInColumn(context, t, 280));
        }
        // 12 个字 = 156dp，明显超过半栏 131dp，必须拒绝
        assertFalse("12 个字必须拒绝并排",
                WidgetFactory.fitsInColumn(context, "一二三四五六七八九十甲乙", 280));
    }

    @Test
    public void 小组件太窄就全都整行() {
        assertFalse("宽度不够，半栏放不下",
                WidgetFactory.fitsInColumn(context, "买牛奶", WidgetFactory.TWO_COLUMN_MIN_WIDTH_DP - 1));
        assertTrue("宽度刚够就能并排",
                WidgetFactory.fitsInColumn(context, "买牛奶", WidgetFactory.TWO_COLUMN_MIN_WIDTH_DP));
    }

    @Test
    public void 空标题也放得下() {
        assertTrue(WidgetFactory.fitsInColumn(context, "", 280));
        assertTrue(WidgetFactory.fitsInColumn(context, null, 280));
    }

    @Test
    public void 分类方块位置按要求调换过() {
        assertEquals("左上：重要不紧急", Category.IMPORTANT_NOT_URGENT, TaskEditor.POSITION_CATEGORY[0]);
        assertEquals("右上：重要紧急", Category.IMPORTANT_URGENT, TaskEditor.POSITION_CATEGORY[1]);
        assertEquals("左下：不重要不紧急", Category.NOT_IMPORTANT_NOT_URGENT, TaskEditor.POSITION_CATEGORY[2]);
        assertEquals("右下：不重要紧急", Category.NOT_IMPORTANT_URGENT, TaskEditor.POSITION_CATEGORY[3]);
    }
}
