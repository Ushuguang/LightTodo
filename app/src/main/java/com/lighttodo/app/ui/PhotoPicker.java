package com.lighttodo.app.ui;

import android.net.Uri;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.lighttodo.app.R;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 拍照 / 从相册选图的小助手。
 *
 * <p>为什么单独拎出来：ActivityResultLauncher <b>必须在 Activity 处于 INITIALIZED 状态时注册</b>，
 * 在弹窗里注册会直接抛 IllegalStateException。所以由 Activity 在 onCreate 里 {@link #init()}，
 * 弹窗再通过 {@link #pick} / {@link #take} 来用。</p>
 *
 * <p>两种方式都<b>不需要申请权限</b>：拍照走系统相机 + FileProvider 给一个临时文件，
 * 选图走系统照片选择器（Android 13+ 是系统 PhotoPicker，低版本回落到文件选择器）。</p>
 */
public final class PhotoPicker {

    /** 选完图（或拍完照）的回调。 */
    public interface Callback {
        void onPicked(List<Uri> uris);
    }

    private final ComponentActivity activity;
    private ActivityResultLauncher<String> pickLauncher;
    private ActivityResultLauncher<Uri> takeLauncher;

    private Callback pending;
    private Uri captureUri;
    private File captureFile;

    public PhotoPicker(ComponentActivity activity) {
        this.activity = activity;
    }

    /** 在 Activity 的 onCreate 里调用一次。 */
    public void init() {
        // 用 GetMultipleContents（ACTION_GET_CONTENT + EXTRA_ALLOW_MULTIPLE）：
        // 从 Android 1.0 到现在的系统都支持，Android 13+ 上会走系统照片选择器，
        // 而且不需要申请读存储权限。
        pickLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> deliver(uris));

        takeLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                ok -> deliver(ok && captureUri != null
                        ? Collections.singletonList(captureUri) : null));
    }

    /** 从相册选图（可多选）。 */
    public void pick(Callback callback) {
        if (pickLauncher == null) return;
        pending = callback;
        pickLauncher.launch("image/*");
    }

    /** 调起相机拍照。 */
    public void take(Callback callback) {
        if (takeLauncher == null) return;
        try {
            File dir = new File(activity.getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) {
                toast(R.string.q_photo_failed);
                return;
            }
            captureFile = new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
            captureUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", captureFile);
            pending = callback;
            takeLauncher.launch(captureUri);
        } catch (Exception e) {
            toast(R.string.q_camera_unavailable);
        }
    }

    /** 拍照完，临时文件给外部去读（读完了记得删）。 */
    public File lastCaptureFile() {
        return captureFile;
    }

    private void deliver(List<Uri> uris) {
        Callback cb = pending;
        pending = null;
        if (cb != null) cb.onPicked(uris);
    }

    private void toast(int resId) {
        Toast.makeText(activity, resId, Toast.LENGTH_LONG).show();
    }
}
