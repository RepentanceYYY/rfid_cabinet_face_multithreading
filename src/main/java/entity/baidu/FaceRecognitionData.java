package entity.baidu;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;
@Data
public class FaceRecognitionData {
    /**
     * 人脸token
     */
    @JSONField(name = "face_token")
    private String faceToken;
    @JSONField(name = "log_id")
    private String logId;
    /**
     * 识别结果数量
     */
    @JSONField(name = "result_num")
    private int resultNum;
    /**
     * 结果数组
     */

    private List<FaceRecognitionResult> result;
}
