package db.cl.gao.common.interceptor;


import db.cl.gao.common.param.DatabaseContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class DatabaseInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头或参数中获取数据库信息
        String database = request.getHeader("X-Database");
        if (database == null) {
            database = request.getParameter("database");
        }

        if (database != null && !database.trim().isEmpty()) {
            DatabaseContextHolder.setDatabase(database);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除数据库上下文，防止内存泄漏
        DatabaseContextHolder.clear();
    }
}
