package com.grapefrukt.camerabooth;

import java.io.File;
import java.io.IOException;

import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;
import android.widget.ViewSwitcher.ViewFactory;

import com.grapefrukt.camerabooth.State;

public class CameraBoothActivity extends Activity implements OnClickListener, SurfaceHolder.Callback, OnCompletionListener, OnErrorListener {
	public static final String TAG = "VIDEOCAPTURE";
	
	public static final int MIN_WAIT_BEFORE_RECORD_TIME = 1000;
	public static final int MIN_RECORD_TIME = 2000;
	public static final int MAX_RECORD_TIME = 6000;
	public static final int RECORD_TIME_BAR_FUDGE = 500;
	public static final int POST_PREVIEW_OKAY_TIME = 5000;
	public static final int POST_PREVIEW_RESULT_TIME = 2000;

	private MediaRecorder recorder;
	private SurfaceHolder holder;
	private CamcorderProfile camcorderProfile;
	private Camera camera;	
	private SurfaceView cameraView;
	private VideoView videoView;
	private File recordFile;
	
	private AudioManager audioManager;
	private ComponentName remoteControlResponder;
	
	private ProgressBar progressBar;
	private CountDownTimer recordTimer;
	private CountDownTimer chanceToSaveTimer;
	private CountDownTimer savedOrDeletedTimer;
	private long recordStartTime = 0;
	private long resetTime = 0;
	
	private ImageSwitcher imageSwitcher;
	
	private State state = State.STARTUP;
	private boolean saveVideoFlag = false;
	
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
		videoView.setOnErrorListener(this);
		videoView.setVisibility(View.INVISIBLE);
		videoView.setMediaController(null); // hides the controls for the video player
		
		progressBar = (ProgressBar) findViewById(R.id.RecordProgress);
		
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		remoteControlResponder = new ComponentName(getPackageName(), RemoteControlReceiver.class.getName());
		RemoteControlReceiver.setMain(this);
		
		recordTimer = new CountDownTimer(MAX_RECORD_TIME, 16) {
	        @Override
	        public void onTick(long millisecondsRemaining) {
	        	progressBar.setMax(MAX_RECORD_TIME - RECORD_TIME_BAR_FUDGE);
	        	progressBar.setProgress(MAX_RECORD_TIME - (int) millisecondsRemaining - RECORD_TIME_BAR_FUDGE);
	        }

	        @Override
	        public void onFinish() {
	        	stopRecording();
	        }
	    };
	    
	    chanceToSaveTimer = new CountDownTimer(POST_PREVIEW_OKAY_TIME, 16) {
	        @Override
	        public void onTick(long millisecondsRemaining) {
	        	progressBar.setMax(POST_PREVIEW_OKAY_TIME);
	        	progressBar.setProgress(POST_PREVIEW_OKAY_TIME - (int) millisecondsRemaining);
	        }

	        @Override
	        public void onFinish() {
	        	deleteVideo();
	        }
	    };
	    
	    savedOrDeletedTimer = new CountDownTimer(POST_PREVIEW_RESULT_TIME, POST_PREVIEW_RESULT_TIME) {
	        @Override
	        public void onTick(long millisecondsRemaining) {
	        	// do nothing
	        }

	        @Override
	        public void onFinish() {
	        	completeSavedOrDeleted();
	        }
	    };
	    
	    imageSwitcher = (ImageSwitcher) findViewById(R.id.ImageSwitcher);
	    imageSwitcher.setFactory(new ViewFactory() {
	    	@Override
	    	public View makeView() {
	    		ImageView myView = new ImageView(getApplicationContext());
	    		myView.setScaleType(ImageView.ScaleType.CENTER_CROP);
	    		myView.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
	    		return myView;
	    	}
	    });
	    
	    // Declare the animations and initialize them
        Animation in = AnimationUtils.loadAnimation(this,android.R.anim.slide_in_left);
        Animation out = AnimationUtils.loadAnimation(this,android.R.anim.slide_out_right);
        
        // set the animation type to imageSwitcher
        imageSwitcher.setInAnimation(in);
        imageSwitcher.setOutAnimation(out);
        
        imageSwitcher.setClickable(true);
        imageSwitcher.setOnClickListener(this);
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
		
		recorder.setMaxDuration(MAX_RECORD_TIME + 1000); // a timer stops this before this value is reached, added a 1second buffer
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
		
		progressBar.setVisibility(View.INVISIBLE);
		imageSwitcher.setImageResource(R.drawable.screen_splash);
		
		state = State.READY;
	}

	private void startRecording(){
		if (state != State.READY) return;
		if (System.currentTimeMillis() - resetTime < MIN_WAIT_BEFORE_RECORD_TIME) return;
		
		recordStartTime = System.currentTimeMillis();
		saveVideoFlag = false;
		progressBar.setVisibility(View.VISIBLE);
		state = State.RECORDING;
		recordTimer.start();
		recorder.start();
		imageSwitcher.setImageResource(R.drawable.screen_record);
		Log.v(TAG, "Recording Started");
	}
	
	private void stopRecording(){
		if (state != State.RECORDING) return;
		if (System.currentTimeMillis() - recordStartTime < MIN_RECORD_TIME) return;
				
		progressBar.setVisibility(View.INVISIBLE);
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
		
		imageSwitcher.setImageResource(R.drawable.screen_playback);
		
		Log.v(TAG, "Playing back from " + recordFile.getAbsolutePath());
		videoView.setVideoPath(recordFile.getAbsolutePath());
		MediaController mediaController = new MediaController(this);
		videoView.setMediaController(mediaController);
		videoView.start();
		videoView.setVisibility(View.VISIBLE);
		videoView.setMediaController(null);
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
	
	private void playbackComplete() {
		state = State.POST_PLAYBACK;
		chanceToSaveTimer.start();
		imageSwitcher.setImageResource(R.drawable.screens_playback_complete);
	}

	private void saveVideo() {
		saveVideoFlag = true;
		completePostPlayback();
	}
	
	private void deleteVideo(){
		saveVideoFlag = false;
		completePostPlayback();
	}
	
	private void completePostPlayback() {
		if (state != State.POST_PLAYBACK) return;
		if (!saveVideoFlag){
			imageSwitcher.setImageResource(R.drawable.screen_post_record_deleted);
			recordFile.delete();
			Log.e(TAG, "deleted video");
		} else {
			imageSwitcher.setImageResource(R.drawable.screen_post_record_saved);
			Log.e(TAG, "saved video");
		}
		savedOrDeletedTimer.start();
		state = State.POST_PLAYBACK_COMPLETE;
	}
	
	private void completeSavedOrDeleted(){
		if (state != State.POST_PLAYBACK_COMPLETE) return;
		cameraView.setVisibility(View.VISIBLE);
		videoView.setVisibility(View.INVISIBLE);
	}
	
	public void surfaceCreated(SurfaceHolder holder) {
		camera = Camera.open(1);
		setupCameraPreview();
		resetTime = System.currentTimeMillis();
	}
	
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (state == State.READY) camera.stopPreview();
		setupCameraPreview();
		prepareRecorder();	
	}
	
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (state == State.RECORDING) stopRecording();
		state = State.LOST;
		recorder.release();
		camera.release();
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		playbackComplete();
	}
	
	@Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.d(TAG, "setOnErrorListener ");
        playbackComplete();
        return true;
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
		handleInput();
	}

	public void setHeadsetButtonState(boolean isDown) {
		if (isDown) return;
		
		handleInput();		
	}
	
	private void handleInput() {
		switch (state) {
			case READY:
				startRecording();
				break;
			case RECORDING:
				stopRecording();
				break;
			case POST_PLAYBACK:
				saveVideo();
			default:
				break;
		}		
	}

}
