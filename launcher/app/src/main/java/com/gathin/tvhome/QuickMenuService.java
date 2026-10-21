package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Menu rapido encima de cualquier app.
 *
 * Solo actua con el boton de engranaje del control (KEYCODE_NOTIFICATION 83 en esta TCL, o KEYCODE_SETTINGS);
 * cualquier otra tecla pasa intacta. No lee el contenido de la pantalla (canRetrieveWindowContent=false), no tiene
 * internet y no guarda nada. El panel se dibuja como capa de accesibilidad, asi la app de atras sigue (el video no
 * se pausa).
 */
public class QuickMenuService extends AccessibilityService implements QuickMenu.Host {

    private WindowManager wm;
    private View overlay;
    private FrameLayout cachedHost;
    private View cachedContent;

    /** Se arma una sola vez; las siguientes aperturas solo actualizan hora, red y opciones. */
    @SuppressLint("InflateParams")
    private void buildHost() {
        ContextThemeWrapper themed = new ContextThemeWrapper(this, R.style.Theme_TvHome);
        cachedHost = new FrameLayout(themed) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                int c = e.getKeyCode();
                if (c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_HOME || c == KeyEvent.KEYCODE_ESCAPE) {
                    if (e.getAction() == KeyEvent.ACTION_UP) closeAnimated();
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        cachedContent = LayoutInflater.from(themed).inflate(R.layout.overlay_quick, cachedHost, false);
        cachedContent.setVisibility(View.VISIBLE);
        cachedHost.addView(cachedContent);
    }

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_NOTIFICATION && code != KeyEvent.KEYCODE_SETTINGS) return false;
        if (dbg()) Log.d(TAG, "engranaje action=" + event.getAction() + " repeat=" + event.getRepeatCount() + " abierto=" + (overlay != null));
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (overlay != null) closeAnimated();
            else show();
        }
        return true; // el engranaje es nuestro; el sistema lo descartaba igual
    }

    private WindowManager.LayoutParams lp;
    private boolean attached;
    private static final String TAG = "QuickMenuService";
    /** Depuracion apagada por defecto. Encender: adb shell setprop log.tag.QuickMenuService DEBUG */
    private static boolean dbg() { return Log.isLoggable(TAG, Log.DEBUG); }
    /** Depuracion: registra cada cuadro dibujado hasta 800 ms despues de abrir o cerrar. */
    private long traceUntil;

    private WindowManager.LayoutParams params(boolean visible) {
        if (lp == null) {
            lp = new WindowManager.LayoutParams(1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            lp.windowAnimations = 0; // sin animacion del sistema: solo la nuestra
            lp.setTitle("MenuRapido");
        }
        if (visible) {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        } else {
            // Oculto: la ventana sigue existiendo (asi la TV no muestra un cuadro viejo al destruirla),
            // pero mide 1x1, no toma el foco ni los toques.
            lp.width = 1;
            lp.height = 1;
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }
        return lp;
    }

    private void trace(String what) {
        if (!dbg()) return;
        traceUntil = android.os.SystemClock.uptimeMillis() + 800;
        Log.d(TAG, what + " t=" + android.os.SystemClock.uptimeMillis());
    }

    private void show() {
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (cachedHost == null) {
            buildHost();
            cachedHost.getViewTreeObserver().addOnDrawListener(() -> {
                if (android.os.SystemClock.uptimeMillis() < traceUntil) {
                    Log.d(TAG, "cuadro t=" + android.os.SystemClock.uptimeMillis() + " alpha=" + cachedContent.getAlpha()
                            + " visible=" + (cachedContent.getVisibility() == View.VISIBLE)
                            + " ventana=" + cachedHost.getWidth() + "x" + cachedHost.getHeight());
                }
            });
        }
        View content = cachedContent;
        content.animate().cancel();
        View first = QuickMenu.populate(this, content, this);
        content.setVisibility(View.VISIBLE);
        try {
            if (!attached) {
                wm.addView(cachedHost, params(true));
                attached = true;
            } else {
                wm.updateViewLayout(cachedHost, params(true));
            }
        } catch (Exception e) {
            Log.e(TAG, "no pude mostrar el menu", e);
            return;
        }
        overlay = cachedHost;
        trace("show");

        View panel = content.findViewById(R.id.quickPanel);
        float density = getResources().getDisplayMetrics().density;
        content.setAlpha(0f);
        panel.setTranslationX(60 * density);
        content.animate().alpha(1f).setDuration(160).start();
        panel.animate().translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        cachedHost.post(first::requestFocus);
    }

    private void closeAnimated() {
        if (overlay == null) return;
        trace("cerrando");
        cachedContent.animate().cancel();
        cachedContent.animate().alpha(0f).setDuration(130).withEndAction(this::close).start();
    }

    @Override
    public void close() {
        if (overlay == null) return;
        overlay = null;
        cachedContent.animate().cancel();
        cachedContent.setAlpha(0f);
        cachedContent.setVisibility(View.GONE);
        try {
            wm.updateViewLayout(cachedHost, params(false));
        } catch (Exception e) {
            Log.e(TAG, "no pude ocultar el menu", e);
        }
        trace("close");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        close();
    }

    private void detach() {
        close();
        if (attached) {
            try { wm.removeView(cachedHost); } catch (Exception ignored) {}
            attached = false;
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        detach();
        return super.onUnbind(intent);
    }
}
