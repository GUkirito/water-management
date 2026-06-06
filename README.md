# 村级自来水管理系统

> Spring Boot 4.0 + Vue 3 + SQLite + JDK 25 全栈项目

---

## 📖 项目简介

村级自来水管理系统是一套面向村/社区级别的小型自来水收费管理软件，支持村民水表信息管理、批量抄表录入、水费/材料费合并缴费、报表导出等功能。

**核心特点：**
- 📦 **单文件部署**：打包为单个 .exe 安装文件，用户双击安装即用，无需安装 Java
- 🗄️ **嵌入式数据库**：使用 SQLite 单文件数据库，备份即复制文件
- 🌐 **B/S 架构**：浏览器访问，支持局域网内多台电脑使用
- 📊 **Excel 导入导出**：支持下载抄表模板 → 线下填写 → 批量导入

---

## 🛠️ 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 4.0.6 |
| 持久层 | Spring Data JPA + Hibernate | 7.2.12 |
| 数据库 | SQLite (sqlite-jdbc) | 3.46.1 |
| API 文档 | SpringDoc OpenAPI | 3.0.1 |
| Excel 操作 | EasyExcel | 4.0.3 |
| 工具库 | Lombok, Hutool | — |
| 前端框架 | Vue 3 + Vite | 6.4 |
| UI 组件库 | Element Plus | 2.10 |
| HTTP 客户端 | Axios | 1.7 |
| 路由 | Vue Router | 4.5 |
| 打包工具 | jlink + jpackage (JDK 自带) | JDK 25 |
| 运行环境 | Windows 10/11 (64位) | — |

---

## 📁 项目结构

```
water-management/
├── build.bat                        ← 一键打包脚本
├── pom.xml                          ← Maven 依赖配置
├── frontend/                        ← Vue 3 前端源码
│   ├── vite.config.js               ← Vite 构建配置 + API 代理
│   └── src/
│       ├── main.js                  ← 应用入口
│       ├── App.vue                  ← 根组件
│       ├── router/index.js          ← 路由（6 个页面）
│       ├── api/index.js             ← Axios 封装 + 全部 API
│       ├── layouts/
│       │   └── MainLayout.vue       ← 主布局（侧边菜单 + 内容区）
│       └── views/
│           ├── Dashboard.vue        ← 仪表盘
│           ├── Households.vue       ← 村民管理
│           ├── Readings.vue         ← 抄表录入
│           ├── Billing.vue          ← 收费管理
│           ├── Reports.vue          ← 报表中心
│           └── Settings.vue         ← 系统设置
├── src/main/java/com/example/watermanagement/
│   ├── WaterManagementApplication.java  ← 启动类
│   ├── config/
│   │   ├── SpringDocConfig.java     ← API 文档配置
│   │   └── SpaConfig.java          ← SPA 路由回退
│   ├── controller/                  ← 控制器层（REST API）
│   │   ├── HouseholdController.java
│   │   ├── ReadingController.java
│   │   ├── PaymentController.java
│   │   └── ReportController.java
│   ├── dto/                         ← 数据传输对象
│   │   ├── ApiResponse.java         ← 统一响应格式
│   │   ├── HouseholdRequest.java
│   │   ├── PaymentRequest.java
│   │   ├── ReadingBatchItem.java
│   │   ├── ReadingExportRow.java    ← Excel 模板行
│   │   ├── WaterBillReportRow.java
│   │   └── MaterialSummaryRow.java
│   ├── entity/                      ← 数据库实体（JPA）
│   │   ├── Household.java           ← 村民/水表信息
│   │   ├── Reading.java             ← 抄表记录
│   │   ├── WaterBill.java           ← 水费账单
│   │   ├── MaterialBill.java        ← 材料费账单
│   │   └── Payment.java             ← 缴费明细
│   ├── exception/                   ← 异常处理
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   ├── repository/                  ← 数据访问层（JPA Repository）
│   ├── service/                     ← 业务逻辑层
│   │   └── impl/
│   └── util/
│       └── ExcelUtil.java           ← Excel 工具类
└── src/main/resources/
    ├── application.yml              ← 应用配置
    └── static/                      ← 前端构建产物
```

---

## 🗄️ 数据库设计

### E-R 关系图

```
households (村民/水表信息)
    │
    ├── 1:1 ── material_bills (材料费账单)
    │
    ├── 1:N ── readings (抄表记录)
    │               │
    │               └── 生成 ──→ water_bills (水费账单)
    │
    └── ──→ payments (缴费明细) ←── 关联 water_bills / material_bills
```

### 数据表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `households` | 村民水表信息 | id, household_name, water_meter_id (unique), village_name, is_active |
| `readings` | 抄表记录 | id, water_meter_id, reading_date, current_reading, previous_reading, usage_amount |
| `water_bills` | 水费账单 | id, water_meter_id, bill_year, bill_month, water_charge, actual_water_paid, water_status |
| `material_bills` | 材料费账单 | id, water_meter_id, total_fee (default 1500), actual_paid, status |
| `payments` | 缴费明细 | id, bill_type (water/material), bill_id, amount, paid_date, payment_method |

---

## 🔌 API 接口

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **村民管理** | GET | `/api/households/list` | 分页列表，支持村名多选 + 水表编号模糊搜索 |
| | POST | `/api/households/add` | 新增村民（自动创建材料费账单） |
| | PUT | `/api/households/update/{id}` | 更新（支持换绑水表） |
| | DELETE | `/api/households/delete/{id}` | 软删除 |
| **抄表管理** | GET | `/api/readings/export-template` | 导出空白抄表 Excel 模板 |
| | POST | `/api/readings/import` | 导入已填写的 Excel |
| | POST | `/api/readings/batch` | 批量保存表底（JSON） |
| | POST | `/api/readings/single` | 单户录入 |
| | GET | `/api/readings/by-month` | 按月查询抄表记录 |
| **收费管理** | GET | `/api/payments/pending-water` | 未缴清水费列表 |
| | GET | `/api/payments/pending-material` | 未缴清材料费 |
| | POST | `/api/payments/pay` | 缴费（支持合并多月水费） |
| | GET | `/api/payments/history` | 缴费历史 |
| **报表中心** | GET | `/api/reports/water-bill` | 水费月报表数据 |
| | GET | `/api/reports/water-bill/export` | 导出水费月报表 Excel |
| | GET | `/api/reports/material-summary` | 材料费统计数据 |
| | GET | `/api/reports/material-summary/export` | 导出材料费统计 Excel |

API 文档页面：`http://localhost:8080/swagger-ui.html`

---

## 🔑 核心业务逻辑

### 1. 批量抄表流程

```
[选择年月 + 村名] → 导出空白模板(Excel)
→ 抄表员线下填写本次表底
→ 导入填好的Excel
→ 后端逐行处理:
    读取上次表底 → 计算用量 → 异常检测(倒转/突增)
    → 写入 readings 表
    → 生成 water_bills (应收水费 = 用量 × 1.8元/吨，状态=未收)
→ 返回结果：成功N条，异常M条
```

### 2. 合并缴费流程

```
[选择户] → 展示所有未缴清水费账单（可多选）
→ 用户勾选多月 → 系统自动计算总应收
→ 用户输入实收金额 → 显示找零
→ 确认支付
→ 后端按欠费比例分配金额到每个账单
→ 创建 payment 记录 → 更新 bill 的 actual_paid
→ 重算状态 (未收 / 部分收 / 已收)
```

### 3. 异常检测

| 异常类型 | 触发条件 | 标记 |
|---------|---------|------|
| 表底倒转 | 本次表底 < 上次表底 | `is_abnormal=true, reason="表底倒转"` |
| 用量突增 | (本次−上次) > 阈值(默认100吨) | `is_abnormal=true, reason="用量突增"` |

---

## 🚀 快速开始

### 环境要求

- JDK 25
- Node.js 18+
- Maven 3.9+ (项目自带 Maven Wrapper)

### 开发模式

```bash
# 1. 启动后端（IDEA 中运行 WaterManagementApplication 或命令行）
./mvnw spring-boot:run

# 2. 启动前端（另一个终端）
cd frontend
npm install
npm run dev
```

- 前端：`http://localhost:3000`（热更新）
- 后端：`http://localhost:8080`
- API 文档：`http://localhost:8080/swagger-ui.html`

### 生产部署（单文件 EXE）

```bash
# 双击运行
build.bat

# 产物在 installer/VillageWaterManagement-1.0.0.exe
# 发给用户，双击安装即可，无需安装 Java
```

### 手动部署（JAR 运行）

```bash
# 构建前端
cd frontend && npm run build && cd ..

# 构建后端
./mvnw package -DskipTests

# 运行
java -jar target/water-management-0.0.1-SNAPSHOT.jar
```

---

## ⚙️ 主要配置项

`application.yml` 中的关键配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:./data/water_meter.db   # 数据库文件位置
  jpa:
    hibernate:
      ddl-auto: update                       # 自动建表

water:
  price: 1.8                                  # 水价（元/吨）
  abnormal:
    threshold: 100                            # 异常阈值（吨）
```

---

## 📦 打包原理

```
[npm run build]    → Vue 前端 → static/
[mvnw package]     → Spring Boot → fat JAR
[jdeps]            → 分析 Java 模块依赖
[jlink]            → 裁剪 JRE (50MB)
[jpackage --type exe] → 生成单文件安装包 (125MB)
```

用户安装包自带裁剪版 JDK 25，无需预装任何 Java 环境。

---

## 📝 License

内部使用项目
