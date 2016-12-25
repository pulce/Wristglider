package com.pulce.wristglider;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Spinner;

public class NDSpinner extends Spinner {

    public int lastActivePosition;

    public NDSpinner(Context context) {
        super(context);
    }

    public NDSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NDSpinner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void
    setSelection(int position, boolean animate) {
        boolean sameSelected = position == getSelectedItemPosition();
        super.setSelection(position, animate);
        if (sameSelected) {
            if (getOnItemSelectedListener() != null) {
                getOnItemSelectedListener().onItemSelected(this, getSelectedView(), position, getSelectedItemId());
            }
        }
    }

    @Override
    public void setSelection(int position) {

        boolean sameSelected = position == getSelectedItemPosition();
        super.setSelection(position);
        if (sameSelected) {
            if (getOnItemSelectedListener() != null) {
                getOnItemSelectedListener().onItemSelected(this, getSelectedView(), position, getSelectedItemId());
            }
        }
    }

    @Override
    public boolean performClick() {
        lastActivePosition = getSelectedItemPosition();
        if(this.getAdapter().getCount() < 2) {
            getOnItemSelectedListener().onItemSelected(this, getSelectedView(), 0, getSelectedItemId());
            return true;
        }
        else {
            return super.performClick();
        }
    }
}