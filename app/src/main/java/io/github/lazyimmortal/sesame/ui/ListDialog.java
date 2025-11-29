package io.github.lazyimmortal.sesame.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectAndCountOneModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.SelectOneModelField;
import io.github.lazyimmortal.sesame.data.modelFieldExt.common.SelectModelFieldFunc;
import io.github.lazyimmortal.sesame.entity.AlipayAnimal;
import io.github.lazyimmortal.sesame.entity.AlipayBeach;
import io.github.lazyimmortal.sesame.entity.AlipayPlantScene;
import io.github.lazyimmortal.sesame.entity.AlipayForestHunt;
import io.github.lazyimmortal.sesame.entity.AlipayMarathon;
import io.github.lazyimmortal.sesame.entity.AlipayNewAncientTree;
import io.github.lazyimmortal.sesame.entity.AlipayReserve;
import io.github.lazyimmortal.sesame.entity.AlipayTree;
import io.github.lazyimmortal.sesame.entity.AlipayUser;
import io.github.lazyimmortal.sesame.entity.CooperateUser;
import io.github.lazyimmortal.sesame.entity.FriendWatch;
import io.github.lazyimmortal.sesame.entity.IdAndName;
import io.github.lazyimmortal.sesame.entity.WalkPath;
import io.github.lazyimmortal.sesame.util.ToastUtil;
import io.github.lazyimmortal.sesame.util.idMap.*;

import java.util.List;

public class ListDialog {
    static AlertDialog listDialog;
    static Button btn_find_last, btn_find_next,
            btn_select_all, btn_select_invert;
    static EditText searchText, oneClickText;
    static ListView lv_list;
    private static SelectModelFieldFunc selectModelFieldFunc;
    static Boolean hasCount;

    static ListType listType;

    static RelativeLayout layout_batch_process;

    public enum ListType {
        RADIO, CHECK, SHOW
    }

    public static void show(Context c, CharSequence title, SelectOneModelField selectModelField, ListType listType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, false, listType);
    }

    public static void show(Context c, CharSequence title, SelectAndCountOneModelField selectModelField, ListType listType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, false, listType);
    }

    public static void show(Context c, CharSequence title, SelectModelField selectModelField) {
        show(c, title, selectModelField, ListDialog.ListType.CHECK);
    }

    public static void show(Context c, CharSequence title, SelectAndCountModelField selectModelField) {
        show(c, title, selectModelField, ListDialog.ListType.CHECK);
    }

    public static void show(Context c, CharSequence title, SelectModelField selectModelField, ListType listType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, false, listType);
    }

    public static void show(Context c, CharSequence title, SelectAndCountModelField selectModelField, ListType listType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, true, listType);
    }

    public static void show(Context c, CharSequence title, List<? extends IdAndName> bl, SelectModelFieldFunc selectModelFieldFunc, Boolean hasCount) {
        show(c, title, bl, selectModelFieldFunc, hasCount, ListType.CHECK);
    }

    public static void show(Context c, CharSequence title, List<? extends IdAndName> bl, SelectModelFieldFunc selectModelFieldFunc, Boolean hasCount, ListType listType) {
        ListDialog.selectModelFieldFunc = selectModelFieldFunc;
        ListDialog.hasCount = hasCount;
        ListAdapter la = ListAdapter.getClear(c, listType);
        la.setBaseList(bl);
        la.setSelectedList(selectModelFieldFunc);
        showListDialog(c, title);
        ListDialog.listType = listType;
    }

    private static void showListDialog(Context c, CharSequence title) {
        if (listDialog == null || listDialog.getContext() != c)
            listDialog = new AlertDialog.Builder(c)
                    .setTitle(title)
                    .setView(getListView(c))
                    .setPositiveButton(c.getString(R.string.close), null)
                    .create();
        listDialog.setOnShowListener(p1 -> {
            AlertDialog d = (AlertDialog) p1;
            layout_batch_process = d.findViewById(R.id.layout_batch_process);
            assert layout_batch_process != null;
            layout_batch_process.setVisibility(listType == ListType.CHECK ? View.VISIBLE : View.GONE);
            ListAdapter.get(c).notifyDataSetChanged();
        });
        listDialog.show();
        Button positiveButton = listDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(ContextCompat.getColor(c, R.color.button));
        }
    }

    private static View getListView(Context c) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_list, null);

        btn_find_last = v.findViewById(R.id.btn_find_last);
        btn_find_next = v.findViewById(R.id.btn_find_next);
        btn_select_all = v.findViewById(R.id.btn_select_all);
        btn_select_invert = v.findViewById(R.id.btn_select_invert);

        View.OnClickListener onBtnClickListener = new View.OnClickListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public void onClick(View v) {
                if (searchText.length() <= 0) {
                    return;
                }
                ListAdapter la = ListAdapter.get(v.getContext());
                int index = -1;
                if (v.getId() == R.id.btn_find_last) {
                    // 下面Text要转String，不然判断equals会出问题
                    index = la.findLast(searchText.getText().toString());
                } else if (v.getId() == R.id.btn_find_next) {
                    // 同上
                    index = la.findNext(searchText.getText().toString());
                }
                if (index < 0) {
                    ToastUtil.show(v.getContext(), "未搜到");
                } else {
                    lv_list.setSelection(index);
                }
            }
        };
        btn_find_last.setOnClickListener(onBtnClickListener);
        btn_find_next.setOnClickListener(onBtnClickListener);


        View.OnClickListener batchBtnOnClickListener = v1 -> {
            ListAdapter la = ListAdapter.get(v1.getContext());
            if (v1.getId() == R.id.btn_select_all) {
                la.selectAll();
            } else if (v1.getId() == R.id.btn_select_invert) {
                la.SelectInvert();
            }
        };
        btn_select_all.setOnClickListener(batchBtnOnClickListener);
        btn_select_invert.setOnClickListener(batchBtnOnClickListener);


        oneClickText = v.findViewById(R.id.one_click_setup);
        oneClickText.setOnFocusChangeListener((v2, hasFocus) -> {
            if (hasFocus) {
                // 获得焦点时，清除提示内容
                oneClickText.setHint("");
            } else {
                // 失去焦点时，处理输入内容
                String input = oneClickText.getText().toString();
                if (input.isEmpty()) {
                    // 如果输入为空，恢复提示内容
                    oneClickText.setHint("请输入数量");
                } else {
                    try {
                        // 将输入内容转换为整数
                        int count = Integer.parseInt(input);

                        if (count <= 0) {
                            // 如果输入的值小于等于 0，弹出提示
                            Toast.makeText(v2.getContext(), "请输入大于 0 的数字", Toast.LENGTH_SHORT).show();
                        } else {
                            // 如果输入的值有效，为选中的复选框赋值
                            ListAdapter la = ListAdapter.get(v2.getContext());
                            for (IdAndName item : la.getSelectedItems()) {
                                selectModelFieldFunc.add(item.id, count); // 为选中的项设置次数
                            }
                            la.notifyDataSetChanged(); // 刷新列表
                        }
                    } catch (NumberFormatException e) {
                        // 如果输入的不是有效数字，弹出提示
                        Toast.makeText(v2.getContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        if (!hasCount) {
            oneClickText.setVisibility(View.GONE);
        }

        searchText = v.findViewById(R.id.edt_find);
        lv_list = v.findViewById(R.id.lv_list);
        lv_list.setAdapter(ListAdapter.getClear(c));
        lv_list.setOnItemClickListener(
                (p1, p2, p3, p4) -> {
                    if (listType == ListType.SHOW) {
                        return;
                    }
                    IdAndName curIdAndName = (IdAndName) p1.getAdapter().getItem(p3);
                    ListAdapter.ViewHolder curViewHolder = (ListAdapter.ViewHolder) p2.getTag();
                    if (!hasCount) {
                        if (listType == ListType.RADIO) {
                            selectModelFieldFunc.clear();
                            if (curViewHolder.cb.isChecked()) {
                                curViewHolder.cb.setChecked(false);
                            } else {
                                for (int i = 0; i < ListAdapter.viewHolderList.size(); i++) {
                                    ListAdapter.ViewHolder viewHolder = ListAdapter.viewHolderList.get(i);
                                    viewHolder.cb.setChecked(false);
                                }
                                curViewHolder.cb.setChecked(true);
                                selectModelFieldFunc.add(curIdAndName.id, 0);
                            }
                        } else {
                            if (curViewHolder.cb.isChecked()) {
                                selectModelFieldFunc.remove(curIdAndName.id);
                                curViewHolder.cb.setChecked(false);
                            } else {
                                if (!selectModelFieldFunc.contains(curIdAndName.id)) {
                                    selectModelFieldFunc.add(curIdAndName.id, 0);
                                }
                                curViewHolder.cb.setChecked(true);
                            }
                        }
                    } else {
                        EditText edt_count = new EditText(c);
                        AlertDialog edtDialog = new AlertDialog.Builder(c)
                                .setTitle(curIdAndName.name)
                                .setView(edt_count)
                                .setPositiveButton(c.getString(R.string.ok), (dialog, which) -> {
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        int count = 0;
                                        if (edt_count.length() > 0)
                                            try {
                                                count = Integer.parseInt(edt_count.getText().toString());
                                            } catch (Throwable t) {
                                                return;
                                            }
                                        Integer value = selectModelFieldFunc.get(curIdAndName.id);
                                        if (count > 0) {
                                            selectModelFieldFunc.add(curIdAndName.id, count);
                                            curViewHolder.cb.setChecked(true);
                                        } else {
                                            if (value != null && value >= 0) {
                                                selectModelFieldFunc.remove(curIdAndName.id);
                                            }
                                            curViewHolder.cb.setChecked(false);
                                        }
                                    }
                                    ListAdapter.get(c).notifyDataSetChanged();
                                })
                                .setNegativeButton(c.getString(R.string.cancel), null)
                                .create();
                        if (curIdAndName instanceof CooperateUser)
                            edt_count.setHint("浇水克数");
                        else if (curIdAndName instanceof AlipayMarathon
                                || curIdAndName instanceof AlipayNewAncientTree)
                            edt_count.setHint("助力克数");
                        else
                            edt_count.setHint("次数");
                        Integer value = selectModelFieldFunc.get(curIdAndName.id);
                        if (value != null && value >= 0)
                            edt_count.setText(String.valueOf(value));
                        else
                            edt_count.getText().clear();
                        edtDialog.show();
                    }
                });
        lv_list.setOnItemLongClickListener(
                (p1, p2, p3, p4) -> {
                    IdAndName curIdAndName = (IdAndName) p1.getAdapter().getItem(p3);
                    if ((curIdAndName instanceof AlipayTree)
                            || (curIdAndName instanceof AlipayReserve)
                            || (curIdAndName instanceof AlipayAnimal)
                            || (curIdAndName instanceof AlipayMarathon)
                            || (curIdAndName instanceof AlipayNewAncientTree)
                            || (curIdAndName instanceof AlipayBeach)
                            || (curIdAndName instanceof AlipayPlantScene)
                            || (curIdAndName instanceof AlipayForestHunt)
                            || (curIdAndName instanceof WalkPath)
                    ) {
                        try {
                            new AlertDialog.Builder(c)
                                    .setTitle("删除 " + curIdAndName.name)
                                    .setPositiveButton(c.getString(R.string.ok), (dialog, which) -> {
                                        if (which == DialogInterface.BUTTON_POSITIVE) {
                                            if (curIdAndName instanceof AlipayTree) {
                                                AlipayTree.remove(curIdAndName.id);
                                                TreeIdMap.remove(curIdAndName.id);
                                                TreeIdMap.save();
                                            } else if (curIdAndName instanceof AlipayReserve) {
                                                AlipayReserve.remove(curIdAndName.id);
                                                ReserveIdMap.remove(curIdAndName.id);
                                                ReserveIdMap.save();
                                            } else if (curIdAndName instanceof AlipayAnimal) {
                                                AlipayAnimal.remove(curIdAndName.id);
                                                AnimalIdMap.remove(curIdAndName.id);
                                                AnimalIdMap.save();
                                            } else if (curIdAndName instanceof AlipayMarathon) {
                                                AlipayMarathon.remove(curIdAndName.id);
                                                MarathonIdMap.remove(curIdAndName.id);
                                                MarathonIdMap.save();
                                            } else if (curIdAndName instanceof AlipayNewAncientTree) {
                                                AlipayNewAncientTree.remove(curIdAndName.id);
                                                NewAncientTreeIdMap.remove(curIdAndName.id);
                                                NewAncientTreeIdMap.save();
                                            } else if (curIdAndName instanceof AlipayBeach) {
                                                AlipayBeach.remove(curIdAndName.id);
                                                BeachIdMap.remove(curIdAndName.id);
                                                BeachIdMap.save();
                                            } else if (curIdAndName instanceof AlipayPlantScene) {
                                                AlipayPlantScene.remove(curIdAndName.id);
                                                PlantSceneIdMap.remove(curIdAndName.id);
                                                PlantSceneIdMap.save();
                                            } else if (curIdAndName instanceof AlipayForestHunt) {
                                                AlipayForestHunt.remove(curIdAndName.id);
                                                ForestHuntIdMap.remove(curIdAndName.id);
                                                ForestHuntIdMap.save();
                                            } else if (curIdAndName instanceof WalkPath) {
                                                WalkPath.remove(curIdAndName.id);
                                                WalkPathIdMap.remove(curIdAndName.id);
                                                WalkPathIdMap.save();
                                            }
                                            selectModelFieldFunc.remove(curIdAndName.id);
                                            ListAdapter.get(c).exitFind();
                                        }
                                        ListAdapter.get(c).notifyDataSetChanged();
                                    })
                                    .setNegativeButton(c.getString(R.string.cancel), null)
                                    .create().show();
                        } catch (Throwable ignored) {
                        }
                    } else if ((curIdAndName instanceof AlipayUser) || (curIdAndName instanceof  FriendWatch)) {
                        new AlertDialog.Builder(c)
                                .setTitle("选项")
                                .setAdapter(
                                        OptionsAdapter.get(c), new OnClickListener() {
                                            Context c;

                                            public OnClickListener setContext(Context c) {
                                                this.c = c;
                                                return this;
                                            }

                                            @Override
                                            public void onClick(DialogInterface p1, int p2) {
                                                String url = null;
                                                switch (p2) {
                                                    case 0:
                                                        url = "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F60000002.h5app.alipay.com%2Fwww%2Fhome.html%3FuserId%3D";
                                                        break;

                                                    case 1:
                                                        url = "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F66666674.h5app.alipay.com%2Fwww%2Findex.htm%3Fuid%3D";
                                                        break;

                                                    case 2:
                                                        url = "alipays://platformapi/startapp?appId=20000166&actionType=profile&userId=";
                                                        break;

                                                    case 3:
                                                        try {
                                                            new AlertDialog.Builder(c)
                                                                    .setTitle("删除 " + curIdAndName.name)
                                                                    .setPositiveButton(c.getString(R.string.ok), (dialog, which) -> {
                                                                        if (which == DialogInterface.BUTTON_POSITIVE) {
                                                                            if (curIdAndName instanceof AlipayUser) {
                                                                                UserIdMap.remove(curIdAndName.id);
                                                                            } else if (curIdAndName instanceof FriendWatch) {
                                                                                // nothing to do
                                                                            }
                                                                            selectModelFieldFunc.remove(curIdAndName.id);
                                                                            ListAdapter.get(c).exitFind();
                                                                        }
                                                                        ListAdapter.get(c).notifyDataSetChanged();
                                                                    })
                                                                    .setNegativeButton(c.getString(R.string.cancel), null)
                                                                    .create().show();
                                                        } catch (Throwable ignored) {
                                                        }
                                                }
                                                if (url != null) {
                                                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url + curIdAndName.id));
                                                    c.startActivity(it);
                                                }
                                            }
                                        }.setContext(c))
                                .setNegativeButton(c.getString(R.string.cancel), null)
                                .create().show();
                    }
                    return true;
                });
        return v;
    }

}
