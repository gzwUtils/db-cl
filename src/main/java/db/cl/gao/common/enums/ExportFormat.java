package db.cl.gao.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@SuppressWarnings("unused")
@Getter
public enum ExportFormat {

    EXCEL("xlsx"),
    CSV("csv"),
    JSON("json"),
    SQL("sql");

    private final String extension;

    ExportFormat(String extension) {
        this.extension = extension;
    }

    // 序列化时使用小写
    @JsonValue
    public String getValue() {
        return this.name().toLowerCase();
    }

    // 反序列化时支持大小写不敏感
    @JsonCreator
    public static ExportFormat fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ExportFormat format : values()) {
            if (format.name().equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("无效的导出格式: " + value);
    }

    public static ExportFormat fromExtension(String extension) {
        for (ExportFormat format : values()) {
            if (format.extension.equalsIgnoreCase(extension)) {
                return format;
            }
        }
        return null;
    }
}