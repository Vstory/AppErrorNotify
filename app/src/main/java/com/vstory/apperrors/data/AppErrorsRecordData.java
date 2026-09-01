
package com.vstory.apperrors.data;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.vstory.apperrors.bean.AppErrorsInfoBean;
import com.vstory.apperrors.utils.factory.FunctionFactoryKt;
import com.vstory.apperrors.wrapper.BuildConfigWrapper;
import com.google.gson.Gson;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class AppErrorsRecordData {

    
    private static final String FOLDER_BASE = "/data/misc/";

    
    private static final String FOLDER_PREFIX = "apperrors_";

    
    private static final String ID_FILE_PATH = FOLDER_BASE + "apperrors_dir_id";

    
    private static final int RANDOM_LENGTH = 16;

    
    private static final char[] RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private static final Gson gson = new Gson();

    
    private static Context context;

    
    private static volatile boolean isSystemServerMode = true;

    
    private static volatile String folderPath;

    
    public static CopyOnWriteArrayList<AppErrorsInfoBean> allData = new CopyOnWriteArrayList<>();

    private static void log(String msg, Throwable e) {
        
        
        android.util.Log.i("AppErrorNotify", msg != null ? msg : "", e);
    }

    

    
    private static String generateRandomId() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) sb.append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]);
        return sb.toString();
    }

    
    private static long moduleFirstInstallTime(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(BuildConfigWrapper.APPLICATION_ID, 0);
            return pi.firstInstallTime;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    
    private static String readIdFile() {
        try {
            File f = new File(ID_FILE_PATH);
            if (!f.exists()) return null;
            String content = readFile(f);
            if (content == null) return null;
            String trimmed = content.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    
    private static boolean writeIdFile(String content) {
        try {
            File f = new File(ID_FILE_PATH);
            writeFile(f, content);
            try {
                f.setReadable(true, false);
                f.setWritable(true, false);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    
    private static String resolveFolderPath(Context ctx, boolean canGenerate) {
        
        if (canGenerate && folderPath != null) return folderPath;
        String id = null;
        String idContent = null;
        
        String content = readIdFile();
        if (content != null) {
            String[] parts = content.split(":", 2);
            if (parts.length == 2 && parts[1] != null && parts[1].matches("[A-Za-z0-9]{8,32}")) {
                id = parts[1];
                idContent = content;
            }
        }
        
        if (id != null && canGenerate) {
            long installTime = moduleFirstInstallTime(ctx);
            if (installTime >= 0 && idContent != null && idContent.startsWith(installTime + ":")) {
                folderPath = FOLDER_BASE + FOLDER_PREFIX + id + "/";
                return folderPath;
            }
            
        }
        
        if (!canGenerate) {
            if (id != null) return FOLDER_BASE + FOLDER_PREFIX + id + "/";
            return null;
        }
        
        String existing = findExistingDataFolder();
        if (existing != null) {
            folderPath = existing;
            
            long installTime = moduleFirstInstallTime(ctx);
            if (installTime >= 0) {
                String suffix = existing.substring(FOLDER_BASE.length() + FOLDER_PREFIX.length());
                if (suffix.endsWith("/")) suffix = suffix.substring(0, suffix.length() - 1);
                writeIdFile(installTime + ":" + suffix);
            }
            return folderPath;
        }
        
        String newId = generateRandomId();
        long installTime = moduleFirstInstallTime(ctx);
        String newContent = installTime + ":" + newId;
        
        writeIdFile(newContent);
        folderPath = FOLDER_BASE + FOLDER_PREFIX + newId + "/";
        log("App errors records folder created: " + folderPath, null);
        return folderPath;
    }

    
    private static String findExistingDataFolder() {
        try {
            File base = new File(FOLDER_BASE);
            File[] dirs = base.listFiles();
            if (dirs == null) return null;
            String best = null;
            long bestTime = -1;
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                String name = d.getName();
                if (!name.startsWith(FOLDER_PREFIX)) continue;
                
                File[] children = d.listFiles();
                if (children == null || children.length == 0) continue;
                if (d.lastModified() > bestTime) {
                    bestTime = d.lastModified();
                    best = d.getAbsolutePath() + "/";
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    
    private static File errorsInfoDataFolder() {
        String p = folderPath != null ? folderPath : resolveFolderPath(context, isSystemServerMode);
        return p != null ? new File(p) : null;
    }

    
    private static List<File> errorsInfoDataFiles() {
        File folder = errorsInfoDataFolder();
        if (folder == null) return new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return list;
    }

    
    private static void initializeDataDirectory() {
        try {
            File folder = errorsInfoDataFolder();
            if (folder == null) return;
            if (!folder.exists() || folder.isFile()) {
                folder.delete();
                folder.mkdirs();
            }
            
            try {
                folder.setReadable(true, false);
                folder.setWritable(true, false);
                folder.setExecutable(true, false);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log("Can't create directory \"" + FOLDER_BASE + "\", there will be problems with the app errors records function", t);
        }
    }

    
    private static CopyOnWriteArrayList<AppErrorsInfoBean> readAllDataFromFiles() {
        CopyOnWriteArrayList<AppErrorsInfoBean> result = new CopyOnWriteArrayList<>();
        try {
            for (File f : errorsInfoDataFiles()) {
                try {
                    String json = readFile(f);
                    AppErrorsInfoBean bean = gson.fromJson(json, AppErrorsInfoBean.class);
                    if (bean != null) result.add(bean);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            log("Read app errors records failed", t);
        }
        return result;
    }

    
    private static String readFile(File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            try {
                byte[] data = new byte[(int) file.length()];
                int len = fis.read(data);
                return new String(data, 0, Math.max(len, 0), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                fis.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    
    private static void writeFile(File file, String content) throws java.io.IOException {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
        try {
            fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
    }

    
    public static java.util.List<AppErrorsInfoBean> latestFromFiles() {
        return new java.util.ArrayList<>(readAllDataFromFiles());
    }

    
    public static void init(Context context) {
        AppErrorsRecordData.context = context;
        isSystemServerMode = true;
        resolveFolderPath(context, true);
        initializeDataDirectory();
        allData = readAllDataFromFiles();
    }

    
    public static void add(AppErrorsInfoBean bean) {
        allData.add(0, bean);
        File folder = errorsInfoDataFolder();
        if (folder == null) {
            log("App errors records folder not ready", null);
            return;
        }
        try {
            File file = new File(folder.getAbsolutePath(), bean.getJsonFileName());
            writeFile(file, gson.toJson(bean));
            
            try {
                file.setReadable(true, false);
                file.setWritable(true, false);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log("Save app errors records failed", t);
        }
    }

    
    public static void remove(AppErrorsInfoBean bean) {
        allData.remove(bean);
        File folder = errorsInfoDataFolder();
        if (folder == null) return;
        try {
            File file = new File(folder.getAbsolutePath(), bean.getJsonFileName());
            file.delete();
        } catch (Throwable ignored) {
        }
    }

    
    public static void clearAll() {
        allData.clear();
        File folder = errorsInfoDataFolder();
        if (folder == null) return;
        try {
            if (folder.exists()) deleteRecursively(folder);
            initializeDataDirectory();
        } catch (Throwable ignored) {
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    
    public static void fetchFromSystemServer(final android.content.Context context,
                                             final android.content.BroadcastReceiver resultReceiver) {
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction("com.vstory.apperrors.action.ERRORS_RESULT");
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final boolean[] finished = {false};       
            final boolean[] gotEmpty = {false};       
            final android.content.BroadcastReceiver[] holder = new android.content.BroadcastReceiver[1];
            
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                    if (intent == null) return;
                    Object extra = FunctionFactoryKt.getSerializableExtraCompat(intent, "errors");
                    
                    
                    
                    if (!(extra instanceof java.util.List)) return;   
                    java.util.List<?> raw = (java.util.List<?>) extra;
                    
                    final java.util.List<AppErrorsInfoBean> accepted = new java.util.ArrayList<>();
                    for (Object o : raw) if (o instanceof AppErrorsInfoBean) accepted.add((AppErrorsInfoBean) o);
                    
                    if (!accepted.isEmpty()) {
                        allData = new CopyOnWriteArrayList<>(accepted);
                        if (!finished[0]) {
                            finished[0] = true;
                            try { ctx.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                            if (resultReceiver != null) resultReceiver.onReceive(ctx, intent);
                        }
                        return;
                    }
                    
                    gotEmpty[0] = true;
                    handler.postDelayed(() -> {
                        if (!finished[0]) {
                            finished[0] = true;
                            try { if (holder[0] != null) context.unregisterReceiver(holder[0]); } catch (Throwable ignored) {}
                            
                            allData = new CopyOnWriteArrayList<>();
                            if (resultReceiver != null) resultReceiver.onReceive(context, null);
                        }
                    }, 600L);   
                }
            };
            holder[0] = receiver;
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            
            
            
            android.content.Intent request = new android.content.Intent("com.vstory.apperrors.action.GET_ERRORS");
            context.sendBroadcast(request);
        } catch (Throwable t) {
            log("Fetch app errors records from system server failed", t);
            if (resultReceiver != null) resultReceiver.onReceive(null, null);
        }
    }

    
    public static String getFolderPathForLog() {
        return folderPath;
    }

    

    
    public static void requestClearAll(android.content.Context context) {
        try {
            android.content.Intent req = new android.content.Intent("com.vstory.apperrors.action.CLEAR_ERRORS");
            context.sendBroadcast(req);
        } catch (Throwable t) {
            log("Request clear all failed", t);
        }
    }

    
    public static void requestRemove(android.content.Context context, AppErrorsInfoBean bean) {
        try {
            android.content.Intent req = new android.content.Intent("com.vstory.apperrors.action.REMOVE_ERROR");
            req.putExtra("bean", bean);
            context.sendBroadcast(req);
        } catch (Throwable t) {
            log("Request remove failed", t);
        }
    }

    private AppErrorsRecordData() {}
}
