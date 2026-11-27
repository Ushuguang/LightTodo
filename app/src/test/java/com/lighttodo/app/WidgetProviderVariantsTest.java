package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 桌面小组件要提供<b>两套</b>：小米小部件版 + 普通安卓组件版。
 *
 * <p>背景：小米把负一屏的小组件分成「全部组件」和「安卓组件」两类。
 * 带 {@code miuiWidget} 标识的走小米小部件通道，必须上架审核后才出现在「全部组件」里；
 * 在上架之前，系统会把它从「安卓组件」分类里也一并摘掉 —— 结果就是两边都搜不到，
 * 用户反馈「原本只能在安卓组件里查到，现在连安卓组件里面都找不到了」。</p>
 *
 * <p>所以普通安卓组件那一套不能带任何小米标识。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
public class WidgetProviderVariantsTest {

    /** 小米小部件版（带 miuiWidget 标识）的 5 个接收器。 */
    private static final String[] MIUI_PROVIDERS = {
            "com.lighttodo.app.widget.TodoListWidgetProvider",
            "com.lighttodo.app.widget.GroupWidgetProvider",
            "com.lighttodo.app.widget.CategoryWidgetProvider",
            "com.lighttodo.app.widget.GroupTaskWidgetProvider",
            "com.lighttodo.app.widget.CategoryTaskWidgetProvider",
    };

    /** 普通安卓组件版的 5 个接收器。 */
    private static final String[] PLAIN_PROVIDERS = {
            "com.lighttodo.app.widget.PlainTodoListWidgetProvider",
            "com.lighttodo.app.widget.PlainGroupWidgetProvider",
            "com.lighttodo.app.widget.PlainCategoryWidgetProvider",
            "com.lighttodo.app.widget.PlainGroupTaskWidgetProvider",
            "com.lighttodo.app.widget.PlainCategoryTaskWidgetProvider",
    };

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void 小米小部件版五个都在且带标识() {
        for (String name : MIUI_PROVIDERS) {
            ActivityInfo info = providerInfo(name);
            assertTrue(name + " 应该存在", info != null);
            assertTrue(name + " 应该带 miuiWidget 标识",
                    info.metaData != null && info.metaData.containsKey("miuiWidget"));
        }
    }

    @Test
    public void 普通安卓组件版五个都在且不带小米标识() {
        for (String name : PLAIN_PROVIDERS) {
            ActivityInfo info = providerInfo(name);
            assertTrue(name + " 应该存在", info != null);
            assertTrue(name + " 不该带 miuiWidget 标识（否则会从「安卓组件」分类里被摘掉）",
                    info.metaData == null || !info.metaData.containsKey("miuiWidget"));
            assertTrue(name + " 不该带曝光刷新（普通组件靠系统定时刷新）",
                    info.metaData == null || !info.metaData.containsKey("miuiWidgetRefresh"));
        }
    }

    @Test
    public void 两套都注册了标准的小组件更新广播() {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> ris = pm.queryBroadcastReceivers(
                new Intent("android.appwidget.action.APPWIDGET_UPDATE"),
                PackageManager.GET_META_DATA);
        Set<String> listening = new HashSet<>();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo != null) listening.add(ri.activityInfo.name);
        }
        for (String name : MIUI_PROVIDERS) {
            assertTrue(name + " 应该监听标准的 APPWIDGET_UPDATE", listening.contains(name));
        }
        for (String name : PLAIN_PROVIDERS) {
            assertTrue(name + " 应该监听标准的 APPWIDGET_UPDATE", listening.contains(name));
        }
    }

    @Test
    public void 两套的名字不重样() {
        Set<String> labels = new HashSet<>();
        for (String name : MIUI_PROVIDERS) {
            assertTrue("小米版名称重复：" + providerInfo(name).loadLabel(
                    context.getPackageManager()), labels.add(
                    providerInfo(name).loadLabel(context.getPackageManager()).toString()));
        }
        for (String name : PLAIN_PROVIDERS) {
            assertTrue("普通版名称重复：" + providerInfo(name).loadLabel(
                    context.getPackageManager()), labels.add(
                    providerInfo(name).loadLabel(context.getPackageManager()).toString()));
        }
        assertEquals("一共应该是 10 个小组件", 10, labels.size());
    }

    @Test
    public void 普通版功能与小米版一致() {
        // 普通版直接继承小米版的类，所以 onUpdate / 配置页 / 刷新逻辑全都是同一份
        for (String plain : PLAIN_PROVIDERS) {
            String miui = plain.replace("Plain", "");
            assertTrue(plain + " 应该继承自 " + miui,
                    isSubclassOf(plain, miui));
        }
    }

    // ------------------------------------------------------------ 辅助

    /** 小组件接收器的信息（meta-data 就在 ActivityInfo 里）。 */
    private ActivityInfo providerInfo(String className) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getReceiverInfo(
                    new android.content.ComponentName(context.getPackageName(), className),
                    PackageManager.GET_META_DATA);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSubclassOf(String sub, String sup) {
        try {
            Class<?> c = Class.forName(sub);
            Class<?> p = Class.forName(sup);
            return p.isAssignableFrom(c);
        } catch (Throwable t) {
            return false;
        }
    }
}
