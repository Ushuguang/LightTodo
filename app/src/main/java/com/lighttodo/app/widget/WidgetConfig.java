package com.lighttodo.app.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 每个小组件实例自己的设置（显示哪个分组 / 哪个分类）。
 *
 * <p>按 appWidgetId 存，所以同一个小组件可以在桌面上叠好几个，各看各的。</p>
 *
 * <p><b>为什么存文件而不是 SharedPreferences：</b>小米小部件（MIUI Widget）规范要求
 * 小组件跑在 {@code :widgetProvider} 独立进程里，而 SharedPreferences 在多进程下的
 * 内存缓存不同步（主进程写了，widget 进程可能读到旧值）。所以这里改成和
 * {@code TodoStore} 一样的 JSON 文件，进程之间天然一致。</p>
 *
 * <p>老版本（v1.4 及以前）把配置存在 SharedPreferences 里，这里读文件时会自动
 * 从 SharedPreferences 迁移一次，保证升级后桌面上已有的卡片不会丢设置。</p>
 */
public final class WidgetConfig {

    private static final String TAG = "WidgetConfig";
    private static final String FILE_NAME = "widget_configs.json";
    /** 老版本用的 SharedPreferences 文件名，只在迁移时读一次。 */
    private static final String LEGACY_PREFS = "lighttodo_widgets";
    private static final String KEY_GROUP = "group_";
    private static final String KEY_CATEGORY = "category_";

    /** 没配置过时返回 -1。 */
    public static final int CATEGORY_NONE = -1;

    private WidgetConfig() {
    }

    // ---------------------------------------------------------------- 读

    public static String getGroup(Context context, int widgetId) {
        return read(context).optString(KEY_GROUP + widgetId, null);
    }

    public static int getCategory(Context context, int widgetId) {
        return read(context).optInt(KEY_CATEGORY + widgetId, CATEGORY_NONE);
    }

    // ---------------------------------------------------------------- 写

    public static void setGroup(Context context, int widgetId, String groupId) {
        JSONObject all = read(context);
        try {
            all.put(KEY_GROUP + widgetId, groupId == null ? JSONObject.NULL : groupId);
        } catch (Exception e) {
            Log.w(TAG, "保存小组件分组设置失败", e);
        }
        write(context, all);
    }

    public static void setCategory(Context context, int widgetId, int category) {
        JSONObject all = read(context);
        try {
            all.put(KEY_CATEGORY + widgetId, category);
        } catch (Exception e) {
            Log.w(TAG, "保存小组件分类设置失败", e);
        }
        write(context, all);
    }

    /** 小组件被从桌面删掉时，清掉它的设置。 */
    public static void clear(Context context, int widgetId) {
        JSONObject all = read(context);
        all.remove(KEY_GROUP + widgetId);
        all.remove(KEY_CATEGORY + widgetId);
        write(context, all);
    }

    // ---------------------------------------------------------------- 存取

    private static File dataFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    /** 读全部配置；文件还不存在时，先从老的 SharedPreferences 迁移一次。 */
    private static JSONObject read(Context context) {
        File f = dataFile(context);
        if (!f.exists()) {
            JSONObject migrated = migrateFromPrefs(context);
            if (migrated != null) {
                write(context, migrated);
                return migrated;
            }
            return new JSONObject();
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            String text = new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return new JSONObject();
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "读取小组件设置失败，按空设置处理", e);
            return new JSONObject();
        }
    }

    /** 原子写：先写 .tmp 再改名，避免 widget 进程读到半截文件。 */
    private static void write(Context context, JSONObject all) {
        File tmp = new File(context.getApplicationContext().getFilesDir(), FILE_NAME + ".tmp");
        try {
            byte[] data = all.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.flush();
                out.getFD().sync();
            }
            File target = dataFile(context);
            if (!tmp.renameTo(target)) {
                // 某些文件系统上 rename 会失败，退化成「删旧写新」
                if (target.exists() && !target.delete()) {
                    Log.w(TAG, "旧设置文件删不掉，先跳过写入");
                }
                tmp.renameTo(target);
            }
        } catch (Exception e) {
            Log.w(TAG, "写入小组件设置失败", e);
        }
    }

    /**
     * 把 v1.4 及以前存在 SharedPreferences 里的配置搬过来。
     * 搬完就把老 key 删掉，避免以后两边各写各的。
     */
    private static JSONObject migrateFromPrefs(Context context) {
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
            JSONObject out = new JSONObject();
            boolean any = false;
            for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();
                if (key.startsWith(KEY_GROUP) && value instanceof String) {
                    out.put(key, value);
                    any = true;
                } else if (key.startsWith(KEY_CATEGORY) && value instanceof Integer) {
                    out.put(key, value);
                    any = true;
                }
            }
            if (any) {
                prefs.edit().clear().apply();
                Log.i(TAG, "已把旧版小组件设置迁移到文件");
            }
            return any ? out : null;
        } catch (Exception e) {
            Log.w(TAG, "迁移旧小组件设置失败", e);
            return null;
        }
    }
}
