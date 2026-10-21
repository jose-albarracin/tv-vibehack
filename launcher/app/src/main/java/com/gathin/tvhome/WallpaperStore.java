package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fondos propios: busca fotos en la TV y en memorias USB, y guarda la elegida ya recortada a
 * 1920x1080 en el almacenamiento privado del launcher (asi sigue funcionando si se saca la USB).
 */
final class WallpaperStore {

    static final int OUT_W = 1920;
    static final int OUT_H = 1080;
    /** Carpeta donde tools\poner-fondo.ps1 deja las fotos que se mandan desde el PC. */
    static final String PC_FOLDER = "Pictures/Fondos";

    private static final int MAX_IMAGES = 300;
    private static final int MAX_DEPTH = 3;
    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp"};

    private WallpaperStore() {}

    static File customFile(Context ctx) {
        return new File(ctx.getFilesDir(), "fondo_propio.jpg");
    }

    static boolean hasCustom(Context ctx) {
        File f = customFile(ctx);
        return f.isFile() && f.length() > 0;
    }

    /** Fotos encontradas, las mas nuevas primero: Fondos del PC, Imagenes, DCIM, Descargas y memorias USB. */
    static List<File> findImages() {
        List<File> roots = new ArrayList<>();
        File ext = Environment.getExternalStorageDirectory();
        roots.add(new File(ext, PC_FOLDER));
        roots.add(new File(ext, "Pictures"));
        roots.add(new File(ext, "DCIM"));
        roots.add(new File(ext, "Download"));
        File storage = new File("/storage");
        File[] volumes = storage.listFiles();
        if (volumes != null) {
            for (File v : volumes) {
                String n = v.getName();
                if (n.equals("emulated") || n.equals("self")) continue;
                roots.add(v); // memorias USB y tarjetas: /storage/XXXX-XXXX
            }
        }
        List<File> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (File r : roots) {
            scan(r, 0, out, seen);
            if (out.size() >= MAX_IMAGES) break;
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private static void scan(File dir, int depth, List<File> out, Set<String> seen) {
        if (out.size() >= MAX_IMAGES || dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (out.size() >= MAX_IMAGES) return;
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth < MAX_DEPTH && !name.equalsIgnoreCase("Android") && !name.equalsIgnoreCase("LOST.DIR")) {
                    scan(f, depth + 1, out, seen);
                }
            } else if (isImage(name) && f.length() > 0) {
                String key;
                try { key = f.getCanonicalPath(); } catch (Exception e) { key = f.getAbsolutePath(); }
                if (seen.add(key)) out.add(f);
            }
        }
    }

    static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String e : EXTENSIONS) if (n.endsWith(e)) return true;
        return false;
    }

    /** Miniatura pequena para el selector (RGB_565: la mitad de memoria). */
    static Bitmap thumbnail(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, w, h);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        return b == null ? null : rotate(b, f);
    }

    /** Decodifica la foto, corrige la rotacion de la camara, la recorta a 16:9 y la guarda en 1920x1080. */
    static boolean importImage(Context ctx, File src) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return false;
        int orientation = exifOrientation(src);
        boolean swap = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270;
        int srcW = swap ? o.outHeight : o.outWidth;
        int srcH = swap ? o.outWidth : o.outHeight;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(srcW, srcH, OUT_W, OUT_H);
        Bitmap decoded = BitmapFactory.decodeFile(src.getAbsolutePath(), o);
        if (decoded == null) return false;
        Bitmap upright = rotate(decoded, src);

        Bitmap out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float scale = Math.max(OUT_W / (float) upright.getWidth(), OUT_H / (float) upright.getHeight());
        int cropW = Math.round(OUT_W / scale), cropH = Math.round(OUT_H / scale);
        int left = (upright.getWidth() - cropW) / 2, top = (upright.getHeight() - cropH) / 2;
        c.drawBitmap(upright, new Rect(left, top, left + cropW, top + cropH), new Rect(0, 0, OUT_W, OUT_H),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        upright.recycle();

        File dst = customFile(ctx);
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            out.compress(Bitmap.CompressFormat.JPEG, 92, fos);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            out.recycle();
        }
        return tmp.renameTo(dst) || (dst.delete() && tmp.renameTo(dst));
    }

    static Bitmap loadCustom(Context ctx) {
        File f = customFile(ctx);
        if (!f.isFile()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    private static int sampleSize(int w, int h, int reqW, int reqH) {
        int s = 1;
        while (w / (s * 2) >= reqW && h / (s * 2) >= reqH) s *= 2;
        return s;
    }

    private static int exifOrientation(File f) {
        try {
            return new ExifInterface(f.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotate(Bitmap b, File f) {
        int deg;
        switch (exifOrientation(f)) {
            case ExifInterface.ORIENTATION_ROTATE_90: deg = 90; break;
            case ExifInterface.ORIENTATION_ROTATE_180: deg = 180; break;
            case ExifInterface.ORIENTATION_ROTATE_270: deg = 270; break;
            default: return b;
        }
        Matrix m = new Matrix();
        m.postRotate(deg);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r;
    }
}
