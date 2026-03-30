package com.honghuang.guard;

import android.content.Context;
import android.util.Log;

/**
 * 语音唤醒管理器
 * 实现关键词唤醒功能："洪荒"、"守护者"
 */
public class VoiceWakeManager {
    
    private static final String TAG = "VoiceWake";
    
    // 唤醒关键词
    private static final String[] WAKE_KEYWORDS = {"洪荒", "守护者", "honghuang", "guard"};
    
    // 唤醒阈值（0-1，越高越严格）
    private static final float WAKE_THRESHOLD = 0.7f;
    
    private Context context;
    private boolean isListening = false;
    private WakeListener listener;
    
    public interface WakeListener {
        void onWakeUp(String keyword);
        void onError(String error);
    }
    
    public VoiceWakeManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public void setListener(WakeListener listener) {
        this.listener = listener;
    }
    
    /**
     * 开始监听唤醒词
     * 在后台持续运行，低功耗模式
     */
    public boolean startListening() {
        if (isListening) {
            Log.w(TAG, "已经在监听中");
            return true;
        }
        
        try {
            Log.i(TAG, "开始语音唤醒监听...");
            Log.i(TAG, "唤醒关键词: " + String.join(", ", WAKE_KEYWORDS));
            
            // 方案A：使用Android系统语音唤醒（如果设备支持）
            // 方案B：使用火山引擎VAD + 本地唤醒词检测
            // 方案C：使用扣子RTC的语音活动检测
            
            // 当前实现：基于RTC音频流的本地检测
            isListening = true;
            startLocalWakeDetection();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "启动唤醒监听失败", e);
            if (listener != null) {
                listener.onError("启动失败: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * 本地唤醒词检测
     * 使用轻量级算法检测音频流中的关键词
     */
    private void startLocalWakeDetection() {
        new Thread(() -> {
            while (isListening) {
                try {
                    // TODO: 实现音频流处理和唤醒词检测
                    // 1. 从RTC获取音频流
                    // 2. VAD语音活动检测
                    // 3. 特征提取
                    // 4. 关键词匹配
                    
                    // 模拟检测到唤醒词
                    Thread.sleep(100);
                    
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
    
    /**
     * 处理音频数据，检测唤醒词
     */
    public void processAudioData(byte[] audioData) {
        if (!isListening) return;
        
        // 步骤1: VAD检测（语音活动检测）
        if (!isVoiceActivity(audioData)) {
            return;
        }
        
        // 步骤2: 特征提取
        float[] features = extractFeatures(audioData);
        
        // 步骤3: 关键词匹配
        String detectedKeyword = matchKeyword(features);
        
        if (detectedKeyword != null) {
            Log.i(TAG, "🎙️ 检测到唤醒词: " + detectedKeyword);
            if (listener != null) {
                listener.onWakeUp(detectedKeyword);
            }
        }
    }
    
    /**
     * VAD语音活动检测
     * 检测是否有语音（排除噪音、静音）
     */
    private boolean isVoiceActivity(byte[] audioData) {
        // 计算音频能量
        double energy = calculateEnergy(audioData);
        
        // 能量阈值判断
        return energy > 0.01; // 阈值可调
    }
    
    /**
     * 计算音频能量
     */
    private double calculateEnergy(byte[] audioData) {
        double sum = 0;
        for (int i = 0; i < audioData.length; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }
        return Math.sqrt(sum / (audioData.length / 2));
    }
    
    /**
     * 提取音频特征
     * 简化版MFCC特征
     */
    private float[] extractFeatures(byte[] audioData) {
        // TODO: 实现MFCC特征提取
        // 或使用火山引擎提供的特征提取API
        
        // 临时返回简化特征
        return new float[]{(float) calculateEnergy(audioData)};
    }
    
    /**
     * 关键词匹配
     * 返回匹配到的关键词，未匹配返回null
     */
    private String matchKeyword(float[] features) {
        // TODO: 实现关键词匹配算法
        // 1. 使用预训练的轻量级模型
        // 2. 或使用简单的模板匹配
        
        // 临时模拟（实际需接入ASR或唤醒模型）
        return null;
    }
    
    /**
     * 停止监听
     */
    public void stopListening() {
        isListening = false;
        Log.i(TAG, "停止语音唤醒监听");
    }
    
    /**
     * 是否正在监听
     */
    public boolean isListening() {
        return isListening;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopListening();
    }
}
