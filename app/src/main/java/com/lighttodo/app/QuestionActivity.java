package com.lighttodo.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lighttodo.app.model.Question;
import com.lighttodo.app.model.QuestionCategory;
import com.lighttodo.app.store.QuestionStore;
import com.lighttodo.app.ui.PhotoPicker;
import com.lighttodo.app.ui.QuestionAdapter;
import com.lighttodo.app.ui.QuestionEditor;

import java.util.List;

/** 某个题目分类下的题目列表。 */
public class QuestionActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_ID = "question_category_id";

    private QuestionStore store;
    private String categoryId;
    private QuestionCategory category;
    private QuestionAdapter adapter;
    private View emptyView;
    private TextView subtitleView;
    private PhotoPicker photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);

        store = QuestionStore.get(this);
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        category = store.findCategory(categoryId);

        photoPicker = new PhotoPicker(this);
        photoPicker.init();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(category == null ? getString(R.string.tab_questions) : category.name);

        subtitleView = findViewById(R.id.subtitle);
        emptyView = findViewById(R.id.empty);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QuestionAdapter(store, new QuestionAdapter.Listener() {
            @Override
            public void onToggleAsked(Question question) {
                store.setAsked(question, !question.asked);
                refresh();
            }

            @Override
            public void onOpen(Question question) {
                QuestionEditor.show(QuestionActivity.this, photoPicker, categoryId, question,
                        QuestionActivity.this::refresh);
            }

            @Override
            public void onMore(Question question) {
                showMenu(question);
            }
        });
        list.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> QuestionEditor.show(this, photoPicker, categoryId, null,
                this::refresh));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Question> list = store.questionsOf(categoryId);
        adapter.setData(list);
        int pending = 0;
        for (Question q : list) {
            if (!q.asked) pending++;
        }
        subtitleView.setText(getString(R.string.q_detail_summary, pending, list.size()));
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 长按题目：编辑 / 标记已问 / 删除。 */
    private void showMenu(final Question question) {
        String[] items = {
                getString(R.string.action_edit),
                getString(R.string.q_mark_asked),
                getString(R.string.action_delete),
        };
        new MaterialAlertDialogBuilder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        QuestionEditor.show(this, photoPicker, categoryId, question,
                                this::refresh);
                    } else if (which == 1) {
                        store.setAsked(question, !question.asked);
                        refresh();
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.q_delete_confirm)
                                .setMessage(R.string.q_delete_hint)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.action_delete, (d, w) -> {
                                    store.deleteQuestion(question);
                                    refresh();
                                })
                                .show();
                    }
                })
                .show();
    }
}
