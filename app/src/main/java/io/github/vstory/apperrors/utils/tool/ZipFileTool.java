
package io.github.vstory.apperrors.utils.tool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class ZipFileTool {

    private static final int BUFF_SIZE = 2048;

    
    public static void zipMultiFile(String filePath, String zipPath, boolean isDirFlag) {
        try {
            File file = new File(filePath);
            File zipFile = new File(zipPath);
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File fileSec : files) {
                        if (isDirFlag) recursionZip(zipOut, fileSec, file.getName() + File.separator);
                        else recursionZip(zipOut, fileSec, "");
                    }
                }
            }
            zipOut.close();
        } catch (Exception ignored) {
        }
    }

    
    public static void unZipFile(String unZipPath, String zipPath) {
        try {
            unZipFileByInput(unZipPath, new FileInputStream(zipPath));
        } catch (Exception ignored) {
        }
    }

    
    private static void unZipFileByInput(String unZipPath, FileInputStream zips) {
        String path = createSeparator(unZipPath);
        BufferedOutputStream bos = null;
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new BufferedInputStream(zips));
            ZipEntry ze;
            byte[] buffer = new byte[BUFF_SIZE];
            int count;
            while ((ze = zis.getNextEntry()) != null) {
                String filename = ze.getName();
                createSubFolders(filename, path);
                if (ze.isDirectory()) {
                    File fmd = new File(path + filename);
                    fmd.mkdirs();
                    continue;
                }
                bos = new BufferedOutputStream(new FileOutputStream(path + filename));
                while ((count = zis.read(buffer)) != -1) bos.write(buffer, 0, count);
                bos.flush();
                bos.close();
            }
        } catch (IOException ignored) {
        } finally {
            try {
                if (zis != null) {
                    zis.closeEntry();
                    zis.close();
                }
                if (bos != null) bos.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void recursionZip(ZipOutputStream zipOut, File file, String baseDir) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File fileSec : files) recursionZip(zipOut, fileSec, baseDir + file.getName() + File.separator);
            }
        } else {
            try {
                byte[] buf = new byte[1024];
                InputStream input = new FileInputStream(file);
                zipOut.putNextEntry(new ZipEntry(baseDir + file.getName()));
                int len;
                while ((len = input.read(buf)) != -1) zipOut.write(buf, 0, len);
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void createSubFolders(String filename, String path) {
        String[] subFolders = filename.split("/");
        if (subFolders.length <= 1) return;
        String pathNow = path;
        for (int i = 0; i < subFolders.length - 1; i++) {
            pathNow = pathNow + subFolders[i] + "/";
            File fmd = new File(pathNow);
            if (fmd.exists()) continue;
            fmd.mkdirs();
        }
    }

    private static String createSeparator(String path) {
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
        return path.endsWith("/") ? path : path + "/";
    }

    private ZipFileTool() {}
}
