package handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;


import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Iterator;

public class BinaryFrameHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    // 客户端发消息
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        try {
            String path = "C:\\Users\\Administrator\\Desktop\\test_tmp";
            ByteBuf buf = frame.content();
            int readableBytes = buf.readableBytes();

            System.out.println("收到二进制帧，大小: " + (readableBytes / 1024) + " KB");

            byte[] data = new byte[readableBytes];
            buf.readBytes(data);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                // 1. 读取图片（TwelveMonkeys 会自动接管）
                BufferedImage img = ImageIO.read(bais);

                if (img == null) {
                    System.err.println("错误：ImageIO.read 返回 null，数据开头 10 字节: " +
                            bytesToHex(data, 10));
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("error_decode"));
                    return;
                }

                // 调试信息：看看读出来的图像到底是什么样子
                System.out.println("解析成功 - 宽: " + img.getWidth() + ", 高: " + img.getHeight() +
                        ", 类型: " + img.getType() + " (1=RGB, 5=3BYTE_BGR, 0=未知/灰度)");

                File dir = new File(path);
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, System.currentTimeMillis() + ".jpg");

                // 2. 使用 TwelveMonkeys 的 JPEG 写入器（最关键）
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (!writers.hasNext()) {
                    System.err.println("错误：找不到 JPEG 写入器！请确认 TwelveMonkeys 已正确引入");
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("error_writer"));
                    return;
                }

                ImageWriter writer = writers.next(); // 优先拿到 TwelveMonkeys 的 writer
                ImageWriteParam param = writer.getDefaultWriteParam();

                // 设置高质量压缩
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.92f); // 和前端保持一致

                try (FileImageOutputStream output = new FileImageOutputStream(file)) {
                    writer.setOutput(output);
                    writer.write(null, new IIOImage(img, null, null), param);
                    System.out.println("使用 TwelveMonkeys 保存成功: " + file.getAbsolutePath());
                } finally {
                    writer.dispose(); // 必须释放
                }

                ctx.channel().writeAndFlush(new TextWebSocketFrame("ack"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.channel().writeAndFlush(new TextWebSocketFrame("error_server"));
        }
    }

    private String bytesToHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, len); i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString();
    }

    // 客户端连接
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("客户端连接：" + ctx.channel().id());
    }

    // 客户端断开
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("客户端断开：" + ctx.channel().id());
    }

    // 异常处理
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}