

-- doc.operation_log definition

CREATE TABLE `operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `database_name` varchar(100) NOT NULL COMMENT '数据库名称',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型',
  `table_name` varchar(100) DEFAULT NULL COMMENT '表名',
  `sql_text` text COMMENT 'SQL语句',
  `affected_rows` int(11) DEFAULT '0' COMMENT '影响行数',
  `execute_time` datetime NOT NULL COMMENT '执行时间',
  `success` tinyint(1) DEFAULT '1' COMMENT '是否成功',
  `message` text COMMENT '操作结果消息',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `created_by` varchar(100) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_database` (`database_name`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_execute_time` (`execute_time`),
  KEY `idx_success` (`success`)
) ENGINE=InnoDB AUTO_INCREMENT=118 DEFAULT CHARSET=utf8mb4 COMMENT='操作日志记录';




-- doc.sql_history definition

CREATE TABLE `sql_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `database_name` varchar(100) NOT NULL COMMENT '数据库名称',
  `sql_text` text NOT NULL COMMENT 'SQL语句',
  `execute_time` datetime NOT NULL COMMENT '执行时间',
  `cost_time` bigint(20) DEFAULT '0' COMMENT '耗时(毫秒)',
  `result_count` int(11) DEFAULT '0' COMMENT '结果数量',
  `success` tinyint(1) DEFAULT '1' COMMENT '是否成功',
  `error_message` text COMMENT '错误信息',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `created_by` varchar(100) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_database` (`database_name`),
  KEY `idx_execute_time` (`execute_time`),
  KEY `idx_success` (`success`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COMMENT='SQL执行历史记录';