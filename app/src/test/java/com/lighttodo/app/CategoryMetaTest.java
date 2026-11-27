package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Category;
import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.ui.CategoryAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务项上归属信息的显示规则：
 *
 * <ul>
 *   <li><b>分类页</b>：任务已经按象限分好，所以不再逐条标「重要紧急」这种分类名 ——
 *       象限标题已经说了；只标「所属分组」和截止 / 提醒。</li>
 *   <li><b>分组页</b>：一个分组里任务可能分属不同象限，所以要标出所属分类。</li>
 *   <li>两者都：只要这一行里有内容（分组 / 分类 / 截止 / 提醒）就必须显示出来，
 *       以前只在「有截止或提醒」时显示，把分组名一起藏掉了。</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
public class CategoryMetaTest {

    private Context context;
    private Context themed;
    private TodoStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        themed = new android.view.ContextThemeWrapper(context, R.style.Theme_LightTodo);
        TodoStore.resetForTest(context);
        store = TodoStore.get(context);
        for (Group g : store.groups()) store.deleteGroup(g.id);
    }

    @Test
    public void 分类页里不标紧急程度但要标分组() {
        Group work = store.addGroup("工作");
        // 没设截止时间，也没设提醒
        Task task = store.addTask(work.id, "只有标题", "", 0L, 0L, true, false);
        assertNotNull(task);

        View item = bindCategoryItem(task);

        // 分类名不显示：象限标题已经写着「重要紧急」，不用每条再写一遍
        TextView cat = item.findViewById(R.id.t_cat);
        assertEquals("分类页不用再标一遍紧急程度", View.GONE, cat.getVisibility());

        // 但分组名要显示
        View meta = item.findViewById(R.id.t_meta);
        assertEquals("这一行必须显示", View.VISIBLE, meta.getVisibility());
        TextView group = item.findViewById(R.id.t_group);
        assertEquals("所属分组要显示出来", View.VISIBLE, group.getVisibility());
        assertEquals("分组名要对", "工作", group.getText().toString());
    }

    @Test
    public void 分组页里要标出所属分类() {
        Group work = store.addGroup("工作");
        Task task = store.addTask(work.id, "甲", "", 0L, 0L, true, false); // 重要不紧急
        assertNotNull(task);

        Intent intent = new Intent(context, GroupActivity.class);
        intent.putExtra(GroupActivity.EXTRA_GROUP_ID, work.id);
        try (ActivityController<GroupActivity> controller = Robolectric.buildActivity(GroupActivity.class)) {
            GroupActivity activity = controller.get();
            activity.setIntent(intent);
            controller.setup();
            RecyclerView list = activity.findViewById(R.id.list);
            assertNotNull(list);
            RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(0);
            assertNotNull("分组页的任务行应该被创建出来", vh);
            View item = vh.itemView;

            TextView cat = item.findViewById(R.id.t_cat);
            assertEquals("分组页要标出所属分类", View.VISIBLE, cat.getVisibility());
            assertEquals("分类名要对", themed.getString(Category.nameRes(Category.IMPORTANT_NOT_URGENT)),
                    cat.getText().toString());
        }
    }

    // ------------------------------------------------------------ 辅助

    /** 把某条任务放进分类列表里渲染，取它那一行的真实 View。 */
    private View bindCategoryItem(Task task) {
        List<Object> items = new ArrayList<>();
        items.add(new CategoryAdapter.Header(Category.of(task), 1, 1));
        items.add(task);

        CategoryAdapter adapter = new CategoryAdapter(store,
                new CategoryAdapter.Listener() {
                    @Override
                    public void onToggle(Task t) {
                    }

                    @Override
                    public void onEdit(Task t) {
                    }

                    @Override
                    public void onMore(Task t) {
                    }
                });
        adapter.setData(items);

        RecyclerView list = new RecyclerView(themed);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(themed));
        list.setAdapter(adapter);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2200);

        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(1);
        assertNotNull("任务行应该被创建出来", vh);
        return vh.itemView;
    }
}
