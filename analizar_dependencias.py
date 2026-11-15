#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Analiza las dependencias (DT_NEEDED) de las librerías empaquetadas en
app/src/main/jniLibs/<abi>/ para las dos arquitecturas soportadas y regenera
REPORTE_ANALISIS_DEPENDENCIAS.md.

Los nombres de jniLibs están adaptados a las reglas de Google Play
(`lib<nombre>.so`) y la app los vuelve a enlazar con su SONAME original en
tiempo de ejecución (ver `AssetHelper.getNativeCandidates`). Este script aplica
esa misma resolución para comprobar que no falte ninguna dependencia.

Uso:
    python3 analizar_dependencias.py
"""

import datetime
import os
import subprocess
import sys

JNI_LIBS = os.path.join("app", "src", "main", "jniLibs")
REPORTE = "REPORTE_ANALISIS_DEPENDENCIAS.md"

# Librerías que provee Android en /system/lib (32-bit) o /system/lib64 (64-bit).
LIBRERIAS_SISTEMA = {
    "libc.so",
    "libm.so",
    "libdl.so",
    "liblog.so",
    "libz.so",
    "libstdc++.so",
    "libgcc.so",
    "libc++_shared.so",
    "libandroid.so",
}

ARQUITECTURAS = [
    {
        "titulo": "32-bit",
        "abi": "armeabi-v7a",
        "clase": "ELF32",
        "maquina": "ARM",
    },
    {
        "titulo": "64-bit",
        "abi": "arm64-v8a",
        "clase": "ELF64",
        "maquina": "AArch64",
    },
]


def readelf(args, ruta):
    try:
        return subprocess.check_output(
            ["readelf"] + args + [ruta], text=True, stderr=subprocess.DEVNULL
        )
    except (subprocess.CalledProcessError, FileNotFoundError, OSError):
        return None


def leer_cabecera(ruta):
    salida = readelf(["-h"], ruta)
    if salida is None:
        return None, None
    clase = maquina = None
    for linea in salida.splitlines():
        if "Class:" in linea and clase is None:
            clase = linea.split("Class:", 1)[1].strip()
        elif "Machine:" in linea and maquina is None:
            maquina = linea.split("Machine:", 1)[1].strip()
    return clase, maquina


def leer_dinamica(ruta):
    """Devuelve (needed, soname)."""
    salida = readelf(["-d"], ruta)
    if salida is None:
        return None, None
    needed = []
    soname = None
    for linea in salida.splitlines():
        if "(NEEDED)" in linea and "[" in linea:
            needed.append(linea.split("[", 1)[1].split("]", 1)[0])
        elif "(SONAME)" in linea and "[" in linea:
            soname = linea.split("[", 1)[1].split("]", 1)[0]
    return needed, soname


def candidatos_en_jni(soname):
    """Réplica de AssetHelper.getNativeCandidates()."""
    candidatos = [soname]
    if soname == "libusb-1.0.so":
        candidatos.append("libusb_1_0.so")
    marca = soname.find(".so.")
    if marca > 0:
        base = soname[:marca]
        sufijo = soname[marca + 4 :].replace(".", "_")
        candidatos.append(f"{base}_{sufijo}.so")
    return candidatos


def analizar(arq):
    directorio = os.path.join(JNI_LIBS, arq["abi"])
    if not os.path.isdir(directorio):
        return {
            "existe": False,
            "filas_elf": [],
            "filas_deps": [],
            "filas_soname": [],
            "faltantes": [f"falta el directorio {directorio}"],
            "n": 0,
        }

    archivos = sorted(
        f for f in os.listdir(directorio) if os.path.isfile(os.path.join(directorio, f))
    )
    presentes = set(archivos)
    dir_sistema = "/system/lib/" if arq["clase"] == "ELF32" else "/system/lib64/"

    filas_elf, filas_deps, filas_soname, faltantes = [], [], [], []

    # 1) Cabecera ELF de cada archivo.
    for nombre in archivos:
        ruta = os.path.join(directorio, nombre)
        clase, maquina = leer_cabecera(ruta)
        correcta = clase == arq["clase"] and maquina == arq["maquina"]
        filas_elf.append(
            [
                f"`{nombre}`",
                f"{clase or '?'} / {maquina or '?'}",
                f"{arq['clase']} / {arq['maquina']}",
                "✔" if correcta else "⚠️",
            ]
        )
        if not correcta:
            faltantes.append(f"{arq['abi']}/{nombre}: arquitectura incorrecta ({clase}/{maquina})")

    # 2) SONAME y su nombre en jniLibs.
    for nombre in archivos:
        ruta = os.path.join(directorio, nombre)
        _, soname = leer_dinamica(ruta)
        if soname is None:
            filas_soname.append([f"`{nombre}`", "—", "—", "—"])
            continue
        esperado = (
            soname
            if soname in presentes
            else next((c for c in candidatos_en_jni(soname) if c in presentes), None)
        )
        coincide = esperado == nombre
        filas_soname.append(
            [
                f"`{nombre}`",
                f"`{soname}`",
                f"`{esperado}`" if esperado else "❌",
                "✔" if coincide else ("≈" if esperado else "❌"),
            ]
        )

    # 3) Dependencias de cada archivo.
    for nombre in archivos:
        ruta = os.path.join(directorio, nombre)
        needed, _ = leer_dinamica(ruta)
        if not needed:
            continue
        for dep in needed:
            if dep in LIBRERIAS_SISTEMA:
                filas_deps.append(
                    [f"`{nombre}`", f"`{dep}`", "Sistema (Android)", f"✔ {dir_sistema}"]
                )
                continue
            resuelto = dep if dep in presentes else next(
                (c for c in candidatos_en_jni(dep) if c in presentes), None
            )
            if resuelto:
                filas_deps.append(
                    [f"`{nombre}`", f"`{dep}`", "Externa (empaquetada)", f"✔ `{resuelto}`"]
                )
            else:
                filas_deps.append([f"`{nombre}`", f"`{dep}`", "Externa", "❌ falta"])
                faltantes.append(f"{arq['abi']}: {dep} (requerida por {nombre})")

    return {
        "existe": True,
        "filas_elf": filas_elf,
        "filas_deps": filas_deps,
        "filas_soname": filas_soname,
        "faltantes": faltantes,
        "n": len(archivos),
    }


def tabla(cabeceras, filas):
    lineas = [
        "| " + " | ".join(cabeceras) + " |",
        "|" + "|".join(["---"] * len(cabeceras)) + "|",
    ]
    for fila in filas:
        lineas.append("| " + " | ".join(str(c) for c in fila) + " |")
    return "\n".join(lineas)


def main():
    resultados = [(arq, analizar(arq)) for arq in ARQUITECTURAS]
    pendientes = [f for _, r in resultados for f in r["faltantes"]]

    with open(REPORTE, "w", encoding="utf-8") as r:
        r.write("# Reporte de análisis de dependencias — 32 y 64 bits\n\n")
        r.write(
            "Generado por `analizar_dependencias.py` el "
            f"{datetime.date.today().isoformat()}.\n\n"
        )
        r.write(
            "Verifica las librerías de `app/src/main/jniLibs/<abi>/`: clase ELF, "
            "SONAME, nombre adaptado a Google Play y que cada dependencia "
            "`DT_NEEDED` se resuelva dentro de la misma carpeta o la provea Android.\n\n"
        )

        r.write("## Resumen\n\n")
        resumen = [
            [
                arq["titulo"],
                f"`{arq['abi']}`",
                str(res["n"]) + " archivos" if res["existe"] else "—",
                "✔ Completo" if res["existe"] and not res["faltantes"] else "⚠️ Incompleto",
            ]
            for arq, res in resultados
        ]
        r.write(tabla(["Arquitectura", "ABI", "Contenido", "Estado"], resumen))
        r.write("\n\n")

        for i, (arq, res) in enumerate(resultados, start=1):
            r.write(f"## {i}. {arq['titulo']} (`{arq['abi']}`)\n\n")
            if not res["existe"]:
                r.write("❌ No existe el directorio.\n\n")
                continue

            r.write("### Clase ELF\n\n")
            r.write(tabla(["Archivo", "Detectado", "Esperado", "OK"], res["filas_elf"]))
            r.write("\n\n")

            r.write("### SONAME → nombre en jniLibs\n\n")
            r.write(
                tabla(
                    ["Archivo", "SONAME", "Nombre que resuelve", "Coincide"],
                    res["filas_soname"],
                )
            )
            r.write("\n\n")

            r.write("### Dependencias `DT_NEEDED`\n\n")
            r.write(
                tabla(
                    ["Archivo", "Dependencia", "Tipo", "Resolución"], res["filas_deps"]
                )
            )
            r.write("\n\n")

        r.write("## Notas\n\n")
        r.write(
            "* `libc.so`, `libm.so`, `libdl.so` y `liblog.so` las provee Android; "
            "no se empaquetan en el APK.\n"
        )
        r.write(
            "* Los nombres con versión se adaptan a Google Play y la app restaura el "
            "SONAME original con symlinks en `usr/lib/` "
            "(`libcrypto.so.3` → `libcrypto_3.so`, `libpci.so.3.15.0` → `libpci_3_15_0.so`).\n"
        )
        r.write(
            "* Los ejecutables también viajan como `lib*.so` "
            "(`flashrom` → `libflashrom_bin.so`, `lspci` → `liblspci.so`, "
            "`setpci` → `libsetpci.so`, `pcilmr` → `libpcilmr.so`, "
            "`ftdi_eeprom` → `libftdi_eeprom.so`).\n"
        )
        r.write("\n")

        if pendientes:
            r.write("## ⚠️ Pendientes\n\n")
            for p in pendientes:
                r.write(f"* {p}\n")
            r.write("\n")

    if pendientes:
        print("Advertencia: elementos pendientes:")
        for p in pendientes:
            print(f"  - {p}")
        print(f"\nReporte regenerado en {REPORTE}")
        return 1

    print("Verificación exitosa: 32 y 64 bits con todas las dependencias presentes.")
    print(f"Reporte regenerado en {REPORTE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
