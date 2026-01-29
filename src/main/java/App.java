import config.SystemConfig;
import handler.manager.FaceApiManager;
import server.WebSocketServer;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import java.util.Iterator;

public class App {


    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        SystemConfig systemConfig = SystemConfig.getInstance();
        // 强制扫描并注册 TwelveMonkeys 插件
        ImageIO.scanForPlugins();
        // 调试：打印当前可用的 JPEG 写入器
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        while (writers.hasNext()) {
            ImageWriter w = writers.next();
            System.out.println("JPEG 写入器启动成功: " + w.getClass().getName());
        }
        /*  sdk初始化 */
        FaceApiManager.load();
        WebSocketServer server = new WebSocketServer(systemConfig.getWebSocketPort());
        long endTime = System.currentTimeMillis();
        // JVM 关闭时优雅停机
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            FaceApiManager.destroy();
            System.out.println("收到关闭信号，正在关闭 WebSocket server...");
            server.shutdown();
        }));
        try {
            server.start();
            long duration = endTime - startTime;
            long totalSeconds = duration / 1000;

            System.out.println("服务启动成功，总共花费时间: " + totalSeconds + " 秒");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
