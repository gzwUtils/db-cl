package db.cl.gao.service;

import db.cl.gao.common.param.AccountLockInfo;
import db.cl.gao.common.param.LoginAttemptInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;

/**
 * 登录保护服务（防止暴力破解）- 使用自定义本地缓存
 */
@SuppressWarnings("unused")
@Slf4j
@Service
public class LoginProtectionService {

    private final LocalCacheWrapper<String, LoginAttemptInfo> loginAttemptCache;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MINUTES = 30;
    private static final long CACHE_TTL_MILLIS = LOCK_TIME_MINUTES * 60 * 1000; // 30分钟

    public LoginProtectionService() {
        this.loginAttemptCache = new LocalCacheWrapper<>();
    }

    @PostConstruct
    public void init() {
        log.info("登录保护服务初始化完成，最大尝试次数: {}，锁定时间: {}分钟",
                MAX_ATTEMPTS, LOCK_TIME_MINUTES);
    }

    @PreDestroy
    public void destroy() {
        loginAttemptCache.shutdown();
        log.info("登录保护服务已关闭");
    }

    /**
     * 记录登录失败
     */
    public void recordLoginFailure(String username, String ip) {
        String key = getCacheKey(username, ip);
        LoginAttemptInfo attemptInfo = loginAttemptCache.getIfPresent(key);

        if (attemptInfo == null) {
            attemptInfo = new LoginAttemptInfo(username, ip);
        }

        attemptInfo.incrementAttempts();
        attemptInfo.setLastAttemptTime(LocalDateTime.now());

        // 如果失败次数超过阈值，锁定账户
        if (attemptInfo.getAttemptCount() >= MAX_ATTEMPTS) {
            attemptInfo.setLocked(true);
            attemptInfo.setLockTime(LocalDateTime.now());
            log.info("账户被锁定，username: {}, ip: {}", username, ip);
        }

        // 使用较长的TTL，锁定后保留更长时间
        long ttl = attemptInfo.isLocked() ?
                CACHE_TTL_MILLIS * 2 : // 锁定后保留60分钟
                CACHE_TTL_MILLIS;      // 未锁定保留30分钟

        loginAttemptCache.put(key, attemptInfo, ttl);
    }

    /**
     * 记录登录成功，清除失败记录
     */
    public void recordLoginSuccess(String username, String ip) {
        String key = getCacheKey(username, ip);
        loginAttemptCache.invalidate(key);
        log.info("登录成功，清除失败记录，username: {}, ip: {}", username, ip);
    }

    /**
     * 检查账户是否被锁定
     */
    public boolean isAccountLocked(String username, String ip) {
        String key = getCacheKey(username, ip);
        LoginAttemptInfo attemptInfo = loginAttemptCache.getIfPresent(key);

        if (attemptInfo == null || !attemptInfo.isLocked()) {
            return false;
        }

        // 检查锁定时间是否已过
        if (isLockExpired(attemptInfo.getLockTime())) {
            // 锁定已过期，清除记录
            loginAttemptCache.invalidate(key);
            log.info("锁定已过期，清除记录，username: {}", username);
            return false;
        }

        // 计算剩余锁定时间
        long remainingTime = getRemainingLockTime(attemptInfo.getLockTime());
        log.info("账户被锁定，剩余时间: {} 分钟，username: {}", remainingTime, username);

        return true;
    }

    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String username, String ip) {
        String key = getCacheKey(username, ip);
        LoginAttemptInfo attemptInfo = loginAttemptCache.getIfPresent(key);

        if (attemptInfo == null) {
            return MAX_ATTEMPTS;
        }

        return Math.max(0, MAX_ATTEMPTS - attemptInfo.getAttemptCount());
    }

    /**
     * 获取账户锁定详细信息
     */
    public AccountLockInfo getAccountLockInfo(String username, String ip) {
        String key = getCacheKey(username, ip);
        LoginAttemptInfo attemptInfo = loginAttemptCache.getIfPresent(key);

        AccountLockInfo lockInfo = new AccountLockInfo();
        lockInfo.setUsername(username);
        lockInfo.setIp(ip);

        if (attemptInfo == null) {
            lockInfo.setLocked(false);
            lockInfo.setRemainingAttempts(MAX_ATTEMPTS);
            lockInfo.setAttemptCount(0);
            return lockInfo;
        }

        lockInfo.setAttemptCount(attemptInfo.getAttemptCount());
        lockInfo.setLastAttemptTime(attemptInfo.getLastAttemptTime());

        // 检查是否被锁定
        boolean isLocked = attemptInfo.isLocked() &&
                !isLockExpired(attemptInfo.getLockTime());
        lockInfo.setLocked(isLocked);

        if (isLocked) {
            lockInfo.setLockTime(attemptInfo.getLockTime());
            lockInfo.setRemainingLockTime(getRemainingLockTime(attemptInfo.getLockTime()));
            lockInfo.setRemainingAttempts(0);
        } else {
            lockInfo.setRemainingAttempts(Math.max(0, MAX_ATTEMPTS - attemptInfo.getAttemptCount()));
        }

        return lockInfo;
    }

    /**
     * 手动解锁账户
     */
    public boolean unlockAccount(String username, String ip) {
        String key = getCacheKey(username, ip);
        LoginAttemptInfo attemptInfo = loginAttemptCache.getIfPresent(key);

        if (attemptInfo != null && attemptInfo.isLocked()) {
            attemptInfo.resetAttempts();
            loginAttemptCache.put(key, attemptInfo, CACHE_TTL_MILLIS);
            log.info("手动解锁账户成功，username: {}, ip: {}", username, ip);
            return true;
        }

        log.info("账户未锁定或不存在，解锁失败，username: {}, ip: {}", username, ip);
        return false;
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return loginAttemptCache.stats();
    }

    /**
     * 清理所有登录保护缓存
     */
    public void clearAllCache() {
        long size = loginAttemptCache.estimatedSize();
        loginAttemptCache.invalidateAll();
        log.info("已清理所有登录保护缓存，共 {} 条记录", size);
    }

    /**
     * 获取缓存key
     */
    private String getCacheKey(String username, String ip) {
        return username + ":" + ip;
    }

    /**
     * 检查锁定是否过期
     */
    private boolean isLockExpired(LocalDateTime lockTime) {
        if (lockTime == null) return true;
        return LocalDateTime.now().isAfter(
                lockTime.plusMinutes(LOCK_TIME_MINUTES)
        );
    }

    /**
     * 获取剩余锁定时间（分钟）
     */
    private long getRemainingLockTime(LocalDateTime lockTime) {
        if (lockTime == null) return 0;
        LocalDateTime unlockTime = lockTime.plusMinutes(LOCK_TIME_MINUTES);
        long minutes = java.time.Duration.between(LocalDateTime.now(), unlockTime).toMinutes();
        return Math.max(0, minutes);
    }

}