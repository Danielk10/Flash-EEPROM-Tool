import os
import re

main_activity_path = "app/src/main/java/com/diamon/curso/ui/activities/MainActivity.java"

with open(main_activity_path, "r") as f:
    java = f.read()

# Add btnAbort and cbFastWrite to fields
if "Button btnAbort" not in java:
    java = java.replace("private Button btnRunCustomCommand, btnClearLogs, btnQuickClear, btnEraseChip;",
                        "private Button btnRunCustomCommand, btnClearLogs, btnQuickClear, btnEraseChip, btnAbort;\n    private android.widget.CheckBox cbFastWrite;")

if "java.util.Arrays" not in java:
    java = java.replace("import java.util.ArrayList;", "import java.util.ArrayList;\nimport java.util.Arrays;")

with open(main_activity_path, "w") as f:
    f.write(java)

# Add @string/str_detener to strings.xml if not present
strings_path = "app/src/main/res/values/strings.xml"
with open(strings_path, "r") as f:
    strings = f.read()

if "str_detener" not in strings:
    strings = strings.replace("</resources>", '    <string name="str_detener">Detener</string>\n</resources>')
    with open(strings_path, "w") as f:
        f.write(strings)

strings_es_path = "app/src/main/res/values-es/strings.xml"
with open(strings_es_path, "r") as f:
    strings_es = f.read()

if "str_detener" not in strings_es:
    strings_es = strings_es.replace("</resources>", '    <string name="str_detener">Detener</string>\n</resources>')
    with open(strings_es_path, "w") as f:
        f.write(strings_es)
        
print("Fixed!")
