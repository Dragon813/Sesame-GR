package io.github.lazyimmortal.sesame.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.TokenConfig;
import io.github.lazyimmortal.sesame.util.ToastUtil;

public class ExtensionsActivity extends BaseActivity {

    @Override
    public void onContentChanged() {
        // 使用自己的大标题，不设 Toolbar
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_extend);

        findViewById(R.id.btn_get_watered_items).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "getWateredItems", null);
            ToastUtil.show(this, "已发送查询请求，请在森林日志查看结果！");
        });
        findViewById(R.id.btn_get_watering_items).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "getWateringItems", null);
            ToastUtil.show(this, "已发送查询请求，请在森林日志查看结果！");
        });
        findViewById(R.id.btn_get_tree_items).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "getTreeItems", null);
            ToastUtil.show(this, "已发送查询请求，请在森林日志查看结果！");
        });
        findViewById(R.id.btn_get_newTree_items).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "getNewTreeItems", null);
            ToastUtil.show(this, "已发送查询请求，请在森林日志查看结果！");
        });
        findViewById(R.id.btn_query_area_trees).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "queryAreaTrees", null);
            ToastUtil.show(this, "已发送查询请求，请在森林日志查看结果！");
        });
        findViewById(R.id.btn_get_unlock_treeItems).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "getUnlockTreeItems", null);
            ToastUtil.show(this, "已发送查询请求，请在森林日志查看结果！");
        });
        findViewById(R.id.btn_fill_watered_friend_list).setOnClickListener(v -> {
            sendItemsBroadcast("antForest", "fillWateredFriendList", null);
            ToastUtil.show(this, "已发送填入请求，请在森林日志查看结果！");
        });

        findViewById(R.id.btn_clear_dish_image).setOnClickListener(v -> {
            Context context = ExtensionsActivity.this;
            new AlertDialog.Builder(context)
                    .setTitle(R.string.clear_dish_image)
                    .setMessage("确认清空" + TokenConfig.getDishImageCount() + "组光盘行动图片？")
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        if (TokenConfig.clearDishImage()) {
                            ToastUtil.show(context, "光盘行动图片清空成功");
                        } else {
                            ToastUtil.show(context, "光盘行动图片清空失败");
                        }
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
        });

        findViewById(R.id.btn_set_custom_walk_path_id_list).setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint(R.string.msg_input_custom_walk_path_id);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.set_custom_walk_path_id_list)
                    .setView(input)
                    .setPositiveButton(R.string.btn_add_custom_walk_path_id, (dialog, which) -> {
                        sendItemsBroadcast("setCustomWalkPathIdList", "addCustomWalkPathId", input.getText().toString().trim());
                    }).show();
        });
        findViewById(R.id.btn_set_custom_walk_path_id_queue).setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint(R.string.msg_input_custom_walk_path_id);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.set_custom_walk_path_id_queue)
                    .setView(input)
                    .setPositiveButton(R.string.btn_add_custom_walk_path_id, (dialog, which) -> {
                        sendItemsBroadcast("setCustomWalkPathIdQueue", "addCustomWalkPathIdQueue", input.getText().toString().trim());
                    }).setNegativeButton(getString(R.string.btn_clear_custom_walk_path_id_queue), (dialog, which) -> {
                        sendItemsBroadcast("setCustomWalkPathIdQueue", "clearCustomWalkPathIdQueue", null);
                    }).show();
        });
        findViewById(R.id.btn_developer_mode).setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, Class.forName("io.github.lazyimmortal.sesame.ui.AlphaActivity")));
            } catch (Exception e) {
                ToastUtil.show(this, "不符合开启资格！");
            }
        });
    }

    private void sendItemsBroadcast(String type, String method, String data) {
        Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.rpctest");
        intent.putExtra("type", type);
        intent.putExtra("method", method);
        intent.putExtra("data", data);
        sendBroadcast(intent);
    }
}
