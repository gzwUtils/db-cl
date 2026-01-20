package db.cl.gao.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 统一API响应对象
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ApiOutput<T> {

    /** 状态码 */
    private int status = 200;

    /** 消息 */
    private String msg = "success";

    /** 请求状态，true正常、false异常 */
    private boolean success = true;

    /** 返回的结果对象 */
    private T result;

    // 快速成功方法
    public static <T> ApiOutput<T> success(T result) {
        return new ApiOutput<T>().setResult(result);
    }

    public static ApiOutput<Void> success() {
        return new ApiOutput<>();
    }

    public static <T> ApiOutput<T> success(T result, String message) {
        return new ApiOutput<T>().setResult(result).setMsg(message);
    }

    // 快速失败方法
    public static <T> ApiOutput<T> failure(String errorMsg) {
        return new ApiOutput<T>().setSuccess(false).setMsg(errorMsg);
    }

    public static <T> ApiOutput<T> failure(int status, String errorMsg) {
        return new ApiOutput<T>().setSuccess(false).setStatus(status).setMsg(errorMsg);
    }
}