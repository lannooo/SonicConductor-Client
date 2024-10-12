package com.lannooo.audiocenter.client;

import static com.lannooo.audiocenter.tool.MessageUtil.fileUploadRequest;

import android.util.Log;

import com.lannooo.audiocenter.audio.AudioEventListener;
import com.lannooo.audiocenter.audio.ClientAudioHandler;
import com.lannooo.audiocenter.audio.UploadingFileItem;
import com.lannooo.audiocenter.tool.AppUtil;

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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.stream.ChunkedNioFile;

public class ClientHandler extends SimpleChannelInboundHandler<Message> {
    public static final String TAG = "ClientHandler";

    private final ClientAudioHandler audioHandler;
    private final MessageListener listener;

    private final Map<String, RequestHandler> requestHandlers;
    private final ExecutorService executor;
    private final ClientService clientService;

    public ClientHandler(ClientService clientService) {
        super();
        this.clientService = clientService;
        this.audioHandler = clientService.getAudioHandler();
        this.listener = clientService.getListener();
        this.executor = clientService.getExecutor();
        this.requestHandlers = registerHandlers();
    }

    private Map<String, RequestHandler> registerHandlers() {
        Map<String, RequestHandler> handles = new HashMap<>();
        handles.put("capture", this::handleCaptureRequest);
        handles.put("playback", this::handlePlaybackRequest);
        handles.put("download", this::handleDownloadFileRequest);
        handles.put("upload", this::handleUploadFileRequest);
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
            listener.onMessageReceived(false, msg.getType(), msg.toString());
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
            clientService.updateRequestTime();
        } else if (msg.getType() == Message.MessageType.DATA_TRANSFER) {
            byte[] payload = msg.getPayload();
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            UploadingFileItem fileItem = audioHandler.writeUploadingFile(ctx, buf);
            if (fileItem != null) {
                if (fileItem.isFinished()) {
                    writeShortResponse(ctx, "File uploaded");
                } else if (fileItem.isFailed()) {
                    writeShortResponse(ctx, "File upload failed");
                }
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        audioHandler.cacheServerChannel(ctx);
        clientService.acquireWakeLock();
        clientService.updateRequestTime();
        Log.i(TAG, "Server connected");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        audioHandler.clearServerChannel();
        clientService.releaseWakeLock();
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

    private void handlePlaybackRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String action = (String) commands.get("action");
        if ("start".equalsIgnoreCase(action)) {
            String playFile = (String) Objects.requireNonNull(commands.get("input"));
            String mode = (String) Objects.requireNonNull(commands.get("mode"));
            boolean loop = (boolean) Objects.requireNonNull(commands.get("loop"));

            audioHandler.configurePlayer(playFile, mode, loop, new AudioEventListener() {
                @Override
                public void onPlaybackStop() {
                    if (!loop) {
                        // if not looping forever, it is stopped by it self
                        // so send a message to client after done, or the user cannot receive response
                        writeShortResponse(ctx, "Stopped");
                    }
                }
            });
            audioHandler.startPlayer();
            writeShortResponse(ctx, "Started");
        } else if ("stop".equalsIgnoreCase(action)) {
            audioHandler.stopPlayer();
            writeShortResponse(ctx, "Stopped");
        } else if ("pause".equalsIgnoreCase(action)) {
            audioHandler.pausePlayer();
            writeShortResponse(ctx, "Paused");
        } else if ("resume".equalsIgnoreCase(action)) {
            audioHandler.resumePlayer();
            writeShortResponse(ctx, "Resumed");
        } else {
            writeShortResponse(ctx, "Oops! Invalid action");
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
            audioHandler.configureRecorder(outputName, (int) duration, process, isCustom,
                    new AudioEventListener() {
                        @Override
                        public void onRecordStart() {
                            if (isCustom && duration > 0) {
                                executor.submit(() -> {
                                    try {
                                        Thread.sleep((long) (duration * 1000));
                                        Log.i(TAG, "Recording stopped automatically by duration limit: " + duration);
                                        audioHandler.stopRecorder();
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
                                executor.submit(() -> {
                                    try {
                                        uploadFileByChunk(ctx, outputFile, postDelete);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error while forwarding file: " + e.getMessage(), e);
                                    }
                                });
                            }
                        }
                    });

            audioHandler.startRecorder();
            writeShortResponse(ctx, "Started");
        } else if ("stop".equalsIgnoreCase(action)) {
            audioHandler.stopRecorder();
            writeShortResponse(ctx, "Stopped");
        } else if ("pause".equalsIgnoreCase(action)) {
            audioHandler.pauseRecorder();
            writeShortResponse(ctx, "Paused");
        } else if ("resume".equalsIgnoreCase(action)) {
            audioHandler.resumeRecorder();
            writeShortResponse(ctx, "Resumed");
        } else {
            writeShortResponse(ctx, "Oops! Invalid action");
        }
    }

    private void handleUploadFileRequest(ChannelHandlerContext ctx, MessageRequest request) {
        // add file upload elements, receive data from DataTransfer Type Message
        Map<String, Object> data = request.getData();
        audioHandler.addUploadingFile(ctx, data);
        writeShortResponse(ctx, "Ready to receive chunks");
    }

    private void handleDownloadFileRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String path = (String) Objects.requireNonNull(commands.get("file"));
        boolean postDelete = (boolean) Objects.requireNonNull(commands.get("delete"));
        Path filepath = audioHandler.getBaseDir().toPath().resolve(path);

        if (Files.exists(filepath)) {
            executor.submit(() -> {
                try {
                    uploadFileByChunk(ctx, filepath.toFile(), postDelete);
                } catch (Exception e) {
                    Log.e(TAG, "Error while uploading file: " + e.getMessage(), e);
                }
            });
        } else {
            writeShortResponse(ctx, "Not found");
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
