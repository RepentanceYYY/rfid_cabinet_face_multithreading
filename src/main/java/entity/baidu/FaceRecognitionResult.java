package entity.baidu;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class FaceRecognitionResult {
    /**
     * 组id
     */
    @JSONField(name = "group_id")
    private String groupId;
    /**
     * 用户id，实际存放的是用户账号
     */
    @JSONField(name = "user_id")
    private String userId;
    /**
     * 相似得分(百分制)
     */
    private double score;
}
