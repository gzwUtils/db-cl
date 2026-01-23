package db.cl.gao.service;

import com.google.code.kaptcha.Producer;
import db.cl.gao.common.excep.DbException;
import db.cl.gao.common.param.CaptchaDTO;
import db.cl.gao.common.param.CaptchaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 验证码服务（使用自定义本地缓存）
 */
@SuppressWarnings("unused")
@Slf4j
@Service
public class CaptchaService {

    private final Producer captchaProducer;
    private final LocalCacheWrapper<String, CaptchaInfo> captchaCache;

    // 验证码有效期（分钟）
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;
    private static final long CAPTCHA_EXPIRE_MILLIS = CAPTCHA_EXPIRE_MINUTES * 60 * 1000;

    public CaptchaService(Producer captchaProducer) {
        this.captchaProducer = captchaProducer;
        this.captchaCache = new LocalCacheWrapper<>();
    }

    @PostConstruct
    public void init() {
        log.info("验证码服务初始化完成，缓存TTL: {}分钟", CAPTCHA_EXPIRE_MINUTES);
    }

    @PreDestroy
    public void destroy() {
        captchaCache.shutdown();
        log.info("验证码服务已关闭");
    }

    /**
     * 生成验证码图片（Base64格式）
     */
    public CaptchaDTO generateCaptcha() {
        // 生成验证码文本
        String capText = captchaProducer.createText();

        // 生成验证码图片
        BufferedImage bi = captchaProducer.createImage(capText);

        // 生成验证码key
        String captchaKey = generateCaptchaKey();

        // 保存验证码信息到本地缓存
        CaptchaInfo captchaInfo = new CaptchaInfo();
        captchaInfo.setCode(capText);
        captchaInfo.setGenerateTime(LocalDateTime.now());
        captchaCache.put(captchaKey, captchaInfo, CAPTCHA_EXPIRE_MILLIS);

        // 将图片转换为Base64
        String base64Image = convertImageToBase64(bi);

        // 构建返回结果
        CaptchaDTO captchaDTO = new CaptchaDTO();
        captchaDTO.setCaptchaKey(captchaKey);
        captchaDTO.setCaptchaImage("data:image/jpeg;base64," + base64Image);
        captchaDTO.setExpireTime(CAPTCHA_EXPIRE_MINUTES * 60); // 转换为秒

        // 记录日志
        log.info("生成验证码，key: {} ,code:{}" ,captchaKey , capText);

        return captchaDTO;
    }

    /**
     * 生成验证码key（更安全的方式）
     */
    private String generateCaptchaKey() {
        return UUID.randomUUID().toString().replace("-", "") +
                System.currentTimeMillis();
    }

    /**
     * 验证验证码
     */
    public boolean validateCaptcha(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null || captchaCode.trim().isEmpty()) {
            return false;
        }

        // 从缓存中获取验证码信息
        CaptchaInfo captchaInfo = captchaCache.getIfPresent(captchaKey);

        if (captchaInfo == null) {
            log.error("验证码不存在或已过期，key: {}",captchaKey);
            return false;
        }

        // 检查验证码是否过期（双重保险）
        if (isCaptchaExpired(captchaInfo.getGenerateTime())) {
            // 移除过期的验证码
            captchaCache.invalidate(captchaKey);
            log.error("验证码已过期，key: {}",captchaKey);
            return false;
        }

        // 验证验证码（忽略大小写）
        boolean isValid = captchaInfo.getCode().equalsIgnoreCase(captchaCode.trim());

        // 验证成功后删除验证码（防止重复使用）
        if (isValid) {
            captchaCache.invalidate(captchaKey);
            log.info("验证码验证成功，已删除，key: {}",captchaKey);
        } else {
            log.warn("验证码验证失败，key: {} , 输入: {}, 正确:{}",captchaKey,captchaCode,captchaInfo.getCode());
        }

        return isValid;
    }

    /**
     * 检查验证码是否过期
     */
    private boolean isCaptchaExpired(LocalDateTime generateTime) {
        return LocalDateTime.now().isAfter(
                generateTime.plusMinutes(CAPTCHA_EXPIRE_MINUTES)
        );
    }

    /**
     * 将BufferedImage转换为Base64字符串
     */
    private String convertImageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream bao = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", bao);
            byte[] bytes = bao.toByteArray();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new DbException("验证码生成失败", e);
        }
    }

    /**
     * 获取验证码缓存统计信息
     */
    public String getCacheStats() {
        return captchaCache.stats();
    }

    /**
     * 清理所有验证码缓存
     */
    public void clearAllCaptcha() {
        captchaCache.invalidateAll();
        log.info("已清理所有验证码缓存");
    }
}