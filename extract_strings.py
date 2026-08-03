import os
import re

LAYOUT_DIR = "app/src/main/res/layout"
VALUES_DIR = "app/src/main/res/values"
VALUES_ES_DIR = "app/src/main/res/values-es"

os.makedirs(VALUES_DIR, exist_ok=True)
os.makedirs(VALUES_ES_DIR, exist_ok=True)

# Translation dictionary mapping Spanish (original) to English
translations = {
    "Selecciona dos archivos para comparar": "Select two files to compare",
    "Archivo A": "File A",
    "Archivo B": "File B",
    "Cargando archivo...": "Loading file...",
    "Preparando dependencias nativas...": "Preparing native dependencies...",
    "Estado USB: Desconectado": "USB Status: Disconnected",
    "Detectar y Conectar Automáticamente": "Detect and Connect Automatically",
    "Identificar Chip": "Identify Chip",
    "Verificar ROM": "Verify ROM",
    "Leer Backup": "Read Backup",
    "Flashear ROM": "Flash ROM",
    "Cargar ROM": "Load ROM",
    "Guardar ROM": "Save ROM",
    "Borrar Chip": "Erase Chip",
    "Borrar ROM": "Erase ROM",
    "Ejecutar Instrucción por Consola": "Execute Console Instruction",
    "Terminal Salida:": "Terminal Output:",
    "Limpiar": "Clear",
    "--- Log ---": "--- Log ---",
    "Detener": "Stop",
    "Auto-Scroll": "Auto-Scroll",
    "Política de Privacidad": "Privacy Policy",
    "Aceptar": "Accept",
    "Programador:": "Programmer:",
    "Extra Args:": "Extra Args:",
    "Guardar": "Save",
    "Cerrar": "Close"
}

# We will collect all string definitions here
strings_en = {}
strings_es = {}

def get_id(text):
    base = re.sub(r'[^a-zA-Z0-9_]', '', text.lower().replace(" ", "_"))
    if not base:
        base = "str"
    return "str_" + base[:15]

# Process XML layout files
for xml_file in os.listdir(LAYOUT_DIR):
    if not xml_file.endswith(".xml"): continue
    filepath = os.path.join(LAYOUT_DIR, xml_file)
    with open(filepath, "r") as f:
        content = f.read()

    def repl(m):
        full_match = m.group(0)
        text_val = m.group(1)
        if text_val.startswith("@string/") or text_val == "":
            return full_match
        
        # create ID
        str_id = get_id(text_val)
        
        # Store translations
        strings_es[str_id] = text_val
        strings_en[str_id] = translations.get(text_val, text_val) # fallback to ES if no EN translation
        
        return f'android:text="@string/{str_id}"'

    new_content = re.sub(r'android:text="([^"]+)"', repl, content)
    
    with open(filepath, "w") as f:
        f.write(new_content)

# Additional Java Strings dictionary
java_strings = {
    "Identificar": "Identify",
    "Leer": "Read",
    "Escribir": "Write",
    "Borrar": "Erase",
    "Verificar": "Verify",
    "Limpiar Pantalla": "Clear Screen",
    "Ver/Editar Buffer": "View/Edit Buffer",
    "Visor Hexadecimal": "Hexadecimal Viewer",
    "Comparar Hex": "Compare Hex",
    "Configuración del Programador": "Programmer Settings",
    "Políticas de Privacidad": "Privacy Policy",
    "Pinouts de Hardware": "Hardware Pinouts",
    "Acerca de": "About",
    "Advertencia": "Warning",
    "Operación Exitosa": "Operation Successful",
    "Error": "Error",
    "Cancelar": "Cancel",
    "Opciones": "Options",
    "Archivo": "File",
    "Salir": "Exit"
}

# Add java strings to resources
for es_text, en_text in java_strings.items():
    str_id = get_id(es_text)
    strings_es[str_id] = es_text
    strings_en[str_id] = en_text

# Write strings.xml (English - Default)
with open(os.path.join(VALUES_DIR, "strings.xml"), "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    f.write('    <string name="app_name">Flash EEPROM Tool</string>\n')
    for k, v in strings_en.items():
        v = v.replace("'", "\\'").replace('"', '\\"')
        f.write(f'    <string name="{k}">{v}</string>\n')
    f.write('</resources>\n')

# Write strings.xml (Spanish)
with open(os.path.join(VALUES_ES_DIR, "strings.xml"), "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    f.write('    <string name="app_name">Flash EEPROM Tool</string>\n')
    for k, v in strings_es.items():
        v = v.replace("'", "\\'").replace('"', '\\"')
        f.write(f'    <string name="{k}">{v}</string>\n')
    f.write('</resources>\n')

print("Extraction and creation of strings.xml complete.")
