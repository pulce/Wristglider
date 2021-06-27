package com.pulce.wristglider;

import com.karlotoy.perfectune.instance.PerfectTune;
import com.pulce.commonclasses.Statics;

public class Beeper {

    private PerfectTune tone;
    private long lastTime;
    private static int BeepDuration = Statics.MY_BEEP_DURATION;
    private static int BeepDurationCoef = Statics.MY_BEEP_DURATION_COEF;
    private static int UpMaxVarioThreshold = Statics.MY_UP_MAX_VARIO_THRESHOLD;
    private static int InitialFreqUp = Statics.MY_INITIAL_FREQ_UP;
    private static int FreqCoef = Statics.MY_FREQ_COEF;
    private static int FreqDown = Statics.MY_FREQ_DOWN;
    private boolean playFlag = true;

    private float upVarioThreshold = 0;
    private float downVarioThreshold = -2;

    Beeper(float varioThresholdUp, float varioThresholdDown) {
        tone = new PerfectTune();
        lastTime = System.currentTimeMillis();
        setThresholds(varioThresholdUp, varioThresholdDown);
    }

    public void setThresholds(float varioThresholdUp, float varioThresholdDown) {
        upVarioThreshold = varioThresholdUp;
        downVarioThreshold = varioThresholdDown;
    }

    public void beep(float vario) {
        float duration = (BeepDuration - vario * BeepDurationCoef >= 25) ? BeepDuration - vario * BeepDurationCoef : 25;
        if (System.currentTimeMillis() - lastTime > duration && !playFlag) {
            playFlag = !playFlag;
            tone.stopTune();
        }
        if (vario > UpMaxVarioThreshold) vario = UpMaxVarioThreshold; // cut-off to max UpMaxVarioThreshold (default 10m/s)

        if (vario > upVarioThreshold) {
            duration = duration * 3;
            if (playFlag && ((System.currentTimeMillis() - lastTime) > duration)) {
                lastTime = System.currentTimeMillis();
                tone.setTuneFreq(InitialFreqUp + vario * FreqCoef);
                tone.playTune();
                playFlag = !playFlag;
            }
        } else if (vario < downVarioThreshold) {
            tone.setTuneFreq(FreqDown);
            tone.playTune();
        } else {
            tone.stopTune();
        }
    }

    public void stop() {
        tone.stopTune();
    }
}
