package com.lighttodo.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lighttodo.app.util.UpdateChecker;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 关于页：版本信息、版权归属、开源仓库，以及「检查更新」。
 *
 * <p>更新流程：查 GitHub 最新 Release → 有新版就弹窗 → 下载 APK → 调系统安装器。
 * 仓库还没上传时接口会返回 404，这时提示「仓库里还没有发布版本」，不会报错。</p>
 */
public class AboutActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.app_version)).setText(
                getString(R.string.about_version, UpdateChecker.versionText(this)));

        ViewGroup rows = findViewById(R.id.rows);
        rows.removeAllViews();

        addRow(rows, R.drawable.ic_update, R.string.settings_update_title,
                R.string.settings_update_desc, R.string.action_check_update,
                v -> checkUpdate());

        addRow(rows, R.drawable.ic_github, R.string.about_source_title,
                R.string.about_source_desc, R.string.action_open,
                v -> {
                    if (!UpdateChecker.openRepo(this)) {
                        Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void addRow(ViewGroup parent, int iconRes, int titleRes, int descRes,
                        int actionRes, View.OnClickListener action) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_permission, parent, false);
        ((android.widget.ImageView) row.findViewById(R.id.p_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.p_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.p_desc)).setText(descRes);
        ((TextView) row.findViewById(R.id.p_status)).setText("");
        MaterialButton btn = row.findViewById(R.id.p_action);
        btn.setText(actionRes);
        btn.setOnClickListener(action);
        parent.addView(row);
    }

    // ------------------------------------------------------------ 检查更新

    private void checkUpdate() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            UpdateChecker.Result result = UpdateChecker.checkLatest();
            main.post(() -> onCheckDone(result));
        });
    }

    private void onCheckDone(UpdateChecker.Result result) {
        String current = UpdateChecker.currentVersion(this);

        if (!result.ok) {
            String msg;
            if ("404".equals(result.error)) {
                msg = getString(R.string.update_repo_empty);
            } else if ("network".equals(result.error)) {
                msg = getString(R.string.update_network_error);
            } else if ("rate-limit".equals(result.error)) {
                msg = getString(R.string.update_failed, "请求过于频繁，请稍后再试");
            } else {
                msg = getString(R.string.update_failed, String.valueOf(result.error));
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.settings_update_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
            return;
        }

        if (!result.isNewerThan(current)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.settings_update_title)
                    .setMessage(getString(R.string.update_latest, current))
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
            return;
        }

        // 有新版
        String tag = UpdateChecker.normalize(result.latestTag);
        String message = getString(R.string.update_available, tag);
        if (result.releaseNotes != null && !result.releaseNotes.trim().isEmpty()) {
            message = message + "\n\n" + result.releaseNotes.trim();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_update_title)
                .setMessage(message)
                .setNegativeButton(R.string.update_later, null)
                .setPositiveButton(R.string.update_download, (d, w) -> downloadAndInstall(result))
                .show();
    }

    private void downloadAndInstall(UpdateChecker.Result result) {
        if (result.apkUrl == null) {
            Toast.makeText(this, R.string.update_no_perm, Toast.LENGTH_LONG).show();
            return;
        }
        String name = result.apkName == null ? "LightTodo.apk" : result.apkName;
        final android.app.ProgressDialog dialog =
                new android.app.ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.setMessage(getString(R.string.update_downloading, name));
        dialog.show();

        executor.execute(() -> {
            final File apk = UpdateChecker.downloadApk(this, result.apkUrl, name,
                    percent -> main.post(() -> dialog.setMessage(
                            getString(R.string.update_downloading, name + " " + percent + "%"))));
            main.post(() -> {
                dialog.dismiss();
                if (apk == null) {
                    Toast.makeText(this, getString(R.string.update_download_failed, "网络错误"),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (!UpdateChecker.installApk(this, apk)) {
                    Toast.makeText(this, R.string.update_no_perm, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, R.string.update_downloaded, Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
