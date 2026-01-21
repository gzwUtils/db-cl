<h1 align="center">数据库管理系统</h1>
<p align="center">
  <img src="https://img.shields.io/github/languages/code-size/nanchengcyu/TechMindWave-backend" alt="code size"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-2.7.10-brightgreen" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Java-8-blue" alt="Java"/>
  <img src="https://img.shields.io/badge/Author-gaozw-orange" alt="Author" />
</p>
<hr>
<h2>项目概述</h2>
一个功能全面的数据库管理平台，集成了数据查询、SQL执行、可视化分析、数据迁移等核心功能，并提供直观的图形界面操作体验。系统支持多数据库连接与管理，适用于数据分析、日常运维和数据库开发等场景。

主要功能
1. 数据表查询
   可视化表结构浏览

分页数据预览

快速条件筛选

字段信息查看

2. 数据库切换
   多数据源连接管理

动态连接切换

连接配置持久化

连接状态监控

3. SQL执行
   多语句执行支持

语法高亮与提示

执行结果分页显示

执行时间统计

4. 图表分析
   查询结果可视化

多种图表类型（柱状图、折线图、饼图等）

图表自定义配置

分析结果导出

5. 导入导出
   多种格式支持（CSV、Excel、JSON、SQL）

批量数据导入

查询结果导出

模板下载与使用

6. 历史记录
   SQL执行历史保存

操作记录追踪

历史记录检索

常用SQL收藏

7. 数据同步服务（独立模块）
   跨数据库数据同步<a href="http://101.42.236.45:7801/tasks">data-sync-service</a>

全量同步
同步状态监控

8. 技术架构
后端技术栈
开发语言: Java  

Web框架: Spring Boot

数据库驱动: 多数据库支持（MySQL、PostgreSQL、SQL Server等）


API接口: RESTful API设计

前端技术栈
框架: Vue.js


图表库: ECharts / D3.js

构建工具: Vite

数据存储
配置存储: MySQL 

安装部署
环境要求
Java 8+ 、 Node.js 14+

MySQL 5.7+

现代浏览器（Chrome 80+、Firefox 75+、Edge 80+）

9. 快速开始
后端部署
bash
# 克隆项目
git clone <repository-url>

# 安装依赖（根据实际技术栈调整）
mvn install  # Java项目
# 或
npm install  # Node.js项目

# 配置数据库
# 1. 创建数据库
# 2. 修改配置文件 config/application.yml #指定默认数据库
