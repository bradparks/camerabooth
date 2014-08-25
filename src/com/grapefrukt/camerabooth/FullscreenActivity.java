package com.grapefrukt.camerabooth;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
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

public class FullscreenActivity extends Activity implements OnClickListener, SurfaceHolder.Callback, OnCompletionListener {
	public static final String LOGTAG = "VIDEOCAPTURE";

	private MediaRecorder recorder;
	private SurfaceHolder holder;
	private CamcorderProfile camcorderProfile;
	private Camera camera;	
	private SurfaceView cameraView;
	private VideoView videoView;
	private File recordFile;
	
	private boolean recording = false;
	private boolean previewRunning = false;
	
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
				
				Log.v(LOGTAG, "Recording to: " + recordFile.getAbsolutePath());
			} catch (IOException e) {
				Log.v(LOGTAG,"Couldn't create file");
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
	}

	public void onClick(View v) {
		if (recording) {
			recorder.stop();
			
			try {
				camera.reconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
		
			recording = false;
			Log.v(LOGTAG, "Recording Stopped");
			
			cameraView.setVisibility(View.INVISIBLE);
			
			Log.v(LOGTAG, "Playing back from " + recordFile.getAbsolutePath());
			videoView.setVideoPath(recordFile.getAbsolutePath());
			MediaController mediaController = new MediaController(this);
			videoView.setMediaController(mediaController);
			videoView.start();
			videoView.setVisibility(View.VISIBLE);
			
		} else {
			recording = true;
			recorder.start();
			Log.v(LOGTAG, "Recording Started");
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");
		
		camera = Camera.open(1);
		setupCameraPreview();
	}

	private void setupCameraPreview() {
		// TODO Auto-generated method stub
		Camera.Parameters p = camera.getParameters();
		
		p.setPreviewSize(1280, 720);
		//p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
		 
		camera.setParameters(p);
		
		try {
			camera.setPreviewDisplay(holder);
		}
		catch (IOException e) {
			Log.e(LOGTAG,e.getMessage());
			e.printStackTrace();
		}
		
		camera.startPreview();
		previewRunning = true;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");

		if (!recording) {
			if (previewRunning){
				camera.stopPreview();
			}
			
			setupCameraPreview();
			prepareRecorder();	
		}
	}

	
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceDestroyed");
		if (recording) {
			recorder.stop();
			recording = false;
		}
		recorder.release();
		
		previewRunning = false;
	
		camera.release();
		//finish();
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		cameraView.setVisibility(View.VISIBLE);
		videoView.setVisibility(View.INVISIBLE);
	}
}
