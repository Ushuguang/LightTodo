package com.lighttodo.app;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;
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
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

/**
 * 「自动变分类」功能的界面截图。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AutoCategoryScreenshotTest {

    private static final int WIDTH_PX = 840;
    private static final int HEIGHT_PX = 1700;

    private Context context;
    private Context themed;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        themed = new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
        TodoStore.resetForTest(context);
        TodoStore store = TodoStore.get(context);
        for (Group g : store.groups()) store.deleteGroup(g.id);
    }

    /** 任务编辑弹窗里的「自动变分类」区块（含已设的两个时间点）。 */
    @Test
    public void 编辑任务里的自动变分类区块() throws Exception {
        TodoStore store = TodoStore.get(context);
        Group work = store.addGroup("工作");
        Task task = store.addTask(work.id, "高数作业", "", 0L, 0L, false, false);

        long day = 24 * 3600_000L;
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now + 3 * day);
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 0);
        long at1 = c.getTimeInMillis();
        c.setTimeInMillis(now + 5 * day);
        c.set(Calendar.HOUR_OF_DAY, 20);
        long at2 = c.getTimeInMillis();
        store.setAutoSteps(work.id, task.id, java.util.Arrays.asList(
                AutoCategoryStep.of(at1, Category.IMPORTANT_URGENT),
                AutoCategoryStep.of(at2, Category.NOT_IMPORTANT_URGENT)));

        View v = inflate(R.layout.dialog_task);
        android.widget.EditText title = v.findViewById(R.id.input_title);
        title.setText("高数作业");
        TextView groupInfo = v.findViewById(R.id.tv_group);
        groupInfo.setVisibility(View.VISIBLE);
        groupInfo.setText(themed.getString(R.string.dialog_group_of, "工作"));

        // 分类四宫格（真实运行时 TaskEditor 会刷新按钮状态，这里也刷一下）
        MaterialButton[] catButtons = new MaterialButton[]{
                v.findViewById(R.id.cat_0), v.findViewById(R.id.cat_1),
                v.findViewById(R.id.cat_2), v.findViewById(R.id.cat_3)
        };
        com.lighttodo.app.ui.TaskEditor.refreshCatButtons(catButtons, Category.NOT_IMPORTANT_NOT_URGENT);

        // 把两个时间点画进「自动变分类」区块
        LinearLayout box = v.findViewById(R.id.auto_steps_box);
        box.removeAllViews();
        addStepRow(box, at1, Category.IMPORTANT_URGENT);
        addStepRow(box, at2, Category.NOT_IMPORTANT_URGENT);

        save(v, "29-auto-category-in-editor.png");
    }

    /** 添加时间点的小弹窗：选目标分类 + 几天之后。 */
    @Test
    public void 添加时间点的弹窗() throws Exception {
        View v = inflate(R.layout.dialog_auto_step);
        MaterialButton btnTime = v.findViewById(R.id.btn_auto_time);
        btnTime.setText(themed.getString(R.string.time_not_set));

        MaterialButton[] catButtons = new MaterialButton[]{
                v.findViewById(R.id.cat_0), v.findViewById(R.id.cat_1),
                v.findViewById(R.id.cat_2), v.findViewById(R.id.cat_3)
        };
        com.lighttodo.app.ui.TaskEditor.refreshCatButtons(catButtons, Category.IMPORTANT_URGENT);

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 3);
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 0);
        btnTime.setText(com.lighttodo.app.util.TimeText.full(c.getTimeInMillis()));

        save(v, "30-auto-step-dialog.png");
    }

    // ------------------------------------------------------------ 辅助

    private void addStepRow(LinearLayout box, long at, int category) {
        View row = android.view.LayoutInflater.from(themed)
                .inflate(R.layout.item_auto_step, box, false);
        TextView time = row.findViewById(R.id.step_time);
        TextView cat = row.findViewById(R.id.step_cat);
        time.setText(com.lighttodo.app.util.TimeText.full(at));
        cat.setText(Category.nameRes(category));
        cat.setTextColor(androidx.core.content.ContextCompat.getColor(themed,
                Category.colorRes(category)));
        box.addView(row);
    }

    private View inflate(int layoutId) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(themed);
        return inflater.inflate(layoutId, null);
    }

    private void save(View view, String name) throws Exception {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.AT_MOST));
        int h = Math.max(1, view.getMeasuredHeight());
        if (h >= HEIGHT_PX) {
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
            h = HEIGHT_PX;
        }
        view.layout(0, 0, WIDTH_PX, h);

        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor("#FFF4F6FA"));
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
