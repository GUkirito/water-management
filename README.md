# 村级自来水管理系统

> Spring Boot 4.0 + Vue 3 + SQLite + JDK 25 全栈项目  
> **当前版本：V1.6.8**

---

## 📖 项目简介

村级自来水管理系统是一套面向村/社区级别的小型自来水收费管理软件，支持村民水表信息管理、批量抄表录入、水费收费、**材料费独立管理**、报表导出、一键备份等功能。

**核心特点：**
- 📦 **单文件部署**：打包为单个 .exe 安装文件，用户双击安装即用，无需安装 Java
- 🗄️ **嵌入式数据库**：使用 SQLite 单文件数据库，备份即下载文件
- 🌐 **B/S 架构**：浏览器访问，支持局域网内多台电脑使用
- 📊 **Excel 导入导出**：支持下载抄表模板 → 线下填写 → 批量导入（含预览确认）
- 🧾 **材料费独立管理**：材料费与水费完全分离，独立导入/收费/统计
- 💰 **水费预存**：支持预存余额充值，自动抵扣水费账单
- 🔒 **月份锁定**：对已结算月份加锁，防止误改历史账单
- 🩺 **账务健康检查**：自动检测异常账务数据，支持手动调整修正
- 💾 **一键数据备份/恢复**：设置页面下载或恢复数据库文件

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
├── build.bat                        ← 一键打包脚本（V1.6.7+）
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
│           ├── Dashboard.vue        ← 仪表盘（含水费+材料费统计）
│           ├── Readings.vue         ← 抄表录入（含历史导入、预览确认）
│           ├── Billing.vue          ← 水费收费（含未缴账单列表+预存余额）
│           ├── MaterialFee.vue      ← 材料费管理（独立模块）
│           ├── Reports.vue          ← 报表中心（水费+材料费双套隔离）
│           └── Settings.vue         ← 系统设置（配置+备份恢复+账务健康）
├── src/main/java/com/example/watermanagement/
│   ├── WaterManagementApplication.java  ← 启动类
│   ├── config/
│   │   ├── SpringDocConfig.java     ← API 文档配置
│   │   └── SpaConfig.java          ← SPA 路由回退
│   ├── controller/                  ← 控制器层（REST API）
│   │   ├── HouseholdController.java      ← 村民管理
│   │   ├── ReadingController.java        ← 抄表录入（含历史导入/预览）
│   │   ├── PaymentController.java        ← 水费收费+预存管理
│   │   ├── MaterialRecordController.java ← 材料费管理
│   │   ├── ReportController.java         ← 报表中心
│   │   ├── AccountingHealthController.java ← 账务健康+月份锁定+调整
│   │   └── SettingsController.java       ← 系统设置+备份恢复
│   ├── dto/                         ← 数据传输对象
│   │   ├── ApiResponse.java         ← 统一响应格式
│   │   ├── AccountingHealthIssue.java
│   │   ├── AccountingAdjustmentRequest.java
│   │   ├── MonthLockRequest.java
│   │   ├── PendingWaterBillRow.java
│   │   ├── ReadingImportDetail.java
│   │   ├── HistoricalReadingImportRow.java
│   │   ├── VillageCollectionSummaryRow.java
│   │   └── ...（其余 DTO）
│   ├── entity/                      ← 数据库实体（JPA）
│   │   ├── Household.java           ← 村民/水表信息
│   │   ├── Reading.java             ← 抄表记录
│   │   ├── WaterBill.java           ← 水费账单
│   │   ├── Payment.java             ← 水费缴费明细
│   │   ├── PrepaymentLog.java       ← 水费预存流水（V1.6.3+）
│   │   ├── MonthLock.java           ← 月份锁定（V1.6.x+）
│   │   ├── AccountingAdjustment.java ← 账务调整记录（V1.6.x+）
│   │   ├── MaterialRecord.java      ← 材料费记录（独立表）
│   │   └── MaterialPayment.java     ← 材料费缴费明细
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
    ├── 1:N ── readings (抄表记录)
    │               │
    │               └── 生成 ──→ water_bills (水费账单)
    │                                │
    │                                └── 缴费 ──→ payments (水费缴费明细)
    │
    ├── 1:N ── prepayment_logs (水费预存流水)
    │
    └── 1:1 ── material_records (材料费记录)
                      │
                      └── 缴费 ──→ material_payments (材料费缴费明细)

month_locks (月份锁定，独立)
accounting_adjustments (账务调整审计日志，独立)
```

### 数据表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `households` | 村民水表信息 | id, household_name, water_meter_id (unique), village_name, phone, is_active |
| `readings` | 抄表记录 | id, water_meter_id, reading_date, current_reading, previous_reading, usage_amount, is_abnormal, abnormal_reason |
| `water_bills` | 水费账单 | id, water_meter_id, bill_year, bill_month, water_charge, actual_paid, status |
| `payments` | 水费缴费明细 | id, bill_id, amount, paid_date, payment_method, operator, note |
| `prepayment_logs` | 水费预存流水 | id, water_meter_id, amount（正=充值/负=抵扣）, type, bill_id, remark |
| `month_locks` | 月份锁定 | id, bill_year, bill_month, operator, note, locked_at |
| `accounting_adjustments` | 账务调整审计 | id, target_type, target_id, before_amount, after_amount, reason, operator |
| `material_records` | 材料费记录 | id, water_meter_id, total_fee, actual_paid, status, paid_at, collector |
| `material_payments` | 材料费缴费明细 | id, material_record_id, amount, paid_date, collector, note |

---

## 🔌 API 接口

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **村民管理** | GET | `/api/households/list` | 分页列表，支持村名筛选 + 水表编号模糊搜索 |
| | POST | `/api/households/add` | 新增村民 |
| | PUT | `/api/households/update/{id}` | 更新村民信息 |
| | DELETE | `/api/households/delete/{id}` | 永久物理删除（含关联抄表/账单） |
| | POST | `/api/households/batch-delete` | 批量删除 |
| | POST | `/api/households/delete-by-village` | 按村组全部删除 |
| | POST | `/api/households/batch-update-village` | 批量修改村组 |
| | GET | `/api/households/export` | 导出现有村民 Excel |
| | POST | `/api/households/import` | 通过 Excel 批量导入村民 |
| | POST | `/api/households/import-from-register` | 从水费登记册导入 |
| **抄表管理** | GET | `/api/readings/export-template` | 导出当期空白抄表模板 |
| | GET | `/api/readings/history-template` | 导出历史抄表导入模板 |
| | POST | `/api/readings/import/preview` | 预览当期抄表导入结果（不写库） |
| | POST | `/api/readings/import` | 导入当期已填写的 Excel |
| | POST | `/api/readings/history-import/preview` | 预览历史抄表导入结果 |
| | POST | `/api/readings/history-import` | 导入历史抄表数据 |
| | POST | `/api/readings/batch` | 批量保存表底 |
| | POST | `/api/readings/single` | 单条录入表底 |
| | GET | `/api/readings/by-date` | 按日期+村组查询抄表记录 |
| | GET | `/api/readings/by-month` | 按年月查询抄表记录 |
| | GET | `/api/readings/abnormal` | 异常抄表列表 |
| | GET | `/api/readings/config` | 获取水价和异常阈值配置 |
| | POST | `/api/readings/config` | 更新水价和异常阈值配置 |
| **水费收费** | GET | `/api/payments/pending-water` | 按户查询未缴清水费（用于收款） |
| | GET | `/api/payments/pending-water-list` | 全村未缴清水费列表（含筛选） |
| | GET | `/api/payments/all-water-bills` | 全量水费账单查询 |
| | POST | `/api/payments/pay` | 缴费（支持合并多月水费） |
| | GET | `/api/payments/history` | 缴费历史 |
| | GET | `/api/payments/water-prepayment-balance` | 查询户预存余额 |
| | GET | `/api/payments/water-prepayment-logs` | 查询户预存流水 |
| **材料费管理** | GET | `/api/material-records/list` | 分页列表，支持村名/状态/日期筛选 |
| | POST | `/api/material-records` | 新增材料费记录 |
| | PUT | `/api/material-records/{id}` | 更新材料费记录 |
| | DELETE | `/api/material-records/{id}` | 删除材料费记录 |
| | POST | `/api/material-records/batch-delete` | 批量删除 |
| | POST | `/api/material-records/import` | 从材料费.xlsx 一键导入 |
| | GET | `/api/material-records/export` | 导出材料费统计 Excel |
| | POST | `/api/material-records/{id}/collect` | 收取材料费 |
| | GET | `/api/material-records/{id}/payments` | 查看材料费缴费历史 |
| **账务管理** | GET | `/api/accounting/health-check` | 账务健康检查（检测异常数据） |
| | GET | `/api/accounting/month-locks` | 查询已锁定月份 |
| | POST | `/api/accounting/month-locks` | 锁定指定月份 |
| | DELETE | `/api/accounting/month-locks` | 解锁指定月份 |
| | GET | `/api/accounting/adjustments` | 查询账务调整记录 |
| | POST | `/api/accounting/adjustments/water-bills/{id}` | 调整水费账单金额 |
| | POST | `/api/accounting/adjustments/material-records/{id}` | 调整材料费金额 |
| **报表中心** | GET | `/api/reports/water-bill` | 水费月报表数据 |
| | GET | `/api/reports/water-bill/export` | 导出水费月报表 Excel |
| **系统设置** | GET | `/api/settings/info` | 获取系统信息（数据库路径等） |
| | GET | `/api/settings/backup/download` | 一键下载数据库备份 |
| | POST | `/api/settings/backup/restore` | 从文件恢复数据库 |

API 文档页面：`http://localhost:8080/swagger-ui.html`

---

## 🔑 核心业务逻辑

### 1. 批量抄表流程

```
[选择日期 + 村名] → 导出空白模板(Excel)
→ 抄表员线下填写本次表底
→ 上传 Excel → 预览导入结果（成功/异常/跳过 分类展示）
→ 确认后正式导入
→ 后端逐行处理:
    读取上次表底 → 计算用量 → 异常检测(倒转/突增)
    → 写入 readings 表
    → 生成 water_bills (应收水费 = 计费用水量 × 水价，状态=未收)
→ 返回结果：成功N条，异常M条
```

### 2. 历史抄表导入

```
[下载历史导入模板] → 填写历史年月+表底数据
→ 预览导入结果 → 确认后写入
用于补录历史数据或迁移旧系统数据，不影响当前账期逻辑
```

### 3. 水费缴费流程

```
[选择户 / 或从未缴账单列表直接点收款]
→ 展示所有未缴清水费账单（可多选）
→ 查询该户预存余额
→ 用户勾选多月 → 系统自动计算总应收
→ 用户输入实收金额 → 显示找零
→ 确认支付
→ 后端按欠费比例分配金额到每个账单
→ 创建 payment 记录 → 更新 bill 的 actual_paid
→ 重算状态 (未收 / 部分收 / 已收)
```

### 4. 水费预存

```
→ 收款员收取预存款 → 系统记录 prepayment_log（正数充值）
→ 缴费时预存余额可抵扣水费
→ 抵扣后记录 prepayment_log（负数扣减，关联 bill_id）
→ Dashboard/收费页实时显示余额
```

### 5. 月份锁定

```
对已完成收费的月份加锁 → 锁定后该月账单不允许修改
→ 账务健康检查可自动识别未锁定的已结算月份
→ 支持手动解锁（操作记录留存）
```

### 6. 账务健康检查

```
→ 自动扫描：金额不一致账单、负余额预存、孤立缴费记录等
→ 以问题列表形式展示，支持直接跳转修正
→ 手动调整后记录 accounting_adjustment（含调整前后金额+原因）
```

### 7. 材料费管理（独立模块）

```
材料费与水费完全分离：
→ 通过导入 材料费.xlsx 一键生成所有户的材料费记录
→ 在材料费管理页面按村组筛选 → 点击 [💰] 收费
→ 记录收费日期、收款人、实收金额
→ 支持部分收费（分多次缴清）
→ Dashboard 展示材料费应收/实收/收缴率
```

### 8. 异常检测

| 异常类型 | 触发条件 | 标记 |
|---------|---------|------|
| 表底倒转 | 本次表底 < 上次表底 | `is_abnormal=true, reason="表底倒转"` |
| 用量突增 | (本次−上次) > 阈值(默认100吨) | `is_abnormal=true, reason="用量突增"` |

---

## 🚀 快速开始

### 环境要求

- JDK 25
- Node.js 18+
- Maven 3.9+（项目自带 Maven Wrapper，无需单独安装）

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
# 双击运行，或在项目根目录执行
build.bat

# 产物在 installer/VillageWaterManagement-1.6.7.exe
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
    url: jdbc:sqlite:${user.home}/.water-management/data/water_meter.db
  jpa:
    hibernate:
      ddl-auto: update                       # 自动建表/迁移

water:
  price: 1.8                                  # 水价（元/吨）
  abnormal:
    threshold: 100                            # 异常阈值（吨）
```

> 水价和阈值可在 Settings 页面中修改，保存后立即生效，无需重启。

---

## 📦 打包原理

```
[npm run build]    → Vue 前端 → static/
[mvnw package]     → Spring Boot → fat JAR
[jdeps]            → 分析 Java 模块依赖
[jlink]            → 裁剪 JRE (~50MB)
[jpackage --type exe] → 生成单文件安装包 (~125MB)
```

用户安装包自带裁剪版 JDK 25，无需预装任何 Java 环境。

> **注意**：`--type exe` 需要安装 WiX Toolset v3.14。未安装时 `build.bat` 自动回退为便携版 `app-image`。

---

## 💾 数据备份与恢复

- **备份**：进入系统设置 → 点击「📥 一键下载备份」下载当前数据库文件
- **恢复**：进入系统设置 → 上传备份文件恢复数据库

**建议频率：** 每月备份一次，保留最近 12 个月的备份文件。

---

## 📝 更新日志（主要版本）

| 版本 | 主要变更 |
|------|---------|
| V1.6.8 | 历史抄表批量导入、一键打包脚本改进、修复已知问题 |
| V1.6.7 | UI 全面改进、修复多处已知问题 |
| V1.6.4 | 缴费管理新增未缴水费账单列表，支持直接从列表收款 |
| V1.6.3 | 新增水费预存充值/抵扣逻辑 |
| V1.6.1 | 报表中心水费/材料费双套完全隔离 |
| V1.6.0 | 全面重构 UI 界面 |
| V1.5.5 | 初始公开版本 |

---

## 📝 License

内部使用项目
