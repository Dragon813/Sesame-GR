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
    
    Button btnGetWateredItems,btnGetWateringItems;
    Button btnGetTreeItems, btnGetNewTreeItems;
    Button btnQueryAreaTrees, btnGetUnlockTreeItems;
    Button btnClearDishImage;
    Button btnSetCustomWalkPathId, btnSetCustomWalkPathIdQueue;
    Button btnDeveloperMode;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_extend);
        setBaseTitle(getString(R.string.extensions));
        btnGetWateredItems = findViewById(R.id.btn_get_watered_items);
        btnGetWateringItems = findViewById(R.id.btn_get_watering_items);
        btnGetTreeItems = findViewById(R.id.btn_get_tree_items);
        btnGetNewTreeItems = findViewById(R.id.btn_get_newTree_items);
        btnQueryAreaTrees = findViewById(R.id.btn_query_area_trees);
        btnGetUnlockTreeItems = findViewById(R.id.btn_get_unlock_treeItems);
        btnClearDishImage = findViewById(R.id.btn_clear_dish_image);
        btnSetCustomWalkPathId = findViewById(R.id.btn_set_custom_walk_path_id_list);
        btnSetCustomWalkPathIdQueue = findViewById(R.id.btn_set_custom_walk_path_id_queue);
        btnDeveloperMode = findViewById(R.id.btn_developer_mode);
        
        
        btnGetWateredItems.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendItemsBroadcast("antForest", "getWateredItems", null);
                ToastUtil.show(ExtensionsActivity.this, "已发送查询请求，请在森林日志查看结果！");
            }
        });
        
        btnGetWateringItems.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendItemsBroadcast("antForest", "getWateringItems", null);
                ToastUtil.show(ExtensionsActivity.this, "已发送查询请求，请在森林日志查看结果！");
            }
        });
        
        btnGetTreeItems.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendItemsBroadcast("antForest", "getTreeItems", null);
                ToastUtil.show(ExtensionsActivity.this, "已发送查询请求，请在森林日志查看结果！");
            }
        });

        btnGetNewTreeItems.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendItemsBroadcast("antForest", "getNewTreeItems", null);
                ToastUtil.show(ExtensionsActivity.this, "已发送查询请求，请在森林日志查看结果！");
            }
        });

        btnQueryAreaTrees.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendItemsBroadcast("antForest", "queryAreaTrees", null);
                ToastUtil.show(ExtensionsActivity.this, "已发送查询请求，请在森林日志查看结果！");
            }
        });

        btnGetUnlockTreeItems.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendItemsBroadcast("antForest", "getUnlockTreeItems", null);
                ToastUtil.show(ExtensionsActivity.this, "已发送查询请求，请在森林日志查看结果！");
            }
        });

        btnClearDishImage.setOnClickListener(v -> {
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

        btnSetCustomWalkPathId.setOnClickListener(v -> {
            Context context = ExtensionsActivity.this;
            EditText input = new EditText(context);
            input.setHint(R.string.msg_input_custom_walk_path_id);

            new AlertDialog.Builder(context)
                    .setTitle(R.string.set_custom_walk_path_id_list)
                    .setView(input)
                    .setPositiveButton(R.string.btn_add_custom_walk_path_id, (dialog, which) -> {
                        String text = input.getText().toString().trim();
                        sendItemsBroadcast("setCustomWalkPathIdList", "addCustomWalkPathId", text);
                    }).show();
        });
        btnSetCustomWalkPathIdQueue.setOnClickListener(v -> {
            Context context = ExtensionsActivity.this;
            EditText input = new EditText(context);
            input.setHint(R.string.msg_input_custom_walk_path_id);

            new AlertDialog.Builder(context)
                    .setTitle(R.string.set_custom_walk_path_id_queue)
                    .setView(input)
                    .setPositiveButton(R.string.btn_add_custom_walk_path_id, (dialog, which) -> {
                        String text = input.getText().toString().trim();
                        sendItemsBroadcast("setCustomWalkPathIdQueue", "addCustomWalkPathIdQueue", text);
                    }).setNegativeButton(getString(R.string.btn_clear_custom_walk_path_id_queue), (dialog, which) -> {
                        sendItemsBroadcast("setCustomWalkPathIdQueue", "clearCustomWalkPathIdQueue", null);
                    }).show();
        });

        btnDeveloperMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(ExtensionsActivity.this, Class.forName("io.github.lazyimmortal.sesame.ui.AlphaActivity")));
                } catch (Exception e) {
                    ToastUtil.show(ExtensionsActivity.this, "不符合开启资格！");
                }
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
