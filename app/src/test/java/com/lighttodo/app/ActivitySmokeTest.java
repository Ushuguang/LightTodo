package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

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

/** 冒烟测试：两个界面都能正常创建，列表能正确渲染。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActivitySmokeTest {

    @Before
    public void setUp() {
        TodoStore.resetForTest(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void 主界面能正常打开并列出分组() {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        store.addGroup("工作");
        store.addGroup("生活");

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            RecyclerView list = activity.findViewById(R.id.list);
            assertNotNull(list);
            assertNotNull(list.getAdapter());
            assertEquals("首页应显示 2 个分组", 2, list.getAdapter().getItemCount());
        }
    }

    @Test
    public void 分组界面能正常打开并列出任务() {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        Group g = store.addGroup("工作");
        store.addTask(g.id, "任务一", "");
        store.addTask(g.id, "任务二", "");
        Task third = store.addTask(g.id, "任务三", "");
        store.setTaskDone(g.id, third.id, true);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, g.id);

        try (ActivityController<GroupActivity> controller =
                     Robolectric.buildActivity(GroupActivity.class, intent)) {
            GroupActivity activity = controller.setup().get();
            RecyclerView list = activity.findViewById(R.id.list);
            assertNotNull(list);
            assertNotNull(list.getAdapter());
            // 3 条任务 + 1 个「已完成」分隔标题
            assertEquals("列表应有 4 行", 4, list.getAdapter().getItemCount());
        }
    }

    @Test
    public void 空数据时界面也能正常打开() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            RecyclerView list = activity.findViewById(R.id.list);
            assertEquals(0, list.getAdapter().getItemCount());
            assertTrue(activity.findViewById(R.id.empty).getVisibility() == android.view.View.VISIBLE);
        }
    }
}
