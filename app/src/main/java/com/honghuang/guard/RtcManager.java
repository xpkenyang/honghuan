package com.honghuang.guard;

import android.content.Context;
import android.util.Log;
import android.view.TextureView;

import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.VideoCanvas;
import com.ss.bytertc.engine.data.RemoteStreamKey;
import com.ss.bytertc.engine.data.StreamIndex;
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler;
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.MediaStreamType;
import com.ss.bytertc.engine.type.MessageConfig;
import com.ss.bytertc.engine.type.RTCRoomStats;
import com.ss.bytertc.engine.type.StreamRemoveReason;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * RTC 引擎管理器
 * 负责管理火山引擎 RTC 音视频引擎
 */
public class RtcManager {

    private static final String TAG = "RtcManager";

    private Context context;
    private RTCVideo rtcVideo;
    private RTCRoom rtcRoom;
    private RtcEventListener listener;

    // 房间信息
    private String appId;
    private String roomId;
    private String uid;
    private String token;

    // 状态
    private boolean isJoined = false;
    private boolean isVideoEnabled = true;
    private boolean isAudioEnabled = true;
    public android.os.Handler timeoutHandler;
    private static final int JOIN_ROOM_TIMEOUT = 15000; // 加入房间超时15秒

    public interface RtcEventListener {
        void onJoinRoomSuccess(String roomId);
        void onJoinRoomError(int errorCode, String errorMessage);
        void onRemoteUserJoined(String uid);
        void onRemoteUserLeft(String uid);
        void onMessageReceived(String message);
        void onError(int errorCode, String errorMessage);
        void onWarning(int warningCode, String warningMessage);
    }

    public RtcManager(Context context) {
        this.context = context.getApplicationContext();
        this.timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    }

    public void setListener(RtcEventListener listener) {
        this.listener = listener;
    }

    /**
     * 创建 RTC 引擎
     */
    public boolean createEngine(String appId) {
        if (rtcVideo != null) {
            Log.w(TAG, "引擎已创建");
            return true;
        }

        try {
            Log.i(TAG, "创建 RTC 引擎，AppID: " + appId);
            rtcVideo = RTCVideo.createRTCVideo(
                    context,
                    appId,
                    new IRTCVideoEventHandler() {
                        @Override
                        public void onWarning(int warn) {
                            String warnMsg = getWarningMessage(warn);
                            Log.w(TAG, "RTC 警告: " + warn + " - " + warnMsg);
                            if (listener != null) {
                                listener.onWarning(warn, warnMsg);
                            }
                        }

                        @Override
                        public void onError(int err) {
                            String errMsg = getErrorMessage(err);
                            Log.e(TAG, "RTC 错误: " + err + " - " + errMsg);
                            if (listener != null) {
                                listener.onError(err, errMsg);
                            }
                        }
                    },
                    null,
                    null
            );

            if (rtcVideo == null) {
                Log.e(TAG, "创建 RTC 引擎失败，返回 null");
                return false;
            }

            Log.i(TAG, "RTC 引擎创建成功");
            return true;

        } catch (Throwable e) { // 捕获所有错误，包括UnsatisfiedLinkError等
            Log.e(TAG, "创建 RTC 引擎异常", e);
            if (listener != null) {
                listener.onError(-1, "创建 RTC 引擎异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 设置本地视频渲染视图
     */
    public boolean setLocalVideoView(TextureView textureView) {
        if (rtcVideo == null) {
            Log.e(TAG, "RTC 引擎未创建");
            return false;
        }

        try {
            VideoCanvas videoCanvas = new VideoCanvas();
            videoCanvas.renderView = textureView;
            videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;
            rtcVideo.setLocalVideoCanvas(StreamIndex.STREAM_INDEX_MAIN, videoCanvas);

            Log.i(TAG, "本地视频视图设置成功");
            return true;

        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "设置本地视频视图失败", e);
            return false;
        }
    }

    /**
     * 设置远程视频渲染视图
     */
    public boolean setRemoteVideoView(String uid, TextureView textureView) {
        if (rtcVideo == null || roomId == null) {
            Log.e(TAG, "RTC 引擎或房间ID未设置");
            return false;
        }

        try {
            VideoCanvas videoCanvas = new VideoCanvas();
            videoCanvas.renderView = textureView;
            videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;

            // 使用 RemoteStreamKey 设置远程视频
            RemoteStreamKey remoteStreamKey = new RemoteStreamKey(
                    roomId,
                    uid,
                    StreamIndex.STREAM_INDEX_MAIN
            );

            rtcVideo.setRemoteVideoCanvas(remoteStreamKey, videoCanvas);

            Log.i(TAG, "远程视频视图设置成功: " + uid);
            return true;

        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "设置远程视频视图失败", e);
            return false;
        }
    }

    /**
     * 创建并加入房间（v1.1.39：严格按照官方示例代码实现）
     */
    public boolean joinRoom(String roomId, String uid, String token) {
        if (rtcVideo == null) {
            Log.e(TAG, "RTC 引擎未创建，请先调用 createEngine");
            return false;
        }

        if (isJoined) {
            Log.w(TAG, "已经在房间中");
            return true;
        }

        try { // v1.1.37：改为 catch Throwable
            Log.i(TAG, "加入房间: " + roomId + ", UID: " + uid);
            this.roomId = roomId;
            this.uid = uid;
            this.token = token;

            // ========== v1.1.39：最小化实现，只保留核心功能 ==========
            
            // 1. 初始化 RTCRoom 对象
            rtcRoom = rtcVideo.createRTCRoom(roomId);
            Log.d(TAG, "v1.1.39: 创建 RTCRoom 成功");
            
            // 2. 设置 RTCRoom 事件处理器
            rtcRoom.setRTCRoomEventHandler(new IRTCRoomEventHandler() {
                @Override
                public void onRoomStateChanged(String roomId, String uid, int state, String extraInfo) {
                    Log.i(TAG, String.format("房间状态变化: roomId=%s, uid=%s, state=%d, extraInfo=%s", 
                            roomId, uid, state, extraInfo));
                    
                    // state=0 表示已加入房间
                    if (state == 0 && !isJoined) {
                        // 移除超时任务
                        timeoutHandler.removeCallbacksAndMessages(null);
                        
                        isJoined = true;
                        if (listener != null) {
                            listener.onJoinRoomSuccess(roomId);
                        }
                    } else if (state != 0) {
                        // 加入失败
                        timeoutHandler.removeCallbacksAndMessages(null);
                        if (listener != null) {
                            listener.onJoinRoomError(state, "房间状态错误: " + extraInfo);
                        }
                    }
                }

                @Override
                public void onUserPublishStream(String uid, MediaStreamType type) {
                    Log.i(TAG, "用户发布流: " + uid + ", type=" + type);
                    if (listener != null) {
                        listener.onRemoteUserJoined(uid);
                    }
                }

                @Override
                public void onUserUnpublishStream(String uid, MediaStreamType type, StreamRemoveReason reason) {
                    Log.i(TAG, "用户取消发布流: " + uid + ", reason=" + reason);
                    if (listener != null) {
                        listener.onRemoteUserLeft(uid);
                    }
                }

                @Override
                public void onLeaveRoom(RTCRoomStats stats) {
                    Log.i(TAG, "离开房间: " + stats);
                    isJoined = false;
                }

                @Override
                public void onTokenWillExpire() {
                    Log.w(TAG, "Token 即将过期");
                }

                @Override
                public void onRoomMessageReceived(String uid, String message) {
                    Log.i(TAG, "收到房间消息: " + uid + " - " + message);
                }

                @Override
                public void onRoomBinaryMessageReceived(String uid, ByteBuffer message) {
                    Log.i(TAG, "收到房间二进制消息: " + uid);
                }

                @Override
                public void onUserMessageReceived(String uid, String message) {
                    Log.i(TAG, "收到用户消息: " + uid + " - " + message);
                    if (listener != null) {
                        listener.onMessageReceived(message);
                    }
                }

                @Override
                public void onUserBinaryMessageReceived(String uid, ByteBuffer message) {
                    Log.i(TAG, "收到用户二进制消息: " + uid);
                }
            });
            Log.d(TAG, "v1.1.39: 设置 RTCRoomEventHandler 成功");
            
            // 3. 开启麦克风（核心功能）
            rtcVideo.startAudioCapture();
            Log.d(TAG, "v1.1.39: 开启麦克风成功");
            
            // 4. 加入房间（核心功能）
            UserInfo userInfo = new UserInfo(uid, "");
            RTCRoomConfig roomConfig = new RTCRoomConfig(
                    ChannelProfile.CHANNEL_PROFILE_COMMUNICATION,
                    true,
                    true,
                    true
            );
            Log.d(TAG, "v1.1.39: 准备调用 joinRoom");
            rtcRoom.joinRoom(token, userInfo, roomConfig);
            Log.d(TAG, "v1.1.39: 调用 joinRoom 成功（异步）");
            
            // ========== 官方示例代码结束 ==========

            // 添加超时任务
            timeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isJoined) {
                        Log.e(TAG, "加入房间超时");
                        if (listener != null) {
                            listener.onJoinRoomError(-2, "加入房间超时，请检查网络后重试");
                        }
                        // 销毁资源
                        destroy();
                    }
                }
            }, JOIN_ROOM_TIMEOUT);

            return true;

        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "加入房间异常", e);
            if (listener != null) {
                listener.onJoinRoomError(-1, "加入房间异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 离开房间
     */
    public void leaveRoom() {
        if (!isJoined || rtcRoom == null) {
            return;
        }

        try {
            rtcRoom.leaveRoom();
            Log.i(TAG, "已离开房间");
        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "离开房间异常", e);
        }
    }

    /**
     * 销毁引擎
     */
    public void destroy() {
        if (rtcRoom != null) {
            try {
                rtcRoom.destroy();
                Log.i(TAG, "RTC 房间已销毁");
            } catch (Throwable e) { // v1.1.37：改为 catch Throwable
                Log.e(TAG, "销毁 RTC 房间异常", e);
            }
            rtcRoom = null;
        }

        if (rtcVideo != null) {
            try {
                RTCVideo.destroyRTCVideo();
                Log.i(TAG, "RTC 引擎已销毁");
            } catch (Throwable e) { // v1.1.37：改为 catch Throwable
                Log.e(TAG, "销毁 RTC 引擎异常", e);
            }
            rtcVideo = null;
        }

        isJoined = false;
    }

    /**
     * 开启/关闭视频采集
     */
    public boolean toggleVideo() {
        if (rtcVideo == null) {
            return false;
        }

        try {
            if (isVideoEnabled) {
                rtcVideo.stopVideoCapture();
                isVideoEnabled = false;
                Log.i(TAG, "视频采集已关闭");
            } else {
                rtcVideo.startVideoCapture();
                isVideoEnabled = true;
                Log.i(TAG, "视频采集已开启");
            }
            return true;
        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "切换视频采集异常", e);
            return false;
        }
    }

    /**
     * 开启/关闭音频采集
     */
    public boolean toggleAudio() {
        if (rtcVideo == null) {
            return false;
        }

        try {
            if (isAudioEnabled) {
                rtcVideo.stopAudioCapture();
                isAudioEnabled = false;
                Log.i(TAG, "音频采集已关闭");
            } else {
                rtcVideo.startAudioCapture();
                isAudioEnabled = true;
                Log.i(TAG, "音频采集已开启");
            }
            return true;
        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "切换音频采集异常", e);
            return false;
        }
    }

    /**
     * 发送消息 - 使用正确的 API
     */
    public boolean sendMessage(String message) {
        if (!isJoined || rtcRoom == null) {
            Log.w(TAG, "未加入房间，无法发送消息");
            return false;
        }

        try {
            // 使用正确的 API 签名：sendUserMessage(String userId, String message, MessageConfig messageConfig)
            // 发送给 bot，userId 应该是 bot 的 ID
            // 这里我们使用房间内的消息广播，uid 作为目标
            rtcRoom.sendUserMessage(uid, message, null);
            Log.i(TAG, "发送消息: " + message);
            return true;
        } catch (Throwable e) { // v1.1.37：改为 catch Throwable
            Log.e(TAG, "发送消息异常", e);
            return false;
        }
    }

    /**
     * 获取错误信息
     */
    private String getErrorMessage(int errorCode) {
        Map<Integer, String> errorMap = new HashMap<>();
        // 通用错误码
        errorMap.put(-1, "异常错误");
        errorMap.put(-2, "操作超时");
        errorMap.put(0, "操作成功");
        errorMap.put(1, "一般错误");
        errorMap.put(2, "无效参数");
        errorMap.put(3, "SDK 未初始化");
        errorMap.put(4, "引擎无效");
        errorMap.put(5, "房间 ID 无效");
        errorMap.put(6, "Token 无效或已过期");
        errorMap.put(7, "网络连接错误");
        errorMap.put(8, "加入房间失败");
        errorMap.put(9, "离开房间失败");
        errorMap.put(10, "用户 ID 无效");
        errorMap.put(11, "音视频权限未授予");
        errorMap.put(12, "网络带宽不足");
        errorMap.put(13, "服务器响应超时");
        errorMap.put(14, "房间人数已满");
        errorMap.put(15, "用户已被踢出房间");
        errorMap.put(16, "音视频编解码错误");
        errorMap.put(17, "设备不支持该音视频格式");
        errorMap.put(18, "防火墙拦截连接");
        errorMap.put(19, "域名解析失败");
        errorMap.put(20, "ICE 打洞失败，无法建立P2P连接");

        // 错误码范围：1000~1999 网络相关
        errorMap.put(1001, "网络断开");
        errorMap.put(1002, "网络切换");
        errorMap.put(1003, "网络抖动");
        errorMap.put(1004, "网关错误");

        // 错误码范围：2000~2999 媒体相关
        errorMap.put(2001, "摄像头被占用");
        errorMap.put(2002, "麦克风被占用");
        errorMap.put(2003, "摄像头启动失败");
        errorMap.put(2004, "麦克风启动失败");
        errorMap.put(2005, "扬声器启动失败");

        return errorMap.getOrDefault(errorCode, "未知错误: " + errorCode);
    }

    /**
     * 获取警告信息
     */
    private String getWarningMessage(int warningCode) {
        Map<Integer, String> warningMap = new HashMap<>();
        warningMap.put(1, "网络质量差，通话可能卡顿");
        warningMap.put(2, "设备性能不足，建议关闭其他后台应用");
        warningMap.put(3, "音频设备异常，可能无法正常说话/听到声音");
        warningMap.put(4, "视频设备异常，可能无法正常显示画面");
        warningMap.put(5, "CPU 占用过高，可能导致通话卡顿");
        warningMap.put(6, "内存占用过高，可能导致应用闪退");
        warningMap.put(7, "网络延迟过高，通话可能有明显延迟");
        warningMap.put(8, "网络丢包严重，通话可能断断续续");
        warningMap.put(9, "电池电量低，建议充电后使用");
        warningMap.put(10, "当前使用移动网络，请注意流量消耗");
        warningMap.put(11, "扬声器被遮挡，可能听不到声音");
        warningMap.put(12, "麦克风被遮挡，对方可能听不到你的声音");

        return warningMap.getOrDefault(warningCode, "未知警告: " + warningCode);
    }

    // Getters
    public boolean isJoined() {
        return isJoined;
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }
}
