package com.lighttodo.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.lighttodo.app.R;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 一个很小的图片加载器：后台线程解码，主线程贴图，自带内存缓存。
 *
 * <p>刻意不引第三方图片库：这个 App 要「零第三方 SDK」。</p>
 *
 * <p>用法：{@code ImageLoader.load(imageView, file, maxEdgePx)}，
 * 同一个 ImageView 再次 load 时会自动取消上一次还没做完的任务。</p>
 */
public final class ImageLoader {

    /** 单例。 */
    private static final ImageLoader INSTANCE = new ImageLoader();

    /** 缓存按字节算，取可用内存的 1/8。 */
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageLoader() {
    }

    public static ImageLoader get() {
        return INSTANCE;
    }

    /** 把一个文件路径贴到 ImageView 上（后台解码，缩到 maxEdgePx）。 */
    public void load(final ImageView view, @Nullable final File file, final int maxEdgePx) {
        if (view == null) return;
        // 记住这次要加载的是哪个文件，回调时好核对是不是已经换人了
        final String tag = file == null ? null : file.getAbsolutePath();
        view.setTag(R.id.tag_image_loader, tag);

        if (file == null || !file.exists()) {
            view.setImageDrawable(null);
            return;
        }

        Bitmap hit = cache.get(tag);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }

        view.setImageDrawable(null);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = decode(file, maxEdgePx);
                if (bmp == null) return;
                cache.put(tag, bmp);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        // 期间可能已经换了别的图，标签对不上就丢弃
                        Object current = view.getTag(R.id.tag_image_loader);
                        if (tag != null && tag.equals(current)) {
                            view.setImageBitmap(bmp);
                        }
                    }
                });
            }
        });
    }

    /** 同步解码，给「查看大图」用。 */
    @Nullable
    public Bitmap decode(File file, int maxEdge) {
        if (file == null || !file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            in.close();

            int edge = Math.max(opts.outWidth, opts.outHeight);
            int sample = 1;
            while (edge / sample > maxEdge * 2) {
                sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            try (FileInputStream in2 = new FileInputStream(file)) {
                Bitmap bmp = BitmapFactory.decodeStream(in2, null, real);
                if (bmp == null) return null;
                int w = bmp.getWidth();
                int h = bmp.getHeight();
                int longEdge = Math.max(w, h);
                if (longEdge <= maxEdge) return bmp;
                float scale = (float) maxEdge / longEdge;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.round(w * scale),
                        Math.round(h * scale), true);
                if (scaled != bmp) bmp.recycle();
                return scaled;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
