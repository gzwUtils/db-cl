package db.cl.gao.common.param;

import lombok.Data;

@Data
public class LoginDTO {


    private String username;

    private String password;

    private String captchaCode;

    private String captchaKey;
}
