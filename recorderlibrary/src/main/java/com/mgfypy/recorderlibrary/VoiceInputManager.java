package com.mgfypy.recorderlibrary;

import android.media.MediaRecorder;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.mgfypy.recorderlibrary.audio.AudioRecorder;
import com.mgfypy.recorderlibrary.audio.RxAmplitude;
import com.mgfypy.recorderlibrary.state.VoiceInputState;

import java.io.File;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by Piasy{github.com/Piasy} on 16/2/29.
 */
public final class VoiceInputManager {
    private static final String TAG = "taohaibing";

    private static final int DEFAULT_MIN_AUDIO_LENGTH_SECONDS = 1;
    private static final int DEFAULT_MAX_AUDIO_LENGTH_SECONDS = 60;

    private static Action1<Throwable> sOnErrorLogger = new Action1<Throwable>() {
        @Override
        public void call(Throwable throwable) {
            throwable.printStackTrace();
        }
    };

    private AudioRecorder mAudioRecorder;
    private final File mAudioFilesDir;
    private EventListener mEventListener;

    private final int mMinAudioLengthSeconds;
    private final int mMaxAudioLengthSeconds;

    private File mAudioFile;

    private VoiceInputState mVoiceInputState;

    public VoiceInputManager(AudioRecorder audioRecorder, File audioFilesDir,
                             EventListener eventListener) {
        this(audioRecorder, audioFilesDir, eventListener, DEFAULT_MIN_AUDIO_LENGTH_SECONDS,
                DEFAULT_MAX_AUDIO_LENGTH_SECONDS);
    }

    public VoiceInputManager(AudioRecorder audioRecorder, File audioFilesDir,
                             EventListener eventListener, int minAudioLengthSeconds, int maxAudioLengthSeconds) {
        mAudioRecorder = audioRecorder;
        mAudioFilesDir = audioFilesDir;
        mEventListener = eventListener;
        mMinAudioLengthSeconds = minAudioLengthSeconds;
        mMaxAudioLengthSeconds = maxAudioLengthSeconds;
        mVoiceInputState = VoiceInputState.init();
    }

    public interface EventListener {
        @WorkerThread
        void onPreparing();

        @WorkerThread
        void onPrepared();

        @WorkerThread
        void onStopped(int duration);

        /**
         * This method is called in worker thread, and should send audio file sync.
         */
        @WorkerThread
        void onSend(File audioFile, int duration);

        @WorkerThread
        void onAmplitudeChanged(int level);

        @WorkerThread
        void onExpireCountdown(int second);

        @WorkerThread
        void onGetAudioFile(File audioFile);

        @WorkerThread
        void onGetAudioRecordTime(int time);
    }

    public void toggleOn() {
        Log.d(TAG, "toggleOn @ " + System.currentTimeMillis());
        addSubscribe(Observable.just(true)
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        pressed();
                        if (state() == VoiceInputState.STATE_PREPARING) {
                            startRecord();
                        }
                    }
                }, sOnErrorLogger));
    }

    private synchronized void pressed() {
        Log.d(TAG, "before pressed " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.pressed();
        Log.d(TAG, "after pressed " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private synchronized void elapsed() {
        Log.d(TAG, "before elapsed " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.elapsed();
        Log.d(TAG, "after elapsed " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private synchronized void released() {
        Log.d(TAG, "before released " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.released();
        Log.d(TAG, "after released " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private synchronized void quickReleased() {
        Log.d(TAG, "before quickReleased " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.quickReleased();
        Log.d(TAG, "after quickReleased " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private synchronized void timeout() {
        Log.d(TAG, "before timeout " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.timeout();
        Log.d(TAG, "after timeout " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private synchronized void resetState() {
        Log.d(TAG, "before resetState " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = VoiceInputState.init();
        Log.d(TAG, "after resetState " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    @VoiceInputState.State
    private synchronized int state() {
        return mVoiceInputState.state();
    }

    private void startRecord() {
        mEventListener.onPreparing();
        mAudioFile = new File(mAudioFilesDir.getAbsolutePath() +
                File.separator + System.currentTimeMillis() + ".file.m4a");
        boolean prepared = mAudioRecorder.prepareRecord(MediaRecorder.AudioSource.MIC,
                MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC, mAudioFile);
        if (!prepared) {
            reset();
            return;
        }

        mEventListener.onPrepared();
        boolean started = mAudioRecorder.startRecord();
        if (!started) {
            reset();
            return;
        }

        elapsed();
        if (state() == VoiceInputState.STATE_RECORDING) {
            addSubscribe(RxAmplitude.from(mAudioRecorder)
                    .subscribeOn(Schedulers.io())
                    .subscribe(new Action1<Integer>() {
                        @Override
                        public void call(Integer level) {
                            int progress = mAudioRecorder.progress();
                            if(progress == -1){
                                return;//录音时间不能为负数
                            }
                            mEventListener.onAmplitudeChanged(level);
                            Log.d(TAG, "startRecord @ " + progress);
                            if(progress == 1){
                                mEventListener.onGetAudioFile(mAudioFile);//录音一秒钟之后，将文件回传，判断是否可以录音
                            }
                            mEventListener.onGetAudioRecordTime(progress);//记录录音时间
                            if (progress >= mMaxAudioLengthSeconds - 3) {
                                mEventListener.onExpireCountdown(mMaxAudioLengthSeconds - progress);
                                if (progress == mMaxAudioLengthSeconds) {
                                    timeout();
                                    if (state() == VoiceInputState.STATE_STOPPING) {
                                        stopRecord();
                                    }
                                }
                            }
                        }
                    }, sOnErrorLogger));
        }
    }

    public void reset() {
        unSubscribeAll();
        resetState();
    }

    public void toggleOff() {
        Log.d(TAG, "toggleOff @ " + System.currentTimeMillis() + "@" + state());
        released();
        int state = state();
        if (state == VoiceInputState.STATE_STOPPING) {
            stopRecord();
        } else if (state == VoiceInputState.STATE_IDLE) {
            addSubscribe(Observable.just(true)
                    .subscribeOn(Schedulers.io())
                    .subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean aBoolean) {
                            mEventListener.onStopped(0);
                        }
                    }, sOnErrorLogger));
        }
    }

    private void stopRecord() {
        addSubscribe(Observable.just(true)
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        int seconds = mAudioRecorder.stopRecord();
                        mEventListener.onStopped(seconds);
                        if (seconds >= mMinAudioLengthSeconds) {
                            elapsed();
                            if (state() == VoiceInputState.STATE_SENDING) {
                                mEventListener.onSend(mAudioFile, seconds);
                                elapsed();
                                if (state() == VoiceInputState.STATE_PREPARING) {
                                    startRecord();
                                }
                            }
                            unSubscribeAll();
                        } else {
                            quickReleased();
                        }
                    }
                }, sOnErrorLogger));
    }

    private CompositeSubscription mCompositeSubscription;

    private void addSubscribe(Subscription subscription) {
        if (mCompositeSubscription == null || mCompositeSubscription.isUnsubscribed()) {
            mCompositeSubscription = new CompositeSubscription();
        }
        mCompositeSubscription.add(subscription);
    }

    private void unSubscribeAll() {
        if (mCompositeSubscription != null && !mCompositeSubscription.isUnsubscribed()) {
            mCompositeSubscription.unsubscribe();
        }
        mCompositeSubscription = null;
    }
}
