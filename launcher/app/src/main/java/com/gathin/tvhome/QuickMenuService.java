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
 * Solo actua con el boton de engranaje del control (KEYCODE_NOTIFICATION 83 en esta TCL, o KEYCODE_SETTINGS)
 * y con el boton Guia (abre la TV en vivo, que antes atendia launcherx; dentro de la TV, TCL abre su guia);
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
        if (dbg()) Log.d(TAG, "servicio conectado");
    }

    /** Tecla propia de TCL para el boton Guia/EPG del control (scanCode 365). */
    private static final int KEYCODE_TCL_EPG = 4001;
    private static final String LIVE_TV_PKG = "com.tcl.tv";
    /** App de delante, segun los eventos de cambio de ventana (solo el nombre del paquete). */
    private String topPkg = "";

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (dbg()) Log.d(TAG, "tecla " + code + " scan=" + event.getScanCode() + " action=" + event.getAction() + " top=" + topPkg);
        if (code == KEYCODE_TCL_EPG || code == KeyEvent.KEYCODE_GUIDE) return onGuideKey(event);
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

    /**
     * Boton Guia: fuera de la TV en vivo, TCL lo reenvia como KEYCODE_GUIDE al receptor global de launcherx (el
     * inicio de Google TV, desactivado), asi que sin esto no hace nada. Aqui abre la TV en vivo; con la TV en vivo
     * delante se deja pasar y TCL abre su guia de canales.
     */
    private boolean onGuideKey(KeyEvent event) {
        if (LIVE_TV_PKG.equals(topPkg)) return false;
        if (dbg()) Log.d(TAG, "guia action=" + event.getAction() + " top=" + topPkg);
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (overlay != null) close();
            openLiveTv(this);
        }
        return true;
    }

    /** Abre la TV en vivo de TCL (ultimo canal); si no esta, su guia de canales (app://com.tcl.tv/epg). */
    static void openLiveTv(android.content.Context ctx) {
        android.content.Intent live = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(LIVE_TV_PKG);
        if (live == null) live = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("app://" + LIVE_TV_PKG + "/epg"));
        live.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(live);
        } catch (Exception e) {
            Log.w(TAG, "no pude abrir la TV en vivo", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            CharSequence cls = event.getClassName();
            if (dbg()) Log.d(TAG, "ventana " + pkg + "/" + cls);
            // Solo cuenta una Activity de verdad: com.tcl.tv tambien abre ventanas de fondo (servicios, avisos),
            // y el panel del menu rapido no es una Activity. El inicio (nuestra MainActivity) si cuenta.
            if (cls == null) return;
            try {
                getPackageManager().getActivityInfo(new android.content.ComponentName(pkg, cls.toString()), 0);
                topPkg = pkg;
            } catch (Exception notAnActivity) {
                // dialogo, toast o capa del sistema: no cambia la app de delante
            }
        }
    }

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
