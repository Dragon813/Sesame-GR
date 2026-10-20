package io.github.lazyimmortal.sesame.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.entity.FriendWatch;
import io.github.lazyimmortal.sesame.entity.IdAndName;
import io.github.lazyimmortal.sesame.util.Log;
import io.github.lazyimmortal.sesame.util.StringUtil;
import io.github.lazyimmortal.sesame.util.FileUtil;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListAdapter extends BaseAdapter {
    private static ListAdapter adapter;

    private static ListDialog.ListType listType;

    public static ListAdapter get(Context c) {
        if (adapter == null) {
            adapter = new ListAdapter(c);
        }
        return adapter;
    }

    public static ListAdapter getClear(Context c) {
        ListAdapter adapter = get(c);
        adapter.findIndex = -1;
        adapter.findWord = null;
        return adapter;
    }

    public static ListAdapter getClear(Context c, ListDialog.ListType listType) {
        if (adapter == null) {
            adapter = new ListAdapter(c);
            viewHolderList = new ArrayList<>();
        }
        ListAdapter.listType = listType;
        adapter.findIndex = -1;
        adapter.findWord = null;
        return adapter;
    }

    Context context;
    List<? extends IdAndName> list;
    SelectModelFieldFunc selectModelFieldFunc;
    int findIndex = -1;
    String findWord = null;
    String filterWord = null;
    List<IdAndName> filteredList = null;

    private ListAdapter(Context c) {
        context = c;
    }

    public void setBaseList(List<? extends IdAndName> l) {
        if (l != list)
            exitFind();
        list = l;
        filterWord = null;
        filteredList = null;
    }

    public void setSelectedList(SelectModelFieldFunc selectModelFieldFunc) {
        this.selectModelFieldFunc = selectModelFieldFunc;
        try {
            Collections.sort(list, (o1, o2) -> {
                if (this.selectModelFieldFunc.contains(o1.id) == this.selectModelFieldFunc.contains(o2.id)) {
                    return o1.compareTo(o2);
                }
                return this.selectModelFieldFunc.contains(o1.id) ? -1 : 1;
            });
        } catch (Throwable t) {
            Log.i("ListAdapter err");
            Log.printStackTrace(t);
        }
    }

    /**
     * 获取当前选中的项列表
     */
    public List<IdAndName> getSelectedItems() {
        List<IdAndName> selectedItems = new ArrayList<>();
        if (list != null && selectModelFieldFunc != null) {
            for (IdAndName item : list) {
                if (selectModelFieldFunc.contains(item.id)) { // 如果该项被选中
                    selectedItems.add(item); // 添加到选中项列表
                }
            }
        }
        return selectedItems;
    }

    public int findLast(String findThis) {
        List<? extends IdAndName> list = visibleList();
        if (list == null || list.isEmpty()) {
            return -1;
        }
        findThis = findThis.toLowerCase();
        if (!Objects.equals(findThis, findWord)) {
            findIndex = -1;
            findWord = findThis;
        }
        int start = findIndex;
        int last = list.size() - 1;
        if (start < 0) {
            start = 0;
        } else if (start > last) {
            start = last;
        }
        int current = start;
        for (; ; ) {
            current--;
            if (current < 0) {
                current = last;
            }
            if (list.get(current).name.toLowerCase().contains(findThis)) {
                findIndex = current;
                break;
            }
            if (current == start) {
                break;
            }
        }
        notifyDataSetChanged();
        return findIndex;
    }

    public int findNext(String findThis) {
        List<? extends IdAndName> list = visibleList();
        if (list == null || list.isEmpty()) {
            return -1;
        }
        findThis = findThis.toLowerCase();
        if (!Objects.equals(findThis, findWord)) {
            findIndex = -1;
            findWord = findThis;
        }
        int start = findIndex;
        int last = list.size() - 1;
        if (start < 0) {
            start = 0;
        } else if (start > last) {
            start = last;
        }
        int current = start;
        for (; ; ) {
            current++;
            if (current > last) {
                current = 0;
            }
            if (list.get(current).name.toLowerCase().contains(findThis)) {
                findIndex = current;
                break;
            }
            if (current == start) {
                break;
            }
        }
        notifyDataSetChanged();
        return findIndex;
    }

    public void exitFind() {
        findIndex = -1;
    }

    /* 关键字实时过滤: 用户名/userID/好友统计正文任一包含即列出, 为空恢复全量 */
    public void filter(String word) {
        if (word == null || word.trim().isEmpty()) {
            filterWord = null;
            filteredList = null;
        } else {
            filterWord = word.toLowerCase();
            filteredList = new ArrayList<>();
            if (list != null) {
                for (IdAndName ai : list) {
                    if (matchFilter(ai, filterWord)) {
                        filteredList.add(ai);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    private boolean matchFilter(IdAndName ai, String word) {
        if (ai.name != null && ai.name.toLowerCase().contains(word)) {
            return true;
        }
        if (ai.id != null && ai.id.contains(word)) {
            return true;
        }
        if (ai instanceof FriendWatch) {
            String body = ((FriendWatch) ai).bodyText;
            return body != null && body.toLowerCase().contains(word);
        }
        return false;
    }

    private List<? extends IdAndName> visibleList() {
        return filteredList != null ? filteredList : list;
    }

    public void selectAll() {
        selectModelFieldFunc.clear();
        for (IdAndName ai : list) {
            selectModelFieldFunc.add(ai.id, 0);
        }
        notifyDataSetChanged();
    }

    public void SelectInvert() {
        for (IdAndName ai : list) {
            if (!selectModelFieldFunc.contains(ai.id)) {
                selectModelFieldFunc.add(ai.id, 0);
            } else {
                selectModelFieldFunc.remove(ai.id);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        List<? extends IdAndName> vl = visibleList();
        return vl == null ? 0 : vl.size();
    }

    @Override
    public Object getItem(int p1) {
        return visibleList().get(p1);
    }

    @Override
    public long getItemId(int p1) {
        return p1;
    }

    @Override
    public View getView(int p1, View p2, ViewGroup p3) {
        ViewHolder vh;
        if (p2 == null) {
            vh = new ViewHolder();
            p2 = View.inflate(context, R.layout.list_item, null);
            vh.tv = p2.findViewById(R.id.tv_idn);
            vh.cb = p2.findViewById(R.id.cb_list);
            vh.iv = p2.findViewById(R.id.iv_avatar);
            vh.llHead = p2.findViewById(R.id.ll_head);
            vh.tvTitle = p2.findViewById(R.id.tv_title);
            vh.tvSub = p2.findViewById(R.id.tv_sub);
            vh.tvDelete = p2.findViewById(R.id.tv_delete);
            vh.tvAvatarText = p2.findViewById(R.id.tv_avatar_text);
            if (listType == ListDialog.ListType.SHOW) {
                vh.cb.setVisibility(View.GONE);
            }
            p2.setTag(vh);
            viewHolderList.add(vh);
        } else {
            vh = (ViewHolder) p2.getTag();
        }

        IdAndName ai = visibleList().get(p1);
        vh.tv.setTextColor(findIndex == p1 ? Color.RED : Color.BLACK);
        vh.cb.setChecked(selectModelFieldFunc != null && selectModelFieldFunc.contains(ai.id));
        //好友统计条目: 头像框占两行, 右侧上行用户名/下行userID, 单删全部文字变红(单删标签用黑色); 其余列表保持原布局
        if (ai instanceof FriendWatch) {
            FriendWatch fw = (FriendWatch) ai;
            vh.llHead.setVisibility(View.VISIBLE);
            vh.tvTitle.setText(fw.name);
            vh.tvSub.setText("userID:" + fw.id);
            int headColor = fw.delete ? Color.RED : Color.BLACK;
            vh.tvTitle.setTextColor(headColor);
            vh.tvSub.setTextColor(headColor);
            vh.tv.setTextColor(fw.delete || findIndex == p1 ? Color.RED : Color.BLACK);
            vh.tvDelete.setVisibility(fw.delete ? View.VISIBLE : View.GONE);
            vh.tv.setText(fw.bodyText);
            //头像: 有链接显示图片, 无链接显示firstAlphaChar单字符占位(同样48dp头像框), 都没有则隐藏
            if (!StringUtil.isEmpty(fw.headImageUrl)) {
                vh.tvAvatarText.setVisibility(View.GONE);
                vh.iv.setVisibility(View.VISIBLE);
                loadAvatar(vh.iv, fw.headImageUrl);
            } else if (!StringUtil.isEmpty(fw.firstAlphaChar)) {
                vh.iv.setVisibility(View.GONE);
                vh.tvAvatarText.setVisibility(View.VISIBLE);
                vh.tvAvatarText.setText(fw.firstAlphaChar);
            } else {
                vh.iv.setVisibility(View.GONE);
                vh.tvAvatarText.setVisibility(View.GONE);
            }
        } else {
            vh.llHead.setVisibility(View.GONE);
            vh.tv.setText(ai.name);
        }
        return p2;
    }

    /* 头像缓存: 内存+磁盘两级, 未命中才网络下载并同时写两级缓存 */
    private static final Map<String, Bitmap> avatarCache = new ConcurrentHashMap<>();
    private static final ExecutorService avatarExecutor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static void loadAvatar(ImageView iv, String url) {
        iv.setTag(url);
        Bitmap cached = avatarCache.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(null);
        avatarExecutor.execute(() -> {
            try {
                Bitmap bmp = downloadAndCache(url);
                if (bmp != null) {
                    avatarCache.put(url, bmp);
                    mainHandler.post(() -> {
                        if (url.equals(iv.getTag())) {
                            iv.setImageBitmap(bmp);
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /* 预下载全部头像到磁盘缓存(已缓存的跳过), 供好友表刷新后调用 */
    public static void predownloadAvatars(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        avatarExecutor.execute(() -> {
            for (String url : urls) {
                try {
                    if (!StringUtil.isEmpty(url)) {
                        downloadAndCache(url);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /* 磁盘缓存命中直接解码, 否则网络下载并写磁盘缓存 */
    private static Bitmap downloadAndCache(String url) throws Exception {
        File cacheFile = getAvatarCacheFile(url);
        if (cacheFile.isFile()) {
            return BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        java.io.InputStream in = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        in.close();
        conn.disconnect();
        byte[] data = bos.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bmp != null) {
            //写入磁盘缓存, URL变化即换新文件, 无需清理旧图
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
            fos.write(data);
            fos.close();
        }
        return bmp;
    }

    private static File getAvatarCacheFile(String url) {
        File dir = new File(FileUtil.MAIN_DIRECTORY_FILE, "avatar");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        //在该目录放置.nomedia文件, 阻止系统媒体扫描器索引, 头像图片不会出现在相册
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (Throwable ignored) {
            }
        }
        return new File(dir, md5(url) + ".png");
    }

    private static String md5(String s) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(s.hashCode());
        }
    }

    public static List<ViewHolder> viewHolderList;

    public static class ViewHolder {
        TextView tv;
        CheckBox cb;
        ImageView iv;
        android.widget.LinearLayout llHead;
        TextView tvTitle;
        TextView tvSub;
        TextView tvDelete;
        TextView tvAvatarText;
    }

}
