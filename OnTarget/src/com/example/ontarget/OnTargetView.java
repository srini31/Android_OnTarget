package com.example.ontarget;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.JetPlayer;
import android.media.JetPlayer.OnJetEventListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class OnTargetView extends SurfaceView implements SurfaceHolder.Callback {

	// public static final int mSuccessThreshold = 10;
	public int mSuccessThreshold = 10;
	// used to calculate level for mutes and trigger clip
	public int mHitTotal = 0; // total number asteroids you need to hit.
	public int mCurrentBed = 0; // which music bed is currently playing?

	// a lazy graphic fudge for the initial title splash
	private Bitmap mTitleBG;
	private Bitmap mTitleBG2;
	String LogDebug = "DEBUG";

	// All the methods of OnTargetView are here
	public static final String TAG = "OnTarget";

	/** The thread that actually draws the animation */
	private OnTargetThread thread; // privateJetBoyThread thread;
	private TextView mTimerView;
	private Button mButtonRetry;
	private TextView mTextView;
	private String mGameCategory;

	/**
	 * The constructor called from the main activity
	 * 
	 * @param context
	 * @param attrs
	 */

	public OnTargetView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public OnTargetView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// register our interest in hearing about changes to our surface
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);

		// create thread only; it's started in surfaceCreated()
		// except if used in the layout editor.
		if (isInEditMode() == false) {
			thread = new OnTargetThread(holder, context, new Handler() {

				public void handleMessage(Message m) {

					mTimerView.setText(m.getData().getString("text"));
					if (m.getData().getString("STATE_LOSE") != null) {
						// mButtonRestart.setVisibility(View.VISIBLE);
						mButtonRetry.setVisibility(View.VISIBLE);
						mTimerView.setVisibility(View.INVISIBLE);
						mTextView.setVisibility(View.VISIBLE);
						Log.d(TAG, "the total was " + mHitTotal);

						if (mHitTotal >= mSuccessThreshold) {
							mTextView.setText(R.string.winText);
						} else {
							mTextView.setText("Sorry, You Lose! \nYou hit "
									+ mHitTotal + " Target's.\n You need "
									+ mSuccessThreshold + " to win.");
						}
						mTimerView.setText("1:12");
						// mTextView.setHeight(20);
					}
				}// end handleMessage
			});
		}
		setFocusable(true); // make sure we get key events
		Log.d(TAG, "@@@ done creating view!");
	}

	/**
	 * Pass in a reference to the timer view widget so we can update it from
	 * here.
	 * 
	 * @param tv
	 */
	public void setTimerView(TextView tv) {
		mTimerView = tv;
	}

	public void setGameCategory(String category) {
		mGameCategory = category;
		Log.d(TAG, "mGameCategory: " + mGameCategory);
	}

	/**
	 * Standard window-focus override. Notice focus lost so we can pause on
	 * focus lost. e.g. user switches to take a call.
	 */
	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		if (!hasWindowFocus) {
			if (thread != null)
				thread.pause();
		}
	}

	/**
	 * Fetches the animation thread corresponding to this LunarView
	 * 
	 * @return the animation thread
	 */
	public OnTargetThread getThread() {
		return thread;
	}

	/** Callback invoked when the surface dimensions change. */
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		thread.setSurfaceSize(width, height);
	}

	public void surfaceCreated(SurfaceHolder arg0) {
		// start the thread here so that we don't busy-wait in run()
		// waiting for the surface to be created
		thread.setRunning(true);
		thread.start();
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 * A reference to the button to start game over
	 * 
	 * @param _buttonRetry
	 */
	public void SetButtonView(Button _buttonRetry) {
		mButtonRetry = _buttonRetry;
	}

	// we reuse the help screen from the end game screen.
	public void SetTextView(TextView textView) {
		mTextView = textView;
	}

	// End of OnTargetView Methods

	/**
	 * Base class for any external event passed to the OnTargetThread. This can
	 * include user input, system events, network input, etc.
	 */
	class GameEvent {
		long eventTime;

		public GameEvent() {
			eventTime = System.currentTimeMillis();
		}
	}

	/** A GameEvent subclass for events from the JetPlayer. */
	class JetGameEvent extends GameEvent {
		public JetPlayer player;
		public short segment;
		public byte track;
		public byte channel;
		public byte controller;
		public byte value;

		/** Simple constructor to make populating this event easier. */
		public JetGameEvent(JetPlayer player, short segment, byte track,
				byte channel, byte controller, byte value) {
			this.player = player;
			this.segment = segment;
			this.track = track;
			this.channel = channel;
			this.controller = controller;
			this.value = value;
		}
	}

	// the JetBoyThread receives all the events from the JET player
	// through the OnJetEventListener interface.
	class OnTargetThread extends Thread implements OnJetEventListener,
			SensorEventListener {

		/** State-tracking constants. */
		public static final int STATE_START = -1;
		public static final int STATE_PLAY = 0;
		public static final int STATE_LOSE = 1;
		public static final int STATE_PAUSE = 2;
		public static final int STATE_RUNNING = 3;

		// how many frames per beat? The basic animation can be changed for
		// instance to 3/4 by changing this to 3.
		// untested is the impact on other parts of game logic for non 4/4 time.
		private static final int ANIMATION_FRAMES_PER_BEAT = 4;// 4;
		public boolean mInitialized = false;
		/** Queue for GameEvents */
		protected ConcurrentLinkedQueue<GameEvent> mEventQueue = new ConcurrentLinkedQueue<GameEvent>();

		/** Context for processKey to maintain state accross frames * */
		protected Object mKeyContext = null;
		public int mTimerLimit; // the timer display in seconds
		public final int TIMER_LIMIT = 60;
		// used for internal timing logic. Change time here
		private String mTimerValue = "1:12"; // string value for timer display
		public int mState; // start, play, running, lose are the states we use
		long currentTime = 0;
		/** The drawable to use as the far background of the animation canvas */
		private Bitmap mBackgroundImageFar;

		/** The drawable to use as the close background of the animation canvas */
		private Bitmap mBackgroundImageNear;

		// SRi//Bitmaps are used to show multiple pics at the same position,
		// illusion of animation
		// used to track beat for synch of mute/unmute actions
		private int mBeatCount = 1;
		private Bitmap[] mBMTargetToHit = new Bitmap[6];
		private Bitmap[] mBMArrows = new Bitmap[1];
		private Bitmap[] mBMExplosions = new Bitmap[4]; // hit animation
		private Bitmap[] mBMTarExplosions = new Bitmap[4]; // hit animation
		private Bitmap mTimerShell;
		private long mLastBeatTime; // used to save the beat event system time.
		private long mPassedTime;

		// the asteroid send events are generated from the Jet File.
		// but which land they start in is random.
		private Random mRandom = new Random();

		// the star of our show, a reference to the JetPlayer object.
		private JetPlayer mJet = null;
		private boolean mJetPlaying = false;

		// Message handler used by thread to interact with TextView
		private Handler mHandler;

		// Handle to the surface manager object we interact with
		private SurfaceHolder mSurfaceHolder;

		// Handle to the application context, used to e.g. fetch Drawables.
		private Context mContext;

		// Indicate whether the surface has been created & is ready to draw
		private boolean mRun = false;
		private Timer mTimer = null;
		// updates the screen clock. Also used for tempo timing.
		private TimerTask mTimerTask = null;
		private int mTaskIntervalInMillis = 1000;
		// one second - used to update timer

		// Current height of the surface/canvas. @see #setSurfaceSize
		private int mCanvasHeight = 1;
		// Current width of the surface/canvas. @see #setSurfaceSize
		private int mCanvasWidth = 1;
		private int mTargetIndex = 0; // used to track the picture to draw for
										// ship animation
		private Vector<Arrow> mArrowVec; // stores all of the arrow objects in
											// order
		private Vector<TargetAsteroid> mTargetAstVec;
		// stores all of the asteroid objects in order
		private Vector<Explosion> mTargetExplVec;

		// right to left scroll tracker for near and far BG
		private int mBGFarMoveX = 0;
		private int mBGNearMoveX = 0;

		// this is the pixel position of the laser beam guide.
		private int mArrowMinY = 200; // how far up arrow can be painted
		private int mTargetMinY = 120;// 160;
		// how far up asteroid can be painted
		private int mMoveYIncVal = 5;// 10;//40;
		private int mMoveYInc = mMoveYIncVal;// 5;// 10;//40;
		// private int mMoveYDec =-10;//40;
		// private boolean mFlipTargDir = false;
		private int mTargetPosn = 0;
		// start here and move laterally + 40
		private int mArrTarLimitY = 0;// init to mCanvasHeight=442/3

		private int mPixelMoveX = 8;
		// how much do we move the arrows per beat?

		Resources mRes;
		private long TargetExplBeatTime = 0;
		// array to store the mute masks that are applied during game
		// play to respond to the player's hit streaks
		private boolean muteMask[][] = new boolean[9][32];

		private int EXPLODE_DIST = 100;
		private boolean CreateNextArrow = true;
		private boolean CreateNewTarget = true;
		private boolean mArrTarExplode = false;
		private SensorManager mSensorManager;
		private Sensor mAccelerometer;
		private Sensor mMagnetometer;
		Float azimut; // View to draw a compass
		public static final String TAG = "MAinDebug";

		float[] mGravity;
		float[] mGeomagnetic;
		float[] mOrientation;

		/**
		 * This is the constructor for the main worker bee
		 * 
		 * @param surfaceHolder
		 * @param context
		 * @param handler
		 */
		public OnTargetThread(SurfaceHolder surfaceHolder, Context context,
				Handler handler) {

			mSurfaceHolder = surfaceHolder;
			mHandler = handler;
			mContext = context;
			mRes = context.getResources();

			// this are the mute arrays associated with the music beds
			// in the
			// JET file
			for (int ii = 0; ii < 8; ii++) {
				for (int xx = 0; xx < 32; xx++) {
					muteMask[ii][xx] = true;
				}
			}
			muteMask[0][2] = false;
			muteMask[0][3] = false;
			muteMask[0][4] = false;
			muteMask[0][5] = false;

			muteMask[1][2] = false;
			muteMask[1][3] = false;
			muteMask[1][4] = false;
			muteMask[1][5] = false;
			muteMask[1][8] = false;
			muteMask[1][9] = false;

			muteMask[2][2] = false;
			muteMask[2][3] = false;
			muteMask[2][6] = false;
			muteMask[2][7] = false;
			muteMask[2][8] = false;
			muteMask[2][9] = false;

			muteMask[3][2] = false;
			muteMask[3][3] = false;
			muteMask[3][6] = false;
			muteMask[3][11] = false;
			muteMask[3][12] = false;

			muteMask[4][2] = false;
			muteMask[4][3] = false;
			muteMask[4][10] = false;
			muteMask[4][11] = false;
			muteMask[4][12] = false;
			muteMask[4][13] = false;

			muteMask[5][2] = false;
			muteMask[5][3] = false;
			muteMask[5][10] = false;
			muteMask[5][12] = false;
			muteMask[5][15] = false;
			muteMask[5][17] = false;

			muteMask[6][2] = false;
			muteMask[6][3] = false;
			muteMask[6][14] = false;
			muteMask[6][15] = false;
			muteMask[6][16] = false;
			muteMask[6][17] = false;

			muteMask[7][2] = false;
			muteMask[7][3] = false;
			muteMask[7][6] = false;
			muteMask[7][14] = false;
			muteMask[7][15] = false;
			muteMask[7][16] = false;
			muteMask[7][17] = false;
			muteMask[7][18] = false;

			// set all tracks to play
			for (int xx = 0; xx < 32; xx++) {
				muteMask[8][xx] = false;
			}

			// always set state to start, ensure we come in from front door if
			// app gets tucked into background
			// mState = STATE_START;
			// setInitialGameState();
			mTitleBG = BitmapFactory.decodeResource(mRes,
					R.drawable.title_sunny_day);
			// load background image as a Bitmap

			mBackgroundImageFar = BitmapFactory.decodeResource(mRes,
					R.drawable.background_scenery_game);
			mBackgroundImageNear = BitmapFactory.decodeResource(mRes,
					R.drawable.background_scenery_game);

			mBMTargetToHit[0] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid01);
			mBMTargetToHit[1] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid03);
			mBMTargetToHit[2] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid05);
			mBMTargetToHit[3] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid07);
			mBMTargetToHit[4] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid10);
			mBMTargetToHit[5] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid12);

			mTimerShell = BitmapFactory.decodeResource(mRes,
					R.drawable.int_timer);

			mBMArrows[0] = BitmapFactory.decodeResource(mRes,
					R.drawable.arrow_1);
			// I wanted them to rotate in a certain way
			// so I loaded them backwards from the way created.
			mBMExplosions[0] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid_explode1);
			mBMExplosions[1] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid_explode2);
			mBMExplosions[2] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid_explode3);
			mBMExplosions[3] = BitmapFactory.decodeResource(mRes,
					R.drawable.asteroid_explode4);

			mBMTarExplosions[0] = mBMExplosions[0];
			mBMTarExplosions[1] = mBMExplosions[1];
			mBMTarExplosions[2] = mBMExplosions[2];
			mBMTarExplosions[3] = mBMExplosions[3];

			mSensorManager = (SensorManager) context
					.getSystemService(Context.SENSOR_SERVICE);

			mAccelerometer = mSensorManager
					.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			mMagnetometer = mSensorManager
					.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			mOrientation = new float[3];
			registerSensorListeners();

			// always set state to start, ensure we come in from front door if
			// app gets tucked into background
			mState = STATE_START;
			setInitialGameState();

		} // Public OnTargetThread

		public void registerSensorListeners() {

			mSensorManager.registerListener((SensorEventListener) this,
					mAccelerometer, SensorManager.SENSOR_DELAY_UI);
			mSensorManager.registerListener((SensorEventListener) this,
					mMagnetometer, SensorManager.SENSOR_DELAY_UI);
		}

		// sensor methods
		public void onSensorChanged(SensorEvent event) {

			// read values less frequently?
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
				mGravity = event.values;
			if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
				mGeomagnetic = event.values;
			if (mGravity != null && mGeomagnetic != null) {
				float R[] = new float[9];
				float I[] = new float[9];

				boolean success = SensorManager.getRotationMatrix(R, I,
						mGravity, mGeomagnetic);
				if (success) {
					SensorManager.getOrientation(R, mOrientation);
					azimut = mOrientation[0]; // orientation contains: azimut,
												// pitch and roll
					Log.d(TAG, " orientation:-- " + mOrientation[0] + " : "
							+ mOrientation[1] + " : " + mOrientation[2]);
				}
			}
			// mCustomDrawableView.invalidate();
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		// Unregisters a listener for the sensors with which it is
		// registered.
		public void unregisterListener() {
			mSensorManager.unregisterListener(this);
		}

		public void unregisterListener(SensorEventListener listener) {
			mSensorManager.unregisterListener(listener);
		}

		public void unregisterListener(SensorEventListener listener,
				Sensor sensor) {

			mSensorManager.unregisterListener(listener, sensor);
		}

		// Does the grunt work of setting up initial jet requirements
		private void initializeJetPlayer() {

			// let's create our JetPlayer instance using the factory.
			// if we already had one, the same singleton is returned.
			mJet = JetPlayer.getJetPlayer();

			mJetPlaying = false;

			// make sure we flush the queue, otherwise left over
			// events from previous gameplay can hang around. Here we don't
			// really need that but if you ever reuse a JetPlayer instance,
			// clear the queue before reusing it, this will also clear any
			// trigger clips that have been triggered but not played yet.
			mJet.clearQueue();

			// we are going to receive in this example all the JET
			// callbacks in this animation thread object.
			// updatelater//
			mJet.setEventListener(this);

			// load the actual JET content the game will be playing,
			// it's stored as a raw resource in our APK, and is
			// labeled "level1"
			mJet.loadJetFile(mContext.getResources().openRawResourceFd(
					R.raw.level1));
			// if our JET file was stored on the sdcard for instance,
			// we would have used
			// mJet.loadJetFile("/sdcard/level1.jet");

			mCurrentBed = 0;
			byte sSegmentID = 0;
			// Log.d(LogDebug, " start queuing jet file");

			// now we're all set to prepare queuing the JET audio
			// segments for the game. In this example, the game uses segment 0
			// for the duration of the game play, and plays segment 1 several
			// times as the "outro" music, so we're going to queue everything
			// upfront, but with more complex compositions, we could also queue
			// the segments during the game play.

			// this is the main game play music, it is located at
			// segment 0 it uses the first DLS lib in the .jet resource, which
			// is at index 0 index -1 means no DLS
			mJet.queueJetSegment(0, 0, 0, 0, 0, sSegmentID);

			// end game music, loop 4 times normal pitch
			mJet.queueJetSegment(1, 0, 4, 0, 0, sSegmentID);

			// end game music loop 4 times up an octave
			mJet.queueJetSegment(1, 0, 4, 1, 0, sSegmentID);

			// set the mute mask as designed for the beginning of the
			// game, when the
			// the player hasn't scored yet.
			mJet.setMuteArray(muteMask[0], true);
		}

		// the heart of the worker bee - This loop keeps running all the time
		public void run() {
			// while running do stuff in this loop!
			while (mRun) {
				Canvas c = null;

				if (mState == STATE_RUNNING) {
					// Process any input and apply it to the game state
					updateGameState();
					if (!mJetPlaying) {
						mInitialized = false;
						mJet.play();
						mJetPlaying = true;
					}
					mPassedTime = System.currentTimeMillis();
					// kick off the timer task for counter update if not already
					// initialized
					if (mTimerTask == null) {
						mTimerTask = new TimerTask() {
							public void run() {
								doCountDown();
							}
						};
						mTimer.schedule(mTimerTask, mTaskIntervalInMillis);
					}// end of TimerTask init block

				}// end of STATE_RUNNING block
				else if (mState == STATE_PLAY && !mInitialized) {
					setInitialGameState();
				} else if (mState == STATE_LOSE) {
					mInitialized = false;
				}
				try {
					c = mSurfaceHolder.lockCanvas(null);
					// synchronized (mSurfaceHolder) {
					doDraw(c);
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above,
					// we don't leave the Surface in an inconsistent state
					if (c != null) {
						mSurfaceHolder.unlockCanvasAndPost(c);
					}
				} // end finally block
			} // end while mrun block
		}

		// Update parameters as per checkBox on screen
		protected void updateStateAsPerGameCategory() {
			if (mGameCategory == "Beginner") {
				mPixelMoveX = 4;
				mMoveYIncVal = 2;
				EXPLODE_DIST = 60;
				mSuccessThreshold = 5;
			} else if (mGameCategory == "Advanced") {
				mPixelMoveX = 8;
				mMoveYIncVal = 5;
				EXPLODE_DIST = 90;
				mSuccessThreshold = 10;
			}
		}

		protected void updateGameState() {
			// Process any game events and apply them

			mArrTarLimitY = mCanvasHeight / 4;
			// Note the time of the last beat
			mLastBeatTime = System.currentTimeMillis();

			// Update explosions before we update targets because updateArrows
			// may add new explosions that we do not want updated until next
			// frame
			updateTargetExplosions(mKeyContext);

			// Update arrow, target positions, hit status and animations
			updateArrows(mKeyContext);
			updateTargets(mKeyContext); // Splitting Arrow and Target updates
			// Check for an event that triggers a new arrow
			if (CreateNextArrow) {
				doArrowCreation();
				Log.d(TAG, "New Arrow Created");
				if (CreateNewTarget) {
					doTargetCreation();
					Log.d(TAG, "New Target Created");
				}
			}
			mBeatCount++;
			if (mBeatCount > 4) {
				mBeatCount = 1;
			}
		}

		// Activated on Retry button
		private void setInitialGameState() {

			Log.d(TAG, " setInitialGameState");
			mTimerLimit = TIMER_LIMIT;
			// set up jet stuff
			initializeJetPlayer();

			mTimer = new Timer();
			mArrowVec = new Vector<Arrow>(); // Asteroid
			mTargetAstVec = new Vector<TargetAsteroid>();
			mTargetExplVec = new Vector<Explosion>();
			// mInitialized = true; - is this flag needed? this will not reset
			// variables on retry button

			mHitTotal = 0;
			mSuccessThreshold = 10;
			// ON Retry button reset the Target asteroid positions
			mMoveYInc = mMoveYIncVal;// 5;// 10;//40;
			mTargetPosn = mTargetMinY;
			updateStateAsPerGameCategory();
		}

		private void doDraw(Canvas canvas) { // called from run -- loop

			if (mState == STATE_RUNNING) {
				doDrawRunning(canvas);
			} else if (mState == STATE_START) {
				doDrawReady(canvas);
			} else if (mState == STATE_PLAY || mState == STATE_LOSE) {
				if (mTitleBG2 == null) {
					mTitleBG2 = BitmapFactory.decodeResource(mRes,
							R.drawable.title_sunny_day_pg_2); // title_bg_hori
				}
				doDrawPlay(canvas);
			}// end state play block
		}

		private void doDrawRunning(Canvas canvas) { // called from run() -- loop

			mBGFarMoveX = mBGFarMoveX - 1; // decrement the far background
			mBGNearMoveX = mBGNearMoveX - 4; // decrement the near background

			canvas.drawBitmap(mBackgroundImageFar, 0, 0, null); // only need one
																// draw
			canvas.drawBitmap(mBackgroundImageNear, 0, 0, null);

			doArrowAnimation(canvas);

			if (mTargetIndex == 72) { // index=24 for inc 15
				mTargetIndex = 0;
				/*
				 * if (mMoveYInc == 5) // 10 mMoveYInc = -5; // -10 else if
				 * (mMoveYInc == -5) // -10 mMoveYInc = 5;// 10;
				 */
				if (mMoveYInc > 0)
					mMoveYInc = -1 * mMoveYIncVal;
				else if (mMoveYInc < 0)
					mMoveYInc = mMoveYIncVal;
			}
			mTargetIndex++;
			doTargetExplosionAnimation(canvas);

			// tick tock
			canvas.drawBitmap(mTimerShell,
					mCanvasWidth - mTimerShell.getWidth(), 0, null);
		}

		private void doDrawReady(Canvas canvas) {
			canvas.drawBitmap(mTitleBG, 0, 0, null);
		}

		private void doDrawPlay(Canvas canvas) {
			canvas.drawBitmap(mTitleBG2, 0, 0, null);
		}

		private void doArrowAnimation(Canvas canvas) {

			if (mArrowVec == null | mArrowVec.size() == 0)
				// If asteroids not created AND explosions are present - return
				// mExplosionVec is initialized but size = 0
				return;
			// Compute what percentage through a beat we are and adjust
			// animation and position based on that. This assumes
			// 140bpm(428ms/beat).
			// This is just inter-beat interpolation, no game state is updated
			long frameDelta = System.currentTimeMillis() - mLastBeatTime;

			int animOffset = (int) (ANIMATION_FRAMES_PER_BEAT * frameDelta / 428);
			frameDelta = 0;
			animOffset = 0; // to make the location stable/ not moving

			for (int i = (mArrowVec.size() - 1); i >= 0; i--) {
				Arrow curArrow = mArrowVec.elementAt(i);
				// adjust arrow position
				canvas.drawBitmap(mBMArrows[(curArrow.mAniIndex + animOffset)
						% mBMArrows.length], curArrow.mDrawX, curArrow.mDrawY,
						null);
			}
			// Log.d(TAG, " orientationCCCC: " + mOrientation[0] + " : "
			// + mOrientation[1] + " : " + mOrientation[2]);
		}

		// similar to doArrowAnimation
		private void doTargetExplosionAnimation(Canvas canvas) {

			if ((mTargetAstVec == null | mTargetAstVec.size() == 0)
					&& (mTargetExplVec != null && mTargetExplVec.size() == 0))
				// If asteroids not created AND explosions are present - return
				return;
			// Compute what percentage through a beat we are and adjust
			// animation and position based on that. This assumes
			// 140bpm(428ms/beat).
			// This is just inter-beat interpolation, no game state is updated
			// long frameDelta = System.currentTimeMillis() - mLastBeatTime;
			long frameDelta = System.currentTimeMillis() - TargetExplBeatTime;
			Log.d(TAG, " mLastBeatTime: " + mLastBeatTime + " frameDelta: "
					+ frameDelta);
			TargetExplBeatTime = System.currentTimeMillis();
			int animOffset = (int) (ANIMATION_FRAMES_PER_BEAT * frameDelta / 428);
			int explanimOffset = animOffset;
			// frameDelta = 0; animOffset = 0; //Sri

			for (int i = (mTargetAstVec.size() - 1); i >= 0; i--) {
				TargetAsteroid curTarget = mTargetAstVec.elementAt(i);

				// The game is so quick on the phone, time diff is small
				// and always getting index = 0

				int animIdx = mRandom.nextInt(4);
				int tempIdx = (curTarget.mAniIndex + animOffset)
						% mBMTargetToHit.length;
				if (tempIdx == 0) {
					if (animIdx > 5) { // there are five pictures
						animIdx = 2;
					}
					tempIdx = animIdx;
				}
				// show the updated arrow position
				// long frameDelta = System.currentTimeMillis() - mLastBeatTime;
				curTarget.mDrawY = mTargetPosn;
				mTargetAstVec.elementAt(i).mDrawY = mTargetPosn;
				canvas.drawBitmap(mBMTargetToHit[animIdx], curTarget.mDrawX,
						curTarget.mDrawY, null);

				currentTime = System.currentTimeMillis();
				mTargetPosn += mMoveYInc;
			}
			for (int i = (mTargetExplVec.size() - 1); i >= 0; i--) {
				Explosion ex = mTargetExplVec.elementAt(i);

				canvas.drawBitmap(
						mBMTarExplosions[(ex.mAniIndex + explanimOffset)
								% mBMTarExplosions.length], ex.mDrawX + 40, // ex.mDrawX,
						mTargetPosn, null);
			}
		}

		protected void updateArrows(Object inputContext) {

			if (mArrowVec == null | mArrowVec.size() == 0)
				return;

			for (int i = (mArrowVec.size() - 1); i >= 0; i--) {
				Arrow curArrow = mArrowVec.elementAt(i);

				// double yMaxRight = -1.4; double yMaxLeft = 1.4;
				double arrowTiltAngle = (90 * mOrientation[1]) / 1.1;
				// (90 * mOrientation[1]) / 1.4;
				// actually multiply by height of arrow
				// double newYINcr = mPixelMoveX *
				// Math.tan(Math.toRadians(arrowTiltAngle));
				double newYINcr = mBMArrows[0].getHeight()
						* Math.tan(Math.toRadians(arrowTiltAngle));
				int newYVal = curArrow.mDrawY + (int) newYINcr;

				if (newYVal < mArrTarLimitY) {
					curArrow.mDrawY = mArrTarLimitY;
				} else if (newYVal > 3 * mArrTarLimitY) {
					curArrow.mDrawY = 3 * mArrTarLimitY;
				} else {
					curArrow.mDrawY = newYVal;
				}
				// Update the asteroids position, even missed ones keep moving
				curArrow.mDrawX -= mPixelMoveX;
				// Update asteroid animation frame
				curArrow.mAniIndex = (curArrow.mAniIndex + ANIMATION_FRAMES_PER_BEAT)
						% mBMArrows.length;
				// if we have scrolled off the screen
				// need logic to find when arrow missed, and scrolled off screen
				// arrow hit target - scroll off screen or "disappear"
				// }
				int arrowLen = mBMArrows[0].getWidth();
				int arrowHgt = mBMArrows[0].getHeight(); // smaller side
				int targetLen = mBMTargetToHit[0].getWidth();

				if (curArrow.mDrawX < (arrowLen / 5)) {// 20
					mArrowVec.removeElementAt(i);
					CreateNextArrow = true;
				} else if (curArrow.mDrawX < (arrowLen / 2)) { // 50

					if (!(mTargetAstVec == null | mTargetAstVec.size() == 0)) {
						TargetAsteroid curTarget = mTargetAstVec.elementAt(0);

						int arrowCenter = curArrow.mDrawY + arrowHgt / 2;
						int targetCenter = curTarget.mDrawY + targetLen / 2;

						if (Math.abs(arrowCenter - targetCenter) < EXPLODE_DIST) { // 100
							mArrTarExplode = true;
							mHitTotal++;
						}
					}
				} else {
					CreateNextArrow = false;
					mArrTarExplode = false;
					curArrow.mMissed = true;
				}
			}// for(int i = mArrowVec.size()
		}

		// Update Target state including position and laser hit status.
		protected void updateTargets(Object inputContext) {

			if (mTargetAstVec == null | mTargetAstVec.size() == 0)
				return;

			TargetAsteroid curTarget = mTargetAstVec.elementAt(0);
			if (mArrTarExplode) {
				Explosion ex = new Explosion();
				ex.mAniIndex = 0;
				ex.mDrawX = curTarget.mDrawX;
				ex.mDrawY = curTarget.mDrawY;
				// +90; //change this to explode at the right place
				mTargetExplVec.add(ex);
				mTargetAstVec.removeElementAt(0);
				CreateNewTarget = true;
			} else {
				CreateNewTarget = false;
				curTarget.mMissed = true;
			}
		}

		protected void updateTargetExplosions(Object inputContext) {
			Log.d(TAG, "updateTargetExplosions - mTargetPosn=" + mTargetPosn);
			if (mTargetExplVec == null | mTargetExplVec.size() == 0)
				return;

			for (int i = mTargetExplVec.size() - 1; i >= 0; i--) {
				Explosion ex = mTargetExplVec.elementAt(i);

				ex.mAniIndex += ANIMATION_FRAMES_PER_BEAT;
				// When the animation completes remove the explosion
				if (ex.mAniIndex > 3) {
					mJet.setMuteFlag(24, true, false);
					mJet.setMuteFlag(23, true, false);

					mTargetExplVec.removeElementAt(i);
					CreateNewTarget = true;
				}
			}
		}

		private void doArrowCreation() {
			Arrow _arr = new Arrow();

			// controls the horizontal position of arrow creation
			// since reference starts from Top Left corner, substract value to
			// position the arrow center of screen
			_arr.mDrawY = mCanvasHeight / 2 - mBMArrows[0].getHeight() / 2;
			_arr.mDrawX = (mCanvasWidth - mBMArrows[0].getWidth());
			_arr.mStartTime = System.currentTimeMillis();
			mArrowVec.add(_arr);
		}

		private void doTargetCreation() {

			// For landscape phone position, with home button on right,
			// mDrawX=0, mDrawY=0 will draw the picture at the Top-Left corner
			// position will be like a postage stamp
			Log.d(TAG, "Target Asteroid creation - mTargetPosn=" + mTargetPosn);
			TargetAsteroid _tar = new TargetAsteroid();

			mTargetPosn = mCanvasHeight / 2 - mBMTargetToHit[0].getWidth() / 2;
			_tar.mDrawY = mTargetPosn;
			// mCanvasHeight/2 - mBMTargetToHit[0].getWidth()/2;
			// horizontal position - length or longer side of phone
			_tar.mDrawX = 0;
			_tar.mStartTime = System.currentTimeMillis();
			mTargetAstVec.add(_tar);
		}

		/**
		 * Used to signal the thread whether it should be running or not.
		 * Passing true allows the thread to run; passing false will shut it
		 * down if it's already running. Calling start() after this was most
		 * recently called with false will result in an immediate shutdown.
		 * 
		 * @param b
		 *            - true to run, false to shut down
		 */
		public void setRunning(boolean b) {
			mRun = b;
			if (mRun == false) {
				if (mTimerTask != null)
					mTimerTask.cancel();
			}
		}

		/**
		 * returns the current int value of game state as defined by state
		 * tracking constants
		 * 
		 * @return
		 */
		public int getGameState() {
			Log.d(TAG, "getGameState");
			synchronized (mSurfaceHolder) {
				return mState;
			}
		}

		/**
		 * Sets the game mode. That is, whether we are running, paused, in the
		 * failure state, in the victory state, etc.
		 * 
		 * @see #setState(int, CharSequence)
		 * @param mode
		 *            one of the STATE_* constants
		 */
		public void setGameState(int mode) {
			Log.d(TAG, " setGameState(int mode)");
			synchronized (mSurfaceHolder) {
				setGameState(mode, null);
			}
		}

		/**
		 * Sets state based on input, optionally also passing in a text message.
		 * 
		 * @param state
		 * @param message
		 */
		public void setGameState(int state, CharSequence message) {

			synchronized (mSurfaceHolder) {
				// change state if needed
				if (mState != state) {
					mState = state;
				}
				if (mState == STATE_PLAY) {
					CreateNextArrow = true;
					CreateNewTarget = true;
					Resources res = mContext.getResources();
					mBackgroundImageFar = BitmapFactory.decodeResource(res,
							R.drawable.title_sunny_day_pg_2);// background_a

					// don't forget to resize the background image
					mBackgroundImageFar = Bitmap.createScaledBitmap(
							mBackgroundImageFar, mCanvasWidth * 2,
							mCanvasHeight, true);
					mBackgroundImageNear = BitmapFactory.decodeResource(res,
							R.drawable.background_scenery_game); // background_b,
																	// bg_blank
					// don't forget to resize the background image
					mBackgroundImageNear = Bitmap.createScaledBitmap(
							mBackgroundImageNear, mCanvasWidth * 2,
							mCanvasHeight, true);

				} else if (mState == STATE_RUNNING) {
					// When we enter the running state we should clear any old
					// events in the queue
					mEventQueue.clear();
					// And reset the key state so we don't think a button is
					// pressed when it isn't
					mKeyContext = null;
				}
			}
		}

		// Callback invoked when the surface dimensions change.
		public void setSurfaceSize(int width, int height) {
			// synchronized to make sure these all change atomically
			synchronized (mSurfaceHolder) {
				mCanvasWidth = width;
				mCanvasHeight = height;
				// don't forget to resize the background image
				mBackgroundImageFar = Bitmap.createScaledBitmap(
						mBackgroundImageFar, width * 2, height, true);
				// don't forget to resize the background image
				mBackgroundImageNear = Bitmap.createScaledBitmap(
						mBackgroundImageNear, width * 2, height, true);
			}
		}

		/** Pauses the physics update & animation */
		public void pause() {
			synchronized (mSurfaceHolder) {
				if (mState == STATE_RUNNING)
					setGameState(STATE_PAUSE);
				if (mTimerTask != null) {
					mTimerTask.cancel();
				}
				if (mJet != null) {
					mJet.pause();
				}
			}
		}

		/** Does the work of updating timer */
		private void doCountDown() {

			mTimerLimit = mTimerLimit - 1;
			try {
				// subtract one minute and see what the result is.
				int moreThanMinute = mTimerLimit - 60;
				if (moreThanMinute >= 0) {
					if (moreThanMinute > 9) {
						mTimerValue = "1:" + moreThanMinute;
					}
					// need an extra '0' for formatting
					else {
						mTimerValue = "1:0" + moreThanMinute;
					}
				} else {
					if (mTimerLimit > 9) {
						mTimerValue = "0:" + mTimerLimit;
					} else {
						mTimerValue = "0:0" + mTimerLimit;
					}
				}
			} catch (Exception e1) {
				Log.e(TAG, "doCountDown threw " + e1.toString());
			}
			Message msg = mHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putString("text", mTimerValue);
			// time's up
			if (mTimerLimit == 0) {
				b.putString("STATE_LOSE", "" + STATE_LOSE);
				mTimerTask = null;
				mState = STATE_LOSE;
			} else {
				mTimerTask = new TimerTask() {
					public void run() {
						doCountDown();
					}
				};
				mTimer.schedule(mTimerTask, mTaskIntervalInMillis);
			}
			// this is how we send data back up to the main JetBoyView thread.
			// if you look in constructor of JetBoyView you will see code for
			// Handling of messages.
			msg.setData(b);
			mHandler.sendMessage(msg);
		}

		// JET event listener interface implementation:
		/**
		 * The method which receives notification from event listener. This is
		 * where we queue up events 80 and 82. Most of this data passed is
		 * unneeded for JetBoy logic but shown for code sample completeness.
		 * 
		 * @param player
		 * @param segment
		 * @param track
		 * @param channel
		 * @param controller
		 * @param value
		 */
		@Override
		public void onJetEvent(JetPlayer player, short segment, byte track,
				byte channel, byte controller, byte value) {
			// events fire outside the animation thread. This can cause timing
			// issues. put in queue for processing by animation thread.
			mEventQueue.add(new JetGameEvent(player, segment, track, channel,
					controller, value));
		}

		// required OnJetEventListener method's. Notifications for queue updates
		@Override
		public void onJetNumQueuedSegmentUpdate(JetPlayer player, int nbSegments) {
		}

		@Override
		public void onJetPauseUpdate(JetPlayer player, int paused) {
			Log.i(TAG, "onJetPauseUpdate(): paused =" + paused);
			// mSensorManager.unregisterListener(this);
		}

		@Override
		public void onJetUserIdUpdate(JetPlayer player, int userId,
				int repeatCount) {
		}

	} // end OnTargetThread

} // OnTargetView
