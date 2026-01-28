package config;

/**
 * 人脸检测参数配置
 * 能容忍阈值
 */
public class FaceConfig {
    /**
     * 最低活体置信度
     */
    public static float liveScoreMin = 0.3f;
    /**
     * 最低嘴巴闭合置信度
     */
    public static float mouthCloseScoreMin = 0.9f;
    /**
     * 最高闭眼置信度
     */
    public static float eyeCloseMax = 0.1f;
    /**
     * 最高模糊置信度
     */
    public static float blurMax = 0.2f;
    /**
     * 人脸识别最低相似度
     */
    public static double similarity = 80d;

}
