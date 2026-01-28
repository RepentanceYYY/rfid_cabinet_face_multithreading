package entity.reply;

import lombok.Data;

@Data
public class FaceLivenessResult {
    /**
     * 是否清晰
     */
    private boolean sharp;
    /**
     * 是否闭眼了
     */
    private boolean eyeClosed;
    /**
     * 是否张嘴了
     */
    private boolean mouthOpen;
    /**
     * 是否左转头
     */
    private boolean headTurnLeft;
    /**
     * 是否右转头
     */
    private boolean headTurnRight;
    /**
     * 是否低头
     */
    private boolean headDown;
    /**
     * 是否仰头
     */
    private boolean headUp;
    /**
     * 是否为活体
     */
    private boolean live;
    /**
     * 人脸图片
     */
    private String imageBase64;
}
