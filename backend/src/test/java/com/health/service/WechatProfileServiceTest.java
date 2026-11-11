package com.health.service;

import com.health.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class WechatProfileServiceTest {
    @Test void avatarIsReencodedAndResizedForPersistentCrossClientUse() throws Exception {
        var source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);
        byte[] normalized = WechatProfileService.normalizeAvatar(Base64.getEncoder().encodeToString(output.toByteArray()));
        assertEquals((byte) 0xff, normalized[0]);
        var result = ImageIO.read(new ByteArrayInputStream(normalized));
        assertEquals(256, result.getWidth()); assertEquals(128, result.getHeight());
    }
    @Test void invalidOrOversizedAvatarIsRejected() {
        assertThrows(BusinessException.class, () -> WechatProfileService.normalizeAvatar("not base64"));
        assertThrows(BusinessException.class, () -> WechatProfileService.normalizeAvatar("a".repeat(1_400_001)));
        assertThrows(BusinessException.class, () -> WechatProfileService.normalizeAvatar(Base64.getEncoder().encodeToString("<svg/>".getBytes())));
    }
}
