package com.lighttodo.app.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * 小米小部件（MIUI Widget / 澎湃 OS 小组件）相关的常量与能力探测。
 *
 * <p>澎湃系统有一套自己的「小米小部件」体系，和普通 Android 组件（AppWidget）不是一回事：
 * 普通组件进不了负一屏，而带 {@code miuiWidget} 标识的小组件才会被系统识别为
 * 「小米小部件」，从而有机会出现在负一屏（智能助理）和小部件中心里。</p>
 *
 * <p>官方规范见《小米小部件技术规范与系统能力说明》：</p>
 * <ul>
 *   <li>小组件要跑在 {@code :widgetProvider} 独立进程；</li>
 *   <li>receiver 上加 {@code miuiWidget=true} 标识才会享受小米小部件能力；</li>
 *   <li>系统会去掉原生定时刷新，改成「曝光刷新」，需要声明
 *       {@code miuiWidgetRefresh=exposure} 和最小间隔 {@code miuiWidgetRefreshMinInterval}；</li>
 *   <li>曝光刷新通过 {@code miui.appwidget.action.APPWIDGET_UPDATE} 广播下发；</li>
 *   <li>布局根节点要写死 {@code @android:id/background}，系统靠这个固定 ID 统一裁圆角，
 *       并且根布局必须有背景色、不能全透明；</li>
 *   <li>深色模式只能在 xml 里静态适配（{@code drawable-night} / {@code values-night}）。</li>
 * </ul>
 */
public final class MiuiWidget {

    private static final String TAG = "MiuiWidget";

    /** 小米的「曝光刷新」广播 action。 */
    public static final String ACTION_EXPOSURE_REFRESH = "miui.appwidget.action.APPWIDGET_UPDATE";

    /** 小米小部件能力探测用的 ContentProvider。 */
    public static final Uri SUPPORT_URI =
            Uri.parse("content://com.miui.personalassistant.widget.external");

    /** 小组件独立进程名（官方规定）。 */
    public static final String WIDGET_PROCESS = ":widgetProvider";

    private MiuiWidget() {
    }

    /**
     * 这台手机是否支持小米小部件。
     *
     * @return true=支持；false=不支持或读不到
     */
    public static boolean isSupported(Context context) {
        return queryFlag(context, "isMiuiWidgetSupported");
    }

    /**
     * 这台手机是否支持「小米小部件详情页」（即能不能调起小部件商店里的详情页）。
     *
     * @return true=支持；false=不支持或读不到
     */
    public static boolean isDetailPageSupported(Context context) {
        return queryFlag(context, "isMiuiWidgetDetailPageSupported");
    }

    private static boolean queryFlag(Context context, String method) {
        try {
            android.os.Bundle bundle = context.getContentResolver()
                    .call(SUPPORT_URI, method, null, null);
            return bundle != null && bundle.getBoolean(method);
        } catch (Exception e) {
            // 非小米手机 / 老系统上这个 provider 根本不存在，属正常情况
            Log.d(TAG, "查询 " + method + " 失败：" + e.getMessage());
            return false;
        }
    }
}
