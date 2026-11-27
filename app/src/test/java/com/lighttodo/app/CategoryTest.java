package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** 四象限分类的行为验证。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CategoryTest {

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
    public void 分类映射正确() {
        assertEquals(Category.IMPORTANT_URGENT, Category.of(true, true));
        assertEquals(Category.IMPORTANT_NOT_URGENT, Category.of(true, false));
        assertEquals(Category.NOT_IMPORTANT_URGENT, Category.of(false, true));
        assertEquals(Category.NOT_IMPORTANT_NOT_URGENT, Category.of(false, false));
    }

    @Test
    public void 分类会跟着任务存盘() {
        Group g = store.addGroup("工作");
        Task t = store.addTask(g.id, "写方案", "", 0L, 0L, true, true);

        Task after = store.findGroup(g.id).findTask(t.id);
        assertTrue(after.important);
        assertTrue(after.urgent);
        assertEquals(Category.IMPORTANT_URGENT, Category.of(after));
    }

    @Test
    public void 各分类未完成数统计正确() {
        Group g = store.addGroup("生活");
        store.addTask(g.id, "甲", "", 0L, 0L, true, true);    // 重要紧急
        store.addTask(g.id, "乙", "", 0L, 0L, true, false);   // 重要不紧急
        store.addTask(g.id, "丙", "", 0L, 0L, false, true);   // 不重要紧急
        store.addTask(g.id, "丁", "", 0L, 0L, false, false);  // 不重要不紧急
        Task done = store.addTask(g.id, "戊", "", 0L, 0L, true, true);
        store.setTaskDone(g.id, done.id, true);

        int[] counts = store.categoryPendingCounts();
        assertEquals(1, counts[Category.IMPORTANT_URGENT]);
        assertEquals(1, counts[Category.IMPORTANT_NOT_URGENT]);
        assertEquals(1, counts[Category.NOT_IMPORTANT_URGENT]);
        assertEquals(1, counts[Category.NOT_IMPORTANT_NOT_URGENT]);
    }

    @Test
    public void 能按分类找到任务所属分组() {
        Group g1 = store.addGroup("工作");
        Group g2 = store.addGroup("生活");
        Task t = store.addTask(g2.id, "买牛奶", "", 0L, 0L, false, true);

        Group found = store.groupOfTask(t);
        assertNotNull(found);
        assertEquals("生活", found.name);
        assertEquals(g2.id, found.id);
    }

    @Test
    public void 分类页签能列出全部任务() {
        Group g = store.addGroup("工作");
        store.addTask(g.id, "甲", "", 0L, 0L, true, true);
        store.addTask(g.id, "乙", "", 0L, 0L, true, false);
        store.addTask(g.id, "丙", "", 0L, 0L, false, true);
        store.addTask(g.id, "丁", "", 0L, 0L, false, false);

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            assertEquals("底部导航四个入口：分组 / 分类 / 题目 / 设置", 4, nav.getMenu().size());

            nav.setSelectedItemId(R.id.nav_categories);

            RecyclerView categoryList = activity.findViewById(R.id.category_list);
            assertNotNull(categoryList.getAdapter());
            // 4 个象限表头 + 4 条任务
            assertEquals(8, categoryList.getAdapter().getItemCount());
            assertEquals(android.view.View.VISIBLE,
                    categoryList.getVisibility());
        }
    }

    @Test
    public void 已完成的任务不出现在分类页() {
        Group g = store.addGroup("工作");
        Task done = store.addTask(g.id, "已完成的", "", 0L, 0L, true, true);
        store.addTask(g.id, "还没做的", "", 0L, 0L, true, true);
        store.toggleTask(g.id, done.id);

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    activity.findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(R.id.nav_categories);

            RecyclerView categoryList = activity.findViewById(R.id.category_list);
            assertNotNull(categoryList.getAdapter());
            // 4 个象限表头 + 1 条未完成的任务
            assertEquals("已完成的任务不该再出现在分类页里",
                    5, categoryList.getAdapter().getItemCount());
        }
    }

    @Test
    public void 分类视图里完成任务会同步到分类() {
        Group g = store.addGroup("工作");
        Task t = store.addTask(g.id, "甲", "", 0L, 0L, true, true);

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(R.id.nav_categories);

            RecyclerView categoryList = activity.findViewById(R.id.category_list);
            // 表头(0) + 任务(1)
            RecyclerView.ViewHolder holder = categoryList.getAdapter().createViewHolder(
                    categoryList, categoryList.getAdapter().getItemViewType(1));
            categoryList.getAdapter().bindViewHolder(holder, 1);
            holder.itemView.findViewById(R.id.t_check).performClick();

            assertTrue(store.findGroup(g.id).findTask(t.id).done);
        }
    }

    @Test
    public void 默认分类是不重要不紧急() {
        Group g = store.addGroup("工作");
        Task t = store.addTask(g.id, "随手记", "");
        assertFalse(t.important);
        assertFalse(t.urgent);
        assertEquals(Category.NOT_IMPORTANT_NOT_URGENT, Category.of(t));
    }

    @Test
    public void 任务弹窗能正常打开且有四个分类按钮() {
        Group g = store.addGroup("工作");
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            com.lighttodo.app.ui.TaskEditor.show(activity, g.id, null, null);

            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("应该弹出了任务编辑框", dialog);
            android.view.View view = dialog.findViewById(android.R.id.content);
            assertNotNull("弹窗内容应已渲染", view);
            assertNotNull("四个分类按钮都要在",
                    view.findViewById(R.id.cat_0));
            assertNotNull(view.findViewById(R.id.cat_1));
            assertNotNull(view.findViewById(R.id.cat_2));
            assertNotNull(view.findViewById(R.id.cat_3));
        }
    }
}
