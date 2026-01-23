package db.cl.gao.common.param;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class CaptchaInfo {

    private String code;
    private LocalDateTime generateTime;
}
