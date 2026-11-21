package com.lighttodo.app.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * 从 GitHub Releases 检查更新并直接安装。
 *
 * <p>仓库：github.com/Ushuguang/LightTodo。约定：每个 Release 至少带一个
 * {@code *.apk} 资产，tag 用版本号（如 {@code v1.4.0}）。</p>
 *
 * <p>没有用任何第三方库：GitHub API 用 {@code HttpURLConnection} 直接读，
 * APK 下载到应用私有目录后用 FileProvider 授权给系统安装器。</p>
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    /** 仓库地址（关于页展示用）。 */
    public static final String REPO_OWNER = "Ushuguang";
    public static final String REPO_NAME = "LightTodo";
    public static final String REPO_URL = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    /** 最新 Release 的 JSON 接口。 */
    public static final String API_LATEST =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    private UpdateChecker() {
    }

    /** 一次检查的结果。 */
    public static final class Result {
        /** 请求是否成功。 */
        public final boolean ok;
        /** 出错时的原因（可读）。 */
        public final String error;
        /** 服务器上的最新版本号，例如 "v1.4.0"。 */
        public final String latestTag;
        /** 新版本说明。 */
        public final String releaseNotes;
        /** 新版本 APK 的下载地址，没有则为 null。 */
        public final String apkUrl;
        /** 新版本 APK 文件名。 */
        public final String apkName;

        /** 构造一个检查结果（公开出来方便单测构造各种情况）。 */
        public Result(boolean ok, String error, String latestTag, String releaseNotes,
                      String apkUrl, String apkName) {
            this.ok = ok;
            this.error = error;
            this.latestTag = latestTag;
            this.releaseNotes = releaseNotes;
            this.apkUrl = apkUrl;
            this.apkName = apkName;
        }

        /** 本机版本是否比服务器上的旧（即确实有更新）。 */
        public boolean isNewerThan(String currentVersion) {
            if (!ok || latestTag == null || currentVersion == null) return false;
            return compareVersion(normalize(currentVersion), normalize(latestTag)) < 0;
        }
    }

    /** 去掉 tag 上的 v 前缀。 */
    public static String normalize(String tag) {
        if (tag == null) return "";
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t;
    }

    /**
     * 比较两个版本号（都应为 "1.4.0" 这种数字分段形式）。
     * 返回负数表示 a 比 b 旧，0 表示相同，正数表示 a 更新。
     */
    public static int compareVersion(String a, String b) {
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? toInt(pa[i]) : 0;
            int vb = i < pb.length ? toInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 查最新 Release。必须在后台线程调用。 */
    public static Result checkLatest() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_LATEST).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "LightTodo-Android");
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 404) {
                // 仓库还没有发布过 Release，或者仓库还没上传
                return new Result(false, "404", null, null, null, null);
            }
            if (code == 403 || code == 429) {
                return new Result(false, "rate-limit", null, null, null, null);
            }
            if (code < 200 || code >= 300) {
                return new Result(false, "HTTP " + code, null, null, null, null);
            }

            String body = readAll(conn.getInputStream());
            String tag = extract(body, "\"tag_name\"");
            String notes = extract(body, "\"body\"");
            String apkUrl = null;
            String apkName = null;

            // 从 assets 数组里找第一个 .apk
            int assetsAt = body.indexOf("\"assets\"");
            if (assetsAt >= 0) {
                String tail = body.substring(assetsAt);
                int idx = 0;
                while (true) {
                    int bUrl = tail.indexOf("\"browser_download_url\"", idx);
                    int bName = tail.indexOf("\"name\"", idx);
                    if (bUrl < 0 || bName < 0) break;
                    int urlStart = tail.indexOf(':', bUrl) + 1;
                    int urlEnd = tail.indexOf('"', urlStart + 1);
                    if (urlEnd < 0) break;
                    String url = tail.substring(urlStart + 1, urlEnd).trim();
                    if (url.endsWith(".apk")) {
                        apkUrl = url;
                        int ns = tail.indexOf(':', bName) + 1;
                        int ne = tail.indexOf('"', ns + 1);
                        apkName = ne > 0 ? tail.substring(ns + 1, ne) : "LightTodo.apk";
                        break;
                    }
                    idx = Math.max(bUrl, bName) + 1;
                }
            }

            if (tag == null) {
                return new Result(false, "parse", null, null, apkUrl, apkName);
            }
            return new Result(true, null, tag, notes, apkUrl, apkName);
        } catch (java.net.UnknownHostException e) {
            return new Result(false, "network", null, null, null, null);
        } catch (Exception e) {
            Log.w(TAG, "检查更新失败", e);
            return new Result(false, String.valueOf(e.getMessage()), null, null, null, null);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 从 JSON 里取一个字符串字段的原始值。 */
    private static String extract(String json, String key) {
        int at = json.indexOf(key);
        if (at < 0) return null;
        int colon = json.indexOf(':', at + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char q = json.charAt(start);
        if (q != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    default: sb.append(n);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    /**
     * 下载 APK 到应用私有目录。必须在后台线程调用。
     *
     * @return 下载好的文件，失败返回 null
     */
    public static File downloadApk(Context context, String url, String fileName,
                                   ProgressListener listener) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;

            int total = conn.getContentLength();
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = context.getFilesDir();
            if (!dir.exists() && !dir.mkdirs()) return null;
            File target = new File(dir, fileName);

            in = conn.getInputStream();
            out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            int read;
            int done = 0;
            int lastPercent = -1;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                done += read;
                if (total > 0 && listener != null) {
                    int percent = (int) (done * 100L / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
            }
            out.flush();
            return target;
        } catch (Exception e) {
            Log.w(TAG, "下载 APK 失败", e);
            return null;
        } finally {
            close(in);
            close(out);
            if (conn != null) conn.disconnect();
        }
    }

    /** 下载进度回调。 */
    public interface ProgressListener {
        void onProgress(int percent);
    }

    /**
     * 调起系统安装器安装 APK。
     *
     * @return 是否成功调起了安装界面
     */
    public static boolean installApk(Context context, File apk) {
        if (apk == null || !apk.exists()) return false;
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                // 系统里没有能装 APK 的东西（正常手机都有）
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "调起安装器失败", e);
            return false;
        }
    }

    /** 打开仓库主页。 */
    public static boolean openRepo(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 当前版本名（versionName），拿不到时返回 "0"。 */
    public static String currentVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** 供日志/展示用的版本串。 */
    public static String versionText(Context context) {
        String v = currentVersion(context);
        return String.format(Locale.getDefault(), "%s", v == null ? "0" : v);
    }

    /** Android 8 以下没有 FileProvider，直接返回 false。 */
    public static boolean canInstall(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }
}
