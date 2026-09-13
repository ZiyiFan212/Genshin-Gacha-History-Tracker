# 抓包导入 — 实现说明（预留）

本文件仅作开发备注，不含可执行代码。具体 HTTP 请求与缓存读取逻辑由维护者参照现有开源抓包工具自行实现。

## 背景

原神抽卡历史通过游戏客户端向米哈游 API 发起 HTTP 请求获取，响应体为 JSON 字符串。
常见桌面抓包工具的做法并非被动监听 socket，而是：

1. 从本地缓存（如 `webCaches` / `Cookies` / 工具自维护的 token 文件）读取鉴权信息；
2. 按分页参数**手动循环**构造 GET 请求；
3. 将每次响应的字符串拼接或归并后，再解析为 UIGF 或官方 record 列表。

## 建议接入点

- **UI**：`ui/screens/CaptureScreen.kt` — 侧边栏「抓包」页，展示说明与操作按钮。
- **业务**：新建实现类（例如 `core/GachaHttpFetcher.kt`），不要复用已删除的 Provider/Manager 单例模式。
- **入库**：解析完成后走与 JSON 导入相同的路径：
  - `parseJson` / 自定义解析 → `DataValidator.validate`
  - `mergeWith` → `calculateStat` → `IOManager.upsertingInSQL`
  - 成功后刷新 `AppViewModel` 中当前 UID 数据（参考 `importFromPath`）。

## HTTP 循环要点（注释）

- 请求需携带有效 `game_token` / `authkey` 等，来源以你所用工具的 cache 读取方式为准。
- `end_id` / `page` / `size` 等分页字段需循环直到返回空列表或 `data.list` 不再增长。
- 每次响应为**字符串**，需自行 `decode`；注意限速与失败重试，避免封号风险。
- 合并时注意 record `id` 字段顺序（见 `GachaRecord.compareChronologically`），不要仅按 `time` 排序。

## 参考

请对照社区现有 UIGF 导出器、Enka/Mihomo 类工具的抓包实现，本仓库不内置具体 URL 与请求头。
