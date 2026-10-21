package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Contenido del menu rapido (boton de engranaje). Lo usan el launcher (cuando esta en pantalla) y
 * QuickMenuService (encima de cualquier app). Cada opcion abre una pantalla del sistema o le pide
 * algo al launcher con el extra "accion".
 */
final class QuickMenu {

    static final String EXTRA_ACCION = "accion";
    static final String ACCION_PERSONALIZAR = "personalizar";
    static final String ACCION_ENTRADAS = "entradas";

    interface Host {
        /** Cierra el menu (sin animacion) antes de abrir otra cosa. */
        void close();
    }

    private QuickMenu() {}

    /** Rellena la cabecera y las opciones del layout overlay_quick ya inflado. Devuelve la primera opcion. */
    static View populate(Context ctx, View root, Host host) {
        ((TextView) root.findViewById(R.id.quickClock)).setText(
                new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()));
        String red = networkName(ctx);
        ((TextView) root.findViewById(R.id.quickSubtitle)).setText("Menú rápido · " + red);

        LinearLayout items = root.findViewById(R.id.quickItems);
        items.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(root.getContext());

        add(inf, items, "🖼️", "Imagen", "Modo de imagen, brillo y retroiluminación",
                () -> openAction(ctx, host, Settings.ACTION_DISPLAY_SETTINGS, null));
        add(inf, items, "🔊", "Sonido", "Modo de sonido, ecualizador y salida de audio",
                () -> openAction(ctx, host, "com.android.tv.action.VIEW_SOUND_SETTINGS", Settings.ACTION_SOUND_SETTINGS));
        add(inf, items, "📺", "Entradas", "HDMI y TV en vivo",
                () -> toLauncher(ctx, host, ACCION_ENTRADAS));
        add(inf, items, "📶", "Red e Internet", red,
                () -> openAction(ctx, host, Settings.ACTION_WIFI_SETTINGS, null));
        add(inf, items, "🎮", "Control y Bluetooth", "Emparejar el control, audífonos o parlantes",
                () -> openAction(ctx, host, "com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES", Settings.ACTION_BLUETOOTH_SETTINGS));
        add(inf, items, "🖥️", "Pantalla", "Resolución, tamaño del texto y fotogramas", () -> {
            host.close();
            start(ctx, new Intent().setComponent(new ComponentName("com.android.tv.settings",
                    "com.android.tv.settings.device.displaysound.DisplaySoundActivity")));
        });
        add(inf, items, "✨", "Personalizar inicio", "Fondo, oscurecer, dock y barra superior",
                () -> toLauncher(ctx, host, ACCION_PERSONALIZAR));
        add(inf, items, "⚙️", "Todos los ajustes", "Ajustes completos de la TV",
                () -> openAction(ctx, host, Settings.ACTION_SETTINGS, null));

        // El foco no sale del panel: ni a los lados ni dando la vuelta arriba/abajo.
        int n = items.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = items.getChildAt(i);
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(v.getId());
        }
        View first = items.getChildAt(0), last = items.getChildAt(n - 1);
        first.setNextFocusUpId(first.getId());
        last.setNextFocusDownId(last.getId());
        return first;
    }

    private static void add(LayoutInflater inf, ViewGroup parent, String icon, String title, String desc, Runnable action) {
        View item = inf.inflate(R.layout.item_quick, parent, false);
        item.setId(View.generateViewId());
        ((TextView) item.findViewById(R.id.quickIcon)).setText(icon);
        ((TextView) item.findViewById(R.id.quickTitle)).setText(title);
        ((TextView) item.findViewById(R.id.quickDesc)).setText(desc);
        item.setOnClickListener(v -> action.run());
        parent.addView(item);
    }

    /**
     * Abre un ajuste por su accion. En esta TCL las pantallas de Imagen y Sonido solo se muestran si llegan con
     * su accion (abiertas por componente se cierran solas); si la accion no existe, se prueba la alternativa.
     */
    static void openAction(Context ctx, Host host, String action, String fallbackAction) {
        host.close();
        if (!start(ctx, new Intent(action)) && fallbackAction != null) start(ctx, new Intent(fallbackAction));
    }

    private static void toLauncher(Context ctx, Host host, String accion) {
        host.close();
        start(ctx, new Intent(ctx, MainActivity.class).putExtra(EXTRA_ACCION, accion)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    static boolean start(Context ctx, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            Toast.makeText(ctx, "No disponible en esta TV", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    static String networkName(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) return "Sin conexión";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            String ssid = info != null ? info.getSSID() : null;
            if (ssid != null && !ssid.contains("unknown ssid")) return ssid.replace("\"", "");
            return "Wi-Fi";
        } catch (Exception e) {
            return "Wi-Fi";
        }
    }
}
