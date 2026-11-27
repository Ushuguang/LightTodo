package com.lighttodo.app.store;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.lighttodo.app.model.Question;
import com.lighttodo.app.model.QuestionCategory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 「我不会的题」的本地存储。
 *
 * <p>和 {@link TodoStore} 一样：全部数据存在应用私有目录的 JSON 文件里，不联网、不上传。
 * 图片存在 {@code files/photos/} 里，JSON 里只记文件名。</p>
 */
public class QuestionStore {

    private static final String TAG = "QuestionStore";
    private static final String FILE_NAME = "questions.json";
    /** 配图目录名（应用私有目录下面）。 */
    private static final String PHOTO_DIR = "photos";
    /** 存进应用目录的长边像素上限，太大的原图没必要留。 */
    public static final int PHOTO_MAX_EDGE = 1600;
    /** 列表里显示的缩略图长边。 */
    public static final int THUMB_MAX_EDGE = 320;

    private static QuestionStore sInstance;

    private final Context appContext;
    private final List<QuestionCategory> categories = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();

    public static synchronized QuestionStore get(Context context) {
        if (sInstance == null) {
            sInstance = new QuestionStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /** 仅供单元测试使用：丢弃缓存实例，重新从磁盘读取。 */
    @androidx.annotation.VisibleForTesting
    public static synchronized void resetForTest(Context context) {
        sInstance = new QuestionStore(context.getApplicationContext());
    }

    private QuestionStore(Context context) {
        this.appContext = context;
        load();
    }

    // ---------------------------------------------------------------- 读

    public List<QuestionCategory> categories() {
        return Collections.unmodifiableList(categories);
    }

    public QuestionCategory findCategory(String categoryId) {
        if (categoryId == null) return null;
        for (QuestionCategory c : categories) {
            if (c.id.equals(categoryId)) return c;
        }
        return null;
    }

    public int categoryCount() {
        return categories.size();
    }

    public int questionCount() {
        return questions.size();
    }

    /** 某个分类下的题目，没问过的排前面。 */
    public List<Question> questionsOf(String categoryId) {
        List<Question> out = new ArrayList<>();
        for (Question q : questions) {
            if (categoryId == null || categoryId.isEmpty() || q.categoryId.equals(categoryId)) {
                out.add(q);
            }
        }
        Collections.sort(out, new Comparator<Question>() {
            @Override
            public int compare(Question a, Question b) {
                if (a.asked != b.asked) return a.asked ? 1 : -1;
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    /** 某个分类下还没问过老师的题目数。 */
    public int pendingCountOf(String categoryId) {
        int n = 0;
        for (Question q : questions) {
            if ((categoryId == null || categoryId.isEmpty() || q.categoryId.equals(categoryId))
                    && !q.asked) {
                n++;
            }
        }
        return n;
    }

    public int totalPending() {
        return pendingCountOf(null);
    }

    /** 题目配图在应用私有目录里的绝对路径。 */
    public File photoFile(String name) {
        return new File(photoDir(), name);
    }

    /** 题目配图目录。 */
    public File photoDir() {
        File dir = new File(appContext.getFilesDir(), PHOTO_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    // ---------------------------------------------------------------- 写

    public QuestionCategory addCategory(String name) {
        String n = name == null ? "" : name.trim();
        QuestionCategory c = new QuestionCategory(UUID.randomUUID().toString(), n,
                System.currentTimeMillis());
        categories.add(c);
        commit();
        return c;
    }

    public boolean renameCategory(String categoryId, String name) {
        QuestionCategory c = findCategory(categoryId);
        if (c == null) return false;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return false;
        c.name = n;
        commit();
        return true;
    }

    /** 删掉一个分类，连同里面的题目和配图一起清掉。 */
    public boolean deleteCategory(String categoryId) {
        QuestionCategory c = findCategory(categoryId);
        if (c == null) return false;
        categories.remove(c);
        for (Question q : new ArrayList<>(questions)) {
            if (q.categoryId.equals(categoryId)) {
                deletePhotosOf(q);
                questions.remove(q);
            }
        }
        commit();
        return true;
    }

    public Question addQuestion(String categoryId, String title, String note,
                                 List<String> images) {
        Question q = new Question();
        q.id = UUID.randomUUID().toString();
        q.categoryId = categoryId == null ? "" : categoryId;
        q.title = title == null ? "" : title.trim();
        q.note = note == null ? "" : note;
        q.images = images == null ? new ArrayList<>() : new ArrayList<>(images);
        q.createdAt = System.currentTimeMillis();
        questions.add(q);
        commit();
        return q;
    }

    public boolean updateQuestion(Question q, String title, String note, List<String> images) {
        if (q == null) return false;
        List<String> old = new ArrayList<>(q.images);
        q.title = title == null ? "" : title.trim();
        q.note = note == null ? "" : note;
        q.images = images == null ? new ArrayList<>() : new ArrayList<>(images);
        // 被删掉的配图文件顺手清掉，别在目录里留垃圾
        for (String name : old) {
            if (!q.images.contains(name)) {
                File f = photoFile(name);
                if (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        commit();
        return true;
    }

    public boolean setAsked(Question q, boolean asked) {
        if (q == null) return false;
        q.asked = asked;
        commit();
        return true;
    }

    public boolean deleteQuestion(Question q) {
        if (q == null || !questions.remove(q)) return false;
        deletePhotosOf(q);
        commit();
        return true;
    }

    private void deletePhotosOf(Question q) {
        if (q.images == null) return;
        for (String name : q.images) {
            File f = photoFile(name);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    // ---------------------------------------------------------------- 图片

    /**
     * 从系统相册选的一张图，复制进应用私有目录。
     *
     * @return 存下来的文件名；失败返回 null。
     */
    public String importPhoto(Uri source) {
        if (source == null) return null;
        String name = "q_" + UUID.randomUUID().toString() + ".jpg";
        File target = photoFile(name);
        try (InputStream in = appContext.getContentResolver().openInputStream(source)) {
            if (in == null) return null;
            Bitmap bmp = decodeScaled(in, PHOTO_MAX_EDGE);
            if (bmp == null) return null;
            try (FileOutputStream out = new FileOutputStream(target)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            bmp.recycle();
            return name;
        } catch (Exception e) {
            Log.w(TAG, "导入图片失败", e);
            return null;
        }
    }

    /**
     * 相机拍完照后，把 FileProvider 给的那个临时文件挪进应用目录。
     *
     * @return 存下来的文件名；失败返回 null。
     */
    public String importPhoto(File captured) {
        if (captured == null || !captured.exists()) return null;
        String name = "q_" + UUID.randomUUID().toString() + ".jpg";
        File target = photoFile(name);
        try (InputStream in = new FileInputStream(captured)) {
            Bitmap bmp = decodeScaled(in, PHOTO_MAX_EDGE);
            if (bmp == null) return null;
            try (FileOutputStream out = new FileOutputStream(target)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            bmp.recycle();
            return name;
        } catch (Exception e) {
            Log.w(TAG, "导入照片失败", e);
            return null;
        } finally {
            // 相机那个临时文件用完就删
            //noinspection ResultOfMethodCallIgnored
            captured.delete();
        }
    }

    /** 按长边上限采样解码，省内存也省空间。 */
    private static Bitmap decodeScaled(InputStream in, int maxEdge) {
        try {
            byte[] data = readAll(in);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            int edge = Math.max(opts.outWidth, opts.outHeight);
            int sample = 1;
            while (edge / sample > maxEdge * 2) {
                sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, real);
            if (bmp == null) return null;
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int longEdge = Math.max(w, h);
            if (longEdge <= maxEdge) return bmp;
            float scale = (float) maxEdge / longEdge;
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.round(w * scale),
                    Math.round(h * scale), true);
            if (scaled != bmp) bmp.recycle();
            return scaled;
        } catch (Exception e) {
            Log.w(TAG, "解码图片失败", e);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ---------------------------------------------------------------- 存取

    private File dataFile() {
        return new File(appContext.getFilesDir(), FILE_NAME);
    }

    private void load() {
        File f = dataFile();
        if (!f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            String text = new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return;
            JSONObject root = new JSONObject(text);
            JSONArray cats = root.optJSONArray("categories");
            if (cats != null) {
                for (int i = 0; i < cats.length(); i++) {
                    QuestionCategory c = QuestionCategory.fromJson(cats.optJSONObject(i));
                    if (c != null && !c.id.isEmpty()) categories.add(c);
                }
            }
            JSONArray qs = root.optJSONArray("questions");
            if (qs != null) {
                for (int i = 0; i < qs.length(); i++) {
                    Question q = Question.fromJson(qs.optJSONObject(i));
                    if (q != null && !q.id.isEmpty()) questions.add(q);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "读取题目数据失败，按空数据处理", e);
        }
    }

    private void commit() {
        try {
            JSONObject root = new JSONObject();
            JSONArray cats = new JSONArray();
            for (QuestionCategory c : categories) cats.put(c.toJson());
            root.put("categories", cats);
            JSONArray qs = new JSONArray();
            for (Question q : questions) qs.put(q.toJson());
            root.put("questions", qs);

            File tmp = new File(appContext.getFilesDir(), FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
            File target = dataFile();
            if (!tmp.renameTo(target)) {
                if (target.exists() && !target.delete()) {
                    Log.w(TAG, "旧题目文件删不掉，跳过写入");
                }
                tmp.renameTo(target);
            }
        } catch (JSONException e) {
            Log.w(TAG, "保存题目数据失败", e);
        } catch (Exception e) {
            Log.w(TAG, "写入题目数据失败", e);
        }
    }
}
