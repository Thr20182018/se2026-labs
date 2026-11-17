# 构建与运行

在 `code/` 下执行，使用 JDK 21 和 MySQL 8.4。创建 `workshop_dev`、`workshop_test` 两个库及各自专用账号，分别授权。

设置环境变量：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 指向开发库；`TEST_DB_URL`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD` 指向测试库。URL 示例：`jdbc:mysql://127.0.0.1:3306/workshop_dev?connectionTimeZone=Asia/Shanghai`，测试库替换库名。PowerShell 使用 `$env:变量名 = '值'`，Linux 使用 `export 变量名='值'`。

首次在空开发库执行建表和演示数据脚本。下列命令打开 MySQL 客户端，端口按实际配置调整：

```bash
mysql --host=127.0.0.1 --port=3306 --user=workshop_dev --password workshop_dev
```

在 MySQL 客户端内按顺序执行一次：

```sql
SOURCE src/main/resources/db/V001__schema.sql;
SOURCE src/main/resources/db/V002__demo.sql;
SOURCE src/main/resources/db/V003__review_version.sql;
SOURCE src/main/resources/db/V004__session_review_version.sql;
SOURCE src/main/resources/db/V005__cancelled_session_visibility.sql;
exit;
```

演示账号见 `V002__demo.sql`，密码都是 `Demo-2026`。库里只保存 BCrypt 哈希。

已有 T2-A 数据库升级时，执行一次 `V004__session_review_version.sql`，为场次添加独立审核版本。T1 数据库先执行 `V003__review_version.sql`，再执行 V004。升级脚本保留现有数据。

T4＋T5 启动前执行一次 V005，记录场次取消前是否已发布，保留已有取消场次的公开展示。未发布的场次取消后仍不对参与者展示。

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Linux 将 `.\mvnw.cmd` 换成 `./mvnw`。访问 http://localhost:8080。`verify` 运行 JUnit 和 Checkstyle，测试只在校验通过的 `workshop_test` 库重建表。

查询页以行展示活动名称、时间、地点和报名状态。可报名和可候补的场次优先，已截止和已取消的排在后面，各组按开始时间排序。点击场次原地展开详情，再次点击收起，切换场次时自动关闭上一项；支持 Enter、空格和 Esc 操作。校徽沿用实验二资源，来源：[南京大学标识](https://www.nju.edu.cn/ndgk/ndbs.htm)。

组织者 `su_qing` 在“活动管理”创建草稿、添加多个场次并提交审核；管理员 `he_qm` 在“审核管理”通过或驳回。通过后，参与者 `lin_zy` 可在查询页看到场次；驳回后，组织者可以修改并重新提交。三个演示账号均使用上述演示密码。

已发布活动可以提交名称和简介的修改申请，审核期间继续展示原内容，通过后替换。组织者也可以新增场次并单独提交审核，通过后该场次发布。管理员在同一待审核列表处理首次发布、内容修改和新增场次三类申请。

组织者的“我的活动”同时展示活动状态、最近一次内容修改结果，以及新增场次各审核状态的数量；未提交过的类别隐藏。内容修改被驳回后，详情页持续显示反馈，重新提交后更新为待审核。

阅读发布流程可从 `ActivityManagementController` → `ActivityManagementService` / `PublishedActivityService` → `ActivityManagementRepository` 开始，日志事务见 `audit/`。对应测试为 `ActivityPublishingHttpTest` 和 `PublishedActivityHttpTest`。

参与者在场次详情中报名；名额已满或已有候补者时，可主动加入候补。提交结果原地显示，“我的报名”列出本人的记录和状态，候补序号仅在后台用于排序。演示参与者为 `lin_zy`、`chen_an`、`wang_he` 和 `xu_lin`，密码同上。

报名按服务器接收请求的时间判断截止。截止前已收到的请求完成后，后台每 5 秒检查并将剩余候补转为“候补未成功”。T3 沿用现有表结构，无需新增数据库升级脚本。

阅读报名流程可从 `RegistrationRequestFilter` → `RegistrationController` → `RegistrationService` → `RegistrationRepository` 开始；`RegistrationAdmission` 登记在途请求，`RegistrationDeadlineJob` 处理截止。对应测试为 `RegistrationHttpTest` 和 `RegistrationDeadlineTest`。

参与者在“我的报名”取消报名或退出候补；截止前重新申请会生成新记录，重新候补排到队尾。取消成功报名时立即递补首位候补者。组织者在活动详情展开场次，调整容量后按顺序递补新增名额，容量下限为当前报名人数。取消、退出和容量调整也按服务器接收时间判断截止，截止任务等待已接收的请求完成。

组织者在场次详情进入“查看名单与签到”，为报名成功者签到；重复签到保留首次时间。后台每 5 秒检查结束场次，将仍未签到的报名转为缺席。组织者和管理员可取消单个场次或整个活动，已结束场次保留，尚未结束场次的有效报名和已签到记录一起取消。管理员从“活动管理”查看所有活动并执行取消。

取消递补和容量调整见 `RegistrationService`；活动取消见 `ActivityLifecycleService`；签到与缺席见 `attendance/`。对应测试为 `CancellationPromotionHttpTest`、`ActivityLifecycleHttpTest`、`AttendanceLifecycleHttpTest`，截止边界在 `RegistrationDeadlineTest`。业务变更与成功日志在同一事务中提交，故障回滚后另记失败日志。
