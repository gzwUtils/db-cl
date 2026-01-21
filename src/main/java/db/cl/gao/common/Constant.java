package db.cl.gao.common;


import java.util.regex.Pattern;

@SuppressWarnings("all")
public class Constant {

    private Constant(){}



    // 通用业务状态码
    public static final int SUCCESS_CODE = 200;
    public static final int BAD_REQUEST_CODE = 400;
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FORBIDDEN_CODE = 403;
    public static final int NOT_FOUND_CODE = 404;
    public static final int INTERNAL_ERROR_CODE = 500;


    public static final String SUCCESS = "success";


    public static final String MESSAGE = "message";


    public static final String CHAT_DATA = "chartData";

    public static final String DEFAULT_DATABASE = "default";


    public static final String TABLE_COUNT = "tableCount";


    public static final String UNKNOWN = "unknown";


    public static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    public static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("--.*|/\\*.*?\\*/");
    public static final Pattern TABLE_CLEAN_PATTERN = Pattern.compile("[`'\"]");

}
