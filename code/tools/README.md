# REQ-018 性能验收

`performance_acceptance.py` 仅使用 Python 3.10+ 标准库。真实查询入口为 `GET /workshops`，每页 8 场次。验收条件为：独立库 1 万场次、20 名独立登录用户、100 次组合查询，至少 95 次在 2 秒内返回完整且正确的结果。

## 准备数据

先按工程启动说明创建专用空库 `workshop_acceptance`，依次执行 V001、V003、V004、V005；不导入 V002。验收实例必须连接该库，开发库和测试库保持独立。

在 `code/` 下生成临时材料（脚本不会连接或修改数据库）：

```powershell
python tools/performance_acceptance.py seed --output ../../../tmp/performance-fixture
```

生成 `acceptance-seed.sql` 与 `fixture.json`。SQL 明确使用 `workshop_acceptance`，存储过程再次核对库名及五张业务表为空，随后在一个事务中插入；不会删除旧数据。由数据库客户端导入 SQL 后核对：users 21、activities 1000、sessions 10000、registrations 16000。导入账号需临时具备创建、执行、删除存储过程权限；应用账号仅需正常业务权限。

20 个虚构参与者账号为 `perf_01` 至 `perf_20`，统一使用工程公开演示密码 `Demo-2026`。另有一个组织者账号。场次含可报名、满额候补、零容量候补、已截止和已取消；预期由 fixture 声明的数据在 Python 中独立计算，不使用被测查询返回值构造预期。

生成日期起 14 天内状态稳定；过期后应重新准备空验收库并生成材料。脚本不会重置现有数据。生成材料存放仓库外，保留本轮 `fixture.json` 供重现；公开运行证据的 environment.json 同时保存该 manifest 及数据/SQL 哈希。

## 运行

启动连接验收库的应用，例如本机端口 18084。单独准备 UTF-8 JSON 环境文件：

```json
{
  "java_version": "填写实际 java -version 输出中的版本",
  "mysql_version": "填写实际 SELECT VERSION() 结果",
  "browser_version": "填写本次页面验收的实际浏览器版本",
  "app_revision": "填写本次被测代码的 Git 提交或工作区说明"
}
```

OS、CPU、逻辑核数、RAM、Python 版本自动记录；必要时可在 JSON 中用 `cpu`、`ram_bytes` 覆盖机器检测值。该文件只需要上述字段，不放数据库密码、Cookie 或个人信息。

```powershell
$env:BASE_URL = 'http://127.0.0.1:18084'
python tools/performance_acceptance.py run `
  --fixture ../../../tmp/performance-fixture/fixture.json `
  --metadata ../../../tmp/performance-environment.json `
  --output "../../tmp/performance-run-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
```

只接受本机 HTTP 地址，禁用代理。HTTP 客户端为 Python urllib；浏览器版本用于记录同机页面验收环境，计时不包含浏览器渲染或 CSS/JS 下载。每名用户拥有独立 CookieJar，解析登录页 CSRF token 并核对登录后的姓名。登录请求不计时，随后单独保存 20 次预热查询。正式阶段每轮用 Barrier 同时释放 20 个线程，共 5 轮、100 次，计时从发起请求直到完整 HTML 接收完成；预期计算和 HTML 正确性核对不计入耗时。

查询覆盖名称、含首尾日期的范围、三种可筛选状态及分页组合，同时检查无状态筛选下的取消场次、日期单点、百分号/下划线字面量和超大页码归到末页。核对总数、页码、总页数、场次 ID 顺序、名称、时间、地点、状态、简介、截止与容量/剩余名额。

## 输出与判定

- `requests.csv`：全部 100 次计时结果，含查询、耗时、HTTP 状态、响应字节数、正确性、预期/实际总数及 ID/状态、完整投影 SHA-256 与失败原因。
- `warmup.csv`：独立的 20 次预热结果，不混入正式分母。
- `environment.json`：数据规模和哈希、机器和软件版本、网络、并发与计时方式。
- `summary.json`：登录结果、预热正确性、合格数、错误数、超时/超 2 秒数及耗时分布。

失败、超时、错误内容均保留在 100 次分母内；不重试或丢弃异常请求。默认网络超时 10 秒，可用 `--timeout` 调整，验收的 2 秒阈值不变。20 人登录和预热正确性均通过、正式记录恰好 100 条且至少 95 条正确并在 2 秒内，退出码才为 0；否则为 1。每次使用新的输出目录，保留先前失败证据。
