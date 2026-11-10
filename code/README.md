# 构建与运行

在 `code/` 下执行。先加载仓库外的环境变量，再构建或启动：

```bash
source ~/.config/se2026-labs/cloud.env
```

`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 指向开发库。`TEST_DB_URL`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD` 指向测试库。连接串使用北京时间，例如 `serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai`。口令只放在环境变量里。

初始化开发库会删除并重建业务表，然后写入虚构演示数据：

```bash
mysql --host=127.0.0.1 --port=13306 --user="$SPRING_DATASOURCE_USERNAME" \
  --password="$SPRING_DATASOURCE_PASSWORD" workshop_dev \
  < src/main/resources/db/V001__schema.sql
mysql --host=127.0.0.1 --port=13306 --user="$SPRING_DATASOURCE_USERNAME" \
  --password="$SPRING_DATASOURCE_PASSWORD" workshop_dev \
  < src/main/resources/db/V002__demo.sql
```

演示账号见 `V002__demo.sql`，密码都是 `Demo-2026`。库里只保存 BCrypt 哈希。

```bash
./mvnw verify
./mvnw spring-boot:run
```

`verify` 运行 JUnit 和 Checkstyle。测试会先确认当前库是 `workshop_test`，再重建表。
