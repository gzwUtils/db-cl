package db.cl.gao.common.annotation;

import java.lang.annotation.*;

@SuppressWarnings("unused")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {

    /**
     * 操作类型
     */
    OperationType type() default OperationType.QUERY;

    /**
     * 表名（支持SpEL表达式，如：#tableName）
     */
    String tableName() default "";

    /**
     * 是否记录参数
     */
    boolean logParams() default true;

    /**
     * 是否记录结果
     */
    boolean logResult() default true;

    /**
     * 是否根据SQL动态调整操作类型
     */
    boolean dynamicType() default true;

    /**
     * 操作说明
     */

    String value() default "";

    /**
     * 需要忽略的异常类型
     */
    Class<? extends Throwable>[] ignoreExceptions() default {};

    /**
     * 操作类型枚举
     */
    enum OperationType {
        QUERY,      // 查询
        INSERT,     // 插入
        UPDATE,     // 更新
        DELETE,     // 删除
        CREATE,     // 创建
        ALTER,      // 修改
        DROP,       // 删除表/库
        TRUNCATE,   // 清空表
        RENAME,     // 重命名
        PERMISSION, // 权限操作
        TRANSACTION,// 事务操作
        EXPLAIN,
        OTHER       // 其他
    }
}