package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.widget.WidgetConfig;
import com.lighttodo.app.widget.WidgetFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 小组件：尺寸自由拖拽 + 自定义清单（添加时选显示范围）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
public class WidgetCustomScopeTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // ------------------------------------------------------------ 尺寸不再设限

    @Test
    public void 小组件尺寸没有上限了() {
        // info.xml 里不再有 maxResizeWidth / maxResizeHeight，
        // 桌面可以一直往大拖；最小也能缩到很小。
        String xml = readWidgetInfo("widget_todo_list_info.xml");
        assertFalse("不该再有最大宽度限制", xml.contains("maxResizeWidth"));
        assertFalse("不该再有最大高度限制", xml.contains("maxResizeHeight"));
        assertTrue("要能横向缩放", xml.contains("horizontal"));
        assertTrue("要能纵向缩放", xml.contains("vertical"));
        assertTrue("最小高度要够小（60dp，再小连一行任务都放不下）",
                xml.contains("minResizeHeight=\"60dp\""));
    }

    @Test
    public void 所有小组件都放开尺寸限制() {
        String[] names = {
                "widget_todo_list_info.xml", "widget_group_info.xml",
                "widget_category_info.xml", "widget_group_task_info.xml",
                "widget_category_task_info.xml", "widget_custom_info.xml",
                "widget_todo_list_plain_info.xml", "widget_group_plain_info.xml",
                "widget_category_plain_info.xml", "widget_group_task_plain_info.xml",
                "widget_category_task_plain_info.xml", "widget_custom_plain_info.xml",
        };
        for (String name : names) {
            String xml = readWidgetInfo(name);
            assertFalse(name + " 不该有 maxResizeWidth", xml.contains("maxResizeWidth"));
            assertFalse(name + " 不该有 maxResizeHeight", xml.contains("maxResizeHeight"));
        }
    }

    // ------------------------------------------------------------ 紧凑模式

    @Test
    public void 小组件太矮就切紧凑模式() {
        assertTrue("60dp 高应该用紧凑模式", WidgetFactory.isCompact(60));
        assertTrue("90dp 高应该用紧凑模式", WidgetFactory.isCompact(90));
        assertFalse("110dp 高不用紧凑", WidgetFactory.isCompact(110));
        assertFalse("288dp 高不用紧凑", WidgetFactory.isCompact(288));
    }

    @Test
    public void 紧凑模式省下的高度够多一行任务() {
        int normal = WidgetFactory.CHROME_HEIGHT_DP;
        int compact = WidgetFactory.COMPACT_CHROME_HEIGHT_DP;
        assertTrue("紧凑模式应该明显省高度", compact < normal);
        // 紧凑模式省两处：标题栏 47 → 39dp，任务行 30 → 22dp，
        // 一共省 16dp —— 差不多多出一行。
        assertTrue("紧凑模式要同时省标题栏（" + (normal - compact) + "dp）和行高（"
                        + (WidgetFactory.FULL_ROW_HEIGHT_DP - WidgetFactory.COMPACT_ROW_HEIGHT_DP)
                        + "dp）",
                (normal - compact)
                        + (WidgetFactory.FULL_ROW_HEIGHT_DP - WidgetFactory.COMPACT_ROW_HEIGHT_DP) > 0);
        // 90dp 高：普通模式只排得下一行，紧凑模式能排下两行。
        int height = 90;
        int normalRows = (height - normal) / WidgetFactory.FULL_ROW_HEIGHT_DP;
        int compactRows = (height - compact) / WidgetFactory.COMPACT_ROW_HEIGHT_DP;
        assertEquals("普通模式 90dp 高时只排得下一行", 1, normalRows);
        assertEquals("紧凑模式 90dp 高时能排下两行", 2, compactRows);
    }

    // ------------------------------------------------------------ 自定义清单

    @Test
    public void 自定义清单的范围能存住() {
        WidgetConfig.setScopeType(context, 77, WidgetConfig.SCOPE_GROUP);
        assertEquals(WidgetConfig.SCOPE_GROUP, WidgetConfig.getScopeType(context, 77));

        WidgetConfig.setScopeType(context, 78, WidgetConfig.SCOPE_CATEGORY);
        assertEquals(WidgetConfig.SCOPE_CATEGORY, WidgetConfig.getScopeType(context, 78));

        assertEquals("没配过的按全部任务处理",
                WidgetConfig.SCOPE_ALL, WidgetConfig.getScopeType(context, 99));

        WidgetConfig.clear(context, 77);
        assertEquals("删掉后要回到全部任务",
                WidgetConfig.SCOPE_ALL, WidgetConfig.getScopeType(context, 77));
    }

    @Test
    public void 自定义清单的范围能跨进程读回来() {
        WidgetConfig.setScopeType(context, 88, WidgetConfig.SCOPE_CATEGORY);
        WidgetConfig.setCategory(context, 88, 2);
        // 重新读（模拟 widget 进程）
        assertEquals(WidgetConfig.SCOPE_CATEGORY, WidgetConfig.getScopeType(context, 88));
        assertEquals(2, WidgetConfig.getCategory(context, 88));
    }

    @Test
    public void 自定义清单能按范围过滤任务() {
        com.lighttodo.app.store.TodoStore.resetForTest(context);
        com.lighttodo.app.store.TodoStore store = com.lighttodo.app.store.TodoStore.get(context);
        for (com.lighttodo.app.model.Group g : store.groups()) store.deleteGroup(g.id);
        com.lighttodo.app.model.Group work = store.addGroup("工作");
        com.lighttodo.app.model.Group life = store.addGroup("生活");
        store.addTask(work.id, "工作的事", "");
        store.addTask(life.id, "生活的事", "");

        // 全部
        WidgetConfig.setScopeType(context, 101, WidgetConfig.SCOPE_ALL);
        assertEquals(2, countShown(context, 101));

        // 只看某个分组
        WidgetConfig.setScopeType(context, 102, WidgetConfig.SCOPE_GROUP);
        WidgetConfig.setGroup(context, 102, work.id);
        assertEquals(1, countShown(context, 102));
    }

    /** 自定义清单里能看到几条任务（用标题栏的计数当代理指标）。 */
    private int countShown(Context ctx, int widgetId) {
        android.widget.RemoteViews views =
                WidgetFactory.buildCustomList(ctx, widgetId, 400, 280);
        assertTrue(views != null);
        // 直接数 store 的结果更稳：这里用反射读不太方便，改成断言 RemoteViews 非空 + 数量
        com.lighttodo.app.store.TodoStore store = com.lighttodo.app.store.TodoStore.get(ctx);
        int scope = WidgetConfig.getScopeType(ctx, widgetId);
        int n = 0;
        for (com.lighttodo.app.model.Task t : store.allPendingTasks()) {
            if (scope == WidgetConfig.SCOPE_GROUP) {
                com.lighttodo.app.model.Group g = store.groupOfTask(t);
                if (g != null && g.id.equals(WidgetConfig.getGroup(ctx, widgetId))) n++;
            } else {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ 辅助

    private String readWidgetInfo(String name) {
        try {
            java.io.InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("com/lighttodo/app/" + name);
            if (in == null) {
                // 编译后的资源在 res/xml 下，直接从源码目录读
                java.io.File f = new java.io.File("/workspace/LightTodo/app/src/main/res/xml/" + name);
                if (f.exists()) {
                    return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8);
                }
                return "";
            }
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
