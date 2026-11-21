package com.lighttodo.app;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.ui.TaskEditor;
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

/** 编辑弹窗 + 新小组件的渲染截图。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DialogWidgetScreenshotTest {

    private static final int WIDTH_PX = 822;   // 411dp * 2
    private static final int HEIGHT_PX = 1782; // 891dp * 2
    private static final int WIDGET_WIDTH_PX = 560;
    private static final int WIDGET_HEIGHT_PX = 320;

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

    /** 编辑任务弹窗：四宫格按颜色标记 + 所属分组 + 提醒时间都在。 */
    @Test
    public void 编辑弹窗截图() throws Exception {
        Context themed = new ContextThemeWrapper(context, R.style.Theme_LightTodo);
        View view = LayoutInflater.from(themed).inflate(R.layout.dialog_task, null);
        ((EditText) view.findViewById(R.id.input_title)).setText("军事理论作业");
        ((TextView) view.findViewById(R.id.tv_group)).setText("所属分组：其它");
        MaterialButton[] buttons = {
                view.findViewById(R.id.cat_0), view.findViewById(R.id.cat_1),
                view.findViewById(R.id.cat_2), view.findViewById(R.id.cat_3)
        };
        TaskEditor.refreshCatButtons(buttons, Category.IMPORTANT_URGENT);

        save(view, "10-dialog-task.png", WIDTH_PX, HEIGHT_PX);
    }

    /** 分类概览小组件。 */
    @Test
    public void 分类概览小组件截图() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "");
        store.addTask(work.id, "把周报发到群里", "", 0L, 0L, true, false);
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "", 0L, 0L, false, true);

        RemoteViews views = WidgetFactory.buildCategoryOverview(context, 170, 180);
        save(apply(views), "11-widget-category.png", WIDGET_WIDTH_PX, WIDGET_HEIGHT_PX + 60);
    }

    /** 长任务独占整行，短任务两个并排 —— 同一个小组件里混着排，颜色表示紧急程度。 */
    @Test
    public void 两栏任务小组件截图() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        long now = System.currentTimeMillis();
        store.addTask(work.id, "整理本周会议纪要", "", now + 3 * 3600_000L, 0L, true, true);
        store.addTask(work.id, "把周报发到群里", "", 0L, 0L, true, false);
        store.addTask(work.id, "约客户确认方案", "", 0L, 0L, false, true);
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶", "", 0L, 0L, false, false);
        store.addTask(life.id, "交房租", "", 0L, 0L, true, false);
        store.addTask(life.id, "取快递", "", 0L, 0L, false, false);

        // 560px / 2 = 280dp，超过并排的门槛
        RemoteViews views = WidgetFactory.buildTodoList(context, 240, 280);
        save(apply(views), "12-widget-two-columns.png", WIDGET_WIDTH_PX, 480);
    }

    /** 小组件内容不能被裁掉：最后一行文字离底边要留出余量。 */
    @Test
    public void 小组件内容不被裁掉() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        for (int i = 1; i <= 8; i++) {
            store.addTask(work.id, "任务" + i, "");
        }
        // 各种高度都试一遍：矮的、中的、高的
        int[] heights = {110, 150, 200, 240, 320};
        for (int height : heights) {
            RemoteViews views = WidgetFactory.buildTodoList(context, height, 280);
            Bitmap bitmap = render(apply(views), WIDGET_WIDTH_PX, height * 2);
            int bottom = lastDarkRow(bitmap);
            int bottomDp = (bitmap.getHeight() - bottom) / 2;
            assertTrue("高度 " + height + "dp 时内容贴到边上了（只剩 " + bottomDp + "dp）",
                    bottomDp >= 2);
        }
    }

    /** 单组任务小组件（配置过某个分组）。 */
    @Test
    public void 单组任务小组件截图() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "", 0L, 0L, true, true);
        store.addTask(work.id, "把周报发到群里", "", 0L, 0L, true, false);
        store.addTask(work.id, "约客户确认方案", "", 0L, 0L, false, true);
        WidgetConfig.setGroup(context, 7, work.id);

        RemoteViews views = WidgetFactory.buildGroupTasks(context, 7, 150, 180);
        save(apply(views), "13-widget-group-tasks.png", WIDGET_WIDTH_PX, WIDGET_HEIGHT_PX);
    }

    private View apply(RemoteViews views) throws Exception {
        FrameLayout parent = new FrameLayout(context);
        Method m = RemoteViews.class.getDeclaredMethod("apply", Context.class, ViewGroup.class);
        m.setAccessible(true);
        return (View) m.invoke(views, context, parent);
    }

    /** 渲染成位图。 */
    private Bitmap render(View view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFE6E8EC);
        view.draw(canvas);
        return bitmap;
    }

    /** 最后一行有内容（深色）的像素位置。 */
    private int lastDarkRow(Bitmap bitmap) {
        int h = bitmap.getHeight();
        int w = bitmap.getWidth();
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                if (Color.red(bitmap.getPixel(x, y)) < 150) return y;
            }
        }
        return 0;
    }

    private void save(View view, String name, int width, int height) throws Exception {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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
