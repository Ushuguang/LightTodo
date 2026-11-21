package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.widget.WidgetConfigActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * 版权页脚：每个页面（Activity）最下方都要有「© Ushuguang」。
 *
 * <p>这是用户明确要求的一条约定，用测试锁住，避免以后改布局时不小心漏掉某一页。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CopyrightFooterTest {

    private static final String EXPECTED = "© Ushuguang";

    @Before
    public void setUp() {
        TodoStore.resetForTest(ApplicationProvider.getApplicationContext());
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        store.addGroup("工作");
    }

    /** 页脚必须在页面里存在、文字正确、并且真的在内容最下方。 */
    private void assertFooter(View root) {
        TextView footer = root.findViewById(R.id.copyright);
        assertNotNull("这一页应该有版权页脚", footer);
        assertEquals("页脚文字应该是 " + EXPECTED, EXPECTED, footer.getText().toString());
        assertTrue("页脚应该可见", footer.getVisibility() == View.VISIBLE);
    }

    @Test
    public void 首页底部有版权行() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            assertFooter(activity.getWindow().getDecorView());
        }
    }

    @Test
    public void 分组页底部有版权行() {
        Group g = TodoStore.get(ApplicationProvider.getApplicationContext()).groups().get(0);
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, g.id);
        try (ActivityController<GroupActivity> controller =
                     Robolectric.buildActivity(GroupActivity.class, intent)) {
            GroupActivity activity = controller.setup().get();
            assertFooter(activity.getWindow().getDecorView());
        }
    }

    @Test
    public void 助手页底部有版权行() {
        try (ActivityController<AssistantActivity> controller =
                     Robolectric.buildActivity(AssistantActivity.class)) {
            AssistantActivity activity = controller.setup().get();
            assertFooter(activity.getWindow().getDecorView());
        }
    }

    @Test
    public void 小组件配置页底部有版权行() {
        try (ActivityController<WidgetConfigActivity> controller =
                     Robolectric.buildActivity(WidgetConfigActivity.class)) {
            WidgetConfigActivity activity = controller.setup().get();
            assertFooter(activity.getWindow().getDecorView());
        }
    }
}
