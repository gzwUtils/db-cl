package db.cl.gao.controller;

import db.cl.gao.common.ApiOutput;
import db.cl.gao.common.Constant;
import db.cl.gao.common.param.AccountLockInfo;
import db.cl.gao.common.param.CaptchaDTO;
import db.cl.gao.common.param.LoginDTO;
import db.cl.gao.common.param.TokenIndo;
import db.cl.gao.common.utils.AddressUtil;
import db.cl.gao.common.utils.JwtUtil;
import db.cl.gao.service.CaptchaService;
import db.cl.gao.service.LoginProtectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    private final CaptchaService captchaService;

    private final LoginProtectionService loginProtectionService;

    @Value("${system.account}")
    private String sysAccount;

    @Value("${system.password}")
    private String sysPass;

    @GetMapping("/captcha")
    public ApiOutput<CaptchaDTO> getCaptcha() {
        CaptchaDTO captcha = captchaService.generateCaptcha();
        return ApiOutput.success(captcha);
    }

    @PostMapping("/login")
    public ApiOutput<Map<String, Object>> login(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        String clientIp = AddressUtil.getIpAddress(request);

        // 1. 检查账户是否被锁定
        if (loginProtectionService.isAccountLocked(loginDTO.getUsername(), clientIp)) {
            return ApiOutput.failure(Constant.LOK_CODE, "账户已被锁定，请30分钟后再试");
        }

        // 2. 验证验证码
        if (!captchaService.validateCaptcha(loginDTO.getCaptchaKey(), loginDTO.getCaptchaCode())) {
            loginProtectionService.recordLoginFailure(loginDTO.getUsername(), clientIp);
            return ApiOutput.failure("验证码错误或已过期");
        }

        // 3. 验证用户名密码（这里简化处理，实际应查询数据库）
        boolean loginSuccess = validateCredentials(loginDTO.getUsername(), loginDTO.getPassword());

        if (!loginSuccess) {
            loginProtectionService.recordLoginFailure(loginDTO.getUsername(), clientIp);
            int remaining = loginProtectionService.getRemainingAttempts(loginDTO.getUsername(), clientIp);
            return ApiOutput.failure("用户名或密码错误，剩余尝试次数: " + remaining);
        }

        // 4. 登录成功，生成token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1);
        claims.put("ip", clientIp);

        String token = jwtUtil.generateToken(loginDTO.getUsername(), claims);

        // 5. 记录登录成功，清除失败记录
        loginProtectionService.recordLoginSuccess(loginDTO.getUsername(), clientIp);

        // 6. 返回登录信息
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", loginDTO.getUsername());
        result.put(Constant.EXPIRES_IN, jwtUtil.getRemainingTime(token));
        result.put("ip", clientIp);

        // 记录登录日志
        log.info("用户登录成功:{} , IP:{}" , loginDTO.getUsername() , clientIp);

        return ApiOutput.success( result,"登录成功");
    }

    @PostMapping("/logout")
    public ApiOutput<String> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            // 可以在这里将token加入本地缓存黑名单
            log.warn("用户登出，token:{} ..." , token.substring(0, 20));
        }
        return ApiOutput.success("登出成功");
    }

    @PostMapping("/refresh-token")
    public ApiOutput<Map<String, Object>> refreshToken(@RequestBody TokenIndo tokenIndo) {
        try {
            String newToken = jwtUtil.refreshToken(tokenIndo.getToken());
            Map<String, Object> result = new HashMap<>();
            result.put("token", newToken);
            result.put("expiresIn", jwtUtil.getRemainingTime(newToken));
            return ApiOutput.success(result);
        } catch (Exception e) {
            log.error("Token刷新失败:{}" , e.getMessage());
            return ApiOutput.failure("Token刷新失败");
        }
    }

    @PostMapping("/validate-token")
    public ApiOutput<Map<String, Object>> validateToken(@RequestBody TokenIndo tokenIndo) {
        boolean isValid = jwtUtil.validateToken(tokenIndo.getToken());
        if (isValid && Boolean.FALSE.equals(jwtUtil.isTokenExpired(tokenIndo.getToken()))) {
            String username = jwtUtil.getUsernameFromToken(tokenIndo.getToken());
            Map<String, Object> result = new HashMap<>();
            result.put("valid", true);
            result.put("username", username);
            result.put("expiresIn", jwtUtil.getRemainingTime(tokenIndo.getToken()));
            return ApiOutput.success(result);
        } else {
            return ApiOutput.failure("");
        }
    }

    @GetMapping("/stats")
    public ApiOutput<String> getCacheStats() {
        String stats = captchaService.getCacheStats();
        return ApiOutput.success(stats);
    }

    /**
     * 验证用户凭据（简化版）
     */
    private boolean validateCredentials(String username, String password) {
        return sysAccount.equals(username) && sysPass.equals(password);
    }


    // 添加新的API接口
    @GetMapping("/account/lock-info")
    public ApiOutput<AccountLockInfo> getAccountLockInfo(@RequestParam String username, HttpServletRequest request) {
        String clientIp = AddressUtil.getIpAddress(request);
        AccountLockInfo lockInfo =
                loginProtectionService.getAccountLockInfo(username, clientIp);
        return ApiOutput.success(lockInfo);
    }

    @PostMapping("/account/unlock")
    public ApiOutput<String> unlockAccount(@RequestParam String username,
                                @RequestParam(required = false) String ip,
                                HttpServletRequest request) {
        String clientIp = ip != null ? ip : AddressUtil.getIpAddress(request);
        boolean success = loginProtectionService.unlockAccount(username, clientIp);

        if (success) {
            return ApiOutput.success("账户解锁成功");
        } else {
            return ApiOutput.failure("账户未锁定或解锁失败");
        }
    }


}