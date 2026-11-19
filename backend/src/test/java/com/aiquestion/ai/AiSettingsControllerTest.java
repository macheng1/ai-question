package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSettingsControllerTest {
    @Test
    void savesEncryptedSecretForCurrentOwnerAndRetainsItOnModelChange() throws Exception {
        String url = "https://example.com/v1";
        var db = mock(AiSettingsRepository.class);
        var master = new byte[32];
        var controller = new AiSettingsController(new AiSettingsService(db, new AiKeyCipher(Base64.getEncoder().encodeToString(master))), mock(AiChatService.class));
        var request = new MockHttpServletRequest();
        request.setAttribute("managementUserId", 12L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertFalse(controller.read().data().keyConfigured());
            assertThrows(BusinessException.class, () -> controller.save(new AiSettingsService.Update("bailian", url, "qwen-plus", "")));
            var settings = controller.save(new AiSettingsService.Update("bailian", url, "qwen-plus", "sample-test-only"));
            assertTrue(settings.data().keyConfigured());
            var sealed = ArgumentCaptor.forClass(byte[].class);
            verify(db).save(eq(12L), eq("bailian"), eq(url), eq("qwen-plus"), sealed.capture());
            byte[] bytes = sealed.getValue();
            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(master, "AES"),
                new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            decrypt.updateAAD(ByteBuffer.allocate(8).putLong(12L).array());
            assertEquals("sample-test-only", new String(decrypt.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8));
            when(db.updateModel(12L, "bailian", url, "qwen-flash")).thenReturn(1);
            assertEquals("qwen-flash", controller.save(new AiSettingsService.Update("bailian", url, "qwen-flash", null)).data().model());
            when(db.configuration(12L)).thenReturn(new AiSettingsRepository.Configuration("bailian", url, "qwen-flash"));
            assertThrows(BusinessException.class, () -> controller.save(new AiSettingsService.Update("deepseek", url, "test", null)));
            assertThrows(BusinessException.class, () -> controller.save(new AiSettingsService.Update("bailian", "https://other.example/v1", "test", null)));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
