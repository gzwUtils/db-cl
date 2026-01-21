package db.cl.gao.common.mapper;

import db.cl.gao.common.model.OperationLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationLogMapper {

    @Insert("INSERT INTO operation_log " +
            "(database_name, operation_type, table_name, sql_text, affected_rows, " +
            "execute_time, success, message, ip_address, created_by, created_at) " +
            "VALUES " +
            "(#{databaseName}, #{operationType}, #{tableName}, #{sqlText}, #{affectedRows}, " +
            "#{executeTime}, #{success}, #{message}, #{ipAddress}, #{createdBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);

    @Select("<script>" +
            "SELECT * FROM operation_log " +
            "<where>" +
            "   <if test='databaseName != null'>AND database_name = #{databaseName}</if>" +
            "   <if test='operationType != null'>AND operation_type = #{operationType}</if>" +
            "   <if test='startTime != null'>AND execute_time &gt;= #{startTime}</if>" +
            "   <if test='endTime != null'>AND execute_time &lt;= #{endTime}</if>" +
            "   <if test='success != null'>AND success = #{success}</if>" +
            "</where>" +
            "ORDER BY execute_time DESC" +
            "</script>")
    List<OperationLog> selectList(@Param("databaseName") String databaseName,
                                  @Param("operationType") String operationType,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("success") Boolean success);

    @Delete("<script>" +
            "DELETE FROM operation_log " +
            "<where>" +
            "   <if test='databaseName != null and databaseName.length() > 0'>AND database_name = #{databaseName}</if>" +
            "</where>" +
            "</script>")
    int delete(@Param("databaseName") String databaseName);
}
