package com.pulce.wristglider;

import android.content.Context;

import java.io.*;
import java.util.StringTokenizer;

final class EarthGravitationalModel {
    private static final double SQRT_03 = 1.7320508075688772935274463415059,
            SQRT_05 = 2.2360679774997896964091736687313,
            SQRT_13 = 3.6055512754639892931192212674705,
            SQRT_17 = 4.1231056256176605498214098559741,
            SQRT_21 = 4.5825756949558400065880471937280;
    private static final int DEFAULT_ORDER = 180;
    private final double semiMajor;
    private final double esq;
    private final double c2;
    private final double rkm;
    private final double grava;
    private final double star;
    private final double[] cnmGeopCoef, snmGeopCoef;
    private final double[] aClenshaw, bClenshaw, as;
    private final double[] cr, sr, s11, s12;

    private static boolean initialized = false;

    EarthGravitationalModel() {
        semiMajor = 6378137.0;
        esq = 0.00669437999013;
        c2 = 108262.9989050e-8;
        rkm = 3.986004418e+14;
        grava = 9.7803267714;
        star = 0.001931851386;
        final int cleanshawLength = locatingArray(DEFAULT_ORDER + 3);
        final int geopCoefLength = locatingArray(DEFAULT_ORDER + 1);
        aClenshaw = new double[cleanshawLength];
        bClenshaw = new double[cleanshawLength];
        cnmGeopCoef = new double[geopCoefLength];
        snmGeopCoef = new double[geopCoefLength];
        as = new double[DEFAULT_ORDER + 1];
        cr = new double[DEFAULT_ORDER + 1];
        sr = new double[DEFAULT_ORDER + 1];
        s11 = new double[DEFAULT_ORDER + 3];
        s12 = new double[DEFAULT_ORDER + 3];
    }

    private static int locatingArray(final int n) {
        return ((n + 1) * n) >> 1;
    }

    void load(Context context) throws IOException {
        LineNumberReader in = null;
        try {
            final InputStream stream = context.getAssets().open("egm180.nor");
            in = new LineNumberReader(new InputStreamReader(stream, "ISO-8859-1"));
            String line;
            while ((line = in.readLine()) != null) {
                final StringTokenizer tokens = new StringTokenizer(line);
                final int n = Short.parseShort(tokens.nextToken());
                final int m = Short.parseShort(tokens.nextToken());
                final double cbar = Double.parseDouble(tokens.nextToken());
                final double sbar = Double.parseDouble(tokens.nextToken());
                if (n <= DEFAULT_ORDER) {
                    final int ll = locatingArray(n) + m;
                    cnmGeopCoef[ll] = cbar;
                    snmGeopCoef[ll] = sbar;
                }
            }
        } finally {
            if (in != null) in.close();
        }
        initialize();
    }

    private void initialize() {
        initialized = true;
        final double[] c2n = new double[6];
        c2n[1] = c2;
        int sign = 1;
        double esqi = esq;
        for (int i = 2; i < c2n.length; i++) {
            sign *= -1;
            esqi *= esq;
            c2n[i] = sign * (3 * esqi) / ((2 * i + 1) * (2 * i + 3)) * (1 - i + (5 * i * c2 / esq));
        }
        cnmGeopCoef[3] += c2n[1] / SQRT_05;
        cnmGeopCoef[10] += c2n[2] / 3;
        cnmGeopCoef[21] += c2n[3] / SQRT_13;
        if (DEFAULT_ORDER > 6) cnmGeopCoef[36] += c2n[4] / SQRT_17;
        if (DEFAULT_ORDER > 9) cnmGeopCoef[55] += c2n[5] / SQRT_21;
        for (int i = 0; i <= DEFAULT_ORDER; i++) {
            as[i] = -Math.sqrt(1.0 + 1.0 / (2 * (i + 1)));
        }
        for (int i = 0; i <= DEFAULT_ORDER; i++) {
            for (int j = i + 1; j <= DEFAULT_ORDER; j++) {
                final int ll = locatingArray(j) + i;
                final int n = 2 * j + 1;
                final int ji = (j - i) * (j + i);
                aClenshaw[ll] = Math.sqrt(n * (2 * j - 1) / (double) (ji));
                bClenshaw[ll] = Math.sqrt(n * (j + i - 1) * (j - i - 1) / (double) (ji * (2 * j - 3)));
            }
        }
    }

    double heightOffset(final double longitude, final double latitude, final double height) {
        if (!initialized) return 0;
        final double phi = Math.toRadians(latitude);
        final double sin_phi = Math.sin(phi);
        final double sin2_phi = sin_phi * sin_phi;
        final double rni = Math.sqrt(1.0 - esq * sin2_phi);
        final double rn = semiMajor / rni;
        final double t22 = (rn + height) * Math.cos(phi);
        final double x2y2 = t22 * t22;
        final double z1 = ((rn * (1 - esq)) + height) * sin_phi;
        final double th = (Math.PI / 2.0) - Math.atan(z1 / Math.sqrt(x2y2));
        final double y = Math.sin(th);
        final double t = Math.cos(th);
        final double f1 = semiMajor / Math.sqrt(x2y2 + z1 * z1);
        final double f2 = f1 * f1;
        final double rlam = Math.toRadians(longitude);
        final double gravn;
        gravn = grava * (1.0 + star * sin2_phi) / rni;
        sr[0] = 0;
        sr[1] = Math.sin(rlam);
        cr[0] = 1;
        cr[1] = Math.cos(rlam);
        for (int j = 2; j <= DEFAULT_ORDER; j++) {
            sr[j] = (2.0 * cr[1] * sr[j - 1]) - sr[j - 2];
            cr[j] = (2.0 * cr[1] * cr[j - 1]) - cr[j - 2];
        }
        double sht = 0, previousSht = 0;
        for (int i = DEFAULT_ORDER; i >= 0; i--) {
            for (int j = DEFAULT_ORDER; j >= i; j--) {
                final int ll = locatingArray(j) + i;
                final int ll2 = ll + j + 1;
                final int ll3 = ll2 + j + 2;
                final double ta = aClenshaw[ll2] * f1 * t;
                final double tb = bClenshaw[ll3] * f2;
                s11[j] = (ta * s11[j + 1]) - (tb * s11[j + 2]) + cnmGeopCoef[ll];
                s12[j] = (ta * s12[j + 1]) - (tb * s12[j + 2]) + snmGeopCoef[ll];
            }
            previousSht = sht;
            sht = (-as[i] * y * f1 * sht) + (s11[i] * cr[i]) + (s12[i] * sr[i]);
        }
        return ((s11[0] + s12[0]) * f1 + (previousSht * SQRT_03 * y * f2)) * rkm /
                (semiMajor * (gravn - (height * 0.3086e-5)));
    }
}

