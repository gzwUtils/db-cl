package db.cl.gao.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
@SuppressWarnings("all")
@Component
public class JwtUtil {

    // 基础密钥，可以配置在application.yml中
    @Value("${jwt.secret.base:gao_yw_wh_009412sq}")
    private String baseSecret;

    // 动态部分
    private String dynamicPart = "";

    // 完整的密钥 = baseSecret + dynamicPart
    private String currentSecret;

    private static final long EXPIRATION = 86400000L; // 24小时

    // 密钥刷新间隔（毫秒）- 默认12小时刷新一次
    @Value("${jwt.secret.refresh-interval:43200000}")
    private long refreshInterval;

    private final Random random = new Random();

    // 初始化生成第一个密钥
    public JwtUtil() {
        refreshSecret();
    }

    /**
     * 刷新动态密钥部分
     */
    private void refreshSecret() {
        // 生成6位随机字符作为动态部分
        dynamicPart = generateRandomString(6);
        currentSecret = baseSecret + dynamicPart;
        System.out.println("JWT密钥已刷新，动态部分：" + dynamicPart);
    }

    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 定时刷新密钥（每12小时刷新一次）
     */
    @Scheduled(fixedRateString = "${jwt.secret.refresh-interval:43200000}")
    public void scheduledRefreshSecret() {
        refreshSecret();
    }

    /**
     * 生成Token
     */
    public String generateToken(String username) {
        return generateToken(username, new HashMap<>());
    }

    /**
     * 生成Token（带额外信息）
     */
    public String generateToken(String username, Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(SignatureAlgorithm.HS256, currentSecret)
                .compact();
    }

    /**
     * 从Token获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    /**
     * 从Token获取Claims
     */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(currentSecret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证Token是否过期
     */
    public Boolean isTokenExpired(String token) {
        Date expiration = getAllClaimsFromToken(token).getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 验证Token
     */
    public Boolean validateToken(String token) {
        try {
            // 尝试使用当前密钥解析
            getAllClaimsFromToken(token);
            return true;
        } catch (Exception e) {
            // 如果失败，可能是密钥已刷新，尝试使用旧的密钥（这里简单处理）
            System.out.println("Token验证失败，尝试刷新密钥后重试");
            return false;
        }
    }

    /**
     * 刷新Token
     */
    public String refreshToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        claims.setIssuedAt(new Date());
        claims.setExpiration(new Date(System.currentTimeMillis() + EXPIRATION));
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, currentSecret)
                .compact();
    }

    /**
     * 获取剩余有效时间（毫秒）
     */
    public Long getRemainingTime(String token) {
        try {
            Date expiration = getAllClaimsFromToken(token).getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return -1L;
        }
    }
}