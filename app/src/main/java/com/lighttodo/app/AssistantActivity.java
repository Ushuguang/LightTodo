package com.lighttodo.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.IslandFocus;
import com.lighttodo.app.util.MiuiWidget;
import com.lighttodo.app.util.Notifier;
import com.lighttodo.app.util.ReminderScheduler;

/**
 * 助手页：提醒要在国产 ROM 上准点响，需要下面几项授权，这里逐项显示状态并提供跳转。
 */
public class AssistantActivity extends AppCompatActivity {

    private TodoStore store;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.toast_deny_notify, Toast.LENGTH_LONG).show();
                }
                refresh();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);

        store = TodoStore.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_test).setOnClickListener(v -> sendTestNotification());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        TextView summary = findViewById(R.id.summary);
        int pending = ReminderScheduler.pendingReminderCount(store);
        summary.setText(getString(R.string.assistant_summary, pending));

        ViewGroup rows = findViewById(R.id.rows);
        rows.removeAllViews();

        // 1. 通知权限
        boolean notifyOk = Notifier.canNotify(this);
        addRow(rows, R.drawable.ic_notification, R.string.perm_notify_title, R.string.perm_notify_desc,
                notifyOk, getString(notifyOk ? R.string.state_ok : R.string.state_off),
                notifyOk ? R.color.brand_success : R.color.warn_due,
                v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            && !Notifier.canNotify(this)) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    } else {
                        openAppNotificationSettings();
                    }
                });

        // 2. 精确闹钟
        boolean alarmOk = ReminderScheduler.canScheduleExact(this);
        addRow(rows, R.drawable.ic_alarm, R.string.perm_alarm_title, R.string.perm_alarm_desc,
                alarmOk, getString(alarmOk ? R.string.state_ok : R.string.state_off),
                alarmOk ? R.color.brand_success : R.color.warn_due,
                v -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        openAppDetails();
                    }
                });

        // 3. 电池优化白名单
        boolean batteryOk = isIgnoringBatteryOptimizations();
        addRow(rows, R.drawable.ic_battery, R.string.perm_battery_title, R.string.perm_battery_desc,
                batteryOk, getString(batteryOk ? R.string.state_ok : R.string.state_off),
                batteryOk ? R.color.brand_success : R.color.warn_due,
                v -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        openAppDetails();
                    }
                });

        // 4. 自启动管理（小米澎湃系统）
        addRow(rows, R.drawable.ic_autostart, R.string.perm_autostart_title, R.string.perm_autostart_desc,
                false, getString(R.string.state_recommend), R.color.warn_today,
                v -> openAutostartSettings());

        // 5. 焦点通知权限：系统里的开关，开了才可能上岛
        addFocusRow(rows);

        // 6. 上岛模式：自动 / 强制 / 关闭
        addIslandModeRow(rows);

        // 7. 小米小部件：决定小组件能不能进负一屏
        addMiuiWidgetRow(rows);
    }

    /** 小米小部件（澎湃系统那套小组件）：决定了小组件能不能出现在负一屏。 */
    private void addMiuiWidgetRow(ViewGroup rows) {
        boolean supported = MiuiWidget.isSupported(this);
        boolean detail = MiuiWidget.isDetailPageSupported(this);
        String status;
        int colorRes;
        if (supported) {
            status = detail
                    ? getString(R.string.state_miui_widget_ok)
                    : getString(R.string.state_miui_widget_ok_no_detail);
            colorRes = R.color.brand_success;
        } else {
            status = getString(R.string.state_miui_widget_unsupported);
            colorRes = R.color.warn_today;
        }
        addRow(rows, R.drawable.ic_update, R.string.perm_miui_widget_title,
                R.string.perm_miui_widget_desc, supported, status, colorRes,
                R.string.action_view, v -> openMiuiWidgetHelp());
    }

    /** 打开小米小部件的官方说明页，方便用户照着开权限 / 找入口。 */
    private void openMiuiWidgetHelp() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584"));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    /** 焦点通知权限：决定这条提醒能不能显示到灵动岛。 */
    private void addFocusRow(ViewGroup rows) {
        int perm = IslandFocus.focusPermissionState(this);
        String status;
        int colorRes;
        if (perm == IslandFocus.PERM_ALLOWED) {
            status = getString(R.string.state_focus_allowed);
            colorRes = R.color.brand_success;
        } else if (perm == IslandFocus.PERM_DENIED) {
            status = getString(R.string.state_focus_denied);
            colorRes = R.color.warn_due;
        } else {
            status = getString(R.string.state_focus_unknown);
            colorRes = R.color.warn_today;
        }
        addRow(rows, R.drawable.ic_notification, R.string.perm_focus_title, R.string.perm_focus_desc,
                perm == IslandFocus.PERM_ALLOWED, status, colorRes, R.string.action_go_set,
                v -> openAppNotificationSettings());
    }

    /** 上岛模式切换。 */
    private void addIslandModeRow(ViewGroup rows) {
        addRow(rows, R.drawable.ic_notification, R.string.perm_island_mode_title,
                R.string.perm_island_mode_desc, true, modeName(IslandFocus.mode(this)),
                R.color.brand_success, R.string.action_switch, v -> showModeDialog());
    }

    private String modeName(int mode) {
        if (mode == IslandFocus.MODE_FORCE) return getString(R.string.mode_force);
        if (mode == IslandFocus.MODE_OFF) return getString(R.string.mode_off);
        return getString(R.string.mode_auto);
    }

    private void showModeDialog() {
        final String[] labels = {
                getString(R.string.mode_auto),
                getString(R.string.mode_force),
                getString(R.string.mode_off)
        };
        final int[] values = {
                IslandFocus.MODE_AUTO, IslandFocus.MODE_FORCE, IslandFocus.MODE_OFF
        };
        int current = IslandFocus.mode(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.perm_island_mode_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    IslandFocus.setMode(this, values[which]);
                    dialog.dismiss();
                    refresh();
                    Toast.makeText(this, getString(R.string.toast_mode_changed, labels[which]),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 超级岛自检报告：把本机各项检测结果摊开给用户看（真机只能靠这个定位问题）。 */
    private void showIslandReport(boolean attached) {
        int perm = IslandFocus.focusPermissionState(this);
        String permText;
        if (perm == IslandFocus.PERM_ALLOWED) {
            permText = "已允许";
        } else if (perm == IslandFocus.PERM_DENIED) {
            permText = "未允许（系统里没开焦点通知）";
        } else {
            permText = "读不到（这台机器可能不支持）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("· 设备：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("· 系统：Android ").append(Build.VERSION.RELEASE)
                .append("（API ").append(Build.VERSION.SDK_INT).append("）\n");
        sb.append("· 小米系设备：").append(IslandFocus.isXiaomiDevice() ? "是" : "否").append("\n");
        sb.append("· 超级岛能力 persist.sys.feature.island：")
                .append(IslandFocus.hasIslandFeature() ? "true" : "false").append("\n");
        sb.append("· 焦点通知协议 notification_focus_protocol：")
                .append(IslandFocus.focusProtocol(this)).append("\n");
        sb.append("· 焦点通知权限 canShowFocus：").append(permText).append("\n");
        sb.append("· 上岛模式：").append(modeName(IslandFocus.mode(this))).append("\n");
        sb.append("· 这次有没有带上上岛参数：").append(attached ? "有" : "没有").append("\n\n");
        sb.append(getString(R.string.island_report_hint));

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_island_report, null);
        ((TextView) view.findViewById(R.id.report_text)).setText(sb.toString());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.island_report_title)
                .setView(view)
                .setPositiveButton(R.string.island_report_close, null)
                .show();
    }

    private void addRow(ViewGroup parent, int iconRes, int titleRes, int descRes,
                        boolean ok, String statusText, int statusColorRes, View.OnClickListener action) {
        addRow(parent, iconRes, titleRes, descRes, ok, statusText, statusColorRes,
                ok ? R.string.action_view : R.string.action_go_set, action);
    }

    private void addRow(ViewGroup parent, int iconRes, int titleRes, int descRes,
                        boolean ok, String statusText, int statusColorRes, int actionTextRes,
                        View.OnClickListener action) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_permission, parent, false);
        ((android.widget.ImageView) row.findViewById(R.id.p_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.p_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.p_desc)).setText(descRes);
        TextView status = row.findViewById(R.id.p_status);
        status.setText(statusText);
        status.setTextColor(ContextCompat.getColor(this, statusColorRes));
        MaterialButton btn = row.findViewById(R.id.p_action);
        btn.setOnClickListener(action);
        btn.setText(actionTextRes);
        parent.addView(row);
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openAppNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAutostartSettings() {
        // 小米澎湃系统的自启动管理页
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show();
        }
    }

    /** 发一条测试通知，确认通知链路是通的。 */
    private void sendTestNotification() {
        if (!Notifier.canNotify(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                openAppNotificationSettings();
            }
            return;
        }
        Notifier.ensureChannel(this);
        Task demo = new Task("test", getString(R.string.assistant_test_title), "", false,
                System.currentTimeMillis(), 0L, 0L);
        demo.dueAt = System.currentTimeMillis() + 30 * 60 * 1000L;
        boolean attached = Notifier.showReminder(this, demo, "", getString(R.string.app_name));
        Toast.makeText(this, R.string.toast_test_sent, Toast.LENGTH_SHORT).show();
        showIslandReport(attached);
    }
}
