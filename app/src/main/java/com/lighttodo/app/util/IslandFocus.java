package com.lighttodo.app.util;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.lighttodo.app.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * 小米澎湃系统的「焦点通知 / 超级岛（灵动岛）」适配。
 *
 * <p>系统侧识别方式是在通知的 extras 里放一份 {@code miui.focus.param} JSON，
 * 图片放 {@code miui.focus.pics}（Bundle），点击动作放 {@code miui.focus.actions}（Bundle）。
 * 字段名严格按小米官方《超级岛推送指南 / 开发指南》里的结构，不自己发明。</p>
 *
 * <p><b>重要教训（v1.3 踩坑）：</b>不要拿「某个 Settings 里的协议号」当作能不能上岛的开关。
 * 那个 key 在澎湃系统上根本读不到（读回来是 0），一旦拿它当门槛，
 * 就会连 {@code miui.focus.param} 都不挂，通知退化成普通通知，永远上不了岛。
 * 正确做法是<b>无条件把参数挂上</b>，让系统自己判断能不能渲染；
 * 官方参数 {@code filterWhenNoPermission=false} 保证「没焦点通知权限时退化成普通通知」，
 * 不会因为挂了参数就把通知本身也丢掉。</p>
 *
 * <p>能不能真的显示成岛，取决于三件事（本机）：</p>
 * <ul>
 *     <li>系统带超级岛能力：{@code persist.sys.feature.island}</li>
 *     <li>应用被允许显示焦点通知：{@code content://miui.statusbar.notification.public} 的 canShowFocus</li>
 *     <li>用户在系统里给本应用打开了「焦点通知」开关（设置 → 通知与控制中心 → 焦点通知）</li>
 * </ul>
 */
public final class IslandFocus {

    /** 焦点 / 超级岛主参数（JSON 字符串） */
    public static final String EXTRA_PARAM = "miui.focus.param";
    /** 图片资源 Bundle */
    public static final String EXTRA_PICS = "miui.focus.pics";
    /** 点击动作 Bundle */
    public static final String EXTRA_ACTIONS = "miui.focus.actions";

    /** 大岛 / 息屏 / 状态栏三处共用的图片 key */
    private static final String PIC_IMAGE_TEXT = "miui.focus.pic_imageText";
    private static final String PIC_TICKER = "miui.focus.pic_ticker";
    private static final String PIC_AOD = "miui.focus.pic_aod";
    /** 「完成」动作在 actions Bundle 里的 key */
    private static final String ACTION_COMPLETE = "miui.focus.action_complete";

    private static final String FOCUS_PROVIDER = "content://miui.statusbar.notification.public";
    private static final String KEY_PROTOCOL = "notification_focus_protocol";
    private static final String TAG = "IslandFocus";

    private static final String PREFS = "lighttodo_settings";
    private static final String KEY_MODE = "island_mode";

    // ------------------------------------------------------------ 模式

    /** 自动：小米系设备（或系统有岛能力）就带上上岛参数 */
    public static final int MODE_AUTO = 0;
    /** 强制：不管什么设备都带上上岛参数（排查用） */
    public static final int MODE_FORCE = 1;
    /** 关闭：完全不发上岛参数，只发普通通知 */
    public static final int MODE_OFF = 2;

    // ------------------------------------------------------------ 权限状态

    /** 查不到焦点通知权限（老系统 / 没有这个 provider） */
    public static final int PERM_UNKNOWN = 0;
    /** 已允许显示焦点通知 */
    public static final int PERM_ALLOWED = 1;
    /** 明确被拒绝 */
    public static final int PERM_DENIED = 2;

    /** 测试用的协议版本覆盖（-1 表示用系统真实值）。 */
    private static volatile int sProtocolOverride = -1;
    /** 测试用的「是否小米设备」覆盖（null 表示用系统真实值）。 */
    private static volatile Boolean sXiaomiOverride = null;

    private IslandFocus() {
    }

    /** 只给单测用：强制指定焦点通知协议版本。 */
    public static void setProtocolOverrideForTest(int protocol) {
        sProtocolOverride = protocol;
    }

    /** 只给单测用：强制指定「是否小米系设备」。 */
    public static void setXiaomiOverrideForTest(Boolean value) {
        sXiaomiOverride = value;
    }

    // ------------------------------------------------------------ 模式开关

    public static int mode(Context context) {
        if (context == null) return MODE_AUTO;
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_MODE, MODE_AUTO);
        } catch (Throwable t) {
            return MODE_AUTO;
        }
    }

    public static void setMode(Context context, int mode) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).apply();
    }

    // ------------------------------------------------------------ 能力探测

    /** 是不是小米 / 红米 / POCO 系设备。 */
    public static boolean isXiaomiDevice() {
        if (sXiaomiOverride != null) return sXiaomiOverride;
        String[] candidates = {
                safe(Build.MANUFACTURER), safe(Build.BRAND), safe(Build.PRODUCT)
        };
        for (String value : candidates) {
            String v = value.toLowerCase();
            if (v.contains("xiaomi") || v.contains("redmi") || v.contains("poco")) {
                return true;
            }
        }
        return false;
    }

    /** 系统是否带超级岛能力（澎湃 OS3 的 persist.sys.feature.island）。 */
    public static boolean hasIslandFeature() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method get = clazz.getMethod("getBoolean", String.class, boolean.class);
            Object value = get.invoke(null, "persist.sys.feature.island", Boolean.FALSE);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 焦点通知协议版本（只用于展示诊断信息，<b>不作为能不能上岛的判据</b>）。
     * 三个来源都试一遍：Settings.System / Settings.Global / 系统属性。
     */
    public static int focusProtocol(Context context) {
        if (sProtocolOverride >= 0) return sProtocolOverride;
        if (context == null) return 0;
        ContentResolver cr = context.getContentResolver();
        int value = 0;
        try {
            value = Settings.System.getInt(cr, KEY_PROTOCOL, 0);
        } catch (Throwable ignored) {
        }
        if (value == 0) {
            try {
                value = Settings.Global.getInt(cr, KEY_PROTOCOL, 0);
            } catch (Throwable ignored) {
            }
        }
        if (value == 0) {
            value = systemPropertyInt("persist.sys." + KEY_PROTOCOL);
        }
        return value;
    }

    /**
     * 应用有没有被允许显示焦点通知。
     * 必须把包名放进 Bundle 里传过去，否则系统不知道问的是哪个应用。
     */
    public static int focusPermissionState(Context context) {
        if (context == null) return PERM_UNKNOWN;
        try {
            Bundle args = new Bundle();
            args.putString("package", context.getPackageName());
            Bundle result = context.getContentResolver()
                    .call(Uri.parse(FOCUS_PROVIDER), "canShowFocus", null, args);
            if (result == null) return PERM_UNKNOWN;
            if (result.containsKey("canShowFocus")) {
                return result.getBoolean("canShowFocus") ? PERM_ALLOWED : PERM_DENIED;
            }
            if (result.containsKey("value")) {
                return result.getBoolean("value") ? PERM_ALLOWED : PERM_DENIED;
            }
            if (result.containsKey("result")) {
                return result.getBoolean("result") ? PERM_ALLOWED : PERM_DENIED;
            }
        } catch (Throwable t) {
            Log.d(TAG, "查询焦点通知权限失败", t);
        }
        return PERM_UNKNOWN;
    }

    /** 焦点通知权限：没有被明确拒绝就算可以用。 */
    public static boolean canShowFocus(Context context) {
        return focusPermissionState(context) != PERM_DENIED;
    }

    /** 能不能发焦点通知（澎湃 OS2 起）。 */
    public static boolean isFocusSupported(Context context) {
        return isXiaomiDevice() || hasIslandFeature() || focusProtocol(context) >= 2;
    }

    /** 能不能上超级岛（澎湃 OS3 起，还要系统开着岛能力）。 */
    public static boolean isIslandSupported(Context context) {
        return hasIslandFeature() || focusProtocol(context) >= 3;
    }

    /** 现在这条通知到底会不会带上上岛参数。 */
    public static boolean willAttach(Context context) {
        int mode = mode(context);
        if (mode == MODE_OFF) return false;
        if (mode == MODE_FORCE) return true;
        return isXiaomiDevice() || hasIslandFeature();
    }

    // ------------------------------------------------------------ 参数构造

    /**
     * 给通知挂上焦点通知 / 超级岛参数（挂在 Builder 的 extras 上）。
     *
     * @return true 表示已经挂上了；false 表示这台机器用不着或构造失败（普通通知照发）
     */
    public static boolean apply(NotificationCompat.Builder builder, Context context,
                                String title, String content, String groupName,
                                PendingIntent completeIntent) {
        if (builder == null || context == null) return false;
        if (!willAttach(context)) return false;
        try {
            Bundle extras = new Bundle();
            extras.putString(EXTRA_PARAM, buildParamJson(title, content, groupName, true));
            extras.putBundle(EXTRA_PICS, buildPics(context));
            if (completeIntent != null) {
                extras.putBundle(EXTRA_ACTIONS, buildActions(context, completeIntent));
            }
            builder.setExtras(extras);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "构造上岛参数失败，退回普通通知", t);
            return false;
        }
    }

    /**
     * 给<b>已经 build 好</b>的通知挂上焦点通知 / 超级岛参数。
     * 官方和社区实现都是 build 之后再写 {@code notification.extras}，这里照做。
     *
     * @return true 表示已经挂上了
     */
    public static boolean applyTo(Notification notification, Context context,
                                  String title, String content, String groupName,
                                  PendingIntent completeIntent) {
        if (notification == null || context == null) return false;
        if (!willAttach(context)) return false;
        try {
            Bundle extras = notification.extras != null ? notification.extras : new Bundle();
            extras.putString(EXTRA_PARAM, buildParamJson(title, content, groupName, true));
            extras.putBundle(EXTRA_PICS, buildPics(context));
            if (completeIntent != null) {
                extras.putBundle(EXTRA_ACTIONS, buildActions(context, completeIntent));
            }
            if (notification.extras == null) notification.extras = extras;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "构造上岛参数失败，退回普通通知", t);
            return false;
        }
    }

    /**
     * 拼出 miui.focus.param 的 JSON。
     * 字段名按小米官方《超级岛推送指南》：
     * <ul>
     *     <li>param_v2.protocol = 1（官方示例值，OS2/OS3 数据接口相同）</li>
     *     <li>param_v2.timeout 单位是<b>分钟</b>（官方文档写明），720 = 12 小时</li>
     *     <li>param_v2.islandTimeout 单位是<b>秒</b></li>
     *     <li>param_v2.filterWhenNoPermission = false（没权限时退化成普通通知，不被过滤）</li>
     * </ul>
     */
    public static String buildParamJson(String title, String content, String groupName,
                                        boolean withIsland) throws JSONException {
        JSONObject v2 = new JSONObject();
        v2.put("protocol", 1);
        v2.put("business", "lighttodo");
        v2.put("enableFloat", true);
        v2.put("updatable", false);
        v2.put("islandFirstFloat", true);
        v2.put("timeout", 720);
        v2.put("filterWhenNoPermission", false);
        v2.put("ticker", safe(title));
        v2.put("tickerPic", PIC_TICKER);
        v2.put("aodTitle", aodTitle(title, groupName));
        v2.put("aodPic", PIC_AOD);
        v2.put("baseInfo", baseInfo(title, content));
        v2.put("hintInfo", hintInfo(groupName));
        v2.put("actions", actionRefs());
        if (withIsland) {
            v2.put("param_island", islandData(title, groupName));
        }

        JSONObject root = new JSONObject();
        root.put("param_v2", v2);
        return root.toString();
    }

    /** 息屏（AOD）显示的文案，带上分组。 */
    private static String aodTitle(String title, String groupName) {
        String t = safe(title);
        if (groupName == null || groupName.isEmpty()) return t;
        return t + " · " + groupName;
    }

    /** 焦点通知卡片：标题 + 正文 + 强调色。 */
    private static JSONObject baseInfo(String title, String content) throws JSONException {
        JSONObject info = new JSONObject();
        info.put("type", 2);
        info.put("title", safe(title));
        info.put("content", safe(content));
        info.put("colorTitle", "#1F4E79");
        info.put("picFunction", PIC_IMAGE_TEXT);
        return info;
    }

    /** 顶部提示条：点一下就是「完成」。 */
    private static JSONObject hintInfo(String groupName) throws JSONException {
        JSONObject actionInfo = new JSONObject();
        actionInfo.put("action", ACTION_COMPLETE);

        JSONObject hint = new JSONObject();
        hint.put("type", 1);
        hint.put("title", groupName == null || groupName.isEmpty()
                ? "轻待办提醒" : groupName + " · 轻待办提醒");
        hint.put("actionInfo", actionInfo);
        return hint;
    }

    /** 动作引用表：指向 miui.focus.actions 里那个 Notification.Action。 */
    private static JSONArray actionRefs() throws JSONException {
        JSONObject ref = new JSONObject();
        ref.put("type", 0);
        ref.put("action", ACTION_COMPLETE);
        ref.put("actionIntentType", 2);
        ref.put("actionTitle", "完成");

        JSONArray array = new JSONArray();
        array.put(ref);
        return array;
    }

    /**
     * 岛数据：大岛左边是「图标 + 文字」，小岛只放图标。
     * 文本信息同时用官方《超级岛推送指南》的 {@code miui.focus.paramtextInfo}
     * 和模板库的 {@code textInfo} 两种写法，系统认哪套就用哪套。
     */
    private static JSONObject islandData(String title, String groupName) throws JSONException {
        JSONObject picInfo = new JSONObject();
        picInfo.put("type", 1);
        picInfo.put("pic", PIC_IMAGE_TEXT);

        JSONObject textOfficial = new JSONObject();
        textOfficial.put("frontTitle", "待办");
        textOfficial.put("title", safe(title));
        textOfficial.put("content", safe(groupName));
        textOfficial.put("useHighLight", false);

        JSONObject textKit = new JSONObject();
        textKit.put("title", safe(title));
        textKit.put("content", safe(groupName));
        textKit.put("showHighlightColor", false);

        JSONObject left = new JSONObject();
        left.put("type", 1);
        left.put("picInfo", picInfo);
        left.put("miui.focus.paramtextInfo", textOfficial);
        left.put("textInfo", textKit);

        JSONObject big = new JSONObject();
        big.put("imageTextInfoLeft", left);
        big.put("picInfo", picInfo);

        JSONObject small = new JSONObject();
        small.put("picInfo", picInfo);

        JSONObject island = new JSONObject();
        island.put("islandProperty", 1);
        island.put("islandPriority", 2);
        island.put("islandTimeout", 300);
        island.put("dismissIsland", true);
        island.put("needCloseAnimation", true);
        island.put("bigIslandArea", big);
        island.put("smallIslandArea", small);
        return island;
    }

    /** 图片资源 Bundle：状态栏 / 息屏 / 岛都用应用小图标。 */
    public static Bundle buildPics(Context context) {
        Bundle pics = new Bundle();
        Icon icon = Icon.createWithResource(context, R.drawable.ic_notification);
        pics.putParcelable(PIC_IMAGE_TEXT, icon);
        pics.putParcelable(PIC_TICKER, icon);
        pics.putParcelable(PIC_AOD, icon);
        return pics;
    }

    /** 点击动作 Bundle：岛上的「完成」。 */
    public static Bundle buildActions(Context context, PendingIntent completeIntent) {
        Bundle actions = new Bundle();
        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_check_white),
                context.getString(R.string.action_complete),
                completeIntent).build();
        actions.putParcelable(ACTION_COMPLETE, action);
        return actions;
    }

    private static int systemPropertyInt(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method get = clazz.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            if (value instanceof String) {
                try {
                    return Integer.parseInt(((String) value).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
