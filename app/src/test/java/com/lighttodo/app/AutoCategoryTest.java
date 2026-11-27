package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.AutoCategoryStep;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「自动变分类」：到点自动把任务改成别的分类，可以排成链条依次生效。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
public class AutoCategoryTest {

    private Context context;
    private TodoStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        TodoStore.resetForTest(context);
        store = TodoStore.get(context);
        for (Group g : store.groups()) store.deleteGroup(g.id);
    }

    @Test
    public void 时间点链条能存住也能读回来() {
        Group g = store.addGroup("工作");
        Task task = store.addTask(g.id, "作业", "", 0L, 0L, false, false);
        assertNotNull(task);

        long day = 24 * 3600_000L;
        long now = System.currentTimeMillis();
        List<AutoCategoryStep> steps = new ArrayList<>();
        steps.add(AutoCategoryStep.of(now + 3 * day, Category.IMPORTANT_URGENT));
        steps.add(AutoCategoryStep.of(now + 5 * day, Category.IMPORTANT_NOT_URGENT));
        assertTrue(store.setAutoSteps(g.id, task.id, steps));

        Task saved = store.findGroup(g.id).findTask(task.id);
        assertNotNull(saved);
        assertEquals("两个时间点都要存住", 2, saved.autoSteps.size());
        // 按时间先后排好了
        assertTrue(saved.autoSteps.get(0).at < saved.autoSteps.get(1).at);
        assertEquals(Category.IMPORTANT_URGENT, saved.autoSteps.get(0).category());
        assertEquals(Category.IMPORTANT_NOT_URGENT, saved.autoSteps.get(1).category());
        // 下一个时间点 = 最近的那个
        assertEquals(saved.autoSteps.get(0).at, saved.nextAutoAt());
    }

    @Test
    public void 到点会自动改成分类() {
        Group g = store.addGroup("工作");
        Task task = store.addTask(g.id, "作业", "", 0L, 0L, false, false);
        assertNotNull(task);
        long now = System.currentTimeMillis();

        store.setAutoSteps(g.id, task.id, Arrays.asList(
                AutoCategoryStep.of(now - 1000, Category.IMPORTANT_URGENT)));

        int applied = store.applyDueAutoSteps(g.id, task.id, now);
        assertEquals("应该改一次", 1, applied);
        Task after = store.findGroup(g.id).findTask(task.id);
        assertEquals("已经变成重要紧急了", Category.IMPORTANT_URGENT, Category.of(after));
        assertTrue("执行过的点要清掉", after.autoSteps.isEmpty());
    }

    @Test
    public void 链条按时间依次生效最后一次说了算() {
        Group g = store.addGroup("工作");
        Task task = store.addTask(g.id, "作业", "", 0L, 0L, false, false);
        assertNotNull(task);
        long now = System.currentTimeMillis();
        long day = 24 * 3600_000L;

        // 两个点都过期了（模拟关机错过、之后一次性补做）
        store.setAutoSteps(g.id, task.id, Arrays.asList(
                AutoCategoryStep.of(now - 2 * day, Category.IMPORTANT_URGENT),
                AutoCategoryStep.of(now - day, Category.NOT_IMPORTANT_URGENT)));

        int applied = store.applyDueAutoSteps(g.id, task.id, now);
        assertEquals("两个点都执行", 2, applied);
        Task after = store.findGroup(g.id).findTask(task.id);
        assertEquals("以最后一个为准", Category.NOT_IMPORTANT_URGENT, Category.of(after));
        assertTrue(after.autoSteps.isEmpty());
    }

    @Test
    public void 没到点的不动() {
        Group g = store.addGroup("工作");
        Task task = store.addTask(g.id, "作业", "", 0L, 0L, false, false);
        assertNotNull(task);
        long now = System.currentTimeMillis();

        store.setAutoSteps(g.id, task.id, Arrays.asList(
                AutoCategoryStep.of(now + 3600_000L, Category.IMPORTANT_URGENT)));

        assertEquals("还没到点就不该改", 0, store.applyDueAutoSteps(g.id, task.id, now));
        Task after = store.findGroup(g.id).findTask(task.id);
        assertEquals("分类不变", Category.NOT_IMPORTANT_NOT_URGENT, Category.of(after));
        assertEquals("时间点还留着", 1, after.autoSteps.size());
    }

    @Test
    public void 链条会留在任务里跟着数据一起存() {
        Group g = store.addGroup("工作");
        Task task = store.addTask(g.id, "作业", "", 0L, 0L, false, false);
        assertNotNull(task);
        long now = System.currentTimeMillis();

        store.setAutoSteps(g.id, task.id, Arrays.asList(
                AutoCategoryStep.of(now + 3600_000L, Category.IMPORTANT_URGENT)));

        // 重新从磁盘读一遍，确认真的写进 todos.json 了
        TodoStore reloaded = TodoStore.get(context);
        Task after = reloaded.findGroup(g.id).findTask(task.id);
        assertNotNull(after);
        assertEquals("存盘后时间点还在", 1, after.autoSteps.size());
        assertEquals(Category.IMPORTANT_URGENT, after.autoSteps.get(0).category());
    }

    @Test
    public void 清空链条就是取消自动变分类() {
        Group g = store.addGroup("工作");
        Task task = store.addTask(g.id, "作业", "", 0L, 0L, false, false);
        assertNotNull(task);
        long now = System.currentTimeMillis();

        store.setAutoSteps(g.id, task.id, Arrays.asList(
                AutoCategoryStep.of(now + 3600_000L, Category.IMPORTANT_URGENT)));
        store.setAutoSteps(g.id, task.id, new ArrayList<>());

        Task after = store.findGroup(g.id).findTask(task.id);
        assertTrue("清空后不该还有时间点", after.autoSteps.isEmpty());
        assertEquals(0L, after.nextAutoAt());
    }
}
