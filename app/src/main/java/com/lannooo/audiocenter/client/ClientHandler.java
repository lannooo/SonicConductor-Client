package com.lannooo.audiocenter.client;

import static com.lannooo.audiocenter.tool.MessageUtil.fileUploadRequest;

import android.util.Log;

import com.lannooo.audiocenter.audio.AbstractAudioHandler;
import com.lannooo.audiocenter.audio.AudioEventListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.stream.ChunkedNioFile;

public class ClientHandler extends SimpleChannelInboundHandler<Message> {
    public static final String TAG = "ClientHandler";

    private final AbstractAudioHandler audioHandler;
    private final MessageListener listener;

    private final Map<String, RequestHandler> requestHandlers;
    private final ExecutorService executor;

    public ClientHandler(ClientService clientService) {
        super();
        this.audioHandler = clientService.getAudioHandler();
        this.listener = clientService.getListener();
        this.executor = clientService.getExecutor();
        this.requestHandlers = registerHandlers();
    }

    private Map<String, RequestHandler> registerHandlers() {
        Map<String, RequestHandler> handles = new HashMap<>();
        handles.put("capture", this::handleCaptureRequest);
        handles.put("delete", this::handleFileDeleteRequest);
        handles.put("list", this::handleFileListRequest);
        // TODO more commands
        return Collections.unmodifiableMap(handles);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        Log.i(TAG, "Client Handler: " + msg.toString());

        // Message read from server should be displayed to UI if listener is set
        if (listener != null) {
            listener.onMessageReceived(false, msg.getType(), new String(msg.getPayload()));
        }

        if (msg.getType() == Message.MessageType.REQUEST) {
            String payloadData = new String(msg.getPayload());
            MessageRequest request = MessageRequest.fromJsonString(payloadData);
            RequestHandler handler = requestHandlers.get(request.getSubtype());
            if (handler != null) {
                // TODO make it async?
                handler.handleMessage(ctx, request);
            } else {
                Log.e(TAG, "No handler found for " + request.getSubtype());
                writeShortResponse(ctx, "Oops!");
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Log.i(TAG, "Server connected");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Log.i(TAG, "Server disconnected");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    private void handleFileListRequest(ChannelHandlerContext ctx, MessageRequest request) {
        String[] files = audioHandler.getBaseDir().list();
        writeShortResponse(ctx, String.join("\n", files));
    }

    private void handleFileDeleteRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String path = (String) Objects.requireNonNull(commands.get("filepath"));
        Path filepath = Paths.get(path);
        if (!filepath.isAbsolute()) { // just a fileName, not an absolute path
            filepath = audioHandler.getBaseDir().toPath().resolve(filepath);
        }
        if (Files.exists(filepath)) {
            try {
                Files.delete(filepath);
                writeShortResponse(ctx, "Deleted");
            } catch (IOException e) {
                writeShortResponse(ctx, "Delete failed");
                throw new RuntimeException(e);
            }
        } else {
            writeShortResponse(ctx, "Not found");
        }
    }

    private void handleCaptureRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String action = (String) commands.get("action");
        if ("start".equalsIgnoreCase(action)) {
            String outputName = (String) Objects.requireNonNull(commands.get("output"));
            String mode = (String) Objects.requireNonNull(commands.get("mode"));
            double duration = (double) Objects.requireNonNull(commands.get("duration"));
            boolean process = (boolean) Objects.requireNonNull(commands.get("process"));
            boolean forward = (boolean) Objects.requireNonNull(commands.get("forward"));
            boolean postDelete = (boolean) Objects.requireNonNull(commands.get("delete"));

            boolean isCustom = !mode.equalsIgnoreCase("simple");
            // TODO what if it ends with other extensions? Need to transform it?
            if (isCustom) {
                if (!outputName.endsWith(".wav")) {
                    // if outputName is not ended with .wav, append it
                    outputName += ".wav";
                }
            } else {
                if (!outputName.endsWith(".m4a")) {
                    // if outputName is not ended with .m4a, append it
                    outputName += ".m4a";
                }
            }

            // configure audio handler according to the mode and more extra parameters
            audioHandler.configure(outputName, (int) duration, process, isCustom,
                    new AudioEventListener() {
                        @Override
                        public void onRecordStart() {
                            if (isCustom && duration > 0) {
                                executor.submit(() -> {
                                    try {
                                        Thread.sleep((long) (duration * 1000));
                                        Log.i(TAG, "Recording stopped automatically by duration limit: " + duration);
                                        audioHandler.stop();
                                    } catch (InterruptedException e) {
                                        Log.e(TAG, "Sleep interrupted", e);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onRecordStop(File outputFile) {
                            if (forward) {
                                // handle file forward (transfer) to server
                                executor.submit(() -> uploadFileByChunk(ctx, outputFile, postDelete));
                            }
                        }
                    });

            audioHandler.start();
            writeShortResponse(ctx, "Started");
        } else if ("stop".equalsIgnoreCase(action)) {
            audioHandler.stop();
            writeShortResponse(ctx, "Stopped");
        } else if ("pause".equalsIgnoreCase(action)) {
            audioHandler.pause();
            writeShortResponse(ctx, "Paused");
        } else if ("resume".equalsIgnoreCase(action)) {
            audioHandler.resume();
            writeShortResponse(ctx, "Resumed");
        } else {
            writeShortResponse(ctx, "Oops! Invalid action");
        }
    }

    private void uploadFileByChunk(ChannelHandlerContext ctx, File file, boolean postDelete) {
        // send file to server [Request] -> [DataSync]
        Log.i(TAG, "Uploading file: " + file.getAbsolutePath());
        try {
            // read file data chunk by chunk and send Messages to server
            ChunkedNioFile chunkedNioFile = new ChunkedNioFile(file, 2048);
            int length = (int) chunkedNioFile.length();
            int chunks = length / 2048;
            if (length % 2048 != 0) {
                chunks++;
            }

            // request the server to prepare for file upload
            String uploadReqPayload = fileUploadRequest(file, length, chunks);
            Message uploadReq = new Message(Message.MessageType.REQUEST, uploadReqPayload.getBytes());
            ctx.writeAndFlush(uploadReq).addListener(
                    (ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            Log.d(TAG, "Upload request sent");
                            if (listener != null) {
                                listener.onMessageReceived(true, uploadReq.getType(), uploadReqPayload);
                            }
                        } else {
                            Log.e(TAG, "Upload request failed: " + future.cause().getMessage());
                        }
                    }
            );

            ByteBufAllocator alloc = ctx.alloc();
            int chunkId = 0;
            while (!chunkedNioFile.isEndOfInput()) {
                long offset = chunkedNioFile.currentOffset();
                ByteBuf byteBuf = chunkedNioFile.readChunk(alloc);
                ByteBuf tgt = alloc.buffer(byteBuf.readableBytes() + 16)
                        .writeInt(++chunkId)
                        .writeInt(chunks)
                        .writeInt((int) offset)
                        .writeInt(length)
                        .writeBytes(byteBuf);
                Log.d(TAG, "Sending chunk: " + chunkId + "/" + chunks + " position: " + offset + "/" + length);
                // flush every time
                ctx.writeAndFlush(new Message(Message.MessageType.DATA_TRANSFER, tgt));
            }

            // delete file after upload
            if (postDelete) {
                boolean deleted = file.delete();
                if (deleted) {
                    Log.d(TAG, "File deleted: " + file.getAbsolutePath());
                } else {
                    Log.e(TAG, "File delete failed: " + file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while reading file: " + e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            Log.e(TAG, "Error while sending file: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void writeShortResponse(ChannelHandlerContext ctx, String x) {
        Message msg = new Message(Message.MessageType.RESPONSE, x.getBytes());
        ctx.writeAndFlush(msg).addListener(
                (ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        if (listener != null) {
                            listener.onMessageReceived(true, Message.MessageType.RESPONSE, x);
                        }
                        Log.d(TAG, "Response sent: " + x);
                    } else {
                        Log.e(TAG, "Response send failed: " + future.cause().getMessage());
                    }
                }
        );
    }


    public interface RequestHandler {
        void handleMessage(ChannelHandlerContext ctx, MessageRequest request);
    }
}
