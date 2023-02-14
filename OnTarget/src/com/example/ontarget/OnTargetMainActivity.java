package com.example.ontarget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.example.ontarget.OnTargetView.OnTargetThread;

public class OnTargetMainActivity extends Activity implements
		View.OnClickListener {

	/** A handle to the thread that's actually running the animation. */
	private OnTargetThread mOnTargetThread;

	/** A handle to the View in which the game is running. */
	private OnTargetView mOnTargetView;

	private Button mBtnStart; // the play start button
	private Button mBtnRetry; // used to hit retry
	private TextView mTVhelp; // the window for instructions and such
	private TextView mTVTimer; // game window timer

	private RadioButton mRBtnBeginner;
	private RadioButton mRBtnAdvanced;
	private String BEGINNER = "Beginner";
	private String ADVANCED = "Advanced";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// get handles to the JetView from XML and the JET thread.
		mOnTargetView = (OnTargetView) findViewById(R.id.OnTargetView);
		mOnTargetThread = mOnTargetView.getThread();

		mBtnStart = (Button) findViewById(R.id.startButton);
		mBtnStart.setOnClickListener(this);

		mBtnRetry = (Button) findViewById(R.id.retryButton);
		mBtnRetry.setOnClickListener(this);

		// set up handles for instruction text and game timer text
		mTVhelp = (TextView) findViewById(R.id.helpTextView);
		mTVTimer = (TextView) findViewById(R.id.timerTextView);
		//
		mRBtnBeginner = (RadioButton) findViewById(R.id.radio_Begin);
		mRBtnAdvanced = (RadioButton) findViewById(R.id.radio_Adv);
		// sicnce this is checked in xml file, on click is not fired
		if (mRBtnBeginner.isChecked()) {
			mOnTargetView.setGameCategory(BEGINNER);
		}

		mOnTargetView.setTimerView(mTVTimer);
		mOnTargetView.SetButtonView(mBtnRetry);
		mOnTargetView.SetTextView(mTVhelp);

	}

	/**
	 * Handles component interaction
	 * 
	 * @param v
	 *            - The object which has been clicked
	 */
	@SuppressLint("NewApi")
	public void onClick(View v) {

		// this is the first screen
		if (mOnTargetThread.getGameState() == OnTargetThread.STATE_START) {
			mBtnStart.setText("PLAY!");
			mTVhelp.setVisibility(View.VISIBLE);

			mRBtnBeginner.setVisibility(View.VISIBLE);
			mRBtnAdvanced.setVisibility(View.VISIBLE);

			mTVhelp.setText(R.string.helpText);
			mOnTargetThread.setGameState(OnTargetThread.STATE_PLAY);
		}
		// we have entered game play, now we about to start running
		else if (mOnTargetThread.getGameState() == OnTargetThread.STATE_PLAY) {
			mBtnStart.setVisibility(View.INVISIBLE);

			mRBtnBeginner.setVisibility(View.INVISIBLE);
			mRBtnAdvanced.setVisibility(View.INVISIBLE);

			mTVhelp.setVisibility(View.INVISIBLE);
			mTVTimer.setVisibility(View.VISIBLE);
			mOnTargetThread.setGameState(OnTargetThread.STATE_RUNNING);

		} else if (mBtnRetry.equals(v)) {

			mTVhelp.setText(R.string.helpText);

			mBtnStart.setText("PLAY!");
			mBtnRetry.setVisibility(View.INVISIBLE);

			mRBtnBeginner.setVisibility(View.VISIBLE);
			mRBtnAdvanced.setVisibility(View.VISIBLE);

			mTVhelp.setVisibility(View.VISIBLE);
			mBtnStart.setText("PLAY!");
			mBtnStart.setVisibility(View.VISIBLE);

			mOnTargetThread.setGameState(OnTargetThread.STATE_PLAY);

		} else {
			Log.d("JB VIEW", "unknown click " + v.getId());
			Log.d("JB VIEW", "state is  " + mOnTargetThread.mState);
		}
	}

	public void onRadioButtonClicked(View view) {

		boolean checked = ((RadioButton) view).isChecked();

		switch (view.getId()) {
		case R.id.radio_Begin:
			if (checked)
				mOnTargetView.setGameCategory(BEGINNER);
			break;
		case R.id.radio_Adv:
			if (checked)
				mOnTargetView.setGameCategory(ADVANCED);
			break;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onStop() {
		super.onStop();
		mOnTargetThread.unregisterListener();
	}

}
