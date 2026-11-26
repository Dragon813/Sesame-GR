package io.github.Dragon813.sesame.hook;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.Dragon813.sesame.util.FileUtil;
import io.github.Dragon813.sesame.util.Log;

/**
 * 拼图截图与成功完整图归档：统一收进 sesame/puzzle.zip 单文件（与 avatar.zip / icon.zip 同思路），
 * 截图最多保留 {@link #MAX_ENTRIES} 张，成功完整图最多保留 {@link #MAX_COMPLETE} 张，超出时丢最旧的。
 * <p>PNG 本身已压缩，条目用 STORED 直接存字节，避免二次 deflate 的 CPU 开销；
 * 重写时旧条目流式拷贝，不会把整包读进内存。
 */
final class PuzzleSampleArchive {

    private static final String TAG = "PuzzleSampleArchive";
    /** 最多保留的截图张数。 */
    private static final int MAX_ENTRIES = 20;
    /** 成功完整图单独保留 20 张，目录仅作为 ZIP 条目前缀存在。 */
    private static final int MAX_COMPLETE = 20;
    private static final String COMPLETE_PREFIX = "puzzle-complete/";
    private static final Object LOCK = new Object();

    private PuzzleSampleArchive() {
    }

    /** 截图包绝对路径，用于日志提示。 */
    static String path() {
        return zipFile().getAbsolutePath();
    }

    /**
     * 保存一张截图。条目名以 matched 开头的会保留，其余（no-slider/no-track/match-failed）在验证结束时清理。
     *
     * @return 条目名；保存失败返回 null
     */
    static String save(Bitmap bitmap, String tag) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)) {
            return null;
        }
        byte[] data = buffer.toByteArray();
        String name = tag + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + ".png";
        return saveBytes(name, data);
    }

    /** 把验证成功后补齐缺口的完整图保存为 ZIP 内的 puzzle-complete/ 条目。 */
    static String saveComplete(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String name = COMPLETE_PREFIX + "complete-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + ".png";
        return saveBytes(name, data);
    }

    /** 读取 ZIP 内的成功完整图，供下一次同图对比使用。 */
    static byte[] read(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            File file = zipFile();
            if (!file.isFile()) {
                return null;
            }
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null || entry.isDirectory()) {
                    return null;
                }
                long size = entry.getSize();
                if (size <= 0 || size > 16L * 1024 * 1024) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) size);
                    copyStream(in, buffer);
                    return buffer.toByteArray();
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
                return null;
            }
        }
    }

    private static String saveBytes(String name, byte[] data) {
        synchronized (LOCK) {
            try {
                append(name, data);
                return name;
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
                // 包损坏时重建，至少保留这一张
                try {
                    deleteQuietly(zipFile());
                    append(name, data);
                    return name;
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
    }

    /** 删掉临时截图，保留 matched 截图和 puzzle-complete/ 成功完整图。 */
    static int deleteNonMatched() {
        synchronized (LOCK) {
            File zip = zipFile();
            if (!zip.isFile()) {
                return 0;
            }
            File tmp = new File(zip.getAbsolutePath() + ".tmp");
            int deleted = 0;
            try (ZipFile old = new ZipFile(zip);
                 ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
                Enumeration<? extends ZipEntry> en = old.entries();
                while (en.hasMoreElements()) {
                    ZipEntry entry = en.nextElement();
                    if (!entry.getName().startsWith("matched") && !isComplete(entry.getName())) {
                        deleted++;
                        continue;
                    }
                    copyEntry(old, entry, zos);
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
                tmp.delete();
                return 0;
            }
            if (deleted == 0) {
                tmp.delete();
                return 0;
            }
            if (!replace(tmp, zip)) {
                return 0;
            }
            return deleted;
        }
    }

    /** 追加一张，并按截图/成功完整图各自的限额裁掉该类最旧的条目。 */
    private static void append(String name, byte[] data) throws IOException {
        File zip = zipFile();
        File tmp = new File(zip.getAbsolutePath() + ".tmp");
        boolean complete = isComplete(name);
        int total = 0;
        if (zip.isFile()) {
            try (ZipFile old = new ZipFile(zip)) {
                Enumeration<? extends ZipEntry> en = old.entries();
                while (en.hasMoreElements()) {
                    if (isComplete(en.nextElement().getName()) == complete) {
                        total++;
                    }
                }
            }
        }
        int limit = complete ? MAX_COMPLETE : MAX_ENTRIES;
        int drop = Math.max(0, total + 1 - limit);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
            if (zip.isFile()) {
                try (ZipFile old = new ZipFile(zip)) {
                    int index = 0;
                    Enumeration<? extends ZipEntry> en = old.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry entry = en.nextElement();
                        if (isComplete(entry.getName()) == complete && index++ < drop) {
                            continue;
                        }
                        copyEntry(old, entry, zos);
                    }
                }
            }
            writeEntry(zos, name, data);
        }
        if (!replace(tmp, zip)) {
            throw new IOException("截图包写入失败");
        }
    }

    /** 拷贝旧条目（STORED，沿用旧包里的 size/crc，不再重新压缩）。 */
    private static void copyEntry(ZipFile old, ZipEntry entry, ZipOutputStream zos) throws IOException {
        ZipEntry copy = new ZipEntry(entry.getName());
        copy.setMethod(ZipEntry.STORED);
        copy.setSize(entry.getSize());
        copy.setCompressedSize(entry.getSize());
        copy.setCrc(entry.getCrc());
        zos.putNextEntry(copy);
        try (InputStream in = old.getInputStream(entry)) {
            copyStream(in, zos);
        }
        zos.closeEntry();
    }

    private static void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static boolean replace(File tmp, File zip) {
        if (zip.exists() && !zip.delete()) {
            tmp.delete();
            return false;
        }
        if (tmp.renameTo(zip)) {
            return true;
        }
        try (InputStream in = new FileInputStream(tmp);
             OutputStream out = new FileOutputStream(zip)) {
            copyStream(in, out);
            out.flush();
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        } finally {
            tmp.delete();
        }
    }

    private static boolean isComplete(String name) {
        return name.startsWith(COMPLETE_PREFIX);
    }

    private static File zipFile() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, "puzzle.zip");
    }

    private static void deleteQuietly(File file) {
        try {
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }
}
