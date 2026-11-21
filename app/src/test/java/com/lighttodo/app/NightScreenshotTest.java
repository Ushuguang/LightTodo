package com.lighttodo.app;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** 深色模式下的分组界面截图。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-night-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NightScreenshotTest {

    private static final int WIDTH_PX = 822;
    private static final int HEIGHT_PX = 1782;

    @Before
    public void setUp() {
        TodoStore.resetForTest(ApplicationProvider.getApplicationContext());
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    @Test
    public void 深色模式分组界面截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "重点记下三个决议");
        store.addTask(work.id, "把周报发到群里", "");
        Task done = store.addTask(work.id, "报销上周打车费", "");
        store.setTaskDone(work.id, done.id, true);

        android.content.Intent intent = new android.content.Intent(
                ApplicationProvider.getApplicationContext(), GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, work.id);

        try (ActivityController<GroupActivity> controller =
                     Robolectric.buildActivity(GroupActivity.class, intent)) {
            GroupActivity activity = controller.setup().get();
            View decor = activity.getWindow().getDecorView();
            decor.measure(
                    View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, WIDTH_PX, HEIGHT_PX);

            Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            decor.draw(canvas);

            File dir = new File("/workspace/output/screenshots");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建截图目录");
            }
            File out = new File(dir, "06-night.png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            assertTrue("截图应已生成", out.exists() && out.length() > 0);
        }
    }
}
