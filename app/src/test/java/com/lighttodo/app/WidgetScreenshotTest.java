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

/** 把桌面小组件的 RemoteViews 渲染成 PNG，确认小组件布局没问题。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetScreenshotTest {

    private static final int WIDTH_PX = 560;
    private static final int HEIGHT_PX = 320;

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        TodoStore.resetForTest(context);
        TodoStore store = TodoStore.get(context);
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    @Test
    public void 清单小组件截图() throws Exception {
        TodoStore store = TodoStore.get(context);
        long now = System.currentTimeMillis();
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "", now + 3 * 3600_000L, 0L, true, true);
        store.addTask(work.id, "把周报发到群里", "", 0L, 0L, true, false);
        store.addTask(work.id, "约客户确认方案", "", 0L, 0L, false, true);
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "", 0L, 0L, false, false);
        store.addTask(life.id, "交房租", "", 0L, 0L, true, false);

        RemoteViews views = WidgetFactory.buildTodoList(context, 150);
        save(apply(views), "04-widget-list.png");
    }

    @Test
    public void 分组小组件截图() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "");
        store.addTask(work.id, "把周报发到群里", "");
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "");
        Group study = store.addGroup("学习");
        store.addTask(study.id, "看完第 3 章并做笔记", "");

        RemoteViews views = WidgetFactory.buildGroupOverview(context, 150);
        save(apply(views), "05-widget-groups.png");
    }

    /** RemoteViews.apply 是 @hide 方法，用反射调起来做渲染验证。 */
    private View apply(RemoteViews views) throws Exception {
        FrameLayout parent = new FrameLayout(context);
        Method m = RemoteViews.class.getDeclaredMethod("apply", Context.class, ViewGroup.class);
        m.setAccessible(true);
        return (View) m.invoke(views, context, parent);
    }

    private void save(View view, String name) throws Exception {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH_PX, HEIGHT_PX);

        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888);
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
        assertTrue("截图应已生成", out.exists() && out.length() > 0);
    }
}
