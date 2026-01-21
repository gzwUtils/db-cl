package db.cl.gao.common.param;

public class DatabaseContextHolder {


    private DatabaseContextHolder(){}


    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void setDatabase(String database) {
        CONTEXT.set(database);
    }

    public static String getDatabase() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
