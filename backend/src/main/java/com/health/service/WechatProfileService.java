package com.health.service;

import com.health.common.exception.BusinessException;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import java.util.UUID;

@Service
public class WechatProfileService {
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    public WechatProfileService(UserMapper users, JdbcTemplate jdbc) { this.users = users; this.jdbc = jdbc; }

    @Transactional
    @CacheEvict(value = "user:info", allEntries = true)
    public void save(Long userId, String nickname, String avatarBase64) {
        User user = users.selectById(userId);
        if (user == null || user.getWechatOpenid() == null) throw new BusinessException("请先使用微信登录");
        if (nickname != null) {
            String value = nickname.strip();
            if (value.isEmpty() || value.length() > 64) throw new BusinessException("昵称需为 1 至 64 个字符");
            user.setNickname(value);
        }
        if (avatarBase64 != null) {
            byte[] image = normalizeAvatar(avatarBase64);
            String key = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO wechat_user_avatar(user_id,avatar_key,image_data) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE avatar_key=VALUES(avatar_key),image_data=VALUES(image_data)", userId, key, image);
            user.setAvatar("/api/user/wechat/avatar/" + key);
        }
        user.setUpdateTime(java.time.LocalDateTime.now());
        users.updateById(user);
    }

    public byte[] avatar(String key) {
        if (!key.matches("[a-f0-9]{32}")) return null;
        return jdbc.query("SELECT a.image_data FROM wechat_user_avatar a JOIN sys_user u ON u.id=a.user_id "
                        + "WHERE a.avatar_key=? AND u.deleted=0 AND u.status=1",
                (rs, row) -> rs.getBytes(1), key).stream().findFirst().orElse(null);
    }

    // 永久保存经过解码、缩放和重新编码的图片，不保存小程序临时文件地址。
    static byte[] normalizeAvatar(String base64) {
        if (base64.length() > 1_400_000) throw new BusinessException("头像不能超过 1 MB，请压缩后重试");
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length > 1_048_576) throw new BusinessException("头像不能超过 1 MB");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new BusinessException("请选择 PNG 或 JPEG 头像");
                var reader = readers.next();
                try {
                    if (!java.util.List.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)))
                        throw new BusinessException("请选择 PNG 或 JPEG 头像");
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 16_000_000) throw new BusinessException("头像尺寸过大");
                    var source = reader.read(0);
                    double scale = Math.min(1, 256d / Math.max(width, height));
                    var target = new BufferedImage(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                    var graphics = target.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
                    } finally { graphics.dispose(); }
                    var output = new ByteArrayOutputStream();
                    ImageIO.write(target, "jpg", output);
                    return output.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("头像读取失败，请重新选择图片"); }
    }
}
