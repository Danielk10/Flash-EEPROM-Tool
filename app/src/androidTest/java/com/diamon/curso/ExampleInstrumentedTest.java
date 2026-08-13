package com.diamon.curso;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.diamon.curso.ui.activities.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    private void setProgrammer(MainActivity activity, String programmer) throws Exception {
        Field field = MainActivity.class.getDeclaredField("selectedProgrammer");
        field.setAccessible(true);
        field.set(activity, programmer);
    }

    private void waitForFlashrom(MainActivity activity) throws Exception {
        Field field = MainActivity.class.getDeclaredField("flashromExecutor");
        field.setAccessible(true);
        com.diamon.curso.core.FlashromExecutor executor = (com.diamon.curso.core.FlashromExecutor) field.get(activity);
        
        long start = System.currentTimeMillis();
        while (executor.isRunning()) {
            Thread.sleep(100);
            if (System.currentTimeMillis() - start > 20000) { // 20s timeout
                throw new RuntimeException("Flashrom execution timed out!");
            }
        }
        // Espera pequeña adicional para que los logs en la UI se actualicen por completo
        Thread.sleep(500);
    }

    @Test
    public void testFullFlowWithDummyProgrammer() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            
            // 1. Configurar el programador dummy vía reflexión
            scenario.onActivity(activity -> {
                try {
                    setProgrammer(activity, "dummy");
                } catch (Exception e) {
                    fail("Fallo al establecer programador dummy: " + e.getMessage());
                }
            });

            // 2. Ejecutar Detección (btnProbe)
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btnProbe).performClick();
            });
            
            scenario.onActivity(activity -> {
                try {
                    waitForFlashrom(activity);
                } catch (Exception e) {
                    fail("Fallo esperando detección: " + e.getMessage());
                }
                
                TextView tvLog = activity.findViewById(R.id.tvLog);
                String logText = tvLog.getText().toString();
                assertTrue("El log debe contener el programador dummy", logText.contains("dummy") || logText.contains("Found"));
            });

            // 3. Ejecutar Lectura (btnRead)
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btnRead).performClick();
            });

            scenario.onActivity(activity -> {
                try {
                    waitForFlashrom(activity);
                } catch (Exception e) {
                    fail("Fallo esperando lectura: " + e.getMessage());
                }

                TextView tvLog = activity.findViewById(R.id.tvLog);
                String logText = tvLog.getText().toString();
                assertTrue("El log debe indicar lectura exitosa", logText.contains("done") || logText.contains("Reading"));
            });

            // 4. Ejecutar Verificación (btnVerify)
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btnVerify).performClick();
            });

            scenario.onActivity(activity -> {
                try {
                    waitForFlashrom(activity);
                } catch (Exception e) {
                    fail("Fallo esperando verificación: " + e.getMessage());
                }

                TextView tvLog = activity.findViewById(R.id.tvLog);
                String logText = tvLog.getText().toString();
                assertTrue("El log debe indicar verificación correcta", logText.contains("VERIFIED") || logText.contains("done"));
            });

            // 5. Ejecutar Comando Personalizado Completo en Consola
            scenario.onActivity(activity -> {
                EditText etCustom = activity.findViewById(R.id.etCustomCommand);
                etCustom.setText("flashrom --version");
                activity.findViewById(R.id.btnRunCustomCommand).performClick();
            });

            scenario.onActivity(activity -> {
                try {
                    waitForFlashrom(activity);
                } catch (Exception e) {
                    fail("Fallo esperando comando personalizado: " + e.getMessage());
                }

                TextView tvLog = activity.findViewById(R.id.tvLog);
                String logText = tvLog.getText().toString();
                assertTrue("El log debe mostrar la versión de flashrom", logText.contains("flashrom v"));
            });

            // 6. Ejecutar Comando Personalizado SIN prefijo 'flashrom' para verificar el error
            scenario.onActivity(activity -> {
                EditText etCustom = activity.findViewById(R.id.etCustomCommand);
                etCustom.setText("-p dummy -r read_test.bin");
                activity.findViewById(R.id.btnRunCustomCommand).performClick();
            });

            scenario.onActivity(activity -> {
                TextView tvLog = activity.findViewById(R.id.tvLog);
                String logText = tvLog.getText().toString();
                String expectedError = activity.getString(R.string.str_err_missing_flashrom_prefix);
                assertTrue("El log debe mostrar el error de prefijo faltante", logText.contains(expectedError));
            });
        }
    }
}