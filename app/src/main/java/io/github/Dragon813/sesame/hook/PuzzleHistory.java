package io.github.Dragon813.sesame.hook;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.Dragon813.sesame.util.FileUtil;
import io.github.Dragon813.sesame.util.Log;

/**
 * 拼图历史库（第三种判断依据之外的两条补充）：
 * <ol>
 *   <li>每次验证成功后把“补齐缺口后的完整图”保存到 puzzle.zip 的 puzzle-complete/ 条目（独立 20 张上限）；</li>
 *   <li>在 sesame/puzzle.json 里按图像记录成功过的缺口位移（同一张图可能有多处）。</li>
 * </ol>
 * 判断顺序：同图完整图差异（第二条）优先 → 历史记录（第一条）与算法互相校验 → 算法（第三条）。
 */
final class PuzzleHistory {

    private static final String TAG = "PuzzleHistory";
    private static final String JSON_NAME = "puzzle.json";
    private static final int MAX_IMAGES = 40;
    /** 两张截图算“同一张图”的签名平均差上限（每通道）。 */
    private static final double SIGNATURE_TOLERANCE = 12.0;
    /** 差异图里判定“有变化”的灰度阈值。 */
    private static final int DIFF_THRESHOLD = 26;
    /** 照片区相对滑块按钮的上下边界（参考坐标，随高度缩放）。 */
    private static final int PHOTO_TOP_FROM_SLIDER = -1120;
    private static final int PHOTO_BOTTOM_FROM_SLIDER = -190;
    private static final Object LOCK = new Object();

    private PuzzleHistory() {
    }

    static final class Position {
        int d;
        int n;

        Position(int d, int n) {
            this.d = d;
            this.n = n;
        }
    }

    static final class ImageEntry {
        String signature;
        int[] blocks;
        int width;
        int height;
        final List<Position> positions = new ArrayList<>();
        String complete;
        int filledLeft = -1;
        int filledTop = -1;
        int filledRight = -1;
        int filledBottom = -1;
    }

    /** 成功后可落盘的数据：完整图 PNG 在 prepare() 里补齐，commit() 里写入。 */
    static final class Pending {
        final String signature;
        final int width;
        final int height;
        final int displacement;
        final int sourceLeft;
        final int sourceTop;
        final int sourceRight;
        final int sourceBottom;
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile byte[] completedPng;

        Pending(String signature, int width, int height, int displacement,
                int sourceLeft, int sourceTop, int sourceRight, int sourceBottom) {
            this.signature = signature;
            this.width = width;
            this.height = height;
            this.displacement = displacement;
            this.sourceLeft = sourceLeft;
            this.sourceTop = sourceTop;
            this.sourceRight = sourceRight;
            this.sourceBottom = sourceBottom;
        }
    }

    static final class Choice {
        final PuzzleSliderMatcher.Result result;
        final Pending pending;

        Choice(PuzzleSliderMatcher.Result result, Pending pending) {
            this.result = result;
            this.pending = pending;
        }
    }

    /**
     * 综合三种依据给出最终位移。任何异常都退回算法结果，不影响原有流程。
     */
    static Choice choose(Bitmap bitmap, int sourceLeft, float sliderScreenY, int screenTop,
                         PuzzleSliderMatcher.Result algorithm) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return new Choice(algorithm, null);
        }
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = width / 1264f;
            int sliderY = Math.round(sliderScreenY - screenTop);
            int[] signature = signature(bitmap, sourceLeft, scale, sliderY);
            if (signature == null) {
                return new Choice(algorithm, null);
            }
            ImageEntry entry;
            synchronized (LOCK) {
                entry = nearest(loadLocked(), signature);
            }
            int expected = Math.max(40, Math.round(180 * scale));
            int[] diff = entry == null ? null
                    : diffGap(bitmap, entry, sourceLeft, scale, sliderY, expected);
            int recorded = entry == null ? -1 : bestPosition(entry, width);
            int nearTolerance = Math.max(10, Math.round(20 * scale));

            int displacement;
            String source;
            if (diff != null) {
                displacement = diff[4];
                source = "history-diff";
            } else if (algorithm != null && algorithm.success) {
                if (recorded >= 0 && Math.abs(algorithm.displacement - recorded) <= nearTolerance) {
                    displacement = recorded;
                    source = "history-record";
                } else {
                    displacement = algorithm.displacement;
                    source = algorithm.method;
                }
            } else if (recorded >= 0) {
                displacement = recorded;
                source = "history-record-only";
            } else {
                return new Choice(algorithm, null);
            }

            // 用于成功后补齐完整图的源块区域。
            int templateWidth = expected;
            int templateHeight = expected;
            int sourceTop = sliderY + Math.round(-707 * scale);
            boolean regionKnown = false;
            if (algorithm != null && algorithm.success && algorithm.templateWidth > 0
                    && algorithm.templateHeight > 0) {
                templateWidth = algorithm.templateWidth;
                templateHeight = algorithm.templateHeight;
                sourceTop = algorithm.sourceTop;
                regionKnown = true;
            } else if (diff != null) {
                templateWidth = Math.max(40, diff[2] - diff[0] + 1);
                templateHeight = Math.max(40, diff[3] - diff[1] + 1);
                sourceTop = diff[1];
                regionKnown = true;
            } else {
                int[] piece = PuzzleSliderMatcher.detectPiece(bitmap, sliderScreenY, screenTop, sourceLeft);
                if (piece != null) {
                    templateWidth = Math.max(40, piece[2] - piece[0] + 1);
                    templateHeight = Math.max(40, piece[3] - piece[1] + 1);
                    sourceTop = piece[1];
                    regionKnown = true;
                }
            }

            Pending pending = regionKnown
                    ? new Pending(toHex(signature), width, height, displacement,
                    sourceLeft, Math.max(0, sourceTop), sourceLeft + templateWidth - 1,
                    Math.max(0, sourceTop) + templateHeight - 1)
                    : null;

            PuzzleSliderMatcher.Result result;
            if (algorithm != null && algorithm.success && displacement == algorithm.displacement
                    && source.equals(algorithm.method)) {
                result = algorithm;
            } else {
                result = PuzzleSliderMatcher.synthetic(displacement, source,
                        diff != null ? 0.90f : 0.75f, sourceLeft, Math.max(0, sourceTop),
                        templateWidth, templateHeight);
            }
            return new Choice(result, pending);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return new Choice(algorithm, null);
        }
    }

    /** 工作线程：把源块复制到缺口位置，压成 PNG，供成功时保存。 */
    static void prepare(Bitmap bitmap, Pending pending) {
        if (pending == null) {
            return;
        }
        try {
            if (bitmap == null || bitmap.isRecycled()
                    || pending.sourceRight < pending.sourceLeft
                    || pending.sourceBottom < pending.sourceTop) {
                return;
            }
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Bitmap completed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(completed);
                canvas.drawBitmap(bitmap, 0, 0, null);
                Rect src = new Rect(pending.sourceLeft, pending.sourceTop,
                        pending.sourceRight + 1, pending.sourceBottom + 1);
                Rect dst = new Rect(pending.sourceLeft + pending.displacement, pending.sourceTop,
                        pending.sourceRight + 1 + pending.displacement, pending.sourceBottom + 1);
                canvas.drawBitmap(bitmap, src, dst, null);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                if (completed.compress(Bitmap.CompressFormat.PNG, 100, buffer)) {
                    pending.completedPng = buffer.toByteArray();
                }
            } finally {
                completed.recycle();
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            pending.ready.countDown();
        }
    }

    /** 工作线程：验证成功后记录位移，并在有完整图时落盘。 */
    static void commit(Pending pending) {
        if (pending == null) {
            return;
        }
        try {
            pending.ready.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            synchronized (LOCK) {
                List<ImageEntry> all = loadLocked();
                int[] blocks = fromHex(pending.signature);
                ImageEntry entry = null;
                if (blocks != null) {
                    entry = nearest(all, blocks);
                }
                if (entry == null) {
                    entry = new ImageEntry();
                    entry.signature = pending.signature;
                    entry.blocks = blocks;
                    entry.width = pending.width;
                    entry.height = pending.height;
                    all.add(entry);
                    while (all.size() > MAX_IMAGES) {
                        all.remove(0);
                    }
                }
                mergePosition(entry, pending.displacement);
                if (pending.completedPng != null) {
                    String name = PuzzleSampleArchive.saveComplete(pending.completedPng);
                    if (name != null) {
                        entry.complete = name;
                        entry.filledLeft = pending.sourceLeft + pending.displacement;
                        entry.filledTop = pending.sourceTop;
                        entry.filledRight = pending.sourceRight + pending.displacement;
                        entry.filledBottom = pending.sourceBottom;
                    }
                }
                saveLocked(all);
                Log.i(TAG, "已记录成功位移=" + pending.displacement
                        + "（该图历史位置=" + entry.positions.size() + " 处"
                        + (pending.completedPng != null ? "，已保存完整图" : "") + "）");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    // ===================== 历史记录 =====================

    private static ImageEntry nearest(List<ImageEntry> entries, int[] blocks) {
        ImageEntry best = null;
        double bestDiff = Double.MAX_VALUE;
        for (ImageEntry entry : entries) {
            double diff = signatureDiff(entry.blocks, blocks);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = entry;
            }
        }
        return bestDiff <= SIGNATURE_TOLERANCE ? best : null;
    }

    /** 选出现次数最多的位置，按当前宽度缩放。 */
    private static int bestPosition(ImageEntry entry, int width) {
        if (entry.positions.isEmpty()) {
            return -1;
        }
        Position best = entry.positions.get(0);
        for (Position p : entry.positions) {
            if (p.n > best.n) {
                best = p;
            }
        }
        return entry.width > 0 && entry.width != width
                ? Math.round(best.d * width / (float) entry.width)
                : best.d;
    }

    private static void mergePosition(ImageEntry entry, int displacement) {
        int tolerance = Math.max(10, Math.round(entry.width * 0.02f));
        for (Position p : entry.positions) {
            if (Math.abs(p.d - displacement) <= tolerance) {
                p.d = Math.round((p.d * p.n + displacement) / (float) (p.n + 1));
                p.n++;
                return;
            }
        }
        entry.positions.add(new Position(displacement, 1));
    }

    private static File jsonFile() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, JSON_NAME);
    }

    private static List<ImageEntry> loadLocked() {
        List<ImageEntry> entries = new ArrayList<>();
        File file = jsonFile();
        if (!file.isFile()) {
            return entries;
        }
        try {
            String text = FileUtil.readFromFile(file);
            if (text == null || text.trim().isEmpty()) {
                return entries;
            }
            JSONObject root = new JSONObject(text);
            JSONArray images = root.optJSONArray("images");
            if (images == null) {
                return entries;
            }
            for (int i = 0; i < images.length(); i++) {
                JSONObject o = images.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                ImageEntry entry = new ImageEntry();
                entry.signature = o.optString("sig", "");
                entry.blocks = fromHex(entry.signature);
                entry.width = o.optInt("width", 0);
                entry.height = o.optInt("height", 0);
                entry.complete = o.optString("complete", null);
                entry.filledLeft = o.optInt("fl", -1);
                entry.filledTop = o.optInt("ft", -1);
                entry.filledRight = o.optInt("fr", -1);
                entry.filledBottom = o.optInt("fb", -1);
                JSONArray positions = o.optJSONArray("positions");
                if (positions != null) {
                    for (int j = 0; j < positions.length(); j++) {
                        JSONObject p = positions.optJSONObject(j);
                        if (p != null) {
                            entry.positions.add(new Position(p.optInt("d", 0), p.optInt("n", 1)));
                        }
                    }
                }
                entries.add(entry);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return entries;
    }

    private static void saveLocked(List<ImageEntry> entries) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            JSONArray images = new JSONArray();
            for (ImageEntry entry : entries) {
                JSONObject o = new JSONObject();
                o.put("sig", entry.signature == null ? "" : entry.signature);
                o.put("width", entry.width);
                o.put("height", entry.height);
                if (entry.complete != null) {
                    o.put("complete", entry.complete);
                }
                if (entry.filledLeft >= 0) {
                    o.put("fl", entry.filledLeft);
                    o.put("ft", entry.filledTop);
                    o.put("fr", entry.filledRight);
                    o.put("fb", entry.filledBottom);
                }
                JSONArray positions = new JSONArray();
                for (Position p : entry.positions) {
                    JSONObject item = new JSONObject();
                    item.put("d", p.d);
                    item.put("n", p.n);
                    positions.put(item);
                }
                o.put("positions", positions);
                images.put(o);
            }
            root.put("images", images);
            FileUtil.write2File(root.toString(), jsonFile());
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    // ===================== 完整图差异 =====================

    /** 返回 {gapLeft, gapTop, gapRight, gapBottom, displacement}；差异不够明显时返回 null。 */
    private static int[] diffGap(Bitmap current, ImageEntry entry, int sourceLeft,
                                 float scale, int sliderY, int expected) {
        if (entry.complete == null || entry.complete.isEmpty()) {
            return null;
        }
        byte[] png = PuzzleSampleArchive.read(entry.complete);
        if (png == null) {
            return null;
        }
        Bitmap reference = BitmapFactory.decodeByteArray(png, 0, png.length);
        if (reference == null) {
            return null;
        }
        try {
            int width = current.getWidth();
            int height = current.getHeight();
            if (reference.getWidth() != width || reference.getHeight() != height) {
                return null;
            }
            float sy = height / 2780f;
            int x0 = Math.max(1, sourceLeft + expected + Math.round(8 * scale));
            int x1 = width - 2;
            int y0 = Math.max(1, sliderY + Math.round(PHOTO_TOP_FROM_SLIDER * sy));
            int y1 = Math.min(height - 2, sliderY + Math.round(PHOTO_BOTTOM_FROM_SLIDER * sy));
            int rw = x1 - x0 + 1;
            int rh = y1 - y0 + 1;
            if (rw <= expected || rh <= expected) {
                return null;
            }
            int[] a = new int[rw * rh];
            int[] b = new int[rw * rh];
            current.getPixels(a, 0, rw, x0, y0, rw, rh);
            reference.getPixels(b, 0, rw, x0, y0, rw, rh);
            int step = Math.max(2, Math.round(2 * scale));
            int gw = rw / step;
            int gh = rh / step;
            if (gw < 8 || gh < 8) {
                return null;
            }
            boolean[] mask = new boolean[gw * gh];
            int margin = Math.round(6 * scale);
            for (int gy = 0; gy < gh; gy++) {
                int pixelY = gy * step;
                for (int gx = 0; gx < gw; gx++) {
                    int i = pixelY * rw + gx * step;
                    int ca = a[i];
                    int cb = b[i];
                    int ga = (77 * ((ca >> 16) & 255) + 150 * ((ca >> 8) & 255) + 29 * (ca & 255)) >> 8;
                    int gb = (77 * ((cb >> 16) & 255) + 150 * ((cb >> 8) & 255) + 29 * (cb & 255)) >> 8;
                    if (Math.abs(ga - gb) <= DIFF_THRESHOLD) {
                        continue;
                    }
                    int px = x0 + gx * step;
                    int py = y0 + pixelY;
                    // 上一张完整图补过的位置会有填充痕迹，跳过它，避免误判。
                    if (entry.filledLeft >= 0 && px >= entry.filledLeft - margin
                            && px <= entry.filledRight + margin && py >= entry.filledTop - margin
                            && py <= entry.filledBottom + margin) {
                        continue;
                    }
                    mask[gy * gw + gx] = true;
                }
            }
            return largestGap(mask, gw, gh, x0, y0, step, expected, scale, sourceLeft);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        } finally {
            reference.recycle();
        }
    }

    private static int[] largestGap(boolean[] mask, int gw, int gh, int x0, int y0, int step,
                                    int expected, float scale, int sourceLeft) {
        boolean[] seen = new boolean[mask.length];
        int[] queue = new int[mask.length];
        long minArea = Math.round(expected * (double) expected / (step * step) * 0.10);
        int bestCount = 0;
        int bestLeft = 0, bestTop = 0, bestRight = 0, bestBottom = 0;
        for (int gy = 1; gy < gh - 1; gy++) {
            for (int gx = 1; gx < gw - 1; gx++) {
                int index = gy * gw + gx;
                if (seen[index]) {
                    continue;
                }
                if (!mask[index]) {
                    seen[index] = true;
                    continue;
                }
                int head = 0, tail = 0;
                queue[tail++] = index;
                seen[index] = true;
                int minX = gx, maxX = gx, minY = gy, maxY = gy;
                while (head < tail) {
                    int point = queue[head++];
                    int px = point % gw, py = point / gw;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    if (px > 0 && !seen[point - 1] && mask[point - 1]) {
                        seen[point - 1] = true;
                        queue[tail++] = point - 1;
                    }
                    if (px + 1 < gw && !seen[point + 1] && mask[point + 1]) {
                        seen[point + 1] = true;
                        queue[tail++] = point + 1;
                    }
                    if (py > 0 && !seen[point - gw] && mask[point - gw]) {
                        seen[point - gw] = true;
                        queue[tail++] = point - gw;
                    }
                    if (py + 1 < gh && !seen[point + gw] && mask[point + gw]) {
                        seen[point + gw] = true;
                        queue[tail++] = point + gw;
                    }
                }
                int bw = (maxX - minX + 1) * step;
                int bh = (maxY - minY + 1) * step;
                if (bw < expected * 0.45f || bw > expected * 1.6f
                        || bh < expected * 0.40f || bh > expected * 1.7f) {
                    continue;
                }
                if (minX <= 0 || maxX >= gw - 1 || minY <= 0 || maxY >= gh - 1) {
                    continue;
                }
                int boxArea = (maxX - minX + 1) * (maxY - minY + 1);
                if (tail < minArea || tail < boxArea * 0.18 || tail <= bestCount) {
                    continue;
                }
                bestCount = tail;
                bestLeft = x0 + minX * step;
                bestTop = y0 + minY * step;
                bestRight = x0 + maxX * step;
                bestBottom = y0 + maxY * step;
            }
        }
        if (bestCount == 0) {
            return null;
        }
        int displacement = bestLeft - sourceLeft;
        if (displacement < Math.round(250 * scale) || displacement > Math.round(800 * scale)) {
            return null;
        }
        return new int[]{bestLeft, bestTop, bestRight, bestBottom, displacement};
    }

    // ===================== 签名 =====================

    /** 照片区 16x12 块中心像素（RGB），用于判断是不是同一张图。 */
    private static int[] signature(Bitmap bitmap, int sourceLeft, float scale, int sliderY) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float sy = height / 2780f;
        int x0 = Math.max(0, sourceLeft + Math.round(180 * scale) + Math.round(8 * scale));
        int x1 = width - 1;
        int y0 = Math.max(0, sliderY + Math.round(PHOTO_TOP_FROM_SLIDER * sy));
        int y1 = Math.min(height - 1, sliderY + Math.round(PHOTO_BOTTOM_FROM_SLIDER * sy));
        if (x1 - x0 < 32 || y1 - y0 < 32) {
            return null;
        }
        final int cols = 16, rows = 12;
        int[] blocks = new int[cols * rows];
        for (int r = 0; r < rows; r++) {
            int y = y0 + Math.round((r + 0.5f) * (y1 - y0) / rows);
            for (int c = 0; c < cols; c++) {
                int x = x0 + Math.round((c + 0.5f) * (x1 - x0) / cols);
                blocks[r * cols + c] = bitmap.getPixel(x, y) & 0xFFFFFF;
            }
        }
        return blocks;
    }

    private static double signatureDiff(int[] a, int[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 255;
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            int x = a[i], y = b[i];
            sum += Math.abs(((x >> 16) & 255) - ((y >> 16) & 255))
                    + Math.abs(((x >> 8) & 255) - ((y >> 8) & 255))
                    + Math.abs((x & 255) - (y & 255));
        }
        return sum / (a.length * 3.0);
    }

    private static String toHex(int[] blocks) {
        StringBuilder sb = new StringBuilder(blocks.length * 6);
        for (int value : blocks) {
            String hex = Integer.toHexString(value & 0xFFFFFF);
            for (int i = hex.length(); i < 6; i++) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static int[] fromHex(String hex) {
        if (hex == null || hex.length() < 6 || hex.length() % 6 != 0) {
            return null;
        }
        int count = hex.length() / 6;
        int[] blocks = new int[count];
        try {
            for (int i = 0; i < count; i++) {
                blocks[i] = Integer.parseInt(hex.substring(i * 6, i * 6 + 6), 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return blocks;
    }
}
