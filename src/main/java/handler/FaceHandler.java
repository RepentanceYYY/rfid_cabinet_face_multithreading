package handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jni.face.Face;
import com.jni.struct.EyeClose;
import com.jni.struct.FaceBox;
import com.jni.struct.HeadPose;
import com.jni.struct.LivenessInfo;
import config.FaceConfig;
import config.SystemConfig;
import entity.FaceResult;
import entity.baidu.FaceRecognitionResponse;
import entity.baidu.FaceRecognitionResult;
import entity.db.User;
import handler.manager.FaceApiManager;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import server.UserService;
import utils.FileUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class FaceHandler {
    private static SystemConfig systemConfig = SystemConfig.getInstance();

    /**
     * 人脸采集
     *
     * @param obj
     * @return
     */
    public static FaceResult capture(JSONObject obj) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        System.out.println("\n进入人脸采集-----"+timestamp+"---------------------\n");
        FileUtils.dumpJSONObjectOnce("人脸采集",obj);
        String base64Frame = obj.getString("frame");
        Object userNameObject = obj.get("userName");
        String userName = null;
        if (userNameObject != null) {
            userName = userNameObject.toString();
        }
        Object actionObject = obj.get("action");
        String actionStr = actionObject.toString();
        Object checkActionObject = obj.get("checkAction");

        Mat rawMat = null;
        Mat rgbMat = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Frame);
            MatOfByte matOfByte = new MatOfByte(bytes);
            rawMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

            if (rawMat == null || rawMat.empty()) {
                return FaceResult.tip(actionStr, "消息格式错误");
            }
            rgbMat = rawMat.clone();

            // 输出原始信息，方便排查
            System.out.println("原始尺寸：" + rgbMat.rows() + "x" + rgbMat.cols() + ", 通道=" + rgbMat.channels());

            long rgbMatAddr = rgbMat.getNativeObjAddr();
            System.out.println("图像内存地址：" + rgbMatAddr);

            synchronized (Face.class) {
                // 人脸检测
                System.out.println("获取人脸检测结果");
                FaceBox[] faceBoxes = Face.detect(rgbMatAddr, 1);
                System.out.println("人脸检测结果：" + JSON.toJSONString(faceBoxes));
                // 静默活体检测
                System.out.println("获取静默活体检测结果");
                LivenessInfo[] liveInfos = Face.rgbLiveness(rgbMatAddr);
                System.out.println();
                System.out.println(JSON.toJSONString(liveInfos));

                if (liveInfos == null || liveInfos.length == 0 || liveInfos[0].box == null) {
                    return FaceResult.tip(actionStr, "未检测到人脸");
                }
                float liveScore = liveInfos[0].livescore;
                if (liveScore < FaceConfig.liveScoreMin) {
                    System.out.println(String.format("检测到非活体,%.3f", liveScore));
                    return FaceResult.tip(actionStr, "活体指数太低:" + liveScore);
                }
                // 既然检测到了是活体，代表检测到了人脸
                // 人脸可用性检测(判断是否和其他人脸做绑定了什么的)
                FaceResult available = availableDetection(rgbMatAddr, userName, actionStr);
                if (available != null) {
                    return available;
                }
                // 获取到嘴巴闭合参数
                System.out.println("获取嘴巴闭合参数");
                float[] mouthCloseScore = Face.faceMouthClose(rgbMatAddr);
                // 如果有动作要求，先返回动作要求的结果
                if (checkActionObject != null) {
                    System.out.println("准备检测动作");
                    return actionDetection(actionObject.toString(), checkActionObject.toString(), mouthCloseScore, rgbMatAddr);
                }
                // 嘴巴闭合检测
                if (mouthCloseScore[0] < FaceConfig.mouthCloseScoreMin) {
                    return FaceResult.tip(actionStr, "请闭合嘴巴");
                }

                // 眼睛闭合检测
                System.out.println("获取眼睛闭合参数");
                EyeClose[] eyeCloses = Face.faceEyeClose(rgbMatAddr);
                if (eyeCloses == null || eyeCloses.length < 1) {
                    return FaceResult.tip(actionStr, "请睁开眼睛");
                }
                if (eyeCloses[0].leftEyeCloseConf > FaceConfig.eyeCloseMax || eyeCloses[0].rightEyeCloseConf > FaceConfig.eyeCloseMax) {
                    return FaceResult.tip(actionStr, "请睁开眼睛");
                }

                // 人脸模糊度检测
                System.out.println("获取人脸模糊度");
                float[] blurList = Face.faceBlur(rgbMatAddr);
                if (blurList == null || blurList.length == 0) {
                    return FaceResult.tip(actionStr, "请保持人脸在画面中");
                }
                System.out.println("当前模糊度:" + blurList[0]);
                if (blurList[0] > FaceConfig.blurMax) {
                    System.out.println(String.format("模糊度太高，%.3f", blurList[0]));
                    return FaceResult.tip(actionStr, "人脸太模糊");
                }
                return FaceResult.success(actionStr, "人脸可以使用");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return FaceResult.fail(actionStr, e.getMessage());
        } finally {
            if (rgbMat != null) rgbMat.release();
            if (rawMat != null) rawMat.release();
            String timestampEnd = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            System.out.println("\n离开人脸采集-----"+timestampEnd+"---------------------\n");
        }
    }

    /**
     * 人脸认证
     *
     * @param obj
     * @return
     */
    public static FaceResult auth(JSONObject obj) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        System.out.println("\n进入人脸认证-----"+timestamp+"---------------------\n");
        FileUtils.dumpJSONObjectOnce("人脸认证",obj);
        String base64Frame = obj.getString("frame");
        Object actionObject = obj.get("action");
        String actionStr = actionObject.toString();
        Object checkAction = obj.get("checkAction");

        Mat rgbMat = null;
        Mat safeMat = null;

        try {
            byte[] bytes = Base64.getDecoder().decode(base64Frame);
            MatOfByte matOfByte = new MatOfByte(bytes);
            rgbMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

            if (rgbMat == null || rgbMat.empty()) {
                return FaceResult.tip(actionStr, "消息格式错误");
            }

            System.out.println("原始尺寸：" + rgbMat.rows() + "x" + rgbMat.cols() + ", 通道=" + rgbMat.channels());

            safeMat = rgbMat.clone();
            long addr = safeMat.getNativeObjAddr();

            System.out.println("安全图像内存地址：" + addr);
            System.out.println(safeMat.rows() + "x" + safeMat.cols() + ", channels=" + safeMat.channels());
            // 同步锁
            synchronized (Face.class) {

                // 1 人脸检测
                System.out.println("获取人脸检测结果");
                FaceBox[] faceBoxes = Face.detect(addr, 1);
                System.out.println("人脸检测结果：" + JSON.toJSONString(faceBoxes));

                if (faceBoxes == null || faceBoxes.length == 0) {
                    return FaceResult.tip(actionStr, "未检测到人脸");
                }

                // 2 静默活体检测
                System.out.println("获取静默活体检测结果");
                LivenessInfo[] liveInfos = Face.rgbLiveness(addr);
                System.out.println(JSON.toJSONString(liveInfos));

                if (liveInfos == null || liveInfos.length == 0 || liveInfos[0].box == null) {
                    return FaceResult.tip(actionStr, "未检测到活体人脸");
                }

                float liveScore = liveInfos[0].livescore;
                System.out.println(String.format("活体置信度为:%.3f", liveScore));

                if (liveScore < FaceConfig.liveScoreMin) {
                    return FaceResult.tip(actionStr, "活体指数太低");
                }

                // 3 动作检测
                if (checkAction != null) {
                    System.out.println("动作检测，先获取嘴巴闭合度");
                    float[] mouthCloseScore = Face.faceMouthClose(addr);
                    return actionDetection(
                            actionStr,
                            checkAction.toString(),
                            mouthCloseScore,
                            addr
                    );
                }

                // 4️ 人脸识别
                System.out.println("加载人脸库到内存中");
                Face.loadDbFace();
                System.out.println("检查人脸是否存在---start");
                String s = Face.identifyWithAllByMat(addr, 0);
                System.out.println(s);

                FaceRecognitionResponse faceRecognitionResponse =
                        JSONObject.parseObject(s, FaceRecognitionResponse.class);

                List<FaceRecognitionResult> results =
                        faceRecognitionResponse.getData().getResult();

                if (results == null || results.isEmpty()) {
                    return FaceResult.fail(actionStr, "人脸不存在");
                }

                System.out.println("检查人脸是否存在----end");

                FaceRecognitionResult best = results.get(0);
                if (best.getScore() < FaceConfig.similarity) {
                    return FaceResult.fail(actionStr, "人脸不存在");
                }

                UserService userService = new UserService();
                User user = userService.getUserByUserName(best.getUserId());
                if (user == null) {
                    return FaceResult.fail(actionStr, "人脸用户数据出现异常，请检查");
                }

                return FaceResult.success(actionStr, "登录成功", user);
            }

        } catch (Exception e) {
            return FaceResult.fail(actionStr, e.getMessage());
        } finally {
            if (safeMat != null) {
                safeMat.release();
            }
            if (rgbMat != null) {
                rgbMat.release();
            }
            String timestampEnd = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            System.out.println("\n离开人脸认证-----"+timestampEnd+"---------------------\n");
        }
    }

    /**
     * 人脸注册和更新
     *
     * @param obj
     * @return
     */
    public static FaceResult update(JSONObject obj) {
        synchronized (Face.class){
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            System.out.println("\n进入人脸注册和更新-----"+timestamp+"---------------------\n");
            String base64Frame = obj.getString("frame");
            Object userNameObject = obj.get("userName");
            String actionStr = "update";
            String userName;
            if (userNameObject == null) {
                return FaceResult.fail(actionStr, "未提供用户账号");
            }
            userName = userNameObject.toString();
            Mat rgbMat = null;
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Frame);
                MatOfByte matOfByte = new MatOfByte(bytes);
                rgbMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);
                if (rgbMat.empty()) {
                    return FaceResult.fail(actionStr, "未提供人脸图片");
                }
                long rgbMatAddr = rgbMat.getNativeObjAddr();
                // 静默活体检测
                LivenessInfo[] liveInfos = Face.rgbLiveness(rgbMatAddr);
                if (liveInfos == null || liveInfos.length <= 0 || liveInfos[0].box == null) {
                    return FaceResult.tip(actionStr, "未检测到人脸");
                }
                System.out.println("人脸注册");
                String addResult = Face.userAddByMat(rgbMatAddr, userName, systemConfig.getBaiduFaceDbDefaultGroup(), "notInfo");
                System.out.println("人脸更新");
                String updateResult = Face.userUpdate(rgbMatAddr, userName, systemConfig.getBaiduFaceDbDefaultGroup(), "notInfo");
                return FaceResult.success(actionStr, "人脸更新成功");
            } catch (Exception e) {
                return FaceResult.fail(actionStr, "人脸更新失败:" + e.getMessage());
            } finally {
                rgbMat.release();
                String timestampEnd = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                System.out.println("\n离开人脸注册和更新-----"+timestampEnd+"---------------------\n");
            }
        }
    }

    public static FaceRecognitionResponse faceRecognition(JSONObject obj) {

        synchronized (Face.class){
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            System.out.println("\n进入faceRecognition-----"+timestamp+"---------------------\n");
            System.out.println("加载人脸库到内存中");
            Face.loadDbFace();
            String base64 = obj.getString("data");
            byte[] bytes = Base64.getDecoder().decode(base64);
            MatOfByte matOfByte = new MatOfByte(bytes);
            Mat rgbMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);
            if (rgbMat.empty()) {
                throw new RuntimeException("图片为空");
            }
            long nativeObjAddr = rgbMat.getNativeObjAddr();
            System.out.println("1:N人脸识别");
            String s = Face.identifyWithAllByMat(nativeObjAddr, 0);
            System.out.println("反序列号识别结果");
            FaceRecognitionResponse faceRecognitionResponse = JSONObject.parseObject(s, FaceRecognitionResponse.class);

            System.out.println(s);
            String timestampEnd = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            System.out.println("\n离开faceRecognition-----"+timestampEnd+"---------------------\n");
            return faceRecognitionResponse;
        }
    }

    /**
     * 人脸可用性检测
     *
     * @param rgbMatAddr
     * @param userName
     * @param action
     * @return
     */
    public static FaceResult availableDetection(long rgbMatAddr, String userName, String action) {
        try {
            System.out.println("加载人脸库到内存");
            Face.loadDbFace();
            System.out.println("1:N对比");
            String identifyResultJson = Face.identifyWithAllByMat(rgbMatAddr, 0);
            FaceRecognitionResponse faceRecognitionResponse = JSONObject.parseObject(identifyResultJson, FaceRecognitionResponse.class);
            List<FaceRecognitionResult> faceRecognitionResults = faceRecognitionResponse.getData().getResult();
            if (faceRecognitionResults == null || faceRecognitionResults.size() < 1) {
                return null;
            }
            FaceRecognitionResult best = faceRecognitionResults.get(0);
            if (best.getScore() < FaceConfig.similarity) {
                return null;
            }
            UserService userService = new UserService();
            User userByUserName = userService.getUserByUserName(best.getUserId());
            // 如果数据没有出错，是不会返回null的
            if (userByUserName == null) {
                return FaceResult.fail(action, "人脸数据出现异常，请检查");
            }
            if ((userName == null || userName.isEmpty()) && userByUserName.getUserName() != null
                    || (userName != null && !userName.isEmpty() && !userName.equals(userByUserName.getUserName()))) {
                return FaceResult.fail(action, "人脸已和" + userByUserName.getName() + "绑定");
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return FaceResult.fail(action, e.getMessage());
        }
    }

    /**
     * 动作完成检测
     * 完成返回202 未完成返回417
     *
     * @param action
     * @param mouthCloseScore
     * @param rgbMatAddr
     * @return
     */
    public static FaceResult actionDetection(String action, String checkAction, float[] mouthCloseScore, long rgbMatAddr) {
        synchronized (Face.class) {
            HeadPose[] headPoses = Face.faceHeadPose(rgbMatAddr);
            if (headPoses == null || headPoses.length == 0) {
                return FaceResult.tip(action, "检测不到人脸");
            }
            if (checkAction.equals("turn_left")) {
                if (headPoses[0].yaw > 20F) {
                    return FaceResult.comm(action, 202, "动作完成", null);
                } else {
                    return FaceResult.comm(action, 401, "请左转头", null);
                }
            }
            if (checkAction.equals("turn_right")) {
                if (headPoses[0].yaw < -20F) {
                    return FaceResult.comm(action, 202, "动作完成", null);
                } else {
                    return FaceResult.comm(action, 401, "请右转头", null);
                }
            }
            if (checkAction.equals("open_mouth")) {
                if (mouthCloseScore[0] < 0.6f) {
                    return FaceResult.comm(action, 202, "动作完成", null);
                } else {
                    return FaceResult.comm(action, 401, "请张嘴", null);
                }
            }
            return FaceResult.fail(action, "动作检测失败，请重试");
        }
    }

    /**
     * 初始化百度人脸数据库
     */
    public static void init() {
        System.out.println("开始--> 重新生成百度人脸数据库");
        long startTime = System.currentTimeMillis();
        UserService userService = new UserService();
        Map<String, String> userIdWithFacePath = userService.getUserIdWithFacePath();
        userIdWithFacePath.forEach((userName, facePath) -> {

            synchronized (Face.class) {
                Mat rgbMat = Imgcodecs.imread(facePath);
                Mat safeMat = rgbMat.clone();
                try {
                    long addr = safeMat.getNativeObjAddr();
                    String res = Face.userAddByMat(addr, userName, systemConfig.getBaiduFaceDbDefaultGroup(), "无信息");
                    System.out.println("----------------------------------");
                    System.out.println(res);
                    rgbMat.release();
                    System.out.println("用户名=" + userName + ", 人脸路径=" + facePath);
                    System.out.println("----------------------------------");
                } catch (Exception ex) {
                    System.out.println("百度人脸数据库新增" + userName + "数据报错" + ex.getMessage());
                } finally {
                    if (safeMat != null) {
                        safeMat.release();
                    }
                    if (rgbMat != null) {
                        rgbMat.release();
                    }
                }
            }
        });
        long endTime = System.currentTimeMillis(); // 结束计时
        long duration = endTime - startTime;
        System.out.println("结束--> 重新生成百度人脸数据库，耗时：" + duration + " 毫秒");
    }


}
