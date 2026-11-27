package com.lighttodo.app.widget;

/**
 * 「分类概览」的<b>普通安卓组件</b>版本。
 *
 * <p>为什么要有这一套：带 {@code miuiWidget} 标识的版本走的是小米小部件通道，
 * 必须经过小米商店上架 + 小部件平台审核才会出现在小部件中心；没上架之前，
 * 系统会把它从负一屏的「安卓组件」分类里也一并摘掉，结果就是两边都找不到。</p>
 *
 * <p>这一套不带任何小米标识，就是标准的 AppWidgetProvider，
 * 因此在负一屏「安卓组件」和桌面的小组件列表里都能直接搜到。</p>
 *
 * <p>功能与 MIUI 版完全一致（继承过来），连刷新按钮、配置页都一样；
 * 唯一的区别是这一套靠系统定时刷新，那一套靠小米曝光刷新。</p>
 */
public class PlainCategoryWidgetProvider extends CategoryWidgetProvider {
}
