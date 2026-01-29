package handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import entity.FaceRequest;
import entity.FaceResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import handler.manager.ConnectionManager;
import handler.manager.FaceApiManager;
import utils.JsonUtils;

import java.util.HashMap;
import java.util.Map;

public class TextFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws JsonProcessingException {
        System.out.println("channelRead0当前所在线程Id:" + Thread.currentThread().getId());
        String originalText = frame.text();
        FaceRequest req;
        try {
            // 返序列号
            req = JsonUtils.MAPPER.readValue(originalText, FaceRequest.class);
        } catch (Exception e) {
            // JSON 非法，直接返回
            ctx.writeAndFlush(
                    new TextWebSocketFrame("{\"code\":400,\"msg\":\"非法JSON\"}")
            );
            return;
        }

        /**
         * 激活百度人脸sdk
         */
        if (("activation".equals(req.getAction()))) {
            FaceResult result = FaceApiManager.activateSDK(req);
            ctx.channel().writeAndFlush(
                    new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(result))
            );
            return;
        }
        /**
         * 获取激活状态
         */
        if ("activationStatus".equals(req.getAction())) {
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
                    new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(faceResult))
            );
            return;
        }
        /**
         * 下面的所有动作都需要百度人脸已经激活
         */
        if (FaceApiManager.sdkInitCode != 0) {
            FaceResult faceResult = FaceResult.fail(req.getAction(), FaceApiManager.getErrorText(FaceApiManager.sdkInitCode));
            ctx.channel().writeAndFlush(
                    new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(faceResult))
            );
            return;
        }
        switch (req.getAction()) {
            case "auth": {
                FaceResult auth = FaceHandler.auth(req);
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(auth))
                );
                break;
            }

            case "capture": {
                FaceResult result = FaceHandler.capture(req);
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(result))
                );
                break;
            }
            case "restart": {
                FaceApiManager.load();
                FaceResult faceResult = FaceResult.success(req.getAction(), null);
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(faceResult))
                );
                break;
            }

            case "update": {
                FaceResult result = FaceHandler.update(req);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(result)));
                break;
            }

            default: {
                Map<String, Object> res = new HashMap<>();
                res.put("reply", "unknown_action");

                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(JsonUtils.MAPPER.writeValueAsString(res))
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
