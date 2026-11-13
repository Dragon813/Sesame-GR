package io.github.lazyimmortal.sesame.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 图片 zip 单文件缓存：把大量小图片收进一个 .zip，避免缓存目录里散落海量碎文件。
 * 条目名 = md5(url) + "." + 图片真实后缀（按文件头嗅探 png/jpg/gif/webp/bmp），
 * zip 文件本身带 .zip 后缀，系统相册不会索引其中内容。
 * 写入时保留全部旧条目（不做孤儿清理）；读单条按偏移随机访问，不解压整包。
 */
public class ImageZipCache {

    private static final String TAG = "ImageZipCache";

    /** 网络下载并发度 */
    private static final ExecutorService downloadPool = Executors.newFixedThreadPool(4);
    /** 写包串行，避免并发重写整个 zip */
    private static final ExecutorService writer = Executors.newSingleThreadExecutor();
    /** 写包互斥锁（同步写入路径共用） */
    private static final Object writeLock = new Object();

    private final File zipFile;

    public ImageZipCache(File zipFile) {
        this.zipFile = zipFile;
    }

    /** 已缓存的图片（不联网），未命中返回 null */
    public Bitmap load(String url) {
        byte[] data = readEntry(url);
        return data == null ? null : BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    /** 已缓存直接解码，缺失则同步下载并写入 zip */
    public Bitmap loadOrDownload(String url) {
        Bitmap bmp = load(url);
        if (bmp != null) {
            return bmp;
        }
        byte[] data = download(url);
        if (data == null) {
            return null;
        }
        Bitmap result = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (result != null) {
            List<ImageEntry> entries = new ArrayList<>(1);
            entries.add(new ImageEntry(entryName(url, data), data));
            writeEntries(entries);
        }
        return result;
    }

    /** 批量异步预下载：跳过已缓存条目，全部下载完成后一次性写入 zip */
    public void predownload(final Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        final List<String> targets = new ArrayList<>(urls);
        writer.execute(() -> {
            try {
                Set<String> cached = cachedDigests();
                List<String> missing = new ArrayList<>();
                for (String url : targets) {
                    if (url != null && url.startsWith("http") && !cached.contains(digest(url))) {
                        missing.add(url);
                    }
                }
                if (missing.isEmpty()) {
                    return;
                }
                List<Future<byte[]>> futures = new ArrayList<>(missing.size());
                for (String url : missing) {
                    futures.add(downloadPool.submit(() -> download(url)));
                }
                List<ImageEntry> entries = new ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    byte[] data = futures.get(i).get();
                    if (data != null) {
                        entries.add(new ImageEntry(entryName(missing.get(i), data), data));
                    }
                }
                if (!entries.isEmpty()) {
                    writeEntries(entries);
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        });
    }

    /* 读取条目的原始字节，条目可能带后缀(md5.xxx)也可能无后缀(md5) */
    private byte[] readEntry(String url) {
        if (url == null || url.isEmpty() || !zipFile.isFile()) {
            return null;
        }
        String dg = digest(url);
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String name = entry.getName();
                if (name.equals(dg) || name.startsWith(dg + ".")) {
                    try (InputStream in = zf.getInputStream(entry)) {
                        return readAll(in);
                    }
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            closeQuietly(zf);
        }
        return null;
    }

    private Set<String> cachedDigests() {
        Set<String> set = new HashSet<>();
        if (!zipFile.isFile()) {
            return set;
        }
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                set.add(name.contains(".") ? name.substring(0, name.indexOf('.')) : name);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            closeQuietly(zf);
        }
        return set;
    }

    /* 重写 zip：复制全部旧条目(同名/同摘要的跳过) + 追加新条目，旧图不清理 */
    private void writeEntries(List<ImageEntry> entries) {
        synchronized (writeLock) {
            File tmp = new File(zipFile.getAbsolutePath() + ".tmp");
            Set<String> newNames = new HashSet<>();
            Set<String> newDigests = new HashSet<>();
            for (ImageEntry entry : entries) {
                newNames.add(entry.name);
                newDigests.add(digestOfEntry(entry.name));
            }
            ZipFile oldZip = null;
            try {
                if (zipFile.isFile()) {
                    oldZip = new ZipFile(zipFile);
                }
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
                    if (oldZip != null) {
                        Enumeration<? extends ZipEntry> en = oldZip.entries();
                        while (en.hasMoreElements()) {
                            ZipEntry old = en.nextElement();
                            String name = old.getName();
                            if (newNames.contains(name) || newDigests.contains(digestOfEntry(name))) {
                                continue;
                            }
                            zos.putNextEntry(new ZipEntry(name));
                            try (InputStream in = oldZip.getInputStream(old)) {
                                copy(in, zos);
                            }
                            zos.closeEntry();
                        }
                    }
                    for (ImageEntry entry : entries) {
                        zos.putNextEntry(new ZipEntry(entry.name));
                        zos.write(entry.data);
                        zos.closeEntry();
                    }
                }
                closeQuietly(oldZip);
                oldZip = null;
                if (zipFile.exists() && !zipFile.delete()) {
                    Log.printStackTrace(TAG, new IOException("旧缓存包删除失败"));
                }
                if (!tmp.renameTo(zipFile)) {
                    try (InputStream in = new FileInputStream(tmp)) {
                        try (OutputStream out = new FileOutputStream(zipFile)) {
                            copy(in, out);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            } finally {
                closeQuietly(oldZip);
                tmp.delete();
            }
        }
    }

    private static String digestOfEntry(String entryName) {
        return entryName.contains(".") ? entryName.substring(0, entryName.indexOf('.')) : entryName;
    }

    private static String entryName(String url, byte[] data) {
        return digest(url) + "." + sniffExt(data);
    }

    private static byte[] download(String url) {
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return readAll(in);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 按文件头判断图片真实格式，用于给条目补后缀 */
    private static String sniffExt(byte[] d) {
        if (d == null || d.length < 4) {
            return "img";
        }
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "png";
        }
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return "gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        if (d[0] == 'B' && d[1] == 'M') {
            return "bmp";
        }
        return "img";
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    private static void closeQuietly(ZipFile zf) {
        if (zf != null) {
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String digest(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(url.hashCode());
        }
    }

    private static final class ImageEntry {
        final String name;
        final byte[] data;

        ImageEntry(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}
