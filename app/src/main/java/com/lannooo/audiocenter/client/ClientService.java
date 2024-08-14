package com.lannooo.audiocenter.client;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.lannooo.audiocenter.R;
import com.lannooo.audiocenter.audio.ClientAudioHandler;
import com.lannooo.audiocenter.tool.MessageUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ClientService extends Service {
    public static final String TAG = "ClientService";

    private final IBinder binder = new ClientBinder();

    // for other IO operations
    private ExecutorService executor;

    // for configure and manage Netty client
    private Bootstrap bootstrap;
    private int port;   // remote server port
    private String ip;  // remote server IP: 192.168.57.86, 192.168.127.2

    // hold this channel for remote communication
    private Channel channel;

    // for Activity to listen to message events (such as display usage)
    private MessageListener listener;

    // for Audio Recording
    private ClientAudioHandler audioHandler;

    public ClientAudioHandler getAudioHandler() {
        return audioHandler;
    }

    public MessageListener getListener() {
        return listener;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Client Service start");
        // TODO maybe handle different intent actions (not supported currently)
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "ClientService onCreate");
        // check permission here again
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No RECORD_AUDIO permission in ClientService");
            stopSelf();
            return;
        }
        // For IO operations, use a separate thread pool
        initTaskExecutor();
        // initialize Netty Bootstrap
        initNettyBootstrap();
        // For audio recording, playback, etc
        initAudioFeatures();
        // start service as foreground
        startForegroundWithNotification();
    }

    private void initAudioFeatures() {
        audioHandler = new ClientAudioHandler(this, this.executor);
    }

    @Override
    public void onDestroy() {
        // TODO release related resources
        if (audioHandler != null) {
            audioHandler.stop();
        }
        if (executor != null) {
            executor.shutdown();
        }
        try {
            disconnect(false);
        } catch (InterruptedException e) {
            // ignore this
            Log.e(TAG, "Client channel closing failed: " + e.getMessage());
        }
    }

    private void initTaskExecutor() {
        // TODO put this in Application class?
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    private void initNettyBootstrap() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(group)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(4096, 8, 4, 0, 0));
                        pipeline.addLast(new ClientEncoder());
                        pipeline.addLast(new ClientDecoder());
                        pipeline.addLast(new ClientHandler(ClientService.this));
                    }
                });
        Log.i(TAG, "Netty Bootstrap initialized");
    }

    private void startForegroundWithNotification() {
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = "Tcp Client Service";
            String channelId = getPackageName();
            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLightColor(Color.BLUE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);

            notification = new NotificationCompat.Builder(this, channelId)
                    .setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setContentTitle("Tcp Client Service")
                    .setContentText("Tcp Client Service is running in the foreground")
                    .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();

        } else {
            notification = new Notification();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            startForeground(1, notification, foregroundServiceType);
        } else {
            startForeground(1, notification);
        }
        Log.i(TAG, "ClientService started as foreground");
    }

    private boolean connect() throws InterruptedException {
        try {
            ChannelFuture channelFuture = bootstrap.connect(new InetSocketAddress(ip, port));
            channel = channelFuture.sync().channel();
            Log.i(TAG, "Client connected to server: " + ip + ":" + port);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Client connect failed: " + e.getMessage(), e);
            return false;
        }
    }

    private void disconnect(boolean block) throws InterruptedException {
        // TODO cancel running tasks maybe
        if (channel != null) {
            // shutdown client Netty Socket Channel
            ChannelFuture future = channel.close();
            if (block) {
                future.sync();
                Log.i(TAG, "Client channel closed");
            } else {
                Log.i(TAG, "Client channel closing...");
            }
        } else {
            Log.i(TAG, "Client channel is null, skip disconnect");
        }
    }

    private boolean isChannelReady() {
        return channel != null && channel.isActive();
    }

    private void sendRegisterMessage() throws InterruptedException {
        if (isChannelReady()) {
            // write a message Object to server
            Message message = new Message(
                    Message.MessageType.REQUEST,
                    MessageUtil.registerRequest().getBytes()
            );
            channel.writeAndFlush(message).addListener(future -> {
                if (future.isSuccess()) {
                    Log.d(TAG, "Message send success");
                    if (listener != null) {
                        listener.onMessageReceived(true, message.getType(), new String(message.getPayload()));
                    }
                } else {
                    Log.e(TAG, "Message send failed: " + future.cause().getMessage());
                }
            }).sync();
        } else {
            Log.e(TAG, "Channel is not active or is null");
        }
    }

    public class ClientBinder extends Binder {
        private ClientService getService() {
            // Also ok, and we don't need extra methods in this binder
            // But personally I do not recommend this way, so marked as *private*
            return ClientService.this;
        }

        public void setMessageListener(MessageListener listener) {
            ClientService.this.listener = listener;
        }

        // Public methods available / accessed from Activity (should be async)
        public boolean connect(final String ip, final int port) throws InterruptedException {
            ClientService.this.ip = ip;
            ClientService.this.port = port;
            return ClientService.this.connect();
        }

        public void disconnect() throws InterruptedException {
            ClientService.this.disconnect(true);
        }

        public void sendRegisterMessage() throws InterruptedException {
            ClientService.this.sendRegisterMessage();
        }
    }
}
