package com.lighttodo.app.ui;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.lighttodo.app.R;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.TimeText;

import java.util.ArrayList;
import java.util.List;

/** 分类视图：四个象限，每个象限下面是该分类的任务。 */
public class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onToggle(Task task);

        void onEdit(Task task);

        void onMore(Task task);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK = 1;

    /** 一个象限的表头 */
    public static class Header {
        public final int category;
        public final int total;
        public final int pending;

        public Header(int category, int total, int pending) {
            this.category = category;
            this.total = total;
            this.pending = pending;
        }
    }

    private final List<Object> items = new ArrayList<>();
    private final TodoStore store;
    private final Listener listener;

    public CategoryAdapter(TodoStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    public void setData(List<Object> list) {
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
            return new HeaderVH(inflater.inflate(R.layout.item_category_header, parent, false));
        }
        return new TaskVH(inflater.inflate(R.layout.item_task, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof HeaderVH) {
            Header header = (Header) item;
            HeaderVH vh = (HeaderVH) holder;
            int color = ContextCompat.getColor(vh.itemView.getContext(), Category.colorRes(header.category));
            vh.dot.getBackground().setTint(color);
            vh.name.setText(Category.nameRes(header.category));
            vh.name.setTextColor(color);
            vh.count.setText(vh.itemView.getContext().getString(
                    R.string.category_count, header.pending, header.total));
            return;
        }

        final Task task = (Task) item;
        TaskVH vh = (TaskVH) holder;
        vh.title.setText(task.title);
        vh.title.setPaintFlags(task.done
                ? vh.title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                : vh.title.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        vh.title.setAlpha(task.done ? 0.45f : 1f);

        vh.check.setImageResource(task.done ? R.drawable.ic_check_circle_filled : R.drawable.ic_radio_unchecked);
        if (task.done) {
            vh.check.clearColorFilter();
        } else {
            int color = MaterialColors.getColor(vh.check,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            vh.check.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }

        if (task.note == null || task.note.trim().isEmpty()) {
            vh.note.setVisibility(View.GONE);
        } else {
            vh.note.setVisibility(View.VISIBLE);
            vh.note.setText(task.note);
            vh.note.setAlpha(task.done ? 0.35f : 0.75f);
        }

        // 分类色点
        int catColor = ContextCompat.getColor(vh.itemView.getContext(), Category.colorRes(Category.of(task)));
        vh.dot.getBackground().setTint(catColor);
        vh.dot.setVisibility(View.VISIBLE);

        // 所属分组
        Group group = store.groupOfTask(task);
        if (group != null) {
            vh.group.setVisibility(View.VISIBLE);
            vh.group.setText(group.name);
        } else {
            vh.group.setVisibility(View.GONE);
        }

        // 截止 / 提醒
        long now = System.currentTimeMillis();
        boolean hasMeta = task.hasDue() || task.hasReminder();
        vh.meta.setVisibility(hasMeta ? View.VISIBLE : View.GONE);
        int normal = MaterialColors.getColor(vh.title, com.google.android.material.R.attr.colorOnSurfaceVariant);
        if (task.hasDue()) {
            vh.dueIcon.setVisibility(View.VISIBLE);
            vh.due.setVisibility(View.VISIBLE);
            if (task.isOverdue(now)) {
                vh.due.setText(vh.itemView.getContext().getString(
                        R.string.due_overdue, TimeText.friendly(task.dueAt)));
                vh.due.setTextColor(ContextCompat.getColor(vh.itemView.getContext(), R.color.warn_due));
                vh.dueIcon.setColorFilter(ContextCompat.getColor(vh.itemView.getContext(), R.color.warn_due),
                        PorterDuff.Mode.SRC_IN);
            } else {
                vh.due.setText(vh.itemView.getContext().getString(
                        R.string.due_at, TimeText.friendly(task.dueAt)));
                vh.due.setTextColor(normal);
                vh.dueIcon.setColorFilter(normal, PorterDuff.Mode.SRC_IN);
            }
        } else {
            vh.dueIcon.setVisibility(View.GONE);
            vh.due.setVisibility(View.GONE);
        }
        if (task.hasReminder()) {
            vh.remindIcon.setVisibility(View.VISIBLE);
            vh.remind.setVisibility(View.VISIBLE);
            vh.remind.setText(vh.itemView.getContext().getString(
                    R.string.remind_at, TimeText.friendly(task.remindAt)));
            vh.remind.setTextColor(normal);
            vh.remindIcon.setColorFilter(normal, PorterDuff.Mode.SRC_IN);
        } else {
            vh.remindIcon.setVisibility(View.GONE);
            vh.remind.setVisibility(View.GONE);
        }

        vh.check.setOnClickListener(v -> listener.onToggle(task));
        vh.itemView.setOnClickListener(v -> listener.onEdit(task));
        vh.itemView.setOnLongClickListener(v -> {
            listener.onMore(task);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final View dot;
        final TextView name;
        final TextView count;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.c_dot);
            name = itemView.findViewById(R.id.c_name);
            count = itemView.findViewById(R.id.c_count);
        }
    }

    static class TaskVH extends RecyclerView.ViewHolder {
        final ImageView check;
        final TextView title;
        final TextView note;
        final View dot;
        final View meta;
        final TextView group;
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
            group = itemView.findViewById(R.id.t_group);
            dueIcon = itemView.findViewById(R.id.t_due_icon);
            due = itemView.findViewById(R.id.t_due);
            remindIcon = itemView.findViewById(R.id.t_remind_icon);
            remind = itemView.findViewById(R.id.t_remind);
        }
    }
}
