package entity;

import lombok.Data;

@Data
public class FaceRequest {
    /**
     * 行为
     */
    private String action;
    /**
     * 图片帧
     */
    private String frame;
    /**
     * 活体动作检测
     */
    private String checkAction;
    /**
     * 激活码
     */
    private String activationCode;
    /**
     * 用户名
     */
    private String userName;
    private String data;
}
