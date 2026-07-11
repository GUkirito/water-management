# 村级自来水管理系统

> Spring Boot 4.0 + Vue 3 + SQLite + JDK 25 + Tauri 2 全栈桌面应用
> **当前版本：V1.7.6**

---

## 📖 项目简介

村级自来水管理系统是一套面向村/社区级别的小型自来水收费管理软件，支持村民水表信息管理、批量抄表录入、水费收费、**材料费独立管理**、报表导出、月份锁定、账务健康检查、一键备份恢复等功能。可打包为单文件 Windows 桌面应用。

**核心特点：**

- 🪟 **双模式运行**：支持浏览器访问（B/S）和 Tauri 桌面窗口两种模式
- 📦 **单文件部署**：打包为单个 .exe 安装文件，双击安装即用，无需安装 Java 或 Node
- 🗄️ **嵌入式数据库**：SQLite WAL 数据库，备份使用经过完整性校验的一致性快照
- 🌐 **局域网共享**：默认仅本机访问，可通过显式配置开启局域网模式
- 📊 **Excel 导入导出**：支持下载抄表模板 → 线下填写 → 批量导入（含预览确认）
- 🧾 **材料费独立管理**：材料费与水费完全分离，独立导入/收费/统计
- 💰 **水费预存**：支持预存余额充值，自动抵扣水费账单
- 🔒 **月份锁定**：对已结算月份加锁，防止误改历史账单
- 🩺 **账务健康检查**：自动检测异常账务数据，支持手动调整修正
- 🔄 **自动更新**：Tauri 版本支持自动更新
- 💾 **一键备份/恢复**：设置页面下载或恢复数据库文件

---

## 🛠️ 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 4.0.6 |
| 运行环境 | JDK | 25 |
| 持久层 | Spring Data JPA + Hibernate | 7.2.12 |
| 数据库 | SQLite (sqlite-jdbc) | 3.46.1 |
| API 文档 | SpringDoc OpenAPI (Swagger UI) | 3.0.1 |
| Excel 操作 | EasyExcel | 4.0.3 |
| 工具库 | Lombok, Hutool | 5.8.44 |
| 前端框架 | Vue 3 (Composition API) + Vite | 6.3 |
| UI 组件库 | Element Plus | 2.10 |
| HTTP 客户端 | Axios | 1.7 |
| 路由 | Vue Router 4 (History Mode) | 4.5 |
| 桌面壳 | **Tauri 2** (Rust) | 2.x |
| 安装打包 | jlink + jpackage | JDK 25 自带 |
| 运行环境 | Windows 10/11 (64位) | — |

---

## 📁 项目结构

```
water-management/
├── build.bat                   ← 一键打包脚本（JAR → jlink → jpackage）
├── pom.xml                     ← Maven 依赖配置（Spring Boot 4.0.6）
├── mvnw / mvnw.cmd             ← Maven Wrapper（无需安装 Maven）
│
├── frontend/                   ← Vue 3 前端源码
│   ├── package.json
│   ├── vite.config.js          ← Vite 构建配置 + API 代理
│   └── src/
│       ├── main.js             ← 应用入口（Element Plus 注册）
│       ├── App.vue             ← 根组件（暗黑模式、事件监听）
│       ├── router/index.js     ← 6 个页面路由
│       ├── api/index.js        ← Axios 封装 + 全部 API 调用
│       ├── layouts/
│       │   └── MainLayout.vue  ← 主布局（侧边菜单 + 全局搜索）
│       └── views/
│           ├── Dashboard.vue        ← 仪表盘（水费+材料费统计）
│           ├── Readings.vue         ← 抄表录入（模板导出/预览/导入）
│           ├── Billing.vue          ← 水费收费（未缴账单+预存管理）
│           ├── MaterialFee.vue      ← 材料费管理（独立模块）
│           ├── Reports.vue          ← 报表中心
│           └── Settings.vue         ← 系统设置（备份恢复/月份锁定/账务健康）
│
├── src/                        ← Spring Boot 后端源码
│   ├── main/java/com/example/watermanagement/
│   │   ├── WaterManagementApplication.java  ← 启动类（自动创建数据目录）
│   │   ├── config/
│   │   │   ├── SpringDocConfig.java         ← Swagger UI 配置
│   │   │   └── SpaConfig.java              ← SPA 路由回退过滤器
│   │   ├── controller/          ← 7 个 REST 控制器
│   │   ├── dto/                 ← 数据传输对象（含 ApiResponse 统一响应）
│   │   ├── entity/              ← 9 个 JPA 实体
│   │   ├── exception/           ← BusinessException + 全局异常处理
│   │   ├── repository/          ← 9 个 JPA Repository
│   │   ├── service/             ← 业务接口 + impl 实现
│   │   └── util/
│   │       └── ExcelUtil.java   ← EasyExcel 工具类
│   └── main/resources/
│       ├── application.yml      ← 应用配置（SQLite 路径、水价等）
│       └── static/              ← 前端构建产物
│
├── AGENTS.md                   ← AI 编码助手配置
├── CLAUDE.md                   ← Claude Code 配置
└── HELP.md                     ← Spring Boot 帮助文档
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
    │                                └── 缴费 ──→ payments (水费缴费明细，多态)
    │
    ├── 1:N ── prepayment_logs (水费预存流水)
    │
    └── 1:1 ── material_records (材料费记录，独立表)
                      │
                      └── 缴费 ──→ material_payments (材料费缴费明细)

month_locks (月份锁定，独立)
accounting_adjustments (账务调整审计日志，独立)
```

### 数据表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `households` | 村民水表信息 | id, household_name, water_meter_id (unique), village_name, phone, is_active |
| `readings` | 抄表记录 | id, water_meter_id, reading_date, current_reading, previous_reading, usage_amount, is_abnormal |
| `water_bills` | 水费账单 | id, water_meter_id, bill_year, bill_month, water_charge, actual_paid, status (未收/部分收/已收), version |
| `payments` | 水费缴费明细 | id, bill_type, bill_id (多态), amount, paid_date, operator |
| `prepayment_logs` | 水费预存流水 | id, water_meter_id, amount (正→充值 / 负→抵扣), bill_id, remark |
| `material_records` | 材料费记录 | id, water_meter_id (unique), total_fee, actual_paid, status, collector |
| `material_payments` | 材料费缴费明细 | id, record_id, amount, paid_date, collector |
| `month_locks` | 月份锁定 | id, bill_year, bill_month, operator (唯一键: 年月) |
| `accounting_adjustments` | 账务调整审计 | id, target_type, target_id, before_amount, after_amount, reason |

---

## 🔌 API 接口

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **村民管理** | GET | `/api/households/list` | 分页列表，支持村名筛选 + 水表编号模糊搜索 |
| | POST | `/api/households/add` | 新增村民 |
| | PUT | `/api/households/update/{id}` | 更新村民信息 |
| | DELETE | `/api/households/delete/{id}` | 无历史数据时物理删除，否则停用归档 |
| | POST | `/api/households/batch-delete` | 批量删除或停用（仅本机） |
| | POST | `/api/households/delete-by-village` | 按村组删除或停用（仅本机） |
| | POST | `/api/households/batch-update-village` | 批量修改村组 |
| | GET | `/api/households/export` | 导出现有村民 Excel |
| | POST | `/api/households/import` | Excel 批量导入村民 |
| | POST | `/api/households/import-from-register` | 从水费登记册导入 |
| **抄表管理** | GET | `/api/readings/export-template` | 导出当期空白抄表模板 |
| | GET | `/api/readings/history-template` | 导出历史抄表导入模板 |
| | POST | `/api/readings/import/preview` | 预览导入结果（不写库） |
| | POST | `/api/readings/import` | 导入当期抄表 Excel |
| | POST | `/api/readings/history-import/preview` | 预览历史导入结果 |
| | POST | `/api/readings/history-import` | 导入历史抄表数据 |
| | POST | `/api/readings/batch` | 批量保存表底 |
| | POST | `/api/readings/single` | 单条录入表底 |
| | GET | `/api/readings/by-date` | 按日期+村组查抄表记录 |
| | GET | `/api/readings/by-month` | 按年月查询抄表记录 |
| | GET | `/api/readings/abnormal` | 异常抄表列表 |
| **水费收费** | GET | `/api/payments/pending-water` | 按户查未缴清水费 |
| | GET | `/api/payments/pending-water-list` | 全村未缴清单（含筛选） |
| | GET | `/api/payments/all-water-bills` | 全量水费账单 |
| | POST | `/api/payments/pay` | 缴费（支持合并多月） |
| | GET | `/api/payments/history` | 缴费历史 |
| | GET | `/api/payments/water-prepayment-balance` | 查预存余额 |
| | GET | `/api/payments/water-prepayment-logs` | 查预存流水 |
| **材料费管理** | GET | `/api/material-records/list` | 分页列表（按村/状态/日期筛选） |
| | POST | `/api/material-records` | 新增材料费记录 |
| | PUT | `/api/material-records/{id}` | 更新 |
| | DELETE | `/api/material-records/{id}` | 删除无缴费/调账历史的记录（仅本机） |
| | POST | `/api/material-records/batch-delete` | 批量删除无历史记录（仅本机） |
| | POST | `/api/material-records/import` | 从 Excel 导入 |
| | GET | `/api/material-records/export` | 导出 Excel |
| | POST | `/api/material-records/{id}/collect` | 收取材料费 |
| | GET | `/api/material-records/{id}/payments` | 缴费历史 |
| **账务管理** | GET | `/api/accounting/health-check` | 账务健康检查 |
| | GET | `/api/accounting/month-locks` | 查已锁定月份 |
| | POST | `/api/accounting/month-locks` | 锁定月份 |
| | DELETE | `/api/accounting/month-locks` | 解锁月份 |
| | POST | `/api/accounting/adjustments/water-bills/{id}` | 调整水费账单 |
| | POST | `/api/accounting/adjustments/material-records/{id}` | 调整材料费 |
| **报表中心** | GET | `/api/reports/water-bill` | 水费月报表数据 |
| | GET | `/api/reports/water-bill/export` | 导出水费月报表 Excel |
| **系统设置** | GET | `/api/settings/info` | 系统信息 |
| | GET | `/api/settings/backup/download` | 下载数据库备份 |
| | POST | `/api/settings/backup/restore` | 校验并暂存恢复数据库（仅本机） |

> API 文档页面（开发模式）：`http://localhost:8080/swagger-ui.html`

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
    → 生成 water_bills (应收水费 = 用量 × 水价，状态=未收)
→ 返回结果：成功N条，异常M条
```

### 2. 历史抄表导入

用于补录历史数据或迁移旧系统数据，不影响当前账期逻辑。

```
[下载历史导入模板] → 填写历史年月+表底数据
→ 预览导入结果 → 确认后写入
```

### 3. 水费缴费流程

```
[选择户/或从未缴账单列表直接点收款]
→ 展示所有未缴清水费账单（可多选）
→ 查询该户预存余额
→ 勾选多月 → 自动计算总应收
→ 输入实收金额 → 显示找零
→ 确认支付
→ 后端按欠费比例分配金额到每个账单
→ 更新 bills 的 actual_paid 和状态 (未收/部分收/已收)
→ 如支付有超出，自动充入预存款
```

### 4. 水费预存

```
→ 收款员收取预存款 → 记录 prepayment_log（正数充值）
→ 缴费时预存余额可抵扣水费
→ 抵扣后记录 prepayment_log（负数扣减，关联 bill_id）
→ Dashboard/收费页实时显示余额
```

### 5. 月份锁定与账务健康检查

```
→ 对已完成收费的月份加锁 → 锁定后该月账单不允许修改
→ 账务健康检查自动扫描：金额不一致账单、未锁定的已结算月份、负余额预存等
→ 以问题列表展示，支持手动调整修正（记录调整前后金额+原因）
```

### 6. 材料费管理（独立模块）

```
材料费与水费完全分离，独立表、独立收费、独立统计：
→ 导入 材料费.xlsx 一键生成所有户记录
→ 按村组筛选 → 点击 [💰] 收费（支持分多次缴清）
→ Dashboard 展示材料费应收/实收/收缴率
```

### 7. 异常检测

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
# 终端 1：启动后端（端口 8080）
./mvnw spring-boot:run

# 终端 2：启动前端（端口 3000，热更新，/api → 代理到 8080）
cd frontend
npm install
npm run dev
```

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`
- API 文档：`http://localhost:8080/swagger-ui.html`

### 运行测试

```bash
./mvnw test
```

### 生产部署

#### 方式一：JAR 手动部署

```bash
cd frontend && npm run build && cd ..      # 构建前端
./mvnw package -DskipTests                  # 构建后端 fat JAR
java -jar target/water-management-0.0.1-SNAPSHOT.jar  # 运行
```

#### 方式二：单文件 EXE 安装包（推荐）

```bash
# 双击运行，或在项目根目录执行
build.bat

# 产物在 installer/VillageWaterManagement-1.7.6.exe
# 发给用户，双击安装即可，无需安装 Java 或任何依赖
```

---

## ⚙️ 主要配置

`application.yml` 中的关键配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:${user.home}/.water-management/data/water_meter.db
  jpa:
    hibernate:
      ddl-auto: update          # 自动建表/迁移

water:
  price: 1.8                     # 水价（元/吨）
  abnormal:
    threshold: 100               # 异常用量阈值（吨）
```

> 水价和异常阈值可在 **Settings** 页面中修改，保存后立即生效，无需重启。

后端默认只监听 `127.0.0.1`。确需局域网共享时显式设置：

```powershell
$env:WATER_SERVER_ADDRESS = "0.0.0.0"
.\mvnw.cmd spring-boot:run
```

局域网模式下，备份恢复、批量删除、月份解锁和调账仍只允许从运行后端的本机调用。Tauri 桌面模式始终绑定 `127.0.0.1`。

---

## 📦 打包原理

```
[npm run build]    → Vue 前端 → static/
[mvnw package]     → Spring Boot → fat JAR (~60MB)
[jdeps]            → 分析 Java 模块依赖
[jlink]            → 裁剪 JRE (~50MB)
[jpackage]         → 生成单文件安装包 (~125MB)
```

用户安装包自带裁剪版 JDK 25，无需预装任何 Java 环境。

> **注意**：`jpackage --type exe` 需要安装 WiX Toolset v3.14。未安装时 `build.bat` 自动回退为便携版 `app-image`。

---

## 💾 数据备份与恢复

- **备份**：进入系统设置并下载备份。系统使用 SQLite `VACUUM INTO` 创建一致性快照，校验文件头、必要表和 `PRAGMA integrity_check` 后才提供下载。
- **桌面恢复**：上传备份后，后端先校验并创建恢复前回滚快照；Tauri 停止后端、替换数据库、清理 WAL/SHM、重启并执行健康检查，失败时自动回滚。
- **Web/JAR 恢复**：只允许上传、校验和暂存，不会在连接池仍运行时在线覆盖数据库；必须先停止应用再执行外部恢复。

数据文件位置：`%USERPROFILE%\.water-management\data\water_meter.db`

**建议频率：** 每月备份一次，保留最近 12 个月的备份文件。

## 🔒 月结与历史数据保护

- 同一水表每个自然月只允许一条抄表；同日可更新，同月不同日和存在后续抄表时拒绝写入。
- 已收、部分收或存在缴费、预存抵扣、调账流水的账单不能通过重录表底静默改价。
- 锁定月份覆盖单条、批量和两类 Excel 导入；解锁必须填写操作人和原因，解锁信息保留在月份锁记录中。
- 没有抄表或财务历史的住户可以物理删除；存在历史数据时只停用归档，报表仍保留其历史账期。

---

## 📝 更新日志

| 版本 | 主要变更 |
|------|---------|
| V1.7.6 | 强化 SQLite 备份恢复、抄表与月锁一致性、历史账务保护及统计口径 |
| V1.7.5 | 引入 Tauri 2 桌面壳（原生窗口/快捷键/单实例/自动更新） |
| V1.6.8 | 历史抄表批量导入、修复已知问题 |
| V1.6.7 | UI 全面改进、修复多处已知问题 |
| V1.6.4 | 缴费管理新增未缴水费账单列表，支持直接从列表收款 |
| V1.6.3 | 新增水费预存充值/抵扣逻辑 |
| V1.6.1 | 报表中心水费/材料费双套完全隔离 |
| V1.6.0 | 全面重构 UI 界面 |
| V1.5.5 | 初始公开版本 |

---

## 📝 License

内部使用项目
