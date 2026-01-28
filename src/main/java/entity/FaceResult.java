package entity;

import lombok.Data;

/**
 * 消息返回实体
 *
 * @param <T>
 */
@Data
public class FaceResult<T> {
    private String action;
    private int code;
    private String message;
    private T data;
    private long timestamp;
    public FaceResult(){

    }

    // 私有化构造函数
    private FaceResult(String action, int code, String message, T data) {
        this.action = action;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // 成功
    public static <T> FaceResult<T> success(String action, T data) {
        return new FaceResult<>(action, 200, "success", data);
    }
    // 成功
    public static <T> FaceResult<T> success(String action,String msg, T data) {
        return new FaceResult<>(action, 200, msg, data);
    }


    // 失败
    public static <T> FaceResult<T> fail(String action, String msg) {
        return new FaceResult<>(action, 500, msg, null);
    }
    // 失败
    public static <T> FaceResult<T> fail(String action, String msg,T data) {
        return new FaceResult<>(action, 500, msg, data);
    }

    // 提示
    public static <T> FaceResult<T> tip(String action, String message) {
        return new FaceResult<>(action, 100, message, null);
    }

    // 提示
    public static <T> FaceResult<T> tip(String action, String message, T data) {
        return new FaceResult<>(action, 100, message, data);
    }

    // 通用
    public static <T> FaceResult<T> comm(String action, int code, String message, T data) {
        return new FaceResult<>(action, code, message, data);
    }
}
