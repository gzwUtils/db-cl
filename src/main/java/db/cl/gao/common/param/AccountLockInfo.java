package db.cl.gao.common.param;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AccountLockInfo {

    private String username;
    private String ip;
    private int attemptCount;
    private boolean locked;
    private LocalDateTime lastAttemptTime;
    private LocalDateTime lockTime;
    private long remainingLockTime; // 分钟
    private int remainingAttempts;
}
