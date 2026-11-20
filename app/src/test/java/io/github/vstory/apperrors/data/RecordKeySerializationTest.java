package io.github.vstory.apperrors.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.vstory.apperrors.bean.AppErrorsInfoBean;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * recordKey 同时挂在两条序列化路径上：随 Intent extras 走 Java 序列化时必须保留，
 * 写进磁盘 JSON 时必须缺席。2026-11-20 详情页堆栈全空，就是前者被 transient 破坏所致
 * （列表里没问题，只有跨进程传过去之后才丢）。两条断言把这个字段的双重身份钉住：
 * 谁想用 transient 或双向排除策略「简化」其中一边，另一边就红。
 */
public class RecordKeySerializationTest {

    private static final String KEY = "2026-11-20_10-04-25-246_ab12cd34.json";

    @Test
    public void recordKeySurvivesJavaSerialization() throws Exception {
        AppErrorsInfoBean bean = new AppErrorsInfoBean();
        bean.recordKey = KEY;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bean);
        }
        AppErrorsInfoBean restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (AppErrorsInfoBean) in.readObject();
        }

        assertEquals(KEY, restored.recordKey);
    }

    @Test
    public void recordKeyStaysOutOfDiskJson() {
        AppErrorsInfoBean bean = new AppErrorsInfoBean();
        bean.recordKey = KEY;
        bean.exceptionMessage = "boom";

        String json = AppErrorsRecordData.gsonForTest().toJson(bean);

        assertTrue(json.contains("\"exceptionMessage\":\"boom\""));
        assertFalse(json.contains("recordKey"));
        assertFalse(json.contains(KEY));
    }
}
