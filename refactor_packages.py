import os
import shutil

base_dir = "/home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/java/com/diamon/curso"
manifest_path = "/home/danielpdiamon/Flash-EEPROM-Tool/app/src/main/AndroidManifest.xml"

mapping = {
    "MainActivity.java": "ui/activities",
    "HexViewerActivity.java": "ui/activities",
    "HexDiffActivity.java": "ui/activities",
    "ProgrammerSettingsActivity.java": "ui/activities",
    "PolicyActivity.java": "ui/activities",
    "PinoutView.java": "ui/views",
    "AssetHelper.java": "utils",
    "FileManager.java": "utils",
    "PtyBridge.java": "core",
    "Publicidad.java": "ads",
    "MostrarPublicidad.java": "ads",
}

# 1. Create directories and move files
for file_name, sub_pkg in mapping.items():
    sub_dir = os.path.join(base_dir, sub_pkg)
    os.makedirs(sub_dir, exist_ok=True)
    src = os.path.join(base_dir, file_name)
    dst = os.path.join(sub_dir, file_name)
    if os.path.exists(src):
        shutil.move(src, dst)

# 2. Update package and imports in all .java files
java_files = []
for root, _, files in os.walk(base_dir):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

def get_class_package(class_name):
    if class_name in mapping:
        return "com.diamon.curso." + mapping[class_name].replace("/", ".")
    return None

for java_file in java_files:
    with open(java_file, "r") as f:
        content = f.read()
    
    file_name = os.path.basename(java_file)
    if file_name not in mapping:
        continue

    new_pkg = get_class_package(file_name)
    content = content.replace("package com.diamon.curso;", f"package {new_pkg};")
    
    imports_to_add = set()
    for other_class, other_pkg in mapping.items():
        class_name = other_class.replace(".java", "")
        # Only add import if class is used as whole word
        import re
        if re.search(r'\b' + class_name + r'\b', content) and other_class != file_name:
            if mapping[other_class] != mapping[file_name]: # different package
                imports_to_add.add(f"import {get_class_package(other_class)}.{class_name};")
                
    if "R." in content and new_pkg != "com.diamon.curso":
        imports_to_add.add("import com.diamon.curso.R;")

    if imports_to_add:
        pkg_statement = f"package {new_pkg};"
        imports_str = "\n".join(sorted(list(imports_to_add)))
        content = content.replace(pkg_statement, pkg_statement + "\n\n" + imports_str)
    
    with open(java_file, "w") as f:
        f.write(content)

# 3. Update AndroidManifest.xml
with open(manifest_path, "r") as f:
    manifest_content = f.read()

for class_name, sub_pkg in mapping.items():
    if "Activity" in class_name:
        name_no_ext = class_name.replace(".java", "")
        dot_pkg = sub_pkg.replace("/", ".")
        manifest_content = manifest_content.replace(f'".{name_no_ext}"', f'".{dot_pkg}.{name_no_ext}"')

with open(manifest_path, "w") as f:
    f.write(manifest_content)
