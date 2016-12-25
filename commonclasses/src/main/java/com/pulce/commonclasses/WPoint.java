package com.pulce.commonclasses;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by pulce on 28.09.16.
 */

public class WPoint {
    private double lon;
    private double lat;
    private double hei;
    private long date;
    //public Date dt;
    SimpleDateFormat ft = new SimpleDateFormat ("HHmmss");

    public WPoint(double lon, double lat, double hei, long date) {
        this.lon = lon;
        this.lat = lat;
        this.hei = hei;
        this.date = date;
    }

    /*private double lonToDec(String lon) {
        int deg = Integer.parseInt(lon.substring(0, lon.length()-5));
        int sec = Integer.parseInt(lon.substring(lon.length()-5, lon.length()));
        return (double) sec/60000 + deg;
    }*/

    public double decToIgcFormat(double dec) {
        double igc = Math.floor(dec);
        double minutes = (dec-igc)*0.6;
        return igc += minutes;
    }

    public String toString() {
        return new Date(date).toString() + " " + lat + " " + lon + " " + hei;
    }
}