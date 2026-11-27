package com.lighttodo.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lighttodo.app.R;
import com.lighttodo.app.model.Question;
import com.lighttodo.app.store.QuestionStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 题目列表：每条题目的文字 + 配图缩略图。 */
public class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.VH> {

    public interface Listener {
        void onToggleAsked(Question question);

        void onOpen(Question question);

        void onMore(Question question);
    }

    private final List<Question> items = new ArrayList<>();
    private final QuestionStore store;
    private final Listener listener;

    public QuestionAdapter(QuestionStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    public void setData(List<Question> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Question q = items.get(position);
        holder.title.setText(q.title.isEmpty()
                ? holder.itemView.getContext().getString(R.string.q_no_title)
                : q.title);
        // 问过了的题目整条压暗，一眼能看出还剩哪些要问
        float alpha = q.asked ? 0.45f : 1f;
        holder.title.setAlpha(alpha);
        holder.asked.setImageResource(q.asked ? R.drawable.ic_checked : R.drawable.ic_radio_unchecked);

        if (q.note == null || q.note.trim().isEmpty()) {
            holder.note.setVisibility(View.GONE);
        } else {
            holder.note.setVisibility(View.VISIBLE);
            holder.note.setText(q.note);
            holder.note.setAlpha(alpha);
        }

        // 配图
        LinearLayout photos = holder.photos;
        photos.removeAllViews();
        if (q.hasImages()) {
            holder.photosScroll.setVisibility(View.VISIBLE);
            int size = holder.itemView.getResources()
                    .getDimensionPixelSize(R.dimen.q_thumb_size);
            for (String name : q.images) {
                ImageView iv = new ImageView(holder.itemView.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd(holder.itemView.getResources()
                        .getDimensionPixelSize(R.dimen.q_thumb_gap));
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photos.addView(iv);
                ImageLoader.get().load(iv, store.photoFile(name), QuestionStore.THUMB_MAX_EDGE);
            }
        } else {
            holder.photosScroll.setVisibility(View.GONE);
        }

        holder.asked.setOnClickListener(v -> listener.onToggleAsked(q));
        holder.itemView.setOnClickListener(v -> listener.onOpen(q));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onMore(q);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView asked;
        final TextView title;
        final TextView note;
        final View photosScroll;
        final LinearLayout photos;

        VH(@NonNull View itemView) {
            super(itemView);
            asked = itemView.findViewById(R.id.q_asked);
            title = itemView.findViewById(R.id.q_title);
            note = itemView.findViewById(R.id.q_note);
            photosScroll = itemView.findViewById(R.id.q_photos_scroll);
            photos = itemView.findViewById(R.id.q_photos);
        }
    }
}
