package entity.baidu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FaceRecognitionData {

    /**
     * 人脸 token
     */
    @JsonProperty("face_token")
    private String faceToken;

    @JsonProperty("log_id")
    private String logId;

    /**
     * 识别结果数量
     */
    @JsonProperty("result_num")
    private int resultNum;

    /**
     * 结果数组
     */
    private List<FaceRecognitionResult> result;
}
