package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import com.lighttodo.app.model.Group;
import com.lighttodo.app.model.Task;
import com.lighttodo.app.store.TodoStore;
import com.lighttodo.app.util.IslandFocus;
import com.lighttodo.app.util.Notifier;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.List;

/**
 * 小米超级岛 / 焦点通知：参数构造与能力探测。
 *
 * <p>v1.3 的教训：不能拿「某个 Settings 里的协议号」当上岛门槛，
 * 那个 key 在真机上读不到，一旦读不到就完全不挂参数，通知永远上不了岛。
 * 所以现在默认一律挂参数，让系统自己判断。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class IslandFocusTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        TodoStore.resetForTest(context);
        TodoStore store = TodoStore.get(context);
        for (Group g : store.groups()) {
            store.deleteGroup(g.id);
        }
        IslandFocus.setProtocolOverrideForTest(-1);
        IslandFocus.setXiaomiOverrideForTest(null);
        IslandFocus.setMode(context, IslandFocus.MODE_AUTO);
    }

    @Test
    public void 参数结构符合官方字段名() throws Exception {
        String json = IslandFocus.buildParamJson("写周报", "工作 · 截止 18:00", "工作", true);
        JSONObject root = new JSONObject(json);
        assertTrue("根节点应该是 param_v2", root.has("param_v2"));
        JSONObject v2 = root.getJSONObject("param_v2");

        assertEquals("写周报", v2.getString("ticker"));
        assertTrue("息屏文案应带上分组", v2.getString("aodTitle").contains("工作"));
        assertEquals("写周报", v2.getJSONObject("baseInfo").getString("title"));
        assertEquals("工作 · 截止 18:00", v2.getJSONObject("baseInfo").getString("content"));
        assertEquals("官方示例里 protocol 就是 1", 1, v2.getInt("protocol"));
        assertEquals("timeout 单位是分钟，720 = 12 小时", 720, v2.getInt("timeout"));
        assertFalse("没焦点通知权限时要退化成普通通知，不能被过滤",
                v2.getBoolean("filterWhenNoPermission"));
        assertTrue("首次出现应该自动展开", v2.getBoolean("islandFirstFloat"));

        JSONObject island = v2.getJSONObject("param_island");
        assertEquals("大岛", 1, island.getInt("islandProperty"));
        assertEquals("岛消失时间单位是秒", 300, island.getInt("islandTimeout"));
        JSONObject left = island.getJSONObject("bigIslandArea").getJSONObject("imageTextInfoLeft");
        assertEquals("写周报", left.getJSONObject("miui.focus.paramtextInfo").getString("title"));
        assertEquals("模板库那套写法也一起带上",
                "写周报", left.getJSONObject("textInfo").getString("title"));
        assertEquals("miui.focus.pic_imageText",
                left.getJSONObject("picInfo").getString("pic"));
        assertNotNull("小岛也要有内容", island.getJSONObject("smallIslandArea"));
    }

    @Test
    public void 焦点通知参数始终存在_不靠协议号判断() throws Exception {
        // 关键回归：v1.3 就是在这里把参数整个丢掉的
        IslandFocus.setProtocolOverrideForTest(0);
        IslandFocus.setXiaomiOverrideForTest(true);
        assertEquals("读不到协议号就是 0", 0, IslandFocus.focusProtocol(context));

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, Notifier.CHANNEL_ID);
        assertTrue("小米设备上必须挂上上岛参数",
                IslandFocus.apply(builder, context, "标题", "正文", "工作", null));
        assertNotNull(builder.getExtras().getString(IslandFocus.EXTRA_PARAM));
    }

    @Test
    public void 小米设备上通知一定带上参数() throws Exception {
        IslandFocus.setXiaomiOverrideForTest(true);

        TodoStore store = TodoStore.get(context);
        Group group = store.addGroup("工作");
        Task task = store.addTask(group.id, "写周报", "");
        task.remindAt = System.currentTimeMillis() + 60_000L;

        Notifier.showReminder(context, task, group.id, group.name);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = org.robolectric.Shadows.shadowOf(manager).getAllNotifications();
        assertEquals("应该发出通知", 1, notifications.size());
        Notification notification = notifications.get(0);
        assertNotNull("通知里应该有上岛参数", notification.extras.getString(IslandFocus.EXTRA_PARAM));
        assertNotNull("通知里应该有图片资源",
                notification.extras.getBundle(IslandFocus.EXTRA_PICS));
        assertNotNull("通知里应该有动作",
                notification.extras.getBundle(IslandFocus.EXTRA_ACTIONS));

        JSONObject v2 = new JSONObject(notification.extras.getString(IslandFocus.EXTRA_PARAM))
                .getJSONObject("param_v2");
        assertEquals("写周报", v2.getString("ticker"));
        assertTrue("带的是超级岛数据", v2.has("param_island"));
    }

    @Test
    public void 非小米设备默认不挂参数_但通知照发() {
        IslandFocus.setXiaomiOverrideForTest(false);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, Notifier.CHANNEL_ID);
        assertFalse("非小米设备不用挂", IslandFocus.apply(builder, context, "标题", "正文", "工作", null));

        TodoStore store = TodoStore.get(context);
        Group group = store.addGroup("生活");
        Task task = store.addTask(group.id, "买牛奶", "");
        Notifier.showReminder(context, task, group.id, group.name);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = org.robolectric.Shadows.shadowOf(manager).getAllNotifications();
        assertEquals("普通通知照发", 1, notifications.size());
    }

    @Test
    public void 强制模式在任何设备都挂参数() {
        IslandFocus.setXiaomiOverrideForTest(false);
        IslandFocus.setMode(context, IslandFocus.MODE_FORCE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, Notifier.CHANNEL_ID);
        assertTrue("强制模式必须挂", IslandFocus.apply(builder, context, "标题", "正文", "工作", null));

        IslandFocus.setMode(context, IslandFocus.MODE_OFF);
        NotificationCompat.Builder builder2 =
                new NotificationCompat.Builder(context, Notifier.CHANNEL_ID);
        assertFalse("关闭模式不能挂", IslandFocus.apply(builder2, context, "标题", "正文", "工作", null));
    }

    @Test
    public void 焦点通知权限被拒绝时通知仍然发得出去() {
        IslandFocus.setXiaomiOverrideForTest(true);

        TodoStore store = TodoStore.get(context);
        Group group = store.addGroup("工作");
        Task task = store.addTask(group.id, "写周报", "");
        Notifier.showReminder(context, task, group.id, group.name);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> notifications = org.robolectric.Shadows.shadowOf(manager).getAllNotifications();
        assertEquals("没权限也要有通知（filterWhenNoPermission=false）", 1, notifications.size());
    }

    @Test
    public void 权限查询拿不到时按允许处理_不误伤通知() {
        assertEquals("查不到就是未知", IslandFocus.PERM_UNKNOWN, IslandFocus.focusPermissionState(context));
        assertTrue("未知不等于拒绝", IslandFocus.canShowFocus(context));
    }

    @Test
    public void 能力探测不会因为协议号读不到就判成不支持() {
        IslandFocus.setProtocolOverrideForTest(0);
        IslandFocus.setXiaomiOverrideForTest(true);
        assertTrue("小米设备就应该认焦点通知", IslandFocus.isFocusSupported(context));
        assertTrue("会带上上岛参数", IslandFocus.willAttach(context));
    }
}
