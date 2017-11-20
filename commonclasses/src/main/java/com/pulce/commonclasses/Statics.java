package com.pulce.commonclasses;

import android.content.SharedPreferences;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class Statics {
    public static final int MY_PERMISSION_WRITE_STORAGE = 42;
    public static final int MY_PERMISSION_FINE_LOCATION = 43;

    private static final DecimalFormat latForm = new DecimalFormat("0000000");
    private static final DecimalFormat lonForm = new DecimalFormat("00000000");

    public static final String DATAPREFERENCES = "/com/pulce/wristglider/datapreferences";
    public static final String DATAIGC = "/com/pulce/wristglider/dataigc";
    public static final String DATADELETE = "/com/pulce/wristglider/datadelete";
    public static final String DATATHROWABLE = "/com/pulce/wristglider/datathrowable";

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