package db.cl.gao.common.excep;
import db.cl.gao.common.ApiOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import javax.servlet.http.HttpServletRequest;


/**
 * @author gzw
 * @version 1.0
 * @Date 2022/10/13
 * @dec
 */
@SuppressWarnings("all")
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {



    /**-------- 通用异常处理方法 --------**/
    @ExceptionHandler(Exception.class)
    public ApiOutput error(HttpServletRequest request, Exception e) {
        StringBuffer url = request.getRequestURL();
        log.error("error url {} message {}",url.toString(),e.getMessage(),e);
        return ApiOutput.failure(e.getMessage());
    }

    /**-------- 指定异常处理方法 --------**/
    @ExceptionHandler(NullPointerException.class)
    public ApiOutput error(HttpServletRequest request,NullPointerException e) {
        StringBuffer url = request.getRequestURL();
        log.error("error url {} message {}",url.toString(),e.getMessage(),e);
        return ApiOutput.failure(e.getMessage());
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ApiOutput error(HttpServletRequest request,HttpClientErrorException e) {
        StringBuffer url = request.getRequestURL();
        log.error("error url {} message {}",url.toString(),e.getMessage(),e);
        return ApiOutput.failure(e.getMessage());
    }

    /**-------- 自定义定异常处理方法 --------**/
    @ExceptionHandler(DbException.class)
    public ApiOutput error(HttpServletRequest request,DbException e) {
        StringBuffer url = request.getRequestURL();
        log.error("DbException error url {} message {}",url.toString(),e.getMessage(),e);
        return ApiOutput.failure(e.getMessage());
    }
}
