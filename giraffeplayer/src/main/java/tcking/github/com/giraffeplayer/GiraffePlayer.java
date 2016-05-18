package tcking.github.com.giraffeplayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by tcking on 15/10/27.
 * enpended by LinGuanHong on 16/5/17
 *
 */

public class GiraffePlayer {
    /**
     * 可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
     */
    public static final String SCALETYPE_FITPARENT="fitParent";
    /**
     * 可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
     */
    public static final String SCALETYPE_FILLPARENT="fillParent";
    /**
     * 将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
     */
    public static final String SCALETYPE_WRAPCONTENT="wrapContent";
    /**
     * 不剪裁,非等比例拉伸画面填满整个View
     */
    public static final String SCALETYPE_FITXY="fitXY";
    /**
     * 不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
     */
    public static final String SCALETYPE_16_9="16:9";
    /**
     * 不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
     */
    public static final String SCALETYPE_4_3="4:3";

    private static final int MESSAGE_SHOW_PROGRESS = 1;
    private static final int MESSAGE_FADE_OUT = 2;
    private static final int MESSAGE_SEEK_NEW_POSITION = 3;
    private static final int MESSAGE_HIDE_CENTER_BOX = 4;
    private static final int MESSAGE_RESTART_PLAY = 5;
    private static final int MESSAGE_LOCK_HIDE = 6;
    private final Activity activity;
    private IjkVideoView videoView;
    private SeekBar seekBar;
    private AudioManager audioManager;
    private int mMaxVolume;
    private String url;
    private Query $;
    private int STATUS_ERROR=-1;
    private int STATUS_IDLE=0;
    private int STATUS_LOADING=1;
    private int STATUS_PLAYING=2;
    private int STATUS_PAUSE=3;
    private int STATUS_COMPLETED=4;
    private long pauseTime;
    private int status=STATUS_IDLE;
    private boolean isLive = true;//是否为直播
    private OrientationEventListener orientationEventListener;
    private int initHeight;
    private int defaultTimeout=3000;
    private int screenWidthPixels;

    private final View.OnClickListener onClickListener=new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.app_video_fullscreen) {
                if (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                updateFullScreenButton();
            } else if (v.getId() == R.id.app_video_play) {
                if(!isNetErr){
                    doPauseResume();
                    show(defaultTimeout);
                    $.id(R.id.app_video_loading).isVisible();
                }else{
                    Toast.makeText(activity,"网络连接失败，请检查您的网路",Toast.LENGTH_SHORT).show();
                    $.id(R.id.app_video_loading).visible();
                }
            }else if (v.getId() == R.id.app_video_replay_icon) { /** 断网再次点击在这里唤醒 */
                /**  */
                videoView.start();
                doPauseResume();
            } else if(v.getId() == R.id.lock){
                updateLockIcon();
            }

            /*else if (v.getId() == R.id.app_video_finish) {
                if (!fullScreenOnly && !portrait) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    activity.finish();
                }
            }*/
        }
    };
    private boolean isShowing;
    private boolean portrait;
    private float brightness=-1;
    private int volume=-1;
    private long newPosition = -1;
    private long defaultRetryTime=5000;

    /**
     * try to play when error(only for live video)
     * @param defaultRetryTime millisecond,0 will stop retry,default is 5000 millisecond
     */
    public void setDefaultRetryTime(long defaultRetryTime) {
        this.defaultRetryTime = defaultRetryTime;
    }

    private int currentPosition;
    private boolean fullScreenOnly;

    public void setTitle(CharSequence title) {
        $.id(R.id.app_video_title).text(title);
    }


    private void doPauseResume() {
        if (status==STATUS_COMPLETED) {
            $.id(R.id.app_video_replay).gone();
            videoView.seekTo(0);
            videoView.start();
        } else if (videoView.isPlaying()) {
            statusChange(STATUS_PAUSE);
            videoView.pause();
        } else {
            videoView.start();
        }
        updatePausePlay();
    }

    private void updatePausePlay() {
        if (videoView.isPlaying()) {
            $.id(R.id.app_video_play).image(R.drawable.ic_stop_white_24dp);
        } else {
            $.id(R.id.app_video_play).image(R.drawable.ic_play_arrow_white_24dp);
        }
    }

    private boolean isLock = false;
    private void updateLockIcon() {
        if (isLock) {
            isLock = false;
            $.id(R.id.lock).image(R.drawable.ic_lock_circle_normal_o);
        } else { /** 锁屏 */
            isLock = true;
            $.id(R.id.lock).image(R.drawable.ic_locked_circle_normal_o);
            hideAll(true);
            /** 延时隐藏锁屏按钮 */
            handler.sendEmptyMessageDelayed(MESSAGE_LOCK_HIDE, 800);
        }
    }

    /**
     * 显示全部的控件
     * @param timeout
     */
    public void show(int timeout) {
        /** 锁屏判断 */
        if(isLock) {
            /** 只显示锁屏按钮,显示的状态下再延时隐藏 */
            if($.id(R.id.lock).Fan()){
                handler.sendEmptyMessageDelayed(MESSAGE_LOCK_HIDE, 2000);
            }
            return;
        }

        if (!isShowing) {
            $.id(R.id.lock).visible();
            $.id(R.id.app_video_top_box).visible();
            if (!isLive) {
                showBottomControl(true,false);
            }
            if (!fullScreenOnly) {
                $.id(R.id.app_video_fullscreen).visible();
            }
            isShowing = true;
        }
        updatePausePlay();
        handler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
        handler.removeMessages(MESSAGE_FADE_OUT);
        if (timeout != 0) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_FADE_OUT), timeout);
        }
    }

    private void showBottomControl(boolean show,boolean delayLock) {
        if(!delayLock)
            $.id(R.id.lock).visibility(show ? View.VISIBLE : View.GONE);

        $.id(R.id.app_video_play).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_currentTime).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_endTime).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_seekBar).visibility(show ? View.VISIBLE : View.GONE);
    }

    private long duration;
    private boolean instantSeeking;
    private boolean isDragging;
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {

        /** onProgressChanged */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser)
                return;
            $.id(R.id.app_video_status).gone();//移动时隐藏掉状态image
            int newPosition = (int) ((duration * progress*1.0) / 1000);
            String time = generateTime(newPosition);
            if (instantSeeking){
                videoView.seekTo(newPosition);
            }
            $.id(R.id.app_video_currentTime).text(time);
        }

        /** 该方法拖动进度条开始拖动的时候调用。 */
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isDragging = true;
            show(3600000);
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (instantSeeking){
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
        }

        /** 该方法拖动进度条停止拖动的时候调用 */
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (!instantSeeking){
                videoView.seekTo((int) ((duration * seekBar.getProgress()*1.0) / 1000));
            }
            show(defaultTimeout);
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            isDragging = false;
            handler.sendEmptyMessageDelayed(MESSAGE_SHOW_PROGRESS, 1000);
        }
    };

    @SuppressWarnings("HandlerLeak")
    private Handler handler=new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_FADE_OUT:
                    hide(false);
                    break;
                case MESSAGE_LOCK_HIDE: /** 延时隐藏锁屏按钮 */
                    $.id(R.id.lock).gone();
                    break;
                case MESSAGE_HIDE_CENTER_BOX:
                    $.id(R.id.app_video_volume_box).gone();
                    $.id(R.id.app_video_brightness_box).gone();
                    $.id(R.id.app_video_fastForward_box).gone();
                    break;
                case MESSAGE_SEEK_NEW_POSITION:
                    if (!isLive && newPosition >= 0) {
                        videoView.seekTo((int) newPosition);
                        newPosition = -1;
                    }
                    break;
                case MESSAGE_SHOW_PROGRESS:
                    long pos = setProgress();
                    if (!isDragging && isShowing) {
                        msg = obtainMessage(MESSAGE_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                        updatePausePlay();
                    }
                    break;
                case MESSAGE_RESTART_PLAY:
                    play(url);
                    break;
            }
        }
    };

    /** 下面在改变 view 的容器的时候，记得对应修改 layoutParam */
    private View view;
    private LinearLayout containerView;
    private RelativeLayout fullContainerView;
    public GiraffePlayer(
            final Activity activity,
            final LinearLayout containerView,
            final String url
    ) {
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        this.activity=activity;
        this.containerView = containerView;
        this.view = LayoutInflater.from(activity).inflate(R.layout.giraffe_player,containerView,false);
        containerView.addView(view);
        commonInit(url);
    }

    public GiraffePlayer(
            final Activity activity,
            final LinearLayout containerView,
            final RelativeLayout fullContainerView,
            final String url
    ) {
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        this.activity=activity;
        this.containerView = containerView;
        this.fullContainerView = fullContainerView;
        this.view = LayoutInflater.from(activity).inflate(R.layout.giraffe_player,containerView,false);
        containerView.addView(view);
        commonInit(url);
    }

    private void commonInit(final String url){
        /** 初始化高度 */
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,250*3);
        view.setLayoutParams(lp);

        view.findViewById(R.id.startPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(url);
                view.findViewById(R.id.startPlay).setVisibility(View.GONE);
            }
        });
        screenWidthPixels = activity.getResources().getDisplayMetrics().widthPixels;
        $=new Query(activity);
        videoView = (IjkVideoView) view.findViewById(R.id.video_view);
        videoView.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer mp) {
                if (duration - videoView.getCurrentPosition() <= 10) {
                    Log.d("zzzzz", "duration-videoView.getCurrentPosition() " + (duration - videoView.getCurrentPosition()));
                    statusChange(STATUS_COMPLETED);
                } else {
                    statusChange(STATUS_PAUSE);
                }
            }
        });
        videoView.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                statusChange(STATUS_ERROR);
                return true;
            }
        });
        videoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                isLive = !videoView.canPause();
            }
        });
        videoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {

                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START: /** 加载中 */
                        isLoading = true;
                        statusChange(STATUS_LOADING);
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        isLoading = false;
                        statusChange(STATUS_PLAYING);
                        break;
                    case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                        isLoading = false;
                        //显示 下载速度
//                        Toaster.show("download rate:" + extra);
                        break;
                    case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        isLoading = false;
                        statusChange(STATUS_PLAYING);
                        break;
                }
                return false;
            }
        });

        seekBar = (SeekBar) view.findViewById(R.id.app_video_seekBar);
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(mSeekListener);

        $.id(R.id.app_video_play).clicked(onClickListener);
        $.id(R.id.app_video_fullscreen).clicked(onClickListener);
        //$.id(R.id.app_video_finish).clicked(onClickListener);
        $.id(R.id.lock).clicked(onClickListener);
        $.id(R.id.app_video_replay_icon).clicked(onClickListener);

        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final GestureDetector gestureDetector = new GestureDetector(activity, new PlayerGestureListener());

        View liveBox = view.findViewById(R.id.app_video_box);
        liveBox.setClickable(true);
        liveBox.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (gestureDetector.onTouchEvent(motionEvent))
                    return true;

                // 处理手势结束
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                        endGesture();
                        break;
                }

                return false;
            }
        });

        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        activity.registerReceiver(new NetWorkChangeBroadcastReceiver(), mFilter);

        orientationEventListener = new OrientationEventListener(activity) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 0 && orientation <= 30 || orientation >= 330 || (orientation >= 150 && orientation <= 210)) {
                    //竖屏
                    if (portrait) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                } else if ((orientation >= 90 && orientation <= 120) || (orientation >= 240 && orientation <= 300)) {
                    if (!portrait) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                }
            }
        };
        if (fullScreenOnly) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        portrait=getScreenOrientation()==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        initHeight=view.findViewById(R.id.app_video_box).getLayoutParams().height;
        hideAll(false);
    }

    /**
     * 手势结束
     */
    private void endGesture() {
        volume = -1;
        brightness = -1f;
        if (newPosition >= 0) {
            handler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
            handler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
        }
        handler.removeMessages(MESSAGE_HIDE_CENTER_BOX);
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_CENTER_BOX, 500);
    }

    private void statusChange(int newStatus) {
        status=newStatus;
        if (!isLive && newStatus==STATUS_COMPLETED) { /** 断网进入这里 */
            hideAll(false);
            $.id(R.id.app_video_replay).visible();
        }else if (newStatus == STATUS_ERROR) {
            hideAll(false);
            if (isLive) {
                showStatus("播放出了点小问题,正在重试...");
                if (defaultRetryTime>0) {
                    handler.sendEmptyMessageDelayed(MESSAGE_RESTART_PLAY, defaultRetryTime);
                }
            } else {
                showStatus("不能播放此视频");
            }
        } else if(newStatus==STATUS_LOADING){
            hideAll(false);
            $.id(R.id.app_video_loading).visible();
        } else if (newStatus == STATUS_PLAYING) {
            /** 断网后再拉进度条也会进入这里，此时点击播放就是 compete 状态 */
            Log.d("zzzzz","STATUS_PLAYING");
            hideAll(false);
        }
    }

    private boolean isLoading = false;
    private void hideAll(boolean delayLock) {
        $.id(R.id.app_video_replay).gone();
        $.id(R.id.app_video_top_box).gone();
        $.id(R.id.app_video_loading).gone();
        $.id(R.id.app_video_fullscreen).invisible();
        $.id(R.id.app_video_status).gone();
        showBottomControl(false, delayLock);
    }

    public void onPause() {
        pauseTime=System.currentTimeMillis();
        show(0);//把系统状态栏显示出来
        if (status==STATUS_PLAYING) {
            videoView.pause();
            if (!isLive) {
                /** 保存暂停的位置 */
                currentPosition = videoView.getCurrentPosition();
            }
        }
    }

    public void onResume() {
        pauseTime=0;
        if (status==STATUS_PLAYING) {
            if (isLive) {
                videoView.seekTo(0);
            } else {
                /*if (currentPosition>0) {
                    videoView.seekTo(currentPosition);
                }*/
            }
            doPauseResume();
            //videoView.start();
        }
    }

    /** second 是否采用第二种全屏方式 */
    public void onConfigurationChanged(final Configuration newConfig,boolean isSecondFullWay) {
        portrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        if(isSecondFullWay){
            if(fullContainerView==null){
                Toast.makeText(activity,"第二种的全屏方式必须传入 fullContainerView ！",Toast.LENGTH_SHORT).show();
                return;
            }
            if(portrait){ /** 竖屏 */
                fullContainerView.removeView(view);
                containerView.addView(view);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,250*3);
                view.setLayoutParams(lp);
            }else{
                containerView.removeView(view);
                fullContainerView.addView(view);
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                view.setLayoutParams(lp);
            }
            /** 取反 */
            setFullScreen(!portrait);
        }else {
            doOnConfigurationChanged(portrait);
        }
    }


    private void doOnConfigurationChanged(final boolean portrait) {
        if (videoView != null && !fullScreenOnly) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("zzzzz","portrait "+portrait);
                    tryFullScreen(!portrait);
                    if (portrait) {
                        $.id(R.id.app_video_box).height(initHeight, false);
                    } else {
                        $.id(R.id.app_video_box).height(activity.getResources().getDisplayMetrics().heightPixels, false);
                    }
                    updateFullScreenButton();
                }
            });
            orientationEventListener.enable();
        }
    }

    private void tryFullScreen(boolean fullScreen) {
        if (activity instanceof AppCompatActivity) {
            ActionBar supportActionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (supportActionBar != null) {
                if (fullScreen) {
                    supportActionBar.hide();
                } else {
                    supportActionBar.show();
                }
            }
        }
        setFullScreen(fullScreen);
    }

    private void setFullScreen(boolean fullScreen) {
        WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
        if (fullScreen) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            activity.getWindow().setAttributes(attrs);
            Log.d("zzzzz","activity.getWindow().setAttributes(attrs);");
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } else {
            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().setAttributes(attrs);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
    }

    public void onDestroy() {
        orientationEventListener.disable();
        handler.removeMessages(MESSAGE_RESTART_PLAY);
        handler.removeMessages(MESSAGE_FADE_OUT);
        handler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
        videoView.stopPlayback();
    }

    private void showStatus(String statusText) {
        $.id(R.id.app_video_status).visible();
        $.id(R.id.app_video_status_text).text(statusText);
    }

    public void play(String url) {
        this.url = url;
        $.id(R.id.app_video_loading).visible();
        videoView.setVideoPath(url);
        videoView.start();
    }

    private String generateTime(long time) {
        int totalSeconds = (int) (time / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    private int getScreenOrientation() {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {
        if (volume == -1) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volume < 0)
                volume = 0;
        }
        hide(true);

        int index = (int) (percent * mMaxVolume) + volume;
        if (index > mMaxVolume)
            index = mMaxVolume;
        else if (index < 0)
            index = 0;

        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

        // 变更进度条
        int i = (int) (index * 1.0 / mMaxVolume * 100);
        String s = i + "%";
        if (i == 0) {
            s = "关闭";
        }
        // 显示
        $.id(R.id.app_video_volume_icon).image(i==0?R.drawable.ic_volume_off_white_36dp:R.drawable.ic_volume_up_white_36dp);
        $.id(R.id.app_video_brightness_box).gone();
        $.id(R.id.app_video_volume_box).visible();
        $.id(R.id.app_video_volume_box).visible();
        $.id(R.id.app_video_volume).text(s).visible();
    }

    private void onProgressSlide(float percent) {
        long position = videoView.getCurrentPosition();
        long duration = videoView.getDuration();
        long deltaMax = Math.min(100 * 1000, duration - position);
        long delta = (long) (deltaMax * percent);


        newPosition = delta + position;
        if (newPosition > duration) {
            newPosition = duration;
        } else if (newPosition <= 0) {
            newPosition=0;
            delta=-position;
        }
        int showDelta = (int) delta / 1000;
        if (showDelta != 0) {
            $.id(R.id.app_video_fastForward_box).visible();
            String text = showDelta > 0 ? ("+" + showDelta) : "" + showDelta;
            $.id(R.id.app_video_fastForward).text(text + "s");
            $.id(R.id.app_video_fastForward_target).text(generateTime(newPosition)+"/");
            $.id(R.id.app_video_fastForward_all).text(generateTime(duration));
        }
    }

    /**
     * 滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        if (brightness < 0) {
            brightness = activity.getWindow().getAttributes().screenBrightness;
            if (brightness <= 0.00f){
                brightness = 0.50f;
            }else if (brightness < 0.01f){
                brightness = 0.01f;
            }
        }
        Log.d(this.getClass().getSimpleName(),"brightness:"+brightness+",percent:"+ percent);
        $.id(R.id.app_video_brightness_box).visible();
        WindowManager.LayoutParams lpa = activity.getWindow().getAttributes();
        lpa.screenBrightness = brightness + percent;
        if (lpa.screenBrightness > 1.0f){
            lpa.screenBrightness = 1.0f;
        }else if (lpa.screenBrightness < 0.01f){
            lpa.screenBrightness = 0.01f;
        }
        $.id(R.id.app_video_brightness).text(((int) (lpa.screenBrightness * 100))+"%");
        activity.getWindow().setAttributes(lpa);

    }

    private long setProgress() {
        if (isDragging){
            return 0;
        }

        long position = videoView.getCurrentPosition();
        long duration = videoView.getDuration();
        if (seekBar != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                seekBar.setProgress((int) pos);
            }
            int percent = videoView.getBufferPercentage();
            seekBar.setSecondaryProgress(percent * 10);
        }

        this.duration = duration;
        //Log.d("zzzzz","duration "+duration);
        $.id(R.id.app_video_currentTime).text(generateTime(position));
        $.id(R.id.app_video_endTime).text(generateTime(this.duration));
        return position;
    }

    public void hide(boolean force) {
        if (force || isShowing) {
            Log.d("zzzzz","force || isShowing");
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            showBottomControl(false,false); /** 这里不用延时隐藏 */
            $.id(R.id.app_video_top_box).gone();
            $.id(R.id.app_video_fullscreen).invisible();
            isShowing = false;
        }
    }

    private void updateFullScreenButton() {
        if (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            $.id(R.id.app_video_fullscreen).image(R.drawable.ic_fullscreen_exit_white_36dp);
        } else {
            $.id(R.id.app_video_fullscreen).image(R.drawable.ic_fullscreen_white_24dp);
        }
    }

    public void setFullScreenOnly(boolean fullScreenOnly) {
        this.fullScreenOnly = fullScreenOnly;
        tryFullScreen(fullScreenOnly);
        if (fullScreenOnly) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    /**
     * <pre>
     *     fitParent:可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
     *     fillParent:可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
     *     wrapContent:将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
     *     fitXY:不剪裁,非等比例拉伸画面填满整个View
     *     16:9:不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
     *     4:3:不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
     * </pre>
     * @param scaleType
     */
    public void setScaleType(String scaleType) {
        if (SCALETYPE_FITPARENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_FIT_PARENT);
        }else if (SCALETYPE_FILLPARENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_FILL_PARENT);
        }else if (SCALETYPE_WRAPCONTENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_WRAP_CONTENT);
        }else if (SCALETYPE_FITXY.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_MATCH_PARENT);
        }else if (SCALETYPE_16_9.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_16_9_FIT_PARENT);
        }else if (SCALETYPE_4_3.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_4_3_FIT_PARENT);
        }
    }

    /**
     * 是否显示左上导航图标(一般有actionbar or appToolbar时需要隐藏)
     * @param show
     */
    public void setShowNavIcon(boolean show) {
        //$.id(R.id.app_video_finish).visibility(show ? View.VISIBLE : View.GONE);
    }


    class Query {
        private final Activity activity;
        private View Qview;

        public Query(Activity activity) {
            this.activity=activity;
        }

        public Query id(int id) {
            Qview = view.findViewById(id);
            return this;
        }

        public Query image(int resId) {
            if (Qview instanceof ImageView) {
                ((ImageView) Qview).setImageResource(resId);
            }
            return this;
        }

        public Query visible() {
            if (Qview != null) {
                Qview.setVisibility(View.VISIBLE);
            }
            return this;
        }

        public Query gone() {
            if (Qview != null) {
                Qview.setVisibility(View.GONE);
            }
            return this;
        }

        public  void isVisible() {
            if (Qview != null) {
                if(Qview.getVisibility() == View.VISIBLE){
                    Qview.setVisibility(View.GONE);
                }
            }
        }

        public boolean Fan() {
            if (Qview != null) {
                if(Qview.getVisibility() == View.VISIBLE){
                    Qview.setVisibility(View.GONE);
                }else{
                    Qview.setVisibility(View.VISIBLE);
                    return true;
                }
            }
            return false;
        }

        public Query invisible() {
            if (Qview != null) {
                Qview.setVisibility(View.INVISIBLE);
            }
            return this;
        }

        public Query clicked(View.OnClickListener handler) {
            if (Qview != null) {
                Qview.setOnClickListener(handler);
            }
            return this;
        }

        public Query text(CharSequence text) {
            if (Qview!=null && Qview instanceof TextView) {
                ((TextView) Qview).setText(text);
            }
            return this;
        }

        public Query visibility(int visible) {
            if (Qview != null) {
                Qview.setVisibility(visible);
            }
            return this;
        }

        private void size(boolean width, int n, boolean dip){

            if(Qview != null){

                ViewGroup.LayoutParams lp = Qview.getLayoutParams();


                if(n > 0 && dip){
                    n = dip2pixel(activity, n);
                }

                if(width){
                    lp.width = n;
                }else{
                    lp.height = n;
                }

                Qview.setLayoutParams(lp);

            }

        }

        public void height(int height, boolean dip) {
            size(false,height,dip);
        }

        public int dip2pixel(Context context, float n){
            int value = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, context.getResources().getDisplayMetrics());
            return value;
        }

        public float pixel2dip(Context context, float n){
            Resources resources = context.getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float dp = n / (metrics.densityDpi / 160f);
            return dp;

        }
    }

    public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
        private boolean firstTouch;
        private boolean volumeControl;
        private boolean toSeek;

        /**
         * 双击
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            videoView.toggleAspectRatio();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            firstTouch = true;
            return super.onDown(e);
        }

        /**
         * 滑动
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            float mOldX = e1.getX(), mOldY = e1.getY();
            float deltaY = mOldY - e2.getY();
            float deltaX = mOldX - e2.getX();
            if (firstTouch) {
                toSeek = Math.abs(distanceX) >= Math.abs(distanceY);
                volumeControl=mOldX > screenWidthPixels * 0.5f;
                firstTouch = false;
            }

            if (toSeek) {
                if (!isLive) {
                    onProgressSlide(-deltaX / videoView.getWidth());
                }
            } else {
                float percent = deltaY / videoView.getHeight();
                if (volumeControl) {
                    onVolumeSlide(percent);
                } else {
                    onBrightnessSlide(percent);
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (isShowing) {
                hide(false);
            } else {
                show(defaultTimeout);
            }
            return true;
        }
    }

    /** 断网广播接收 */
    private boolean isNetErr = false;
    public class NetWorkChangeBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {


            if(((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo()==null){
                /** 断网 */
                isNetErr = true;
                if(isLoading){
                    if(videoView.canPause()){
                        videoView.pause();
                    }
                }
                if(videoView.isPlaying()){ /** 还一种情况是还在缓冲就断网了 */
                    if(videoView.canPause()){
                        Log.d("zzzzz","net change videoView.pause();");
                        videoView.pause();
                    }
                }
            }else{ /** 恢复 */
                if(isNetErr){
                    isNetErr = false;
                    Log.d("zzzzz","net change doPauseResume");
                    $.id(R.id.app_video_loading).isVisible();
                    doPauseResume();
                }
            }
        }
    }
}
