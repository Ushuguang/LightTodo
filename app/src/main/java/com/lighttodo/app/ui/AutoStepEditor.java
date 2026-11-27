package com.lighttodo.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.lighttodo.app.R;
import com.lighttodo.app.model.AutoCategoryStep;
import com.lighttodo.app.model.Category;
import com.lighttodo.app.util.TimeText;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 「自动变分类」的一个时间点：选目标分类 + 选什么时候变。
 *
 * <p>时间给三个快捷按钮（1 天后 / 3 天后 / 1 周后），也可以自己挑日期和时间。</p>
 */
public final class AutoStepEditor {

    public interface Callback {
        /** 用户确定了一个时间点。 */
        void onPicked(AutoCategoryStep step);
    }

    private AutoStepEditor() {
    }

    /**
     * 弹出编辑时间点的对话框。
     *
     * @param existing 已经有的时间点（传 null 表示新建）
     */
    public static void show(final AppCompatActivity activity, final AutoCategoryStep existing,
                             final Callback callback) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_auto_step, null);
        final MaterialButton btnTime = view.findViewById(R.id.btn_auto_time);
        final MaterialButton[] catButtons = new MaterialButton[]{
                view.findViewById(R.id.cat_0), view.findViewById(R.id.cat_1),
                view.findViewById(R.id.cat_2), view.findViewById(R.id.cat_3)
        };

        final int[] pickedCat = new int[]{
                existing == null ? Category.IMPORTANT_URGENT : existing.category()
        };
        // 默认时间：明天早上 8 点
        final long[] pickedAt = new long[]{
                existing == null ? defaultTime(activity) : existing.at
        };

        for (int i = 0; i < catButtons.length; i++) {
            final int cat = TaskEditor.POSITION_CATEGORY[i];
            catButtons[i].setOnClickListener(v -> {
                pickedCat[0] = cat;
                TaskEditor.refreshCatButtons(catButtons, pickedCat[0]);
            });
        }
        TaskEditor.refreshCatButtons(catButtons, pickedCat[0]);
        updateTimeLabel(btnTime, pickedAt[0]);

        btnTime.setOnClickListener(v -> pickDateTime(activity, pickedAt, btnTime));
        view.findViewById(R.id.btn_auto_in1).setOnClickListener(v -> {
            pickedAt[0] = daysLater(activity, 1);
            updateTimeLabel(btnTime, pickedAt[0]);
        });
        view.findViewById(R.id.btn_auto_in3).setOnClickListener(v -> {
            pickedAt[0] = daysLater(activity, 3);
            updateTimeLabel(btnTime, pickedAt[0]);
        });
        view.findViewById(R.id.btn_auto_in7).setOnClickListener(v -> {
            pickedAt[0] = daysLater(activity, 7);
            updateTimeLabel(btnTime, pickedAt[0]);
        });

        final AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_auto_step_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (pickedAt[0] <= System.currentTimeMillis()) {
                btnTime.setError(activity.getString(R.string.error_auto_past));
                return;
            }
            dialog.dismiss();
            if (callback != null) {
                callback.onPicked(AutoCategoryStep.of(pickedAt[0], pickedCat[0]));
            }
        }));
        dialog.show();
    }

    /** 默认时间：明天早上 8 点。 */
    private static long defaultTime(Context context) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 几天之后的同一时刻。 */
    private static long daysLater(Context context, int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, days);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void updateTimeLabel(MaterialButton btn, long at) {
        btn.setText(TimeText.full(at));
    }

    /** 先选日期，再选时间，合并成一个时间戳。 */
    private static void pickDateTime(final AppCompatActivity activity, final long[] pickedAt,
                                      final MaterialButton btnTime) {
        long current = pickedAt[0];
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
            showTimePicker(activity, pickedAt, btnTime,
                    utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        });
        datePicker.show(activity.getSupportFragmentManager(), "auto_date");
    }

    private static void showTimePicker(final AppCompatActivity activity, final long[] pickedAt,
                                       final MaterialButton btnTime, int year, int month, int day) {
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
            pickedAt[0] = c.getTimeInMillis();
            updateTimeLabel(btnTime, pickedAt[0]);
        });
        timePicker.show(activity.getSupportFragmentManager(), "auto_time");
    }
}
