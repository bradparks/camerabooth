package com.grapefrukt.camerabooth;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.MediaController;
import android.widget.VideoView;

import com.grapefrukt.camerabooth.State;

public class CameraBoothActivity extends Activity implements OnClickListener, SurfaceHolder.Callback, OnCompletionListener {
	public static final String TAG = "VIDEOCAPTURE";

	private MediaRecorder recorder;
	private SurfaceHolder holder;
	private CamcorderProfile camcorderProfile;
	private Camera camera;	
	private SurfaceView cameraView;
	private VideoView videoView;
	private File recordFile;
	
	private AudioManager audioManager;
	private ComponentName remoteControlResponder;
	
	private State state = State.STARTUP;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
				
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);

		setContentView(R.layout.activity_fullscreen);
		
		cameraView = (SurfaceView) findViewById(R.id.CameraView);
		holder = cameraView.getHolder();
		holder.addCallback(this);

		cameraView.setClickable(true);
		cameraView.setOnClickListener(this);
		
		videoView = (VideoView) findViewById(R.id.VideoView);
		videoView.setOnCompletionListener(this);
		videoView.setVisibility(View.INVISIBLE);
		
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		remoteControlResponder = new ComponentName(getPackageName(), RemoteControlReceiver.class.getName());
		RemoteControlReceiver.setMain(this);
	}

	private void prepareRecorder() {
        recorder = new MediaRecorder();
		recorder.setPreviewDisplay(holder.getSurface());
		
		camera.unlock();
		recorder.setCamera(camera);
		
		recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

		recorder.setProfile(camcorderProfile);

		// This is all very sloppy
		if (camcorderProfile.fileFormat == MediaRecorder.OutputFormat.MPEG_4) {
        	try {
				recordFile = File.createTempFile("videocapture", ".mp4", Environment.getExternalStorageDirectory());
				recorder.setOutputFile(recordFile.getAbsolutePath());
				
				Log.v(TAG, "Recording to: " + recordFile.getAbsolutePath());
			} catch (IOException e) {
				Log.v(TAG,"Couldn't create file");
				e.printStackTrace();
				finish();
			}
		}
		
		recorder.setMaxDuration(50000); // 50 seconds
		recorder.setMaxFileSize(20 * 1024 * 1024); // 20 megabytes
		
		try {
			recorder.prepare();
		} catch (IllegalStateException e) {
			e.printStackTrace();
			finish();
		} catch (IOException e) {
			e.printStackTrace();
			finish();
		}
		
		state = State.READY;
	}

	private void startRecording(){
		if(state != State.READY) return;
		state = State.RECORDING;
		recorder.start();
		Log.v(TAG, "Recording Started");
	}
	
	private void stopRecording(){
		if (state != State.RECORDING) return;
		
		recorder.stop();
		
		try {
			camera.reconnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Log.v(TAG, "Recording Stopped");
		
		showPlayback();
	}
	
	private void showPlayback() {
		state = State.PLAYBACK;
		
		cameraView.setVisibility(View.INVISIBLE);
		
		Log.v(TAG, "Playing back from " + recordFile.getAbsolutePath());
		videoView.setVideoPath(recordFile.getAbsolutePath());
		MediaController mediaController = new MediaController(this);
		videoView.setMediaController(mediaController);
		videoView.start();
		videoView.setVisibility(View.VISIBLE);
	}

	private void setupCameraPreview() {
		Camera.Parameters p = camera.getParameters();
		
		p.setPreviewSize(1280, 720);
		//p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
		 
		camera.setParameters(p);
		
		try {
			camera.setPreviewDisplay(holder);
		}
		catch (IOException e) {
			Log.e(TAG,e.getMessage());
			e.printStackTrace();
		}
		
		camera.startPreview();
	}
	
	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(TAG, "surfaceCreated");
		
		camera = Camera.open(1);
		setupCameraPreview();
	}
	
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(TAG, "surfaceChanged");

		if (state == State.READY){
			camera.stopPreview();
		}
		
		setupCameraPreview();
		prepareRecorder();	
	}
	
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(TAG, "surfaceDestroyed");
		
		if (state == State.RECORDING) stopRecording();
		
		state = State.LOST;
		
		recorder.release();
		camera.release();
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		cameraView.setVisibility(View.VISIBLE);
		videoView.setVisibility(View.INVISIBLE);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		audioManager.registerMediaButtonEventReceiver(remoteControlResponder);
	}
	
	@Override
	protected void onPause() {
		audioManager.unregisterMediaButtonEventReceiver(remoteControlResponder);
		super.onPause();
	}
	
	@Override
	public void onClick(View v) {
		toggleRecording();
	}

	public void setHeadsetButtonState(boolean isDown) {
		if (isDown) return;
		
		toggleRecording();		
	}
	
	private void toggleRecording() {
		switch (state) {
			case READY:
				startRecording();
				break;
			case RECORDING:
				stopRecording();
				break;
			default:
				break;
		}		
	}

}
