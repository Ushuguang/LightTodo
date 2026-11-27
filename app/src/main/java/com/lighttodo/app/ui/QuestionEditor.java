package com.lighttodo.app.ui;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lighttodo.app.R;
import com.lighttodo.app.model.Question;
import com.lighttodo.app.model.QuestionCategory;
import com.lighttodo.app.store.QuestionStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 记录一道「不会的题」的弹窗：文字 + 配图（拍照或从相册选）。
 *
 * <p>拍照走系统相机（{@code ACTION_IMAGE_CAPTURE} + FileProvider 给一个临时文件），
 * 不申请 CAMERA 权限；相册走系统照片选择器，也不用申请读存储权限。</p>
 *
 * <p>选图和拍照的 ActivityResultLauncher 由 Activity 在 onCreate 里注册
 * （见 {@link PhotoPicker}），这里只负责调用和把结果收进来。</p>
 */
public final class QuestionEditor {

    public interface Callback {
        void onSaved();
    }

    private QuestionEditor() {
    }

    public static void show(final AppCompatActivity activity, final PhotoPicker picker,
                            final String categoryId, final Question existing,
                            final Callback callback) {
        final QuestionStore store = QuestionStore.get(activity);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_question, null);
        final EditText inputTitle = view.findViewById(R.id.input_title);
        final EditText inputNote = view.findViewById(R.id.input_note);
        final TextView categoryInfo = view.findViewById(R.id.tv_category);
        final LinearLayout photosBox = view.findViewById(R.id.photos_container);

        QuestionCategory cat = store.findCategory(categoryId);
        categoryInfo.setText(activity.getString(R.string.q_dialog_category,
                cat == null ? activity.getString(R.string.q_no_category) : cat.name));

        // 已选的图片文件名（存在应用私有目录里的）
        final List<String> picked = new ArrayList<>();
        if (existing != null && existing.images != null) {
            picked.addAll(existing.images);
        }
        if (existing != null) {
            inputTitle.setText(existing.title);
            inputNote.setText(existing.note);
        }

        renderPhotos(activity, photosBox, picked, store);

        view.findViewById(R.id.btn_camera).setOnClickListener(v -> picker.take(uris -> {
            File temp = picker.lastCaptureFile();
            if (uris == null || uris.isEmpty() || temp == null) {
                // 用户取消了拍照，临时文件清掉
                if (temp != null && temp.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
                return;
            }
            String name = store.importPhoto(temp);
            if (name == null) {
                Toast.makeText(activity, R.string.q_photo_failed, Toast.LENGTH_LONG).show();
                return;
            }
            picked.add(name);
            renderPhotos(activity, photosBox, picked, store);
        }));

        view.findViewById(R.id.btn_gallery).setOnClickListener(v -> picker.pick(uris -> {
            if (uris == null || uris.isEmpty()) return;
            int ok = 0;
            for (Uri uri : uris) {
                String name = store.importPhoto(uri);
                if (name != null) {
                    picked.add(name);
                    ok++;
                }
            }
            if (ok < uris.size()) {
                Toast.makeText(activity, R.string.q_photo_some_failed, Toast.LENGTH_LONG).show();
            }
            renderPhotos(activity, photosBox, picked, store);
        }));

        new MaterialAlertDialogBuilder(activity)
                .setTitle(existing == null ? R.string.action_new_question : R.string.action_edit)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String title = inputTitle.getText().toString().trim();
                    String note = inputNote.getText().toString().trim();
                    if (title.isEmpty() && note.isEmpty() && picked.isEmpty()) {
                        Toast.makeText(activity, R.string.q_empty_toast, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (existing == null) {
                        store.addQuestion(categoryId, title, note, picked);
                    } else {
                        store.updateQuestion(existing, title, note, picked);
                    }
                    callback.onSaved();
                })
                .show();
    }

    /** 把已选的图片画成一格一格的小图，每个右上角一个删除按钮。 */
    private static void renderPhotos(final Context ctx, final LinearLayout box,
                                     final List<String> picked, final QuestionStore store) {
        box.removeAllViews();
        if (picked.isEmpty()) return;

        int size = ctx.getResources().getDimensionPixelSize(R.dimen.q_photo_edit_size);
        int gap = ctx.getResources().getDimensionPixelSize(R.dimen.q_thumb_gap);
        final int perRow = 3;

        LinearLayout row = null;
        for (int i = 0; i < picked.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            final String name = picked.get(i);
            View cell = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_question_photo_edit, box, false);
            ImageView iv = cell.findViewById(R.id.photo);
            ImageLoader.get().load(iv, store.photoFile(name), QuestionStore.THUMB_MAX_EDGE);
            cell.findViewById(R.id.photo_remove).setOnClickListener(v -> {
                picked.remove(name);
                File f = store.photoFile(name);
                if (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
                renderPhotos(ctx, box, picked, store);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(gap);
            lp.bottomMargin = gap;
            cell.setLayoutParams(lp);
            row.addView(cell);
        }
    }
}
