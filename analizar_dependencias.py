import subprocess, os

# Directorio de bibliotecas
jni_dir = 'app/src/main/jniLibs/arm64-v8a/'

# Verificar si el directorio existe
if not os.path.exists(jni_dir):
    print(f"Error: {jni_dir} no encontrado. Asegúrate de ejecutar este script desde la raíz del proyecto.")
    exit(1)

files = set(os.listdir(jni_dir))

# Generar Reporte
with open('REPORTE_ANALISIS_DEPENDENCIAS.md', 'w') as r:
    r.write('# Reporte Actualizado de Dependencias\n\n')
    for f in sorted(files):
        r.write(f'### {f}\n| Dep | Class | InFolder |\n|---|---|---|\n')
        try:
            out = subprocess.check_output(['readelf', '-d', jni_dir + f], text=True)
            for line in out.splitlines():
                if '(NEEDED)' in line:
                    d = line.split('[')[1].split(']')[0]
                    es_sistema = d in ['libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libz.so', 'libz.so.1', 'libstdc++.so', 'libgcc.so', 'libc++_shared.so']
                    c = 'Sistema' if es_sistema else 'Externa'
                    r.write(f'| {d} | {c} | {('Sí' if d in files else 'No')} |\n')
        except: pass
        r.write('\n')

# Verificación
missing_deps = set()
for f in files:
    try:
        out = subprocess.check_output(['readelf', '-d', jni_dir + f], text=True)
        for line in out.splitlines():
            if '(NEEDED)' in line:
                d = line.split('[')[1].split(']')[0]
                if d not in files and d not in ['libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libz.so', 'libz.so.1', 'libstdc++.so', 'libgcc.so', 'libc++_shared.so']:
                    missing_deps.add(d)
    except: pass

if missing_deps:
    print('Error: Dependencias faltantes:', missing_deps)
else:
    print('Verificación exitosa: Todas las dependencias externas están presentes.')
