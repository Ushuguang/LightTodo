package com.lighttodo.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.lighttodo.app.R;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.Notifier;
import com.lighttodo.app.util.ReminderScheduler;
import com.lighttodo.app.util.TimeText;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 任务编辑弹窗（新建 / 编辑）。分组页和分类页共用。
 */
public final class TaskEditor {

    /** 弹窗里两个时间选择器的下标 */
    private static final int IDX_DUE = 0;
    private static final int IDX_REMIND = 1;

    /**
     * 四个分类在四宫格里的位置顺序。
     * 左上「重要不紧急」· 右上「重要紧急」· 左下「不重要不紧急」· 右下「不重要紧急」。
     */
    public static final int[] POSITION_CATEGORY = {
            Category.IMPORTANT_NOT_URGENT,
            Category.IMPORTANT_URGENT,
            Category.NOT_IMPORTANT_NOT_URGENT,
            Category.NOT_IMPORTANT_URGENT,
    };

    public interface Callback {
        void onSaved();
    }

    private TaskEditor() {
    }

    public static void show(final AppCompatActivity activity, final String groupId,
                             final Task existing, final Callback callback) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_task, null);
        final EditText title = view.findViewById(R.id.input_title);
        final EditText note = view.findViewById(R.id.input_note);
        final MaterialButton btnDue = view.findViewById(R.id.btn_due);
        final MaterialButton btnDueClear = view.findViewById(R.id.btn_due_clear);
        final MaterialButton btnRemind = view.findViewById(R.id.btn_remind);
        final MaterialButton btnRemindClear = view.findViewById(R.id.btn_remind_clear);
        final TextView tip = view.findViewById(R.id.tip_remind);
        final TextView groupInfo = view.findViewById(R.id.tv_group);
        final LinearLayout autoBox = view.findViewById(R.id.auto_steps_box);
        final MaterialButton btnAutoAdd = view.findViewById(R.id.btn_auto_add);

        final long[] picked = new long[2];
        picked[IDX_DUE] = existing == null ? 0L : existing.dueAt;
        picked[IDX_REMIND] = existing == null ? 0L : existing.remindAt;

        // 四象限分类
        final int[] pickedCat = new int[]{
                existing == null ? Category.NOT_IMPORTANT_NOT_URGENT : Category.of(existing)
        };
        final MaterialButton[] catButtons = new MaterialButton[]{
                view.findViewById(R.id.cat_0), view.findViewById(R.id.cat_1),
                view.findViewById(R.id.cat_2), view.findViewById(R.id.cat_3)
        };

        final TodoStore store = TodoStore.get(activity);

        // 「自动变分类」的时间点链条：按时间先后依次生效
        final java.util.List<com.lighttodo.app.model.AutoCategoryStep> autoSteps =
                new java.util.ArrayList<>();
        if (existing != null && existing.autoSteps != null) {
            autoSteps.addAll(existing.autoSteps);
        }

        // 所属分组：从分类页进来时也能一眼看出这条任务属于哪个分组
        Group group = groupId == null ? null : store.findGroup(groupId);
        if (group != null) {
            groupInfo.setVisibility(View.VISIBLE);
            groupInfo.setText(activity.getString(R.string.dialog_group_of, group.name));
        } else {
            groupInfo.setVisibility(View.GONE);
        }

        if (existing != null) {
            title.setText(existing.title);
            note.setText(existing.note);
        }
        updateTimeButton(btnDue, btnDueClear, picked[IDX_DUE]);
        updateTimeButton(btnRemind, btnRemindClear, picked[IDX_REMIND]);

        for (int i = 0; i < catButtons.length; i++) {
            final int cat = POSITION_CATEGORY[i];
            catButtons[i].setOnClickListener(v -> {
                pickedCat[0] = cat;
                refreshCatButtons(catButtons, pickedCat[0]);
            });
        }
        refreshCatButtons(catButtons, pickedCat[0]);

        btnDue.setOnClickListener(v -> pickDateTime(activity, IDX_DUE, picked, btnDue, btnDueClear));
        btnRemind.setOnClickListener(v -> pickDateTime(activity, IDX_REMIND, picked, btnRemind, btnRemindClear));
        btnDueClear.setOnClickListener(v -> {
            picked[IDX_DUE] = 0L;
            updateTimeButton(btnDue, btnDueClear, 0L);
        });
        btnRemindClear.setOnClickListener(v -> {
            picked[IDX_REMIND] = 0L;
            updateTimeButton(btnRemind, btnRemindClear, 0L);
        });

        // 自动变分类：链条列表 + 添加时间点
        btnAutoAdd.setOnClickListener(v -> AutoStepEditor.show(activity, null, step -> {
            autoSteps.add(step);
            renderAutoSteps(activity, autoBox, autoSteps);
        }));
        renderAutoSteps(activity, autoBox, autoSteps);

        final boolean ready = Notifier.canNotify(activity) && ReminderScheduler.canScheduleExact(activity);
        tip.setText(ready ? R.string.tip_remind : R.string.tip_remind_need_permission);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(existing == null ? R.string.dialog_new_task : R.string.dialog_edit_task)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(existing == null ? R.string.action_add : R.string.action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) {
                title.setError(activity.getString(R.string.error_empty));
                return;
            }
            String n = note.getText().toString().trim();
            boolean important = pickedCat[0] == Category.IMPORTANT_URGENT
                    || pickedCat[0] == Category.IMPORTANT_NOT_URGENT;
            boolean urgent = pickedCat[0] == Category.IMPORTANT_URGENT
                    || pickedCat[0] == Category.NOT_IMPORTANT_URGENT;

            if (existing == null) {
                Task created = store.addTask(groupId, t, n, picked[IDX_DUE], picked[IDX_REMIND],
                        important, urgent);
                if (created != null && !autoSteps.isEmpty()) {
                    store.setAutoSteps(groupId, created.id, autoSteps);
                }
            } else {
                existing.important = important;
                existing.urgent = urgent;
                store.updateTask(groupId, existing.id, t, n, picked[IDX_DUE], picked[IDX_REMIND]);
                store.setAutoSteps(groupId, existing.id, autoSteps);
            }
            dialog.dismiss();
            if (callback != null) callback.onSaved();

            if (picked[IDX_REMIND] > 0 && !ready) {
                Toast.makeText(activity, R.string.toast_need_permission, Toast.LENGTH_LONG).show();
            } else if (picked[IDX_REMIND] > 0 && picked[IDX_REMIND] <= System.currentTimeMillis()) {
                Toast.makeText(activity, R.string.toast_remind_past, Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    /**
     * 渲染「自动变分类」的链条：每个时间点一行，写清楚什么时间变成哪个分类。
     */
    private static void renderAutoSteps(final AppCompatActivity activity,
                                         LinearLayout box,
                                         final java.util.List<com.lighttodo.app.model.AutoCategoryStep> steps) {
        box.removeAllViews();
        if (steps == null || steps.isEmpty()) return;
        // 按时间先后排一下，看着清楚
        java.util.Collections.sort(steps,
                new java.util.Comparator<com.lighttodo.app.model.AutoCategoryStep>() {
                    @Override
                    public int compare(com.lighttodo.app.model.AutoCategoryStep a,
                                       com.lighttodo.app.model.AutoCategoryStep b) {
                        return Long.compare(a.at, b.at);
                    }
                });
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < steps.size(); i++) {
            final com.lighttodo.app.model.AutoCategoryStep step = steps.get(i);
            final int index = i;
            View row = inflater.inflate(R.layout.item_auto_step, box, false);
            TextView time = row.findViewById(R.id.step_time);
            TextView cat = row.findViewById(R.id.step_cat);
            time.setText(com.lighttodo.app.util.TimeText.full(step.at));
            int category = step.category();
            cat.setText(Category.nameRes(category));
            cat.setTextColor(ContextCompat.getColor(activity, Category.colorRes(category)));
            row.findViewById(R.id.step_delete).setOnClickListener(v -> {
                steps.remove(index);
                renderAutoSteps(activity, box, steps);
            });
            box.addView(row);
        }
    }

    /**
     * 四个分类按钮里只允许一个处于选中态，并按四象限各自的颜色标记：
     * 重要紧急=红、重要不紧急=橙、不重要紧急=蓝、不重要不紧急=灰。
     */
    public static void refreshCatButtons(MaterialButton[] buttons, int selected) {
        Context context = buttons[0].getContext();
        for (int i = 0; i < buttons.length; i++) {
            int cat = POSITION_CATEGORY[i];
            int color = ContextCompat.getColor(context, Category.colorRes(cat));
            boolean on = cat == selected;
            buttons[i].setText(Category.nameRes(cat));
            buttons[i].setChecked(on);
            if (buttons[i].getIcon() == null) {
                buttons[i].setIcon(ContextCompat.getDrawable(context, R.drawable.ic_dot));
            }
            buttons[i].setBackgroundTintList(ColorStateList.valueOf(on ? color : Color.TRANSPARENT));
            buttons[i].setStrokeColor(ColorStateList.valueOf(color));
            buttons[i].setTextColor(on ? Color.WHITE : color);
            buttons[i].setIconTint(ColorStateList.valueOf(on ? Color.WHITE : color));
        }
    }

    private static void updateTimeButton(MaterialButton btn, MaterialButton clearBtn, long value) {
        if (value > 0) {
            btn.setText(TimeText.full(value));
            clearBtn.setVisibility(View.VISIBLE);
        } else {
            btn.setText(R.string.time_not_set);
            clearBtn.setVisibility(View.GONE);
        }
    }

    /** 先选日期，再选时间，合并成一个时间戳。 */
    private static void pickDateTime(final AppCompatActivity activity, final int which, final long[] picked,
                                      final MaterialButton btn, final MaterialButton clearBtn) {
        long current = picked[which];
        long selection = MaterialDatePicker.todayInUtcMilliseconds();
        if (current > 0) {
            Calendar local = Calendar.getInstance();
            local.setTimeInMillis(current);
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear();
            utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                    local.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            selection = utc.getTimeInMillis();
        }

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.pick_date)
                .setSelection(selection)
                .build();
        datePicker.addOnPositiveButtonClickListener(sel -> {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(sel);
            int y = utc.get(Calendar.YEAR);
            int mo = utc.get(Calendar.MONTH);
            int day = utc.get(Calendar.DAY_OF_MONTH);
            showTimePicker(activity, which, picked, btn, clearBtn, y, mo, day);
        });
        datePicker.show(activity.getSupportFragmentManager(), "date_" + which);
    }

    private static void showTimePicker(final AppCompatActivity activity, int which, long[] picked,
                                       MaterialButton btn, MaterialButton clearBtn,
                                       int year, int month, int day) {
        Calendar now = Calendar.getInstance();
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText(R.string.pick_time)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .build();
        timePicker.addOnPositiveButtonClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.set(year, month, day, timePicker.getHour(), timePicker.getMinute(), 0);
            c.set(Calendar.MILLISECOND, 0);
            picked[which] = c.getTimeInMillis();
            updateTimeButton(btn, clearBtn, picked[which]);
        });
        timePicker.show(activity.getSupportFragmentManager(), "time_" + which);
    }
}
