package com.grapefrukt.camerabooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

public class RemoteControlReceiver extends BroadcastReceiver {
	
	private static CameraBoothActivity mMain;
	private static boolean lastState = false;
	public static void setMain(CameraBoothActivity main){
		mMain = main;
	}
	
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            /* handle media button intent here by reading contents */
            /* of EXTRA_KEY_EVENT to know which key was pressed    */
        	
        	Bundle extras = intent.getExtras();
        	KeyEvent event = (KeyEvent) extras.get(Intent.EXTRA_KEY_EVENT);
        	
        	int action = event.getAction();
        	boolean state = action == KeyEvent.ACTION_DOWN;
        	if (state == lastState) return;
        	
        	lastState = state;
        	mMain.setHeadsetButtonState(state);
        }
    }
}