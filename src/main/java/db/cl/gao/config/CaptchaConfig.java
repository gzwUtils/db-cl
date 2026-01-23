package db.cl.gao.config;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 验证码生成器配置
 */
@SuppressWarnings("unused")
@Component
public class CaptchaConfig {

    /**
     * 创建验证码生成器
     */
    @Bean
    public DefaultKaptcha captchaProducer() {
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();

        Properties properties = new Properties();
        // 是否有边框 默认true 我们可以自己设置yes，no
        properties.setProperty("kaptcha.border", "yes");
        // 边框颜色 默认Color.BLACK
        properties.setProperty("kaptcha.border.color", "105,179,90");
        // 边框厚度 默认为1
        properties.setProperty("kaptcha.border.thickness", "2");
        // 验证码文本字符颜色 默认Color.BLACK
        properties.setProperty("kaptcha.textproducer.font.color", "blue");
        // 验证码图片宽度 默认200
        properties.setProperty("kaptcha.image.width", "160");
        // 验证码图片高度 默认50
        properties.setProperty("kaptcha.image.height", "60");
        // 验证码文本字符大小 默认40
        properties.setProperty("kaptcha.textproducer.font.size", "38");
        // 验证码文本字符间距 默认2
        properties.setProperty("kaptcha.textproducer.char.space", "3");
        // 验证码文本字符长度 默认5
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        // 验证码文本字体样式 默认为new Font("Arial", 1, fontSize), new Font("Courier", 1, fontSize)
        properties.setProperty("kaptcha.textproducer.font.names", "Arial,Courier");
        // 验证码噪点颜色 默认Color.BLACK
        properties.setProperty("kaptcha.noise.color", "gray");
        // 干扰实现类
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.DefaultNoise");
        // 图片样式 水纹com.google.code.kaptcha.impl.WaterRipple 鱼眼com.google.code.kaptcha.impl.FishEyeGimpy 阴影com.google.code.kaptcha.impl.ShadowGimpy
        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.ShadowGimpy");
        // 背景实现类
        properties.setProperty("kaptcha.background.impl", "com.google.code.kaptcha.impl.DefaultBackground");
        // 背景颜色渐变，结束颜色
        properties.setProperty("kaptcha.background.clear.to", "white");
        // 文字渲染器
        properties.setProperty("kaptcha.word.impl", "com.google.code.kaptcha.text.impl.DefaultWordRenderer");

        Config config = new Config(properties);
        defaultKaptcha.setConfig(config);

        return defaultKaptcha;
    }
}