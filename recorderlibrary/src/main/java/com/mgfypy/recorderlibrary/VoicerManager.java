package com.mgfypy.recorderlibrary;

import android.Manifest;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.mgfypy.recorderlibrary.audio.AudioRecorder;
import com.mgfypy.recorderlibrary.audio.RxAudioPlayer;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.io.File;

import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * 声音模块
 * Created by jianglei on 16-4-12.
 */
public class VoicerManager implements VoiceInputManager.EventListener, AudioRecorder.OnErrorListener {
    private static final String TAG = "VoicerManager";

    public interface RecordCallback {
        /**
         * 准备录音
         */
        public void onRecordPreparing();

        /**
         * 录音已准备
         */
        public void onRecordPrepared();

        /**
         * 录音结束
         */
        public void onRecordStopped(int duration);

        /**
         * 已录文件
         *
         * @param audioFile
         * @param duration
         */
        public void onRecordSend(File audioFile, int duration);

        /**
         * 采集音量变化
         *
         * @param level
         */
        public void onRecordAmplitudeChanged(int level);

        /**
         * 录音剩余时间
         *
         * @param second
         */
        public void onRecordExpireCountdown(int second);

        public void onRecordError(int error);

        /**
         * 获取两秒钟录音文件
         * */
        public void onGetVoicerManagerAudioFile(File file);

        /**
         * 记录录音时间
         * */
        public void onGetVoicerRecordTime(int time);

    }

    private static VoicerManager mInstance;
    private Context context;
    private VoiceInputManager mVoiceInputManager;

    private RecordCallback mRecordCallback;

    private RxAudioPlayer mRxAudioPlayer;

    public VoicerManager(Context context) {
        this.context = context;
        AudioRecorder audioRecorder = AudioRecorder.getInstance();
        audioRecorder.setOnErrorListener(this);
        mVoiceInputManager =
                new VoiceInputManager(audioRecorder, getDiskCacheDir(context),
                        this);

        mRxAudioPlayer = RxAudioPlayer.getInstance();
    }

    public static VoicerManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new VoicerManager(context);
        }
        return mInstance;
    }

    /**
     * 开始录音
     */
    public void startRecord(RecordCallback callback) {
        this.mRecordCallback = callback;
        this.mVoiceInputManager.reset();

        if (checkPermission()) {
            mVoiceInputManager.toggleOn();
        }
    }

    /**
     * 停止录音
     */
    public void stopRecord() {
        mVoiceInputManager.toggleOff();
    }

    /**
     * 播放录音
     */
    public void playRecord(final File file,VoiceCallBack.PlayVoiceCallBack callBack) {
        Log.i(TAG, "startPlay=" + file);
        if (file != null) {
            mRxAudioPlayer.play(file,callBack)
                    .subscribeOn(Schedulers.io())
                    .subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean aBoolean) {
                            //playRecord(file);
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    });
        }

    }

    /**
     * 停止播放录音
     * */
    public void stopPlayRecord(){
        mRxAudioPlayer.stopPlay();
    }


    public void playRecord(Context context, int audiRes) {
        mRxAudioPlayer.play(context, audiRes).toBlocking().value();
    }

    private boolean checkPermission() {
        boolean isPermissionsGranted = RxPermissions.getInstance(context.getApplicationContext())
                .isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                RxPermissions.getInstance(context.getApplicationContext())
                        .isGranted(Manifest.permission.RECORD_AUDIO);
        if (!isPermissionsGranted) {
            RxPermissions.getInstance(context.getApplicationContext())
                    .request(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO)
                    .subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean granted) {
                            // not record first time to request permission
                            if (granted) {
                                Log.i(TAG, "Permission granted");
                                if (mRecordCallback != null) {
                                    mRecordCallback.onRecordError(-1);
                                }
                            } else {
                                Log.i(TAG, "Permission not granted");
                                if (mRecordCallback != null) {
                                    mRecordCallback.onRecordError(-2);
                                }
                            }
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    });
            return false;
        }
        return true;
    }

    private File getDiskCacheDir(Context context) {
        //如果sd卡存在并且没有被移除
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            return context.getExternalCacheDir();
        }
        return context.getCacheDir();
    }

    @Override
    public void onPreparing() {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordPreparing();
        }
    }

    @Override
    public void onPrepared() {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordPrepared();
        }
    }

    @Override
    public void onStopped(int duration) {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordStopped(duration);
        }
    }

    @Override
    public void onSend(File file, int duration) {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordSend(file, duration);
        }
    }

    @Override
    public void onAmplitudeChanged(int level) {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordAmplitudeChanged(level);
        }
    }

    @Override
    public void onExpireCountdown(int second) {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordExpireCountdown(second);
        }
    }


    @Override
    public void onError(@AudioRecorder.Error int error) {
        if (mRecordCallback != null) {
            mRecordCallback.onRecordError(error);
        }
    }

    @Override
    public void onGetAudioFile(File audioFile) {
        if (mRecordCallback != null){
            mRecordCallback.onGetVoicerManagerAudioFile(audioFile);
        }
    }

    @Override
    public void onGetAudioRecordTime(int time) {
        if (mRecordCallback != null){
            mRecordCallback.onGetVoicerRecordTime(time);
        }
    }

}
