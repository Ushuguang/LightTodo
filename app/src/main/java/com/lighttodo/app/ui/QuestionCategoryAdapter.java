package com.lighttodo.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lighttodo.app.R;
import com.lighttodo.app.model.QuestionCategory;
import com.lighttodo.app.store.QuestionStore;

import java.util.ArrayList;
import java.util.List;

/** 题目分类列表：名字 + 还没问老师的题数。 */
public class QuestionCategoryAdapter extends RecyclerView.Adapter<QuestionCategoryAdapter.VH> {

    public interface Listener {
        void onOpen(QuestionCategory category);

        void onMore(QuestionCategory category);
    }

    private final List<QuestionCategory> items = new ArrayList<>();
    private final QuestionStore store;
    private final Listener listener;

    public QuestionCategoryAdapter(QuestionStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    public void setData(List<QuestionCategory> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        QuestionCategory c = items.get(position);
        holder.name.setText(c.name);
        int pending = store.pendingCountOf(c.id);
        holder.count.setText(holder.itemView.getContext()
                .getString(R.string.q_category_count, pending));
        holder.itemView.setOnClickListener(v -> listener.onOpen(c));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onMore(c);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.qc_name);
            count = itemView.findViewById(R.id.qc_count);
        }
    }
}
