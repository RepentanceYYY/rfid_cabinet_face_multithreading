package entity.db;

import lombok.Data;

import java.util.Date;

@Data
public class User {
    private Long id;
    private String name;
    private String userName;
    private String gender;
    private Date createdAt;
    private Boolean active;
    private String role;
    private String cardInfo;
    private String fingerPrintInfo;
    private String faceInfo;
    private String passWord;
    private String srttings;
    private String openId;
}
