# Compatibilidad: en qué TV está probado y candidatas

## Probado y funcionando (2026-10-21)

| Dato | Valor |
|---|---|
| Modelo | **TCL 55" P7K** (Google TV), nombre interno «Smart TV Pro» (`ro.oem.key1 = ATV00105523P7K`) |
| Dispositivo / producto | `G09` / `G09_4K_GB`, placa `TC8000` |
| Chip | Amlogic **T963D4** (plataforma `t5m`), 32 bits (`armeabi-v7a`) |
| Memoria | 2 GB (~1,9 GB visibles) |
| Sistema | Google TV, Android 12 (SDK 31), build `STT2.230203.001.AS24` |
| Huella | `TCL/G09_4K_GB/G09:12/STT2.230203.001/AS25:user/release-keys` |

Lo que funcionó en esta TV:

- **Conexión:** basta con *Depuración por USB* (Ajustes → Sistema → Información → pulsar 7 veces «Compilación del SO de Android TV»); el puerto 5555 queda abierto por Wi-Fi.
- **Limpieza:** 26 paquetes desactivados con `config/debloat-tcl-g09.txt` (reversible con `tools\restore.ps1`).
- **Launcher propio de inicio:** hubo que desactivar `com.google.android.tungsten.setupwraith` **antes** de `launcherx`. Antes de hacerlo, olvidar la red Wi-Fi y volver a unirse desde Ajustes, porque las redes creadas en el asistente inicial pertenecen a setupwraith. Probado el reinicio y la vuelta con `tools\restaurar-inicio-google.ps1`.
- **Botón ⚙️ del control:** manda `KEYCODE_NOTIFICATION` (83). El menú rápido funciona sobre cualquier app con `QuickMenuService` (`tools\menu-rapido.ps1 -Activar`) y sigue activo tras reiniciar.
- **Imagen y Sonido de TCL:** se abren por acción (`android.settings.DISPLAY_SETTINGS`, `com.android.tv.action.VIEW_SOUND_SETTINGS`); si se abren por componente, se cierran solas.
- **Particularidades de esta TCL:** quitar la ventana del menú hace que la TV muestre un cuadro fantasma (por eso la ventana se reutiliza). La TV no trae selector de archivos (el launcher tiene el suyo). `screencap` sale negro en apps con DRM.

## Candidatas (sin probar)

La confianza se basa en cuánto se parecen a la probada. Antes de tocar nada, revisar siempre con `tools\tv.py --probe` y con la cadena de HOME (`cmd package query-activities ... HOME`).

| Confianza | TV | Por qué |
|---|---|---|
| **Alta** | TCL P7K de otros tamaños (43/50/65/75") y otras TCL con producto `G09_*` | Misma familia de firmware y mismo chip: deberían comportarse igual |
| **Media** | TCL con Google TV 12 y build `STT2.230203.*` de otra plataforma, por ejemplo `G10` (Realtek, lista en `examples/tcl-g10-google-tv`) | Mismo sistema de TCL, otro chip. Los nombres de paquete coinciden casi todos; el botón ⚙️ y los paneles de Imagen/Sonido pueden variar |
| **Media-baja** | Otras marcas con Google TV 12 (Sony, Hisense...) | Mismo mecanismo de inicio (launcherx → setupwraith), pero los paquetes del fabricante, los paneles de imagen y las teclas del control cambian |
| **Baja, con cuidado** | Google TV 14 (Xiaomi, Chromecast, Kalley, etc.) | En repos parecidos se reportan bucles de arranque al quitar setupwraith y que el Chromecast deja de funcionar; algunos modelos reactivan apps solos al arrancar |
| **Distinto camino** | Android TV sin Google TV (inicio `com.google.android.tvlauncher`) | La limpieza sirve igual; el cambio de inicio suele ser más sencillo (no hay setupwraith) |
| **No sirve** | Samsung (Tizen), LG (webOS), Roku, Fire TV | No son Android TV con ADB de este tipo |

## Si la pruebas en otra TV

1. Respaldo primero (`backup/<fecha>-<equipo>/`), como en el procedimiento de la TCL.
2. Partir de la lista del ejemplo más parecido y revisar cada paquete con `dumpsys package` antes de desactivarlo.
3. Confirmar la tecla del ⚙️ con `getevent -lt` y `logcat | grep handleKeyBeforeQueueing` mientras alguien la presiona.
4. Anotar aquí el resultado (modelo, `ro.product.name`, build y qué funcionó).
