package utils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {

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

