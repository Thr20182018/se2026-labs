# Repository Guidelines

## 项目结构与依据

在本仓库根目录执行 Git 命令。先阅读 [PROJECT.md](PROJECT.md) 和 [PROGRESS.md](PROGRESS.md)，按当前任务范围工作。

- `requirements.md`、`chat.txt`：需求基线与访谈记录，保留需求编号和访谈原文。
- `UML/`、`UI界面/`：四张 UML 及九张正式 UI，作为结构和页面依据。
- `code/`：实验三 Java 工程，按 `identity`、`activity`、`registration`、`attendance`、`statistics`、`audit` 分模块；T1 建立此目录。
- `code/src/main/resources/`：Thymeleaf 模板、静态资源和数据库脚本；`code/src/test/` 保存测试。

实验三新增决策以 PROJECT.md 的决策记录为准。

## 技术与风格

使用 Java 21、Spring Boot、Spring Security、Thymeleaf、MySQL 8.4 和 JdbcTemplate。Maven 管理构建，Java 遵循 Google Java Style，以 Checkstyle 检查。文档使用简洁中文和 UTF-8。

Controller 处理请求，Service 组织业务与事务，Repository 保存 SQL。按业务职责命名，使用 `Controller`、`Service`、`Repository` 后缀。注释说明业务原因与事务边界。PlantUML 保留两空格缩进及现有编号文件名。

## 构建与验证

T1 建立 Maven Wrapper 后，在 `code/` 执行：

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

云端 Linux 使用 `./mvnw verify` 和 `./mvnw spring-boot:run`。`verify` 应运行测试及风格检查。

JUnit 测试按功能命名，类名以 `Test` 结尾。事务、SQL 和并发用真实 MySQL 独立测试库验证；验收场景见 PROJECT.md。交付时提供实际检查结果、启动方法和必要的界面截图。

## 分支、提交与 PR

每轮任务从最新 `main` 创建独立分支，例如 `task/t1-foundation`，对应一个 PR。Cursor 实现，本助手审查并修复；已启动任务允许在任务分支提交和推送，合并 `main` 由用户确认。后续任务从合并后的 `main` 开始。

按完整步骤提交，标题简短说明动作，例如“实现活动组合查询”。显式暂存路径，检查 `git diff --cached` 和 `git diff --cached --check`。PR 描述任务范围、相关需求和验证结果。采用 Squash and merge，使主分支每轮任务对应一个提交。

## 任务预算与记录

每轮新增及改写的手写非空行最多 2000 行，包含注释、页面、SQL、配置和测试；工具生成文件单列。每轮使用一个云 Agent 会话，按需读取文件和输出测试摘要，为审查修复预留空间。

PROGRESS.md 简短记录结果、待办及下一步。计划变化写入 PROJECT.md。临时日志和详细测试输出保存在本地或任务附件中。

## 数据与材料

示例身份使用林知远、`lin_zy` 等虚构信息。凭据通过环境变量或被忽略的本地配置提供。提交前检查暂存内容中的个人信息与秘密。保留既有实验标签，阶段验收后标记 `lab3-m1` 等版本，最终交付标记 `lab3-final`。实验三报告由学生自行撰写，开发任务保存运行与验证事实。
