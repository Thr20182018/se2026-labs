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
exit;
```

演示账号见 `V002__demo.sql`，密码都是 `Demo-2026`。库里只保存 BCrypt 哈希。

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Linux 将 `.\mvnw.cmd` 换成 `./mvnw`。访问 http://localhost:8080。`verify` 运行 JUnit 和 Checkstyle，测试只在校验通过的 `workshop_test` 库重建表。

查询页以行展示活动名称、时间、地点和报名状态。点击场次原地展开详情，再次点击收起，切换场次时自动关闭上一项；支持 Enter、空格和 Esc 操作。校徽沿用实验二资源，来源：[南京大学标识](https://www.nju.edu.cn/ndgk/ndbs.htm)。

组织者 `su_qing` 在“活动管理”创建草稿、添加多个场次并提交审核；管理员 `he_qm` 在“审核管理”通过或驳回。通过后，参与者 `lin_zy` 可在查询页看到场次；驳回后，组织者可以修改并重新提交。三个演示账号均使用上述演示密码。

阅读发布流程可从 `ActivityManagementController` → `ActivityManagementService` → `ActivityManagementRepository` 开始，日志事务见 `audit/`，完整回归场景见 `ActivityPublishingHttpTest`。
