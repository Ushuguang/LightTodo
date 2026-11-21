package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * 核心行为验证：
 * 1. 完成任务后任务沉底（移到列表最后）；
 * 2. 任务全部完成后分组依然存在，不会被清掉；
 * 3. 数据落盘后能重新读回来。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TodoStoreTest {

    private Context context;
    private TodoStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        TodoStore.resetForTest(context);
        store = TodoStore.get(context);
        // 每个用例从干净数据开始
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    @Test
    public void 完成任务后任务移到最后且分组不消失() {
        Group g = store.addGroup("工作");
        store.addTask(g.id, "任务A", "");
        store.addTask(g.id, "任务B", "");
        store.addTask(g.id, "任务C", "");

        store.setTaskDone(g.id, g.tasks.get(0).id, true);

        Group after = store.findGroup(g.id);
        assertNotNull("分组不能消失", after);
        assertEquals("分组里任务数量不变", 3, after.tasks.size());

        List<Task> sorted = after.sortedTasks();
        assertEquals("任务B", sorted.get(0).title);
        assertEquals("任务C", sorted.get(1).title);
        assertEquals("已完成的任务沉到最后", "任务A", sorted.get(2).title);
        assertTrue(sorted.get(2).done);
        assertEquals("未完成计数正确", 2, after.pendingCount());
        assertEquals("已完成计数正确", 1, after.doneCount());
    }

    @Test
    public void 全部完成后分组仍然保留() {
        Group g = store.addGroup("生活");
        Task t = store.addTask(g.id, "买牛奶", "");
        store.setTaskDone(g.id, t.id, true);

        Group after = store.findGroup(g.id);
        assertNotNull("全做完也不能删分组", after);
        assertEquals(1, after.tasks.size());
        assertEquals(0, after.pendingCount());
        assertEquals(1, after.doneCount());
    }

    @Test
    public void 取消完成后任务回到未完成区() {
        Group g = store.addGroup("学习");
        store.addTask(g.id, "先做", "");
        Task second = store.addTask(g.id, "后做", "");

        store.setTaskDone(g.id, second.id, true);
        assertEquals("后做", g.sortedTasks().get(1).title);

        store.setTaskDone(g.id, second.id, false);
        List<Task> sorted = g.sortedTasks();
        assertEquals("先做", sorted.get(0).title);
        assertEquals("后做", sorted.get(1).title);
        assertFalse(sorted.get(1).done);
    }

    @Test
    public void 数据写入文件且能读回() throws Exception {
        Group g = store.addGroup("持久化");
        store.addTask(g.id, "记得留存的标题", "备注内容");

        java.io.File file = new java.io.File(context.getFilesDir(), "todos.json");
        assertTrue("todos.json 应该存在", file.exists());

        String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue(text.contains("记得留存的标题"));
        assertTrue(text.contains("备注内容"));
    }

    @Test
    public void 统计数字正确() {
        Group g = store.addGroup("统计");
        store.addTask(g.id, "一", "");
        store.addTask(g.id, "二", "");
        store.addTask(g.id, "三", "");
        store.setTaskDone(g.id, g.tasks.get(2).id, true);

        assertEquals(1, store.groupCount());
        assertEquals(3, store.totalTasks());
        assertEquals(2, store.totalPending());
        assertEquals(2, store.allPendingTasks().size());
    }
}
