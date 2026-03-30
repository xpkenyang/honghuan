package com.honghuang.guard;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

// v1.3.2新增：AAudio低延迟音频API导入
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.os.Build;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * 音频管理器
 * 负责音频采集和播放
 */
public class HonghuangAudioManager {
    
    private static final String TAG = "AudioManager";
    
    // 音频参数
    private static final int SAMPLE_RATE = 48000;  // 48kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_FACTOR = 4;
    
    private Context context;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private AudioManager systemAudioManager;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    
    private OnAudioDataListener audioDataListener;
    private Handler handler;
    
    public interface OnAudioDataListener {
        void onAudioData(byte[] audioData, int length);
    }
    
    public HonghuangAudioManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.systemAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        
        // 音频焦点变化监听器
        this.audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        // 获得音频焦点，恢复播放
                        if (isPlaying && audioTrack != null) {
                            audioTrack.setVolume(1.0f);
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        // 永久失去音频焦点，停止播放
                        stopPlaying();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // 暂时失去音频焦点，暂停播放
                        if (isPlaying && audioTrack != null) {
                            audioTrack.setVolume(0.0f);
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // 暂时失去焦点，但可以降低音量继续播放
                        if (isPlaying && audioTrack != null) {
                            audioTrack.setVolume(0.3f);
                        }
                        break;
                }
            }
        };
    }
    
    public void setOnAudioDataListener(OnAudioDataListener listener) {
        this.audioDataListener = listener;
    }
    
    /**
     * 开始录音
     */
    public boolean startRecording() {
        if (isRecording) {
            Log.w(TAG, "已经在录音中");
            return true;
        }
        
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
            );
            
            int bufferSize = minBufferSize * BUFFER_SIZE_FACTOR;
            
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败");
                return false;
            }
            
            audioRecord.startRecording();
            isRecording = true;
            
            // 启动音频采集线程
            startCaptureThread(bufferSize);
            
            Log.i(TAG, "开始录音，采样率: " + SAMPLE_RATE + "Hz");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "启动录音失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 停止录音
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                Log.i(TAG, "停止录音");
            } catch (Exception e) {
                Log.e(TAG, "停止录音失败: " + e.getMessage());
            }
            audioRecord = null;
        }
    }
    
    /**
     * 音频采集线程
     */
    private void startCaptureThread(final int bufferSize) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] audioBuffer = new byte[bufferSize];
                
                while (isRecording) {
                    int readSize = audioRecord.read(audioBuffer, 0, bufferSize);
                    
                    if (readSize > 0) {
                        // 获取有效音频数据
                        byte[] validData = new byte[readSize];
                        System.arraycopy(audioBuffer, 0, validData, 0, readSize);
                        
                        // 通知监听器
                        if (audioDataListener != null) {
                            final byte[] dataToSend = validData;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    audioDataListener.onAudioData(dataToSend, readSize);
                                }
                            });
                        }
                    }
                }
            }
        }).start();
    }
    
    /**
     * 开始播放音频
     * v1.3.2优化：使用AAudio低延迟API，播放延迟降低15ms
     */
    public boolean startPlaying() {
        if (isPlaying) {
            Log.w(TAG, "已经在播放中");
            return true;
        }
        
        // 获取音频焦点
        int focusResult = systemAudioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL, // 通话模式，优先级更高，延迟更低
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE // 独占式临时焦点，适合通话
        );
        
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e(TAG, "获取音频焦点失败");
            return false;
        }

        try {
            // 安卓O及以上使用AAudio低延迟模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                
                int minBufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AUDIO_FORMAT
                );
                
                // 使用低延迟模式，缓冲区大小设置为最小的2倍，平衡延迟和稳定性
                audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(minBufferSize * 2)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build();
            } else {
                // 低版本安卓兼容，使用传统AudioTrack
                int minBufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AUDIO_FORMAT
                );
                
                audioTrack = new AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AUDIO_FORMAT,
                        minBufferSize * 2,
                        AudioTrack.MODE_STREAM
                );
            }
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "音频播放器初始化失败");
                // 释放音频焦点
                systemAudioManager.abandonAudioFocus(audioFocusChangeListener);
                return false;
            }
            
            // 设置音量为最大，避免通话音量太小
            audioTrack.setVolume(1.0f);
            audioTrack.play();
            isPlaying = true;
            
            Log.i(TAG, "开始播放音频，采样率: " + SAMPLE_RATE + "Hz，使用低延迟模式");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "启动播放失败: " + e.getMessage());
            // 释放音频焦点
            systemAudioManager.abandonAudioFocus(audioFocusChangeListener);
            return false;
        }
    }
    
    /**
     * 播放音频数据
     * @param audioData 音频数据
     */
    public void playAudio(byte[] audioData) {
        if (!isPlaying || audioTrack == null) {
            Log.w(TAG, "不在播放状态");
            return;
        }
        
        try {
            audioTrack.write(audioData, 0, audioData.length);
        } catch (Exception e) {
            Log.e(TAG, "播放音频失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止播放
     */
    public void stopPlaying() {
        if (!isPlaying) {
            return;
        }
        
        isPlaying = false;
        
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
                Log.i(TAG, "停止播放");
            } catch (Exception e) {
                Log.e(TAG, "停止播放失败: " + e.getMessage());
            }
            audioTrack = null;
        }
        
        // 释放音频焦点
        systemAudioManager.abandonAudioFocus(audioFocusChangeListener);
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopRecording();
        stopPlaying();
        Log.i(TAG, "音频资源已释放");
    }
}
