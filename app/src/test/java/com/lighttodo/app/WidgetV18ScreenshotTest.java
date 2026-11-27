package com.lighttodo.app;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.widget.WidgetConfig;
import com.lighttodo.app.widget.WidgetFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

/**
 * v1.8.0 的小组件截图：
 * <ul>
 *   <li>自由缩放后的矮尺寸（紧凑标题栏，不裁切、不浪费空间）；</li>
 *   <li>「自定义清单」小组件（显示某个分组 / 某个分类）。</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetV18ScreenshotTest {

    private static final int WIDTH_PX = 480;          // 240dp
    private static final float DENSITY = 2.0f;

    private Context context;
    /** 用带 App 主题的 context 渲染：字体行高才跟真机一致（无主题会偏大 1~2dp）。 */
    private Context themed;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        themed = new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
        TodoStore.resetForTest(context);
        TodoStore store = TodoStore.get(context);
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    @Test
    public void 自由缩放后的紧凑小组件() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "");
        store.addTask(work.id, "把周报发到群里", "");
        store.addTask(work.id, "约客户确认方案", "");
        store.addTask(work.id, "回复客户邮件", "");

        // 用户把组件拖得很矮：60dp（几乎只有一两行）
        save(WidgetFactory.buildTodoList(context, 60, 240), 60, "24-widget-compact-60.png");
        // 中等高度：90dp
        save(WidgetFactory.buildTodoList(context, 90, 240), 90, "25-widget-compact-90.png");
        // 较高：130dp（正常标题栏）
        save(WidgetFactory.buildTodoList(context, 130, 240), 130, "26-widget-compact-130.png");
    }

    @Test
    public void 自定义清单小组件() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "");
        store.addTask(work.id, "把周报发到群里", "");
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "");

        int widgetId = 8801;
        WidgetConfig.setScopeType(context, widgetId, WidgetConfig.SCOPE_GROUP);
        WidgetConfig.setGroup(context, widgetId, work.id);
        save(WidgetFactory.buildCustomList(context, widgetId, 130, 240), 130,
                "27-widget-custom-group.png");

        int widgetId2 = 8802;
        WidgetConfig.setScopeType(context, widgetId2, WidgetConfig.SCOPE_ALL);
        save(WidgetFactory.buildCustomList(context, widgetId2, 130, 240), 130,
                "28-widget-custom-all.png");
    }

    private View apply(RemoteViews views) throws Exception {
        FrameLayout parent = new FrameLayout(themed);
        Method m = RemoteViews.class.getDeclaredMethod("apply", Context.class, ViewGroup.class);
        m.setAccessible(true);
        return (View) m.invoke(views, themed, parent);
    }

    private void save(RemoteViews views, int heightDp, String name) throws Exception {
        View view = apply(views);
        int heightPx = Math.round(heightDp * DENSITY);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH_PX, heightPx);

        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFE6E8EC);
        view.draw(canvas);

        File dir = new File("/workspace/output/screenshots");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建截图目录");
        }
        File out = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue("截图应已生成：" + name, out.exists() && out.length() > 0);
    }
}
