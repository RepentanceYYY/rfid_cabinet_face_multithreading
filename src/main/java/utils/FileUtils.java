package utils;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import entity.FaceRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileUtils {

    private static final ObjectMapper DUMP_MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void dumpRequestOnce(FaceRequest request) {
        File file = new File("D:\\face-native\\rfid_cabinet_face_dump.txt");

        if (file.exists()) {
            file.delete();
        }

        try (FileWriter writer = new FileWriter(file, false)) {

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            writer.write("-------- " + request.getAction() + " | " + timestamp + " -----------------------");
            writer.write(System.lineSeparator());

            String prettyJson = DUMP_MAPPER.writeValueAsString(request);
            writer.write(prettyJson);

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

