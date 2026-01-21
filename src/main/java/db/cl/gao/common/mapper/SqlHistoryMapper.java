package db.cl.gao.common.mapper;

import db.cl.gao.common.model.SqlHistory;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SqlHistoryMapper {

    @Insert("INSERT INTO sql_history " +
            "(database_name, sql_text, execute_time, cost_time, result_count, " +
            "success, error_message, ip_address, created_by, created_at) " +
            "VALUES " +
            "(#{databaseName}, #{sqlText}, #{executeTime}, #{costTime}, #{resultCount}, " +
            "#{success}, #{errorMessage}, #{ipAddress}, #{createdBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SqlHistory history);

    @Select("<script>" +
            "SELECT * FROM sql_history " +
            "<where>" +
            "   <if test='databaseName != null'>AND database_name = #{databaseName}</if>" +
            "   <if test='startTime != null'>AND execute_time &gt;= #{startTime}</if>" +
            "   <if test='endTime != null'>AND execute_time &lt;= #{endTime}</if>" +
            "   <if test='success != null'>AND success = #{success}</if>" +
            "</where>" +
            "ORDER BY execute_time DESC" +
            "</script>")
    List<SqlHistory> selectList(@Param("databaseName") String databaseName,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("success") Boolean success);

    @Delete("<script>" +
            "DELETE FROM sql_history " +
            "<where>" +
            "   <if test='databaseName != null and databaseName.length() > 0 '>AND database_name = #{databaseName}</if>" +
            "</where>" +
            "</script>")
    int delete(@Param("databaseName") String databaseName);

    @Select("SELECT COUNT(*) FROM sql_history " +
            "WHERE database_name = #{databaseName} AND DATE(execute_time) = CURDATE()")
    int countToday(@Param("databaseName") String databaseName);
}
