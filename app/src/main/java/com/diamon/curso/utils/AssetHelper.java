package com.diamon.curso.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import android.content.SharedPreferences;
import java.util.Set;

public class AssetHelper {
    private static final String TAG = "AssetHelper";
    private static final String PREFS_NAME = "AssetHelperPrefs";
    private static final String KEY_EXTRACTED = "assets_extracted_v2";
    private static final int BUFFER_SIZE = 8192;
    private static volatile String cachedAssetRuntimeRoot;
    private static final String COMPILED_RUNTIME_ROOT = "data/data/com.diamon.curso/files/usr";

    /**
     * Prepara runtime de assets una sola vez. Si ya existen, no vuelve a recorrer
     * todo el árbol;
     * solamente repara archivos críticos faltantes.
     */
    public static synchronized boolean ensureRuntimeReady(Context context) {
        long start = System.currentTimeMillis();
        String runtimeRoot = resolveAssetRuntimeRoot(context);
        if (runtimeRoot == null) {
            Log.e(TAG, "No se encontró ruta runtime en assets (data/data/*/files/usr).");
            return false;
        }

        File usrDir = new File(context.getFilesDir(), "usr");
        boolean alreadyExtracted = areAssetsExtracted(context);

        if (!alreadyExtracted) {
            if (!extractAssets(context, runtimeRoot, usrDir)) {
                return false;
            }
            // Marcamos como extraído exitosamente
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_EXTRACTED, true).apply();

            boolean linked = ensureNativeToolLinks(context);
            long duration = System.currentTimeMillis() - start;
            Log.i(TAG, "ensureRuntimeReady (extracción) completado en " + duration + "ms. Resultado: " + linked);
            return linked;
        }

        // Reparación mínima de recursos críticos del runtime compilado (no
        // ejecutables).
        AssetManager assetManager = context.getAssets();
        File pciIdsTarget = new File(usrDir, "share/pci.ids.gz");
        if (!pciIdsTarget.exists() && !copyCriticalPciIds(assetManager, runtimeRoot, pciIdsTarget)) {
            Log.e(TAG, "Fallo al reparar pci.ids.gz");
            return false;
        }

        boolean ok = ensureNativeToolLinks(context);
        long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "ensureRuntimeReady (reparación) completado en " + duration + "ms. Resultado: " + ok);
        return ok;
    }

    public static String getResolvedRuntimeRoot(Context context) {
        return resolveAssetRuntimeRoot(context);
    }

    private static String resolveAssetRuntimeRoot(Context context) {
        if (cachedAssetRuntimeRoot != null) {
            return cachedAssetRuntimeRoot;
        }

        AssetManager assetManager = context.getAssets();
        try {
            // 1) Prioridad absoluta: ruta exacta de compilación documentada.
            String[] exactChildren = assetManager.list(COMPILED_RUNTIME_ROOT);
            if (exactChildren != null && exactChildren.length > 0) {
                cachedAssetRuntimeRoot = COMPILED_RUNTIME_ROOT;
                Log.i(TAG, "Runtime root de assets detectado (exacto): " + cachedAssetRuntimeRoot);
                return cachedAssetRuntimeRoot;
            }

            String[] pkgCandidates = assetManager.list("data/data");
            if (pkgCandidates == null || pkgCandidates.length == 0) {
                return null;
            }

            String expectedPkg = context.getPackageName();
            List<String> ordered = new ArrayList<>();
            // Priorizamos el package real de la app
            ordered.add(expectedPkg);
            for (String candidate : pkgCandidates) {
                if (candidate != null && !candidate.equals(expectedPkg)) {
                    ordered.add(candidate);
                }
            }

            for (String pkg : ordered) {
                if (pkg == null || pkg.isEmpty()) {
                    continue;
                }
                String candidateRoot = "data/data/" + pkg + "/files/usr";
                String[] children = assetManager.list(candidateRoot);
                if (children != null && children.length > 0) {
                    cachedAssetRuntimeRoot = candidateRoot;
                    Log.i(TAG, "Runtime root de assets detectado: " + candidateRoot);
                    return cachedAssetRuntimeRoot;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "No se pudo resolver runtime root de assets: " + e.getMessage());
        }
        return null;
    }

    public static boolean extractAssets(Context context, String assetPath, File destDir) {
        AssetManager assetManager = context.getAssets();

        try {
            String[] files = assetManager.list(assetPath);

            if (files == null || files.length == 0) {
                // Es un archivo, copiarlo
                return copyAssetFile(assetManager, assetPath, destDir);
            } else {
                // Es un directorio, crearlo y procesar recursivamente
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Log.e(TAG, "No se pudo crear directorio: " + destDir.getAbsolutePath());
                    return false;
                }

                for (String fileName : files) {
                    if (fileName == null || fileName.isEmpty())
                        continue;

                    String childAssetPath = assetPath + "/" + fileName;
                    File childDestDir = new File(destDir, fileName);

                    // Verificar si es un directorio o archivo
                    String[] subFiles = assetManager.list(childAssetPath);
                    if (subFiles != null && subFiles.length > 0) {
                        // Es un subdirectorio
                        if (!extractAssets(context, childAssetPath, childDestDir)) {
                            return false;
                        }
                    } else {
                        // Es un archivo
                        if (!copyAssetFile(assetManager, childAssetPath, destDir)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extrayendo assets: " + e.getMessage());
            return false;
        }
    }

    private static boolean copyAssetFile(AssetManager assetManager, String assetPath, File destDir) {
        String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
        if (assetPath.endsWith("/share/pci.ids")) {
            // En algunos empaquetados Android, pci.ids.gz puede quedar listado como
            // pci.ids.
            // Conservamos el nombre runtime esperado por pciutils/flashrom.
            fileName = "pci.ids.gz";
        }
        File destFile = new File(destDir, fileName);

        // Si el archivo ya existe, omitir
        if (destFile.exists()) {
            return true;
        }

        // Asegurar que el directorio padre existe de forma robusta
        if (!destDir.exists()) {
            if (!destDir.mkdirs() && !destDir.exists()) {
                Log.e(TAG, "No se pudo crear directorio (y no existe): " + destDir.getAbsolutePath());
                return false;
            }
        } else if (!destDir.isDirectory()) {
            Log.e(TAG, "Existe un archivo con el mismo nombre que el directorio: " + destDir.getAbsolutePath());
            return false;
        }

        try (InputStream in = assetManager.open(assetPath);
                OutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            if (assetPath.contains("/bin/") || assetPath.contains("/sbin/")) {
                // noinspection ResultOfMethodCallIgnored
                destFile.setExecutable(true, true);
            }
            Log.d(TAG, "Copiado: " + destFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error copiando " + assetPath + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean areAssetsExtracted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean flagged = prefs.getBoolean(KEY_EXTRACTED, false);

        // Verificamos de forma dura que la estructura fundamental exista.
        // Android Auto-Backup puede restaurar SharedPreferences sin restaurar los
        // archivos crudos (getFilesDir).
        File shareDir = new File(context.getFilesDir(), "usr/share");
        boolean shareExists = shareDir.exists() && shareDir.isDirectory();

        if (flagged && shareExists) {
            // Un check suave de que hay archivos extraídos internamente
            String[] list = shareDir.list();
            if (list != null && list.length > 0) {
                return true; // OK!
            }
        }

        Log.w(TAG,
                "areAssetsExtracted: Hubo una inconsistencia de caché (posible reinstalación). Forzando re-extracción.");
        return false;
    }

    private static boolean copyCriticalPciIds(AssetManager assetManager, String runtimeRoot, File target) {
        String[] candidates = new String[] {
                runtimeRoot + "/share/pci.ids.gz",
                runtimeRoot + "/share/pci.ids"
        };

        File parent = target.getParentFile();
        if (parent == null) {
            return false;
        }

        for (String candidate : candidates) {
            try (InputStream ignored = assetManager.open(candidate)) {
                Log.i(TAG, "Copiando pci.ids crítico desde asset: " + candidate);
                return copyAssetFile(assetManager, candidate, parent);
            } catch (IOException ignored) {
                // Intentar siguiente candidato
            }
        }

        Log.e(TAG, "No se encontró pci.ids(.gz) en assets para reconstruir runtime crítico.");
        return false;
    }

    private static boolean ensureNativeToolLinks(Context context) {
        File filesDir = context.getFilesDir();
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File usrBin = new File(filesDir, "usr/bin");
        File usrSbin = new File(filesDir, "usr/sbin");
        File usrLib = new File(filesDir, "usr/lib");
        File pythonSitePackages = new File(usrLib, "python3.14/site-packages");

        if (!usrBin.exists())
            usrBin.mkdirs();
        if (!usrSbin.exists())
            usrSbin.mkdirs();
        if (!usrLib.exists())
            usrLib.mkdirs();
        if (!pythonSitePackages.exists())
            pythonSitePackages.mkdirs();

        // Ejecutables/librerías principales se resuelven desde jniLibs con nombres
        // clásicos vía symlink.
        boolean ok = true;
        ok &= linkTool(new File(usrSbin, "flashrom"), new File(nativeLibDir, "libflashrom_bin.so"));
        ok &= linkTool(new File(usrBin, "lspci"), new File(nativeLibDir, "liblspci.so"));
        ok &= linkTool(new File(usrSbin, "setpci"), new File(nativeLibDir, "libsetpci.so"));
        ok &= linkTool(new File(usrSbin, "pcilmr"), new File(nativeLibDir, "libpcilmr.so"));
        // update-pciids is a bash script in assets, no symlink needed from jniLibs
        ok &= linkTool(new File(usrBin, "ftdi_eeprom"), new File(nativeLibDir, "libftdi_eeprom.so"));
        // libftdi1-config is a shell script extracted from assets (not ELF), no jniLibs link needed

        // Sonames esperados por binarios/nativas: apuntan al nombre Android copiable
        // (.so).
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libflashrom.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libflashrom.so.1");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libflashrom.so.1.0.0");

        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libpci.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libpci.so.3");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libpci.so.3.15.0");

        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libftdi1.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libftdi1.so.2");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libftdi1.so.2.6.0");

        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libftdipp1.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libftdipp1.so.3");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libftdipp1.so.2.6.0");

        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libusb-1.0.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libjaylink.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libcrypto.so.3");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libz.so.1");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libconfuse.so");
        ok &= linkRuntimeSoname(usrLib, nativeLibDir, "libc++_shared.so");
        // Extensión Python: nombre Android en jniLibs y nombre original vía symlink en
        // site-packages.
        if (!linkTool(new File(pythonSitePackages, "_pyftdi1.so"), new File(nativeLibDir, "libpyftdi1.so"))) {
            Log.e(TAG, "Fallo al enlazar extensión Python _pyftdi1.so");
            ok = false;
        }

        // Validación mínima de dependencias críticas para herramientas principales.
        if (!ensurePresent(new File(usrLib, "libcrypto.so.3"))) {
            Log.e(TAG, "Falta libcrypto.so.3 en runtime");
            ok = false;
        }
        if (!ensurePresent(new File(usrLib, "libconfuse.so"))) {
            Log.e(TAG, "Falta libconfuse.so en runtime");
            ok = false;
        }
        if (!ensurePresent(new File(usrLib, "libz.so.1"))) {
            Log.e(TAG, "Falta libz.so.1 en runtime");
            ok = false;
        }
        if (!ensurePresent(new File(usrLib, "libc++_shared.so"))) {
            Log.e(TAG, "Falta libc++_shared.so en runtime");
            ok = false;
        }

        return ok;
    }

    private static boolean linkRuntimeSoname(File usrLib, File nativeLibDir, String runtimeSoname) {
        File linkPath = new File(usrLib, runtimeSoname);
        File source = resolveNativeLibrary(nativeLibDir, runtimeSoname);
        if (source == null) {
            Log.w(TAG, "No se encontró librería para soname runtime: " + runtimeSoname);
            return false;
        }
        boolean result = linkTool(linkPath, source);
        if (!result) {
            Log.e(TAG, "Fallo al enlazar soname runtime: " + runtimeSoname);
        }
        return result;
    }

    private static File resolveNativeLibrary(File nativeLibDir, String runtimeSoname) {
        for (String candidate : getNativeCandidates(runtimeSoname)) {
            File file = new File(nativeLibDir, candidate);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private static List<String> getNativeCandidates(String runtimeSoname) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(runtimeSoname);

        if ("libusb-1.0.so".equals(runtimeSoname)) {
            candidates.add("libusb_1_0.so");
        }

        int soMarker = runtimeSoname.indexOf(".so.");
        if (soMarker > 0) {
            String base = runtimeSoname.substring(0, soMarker);
            String suffix = runtimeSoname.substring(soMarker + 4).replace('.', '_');
            candidates.add(base + "_" + suffix + ".so");
        }

        return new ArrayList<>(candidates);
    }

    private static boolean ensurePresent(File file) {
        if (!file.exists()) {
            Log.w(TAG, "Falta dependencia crítica: " + file.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static boolean linkTool(File linkPath, File target) {
        if (!target.exists()) {
            Log.e(TAG, "Librería nativa faltante. Imposible enlazar: " + target.getAbsolutePath());
            return false;
        }

        try {
            // Validar si es un symlink correcto
            if (linkPath.exists() && linkPath.getCanonicalPath().equals(target.getCanonicalPath())) {
                return true; // Ya existe y apunta correctamente
            }
            // Eliminar si existe un enlace roto (al actualizar APK se desfasan las rutas
            // base de nativeLibraryDir)
            linkPath.delete();
            // Crear nuevo symlink
            Os.symlink(target.getAbsolutePath(), linkPath.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Symlink falló para " + linkPath.getName() + " -> " + target.getName()
                    + ": " + e.getMessage() + ". Intentando fallback por copia...");
            return copyFile(target, linkPath);
        }
    }

    private static boolean copyFile(File source, File dest) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        try (InputStream in = new java.io.FileInputStream(source);
                OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            // Si es herramienta de ejecución, forzar permiso de ejecución.
            File binParent = dest.getParentFile();
            String parentName = binParent != null ? binParent.getName() : "";
            if ("bin".equals(parentName) || "sbin".equals(parentName)) {
                // noinspection ResultOfMethodCallIgnored
                dest.setExecutable(true, true);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copiando archivo de fallback: " + e.getMessage());
            return false;
        }
    }
}
