package io.github.lazyimmortal.sesame.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.github.lazyimmortal.sesame.R;
import io.github.lazyimmortal.sesame.entity.FriendWatch;
import io.github.lazyimmortal.sesame.entity.IdAndName;

public class ListViewerActivity extends AppCompatActivity {
    private LinearLayout listContainer;
    private ScrollView scrollView;
    private List<IdAndName> allItems = new ArrayList<>();
    private int textColor, subColor, pad, pad12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra("title");
        if (title == null) title = "列表";

        textColor = ContextCompat.getColor(this, R.color.miuix_on_background);
        subColor = ContextCompat.getColor(this, R.color.miuix_on_background_variant);
        pad = (int) getResources().getDimension(R.dimen.miuix_spacing_16);
        pad12 = (int) getResources().getDimension(R.dimen.miuix_spacing_12);

        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.miuix_background));
        scrollView.setPadding(pad, 0, pad, pad);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 状态栏占位
        View sb = new View(this);
        sb.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (24 * getResources().getDisplayMetrics().density)));
        root.addView(sb);

        // 标题行（大标题 + 搜索框）
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, (int) (48 * getResources().getDisplayMetrics().density), 0, pad);

        TextView bigTitle = new TextView(this);
        bigTitle.setText(title);
        bigTitle.setTextSize(34);
        bigTitle.setTextColor(textColor);
        bigTitle.setTypeface(bigTitle.getTypeface(), android.graphics.Typeface.BOLD);
        titleRow.addView(bigTitle);

        // 搜索框
        EditText searchBox = new EditText(this);
        searchBox.setHint("搜索");
        searchBox.setTextSize(13);
        searchBox.setSingleLine();
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setBackgroundResource(R.drawable.miuix_card_bg);
        searchBox.setPadding(12, 6, 12, 6);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams((int) (80 * getResources().getDisplayMetrics().density), ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMarginStart(pad12);
        titleRow.addView(searchBox, sp);

        searchBox.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                String q = s.toString().toLowerCase();
                for (int i = 0; i < allItems.size(); i++) {
                    String n = allItems.get(i).getName().toLowerCase();
                    String id = allItems.get(i).id != null ? allItems.get(i).id.toLowerCase() : "";
                    if (q.isEmpty() || n.contains(q) || id.contains(q)) {
                        // scroll to matching item
                        if (listContainer.getChildCount() > i) {
                            View v = listContainer.getChildAt(i);
                            int top = v.getTop() - scrollView.getScrollY();
                            scrollView.smoothScrollBy(0, top);
                        }
                        break;
                    }
                }
            }
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
        });
        root.addView(titleRow);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        scrollView.addView(root);
        setContentView(scrollView);

        buildList();
    }

    private void buildList() {
        allItems = new ArrayList<>(FriendWatch.getList());
        for (IdAndName item : allItems) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(ContextCompat.getDrawable(this, R.drawable.miuix_card_bg));
            card.setPadding(pad12, pad12, pad12, pad12);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, (int) getResources().getDimension(R.dimen.miuix_spacing_8));
            card.setLayoutParams(cp);

            TextView tv = new TextView(this);
            tv.setText(item.getName());
            tv.setTextSize(15);
            tv.setTextColor(textColor);
            card.addView(tv);

            if (item.id != null && !item.id.isEmpty()) {
                TextView tvId = new TextView(this);
                tvId.setText(item.id);
                tvId.setTextSize(12);
                tvId.setTextColor(subColor);
                tvId.setPadding(0, 2, 0, 0);
                card.addView(tvId);
            }
            listContainer.addView(card);
        }
    }
}
