package com.lighttodo.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.ui.CategoryAdapter;
import com.lighttodo.app.ui.TaskEditor;
import com.lighttodo.app.util.Notifier;
import com.lighttodo.app.util.ReminderScheduler;
import com.lighttodo.app.util.TimeText;
import com.lighttodo.app.util.UpdateChecker;
import com.lighttodo.app.widget.TodoWidgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 首页：分组列表。 */
public class MainActivity extends AppCompatActivity {

    private static final String ACTION_ADD = "add";

    /** 外部（比如桌面小组件）可以直接指定打开哪个页签：0 分组，1 分类。 */
    public static final String EXTRA_TAB = "tab";

    /** 底部导航三个页面 */
    public static final int PAGE_GROUPS = 0;
    public static final int PAGE_CATEGORIES = 1;
    public static final int PAGE_SETTINGS = 2;

    private TodoStore store;
    private GroupAdapter adapter;
    private CategoryAdapter categoryAdapter;
    private View emptyView;
    private View emptyCategoryView;
    private TextView summaryView;
    private TextView summaryCategoryView;
    private Toolbar toolbar;
    /** 当前页：分组 / 分类 / 设置 */
    private int currentPage = PAGE_GROUPS;
    /** 从小组件「+」进入且当前没有分组时，建好分组直接进入新建任务。 */
    private boolean autoAddAfterCreate;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            android.widget.Toast.makeText(this, R.string.toast_deny_notify,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = TodoStore.get(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        summaryView = findViewById(R.id.summary);
        summaryCategoryView = findViewById(R.id.summary_category);
        emptyView = findViewById(R.id.empty);
        emptyCategoryView = findViewById(R.id.empty_category);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupAdapter();
        list.setAdapter(adapter);

        RecyclerView categoryList = findViewById(R.id.category_list);
        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter(store, new CategoryAdapter.Listener() {
            @Override
            public void onToggle(Task task) {
                Group group = store.groupOfTask(task);
                if (group != null) {
                    store.toggleTask(group.id, task.id);
                    refresh();
                }
            }

            @Override
            public void onEdit(Task task) {
                Group group = store.groupOfTask(task);
                if (group != null) {
                    TaskEditor.show(MainActivity.this, group.id, task, MainActivity.this::refresh);
                }
            }

            @Override
            public void onMore(Task task) {
                showTaskMenu(task);
            }
        });
        categoryList.setAdapter(categoryAdapter);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int page;
            if (item.getItemId() == R.id.nav_categories) {
                page = PAGE_CATEGORIES;
            } else if (item.getItemId() == R.id.nav_settings) {
                page = PAGE_SETTINGS;
            } else {
                page = PAGE_GROUPS;
            }
            showPage(page);
            return true;
        });
        nav.setOnItemReselectedListener(item -> {
        });

        // 设置页内容（提醒与权限 / 关于）
        buildSettingsPage();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            if (currentPage == PAGE_CATEGORIES) {
                showPickGroupDialog();
            } else {
                showAddGroupDialog();
            }
        });

        findViewById(R.id.empty_action).setOnClickListener(v -> showAddGroupDialog());

        // 桌面小组件点「分类概览」进来时，直接切到分类页
        int tab = getIntent().getIntExtra(EXTRA_TAB, -1);
        if (tab >= 0 && tab <= PAGE_CATEGORIES) {
            nav.setSelectedItemId(tab == PAGE_CATEGORIES
                    ? R.id.nav_categories : R.id.nav_groups);
        }

        if (ACTION_ADD.equals(getIntent().getAction())) {
            getIntent().setAction(null);
            showPickGroupDialog();
        }

        // 把还没触发的提醒重新登记一遍，防止系统重启后闹钟丢失
        ReminderScheduler.rescheduleAll(this);

        // 第一次进 App 就把通知权限要过来，不然到点没有通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(this)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_ADD.equals(intent.getAction())) {
            intent.setAction(null);
            showPickGroupDialog();
        }
        int tab = intent.getIntExtra(EXTRA_TAB, -1);
        if (tab >= 0 && tab <= PAGE_CATEGORIES) {
            BottomNavigationView nav = findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(tab == PAGE_CATEGORIES
                    ? R.id.nav_categories : R.id.nav_groups);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.setData(store.groups());
        int pending = store.totalPending();
        summaryView.setText(getString(R.string.main_summary,
                store.groupCount(), pending, store.totalTasks()));
        emptyView.setVisibility(store.groupCount() == 0 ? View.VISIBLE : View.GONE);

        int[] counts = store.categoryPendingCounts();
        int catPending = counts[0] + counts[1] + counts[2] + counts[3];
        summaryCategoryView.setText(getString(R.string.category_summary, catPending));
        categoryAdapter.setData(buildCategoryItems());
        emptyCategoryView.setVisibility(store.totalTasks() == 0 ? View.VISIBLE : View.GONE);
    }

    /** 切换底部导航的三个页面。 */
    private void showPage(int page) {
        currentPage = page;
        findViewById(R.id.page_groups).setVisibility(page == PAGE_GROUPS ? View.VISIBLE : View.GONE);
        findViewById(R.id.page_categories)
                .setVisibility(page == PAGE_CATEGORIES ? View.VISIBLE : View.GONE);
        findViewById(R.id.page_settings)
                .setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);

        FloatingActionButton fab = findViewById(R.id.fab);
        // 设置页没有「新建」按钮
        fab.setVisibility(page == PAGE_SETTINGS ? View.GONE : View.VISIBLE);
        fab.setContentDescription(getString(page == PAGE_CATEGORIES
                ? R.string.action_new_task : R.string.action_new_group));

        if (toolbar != null) {
            toolbar.setTitle(page == PAGE_GROUPS ? R.string.app_name
                    : page == PAGE_CATEGORIES ? R.string.tab_categories : R.string.tab_settings);
        }
        emptyView.setVisibility(View.GONE);
        emptyCategoryView.setVisibility(View.GONE);
        refresh();
    }

    /** 设置页：两个入口（提醒与权限 / 关于）。 */
    private void buildSettingsPage() {
        ViewGroup rows = findViewById(R.id.settings_rows);
        rows.removeAllViews();

        addSettingsRow(rows, R.drawable.ic_notification, R.string.settings_perm_title,
                R.string.settings_perm_desc, R.string.settings_perm_status,
                v -> startActivity(new Intent(this, AssistantActivity.class)));

        addSettingsRow(rows, R.drawable.ic_info, R.string.settings_about_title,
                R.string.settings_about_desc, R.string.settings_about_status,
                v -> startActivity(new Intent(this, AboutActivity.class)));

        ((TextView) findViewById(R.id.settings_version)).setText(
                getString(R.string.settings_version_line, UpdateChecker.versionText(this)));
    }

    private void addSettingsRow(ViewGroup parent, int iconRes, int titleRes, int descRes,
                                 int statusRes, View.OnClickListener action) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_permission, parent, false);
        ((android.widget.ImageView) row.findViewById(R.id.p_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.p_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.p_desc)).setText(descRes);
        ((TextView) row.findViewById(R.id.p_status)).setText(statusRes);
        MaterialButton btn = row.findViewById(R.id.p_action);
        btn.setText(R.string.action_view);
        btn.setOnClickListener(action);
        parent.addView(row);
    }

    /** 四个象限，每个象限下面是该分类的任务（未完成在前）。 */
    private List<Object> buildCategoryItems() {
        List<Object> items = new ArrayList<>();
        List<List<Task>> buckets = new ArrayList<>();
        for (int i = 0; i < Category.COUNT; i++) buckets.add(new ArrayList<>());
        for (Group group : store.groups()) {
            for (Task task : group.tasks) {
                buckets.get(Category.of(task)).add(task);
            }
        }
        for (int i = 0; i < Category.COUNT; i++) {
            List<Task> list = buckets.get(i);
            Collections.sort(list, new Comparator<Task>() {
                @Override
                public int compare(Task a, Task b) {
                    if (a.done != b.done) return a.done ? 1 : -1;
                    return Long.compare(a.order, b.order);
                }
            });
            int pending = 0;
            for (Task t : list) {
                if (!t.done) pending++;
            }
            items.add(new CategoryAdapter.Header(i, list.size(), pending));
            items.addAll(list);
        }
        return items;
    }

    /** 分类视图里长按任务：编辑 / 删除。 */
    private void showTaskMenu(final Task task) {
        Group group = store.groupOfTask(task);
        if (group == null) return;
        String[] items = new String[]{getString(R.string.action_edit), getString(R.string.action_delete)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(task.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        TaskEditor.show(this, group.id, task, this::refresh);
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.dialog_delete_task_title)
                                .setMessage(task.title)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.action_delete, (d2, w) -> {
                                    store.deleteTask(group.id, task.id);
                                    refresh();
                                })
                                .show();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ 分组操作

    private void showAddGroupDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        final EditText input = view.findViewById(R.id.input_name);
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_new_group)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_create, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError(getString(R.string.error_empty));
                        return;
                    }
                    Group created = store.addGroup(name);
                    refresh();
                    dialog.dismiss();
                    if (autoAddAfterCreate) {
                        autoAddAfterCreate = false;
                        Intent intent = new Intent(this, GroupActivity.class);
                        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, created.id);
                        intent.putExtra(GroupActivity.EXTRA_AUTO_ADD, true);
                        startActivity(intent);
                    }
                }));
        dialog.show();
    }

    private void showRenameGroupDialog(final Group group) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        final EditText input = view.findViewById(R.id.input_name);
        input.setText(group.name);
        input.setSelection(group.name.length());
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_rename_group)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError(getString(R.string.error_empty));
                        return;
                    }
                    store.renameGroup(group.id, name);
                    refresh();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmDeleteGroup(final Group group) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_group_title)
                .setMessage(getString(R.string.dialog_delete_group_message, group.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    store.deleteGroup(group.id);
                    refresh();
                })
                .show();
    }

    /** 供小组件「+」按钮调用：选一个分组后直接新建任务。 */
    private void showPickGroupDialog() {
        final List<Group> groups = store.groups();
        if (groups.isEmpty()) {
            autoAddAfterCreate = true;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_need_group_title)
                    .setMessage(R.string.dialog_need_group_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_create, (d, w) -> showAddGroupDialog())
                    .show();
            return;
        }
        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            names[i] = g.name + "（" + g.pendingCount() + " 项待办）";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_pick_group)
                .setItems(names, (d, which) -> {
                    Intent intent = new Intent(this, GroupActivity.class);
                    intent.putExtra(GroupActivity.EXTRA_GROUP_ID, groups.get(which).id);
                    intent.putExtra(GroupActivity.EXTRA_AUTO_ADD, true);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        if (id == R.id.action_refresh_widget) {
            TodoWidgets.refreshAll(this);
            return true;
        }
        if (id == R.id.action_assistant) {
            startActivity(new Intent(this, AssistantActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------ 适配器

    private class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {

        private final List<Group> data = new ArrayList<>();

        void setData(List<Group> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final Group group = data.get(position);
            holder.name.setText(group.name);
            holder.count.setText(getString(R.string.group_pending_badge, group.pendingCount()));
            holder.progress.setText(getString(R.string.group_progress, group.doneCount(), group.tasks.size()));
            holder.progressBar.setMax(Math.max(1, group.tasks.size()));
            holder.progressBar.setProgress(group.doneCount());

            List<com.lighttodo.app.model.Task> pending = group.pendingTasks();
            if (pending.isEmpty()) {
                holder.preview.setText(group.tasks.isEmpty()
                        ? getString(R.string.group_empty_hint)
                        : getString(R.string.group_all_done_hint));
            } else {
                StringBuilder sb = new StringBuilder();
                int shown = Math.min(3, pending.size());
                for (int i = 0; i < shown; i++) {
                    if (i > 0) sb.append('\n');
                    Task t = pending.get(i);
                    sb.append("· ").append(t.title);
                    if (t.hasDue()) {
                        sb.append("（").append(TimeText.friendly(t.dueAt)).append("）");
                    }
                }
                if (pending.size() > shown) {
                    sb.append("\n… 还有 ").append(pending.size() - shown).append(" 项");
                }
                holder.preview.setText(sb.toString());
            }

            bindAlert(holder, group);

            holder.itemView.setOnClickListener(v -> openGroup(group));
            holder.itemView.setOnLongClickListener(v -> {
                showGroupMenu(group);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        /** 逾期提示：优先显示逾期数量，否则显示最近一个截止时间。 */
        private void bindAlert(GroupAdapter.VH holder, Group group) {
            long now = System.currentTimeMillis();
            int overdue = 0;
            Task nearest = null;
            for (Task t : group.tasks) {
                if (t.isOverdue(now)) overdue++;
                if (t.hasDue() && !t.done && (nearest == null || t.dueAt < nearest.dueAt)) {
                    nearest = t;
                }
            }
            if (overdue > 0) {
                holder.alert.setVisibility(View.VISIBLE);
                holder.alert.setText(getString(R.string.group_overdue_alert, overdue));
                holder.alert.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.warn_due));
            } else if (nearest != null) {
                holder.alert.setVisibility(View.VISIBLE);
                holder.alert.setText(getString(R.string.group_nearest_due, TimeText.friendly(nearest.dueAt)));
                holder.alert.setTextColor(MaterialColors.getColor(holder.alert,
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
            } else {
                holder.alert.setVisibility(View.GONE);
            }
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView count;
            final TextView progress;
            final TextView preview;
            final TextView alert;
            final android.widget.ProgressBar progressBar;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.g_name);
                count = itemView.findViewById(R.id.g_count);
                progress = itemView.findViewById(R.id.g_progress);
                preview = itemView.findViewById(R.id.g_preview);
                alert = itemView.findViewById(R.id.g_alert);
                progressBar = itemView.findViewById(R.id.g_progress_bar);
            }
        }
    }

    private void openGroup(Group group) {
        Intent intent = new Intent(this, GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, group.id);
        startActivity(intent);
    }

    private void showGroupMenu(final Group group) {
        String[] items = new String[]{getString(R.string.action_rename), getString(R.string.action_delete)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(group.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) showRenameGroupDialog(group);
                    else confirmDeleteGroup(group);
                })
                .show();
    }
}
