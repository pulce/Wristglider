package com.pulce.commonclasses;

import android.content.SharedPreferences;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class Statics {
    public static final int MY_PERMISSION_WRITE_STORAGE = 42;
    public static final int MY_PERMISSION_FINE_LOCATION = 43;

    public static final int MY_REQUEST_ENABLE_BT = 1;

    public static final int MY_BT_FAILED_NO_BT = 1;
    public static final int MY_BT_FAILED_NO_DEVICE = 2;
    public static final int MY_BT_FAILED_USER = 3;

    public static final int MY_BT_MESSAGE_READ = 0;
    public static final int MY_BT_MESSAGE_WRITE = 1;

    public static final int MY_NULL_VALUE = -999999;

    public static final float MY_LPF_ALPHA = 0.25f;

    public static final double KF_PRESSURE_VAR_ACCEL = 0.0075;  // Variance of pressure acceleration noise input.
    public static final double KF_PRESSURE_VAR_MEASUREMENT = 0.05;  // Variance of pressure measurement noise.
    public static final double KF_ALT_VAR_ACCEL = 0.025;  // Variance of altitude acceleration noise input.
    public static final double KF_ALT_VAR_MEASUREMENT = 0.001;  // Variance of altitude measurement noise.

    public static final int MY_BEEP_DURATION = 200;
    public static final int MY_BEEP_DURATION_COEF = 70;
    public static final int MY_UP_MAX_VARIO_THRESHOLD = 10;
    public static final int MY_INITIAL_FREQ_UP = 1200;
    public static final int MY_FREQ_COEF = 100;
    public static final int MY_FREQ_DOWN = 800;

    private static final DecimalFormat latForm = new DecimalFormat("0000000");
    private static final DecimalFormat lonForm = new DecimalFormat("00000000");

    public static final String DATAPREFERENCES = "/com/pulce/wristglider/datapreferences";
    public static final String DATAIGC = "/com/pulce/wristglider/dataigc";
    public static final String DATADELETE = "/com/pulce/wristglider/datadelete";
    public static final String DATATHROWABLE = "/com/pulce/wristglider/datathrowable";
    public static final String DATABTFAILED = "/com/pulce/wristglider/databtfailed";

    public static final String PREFPILOTNAME = "/com/pulce/wristglider/prefpilotname";
    public static final String PREFGLIDERTYPE = "/com/pulce/wristglider/glidertype";
    public static final String PREFGLIDERID = "/com/pulce/wristglider/gliderid";
    public static final String PREFGLIDERARRAY = "/com/pulce/wristglider/gliderarray";
    public static final String PREFLOGGERAUTO = "/com/pulce/wristglider/prefloggerauto";
    public static final String PREFLOGGERSECONDS = "/com/pulce/wristglider/prefloggerseconds";
    public static final String PREFROTATEVIEW = "/com/pulce/wristglider/prefrotateview";
    public static final String PREFROTATEDEGREES = "/com/pulce/wristglider/prefrotatedegrees";
    public static final String PREFSCREENON = "/com/pulce/wristglider/prefscreenon";
    public static final String PREFHEIGTHUNIT = "/com/pulce/wristglider/prefheightunit";
    public static final String PREFSPEEDUNIT = "/com/pulce/wristglider/prefspeedunit";
    public static final String PREFVARIOUNIT = "/com/pulce/wristglider/prefvariounit";
    public static final String PREFUSEBTVARIO = "/com/pulce/wristglider/prefusebtvario";
    public static final String PREFBTVARIODEVICE = "/com/pulce/wristglider/prefbtvariodevice";
    public static final String PREFVARIOBEEPER = "/com/pulce/wristglider/prefvariobeeper";
    public static final String PREFVARIOBEEPERDOWN = "/com/pulce/wristglider/prefvariobeeperdown";
    public static final String PREFVARIOBEEPERUP = "/com/pulce/wristglider/prefvariobeeperup";

    public static String getUTCdateReverse() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static String getUTCdateAsString() {
        final SimpleDateFormat sdf = new SimpleDateFormat("ddMMyy");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static String getUTCtimeAsString(long time) {
        final SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(time));
    }

    public static String decToIgcFormat(double dec, boolean latitude) {
        String mark = "";
        if (latitude) {
            if (dec > 0) {
                mark = "N";
            } else {
                mark = "S";
            }
        } else {
            if (dec > 0) {
                mark = "E";
            } else {
                mark = "W";
            }
        }
        dec = Math.abs(dec);
        double igc = Math.floor(dec);
        double minutes = (dec - igc) * 0.6;
        if (latitude) {
            return latForm.format((igc + minutes) * 100000) + mark;
        } else {
            return lonForm.format((igc + minutes) * 100000) + mark;
        }
    }
}