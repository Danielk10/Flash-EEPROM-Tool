package com.diamon.curso.ui.activities;

import com.diamon.curso.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ProgrammerSettingsActivity extends AppCompatActivity {

    private static final String PREFS = "flashrom_prefs";
    private static final String KEY_PROGRAMMER = "selected_programmer";

    private static final String[] SUPPORTED_PROGRAMMERS = {
            "asm106x", "atavia", "buspirate_spi", "ch341a_spi", "ch347_spi", "dediprog",
            "developerbox_spi", "digilent_spi", "dirtyjtag_spi", "drkaiser", "dummy",
            "ft2232_spi", "gfxnvidia", "internal", "it8212", "jlink_spi", "linux_mtd",
            "linux_spi", "mediatek_i2c_spi", "mstarddc_spi", "nicintel", "nicintel_eeprom",
            "nicintel_spi", "nv_sma_spi", "ogp_spi", "parade_lspcon", "pickit2_spi",
            "pony_spi", "raiden_debug_spi", "realtek_mst_i2c_spi", "satasii", "serprog",
            "spidriver", "stlinkv3_spi", "usbblaster_spi"
    };

    private Spinner spinnerProgrammer;
    private EditText etProgrammerParam;
    private Button btnSaveProgrammer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_programmer_settings);

        setTitle("Configuración de Programador");

        etProgrammerParam = findViewById(R.id.etProgrammerParam);
        btnSaveProgrammer = findViewById(R.id.btnSaveProgrammer);
        spinnerProgrammer = findViewById(R.id.spinnerProgrammer);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String current = prefs.getString(KEY_PROGRAMMER, "ch341a_spi");
        etProgrammerParam.setText(current);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                SUPPORTED_PROGRAMMERS);
        spinnerProgrammer.setAdapter(adapter);

        // Intentar pre-seleccionar el programador base que el usuario ya tenia guardado
        for (int i = 0; i < SUPPORTED_PROGRAMMERS.length; i++) {
            if (current != null && current.startsWith(SUPPORTED_PROGRAMMERS[i])) {
                spinnerProgrammer.setSelection(i);
                break;
            }
        }

        spinnerProgrammer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean isFirstLaunch = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isFirstLaunch) {
                    isFirstLaunch = false;
                    return; // Evitamos sobreescribir el etProgrammerParam al instanciar la actividad
                }
                etProgrammerParam.setText(SUPPORTED_PROGRAMMERS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnSaveProgrammer.setOnClickListener(v -> {
            String value = etProgrammerParam.getText().toString().trim();
            if (value.isEmpty()) {
                value = "ch341a_spi";
            }
            prefs.edit().putString(KEY_PROGRAMMER, value).apply();
            Toast.makeText(this, "Guardado: " + value, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
