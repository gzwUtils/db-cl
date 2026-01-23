package db.cl.gao.common.param;


import lombok.Data;

@Data
public class CaptchaDTO {


    private String captchaKey;
    private String captchaImage;
    private long expireTime;
}
