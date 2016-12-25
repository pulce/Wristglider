package com.pulce.commonclasses;

/**
 * Created by pulce on 02.10.16.
 */

public class PreferenceSet {
    private String pilotname;
    private String glidertype;
    private String gliderid;
    private boolean autologger;

    public PreferenceSet(String pilotname, String glidertype, String gliderid, boolean autologger) {
        this.pilotname = pilotname;
        this.glidertype = glidertype;
        this.gliderid = gliderid;
        this.autologger = autologger;
    }

    public String getPilotname() {
        return pilotname;
    }

    public String getGlidertype() {
        return glidertype;
    }

    public String getGliderid() {
        return gliderid;
    }

    public boolean isAutologger() {
        return autologger;
    }
}
