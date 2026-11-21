package com.lighttodo.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.lighttodo.app.R;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;

/**
 * 小组件配置页：加「某个组的任务」「某个类的任务」小组件时，选它要显示什么。
 *
 * <p>每个小组件实例的设置按 appWidgetId 单独存，所以桌面可以叠好几个，各看各的。</p>
 */
public class WidgetConfigActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_GROUP = "group";
    public static final String MODE_CATEGORY = "category";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_config);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        String mode = resolveMode();
        if (MODE_GROUP.equals(mode)) {
            showGroups();
        } else {
            showCategories();
        }
    }

    /** 桌面拉起配置页时只带 appWidgetId，靠是哪个小组件来判断选分组还是选分类。 */
    private String resolveMode() {
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode != null) return mode;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(appWidgetId);
            if (info != null && info.provider != null) {
                String name = info.provider.getClassName();
                if (GroupTaskWidgetProvider.class.getName().equals(name)) return MODE_GROUP;
                if (CategoryTaskWidgetProvider.class.getName().equals(name)) return MODE_CATEGORY;
            }
        } catch (Throwable ignored) {
        }
        return MODE_GROUP;
    }

    private void showGroups() {
        TextView hint = findViewById(R.id.hint);
        hint.setText(R.string.widget_config_pick_group);
        setTitle(R.string.widget_config_pick_group);

        TodoStore store = TodoStore.get(this);
        ViewGroup rows = findViewById(R.id.rows);
        rows.removeAllViews();
        boolean any = false;
        for (Group group : store.groups()) {
            any = true;
            View row = LayoutInflater.from(this).inflate(R.layout.item_widget_config, rows, false);
            ((View) row.findViewById(R.id.c_dot)).setBackgroundResource(R.drawable.w_dot_not_important_not_urgent);
            ((TextView) row.findViewById(R.id.c_name)).setText(group.name);
            ((TextView) row.findViewById(R.id.c_count)).setText(
                    getString(R.string.widget_config_count, group.pendingCount()));
            row.setOnClickListener(v -> {
                WidgetConfig.setGroup(this, appWidgetId, group.id);
                finishWithResult(WidgetFactory.buildGroupTasks(this, appWidgetId, 110, 180));
            });
            rows.addView(row);
        }
        setEmpty(!any);
    }

    private void showCategories() {
        TextView hint = findViewById(R.id.hint);
        hint.setText(R.string.widget_config_pick_category);
        setTitle(R.string.widget_config_pick_category);

        TodoStore store = TodoStore.get(this);
        int[] counts = store.categoryPendingCounts();
        ViewGroup rows = findViewById(R.id.rows);
        rows.removeAllViews();
        for (int cat = 0; cat < Category.COUNT; cat++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_widget_config, rows, false);
            View dot = row.findViewById(R.id.c_dot);
            dot.getBackground().setTint(ContextCompat.getColor(this, Category.colorRes(cat)));
            ((TextView) row.findViewById(R.id.c_name)).setText(Category.nameRes(cat));
            ((TextView) row.findViewById(R.id.c_count)).setText(
                    getString(R.string.widget_config_count, counts[cat]));
            final int category = cat;
            row.setOnClickListener(v -> {
                WidgetConfig.setCategory(this, appWidgetId, category);
                finishWithResult(WidgetFactory.buildCategoryTasks(this, appWidgetId, 110, 180));
            });
            rows.addView(row);
        }
        setEmpty(false);
    }

    private void setEmpty(boolean empty) {
        View view = findViewById(R.id.empty);
        view.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    /** 保存好设置，把小组件画出来，然后告诉桌面「配置完成」。 */
    private void finishWithResult(android.widget.RemoteViews views) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (views != null) manager.updateAppWidget(appWidgetId, views);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
