package com.lighttodo.app;

import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.ui.TaskEditor;
import com.lighttodo.app.util.TimeText;

import java.util.ArrayList;
import java.util.List;

/** 分组详情：该分组下的全部任务，完成的任务自动沉底。 */
public class GroupActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_AUTO_ADD = "auto_add";

    private TodoStore store;
    private String groupId;
    private Group group;
    private TaskAdapter adapter;
    private View emptyView;
    private TextView subtitleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group);

        store = TodoStore.get(this);
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        group = store.findGroup(groupId);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        subtitleView = findViewById(R.id.subtitle);
        emptyView = findViewById(R.id.empty);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter();
        list.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> TaskEditor.show(this, groupId, null, this::refresh));

        if (getIntent().getBooleanExtra(EXTRA_AUTO_ADD, false)) {
            getIntent().removeExtra(EXTRA_AUTO_ADD);
            fab.post(() -> TaskEditor.show(this, groupId, null, this::refresh));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        group = store.findGroup(groupId);
        if (group == null) {
            finish();
            return;
        }
        setTitle(group.name);
        int overdue = 0;
        long now = System.currentTimeMillis();
        for (Task t : group.tasks) {
            if (t.isOverdue(now)) overdue++;
        }
        if (overdue > 0) {
            subtitleView.setText(getString(R.string.group_subtitle_overdue,
                    group.pendingCount(), group.tasks.size(), overdue));
        } else {
            subtitleView.setText(getString(R.string.group_subtitle,
                    group.pendingCount(), group.tasks.size()));
        }
        adapter.setData(buildItems(group));
        if (group.tasks.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    /** 未完成任务在前；完成后追加一个「已完成」分隔标题。 */
    private List<Object> buildItems(Group g) {
        List<Object> items = new ArrayList<>();
        List<Task> sorted = g.sortedTasks();
        boolean headerAdded = false;
        for (Task t : sorted) {
            if (t.done && !headerAdded) {
                items.add(new Header(g.doneCount()));
                headerAdded = true;
            }
            items.add(t);
        }
        return items;
    }

    // ------------------------------------------------------------ 任务操作

    private void confirmDeleteTask(final Task task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_task_title)
                .setMessage(task.title)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    store.deleteTask(groupId, task.id);
                    refresh();
                })
                .show();
    }

    private void showTaskMenu(final Task task) {
        String[] items = new String[]{getString(R.string.action_edit), getString(R.string.action_delete)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(task.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        TaskEditor.show(this, groupId, task, this::refresh);
                    } else {
                        confirmDeleteTask(task);
                    }
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_rename_group) {
            showRenameGroupDialog();
            return true;
        }
        if (id == R.id.action_delete_group) {
            confirmDeleteGroup();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRenameGroupDialog() {
        if (group == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        final EditText input = view.findViewById(R.id.input_name);
        input.setText(group.name);
        input.setSelection(group.name.length());
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_rename_group)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(getString(R.string.error_empty));
                return;
            }
            store.renameGroup(groupId, name);
            refresh();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void confirmDeleteGroup() {
        if (group == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_group_title)
                .setMessage(getString(R.string.dialog_delete_group_message, group.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    store.deleteGroup(groupId);
                    finish();
                })
                .show();
    }

    // ------------------------------------------------------------ 适配器

    private static class Header {
        final int count;

        Header(int count) {
            this.count = count;
        }
    }

    private class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_TASK = 0;
        private static final int TYPE_HEADER = 1;

        private final List<Object> items = new ArrayList<>();

        void setData(List<Object> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof Header ? TYPE_HEADER : TYPE_TASK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(inflater.inflate(R.layout.item_header, parent, false));
            }
            return new TaskVH(inflater.inflate(R.layout.item_task, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).title.setText(getString(R.string.header_done, ((Header) item).count));
                return;
            }
            final Task task = (Task) item;
            TaskVH vh = (TaskVH) holder;
            vh.title.setText(task.title);
            vh.title.setPaintFlags(task.done
                    ? vh.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : vh.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            vh.title.setAlpha(task.done ? 0.45f : 1f);
            vh.check.setImageResource(task.done ? R.drawable.ic_check_circle_filled : R.drawable.ic_radio_unchecked);
            if (task.done) {
                vh.check.clearColorFilter();
            } else {
                int color = MaterialColors.getColor(vh.check, com.google.android.material.R.attr.colorOnSurfaceVariant);
                vh.check.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            vh.check.setContentDescription(task.done
                    ? getString(R.string.cd_uncheck) : getString(R.string.cd_check));

            if (task.note == null || task.note.trim().isEmpty()) {
                vh.note.setVisibility(View.GONE);
            } else {
                vh.note.setVisibility(View.VISIBLE);
                vh.note.setText(task.note);
                vh.note.setAlpha(task.done ? 0.35f : 0.75f);
            }

            // 分类色点
            int catColor = ContextCompat.getColor(GroupActivity.this, Category.colorRes(Category.of(task)));
            vh.dot.getBackground().setTint(catColor);
            vh.dot.setVisibility(View.VISIBLE);
            vh.dot.setContentDescription(getString(Category.nameRes(Category.of(task))));

            bindTimeInfo(vh, task);

            vh.check.setOnClickListener(v -> {
                store.toggleTask(groupId, task.id);
                refresh();
            });
            vh.itemView.setOnClickListener(v -> TaskEditor.show(GroupActivity.this, groupId, task, GroupActivity.this::refresh));
            vh.itemView.setOnLongClickListener(v -> {
                showTaskMenu(task);
                return true;
            });
        }

        /** 截止时间和提醒时间的显示：逾期标红、今天标橙。 */
        private void bindTimeInfo(TaskVH vh, Task task) {
            long now = System.currentTimeMillis();
            boolean hasDue = task.hasDue();
            boolean hasRemind = task.hasReminder();
            vh.meta.setVisibility(hasDue || hasRemind ? View.VISIBLE : View.GONE);

            int normal = MaterialColors.getColor(vh.title, com.google.android.material.R.attr.colorOnSurfaceVariant);
            int overdueColor = ContextCompat.getColor(GroupActivity.this, R.color.warn_due);
            int todayColor = ContextCompat.getColor(GroupActivity.this, R.color.warn_today);

            if (hasDue) {
                vh.dueIcon.setVisibility(View.VISIBLE);
                vh.due.setVisibility(View.VISIBLE);
                if (task.isOverdue(now)) {
                    vh.due.setText(getString(R.string.due_overdue, TimeText.friendly(task.dueAt)));
                    vh.due.setTextColor(overdueColor);
                    vh.dueIcon.setColorFilter(overdueColor, PorterDuff.Mode.SRC_IN);
                } else {
                    vh.due.setText(getString(R.string.due_at, TimeText.friendly(task.dueAt)));
                    if (TimeText.isToday(task.dueAt)) {
                        vh.due.setTextColor(todayColor);
                        vh.dueIcon.setColorFilter(todayColor, PorterDuff.Mode.SRC_IN);
                    } else {
                        vh.due.setTextColor(normal);
                        vh.dueIcon.setColorFilter(normal, PorterDuff.Mode.SRC_IN);
                    }
                }
            } else {
                vh.dueIcon.setVisibility(View.GONE);
                vh.due.setVisibility(View.GONE);
            }

            if (hasRemind) {
                vh.remindIcon.setVisibility(View.VISIBLE);
                vh.remind.setVisibility(View.VISIBLE);
                vh.remind.setText(getString(R.string.remind_at, TimeText.friendly(task.remindAt)));
                vh.remind.setTextColor(normal);
                vh.remindIcon.setColorFilter(normal, PorterDuff.Mode.SRC_IN);
            } else {
                vh.remindIcon.setVisibility(View.GONE);
                vh.remind.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class TaskVH extends RecyclerView.ViewHolder {
            final ImageView check;
            final TextView title;
            final TextView note;
            final View dot;
            final View meta;
            final ImageView dueIcon;
            final TextView due;
            final ImageView remindIcon;
            final TextView remind;

            TaskVH(@NonNull View itemView) {
                super(itemView);
                check = itemView.findViewById(R.id.t_check);
                title = itemView.findViewById(R.id.t_title);
                note = itemView.findViewById(R.id.t_note);
                dot = itemView.findViewById(R.id.t_dot);
                meta = itemView.findViewById(R.id.t_meta);
                dueIcon = itemView.findViewById(R.id.t_due_icon);
                due = itemView.findViewById(R.id.t_due);
                remindIcon = itemView.findViewById(R.id.t_remind_icon);
                remind = itemView.findViewById(R.id.t_remind);
            }
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            final TextView title;

            HeaderVH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.h_title);
            }
        }
    }
}
