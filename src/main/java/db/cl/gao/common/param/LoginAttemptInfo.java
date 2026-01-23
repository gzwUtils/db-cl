package db.cl.gao.common.param;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginAttemptInfo {
    private String username;
    private String ip;
    private int attemptCount;
    private LocalDateTime lastAttemptTime;
    private boolean locked;
    private LocalDateTime lockTime;

    public LoginAttemptInfo(String username, String ip) {
        this.username = username;
        this.ip = ip;
        this.attemptCount = 0;
        this.locked = false;
        this.lastAttemptTime = LocalDateTime.now();
    }

    /**
     * 增加尝试次数
     */
    public void incrementAttempts() {
        this.attemptCount++;
        this.lastAttemptTime = LocalDateTime.now();
    }

    /**
     * 重置尝试次数
     */
    public void resetAttempts() {
        this.attemptCount = 0;
        this.locked = false;
        this.lockTime = null;
        this.lastAttemptTime = LocalDateTime.now();
    }
}