package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cuadricula de miniaturas del selector de fondos. Carga en segundo plano con un cache acotado (TV de 2 GB). */
final class WallpaperPickerAdapter extends RecyclerView.Adapter<WallpaperPickerAdapter.Holder> {

    interface Listener { void onPicked(File f); }

    private static final int THUMB_W = 384, THUMB_H = 216;

    private final List<File> files = new ArrayList<>();
    private final Listener listener;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    WallpaperPickerAdapter(Listener listener) {
        this.listener = listener;
    }

    void setFiles(List<File> list) {
        files.clear();
        files.addAll(list);
        notifyDataSetChanged();
    }

    void release() {
        cache.evictAll();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        File f = files.get(position);
        String key = f.getAbsolutePath();
        h.key = key;
        h.name.setText(f.getName());
        h.itemView.setOnClickListener(v -> listener.onPicked(f));
        Bitmap cached = cache.get(key);
        if (cached != null) {
            h.thumb.setImageBitmap(cached);
            return;
        }
        h.thumb.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap b;
            try { b = WallpaperStore.thumbnail(f, THUMB_W, THUMB_H); } catch (Throwable t) { b = null; }
            final Bitmap bmp = b;
            main.post(() -> {
                if (bmp != null) cache.put(key, bmp);
                if (key.equals(h.key)) {
                    if (bmp != null) h.thumb.setImageBitmap(bmp);
                    else h.name.setText(f.getName() + "  (no se puede abrir)");
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView name;
        String key;

        Holder(View v) {
            super(v);
            thumb = v.findViewById(R.id.wallThumb);
            name = v.findViewById(R.id.wallName);
            v.setOnFocusChangeListener((view, has) ->
                    view.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(140).start());
        }
    }
}
