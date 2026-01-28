package entity.baidu;

import lombok.Data;

@Data
public class FaceRecognitionResponse {
    /**
     * 是否出现异常
     */
    private int errno;
    /**
     * 消息
     */
    private String msg;
    /**
     * 检测结果
     */
    private FaceRecognitionData data;
}
