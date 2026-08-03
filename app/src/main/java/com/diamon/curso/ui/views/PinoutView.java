package com.diamon.curso.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.ImageView;

/**
 * PinoutView: dibuja diagramas de pinout de hardware usando
 * Canvas nativo de Android.
 */
public class PinoutView {

    // Paleta de colores del tema oscuro de la app
    private static final int COL_BG = 0xFF1B1E2B;
    private static final int COL_PANEL = 0xFF252A3C;
    private static final int COL_BORDE = 0xFF546E7A;
    private static final int COL_LINEA = 0xFF78909C;
    private static final int COL_TITULO = 0xFFCFD8DC;
    private static final int COL_LABEL = 0xFFB0BEC5;
    private static final int COL_PIN_NUM = 0xFF80CBC4;
    private static final int COL_AVISO = 0xFFFF7043;
    private static final int COL_VCC = 0xFFEF5350;
    private static final int COL_GND = 0xFF78909C;
    private static final int COL_SPI = 0xFF66BB6A;
    private static final int COL_CHIP = 0xFF1565C0;

    private static final int W = 520;
    private static final int H = 380;
    
    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static void dibujarCH341A(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "CH341A Mini Programmer — Header SPI");
        dibujarChipSOIC8(canvas, 40, 90, new String[] { "CS", "MISO", "WP", "GND", "MOSI", "CLK", "HOLD", "VCC" }, true);
        dibujarPinHeader(canvas, 290, 90, new String[] { "CS", "MISO", "WP", "GND", "MOSI", "CLK", "HOLD", "VCC" });
        dibujarNota(canvas, "⚠ Jumper 1-2: SPI  ⚠ Jumper 2-3: UART/I2C  ⚠ SOLO 3.3V");
        aplicar(bmp, target);
    }

    public static void dibujarSOIC8(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "Chip Flash SOIC8 / DIP8 — Conexión a CH341A");
        dibujarChipSOIC8(canvas, 54, 90, new String[] { "CS", "DO", "WP", "GND", "DI", "CLK", "HOLD", "VCC" }, false);
        dibujarFlecha(canvas, 270, 190);
        dibujarTablaConexion(canvas, 310, 80,
                new String[] { "1-CS", "2-DO", "3-WP", "4-GND", "5-DI", "6-CLK", "7-HOLD", "8-VCC" },
                new String[] { "1-CS", "2-MISO", "3-WP(VCC)", "4-GND", "5-MOSI", "6-CLK", "7-HOLD(VCC)", "8-VCC" });
        dibujarNota(canvas, "⚠ HOLD y WP → VCC si no se usan  ⚠ 3.3V máximo");
        aplicar(bmp, target);
    }

    public static void dibujarSPI(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "Bus SPI — Serial Peripheral Interface");
        dibujarBusSPI(canvas);
        dibujarNota(canvas, "CPOL=0, CPHA=0 (Modo 0)  |  Velocidad típica: 1–50 MHz");
        aplicar(bmp, target);
    }

    public static void dibujarLPC(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "Bus LPC / FWH — (Chips BIOS)");
        dibujarBusLPC(canvas);
        dibujarNota(canvas, "flashrom soporta LPC/FWH de forma nativa (PLCC32)");
        aplicar(bmp, target);
    }

    public static void dibujarArduinoSerprog(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "Arduino UNO (serprog) — Conexión a Flash SPI");
        dibujarChipSOIC8(canvas, 54, 55, new String[] { "CS", "DO", "WP", "GND", "DI", "CLK", "HOLD", "VCC" }, false);
        dibujarFlecha(canvas, 270, 155);
        dibujarTablaConexionGeneral(canvas, 310, 50,
                "Flash Chip", "Arduino UNO",
                new String[] { "1-CS", "2-DO(MISO)", "3-WP", "4-GND", "5-DI(MOSI)", "6-CLK", "7-HOLD", "8-VCC" },
                new String[] { "Pin 10 (SS)", "Pin 12 (MISO)", "3.3V", "GND", "Pin 11 (MOSI)", "Pin 13 (SCK)", "3.3V",
                        "3.3V" });
        dibujarNota(canvas, "⚠ Arduino UNO es 5V — usar level shifter (HEF4050) a 3.3V");
        aplicar(bmp, target);
    }

    public static void dibujarBusPirate(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "Bus Pirate — Conexión a Flash SPI");
        dibujarChipSOIC8(canvas, 54, 55, new String[] { "CS", "DO", "WP", "GND", "DI", "CLK", "HOLD", "VCC" }, false);
        dibujarFlecha(canvas, 270, 155);
        dibujarTablaConexionGeneral(canvas, 310, 50,
                "Flash Chip", "Bus Pirate",
                new String[] { "1-CS", "2-DO(MISO)", "3-WP", "4-GND", "5-DI(MOSI)", "6-CLK", "7-HOLD", "8-VCC" },
                new String[] { "CS", "MISO", "3.3V", "GND", "MOSI", "CLK", "3.3V", "3.3V (Vout)" });
        dibujarNota(canvas, "⚠ Bus Pirate v3: max ~8MHz SPI  ⚠ Alimentar chip desde Vout");
        aplicar(bmp, target);
    }

    public static void dibujarSPIDriver(Context ctx, ImageView target) {
        Bitmap bmp = crearBitmap();
        Canvas canvas = new Canvas(bmp);
        dibujarHeaderPinout(canvas, "SPIDriver — Conexión a Flash SPI");
        dibujarChipSOIC8(canvas, 54, 55, new String[] { "CS", "DO", "WP", "GND", "DI", "CLK", "HOLD", "VCC" }, false);
        dibujarFlecha(canvas, 270, 155);
        dibujarTablaConexionGeneral(canvas, 310, 50,
                "Flash Chip", "SPIDriver",
                new String[] { "1-CS", "2-DO(MISO)", "3-WP", "4-GND", "5-DI(MOSI)", "6-CLK", "7-HOLD", "8-VCC" },
                new String[] { "CS (A)", "MISO", "3.3V", "GND", "MOSI", "SCK", "3.3V", "3.3V" });
        dibujarNota(canvas, "⚠ SPIDriver opera a 3.3V nativo — no requiere level shifter");
        aplicar(bmp, target);
    }

    // ────────── Helpers de dibujo ────────────────────────────────────────────

    private static Bitmap crearBitmap() {
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    }

    private static void dibujarHeaderPinout(Canvas g, String titulo) {
        g.drawColor(COL_BG);
        dibujarRectangulo(g, 0, 0, W, 32, 0xFF1A237E);
        configurarTexto(15f, true);
        dibujarTexto(g, titulo, 12, 22, COL_TITULO);
        dibujarLinea(g, 0, 32, W, 32, COL_BORDE);
    }

    private static void dibujarNota(Canvas g, String nota) {
        dibujarRectangulo(g, 0, H - 26, W, 26, 0xFF1A1C27);
        dibujarLinea(g, 0, H - 26, W, H - 26, COL_AVISO);
        configurarTexto(11f, false);
        dibujarTexto(g, nota, 8, H - 9, COL_AVISO);
    }

    private static void dibujarChipSOIC8(Canvas g, float x, float y,
            String[] pines, boolean conPunto) {
        float CW = 100, CH = 170;
        float pinW = 22, pinH = 16, gap = (CH - 4 * pinH) / 5f;

        dibujarRectangulo(g, x, y, CW, CH, COL_CHIP);
        bordes(g, x, y, CW, CH, COL_BORDE);

        dibujarLinea(g, x + CW / 2 - 14, y, x + CW / 2 + 14, y, 0xFF90CAF9);

        if (conPunto) {
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            g.drawRect(x + 6, y + 10, x + 10, y + 14, paint);
        }

        configurarTexto(11f, true);
        dibujarTexto(g, "SOIC8", x + 24, y + CH / 2 + 5, 0xFF90CAF9);

        for (int i = 0; i < 4; i++) {
            float py = y + gap + i * (pinH + gap);

            dibujarRectangulo(g, x - pinW, py, pinW - 2, pinH - 2, COL_PANEL);
            dibujarLinea(g, x - pinW, py, x, py, COL_LINEA);
            dibujarLinea(g, x - pinW, py + pinH, x, py + pinH, COL_LINEA);
            dibujarLinea(g, x - pinW, py, x - pinW, py + pinH, COL_LINEA);
            configurarTexto(10f, false);
            dibujarTexto(g, String.valueOf(i + 1), x - pinW + 2, py + pinH - 3, COL_PIN_NUM);
            configurarTexto(9f, false);
            int col = colorPin(pines[i]);
            dibujarTexto(g, pines[i], x - pinW - 38, py + pinH - 3, col);

            int ri = 7 - i;
            float prx = x + CW + 2;
            dibujarRectangulo(g, prx, py, pinW - 2, pinH - 2, COL_PANEL);
            dibujarLinea(g, prx, py, prx + pinW, py, COL_LINEA);
            dibujarLinea(g, prx, py + pinH, prx + pinW, py + pinH, COL_LINEA);
            dibujarLinea(g, prx + pinW, py, prx + pinW, py + pinH, COL_LINEA);
            configurarTexto(10f, false);
            dibujarTexto(g, String.valueOf(ri + 1), prx + 2, py + pinH - 3, COL_PIN_NUM);
            configurarTexto(9f, false);
            int colR = colorPin(pines[ri]);
            dibujarTexto(g, pines[ri], prx + pinW + 4, py + pinH - 3, colR);
        }
    }

    private static void dibujarPinHeader(Canvas g, float x, float y, String[] etiquetas) {
        float pH = 20, pW = 14, espacio = 4;
        configurarTexto(11f, true);
        dibujarTexto(g, "Conector SPI", x, y - 6, COL_LABEL);
        for (int i = 0; i < 8; i++) {
            float py = y + i * (pH + espacio);
            dibujarRectangulo(g, x, py, pW, pH, COL_PANEL);
            bordes(g, x, py, pW, pH, COL_BORDE);
            configurarTexto(9f, false);
            dibujarTexto(g, String.valueOf(i + 1), x + 3, py + pH - 4, COL_PIN_NUM);
            int col = colorPin(etiquetas[i]);
            dibujarTexto(g, etiquetas[i], x + pW + 6, py + pH - 4, col);
        }
    }

    private static void dibujarBusSPI(Canvas g) {
        float mx = 50, sy = 70, bW = 140, bH = 160;

        dibujarRectangulo(g, mx, sy, bW, bH, COL_CHIP);
        bordes(g, mx, sy, bW, bH, COL_BORDE);
        configurarTexto(12f, true);
        dibujarTexto(g, "MASTER", mx + 30, sy + 20, COL_TITULO);
        configurarTexto(10f, false);
        dibujarTexto(g, "CH341A", mx + 35, sy + 35, COL_LABEL);

        float sx = mx + bW + 120;
        dibujarRectangulo(g, sx, sy, bW, bH, 0xFF1B5E20);
        bordes(g, sx, sy, bW, bH, COL_BORDE);
        configurarTexto(12f, true);
        dibujarTexto(g, "SLAVE", sx + 35, sy + 20, COL_TITULO);
        configurarTexto(10f, false);
        dibujarTexto(g, "Flash Chip", sx + 28, sy + 35, COL_LABEL);

        String[] sigs = { "MOSI", "MISO", "CLK", "CS" };
        boolean[] esEntrada = { true, false, true, true };
        int[] cols = { COL_SPI, 0xFF29B6F6, 0xFFFFCA28, 0xFFCE93D8 };
        float yBase = sy + 60;
        float lineX1 = mx + bW, lineX2 = sx;
        float midX = (lineX1 + lineX2) / 2f;

        configurarTexto(10f, false);
        for (int i = 0; i < 4; i++) {
            float fy = yBase + i * 26;
            dibujarLinea(g, lineX1, fy, lineX2, fy, cols[i]);
            if (esEntrada[i]) {
                flecha(g, midX - 6, fy, midX + 8, fy, cols[i]);
            } else {
                flecha(g, midX + 6, fy, midX - 8, fy, cols[i]);
            }
            dibujarTexto(g, sigs[i], midX - 14, fy - 4, cols[i]);
            dibujarTexto(g, sigs[i], mx + bW - 38, fy + 4, cols[i]);
            String slaveLabel = esEntrada[i] ? "→" + sigs[i] : sigs[i] + "→";
            dibujarTexto(g, slaveLabel, sx + 4, fy + 4, cols[i]);
        }

        configurarTexto(10f, false);
        dibujarTexto(g, "MOSI = Master Out Slave In", 20, sy + bH + 22, COL_LABEL);
        dibujarTexto(g, "MISO = Master In Slave Out", 20, sy + bH + 36, COL_LABEL);
        dibujarTexto(g, "CLK  = Reloj (genera Master)", 20, sy + bH + 50, COL_LABEL);
        dibujarTexto(g, "CS   = Chip Select (bajo = activo)", 280, sy + bH + 22, COL_LABEL);
    }

    private static void dibujarBusLPC(Canvas g) {
        float mx = 50, sy = 70, bW = 140, bH = 160;

        dibujarRectangulo(g, mx, sy, bW, bH, COL_CHIP);
        bordes(g, mx, sy, bW, bH, COL_BORDE);
        configurarTexto(12f, true);
        dibujarTexto(g, "HOST / CPU", mx + 20, sy + 20, COL_TITULO);
        configurarTexto(10f, false);
        dibujarTexto(g, "(Southbridge)", mx + 15, sy + 35, COL_LABEL);

        float sx = mx + bW + 120;
        dibujarRectangulo(g, sx, sy, bW, bH, 0xFF1B5E20);
        bordes(g, sx, sy, bW, bH, COL_BORDE);
        configurarTexto(12f, true);
        dibujarTexto(g, "BIOS CHIP", sx + 25, sy + 20, COL_TITULO);
        configurarTexto(10f, false);
        dibujarTexto(g, "(LPC / FWH)", sx + 20, sy + 35, COL_LABEL);

        String[] sigs = { "LAD0-LAD3", "LFRAME#", "LCLK", "LRESET#" };
        int[] cols = { 0xFF29B6F6, 0xFFCE93D8, 0xFFFFCA28, COL_AVISO };
        float yBase = sy + 60;
        float lineX1 = mx + bW, lineX2 = sx;
        float midX = (lineX1 + lineX2) / 2f;

        configurarTexto(10f, false);
        for (int i = 0; i < 4; i++) {
            float fy = yBase + i * 26;
            dibujarLinea(g, lineX1, fy, lineX2, fy, cols[i]);
            if (i == 0) {
                flecha(g, midX - 6, fy, midX + 8, fy, cols[i]);
                flecha(g, midX + 6, fy, midX - 8, fy, cols[i]);
            } else {
                flecha(g, midX - 6, fy, midX + 8, fy, cols[i]);
            }
            dibujarTexto(g, sigs[i], midX - 25, fy - 4, cols[i]);
            dibujarTexto(g, sigs[i], mx + bW - 45, fy + 4, cols[i]);
            dibujarTexto(g, sigs[i], sx + 4, fy + 4, cols[i]);
        }

        configurarTexto(10f, false);
        dibujarTexto(g, "LAD0-3 = Bus multiplexado Datos/Dir (4 bits)", 20, sy + bH + 22, COL_LABEL);
        dibujarTexto(g, "LFRAME# = Inicio de ciclo LPC (activo en bajo)", 20, sy + bH + 36, COL_LABEL);
        dibujarTexto(g, "LCLK    = Reloj típicamente 33 MHz", 20, sy + bH + 50, COL_LABEL);
        dibujarTexto(g, "FWH usa un protocolo físico muy similar a LPC", 20, sy + bH + 64, 0xFF90CAF9);
    }

    private static void dibujarTablaConexion(Canvas g, float x, float y,
            String[] col1, String[] col2) {
        float rowH = 22, col1W = 80, col2W = 100;
        configurarTexto(11f, true);
        dibujarTexto(g, "Flash Chip → CH341A", x, y - 6, COL_LABEL);
        dibujarRectangulo(g, x, y, col1W + col2W, rowH, 0xFF1A237E);
        dibujarTexto(g, "Pin chip", x + 4, y + 15, COL_TITULO);
        dibujarTexto(g, "CH341A", x + col1W + 4, y + 15, COL_TITULO);
        for (int i = 0; i < col1.length; i++) {
            float ry = y + (i + 1) * rowH;
            int bg = (i % 2 == 0) ? COL_PANEL : COL_BG;
            dibujarRectangulo(g, x, ry, col1W + col2W, rowH, bg);
            configurarTexto(10f, false);
            dibujarTexto(g, col1[i], x + 4, ry + 15, COL_PIN_NUM);
            dibujarTexto(g, col2[i], x + col1W + 4, ry + 15, colorPin(col2[i]));
        }
    }

    private static void dibujarTablaConexionGeneral(Canvas g, float x, float y,
            String headerLeft, String headerRight,
            String[] col1, String[] col2) {
        float rowH = 22, col1W = 80, col2W = 110;
        configurarTexto(11f, true);
        dibujarTexto(g, headerLeft + " → " + headerRight, x, y - 6, COL_LABEL);
        dibujarRectangulo(g, x, y, col1W + col2W, rowH, 0xFF1A237E);
        dibujarTexto(g, "Pin chip", x + 4, y + 15, COL_TITULO);
        dibujarTexto(g, headerRight, x + col1W + 4, y + 15, COL_TITULO);
        for (int i = 0; i < col1.length; i++) {
            float ry = y + (i + 1) * rowH;
            int bg = (i % 2 == 0) ? COL_PANEL : COL_BG;
            dibujarRectangulo(g, x, ry, col1W + col2W, rowH, bg);
            configurarTexto(10f, false);
            dibujarTexto(g, col1[i], x + 4, ry + 15, COL_PIN_NUM);
            dibujarTexto(g, col2[i], x + col1W + 4, ry + 15, colorPin(col2[i]));
        }
    }

    private static void dibujarFlecha(Canvas g, float x, float y) {
        dibujarLinea(g, x, y - 16, x + 28, y, 0xFF90A4AE);
        dibujarLinea(g, x, y + 16, x + 28, y, 0xFF90A4AE);
        dibujarLinea(g, x + 28, y, x + 28, y, 0xFF90A4AE);
    }

    // ────────── Utilidades ───────────────────────────────────────────────────

    private static void configurarTexto(float size, boolean negrita) {
        paint.setTextSize(size * 1.5f); // Ajuste proporcional
        paint.setTypeface(negrita ? Typeface.DEFAULT_BOLD : Typeface.MONOSPACE);
    }

    private static void dibujarTexto(Canvas c, String text, float x, float y, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawText(text, x, y, paint);
    }

    private static void dibujarRectangulo(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(x, y, x + w, y + h, paint);
    }

    private static void dibujarLinea(Canvas c, float x1, float y1, float x2, float y2, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        c.drawLine(x1, y1, x2, y2, paint);
    }

    private static void bordes(Canvas g, float x, float y, float w, float h, int color) {
        dibujarLinea(g, x, y, x + w, y, color);
        dibujarLinea(g, x + w, y, x + w, y + h, color);
        dibujarLinea(g, x, y + h, x + w, y + h, color);
        dibujarLinea(g, x, y, x, y + h, color);
    }

    private static void flecha(Canvas g, float x1, float y, float x2, float y2, int color) {
        dibujarLinea(g, x1, y, x2, y2, color);
        float dx = x2 - x1;
        float sign = dx > 0 ? 1f : -1f;
        dibujarLinea(g, x2, y2, x2 - sign * 7, y2 - 5, color);
        dibujarLinea(g, x2, y2, x2 - sign * 7, y2 + 5, color);
    }

    private static int colorPin(String pin) {
        if (pin == null)
            return COL_LABEL;
        String p = pin.toUpperCase();
        if (p.contains("VCC") || p.contains("3.3"))
            return COL_VCC;
        if (p.contains("GND"))
            return COL_GND;
        if (p.contains("MOSI") || p.contains("DI"))
            return COL_SPI;
        if (p.contains("MISO") || p.contains("DO"))
            return 0xFF29B6F6;
        if (p.contains("CLK") || p.contains("SCK"))
            return 0xFFFFCA28;
        if (p.contains("CS"))
            return 0xFFCE93D8;
        if (p.contains("WP"))
            return COL_AVISO;
        if (p.contains("HOLD"))
            return 0xFF90A4AE;
        if (p.contains("SDA"))
            return 0xFF29B6F6;
        if (p.contains("SCL"))
            return 0xFFFFCA28;
        return COL_LABEL;
    }

    private static void aplicar(Bitmap bmp, ImageView target) {
        target.setImageBitmap(bmp);
        target.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }
}
