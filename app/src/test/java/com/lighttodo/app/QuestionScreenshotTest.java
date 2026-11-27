package com.lighttodo.app;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Question;
import com.lighttodo.app.model.QuestionCategory;
import com.lighttodo.app.store.QuestionStore;
import com.lighttodo.app.store.TodoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/** 「题目」功能的界面截图：让用户能直接核对长什么样。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QuestionScreenshotTest {

    private static final int WIDTH_PX = 840;
    private static final int HEIGHT_PX = 1700;

    private Context context;
    /** inflate 布局必须用带 App 主题的 context，否则 ?attr/... 取不到值。 */
    private Context themed;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        themed = new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
        TodoStore.resetForTest(context);
        QuestionStore.resetForTest(context);
        TodoStore store = TodoStore.get(context);
        for (Group g : store.groups()) store.deleteGroup(g.id);
    }

    /** 题目分类页（MainActivity 里的那一页）。 */
    @Test
    public void 题目分类页截图() throws Exception {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory math = store.addCategory("高等数学");
        QuestionCategory linear = store.addCategory("线性代数");
        QuestionCategory c = store.addCategory("C 语言");
        store.addQuestion(math.id, "第 3 题 积分变换", "老师说过用留数定理，但我不太记得怎么套", null);
        store.addQuestion(math.id, "中值定理证明题", "", null);
        store.addQuestion(linear.id, "特征值和特征向量", "第二遍做还是卡在怎么判断重根", null);
        store.addQuestion(c.id, "指针那块儿", "老师上次说回去自己写一遍", null);

        // 走真实 Activity 渲染，底部导航才是完整的（inflate 单独测会量不准）
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     org.robolectric.Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    activity.findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(R.id.nav_questions);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            android.view.View decor = activity.getWindow().getDecorView();
            saveDirect(decor, "21-questions-page.png");
        }
    }

    /** 题目列表页（某个分类下的题目）。 */
    @Test
    public void 题目列表页截图() throws Exception {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory math = store.addCategory("高等数学");
        store.addQuestion(math.id, "第 3 题 积分变换",
                "老师说过用留数定理，但我不太记得怎么套\n问的时候记得把课本翻到那一页", null);
        store.addQuestion(math.id, "中值定理的证明", "", null);
        store.addQuestion(math.id, "级数收敛的判别法", "调和级数那里没听懂", null);

        View v = inflate(R.layout.activity_question);
        // 标题栏文字
        com.google.android.material.appbar.MaterialToolbar toolbar = v.findViewById(R.id.toolbar);
        toolbar.setTitle("高等数学");
        android.widget.TextView sub = v.findViewById(R.id.subtitle);
        sub.setText("3 题待问 · 共 3 题");
        RecyclerScroll(v);
        save(v, "22-question-list.png");
    }

    /** 记一道题的弹窗（文字 + 图片按钮）。 */
    @Test
    public void 记题弹窗截图() throws Exception {
        View v = inflate(R.layout.dialog_question);
        android.widget.TextView cat = v.findViewById(R.id.tv_category);
        cat.setText("分类：高等数学");
        android.widget.EditText title = v.findViewById(R.id.input_title);
        title.setText("第 3 题 积分变换");
        android.widget.EditText note = v.findViewById(R.id.input_note);
        note.setText("老师说过用留数定理\n问的时候记得把课本翻到那一页");
        save(v, "23-question-editor.png");
    }

    // ------------------------------------------------------------ 辅助

    /** 把某个布局 inflate 出来，并让里面的 RecyclerView 填上真实数据。 */
    private View inflate(int layoutId) throws Exception {
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(themed);
        View v = inflater.inflate(layoutId, null);
        v.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.WRAP_CONTENT, ViewGroup.WRAP_CONTENT));
        fillLists(v);
        return v;
    }

    /** 找到页面里的 RecyclerView 并塞数据。 */
    private void fillLists(View root) {
        android.widget.TextView summary = root.findViewById(R.id.summary_question);
        if (summary != null) {
            QuestionStore store = QuestionStore.get(context);
            summary.setText(themed.getString(R.string.q_summary,
                    store.categoryCount(), store.totalPending()));
        }
        androidx.recyclerview.widget.RecyclerView list =
                root.findViewById(R.id.question_category_list);
        if (list != null) {
            QuestionStore store = QuestionStore.get(context);
            com.lighttodo.app.ui.QuestionCategoryAdapter adapter =
                    new com.lighttodo.app.ui.QuestionCategoryAdapter(store,
                            new com.lighttodo.app.ui.QuestionCategoryAdapter.Listener() {
                                @Override
                                public void onOpen(QuestionCategory category) {
                                }

                                @Override
                                public void onMore(QuestionCategory category) {
                                }
                            });
            adapter.setData(store.categories());
            list.setAdapter(adapter);
            list.setLayoutManager(
                    new androidx.recyclerview.widget.LinearLayoutManager(themed));
        }
        androidx.recyclerview.widget.RecyclerView ql = root.findViewById(R.id.list);
        if (ql != null) {
            // 这是「题目分类详情」页里的列表（activity_question），
            // 和 MainActivity 里的待办列表（activity_main）不是同一个页面。
            QuestionStore store = QuestionStore.get(context);
            com.lighttodo.app.ui.QuestionAdapter adapter =
                    new com.lighttodo.app.ui.QuestionAdapter(store,
                            new com.lighttodo.app.ui.QuestionAdapter.Listener() {
                                @Override
                                public void onToggleAsked(Question question) {
                                }

                                @Override
                                public void onOpen(Question question) {
                                }

                                @Override
                                public void onMore(Question question) {
                                }
                            });
            adapter.setData(store.questionsOf(null));
            ql.setAdapter(adapter);
            ql.setLayoutManager(
                    new androidx.recyclerview.widget.LinearLayoutManager(themed));
        }
    }

    /** MainActivity 里同时有「分组页」和「题目页」，截图前先把题目页切出来。 */
    private void showQuestionsPage(View root) {
        View groups = root.findViewById(R.id.page_groups);
        View questions = root.findViewById(R.id.page_questions);
        if (groups != null) groups.setVisibility(View.GONE);
        if (questions != null) questions.setVisibility(View.VISIBLE);
        View empty = root.findViewById(R.id.empty);
        if (empty != null) empty.setVisibility(View.GONE);
        View emptyQ = root.findViewById(R.id.empty_question);
        if (emptyQ != null) emptyQ.setVisibility(View.GONE);
    }

    /** 手动量一遍布局，让 RecyclerView 的子项都算好高度。 */
    private void RecyclerScroll(View root) {
        root.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, WIDTH_PX, Math.min(HEIGHT_PX, root.getMeasuredHeight()));
    }

    /** 直接画已经测量布局好的视图（Activity decor），不再重新测量。 */
    private void saveDirect(View view, String name) throws Exception {
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0 || h <= 0) {
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, WIDTH_PX, HEIGHT_PX);
            w = WIDTH_PX;
            h = HEIGHT_PX;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
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

    private void save(View view, String name) throws Exception {
        // 先按「最多 HEIGHT_PX」量一次；内容比屏幕高时改成精确高度，
        // 否则底部导航会被裁掉（用户就看不到第 4 个「题目」入口了）。
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

    /** ViewGroup 的 wrap_content 常量。 */
    private static final class ViewGroup {
        static final int WRAP_CONTENT = -2;
    }
}
