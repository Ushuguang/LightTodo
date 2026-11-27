package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Question;
import com.lighttodo.app.model.QuestionCategory;
import com.lighttodo.app.store.QuestionStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * 「题目」功能：分类、题目、图片。
 *
 * <p>用户的需求是「把遇到的不会的题攒下来，等有空问老师，可以插图片和文字」。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
public class QuestionStoreTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        QuestionStore.resetForTest(context);
        // 清掉上一次测试留下的配图
        File dir = new File(context.getFilesDir(), "photos");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    @Test
    public void 能建题目分类() {
        QuestionStore store = QuestionStore.get(context);
        assertEquals(0, store.categoryCount());
        QuestionCategory c = store.addCategory("高等数学");
        assertEquals(1, store.categoryCount());
        assertEquals("高等数学", c.name);
        assertFalse(c.id.isEmpty());
        assertTrue(store.findCategory(c.id) != null);
    }

    @Test
    public void 分类重命名和删除() {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory c = store.addCategory("线代");
        assertTrue(store.renameCategory(c.id, "线性代数"));
        assertEquals("线性代数", store.findCategory(c.id).name);
        assertFalse("空名字不该能改", store.renameCategory(c.id, "   "));
        assertTrue(store.deleteCategory(c.id));
        assertEquals(0, store.categoryCount());
    }

    @Test
    public void 能记题目带文字和图片() {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory c = store.addCategory("高数");
        Question q = store.addQuestion(c.id, "第 3 题 积分变换", "老师说过用留数定理",
                Arrays.asList("a.jpg", "b.jpg"));
        assertEquals(1, store.questionCount());
        assertEquals("第 3 题 积分变换", q.title);
        assertEquals("老师说过用留数定理", q.note);
        assertEquals(2, q.images.size());
        assertTrue(q.hasImages());
        assertEquals(1, store.questionsOf(c.id).size());
    }

    @Test
    public void 没问过的排前面_问过的沉底() {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory c = store.addCategory("高数");
        Question asked = store.addQuestion(c.id, "问过了", "", null);
        Question pending = store.addQuestion(c.id, "还没问", "", null);
        store.setAsked(asked, true);

        java.util.List<Question> list = store.questionsOf(c.id);
        assertEquals(2, list.size());
        assertEquals("还没问", list.get(0).title);
        assertEquals("问过了", list.get(1).title);
        assertEquals(1, store.pendingCountOf(c.id));
        assertTrue(pending.asked == false);
    }

    @Test
    public void 删分类会连题目和配图一起删() throws Exception {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory c = store.addCategory("高数");
        store.addQuestion(c.id, "带图", "", Arrays.asList("x.jpg"));
        // 造一个真的配图文件
        File f = store.photoFile("x.jpg");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[]{1, 2, 3});
        }
        assertTrue(f.exists());

        store.deleteCategory(c.id);
        assertEquals(0, store.questionCount());
        assertFalse("配图文件也要一起删掉", f.exists());
    }

    @Test
    public void 删题目会删掉配图() throws Exception {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory c = store.addCategory("高数");
        Question q = store.addQuestion(c.id, "带图", "", Arrays.asList("y.jpg"));
        File f = store.photoFile("y.jpg");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[]{1, 2, 3});
        }
        store.deleteQuestion(q);
        assertEquals(0, store.questionCount());
        assertFalse(f.exists());
    }

    @Test
    public void 数据能存盘再读回来() {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory c = store.addCategory("数据结构");
        store.addQuestion(c.id, "链表反转", "写了两遍还是错",
                Arrays.asList("shot.jpg"));

        // 重新读一遍（模拟 App 重启）
        QuestionStore.resetForTest(context);
        QuestionStore again = QuestionStore.get(context);
        assertEquals(1, again.categoryCount());
        assertEquals("数据结构", again.categories().get(0).name);
        assertEquals(1, again.questionCount());
        Question q = again.questionsOf(again.categories().get(0).id).get(0);
        assertEquals("链表反转", q.title);
        assertEquals("写了两遍还是错", q.note);
        assertEquals(1, q.images.size());
        assertEquals("shot.jpg", q.images.get(0));
    }

    @Test
    public void 配图会存到应用私有目录并压缩() throws Exception {
        QuestionStore store = QuestionStore.get(context);
        // 造一张大图
        Bitmap big = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888);
        File src = new File(context.getCacheDir(), "src.jpg");
        try (FileOutputStream out = new FileOutputStream(src)) {
            big.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        big.recycle();

        String name = store.importPhoto(src);
        assertNotNull("图片应该能导入", name);
        File saved = store.photoFile(name);
        assertTrue(saved.exists());
        assertTrue("应该存在应用私有目录里", saved.getAbsolutePath()
                .startsWith(context.getFilesDir().getAbsolutePath()));

        // 长边被压到 1600 以内
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(saved.getAbsolutePath(), opts);
        assertTrue("长边应该不超过 " + QuestionStore.PHOTO_MAX_EDGE + "，实际 "
                + Math.max(opts.outWidth, opts.outHeight),
                Math.max(opts.outWidth, opts.outHeight) <= QuestionStore.PHOTO_MAX_EDGE);
    }

    @Test
    public void 题目分类页能按分类筛出题目() {
        QuestionStore store = QuestionStore.get(context);
        QuestionCategory a = store.addCategory("高数");
        QuestionCategory b = store.addCategory("线代");
        store.addQuestion(a.id, "高数题", "", null);
        store.addQuestion(b.id, "线代题", "", null);

        assertEquals(1, store.questionsOf(a.id).size());
        assertEquals("高数题", store.questionsOf(a.id).get(0).title);
        assertEquals(1, store.questionsOf(b.id).size());
        assertEquals(2, store.questionsOf(null).size());
    }
}
