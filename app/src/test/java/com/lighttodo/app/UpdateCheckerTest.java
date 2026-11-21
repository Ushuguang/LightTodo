package com.lighttodo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.lighttodo.app.util.UpdateChecker;

import org.junit.Test;

/**
 * 「检查更新」的版本比较逻辑。
 *
 * <p>不联网测，只测最容易出错的地方：版本号怎么比。仓库还没上传时接口返回 404，
 * 那属于「查不到新版本」，不能当成「有更新」，也不能崩。</p>
 */
public class UpdateCheckerTest {

    @Test
    public void 比较版本号大小() {
        assertTrue("1.4.0 比 1.3.1 新", UpdateChecker.compareVersion("1.4.0", "1.3.1") > 0);
        assertTrue("1.3.1 比 1.4.0 旧", UpdateChecker.compareVersion("1.3.1", "1.4.0") < 0);
        assertEquals("相同版本应为 0", 0, UpdateChecker.compareVersion("1.4.0", "1.4.0"));
    }

    @Test
    public void 小版本号也能正确比较() {
        assertTrue(UpdateChecker.compareVersion("1.10.0", "1.9.0") > 0);
        assertTrue(UpdateChecker.compareVersion("1.4", "1.4.1") < 0);
        assertEquals(0, UpdateChecker.compareVersion("1.4", "1.4.0"));
    }

    @Test
    public void 去掉v前缀() {
        assertEquals("1.4.0", UpdateChecker.normalize("v1.4.0"));
        assertEquals("1.4.0", UpdateChecker.normalize("V1.4.0"));
        assertEquals("1.4.0", UpdateChecker.normalize(" 1.4.0 "));
    }

    @Test
    public void 检查失败时不算有更新() {
        // 仓库还没上传 → 404
        UpdateChecker.Result r = new UpdateChecker.Result(
                false, "404", null, null, null, null);
        assertFalse("查不到版本就不该提示更新", r.isNewerThan("1.4.0"));
    }

    @Test
    public void 仓库有新版本时能识别出来() {
        UpdateChecker.Result r = new UpdateChecker.Result(
                true, null, "v1.5.0", "新增功能", "https://example/LightTodo.apk", "LightTodo.apk");
        assertTrue("1.5.0 应该比本机 1.4.0 新", r.isNewerThan("1.4.0"));
        assertFalse("版本相同就不算更新", r.isNewerThan("1.5.0"));
        assertFalse("本机更新时不算更新", r.isNewerThan("2.0.0"));
    }

    @Test
    public void 仓库地址指向正确仓库() {
        assertEquals("https://github.com/Ushuguang/LightTodo", UpdateChecker.REPO_URL);
        assertTrue(UpdateChecker.API_LATEST.contains("Ushuguang/LightTodo"));
        assertTrue("更新接口要用 HTTPS", UpdateChecker.API_LATEST.startsWith("https://"));
    }
}
