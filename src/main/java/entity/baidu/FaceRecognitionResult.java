package entity.baidu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FaceRecognitionResult {

    /**
     * 组id
     */
    @JsonProperty("group_id")
    private String groupId;

    /**
     * 用户id，实际存放的是用户账号
     */
    @JsonProperty("user_id")
    private String userId;

    /**
     * 相似得分(百分制)
     */
    private double score;
}
