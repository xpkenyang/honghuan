package com.honghuang.guard;

/**
 * 火山引擎RTC配置
 * 用于扣子智能体与APP的实时音视频交互
 * 
 * 配置来源：火山引擎控制台 + 扣子RTC智能体配置
 */
public class RtcConfig {
    
    // ========== 火山引擎RTC配置 ==========
    
    /**
     * 火山引擎AppId
     * 实时对话式AI应用 - 用于AI音视频互动方案
     */
    public static final String VOLC_APP_ID = "69ca70bc81235a017632e1e3";
    
    /**
     * RTC房间ID
     * 火山引擎控制台配置的固定房间
     */
    public static final String ROOM_ID = "ChatRoom01";
    
    /**
     * APP端用户ID
     * 在房间中的标识
     */
    public static final String USER_ID = "Huoshan01";
    
    /**
     * 加入房间的Token
     * 由火山引擎生成，有效期至 2026-04-07 21:22
     * 注意：Token会过期，需要定期更新
     */
    public static final String ROOM_TOKEN = "00169ca70bc81235a017632e1e3SQBNiKsBlcrLaRUF1WkKAENoYXRSb29tMDEJAEh1b3NoYW4wMQYAAAVBdVpAQAVBdVpAgAVBdVpAwAVBdVpBAQAVBdVpBQAVBdVpIAB6HZIl3sGv3/g7U+u2GokaeptF+rbAcTKxE0euRI3GKQ==";
    
    // ========== 扣子智能体配置 ==========
    
    /**
     * 扣子智能体BotId
     * 洪荒的大脑
     */
    public static final String COZE_BOT_ID = "7619603171669147688";
    
    /**
     * 扣子API地址
     */
    public static final String COZE_API_URL = "https://api.coze.cn";
    
    /**
     * 扣子智能体在RTC房间中的UserId
     */
    public static final String COZE_USER_ID = "ChatBot01";
    
    /**
     * 扣子智能体目标用户ID
     * 即APP端的USER_ID
     */
    public static final String COZE_TARGET_USER_ID = "Huoshan01";
    
    // ========== 版本信息 ==========
    
    /**
     * 应用版本号
     */
    public static final String APP_VERSION = "1.6.7";
    
    /**
     * 版本代码
     */
    public static final int VERSION_CODE = 52;
    
    /**
     * 版本描述
     */
    public static final String VERSION_DESC = "火山引擎RTC直连版";
    
    private RtcConfig() {
        // 工具类，禁止实例化
    }
}
