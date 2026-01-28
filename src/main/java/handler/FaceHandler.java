package handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jni.face.Face;
import com.jni.struct.EyeClose;
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
        String base64Frame = obj.getString("frame");
        Object userNameObject = obj.get("userName");
        String userName = null;
        if (userNameObject != null) {
            userName = userNameObject.toString();
        }
        Object actionObject = obj.get("action");
        String actionStr = actionObject.toString();
        Object checkActionObject = obj.get("checkAction");

        Mat rgbMat = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Frame);
            MatOfByte matOfByte = new MatOfByte(bytes);
            rgbMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

            if (rgbMat.empty()) {
                return FaceResult.tip(actionStr, "未捕获到视频帧");
            }

            // 输出原始信息，方便排查
            System.out.println("原始尺寸：" + rgbMat.rows() + "x" + rgbMat.cols() + ", 通道=" + rgbMat.channels());

            // ✅ resize 成模型期望尺寸
//            Mat resizedMat = new Mat();
//            Imgproc.resize(rgbMat, resizedMat, new Size(640, 480));

            // 如果 native 需要 float
            // resizedMat.convertTo(resizedMat, CvType.CV_32FC3);

            long rgbMatAddr = rgbMat.getNativeObjAddr();
            System.out.println("Native 指针：" + rgbMatAddr);
            // 人脸检测
            System.out.println("人脸检测:"+JSON.toJSONString(Face.detect(rgbMatAddr,1)));
            // 静默活体检测
            LivenessInfo[] liveInfos = Face.rgbLiveness(rgbMatAddr);
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
            float[] mouthCloseScore = Face.faceMouthClose(rgbMatAddr);
            // 如果有动作要求，先返回动作要求的结果
            if (checkActionObject != null) {
                return actionDetection(actionObject.toString(), checkActionObject.toString(), mouthCloseScore, rgbMatAddr);
            }
            // 嘴巴闭合检测
            if (mouthCloseScore[0] < FaceConfig.mouthCloseScoreMin) {
                return FaceResult.tip(actionStr, "请闭合嘴巴");
            }

            // 眼睛闭合检测
            EyeClose[] eyeCloses = Face.faceEyeClose(rgbMatAddr);
            if (eyeCloses == null || eyeCloses.length < 1) {
                return FaceResult.tip(actionStr, "请睁开眼睛");
            }
            if (eyeCloses[0].leftEyeCloseConf > FaceConfig.eyeCloseMax || eyeCloses[0].rightEyeCloseConf > FaceConfig.eyeCloseMax) {
                return FaceResult.tip(actionStr, "请睁开眼睛");
            }

            // 人脸模糊度检测
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
        } catch (Exception e) {
            e.printStackTrace();
            return FaceResult.fail(actionStr, e.getMessage());
        } finally {
            if (rgbMat != null) rgbMat.release();
        }
    }

    /**
     * 人脸认证
     *
     * @param obj
     * @return
     */
    public static FaceResult auth(JSONObject obj) {
        String base64Frame = obj.getString("frame");
        Object actionObject = obj.get("action");
        // 需要检查的动作
        String actionStr = actionObject.toString();
        Object checkAction = obj.get("checkAction");
        Mat rgbMat = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Frame);
            MatOfByte matOfByte = new MatOfByte(bytes);
            rgbMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);
//            // 1. 确认 Mat 数据
            System.out.println(rgbMat.rows() + "x" + rgbMat.cols() + ", type=" + rgbMat.type());
//            // 2. 可选：resize/转换成模型期望尺寸
            // Mat resizedMat = new Mat();
            // Imgproc.resize(rgbMat, resizedMat, new Size(100, 100));
            // resizedMat.convertTo(resizedMat, CvType.CV_32FC3); // 如果 native 需要 float

            // 3. 获取 native pointer
            long rgbMatAddr = rgbMat.getNativeObjAddr();
            if (rgbMat.empty()) {
                return FaceResult.tip(actionStr, "消息格式错误");
            }
            // 获取人脸属性
            System.out.println("获取人脸属性");
            System.out.println(rgbMatAddr);
            System.out.println(rgbMat.rows() + "x" + rgbMat.cols() + ", channels=" + rgbMat.channels());
            // 人脸检测
            System.out.println("人脸检测:"+JSON.toJSONString(Face.detect(rgbMatAddr,1)));
            // 静默活体检测
            System.out.println("准备检测活体指数");
            LivenessInfo[] liveInfos = Face.rgbLiveness(rgbMatAddr);
            System.out.println(JSON.toJSONString(liveInfos));
            if (liveInfos == null || liveInfos.length == 0 || liveInfos[0].box == null) {
                return FaceResult.tip(actionStr, "未检测到人脸");
            }
            float liveScore = liveInfos[0].livescore;
            System.out.println(String.format("活体置信度为:%.3f", liveScore));
            if (liveScore < FaceConfig.liveScoreMin) {
                return FaceResult.tip(actionStr, "活体指数太低");
            }
            // 如果当前是动作检测
            if (checkAction != null) {
                // 获取到嘴巴闭合参数
                float[] mouthCloseScore = Face.faceMouthClose(rgbMatAddr);
                return actionDetection(actionStr, checkAction.toString(), mouthCloseScore, rgbMatAddr);
            }
            System.out.println("准备检查人脸是否存在---start");
            Face.loadDbFace();
            String s = Face.identifyWithAllByMat(rgbMatAddr, 0);
            System.out.println(s);
            FaceRecognitionResponse faceRecognitionResponse = JSONObject.parseObject(s, FaceRecognitionResponse.class);
            List<FaceRecognitionResult> faceRecognitionResults = faceRecognitionResponse.getData().getResult();
            if (faceRecognitionResults == null || faceRecognitionResults.size() < 1) {
                return FaceResult.fail(actionStr, "人脸不存在");
            }
            System.out.println("准备检查人脸是否存在----end");
            FaceRecognitionResult best = faceRecognitionResults.get(0);
            if (best.getScore() < FaceConfig.similarity) {
                return FaceResult.fail(actionStr, "人脸不存在");
            }
            UserService userService = new UserService();
            User userByUserName = userService.getUserByUserName(best.getUserId());
            if (userByUserName == null) {
                return FaceResult.fail(actionStr, "人脸用户数据出现异常，请检查");
            }
            return FaceResult.success(actionStr, "登录成功", userByUserName);

        } catch (Exception e) {
            return FaceResult.fail(actionStr, e.getMessage());
        } finally {
            rgbMat.release();
        }
    }

    /**
     * 人脸注册和更新
     *
     * @param obj
     * @return
     */
    public static FaceResult update(JSONObject obj) {
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
            String addResult = Face.userAddByMat(rgbMatAddr, userName, systemConfig.getBaiduFaceDbDefaultGroup(), "notInfo");
            String updateResult = Face.userUpdate(rgbMatAddr, userName, systemConfig.getBaiduFaceDbDefaultGroup(), "notInfo");
            return FaceResult.success(actionStr, "人脸更新成功");
        } catch (Exception e) {
            return FaceResult.fail(actionStr, "人脸更新失败:" + e.getMessage());
        } finally {
            rgbMat.release();
        }
    }

    public static FaceRecognitionResponse faceRecognition(JSONObject obj) {
        Face.loadDbFace();
        String base64 = obj.getString("data");
        byte[] bytes = Base64.getDecoder().decode(base64);
        MatOfByte matOfByte = new MatOfByte(bytes);
        Mat rgbMat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);
        if (rgbMat.empty()) {
            throw new RuntimeException("图片为空");
        }
        long nativeObjAddr = rgbMat.getNativeObjAddr();
        String s = Face.identifyWithAllByMat(nativeObjAddr, 0);
        FaceRecognitionResponse faceRecognitionResponse = JSONObject.parseObject(s, FaceRecognitionResponse.class);

        System.out.println(s);
        return faceRecognitionResponse;
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
            Face.loadDbFace();
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

    /**
     * 初始化百度人脸数据库
     */
    public static void init() {
        System.out.println("开始--> 重新生成百度人脸数据库");
        long startTime = System.currentTimeMillis();
        UserService userService = new UserService();
        Map<String, String> userIdWithFacePath = userService.getUserIdWithFacePath();
        userIdWithFacePath.forEach((userName, facePath) -> {
            Mat mat = Imgcodecs.imread(facePath);
            long matAddr = mat.getNativeObjAddr();
            String res = Face.userAddByMat(matAddr, userName, systemConfig.getBaiduFaceDbDefaultGroup(), "无信息");
            System.out.println("----------------------------------");
            System.out.println(res);
            mat.release();
            System.out.println("用户名=" + userName + ", 人脸路径=" + facePath);
            System.out.println("----------------------------------");
        });
        long endTime = System.currentTimeMillis(); // 结束计时
        long duration = endTime - startTime;
        System.out.println("结束--> 重新生成百度人脸数据库，耗时：" + duration + " 毫秒");
    }


}
