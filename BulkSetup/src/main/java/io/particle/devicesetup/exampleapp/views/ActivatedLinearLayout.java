package io.particle.devicesetup.exampleapp.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

import io.particle.devicesetup.exampleapp.R;

/**
 * Created by jwhit on 08/03/2016.
 */
public class ActivatedLinearLayout extends LinearLayout implements Checkable {

    public static final int[] CHECKED_STATE = {android.R.attr.state_checked};
    private boolean mChecked;

    public ActivatedLinearLayout(Context context) {
        super(context);
    }

    public ActivatedLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActivatedLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    public void setChecked(boolean b) {
        mChecked = b;
        refreshDrawableState();
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        mChecked = !mChecked;
        refreshDrawableState();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] states =  super.onCreateDrawableState(extraSpace + 1);
        if (mChecked){
            mergeDrawableStates(states, CHECKED_STATE);
        }
        return states;
    }


}
