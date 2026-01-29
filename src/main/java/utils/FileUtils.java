package utils;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileUtils {

    public static void dumpJSONObjectOnce(String action, JSONObject obj) {
        File file = new File("D:\\face-native\\rfid_cabinet_face_dump.txt");

        if (file.exists()) {
            file.delete();
        }

        try (FileWriter writer = new FileWriter(file, false)) {
            // 1. 获取带毫秒的时间戳
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

            // 2. 写入头部信息
            writer.write("-------- " + action + " | " + timestamp + " -----------------------");
            writer.write(System.lineSeparator());

            // 3. 正确序列化 JSON（使用 Fastjson2 的枚举特性）
            writer.write(obj.toJSONString(JSONWriter.Feature.PrettyFormat));
            writer.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteDirectory(Path dir) {
        if (Files.exists(dir)) {
            try {
                deleteDirectoryRecursively(dir);
                System.out.println("目录已删除：" + dir.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("目录删除失败：" + dir.toAbsolutePath());
                e.printStackTrace();
            }
        } else {
            System.out.println("目录不存在，无需删除：" + dir.toAbsolutePath());
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    // 去掉只读属性
                    file.toFile().setWritable(true);
                    Files.delete(file);
                    System.out.println("删除文件: " + file.toAbsolutePath());
                } catch (IOException e) {
                    System.out.println("删除文件失败: " + file.toAbsolutePath());
                    throw e;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                try {
                    dir.toFile().setWritable(true);
                    Files.delete(dir);
                    System.out.println("删除目录: " + dir.toAbsolutePath());
                } catch (IOException e) {
                    System.out.println("删除目录失败: " + dir.toAbsolutePath());
                    throw e;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

}

