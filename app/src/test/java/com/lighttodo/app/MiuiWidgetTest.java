package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Category;
import com.lighttodo.app.util.MiuiWidget;
import com.lighttodo.app.widget.WidgetConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 小米小部件（MIUI Widget）适配的回归测试。
 *
 * <p>覆盖三件事：manifest 里的标识有没有写对、布局根节点是不是官方要求的固定 ID、
 * 小组件配置改存文件后还能不能正常读写。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MiuiWidgetTest {

    private Context context;
    private PackageManager pm;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        pm = context.getPackageManager();
    }

    // ------------------------------------------------------------ manifest 标识

    @Test
    public void 每个小组件都带小米标识() {
        String[] classes = {
                "com.lighttodo.app.widget.TodoListWidgetProvider",
                "com.lighttodo.app.widget.GroupWidgetProvider",
                "com.lighttodo.app.widget.CategoryWidgetProvider",
                "com.lighttodo.app.widget.GroupTaskWidgetProvider",
                "com.lighttodo.app.widget.CategoryTaskWidgetProvider",
        };
        for (String cls : classes) {
            android.content.pm.ActivityInfo info = receiverInfo(cls);
            assertNotNull("找不到 receiver：" + cls, info);
            Bundle meta = info.metaData;
            assertNotNull(cls + " 缺少 meta-data", meta);
            assertEquals(cls + " 缺少 miuiWidget 标识", Boolean.TRUE,
                    meta.getBoolean("miuiWidget"));
            assertEquals(cls + " 曝光刷新没开", "exposure",
                    meta.getString("miuiWidgetRefresh"));
            assertTrue(cls + " 曝光刷新最小间隔没设",
                    meta.getInt("miuiWidgetRefreshMinInterval", 0) >= 10000);
        }
    }

    @Test
    public void 小组件跑在独立进程里() {
        String[] classes = {
                "com.lighttodo.app.widget.TodoListWidgetProvider",
                "com.lighttodo.app.widget.GroupWidgetProvider",
                "com.lighttodo.app.widget.CategoryWidgetProvider",
                "com.lighttodo.app.widget.GroupTaskWidgetProvider",
                "com.lighttodo.app.widget.CategoryTaskWidgetProvider",
        };
        for (String cls : classes) {
            android.content.pm.ActivityInfo info = receiverInfo(cls);
            assertNotNull(cls, info);
            assertNotNull(cls, info.applicationInfo);
            assertNotNull(cls + " 拿不到进程名", info.processName);
            assertTrue(cls + " 必须跑在 :widgetProvider 独立进程，实际是 " + info.processName,
                    info.processName.endsWith(MiuiWidget.WIDGET_PROCESS));
        }
    }

    @Test
    public void 曝光刷新广播已注册() {
        // 系统/小米都会用这个 action 触发刷新，注册了才收得到
        Intent probe = new Intent(MiuiWidget.ACTION_EXPOSURE_REFRESH)
                .setPackage(context.getPackageName());
        assertTrue("小米曝光刷新广播没有注册",
                pm.queryBroadcastReceivers(probe, 0).size() > 0);
    }

    @Test
    public void 应用层声明了小部件版本号() {
        // 小米规范：版本号必须放在 application 下（不是 provider 下），且只能升高
        assertTrue("小部件版本号必须大于 0",
                context.getResources().getInteger(R.integer.miui_widget_version) > 0);
    }

    @Test
    public void 能力探测在不支持的机器上不会崩() {
        // 沙箱里没有小米那个 provider，必须安全返回 false 而不是抛异常
        assertFalse(MiuiWidget.isSupported(context));
        assertFalse(MiuiWidget.isDetailPageSupported(context));
    }

    // ------------------------------------------------------------ 布局规范

    @Test
    public void 布局根节点是官方要求的固定ID() {
        // 小米靠 @android:id/background 统一裁圆角，根节点 ID 写错就裁不出圆角
        int[] layouts = {
                R.layout.widget_task_list,
                R.layout.widget_group,
                R.layout.widget_category,
        };
        for (int layoutId : layouts) {
            android.view.View root = inflate(layoutId);
            assertNotNull(root);
            assertEquals("布局 " + layoutId + " 的根节点 ID 不对", Integer.valueOf(android.R.id.background),
                    Integer.valueOf(root.getId()));
        }
    }

    @Test
    public void 布局根节点有背景色() {
        // 官方要求根布局必须有背景色、不能全透明（切页动画要用背景色）
        int[] layouts = {
                R.layout.widget_task_list,
                R.layout.widget_group,
                R.layout.widget_category,
        };
        for (int layoutId : layouts) {
            android.view.View root = inflate(layoutId);
            android.graphics.drawable.Drawable bg = root.getBackground();
            assertNotNull("布局 " + layoutId + " 没有背景", bg);
            assertFalse("布局 " + layoutId + " 的背景是全透明的", bg.getOpacity() == android.graphics.PixelFormat.TRANSPARENT);
        }
    }

    private android.view.View inflate(int layoutId) {
        android.content.Context themed = new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
        return android.view.LayoutInflater.from(themed).inflate(layoutId, null, false);
    }

    // ------------------------------------------------------------ 配置改存文件

    @Test
    public void 小组件配置能存能读() {
        WidgetConfig.setGroup(context, 11, "g-abc");
        WidgetConfig.setCategory(context, 11, Category.IMPORTANT_URGENT);

        assertEquals("g-abc", WidgetConfig.getGroup(context, 11));
        assertEquals(Category.IMPORTANT_URGENT, WidgetConfig.getCategory(context, 11));
    }

    @Test
    public void 各个小组件实例各存各的() {
        WidgetConfig.setGroup(context, 21, "g-1");
        WidgetConfig.setGroup(context, 22, "g-2");
        WidgetConfig.setCategory(context, 21, Category.IMPORTANT_URGENT);
        WidgetConfig.setCategory(context, 22, Category.NOT_IMPORTANT_NOT_URGENT);

        assertEquals("g-1", WidgetConfig.getGroup(context, 21));
        assertEquals("g-2", WidgetConfig.getGroup(context, 22));
        assertEquals(Category.IMPORTANT_URGENT, WidgetConfig.getCategory(context, 21));
        assertEquals(Category.NOT_IMPORTANT_NOT_URGENT, WidgetConfig.getCategory(context, 22));
    }

    @Test
    public void 没配置过时返回默认值() {
        assertNull(WidgetConfig.getGroup(context, 99));
        assertEquals(WidgetConfig.CATEGORY_NONE, WidgetConfig.getCategory(context, 99));
    }

    @Test
    public void 删掉小组件会清掉它的设置() {
        WidgetConfig.setGroup(context, 31, "g-x");
        WidgetConfig.setCategory(context, 31, Category.IMPORTANT_URGENT);
        WidgetConfig.clear(context, 31);
        assertNull(WidgetConfig.getGroup(context, 31));
        assertEquals(WidgetConfig.CATEGORY_NONE, WidgetConfig.getCategory(context, 31));
    }

    @Test
    public void 设置写进了文件而不是SharedPreferences() {
        WidgetConfig.setGroup(context, 41, "g-file");
        java.io.File f = new java.io.File(context.getFilesDir(), "widget_configs.json");
        assertTrue("配置应该落在 widget_configs.json 里", f.exists());
    }

    // ------------------------------------------------------------ 工具方法

    private android.content.pm.ActivityInfo receiverInfo(String cls) {
        try {
            return pm.getReceiverInfo(new android.content.ComponentName(context, cls),
                    PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
