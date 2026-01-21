package db.cl.gao.common.annotation;

import java.lang.annotation.*;

/**
 * SQL执行追踪注解
 * 用于自动记录SQL执行历史
 */
@SuppressWarnings("unused")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackSql {

    /**
     * SQL参数名称（支持Spring EL表达式）
     */
    String sqlParam() default "sql";

    /**
     * 是否记录结果集数量
     */
    boolean countResult() default true;

    /**
     * 记录描述信息
     */
    String description() default "";

    /**
     * 是否忽略错误（不抛出异常）
     */
    boolean ignoreError() default false;
}
