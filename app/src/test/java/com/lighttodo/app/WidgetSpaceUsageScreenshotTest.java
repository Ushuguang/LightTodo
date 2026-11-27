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

/**
 * 小组件空间利用率对比图：同一批任务，分别按 3/4/5/6 个格子渲染，
 * 用来肉眼确认「分配了几格 → 实际用了几格」。
 *
 * <p>小米桌面一格约 48dp，所以 3 格 ≈ 144dp、4 格 ≈ 192dp，以此类推。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetSpaceUsageScreenshotTest {

    private static final int WIDTH_PX = 560;          // 280dp
    private static final float DENSITY = 2.0f;

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
    public void 不同格子高度对比() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "");
        store.addTask(work.id, "把周报发到群里", "");
        store.addTask(work.id, "约客户确认方案", "");
        store.addTask(work.id, "回复邮件", "");
        store.addTask(work.id, "订机票", "");
        store.addTask(work.id, "写周报", "");
        store.addTask(work.id, "交房租", "");
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "");
        store.addTask(life.id, "交房租", "");

        // 格子数 → 高度 dp（1 格 ≈ 48dp）
        save(WidgetFactory.buildTodoList(context, 192, 280), 192, "16-widget-4grids.png");
        save(WidgetFactory.buildTodoList(context, 240, 280), 240, "17-widget-5grids.png");
        save(WidgetFactory.buildTodoList(context, 288, 280), 288, "18-widget-6grids.png");
        save(WidgetFactory.buildTodoList(context, 144, 280), 144, "19-widget-3grids.png");
    }

    @Test
    public void 窄版整行排列对比() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "");
        store.addTask(work.id, "把周报发到群里", "");
        store.addTask(work.id, "约客户确认方案", "");
        store.addTask(work.id, "回复客户邮件", "");

        // 宽度不够并排 → 每条独占整行
        save(WidgetFactory.buildTodoList(context, 192, 180), 192, "20-widget-narrow.png");
    }

    private View apply(RemoteViews views) throws Exception {
        FrameLayout parent = new FrameLayout(context);
        Method m = RemoteViews.class.getDeclaredMethod("apply", Context.class, ViewGroup.class);
        m.setAccessible(true);
        return (View) m.invoke(views, context, parent);
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
