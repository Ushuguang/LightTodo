package com.lighttodo.app;

import static org.junit.Assert.assertTrue;

import android.content.Intent;
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

/** 用 Robolectric 原生渲染模式把界面画成 PNG，用于交付前的目视检查。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {

    private static final int WIDTH_PX = 822;   // 411dp * 2
    private static final int HEIGHT_PX = 1782; // 891dp * 2

    @Before
    public void setUp() {
        TodoStore.resetForTest(ApplicationProvider.getApplicationContext());
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
    }

    @Test
    public void 主界面截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "重点记下三个决议");
        store.addTask(work.id, "把周报发到群里", "",
                System.currentTimeMillis() + 6 * 3600_000L, 0L);
        store.addTask(work.id, "约客户确认方案", "");
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "");
        store.addTask(life.id, "交房租", "每月 15 号");
        Group study = store.addGroup("学习");
        store.addTask(study.id, "看完第 3 章并做笔记", "");

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            save(activity.getWindow().getDecorView(), "01-main.png");
        }
    }

    /** 带截止时间、提醒时间、逾期任务的任务列表。 */
    @Test
    public void 截止时间与提醒截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        Group work = store.addGroup("工作");
        long now = System.currentTimeMillis();
        store.addTask(work.id, "整理本周会议纪要", "重点记下三个决议",
                now + 3 * 3600_000L, now + 2 * 3600_000L);
        store.addTask(work.id, "把周报发到群里", "", now + 24 * 3600_000L, 0L);
        store.addTask(work.id, "回复客户的邮件", "上周就说过要回了", now - 2 * 3600_000L, 0L);
        Task done = store.addTask(work.id, "报销上周打车费", "已提交，等审批");
        store.setTaskDone(work.id, done.id, true);

        android.content.Intent intent = new android.content.Intent(
                ApplicationProvider.getApplicationContext(), GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, work.id);

        try (ActivityController<GroupActivity> controller =
                     Robolectric.buildActivity(GroupActivity.class, intent)) {
            GroupActivity activity = controller.setup().get();
            save(activity.getWindow().getDecorView(), "08-deadline.png");
        }
    }

    /** 分类视图（重要 / 紧急四象限）。 */
    @Test
    public void 分类视图截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        long now = System.currentTimeMillis();
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "重点记下三个决议",
                now + 3 * 3600_000L, 0L, true, true);
        store.addTask(work.id, "把周报发到群里", "", now + 24 * 3600_000L, 0L, true, false);
        store.addTask(work.id, "回复客户的邮件", "上周就说过要回了",
                now - 2 * 3600_000L, 0L, false, true);
        Group life = store.addGroup("生活");
        store.addTask(life.id, "买牛奶和咖啡豆", "", 0L, 0L, false, false);
        store.addTask(life.id, "交房租", "每月 15 号", 0L, 0L, false, false);
        // 这两条没设截止时间和提醒 —— 以前这一行会被整体藏掉，什么都看不见
        store.addTask(life.id, "体育", "", 0L, 0L, true, false);
        store.addTask(work.id, "作业", "", 0L, 0L, true, true);
        // 这条已经做完了 —— 分类页不该再列它（分组页里还能看到）
        Task finished = store.addTask(work.id, "已经做完了的事", "", 0L, 0L, true, true);
        store.toggleTask(work.id, finished.id);
        // 这条设了「3 天后自动变成重要紧急」—— 任务项上要显示出来
        Task auto = store.addTask(life.id, "体育测试", "", 0L, 0L, true, false);
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_YEAR, 3);
        c.set(java.util.Calendar.HOUR_OF_DAY, 8);
        c.set(java.util.Calendar.MINUTE, 0);
        store.setAutoSteps(life.id, auto.id, java.util.Arrays.asList(
                com.lighttodo.app.model.AutoCategoryStep.of(c.getTimeInMillis(),
                        com.lighttodo.app.model.Category.IMPORTANT_URGENT)));

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    activity.findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(R.id.nav_categories);
            save(activity.getWindow().getDecorView(), "09-category.png");
        }
    }

    /** 提醒与权限（助手页）。 */
    @Test
    public void 助手页截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        Group work = store.addGroup("工作");
        store.addTask(work.id, "写周报", "", 0L,
                System.currentTimeMillis() + 3600_000L);

        try (ActivityController<AssistantActivity> controller =
                     Robolectric.buildActivity(AssistantActivity.class)) {
            AssistantActivity activity = controller.setup().get();
            save(activity.getWindow().getDecorView(), "07-assistant.png");
        }
    }

    @Test
    public void 分组界面截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        Group work = store.addGroup("工作");
        store.addTask(work.id, "整理本周会议纪要", "重点记下三个决议");
        store.addTask(work.id, "把周报发到群里", "");
        store.addTask(work.id, "约客户确认方案", "");
        Task done = store.addTask(work.id, "报销上周打车费", "已提交，等审批");
        store.setTaskDone(work.id, done.id, true);
        Task done2 = store.addTask(work.id, "回复邮件", "");
        store.setTaskDone(work.id, done2.id, true);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, work.id);

        try (ActivityController<GroupActivity> controller =
                     Robolectric.buildActivity(GroupActivity.class, intent)) {
            GroupActivity activity = controller.setup().get();
            save(activity.getWindow().getDecorView(), "02-group.png");
        }
    }

    @Test
    public void 空状态截图() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            save(activity.getWindow().getDecorView(), "03-empty.png");
        }
    }

    /** 底部导航「设置」页。 */
    @Test
    public void 设置页截图() throws Exception {
        TodoStore store = TodoStore.get(ApplicationProvider.getApplicationContext());
        store.addGroup("工作");

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    activity.findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(R.id.nav_settings);
            save(activity.getWindow().getDecorView(), "14-settings.png");
        }
    }

    /** 关于页：版本、版权、仓库、检查更新。 */
    @Test
    public void 关于页截图() throws Exception {
        try (ActivityController<AboutActivity> controller =
                     Robolectric.buildActivity(AboutActivity.class)) {
            AboutActivity activity = controller.setup().get();
            save(activity.getWindow().getDecorView(), "15-about.png");
        }
    }

    private void save(View decor, String name) throws Exception {
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
        File out = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue("截图应已生成", out.exists() && out.length() > 0);
    }
}
