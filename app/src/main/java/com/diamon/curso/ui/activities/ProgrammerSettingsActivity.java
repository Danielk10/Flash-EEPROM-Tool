package com.diamon.curso.ui.activities;

import com.diamon.curso.R;
import com.diamon.curso.core.SpiSpeed;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
            "developerbox", "digilent_spi", "dirtyjtag_spi", "drkaiser", "dummy",
            "ft2232_spi", "gfxnvidia", "internal", "it8212", "jlink_spi", "linux_mtd",
            "linux_spi", "mediatek_i2c_spi", "mstarddc_spi", "nicintel", "nicintel_eeprom",
            "nicintel_spi", "nv_sma_spi", "ogp_spi", "parade_lspcon", "pickit2_spi",
            "pony_spi", "raiden_debug_spi", "realtek_mst_i2c_spi", "satasii", "serprog",
            "spidriver", "stlinkv3_spi", "usbblaster_spi"
    };

    private Spinner spinnerProgrammer;
    private Spinner spinnerSpiSpeed;
    private EditText etProgrammerParam;
    private Button btnSaveProgrammer;
    private View layoutSpiSpeed;
    private android.widget.RadioGroup rgSerprogTransport;
    private android.widget.RadioButton rbTransportSocket;
    private android.widget.RadioButton rbTransportPty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_programmer_settings);

        setTitle(R.string.str_configuracin_de);

        etProgrammerParam = findViewById(R.id.etProgrammerParam);
        btnSaveProgrammer = findViewById(R.id.btnSaveProgrammer);
        spinnerProgrammer = findViewById(R.id.spinnerProgrammer);
        spinnerSpiSpeed = findViewById(R.id.spinnerSpiSpeed);
        layoutSpiSpeed = findViewById(R.id.layoutSpiSpeed);
        rgSerprogTransport = findViewById(R.id.rgSerprogTransport);
        rbTransportSocket = findViewById(R.id.rbTransportSocket);
        rbTransportPty = findViewById(R.id.rbTransportPty);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String current = prefs.getString(KEY_PROGRAMMER, "ch341a_spi");
        etProgrammerParam.setText(current);

        String[] speedLabels = {
                getString(R.string.str_spi_speed_15),
                getString(R.string.str_spi_speed_30),
                getString(R.string.str_spi_speed_60)
        };
        ArrayAdapter<String> speedAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, speedLabels);
        speedAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerSpiSpeed.setAdapter(speedAdapter);

        String savedSpeed = SpiSpeed.normalize(prefs.getString(SpiSpeed.PREF_KEY, SpiSpeed.DEFAULT));
        for (int i = 0; i < SpiSpeed.OPTIONS.length; i++) {
            if (SpiSpeed.OPTIONS[i].equals(savedSpeed)) {
                spinnerSpiSpeed.setSelection(i, false);
                break;
            }
        }

        etProgrammerParam.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSpiSpeedVisibility();
            }
        });
        updateSpiSpeedVisibility();

        String currentTransport = prefs.getString(com.diamon.curso.core.UsbController.KEY_SERPROG_TRANSPORT,
                com.diamon.curso.core.UsbController.TRANSPORT_SOCKET);
        if (com.diamon.curso.core.UsbController.TRANSPORT_PTY.equals(currentTransport)) {
            rbTransportPty.setChecked(true);
        } else {
            rbTransportSocket.setChecked(true);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item,
                SUPPORTED_PROGRAMMERS);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerProgrammer.setAdapter(adapter);

        // Intentar pre-seleccionar el programador base que el usuario ya tenia guardado
        boolean found = false;
        for (int i = 0; i < SUPPORTED_PROGRAMMERS.length; i++) {
            if (current != null && current.startsWith(SUPPORTED_PROGRAMMERS[i])) {
                spinnerProgrammer.setSelection(i);
                found = true;
                break;
            }
        }
        if (!found) {
            for (int i = 0; i < SUPPORTED_PROGRAMMERS.length; i++) {
                if ("ch341a_spi".equals(SUPPORTED_PROGRAMMERS[i])) {
                    spinnerProgrammer.setSelection(i);
                    break;
                }
            }
        }

        spinnerProgrammer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
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
            String selectedTransport = rbTransportPty.isChecked()
                    ? com.diamon.curso.core.UsbController.TRANSPORT_PTY
                    : com.diamon.curso.core.UsbController.TRANSPORT_SOCKET;
            int speedPosition = spinnerSpiSpeed.getSelectedItemPosition();
            String selectedSpeed = (speedPosition >= 0 && speedPosition < SpiSpeed.OPTIONS.length)
                    ? SpiSpeed.OPTIONS[speedPosition]
                    : SpiSpeed.DEFAULT;

            prefs.edit()
                    .putString(KEY_PROGRAMMER, value)
                    .putString(SpiSpeed.PREF_KEY, selectedSpeed)
                    .putString(com.diamon.curso.core.UsbController.KEY_SERPROG_TRANSPORT, selectedTransport)
                    .apply();

            Toast.makeText(this, getString(R.string.str_saved, value), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void updateSpiSpeedVisibility() {
        if (layoutSpiSpeed == null || etProgrammerParam == null) {
            return;
        }
        boolean visible = SpiSpeed.supportsSpiSpeed(etProgrammerParam.getText().toString());
        layoutSpiSpeed.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
