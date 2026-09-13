package github.zerorooot.nap511.player;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.view.MotionEvent;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

import github.zerorooot.nap511.R;
import github.zerorooot.nap511.util.PlaybackUtil;

public class MyGSYVideoPlayer extends StandardGSYVideoPlayer {
    private TextView mMoreScale;
    private TextView switchSpeed;
    private int mType = 0;

    private long forwardRewindIncrementMs = 15000;
    private float normalPlaybackSpeed = 1f;
    private float longPressSpeed = 2f;
    private final HoldSpeedGesture holdGesture = new HoldSpeedGesture();
    private boolean suppressGestureUntilUp;
    private long pressDownTime;
    private float pressX;
    private float pressY;
    private ImageView bottomPlayPause;
    private TextView holdSpeedHint;
    private final Runnable activateHoldSpeed = () -> {
        if (!holdGesture.activate(mCurrentState == CURRENT_STATE_PLAYING, !mLockCurScreen)) return;
        suppressGestureUntilUp = true;
        // Cancel child clicks and GSY seek/tap tracking before taking over the gesture.
        MotionEvent cancel = MotionEvent.obtain(pressDownTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, pressX, pressY, 0);
        super.dispatchTouchEvent(cancel);
        gestureDetector.onTouchEvent(cancel);
        cancel.recycle();
        setSpeed(Math.max(normalPlaybackSpeed, longPressSpeed), true);
        holdSpeedHint.setText(PlaybackUtil.speedLabel(Math.max(normalPlaybackSpeed, longPressSpeed))
                + " 临时倍速 · 松手恢复");
        holdSpeedHint.setVisibility(VISIBLE);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        cancelDismissControlViewTimer();
    };
    private Consumer<Float> onPlaybackSpeedChanged;
    private final Runnable dismissSeekHint = this::dismissProgressDialog;
    private TextView batteryTextView;
    private TextView timeTextView;
    public static String TAG = "MyGSYVideoPlayer";

    // 独立的 UI 主线程定时器，独立于 GSY 播放状态
    private final Handler mClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            setBatteryAndTime();
            // 每 1000ms 刷新一次，确保即使暂停也能每秒更新
            mClockHandler.postDelayed(this, 1000);
        }
    };

    public MyGSYVideoPlayer(Context context) {
        super(context);
        gestureDetector.setIsLongpressEnabled(false);
    }

    public MyGSYVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector.setIsLongpressEnabled(false);
    }

    @Override
    public String getPlayTag() {
        return TAG;
    }

    @Override
    protected void init(Context context) {
        super.init(context);
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
        initView();
    }

    /**
     * 设置外挂字幕字体：GSYSubtitleStyle 不含字体属性，直接作用在字幕 TextView 上。
     * applyStyle 只改颜色/字号/底色/阴影/位置，不会覆盖 typeface。
     */
    public void applySubtitleTypeface(Typeface typeface) {
        ensureSubtitleController();
        if (mSubtitleView != null) {
            mSubtitleView.setTypeface(typeface);
        }
    }

    public void setHideLoadingView(boolean hide) {
        if (hide && findViewById(R.id.startAndLoadLayout) != null) {
            XLog.d("MyGSYVideoPlayer hide video start view");
            findViewById(R.id.startAndLoadLayout).setVisibility(GONE);
        }
    }

    private void initView() {
        batteryTextView = findViewById(R.id.batteryTextView);
        timeTextView = findViewById(R.id.timeTextView);

        mMoreScale = findViewById(R.id.moreScale);
        switchSpeed = findViewById(R.id.switchSpeed);
        holdSpeedHint = findViewById(R.id.holdSpeedHint);
        bottomPlayPause = findViewById(R.id.bottomPlayPause);
        bottomPlayPause.setOnClickListener(v -> {
            stopTemporarySpeed();
            clickStartIcon();
        });
        updateBottomPlayPause();
        mNeedLockFull = true;
        mLockScreen.setContentDescription("锁定控制");
        mLockScreen.setOnClickListener(v -> {
            if (!mHadPlay) return;
            stopTemporarySpeed();
            lockTouchLogic();
            mLockScreen.setContentDescription(mLockCurScreen ? "解锁控制" : "锁定控制");
            resolveUIState(mCurrentState);
            updateBottomPlayPause();
            mLockScreen.setVisibility(VISIBLE);
        });
        updateStatusVisibility();

        // 切换画面比例
        mMoreScale.setOnClickListener(v -> {
            if (!mHadPlay) return;
            mType = (mType + 1) % 5;
            resolveTypeUI();
        });

        switchSpeed.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(getContext(), v);
            for (float speed : PlaybackUtil.INSTANCE.getSPEEDS()) {
                popup.getMenu().add(PlaybackUtil.speedLabel(speed))
                        .setCheckable(true).setChecked(speed == normalPlaybackSpeed)
                        .setOnMenuItemClickListener(item -> {
                            setPlaybackSpeed(speed);
                            if (onPlaybackSpeedChanged != null) onPlaybackSpeedChanged.accept(speed);
                            return true;
                        });
            }
            popup.show();
        });
    }

    public void setOnPlaybackSpeedChanged(Consumer<Float> listener) {
        onPlaybackSpeedChanged = listener;
    }

    public void setPlaybackSpeed(float speed) {
        normalPlaybackSpeed = Float.isFinite(speed) ? Math.max(0.5f, Math.min(3f, speed)) : 1f;
        stopTemporarySpeed();
        setSpeed(normalPlaybackSpeed, true);
        if (switchSpeed != null) switchSpeed.setText(PlaybackUtil.speedLabel(normalPlaybackSpeed));
    }

    public void setLongPressSpeed(float speed) {
        longPressSpeed = speed == 3f ? 3f : 2f;
    }

    public void setSeekStepSeconds(long seconds) {
        forwardRewindIncrementMs = Math.max(1, Math.min(60, seconds)) * 1000;
    }

    public boolean isActivelyPlaying() {
        return mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PREPAREING
                || mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START;
    }

    @Override
    protected void touchLongPress(MotionEvent event) {
        // All long presses are handled at dispatchTouchEvent, before video/overlay child views.
    }

    public void stopTemporarySpeed() {
        if (holdGesture == null) return; // GSY can call state hooks during super construction.
        removeCallbacks(activateHoldSpeed);
        boolean wasActive = holdGesture.isActive();
        holdGesture.reset();
        if (wasActive) setSpeed(normalPlaybackSpeed, true);
        if (holdSpeedHint != null) holdSpeedHint.setVisibility(GONE);
    }

    private boolean touches(View child, MotionEvent event) {
        if (child == null || !child.isShown()) return false;
        Rect bounds = new Rect();
        return child.getGlobalVisibleRect(bounds)
                && bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            stopTemporarySpeed();
            suppressGestureUntilUp = false;
            pressDownTime = event.getDownTime();
            pressX = event.getX();
            pressY = event.getY();
            boolean eligible = mCurrentState == CURRENT_STATE_PLAYING && !mLockCurScreen
                    && event.getPointerCount() == 1 && !touches(mTopContainer, event)
                    && !touches(mBottomContainer, event) && !touches(mLockScreen, event)
                    && !touches(mStartButton, event);
            holdGesture.down(pressX, pressY, eligible);
            if (eligible) postDelayed(activateHoldSpeed, ViewConfiguration.getLongPressTimeout());
        } else if (action == MotionEvent.ACTION_MOVE) {
            holdGesture.move(event.getX(), event.getY(), event.getPointerCount(),
                    ViewConfiguration.get(getContext()).getScaledTouchSlop());
            if (!holdGesture.isPending()) removeCallbacks(activateHoldSpeed);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            stopTemporarySpeed();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = suppressGestureUntilUp;
            stopTemporarySpeed();
            suppressGestureUntilUp = false;
            if (consumed) {
                startDismissControlViewTimer();
                return true;
            }
        }
        if (suppressGestureUntilUp) return true;
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        return mLockCurScreen || super.onTouch(view, event);
    }

    private void updateBottomPlayPause() {
        if (bottomPlayPause == null) return;
        boolean playing = isActivelyPlaying();
        bottomPlayPause.setImageResource(playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
        bottomPlayPause.setContentDescription(playing ? "暂停" : "播放");
        bottomPlayPause.setEnabled(!mLockCurScreen);
    }

    public boolean unlockControlsIfLocked() {
        if (!mLockCurScreen) return false;
        lockTouchLogic();
        mLockScreen.setContentDescription("锁定控制");
        resolveUIState(mCurrentState);
        updateBottomPlayPause();
        return true;
    }

    @Override
    protected void setViewShowState(View view, int visibility) {
        // 本应用通过 Activity 横竖屏切换而非 GSY 的全屏克隆，锁屏在两种方向均可用。
        if (view != null && view == mLockScreen) {
            super.setViewShowState(view, mHadPlay ? VISIBLE : GONE);
            return;
        }
        if (mLockCurScreen && (view == mTopContainer || view == mBottomContainer || view == mStartButton)) {
            visibility = GONE;
        }
        super.setViewShowState(view, visibility);
    }

    @Override
    protected void setStateAndUi(int state) {
        if (state == CURRENT_STATE_PAUSE || state == CURRENT_STATE_NORMAL
                || state == CURRENT_STATE_AUTO_COMPLETE || state == CURRENT_STATE_ERROR) stopTemporarySpeed();
        super.setStateAndUi(state);
        updateBottomPlayPause();
    }

    @Override
    protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateStatusVisibility();
    }

    private void updateStatusVisibility() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        View status = findViewById(R.id.layout_status_info);
        View divider = findViewById(R.id.statusDivider);
        if (status != null) status.setVisibility(landscape ? VISIBLE : GONE);
        if (divider != null) divider.setVisibility(landscape ? VISIBLE : GONE);
    }

    /**
     * View 挂载到窗口时启动定时器
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startClockTimer();
    }

    /**
     * View 销毁离开窗口时停止定时器，防止内存泄漏
     */
    @Override
    protected void onDetachedFromWindow() {
        stopTemporarySpeed();
        removeCallbacks(dismissSeekHint);
        super.onDetachedFromWindow();
        stopClockTimer();
    }

    private void startClockTimer() {
        stopClockTimer();
        mClockHandler.post(mClockRunnable);
    }

    private void stopClockTimer() {
        mClockHandler.removeCallbacks(mClockRunnable);
    }


    @Override
    public int getLayoutId() {
        return R.layout.video_layout_preview;
    }

    private void setBatteryAndTime() {
        if (batteryTextView == null || timeTextView == null || getContext() == null) {
            return;
        }

        // 获取电量与充电状态
        Intent batteryStatus = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            int batteryPct = (scale > 0) ? (level * 100 / scale) : 100;
            batteryTextView.setText(batteryPct + "%" + (isCharging ? " ⚡" : ""));
        }

        // 规范为 HH:mm:ss
        timeTextView.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
    }

    private void resolveTypeUI() {
        if (!mHadPlay) return;
        if (mType == 1) {
            mMoreScale.setText("16:9");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_16_9);
        } else if (mType == 2) {
            mMoreScale.setText("4:3");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_4_3);
        } else if (mType == 3) {
            mMoreScale.setText("全屏");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_FULL);
        } else if (mType == 4) {
            mMoreScale.setText("拉伸");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_MATCH_FULL);
        } else if (mType == 0) {
            mMoreScale.setText("默认");
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
        }
        changeTextureViewShowType();
        if (mTextureView != null) mTextureView.requestLayout();
    }

    public void forwardOrRewind(long time) {
        if (mLockCurScreen || !mHadPlay || getDuration() <= 0) return;
        long duration = getDuration();
        mSeekTimePosition = PlaybackUtil.seekPosition(getCurrentPositionWhenPlaying(), time, duration);
        getGSYVideoManager().seekTo(mSeekTimePosition);
        refreshSubtitleAfterSeek(mSeekTimePosition);
        showProgressDialog(time, CommonUtil.stringForTime(mSeekTimePosition), mSeekTimePosition,
                CommonUtil.stringForTime(duration), duration);
        removeCallbacks(dismissSeekHint);
        postDelayed(dismissSeekHint, 700);
    }

    public void playNext(String url, String title) {
        setUp(url, mCache, null, title, true);
        mTitleTextView.setText(title);
        startPlayLogic();
    }

    @Override
    public void touchDoubleUp(MotionEvent event) {
        if (mLockCurScreen || holdGesture.isActive() || !mHadPlay) return;
        float x = event.getX();
        float width = mTextureViewContainer.getWidth();
        if (width <= 0) return;
        if (x < width / 3f) {
            forwardOrRewind(-forwardRewindIncrementMs);
        } else if (x > width * 2f / 3f) {
            forwardOrRewind(forwardRewindIncrementMs);
        } else {
            clickStartIcon();
        }
    }
}
