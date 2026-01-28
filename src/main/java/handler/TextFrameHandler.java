package handler;

import entity.FaceResult;
import entity.Reply;
import entity.baidu.FaceRecognitionResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import handler.manager.ConnectionManager;
import handler.manager.FaceApiManager;

import java.util.HashMap;
import java.util.Map;

public class TextFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        System.out.println("Thread [" + Thread.currentThread().getName() + "] handling message...");
        String originalText = frame.text();
        JSONObject obj = JSONObject.parseObject(originalText);
        String action = obj.getString("action");
        /**
         * 激活百度人脸sdk
         */
        if (action.equals("activation")) {
            FaceResult result = FaceApiManager.activateSDK("activation", obj);
            ctx.channel().writeAndFlush(
                    new TextWebSocketFrame(JSON.toJSONString(result))
            );
            return;
        }
        /**
         * 获取激活状态
         */
        if (action.equals("activationStatus")) {
            String actionStr = "activationStatus";
            Map<Object, Object> dataMap = new HashMap<>();
            if (FaceApiManager.sdkInitCode <= 1015 && FaceApiManager.sdkInitCode >= -1019) {
                dataMap.put("needActivation", true);
                dataMap.put("activationCode", FaceApiManager.queryActivationCode());
            }
            FaceResult faceResult;
            dataMap.put("sdkInitCode", FaceApiManager.sdkInitCode);
            if (FaceApiManager.sdkInitCode != 0) {
                faceResult = FaceResult.fail(actionStr, FaceApiManager.getErrorText(FaceApiManager.sdkInitCode), dataMap);

            } else {
                faceResult = FaceResult.success(actionStr, "百度人脸SDK已激活", dataMap);
            }
            ctx.channel().writeAndFlush(
                    new TextWebSocketFrame(JSON.toJSONString(faceResult))
            );
            return;
        }
        /**
         * 下面的所有动作都需要百度人脸已经激活
         */
        if (FaceApiManager.sdkInitCode != 0) {
            FaceResult faceResult = FaceResult.fail(action, FaceApiManager.getErrorText(FaceApiManager.sdkInitCode));
            ctx.channel().writeAndFlush(
                    new TextWebSocketFrame(JSON.toJSONString(faceResult))
            );
            return;
        }
        switch (action) {
            case "auth": {
                FaceResult auth = FaceHandler.auth(obj);
                String resultStr = JSON.toJSONString(auth);
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(resultStr)
                );
                break;
            }

            case "capture": {
                FaceResult result = FaceHandler.capture(obj);
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JSON.toJSONString(result))
                );
                break;
            }
            case "restart": {
                FaceApiManager.load();
                Reply restart = new Reply();
                restart.setType("restart");
                restart.setSuccessMessage("重启百度人脸服务成功");
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JSON.toJSONString(restart))
                );
                break;
            }

            case "update": {
                FaceResult result = FaceHandler.update(obj);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(result)));
                break;
            }

            case "faceRecognition": {
                try {
                    FaceRecognitionResponse faceRecognitionResponse =
                            FaceHandler.faceRecognition(obj);

                    Map<String, Object> res = new HashMap<>();
                    res.put("reply", "ack");
                    res.put("type", "faceRecognition");
                    res.put("data", faceRecognitionResponse);

                    ctx.channel().writeAndFlush(
                            new TextWebSocketFrame(JSON.toJSONString(res))
                    );
                } catch (Exception e) {
                    e.printStackTrace();

                    Map<String, Object> res = new HashMap<>();
                    res.put("reply", "error");

                    ctx.channel().writeAndFlush(
                            new TextWebSocketFrame(JSON.toJSONString(res))
                    );
                }
                break;
            }

            default: {
                Map<String, Object> res = new HashMap<>();
                res.put("reply", "unknown_type");

                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JSON.toJSONString(res))
                );
                break;
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("客户端连接：" + ctx.channel().id());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("客户端断开：" + ctx.channel().id());
        ConnectionManager.all().forEach(ch -> {
            if (ch == ctx.channel()) {
                ConnectionManager.remove(ch.id().asLongText());
            }
        });
    }

}
