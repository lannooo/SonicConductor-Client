package com.lannooo.audiocenter.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.util.Log;

import com.lannooo.audiocenter.tool.AppUtil;

import java.io.File;
import java.util.concurrent.ExecutorService;

import io.netty.util.internal.StringUtil;

public abstract class AbstractAudioHandler implements AudioRecorder {
    private static final String TAG = "AudioHandler";

    protected final Context context;
    protected final ExecutorService executor;

    protected final boolean supportUnprocess;
    protected final File baseDir;
    protected AudioRecorder recorder;

    public AbstractAudioHandler(Context context, ExecutorService executor) {
        this.context = context;
        this.executor = executor;
        AudioManager audioManager = (AudioManager) context.getSystemService(
                Context.AUDIO_SERVICE);
        String v = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        Log.i(TAG, "Support unprocessed audio source?: " + v);
        this.supportUnprocess = !(StringUtil.isNullOrEmpty(v) || "false".equalsIgnoreCase(v));
        this.baseDir = getRecordingBaseDir();
    }

    public abstract void configure(String outputFile,
                                   int duration,
                                   boolean enableProcess,
                                   boolean isCustom,
                                   AudioEventListener listener);

    public File getBaseDir() {
        return baseDir;
    }

    private File getRecordingBaseDir() {
        File dir = AppUtil.getAppRecordingDir(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    protected int getAudioSource(boolean enableProcess) {
        int audioSource;
        if (enableProcess) {
            audioSource = MediaRecorder.AudioSource.MIC;
        } else if (supportUnprocess) {
            audioSource = MediaRecorder.AudioSource.UNPROCESSED;
            Log.d(TAG, "Use UNPROCESSED as audio source");
        } else {
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            Log.d(TAG, "Use VOICE_RECOGNITION as audio source");
        }
        return audioSource;
    }

    @Override
    public void start() {
        if (recorder != null) {
            recorder.start();
        }
    }

    @Override
    public void stop() {
        if (recorder != null) {
            recorder.stop();
        }
    }

    @Override
    public void pause() {
        if (recorder != null) {
            recorder.pause();
        }
    }

    @Override
    public void resume() {
        if (recorder != null) {
            recorder.resume();
        }
    }
}
