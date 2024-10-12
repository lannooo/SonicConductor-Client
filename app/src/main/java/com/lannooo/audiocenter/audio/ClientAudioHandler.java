package com.lannooo.audiocenter.audio;

import android.content.Context;
import android.util.Log;

import com.lannooo.audiocenter.tool.AppUtil;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

public class ClientAudioHandler extends AbstractAudioHandler {
    private static final String TAG = "ClientAudioHandler";

    private Channel remoteChannel;
    private String remoteKey;
    private final FileUploadManager fileUploadManager;

    public ClientAudioHandler(Context context, ExecutorService executor) {
        super(context, executor);
        this.fileUploadManager = new FileUploadManager();
    }

    @Override
    public void configureRecorder(String outputFile,
                                  int duration,
                                  boolean enableProcess,
                                  boolean isCustom,
                                  AudioEventListener listener) {
        // UNPROCESSED and VOICE_RECOGNITION type audio sources do not
        // employ 'AGC' or 'Noise Suppression', while DEFAULT or MIC do.
        int audioSource = getAudioSource(enableProcess);
        int durationMs = (duration == -1) ? -1 : duration * 1000;
        File audioFile = new File(baseDir, outputFile);
        Log.i(TAG, "Recording file: " + audioFile.getAbsolutePath() + ", duration: " + durationMs);
        if (isCustom) {
            recorder = new CustomAudioRecorder(this, audioFile, audioSource);
        } else {
            recorder = new SimpleAudioRecorder(this, audioFile, audioSource, durationMs);
        }
        recorder.setListener(listener);
    }

    @Override
    public void configurePlayer(String inputFile,
                                String type,
                                boolean enableLoop,
                                AudioEventListener listener) {
        int audioType = getAudioContentType(type);
        File audioFile = Paths.get(baseDir.getAbsolutePath(), "server", inputFile).toFile();
        Log.i(TAG, "Playback file: " + audioFile.getAbsolutePath() + ", type: " + type);
        player = new SimpleAudioPlayer(this, audioFile, audioType, enableLoop);
        player.setListener(listener);
    }

    @Override
    public UploadingFileItem writeUploadingFile(ChannelHandlerContext ctx, ByteBuf buf) {
        return fileUploadManager.writeChunk(Objects.requireNonNull(remoteKey), buf);
    }

    @Override
    public void addUploadingFile(ChannelHandlerContext ctx, Map<String, Object> data) {
        String savePath = getBaseDir().getAbsolutePath();
        // transform double to long
        long chunks = (long) (double) Objects.requireNonNull(data.get("chunks"));
        long length = (long) (double) Objects.requireNonNull(data.get("length"));
        String filename = (String) data.get("filepath");
        Log.i(TAG, "File upload request: " + filename + ", chunks: " + chunks + ", length: " + length);

        fileUploadManager.addTask(Objects.requireNonNull(remoteKey), savePath, filename, chunks, length);
    }

    public void cacheServerChannel(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String remoteKey = AppUtil.parseAddress(channel, true, true);
        String localKey = AppUtil.parseAddress(channel, false, true);
        String key = AppUtil.sha1Hex(remoteKey + localKey, 8);
        this.remoteChannel = channel;
        this.remoteKey = key;
    }

    public void clearServerChannel() {
        this.remoteChannel = null;
        this.remoteKey = null;
    }
}
