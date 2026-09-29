# 03 · 数据架构规范（ViewPhone / 微光机）

> 版本 v1.0 ｜ 适用：`core:*`(KMP) / Android(Compose+Room) / Web(TS+React)
> **唯一最高目标：数据结构永不因体积而崩溃。** 任何与本规范冲突的"临时方案"都必须先改本文件。
> 本项目不迁移任何旧版数据，schema 从 v1 干净起步。

已确认的技术选型：结构化数据 Android 用 Room(SQLite)，Web 用 IndexedDB；二进制一律外置文件系统；跨端由同一份 `core/schema` 生成两侧实体。

---

## 1. 存储分层契约

| 层 | 放什么 | 放哪里（Android / Web） | 为什么 | 违反会怎样 |
|---|---|---|---|---|
| A 结构化 | 实体、关系、索引、账务、任务状态 | Room DB / IndexedDB | 需要事务、索引、游标分页 | — |
| B 二进制 | 图片、语音、书籍正文分片、附件 | `context.filesDir/media/` / OPFS | 文件系统天然分块、可流式、可单删 | 库体积暴涨 |
| C 轻量设置 | ≤200 项、单值 <8KB 的开关与偏好 | DataStore(Proto) / localStorage(前缀 `vp.`) | 读写频次高、无查询需求 | 设置污染主库 |
| D 临时状态 | 页面草稿、滚动位置、UI 展开态 | ViewModel + `SavedState` / sessionStorage | 无持久化价值 | 崩溃后脏数据 |
| E 导出产物 | 备份 ZIP、导出 JSON | `cacheDir/export/` / OPFS `export/` | 可丢弃、需流式写 | 撑爆主存储 |

**红线**
- A 层任何字段**不得**存放 base64 / data URL / 原始字节。B 层是二进制的唯一归宿。
- C 层只允许"标量设置"，禁止存业务列表、聊天记录、图片。键总数上限 200，超限 CI 失败。
- D 层禁止承载超过 1MB 的 payload。
- E 层产物生成后必须可被"一键清理"，且不得被主库引用。

> 旧版事故：全库存在 5 套不同源的持久化（Dexie 主库 + 私有音乐库 + 127 个 localStorage 键 + 原生沙盒），备份只覆盖其中 1 套，导致"数据在哪"无人能答。

---

## 2. 二进制外置规范

### 2.1 目录树与命名

采用**内容寻址**（SHA-256），不采用 UUID：

```
media/
  img/<sha[0:2]>/<sha[0:32]>_orig.webp     # 原图
  img/<sha[0:2]>/<sha[0:32]>_thumb.webp    # 列表缩略图
  img/<sha[0:2]>/<sha[0:32]>_cover.webp    # 卡片封面
  audio/<sha[0:2]>/<sha[0:32]>.opus
  book/<bookId>/<chapterIdx:05d>.txt        # 正文分片
  tmp/                                       # 导入临时区，禁止被引用
```

**为什么内容寻址**：同一张图被收藏室/消息/桌面壁纸多次引用时，物理上只有一份；导入时先算 hash，命中即复用，天然去重；孤儿文件判定 = "磁盘有、引用表无"，可安全回收。
**为什么加两级散列目录**：单目录文件数 >5000 时 ext4/APFS 的目录项查找与 `readdir` 明显退化；二级散列把单目录控制在数百个。
**为什么不用 UUID**：UUID 每次写入都产生新文件，去重、引用计数、孤儿回收全部失效。

> 旧版事故：生图结果的 thumb + hd 双份 base64 写进同一条消息的两个字段，收藏室再存一份 → 同一张图 3 份拷贝全部进库。

### 2.2 引用格式

数据库里只存相对路径字符串：`media://img/ab/abcdef..._thumb.webp`。
解析规则：`media://` → 平台媒体根（Android `filesDir/media`，Web OPFS `media`）。禁止存绝对路径、`file://`、`content://`。

### 2.3 图片衍生规格（三档，仅此三档）

| 档 | 长边 | 格式/质量 | 用途 |
|---|---|---|---|
| `_orig` | 不压缩（仅去元数据） | 原格式；>2MB 转 WebP q=88 | 全屏查看、导出 |
| `_thumb` | 256px | WebP q=72 | 列表、头像、气泡 |
| `_cover` | 1024px | WebP q=80 | 卡片、详情头图 |

HEIF 仅作为**输入**接受（相机/相册），落盘一律转 WebP——Web 端 Safari 之外的解码兼容性与体积都更优。单文件上限 64MB，超限在导入阶段直接拒绝并报错。

### 2.4 语音与书籍正文

- 语音：单条上限 10 分钟，Opus 64kbps；同时写 `duration_ms` 到库，列表不读文件即可渲染时长。
- 书籍：**按章切分**，一章一分片，单分片上限 256KB；超长章节按 256KB 二次切分并记录 `partIndex`。
- 流式读取：`readChapter(bookId, idx): Flow<String>` 逐块 32KB 读取；**禁止**一次性 `readText()` 整本。章节切分在**导入时**完成并落库 `chapterIndex/title/charCount`，**禁止**运行时用正则切分正文。

> 旧版事故：书籍正文整章塞进一个字段、章节切分靠正则、TXT 编码探测实为 UTF-8 vs GBK 二选一，导致首屏解析卡 1~3 秒。

### 2.5 导入顺序（严格不可调换）

1. 流式写入 `media/tmp/<uuid>`；
2. 计算 SHA-256 并校验（与来源声明不一致 → 拒绝）；
3. 原子 `rename` 到内容寻址终址（同分区 rename 是原子操作）；
4. 写入 `media_object` 行 + 业务实体行，**同一个事务**；
5. 事务提交后清理 tmp。

**只允许"文件多、库少"（孤儿），绝不允许"库多、文件少"（悬空引用）。**

---

## 3. 写入守卫（Guard）

### 3.1 拦截点：真实仓储调用点

守卫必须内联在**生产仓储实现**里，而不是测试代码里：

```kotlin
interface MessageRepository { suspend fun insert(m: MessageEntity): Long }

class RoomMessageRepository(private val db: AppDb) : MessageRepository {
    override suspend fun insert(m: MessageEntity): Long {
        StorageGuard.assertStorable(m)          // 每次写都走，包括 release
        return db.messageDao().insert(m)
    }
}
```

Room 侧再加一层数据库级保险：所有含大字段的实体实现 `BinaryFree` 标记接口，配对 `@TypeConverter` 只接受 `String` 且拒绝超长；未实现接口的实体不得进入 `MessageDao`。

**为什么"只在测试里调用守卫"等于没做**：测试只覆盖测试构造的数据路径；真实崩溃来自生产调用点（导入、生图回调、小程序沙箱写回）。守卫不在调用点，就等于没有守卫——而 CI 只会因为"守卫压根不在生产代码里"而永远通过。

> 旧版事故：为绕开"无索引导致的 SchemaError"，作者把"不改 schema、全量 `toArray()` 拉内存再 `filter()`"立为规范，正是因为没有调用点级约束。

### 3.2 检测规则

1. 字符串字段匹配 `^data:[a-z/+.-]+;base64,`、或长度 > 64KB 且 base64 字符集占比 >95%；
2. 出现 `byte[]`、`ByteArray`、`Uint8Array`、`Blob`、`Bitmap` 类型字段声明（源码扫描）；
3. 字段名命中 `*Base64`、`*DataUrl`、`*Blob`、`*Bytes`；
4. 深度上限：递归检查至 8 层，遇到 `Collection`/`Map` 最多遍历前 64 个元素做抽样——守卫不得成为性能瓶颈，但任何一层的命中都必须抛错。

抛 `IllegalStateException(code=STORAGE_GUARD_BASE64, path=...)`，**不吞异常**。

### 3.3 CI 负向测试

- **源码扫描**（Gradle task `storageGuardScan`）：`*/repository/**` 与 `*/dao/**` 下出现 `Base64`、`toByteArray()` 直接构建，构建失败。
- **负向单测**：合法 fixture + 4 个违规 fixture（base64 图、data URL、`ByteArray` 字段、1MB 纯文本），断言逐个抛错**且数据库零写入**（`SELECT COUNT(*)` 前后不变）。
- 违规 fixture 本身入库为测试资源，保证扫描器有真阳性样本。

---

## 4. Room 数据模型

### 4.1 实体清单

```kotlin
@Entity(tableName = "character",
  indices = [Index("deletedAt"), Index(value=["name"], unique=false)])
data class CharacterEntity(
  @PrimaryKey val id: String,           // UUIDv7
  val name: String, val avatarMediaId: String?,   // 引用 media_object，不存字节
  val persona: String,                  // ≤8KB
  val createdAt: Long, val updatedAt: Long, val deletedAt: Long?
)

@Entity(tableName = "user_mask",
  indices = [Index(value=["characterId","isActive"])])
data class UserMaskEntity(
  @PrimaryKey val id: String, val characterId: String,
  val displayName: String, val bio: String, val isActive: Boolean
)

@Entity(tableName = "session",
  indices = [Index(value=["characterId","lastMessageAt"])],
  foreignKeys = [ForeignKey(CharacterEntity::class, ["id"], ["characterId"], onDelete = CASCADE)])
data class SessionEntity(
  @PrimaryKey val id: String, val characterId: String, val title: String,
  val lastMessageAt: Long, val messageCount: Int,   // 冗余计数，避免 COUNT(*) 全表
  val createdAt: Long, val deletedAt: Long?
)

@Entity(tableName = "message",
  indices = [
    Index(value=["sessionId","seq"]),                        // 游标分页主索引
    Index(value=["sessionId","createdAt","id"]),             // 备用时间轴
    Index(value=["sessionId","role","createdAt"]),           // 按角色筛选
    Index(value=["status","createdAt"])                      // 待发送/失败重试扫描
  ],
  foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = CASCADE)])
data class MessageEntity(
  @PrimaryKey val seq: Long,            // 全局单调自增，游标用它
  val id: String,                       // UUIDv7，对外稳定 id
  val sessionId: String, val role: String,
  val content: String,                  // 纯文本，≤64KB；更长内容转附件分片
  val contentType: String,              // text | media | book_ref | card
  val createdAt: Long, val status: Int, val deletedAt: Long?
)

@Entity(tableName = "message_attachment",
  indices = [Index(value=["messageId","ordinal"], unique=true), Index("mediaId")],
  foreignKeys = [ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = CASCADE)])
data class MessageAttachmentEntity(
  @PrimaryKey val id: String, val messageId: String,
  val mediaId: String,                  // → media_object.sha256
  val kind: String,                     // image_thumb | image_orig | audio | book_part
  val ordinal: Int
)

@Entity(tableName = "media_object",
  indices = [Index(value=["kind","createdAt"]), Index("refCount"), Index(value=["sha256"], unique=true)])
data class MediaObjectEntity(
  @PrimaryKey val sha256: String, val kind: String, val ext: String,
  val bytes: Long, val width: Int?, val height: Int?, val durationMs: Int?,
  val refCount: Int, val createdAt: Long
)

@Entity(tableName = "world_book", indices = [Index("updatedAt")])
data class WorldBookEntity(@PrimaryKey val id: String, val name: String,
  val updatedAt: Long, val deletedAt: Long?)

@Entity(tableName = "world_book_entry",
  indices = [Index(value=["bookId","priority"]), Index(value=["bookId","enabled"])])
data class WorldBookEntryEntity(@PrimaryKey val id: String, val bookId: String,
  val keywords: String, val content: String, val priority: Int, val enabled: Boolean)

@Entity(tableName = "memory_summary",
  indices = [Index(value=["sessionId","upToSeq"])])
data class MemorySummaryEntity(@PrimaryKey val id: String, val sessionId: String,
  val upToSeq: Long, val summary: String, val tokens: Int, val createdAt: Long)

@Entity(tableName = "embedding_record",
  indices = [Index(value=["ownerType","ownerId"]), Index(value=["model","dim"])])
data class EmbeddingRecordEntity(@PrimaryKey val id: String,
  val ownerType: String, val ownerId: String, val model: String, val dim: Int,
  val vector: ByteArray,        // float32 little-endian，无 base64
  val createdAt: Long)

@Entity(tableName = "api_config", indices = [Index(value=["provider","isActive"])])
data class ApiConfigEntity(@PrimaryKey val id: String, val provider: String,
  val baseUrl: String, val model: String, val keyCipher: String,   // 加密后密文
  val isActive: Boolean, val updatedAt: Long)

@Entity(tableName = "wallet_account", indices = [Index(value=["ownerType","ownerId"], unique=true)])
data class WalletAccountEntity(@PrimaryKey val id: String,
  val ownerType: String, val ownerId: String,
  val balanceCents: Long,      // 一律整数"分"
  val currency: String, val updatedAt: Long)

@Entity(tableName = "ledger_entry",
  indices = [
    Index(value=["accountId","createdAt","id"]),
    Index(value=["idempotencyKey"], unique=true)     // 幂等的物理保证
  ])
data class LedgerEntryEntity(@PrimaryKey val id: String, val accountId: String,
  val deltaCents: Long, val reason: String,
  val idempotencyKey: String,  // 如 "redpacket:<envelopeId>:<receiverId>"
  val createdAt: Long)

@Entity(tableName = "long_task", indices = [Index(value=["state","nextRunAt"])])
data class LongTaskEntity(@PrimaryKey val id: String, val kind: String,
  val state: Int, val progress: Int, val payload: String, val nextRunAt: Long)

@Entity(tableName = "miniapp_kv",
  indices = [Index(value=["appId","key"], unique=true)])
data class MiniappKvEntity(@PrimaryKey val id: String, val appId: String,
  val key: String, val value: String, val bytes: Int,
  val updatedAt: Long, val quotaBytes: Long = 2L * 1024 * 1024)
```

### 4.2 索引理由

- `message(sessionId, seq)`：唯一热路径。会话内拉页 = `WHERE sessionId=? AND seq<? ORDER BY seq DESC LIMIT n+1`，走索引直接定位，不做排序不做全扫。
- `message(sessionId, createdAt, id)`：外部时间轴/导出排序用；`(createdAt,id)` 与游标编码一致，保证稳定顺序。
- `message(status, createdAt)`：重发、超时扫描是后台高频任务，不建索引会全表扫。
- `media_object(sha256)` 唯一索引：内容寻址的去重与引用计数都依赖它，同时也是"孤儿文件判定"的反查。
- `ledger_entry(idempotencyKey)` 唯一索引：把幂等从"应用层读-判-写"降级为数据库约束，并发重复领取直接 `UNIQUE` 冲突。

**明令禁止建索引的大字段**：`message.content`、`book chapter 正文`、`persona`、`memory_summary.summary`、`miniapp_kv.value`。这些人字段只允许作为查询结果列，不允许进入 `WHERE`/`ORDER BY` 的索引前缀。

> 旧版事故：87 张表里有 11 个大文本字段被列入索引（`messages.content`、`reader_chapters.content`、`sticker_items.imageUrl`），索引体积接近正文本身。

### 4.3 外键与级联

子表到父表一律 `onDelete = CASCADE`（`message_attachment → message → session → character`），并在 `AppDb` 打开时执行 `PRAGMA foreign_keys = ON`（Room 默认不开）。`media_object` 不设级联：它由引用计数管理，只有 `refCount = 0` 且超过 24h 才允许物理删除。

---

## 5. 分页契约

### 5.1 游标编码

游标 = `base64url(JSON{"s":sessionId,"seq":123456,"t":1730000000000,"v":1})`。必须同时携带 `(seq, createdAt)`：`seq` 是主排序键，`createdAt` 用于与 Web 端 `(timestamp,id)` 语义对齐，`v` 为游标格式版本，服务端遇到不认识的 `v` 直接拒绝（不猜测）。

### 5.2 硬性规则

- 页大小：默认 30，**上限 50**，超出直接抛错而不是截断。
- 深翻必须带游标；**禁止** `LIMIT/OFFSET`。
- 禁止"先 `COUNT(*)` 再决定页数"——总数用 `session.messageCount` 冗余字段。

**为什么禁止 OFFSET**：SQLite 的 OFFSET 必须先扫描并丢弃前 N 行，第 1000 页（每页 50）意味着先读 5 万行，耗时随页码线性增长，正是旧版"进对话卡 1~3 秒"的成因。游标定位是 B-Tree 的 `seek`，复杂度 O(log n + pageSize)，与页码无关。

### 5.3 验收查询与验收标准

```kotlin
@Query("""
  SELECT * FROM message
  WHERE sessionId = :sid AND deletedAt IS NULL
    AND (seq < :cursorSeq OR (:cursorSeq = 0 AND 1=1))
  ORDER BY seq DESC LIMIT :limit
""")
suspend fun pageMessages(sid: String, cursorSeq: Long, limit: Int): List<MessageEntity>
```

`pageMessages` 由 `MessageRepository.page()` 包裹，负责游标解码、`limit.coerceAtMost(50)` 与 `assertStorable` 前置检查。

**验收**：造 10 万条消息的单会话数据，连续取到第 1000 页（每页 50），**每页 P95 < 50ms**，`EXPLAIN QUERY PLAN` 输出必须包含 `SEARCH message USING INDEX index_message_sessionId_seq`，出现 `SCAN` 或 `USE TEMP B-TREE FOR ORDER BY` 即验收失败。

---

## 6. 迁移纪律

- **单 schema 声明**：`@Database(version = N, entities = [...])` 只有一处，`entities` 列表是唯一事实来源。
- **每个版本一个显式 `Migration`**：`MIGRATION_1_2`、`MIGRATION_2_3`…，禁止 `fallbackToDestructiveMigration()`，禁止 `AutoMigration` 用于删列/改类型。
- **每个 Migration 配一个测试**：`MigrationTestHelper` 用上一版导出的 schema JSON 建库，写入样本行，迁移后断言数据与索引都在。
- **禁止累积式 schema 声明**：不得出现 27 个并列的版本声明各自描述"这一版有哪些表"。
- **schema 版本与 app 版本解耦**：app 版本号（`2.4.1`）与 `dbVersion`（`7`）独立递增；备份 `manifest.json` 同时记录两者，导入时以 `dbVersion` 判定兼容，以 `appVersion` 仅作提示。
- **破坏性变更流程**：① 新增"影子表"；② 双写一个版本；③ 迁移函数搬运并校验行数；④ 删除旧表；⑤ 该迁移的测试必须包含"旧数据可读"断言。删列只能在两次发版之后。

> 旧版事故：87 张表、27 个 `version()` 声明，只有 3 张表做对"父键+时间"复合索引；`messages`（最热路径）恰恰没有。

---

## 7. 备份 / 恢复（一级功能）

### 7.1 ZIP 容器结构

```
backup-vp-20260101T1200.zip
  manifest.json      # 唯一入口
  tables/<table>.jsonl   # 每行一条记录，流式
  media/<sha path>       # 可选包含媒体
  checksums.txt
```

```json
{
  "format": "viewphone-backup",
  "formatVersion": 1,
  "dbVersion": 7,
  "appVersion": "2.4.1",
  "createdAt": 1767000000000,
  "device": "android",
  "tables": { "message": {"rows": 104233, "sha256": "..."}, "...": {} },
  "media": { "count": 5120, "bytes": 2147483648 },
  "totalSha256": "..."
}
```

### 7.2 表注册表驱动

```kotlin
object BackupRegistry {
    val tables: List<TableSpec> = listOf(
        TableSpec("character", dependsOn = emptyList()),
        TableSpec("message", dependsOn = listOf("session")),
        TableSpec("message_attachment", dependsOn = listOf("message", "media_object")),
        // 新增表必须在此登记
    )
}
```

导出、清空、导入、容量统计**四处全部读同一个注册表**，由它生成代码。

**CI 门禁**：反射比对 `@Database.entities` 与 `BackupRegistry.tables`，集合不等即构建失败（含"注册了但实体不存在"与"实体存在但未注册"两个方向）。

> 旧版事故：备份只覆盖 43/87 张表，`assets`（桌面照片/壁纸正本）、衣柜、快穿局 8 表、奇遇 6 表、工作台 5 表、购物 5 表从未被导出；新增一张表要手抄四处，漏一处导致备份事务死锁。

### 7.3 流式写盘

全程 `ZipOutputStream` + 逐行写 `JsonLine`，**禁止** `JSON.stringify(整个库)`、禁止在内存里拼 40MB 字符串。写盘缓冲 256KB，`media` 大文件用 `copyTo` 分块搬运。

### 7.4 导入顺序与三段式校验

顺序严格按注册表 `dependsOn` 拓扑排序，单事务、`PRAGMA defer_foreign_keys = ON`。

- **第一段（预检，不写库）**：`manifest` 存在、`formatVersion` 已知、`dbVersion` ≤ 本机且能提供迁移路径；未知表 → **硬失败**，不允许静默跳过。
- **第二段（完整性）**：逐表比对 `rows` 与 `sha256`；解压出来的表集合与注册表必须**完全相等**（缺表或多余表都失败）。
- **第三段（提交）**：单事务内按拓扑序插入，事务结束前跑外键检查 `PRAGMA foreign_key_check`，非空即回滚。

分块传输上限：单次 RPC/文件写入分片 ≤4MB，**禁止**把一个整表 JSON 当一次 IPC 参数传（这正是旧版"单次跨进程传输崩溃"的直接原因）。

### 7.5 全表覆盖测试

1. 每张表写入 ≥2 行样本（含 1 行边界值：空串、超长文本、NULL 外键可选列）；
2. 导出 → 校验 `manifest.tables` 的键集合 == 注册表集合；
3. 清空全库 → 导入 → 逐表比对 `COUNT(*)` 与全字段快照；
4. 追加断言：`media_object` 的 `refCount` 与 `message_attachment` 引用数一致。

> 旧版事故：导入零校验——无版本号/schema/校验和，`if (data.X)` 逐表 clear+bulkAdd，data 里没有的表**不清空** → 静默半恢复。

---

## 8. 容量与配额

- **分开统计**：`structuredBytes = DB 文件大小 + -wal + -shm`；`mediaBytes = media/ 递归求和`。两者在设置页分别显示，不合并成一个"已用空间"。
- **容量可视化**：横向双色条 + 分项 Top 10（按 `media_object.bytes` 聚类的 kind 排行）。刷新节流 5 秒，求和走后台线程。
- **超限必须可见报错**：任何写入捕获到 `QuotaExceededError` / `SQLiteFullException` / `ENOSPC` → 弹出阻断式 Dialog，给出"清理缩略图 / 清理孤儿媒体 / 导出后删除"，并在 UI 上保留"最近一次写入失败"记录。**`catch` 后不展示 = 否决**。
- **清理策略**：
  - `_thumb`/`_cover` 可随时删除并从 `_orig` 重建（后台队列，限速）；
  - 孤儿回收：磁盘文件集合 − `media_object` 集合 = 候选，且要求"文件 mtime > 24h"才删，避免删掉正在导入的文件；
  - 引用计数：`refCount` 在写实体的事务内 `+1`，删实体时 `-1`，`refCount = 0` 且超 24h 才物理删除。

> 旧版事故：全库 0 处 `navigator.storage.persist()`，且 `QuotaExceededError` 被静默吞掉，用户看到"保存成功"而照片其实没落盘。

Web 端补充：Web 首屏必须调用 `navigator.storage.persist()`；拒绝时在 UI 明确提示"浏览器可能在存储紧张时清除本站数据，请定期导出备份"。Chrome 下用 `navigator.storage.estimate()` 交叉核对配额。

---

## 9. 跨端数据交换

- **单一事实来源**：`core/schema/schema.json` 定义实体、字段类型、索引、约束；由它生成 Kotlin data class + Room 注解，以及 Web 侧 TypeScript 类型 + IndexedDB 对象仓库与索引声明。两端**不允许**手写第二份定义；`schema.json` 变更后生成的产物纳入版本控制，CI 校验"生成的产物与 schema 一致"。
- **字段级同一性**：字段名、类型语义、可空性两端一致；时间统一为 UTC 毫秒 `Long/number`；金额统一为整数分；主键统一 UUIDv7 字符串。
- **Web 端选型**：IndexedDB 承载结构化数据（与 Room 表一一对应，同名 objectStore、同名复合索引），OPFS 承载二进制（`media/` 同一套内容寻址路径）。禁止用 localStorage 存业务数据。
- **同一份备份格式**：ZIP + `manifest.json` + `tables/*.jsonl` 是两端唯一交换契约。Web 导出必须能被 Android 导入，反之亦然；`formatVersion` 由 schema 派生，两端 CI 使用**同一批 fixture ZIP** 做双向导入测试。

---

## 10. 数据自检工具（`vp doctor` / 设置页"数据诊断"）

一条命令输出可复制报告，覆盖：

1. **索引完整性**：读取 `sqlite_master` 中全部索引与 `PRAGMA index_list(<table>)`，与 `schema.json` 声明比对，输出缺失/多余索引清单。
2. **base64 残留扫描**：对大文本字段做正则抽样（`^data:` 与 base64 密度），报告命中的表、行 id、字段名与长度。
3. **孤儿文件**：磁盘 − `media_object` 的差集（含字节数）。
4. **悬空引用**：`message_attachment.mediaId` 不存在于 `media_object` 的行数（**必须为 0**）。
5. **超大字段**：任何文本字段 >64KB 的 Top 50，按表聚合。
6. **慢查询清单**：维护内置语句白名单，对每条跑 `EXPLAIN QUERY PLAN`，输出含 `SCAN` 或 `TEMP B-TREE` 的语句；release 构建下抽样 `EXPLAIN QUERY PLAN` 校验热路径仍走索引。
7. **健康分**：以上 1/3/4 任一非空即判定 `UNHEALTHY`，在设置页显示红点。

---

## 11. 反面清单（本项目永久禁止）

| # | 永久禁止 | 旧版事故理由 |
|---|---|---|
| 1 | 任何 base64 / data URL / 原始字节写入结构化库 | thumb+hd 双份 base64 入同一条消息，收藏室再存一份 |
| 2 | 一个实体表有多个 schema 版本声明（累积式） | 87 表 27 个 `version()`，无人能说出真实 schema |
| 3 | 列表查询不建 `(父键, 排序键)` 复合索引 | 只有 3 张表做对，`messages` 热路径无索引 |
| 4 | 用"全量 `toArray()` 拉内存再 `filter()`"替代索引 | 被立为规范，导致进入即卡 1~3 秒 |
| 5 | 给大文本字段建索引 | 11 个大字段入索引，索引体积≈正文 |
| 6 | 手工维护多份表清单（导出/清空/导入/容量） | 43/87 覆盖，新增表漏抄致备份事务死锁 |
| 7 | 导入不做版本号 + 校验和 + 表集合校验 | 无校验，缺表不清空，静默半恢复 |
| 8 | 金额用浮点"元" + `toFixed(2)` | 无"分"整数，累计误差与对账失败 |
| 9 | 幂等靠"读-判-写" | 红包可重复领取 |
| 10 | 整章正文单字段存储、运行时正则切章 | 首屏解析阻塞主线程 |
| 11 | 不调用 `navigator.storage.persist()` | 全库 0 处调用 |
| 12 | 静默 `catch` 配额/写盘异常 | `QuotaExceededError` 被吞，"照片保存了却没有" |
| 13 | 把整库/整表 JSON 作为单次 IPC 参数 | 40MB 导出 + 单次跨进程传输崩溃 |
| 14 | 多套不同源持久化并存 | 主库 + 私有音乐库 + 127 个 localStorage 键 + 原生沙盒 |
| 15 | `LIMIT/OFFSET` 深翻与长列表全量渲染 | 第 1000 页卡顿、DOM 爆炸 |
| 16 | `fallbackToDestructiveMigration()` | 迁移失败即用户数据消失 |

---

## 附：验收标准汇总

| 项 | 标准 |
|---|---|
| 单条消息 | 库内不存二进制；`content` ≤64KB |
| 第 1000 页查询 | P95 < 50ms，走 `index_message_sessionId_seq` |
| 备份覆盖 | `manifest.tables` == `BackupRegistry` == `@Database.entities` |
| 导入 | 未知表硬失败；`foreign_key_check` 为空才提交 |
| 孤儿/悬空 | 悬空引用 = 0；孤儿可回收且清理不影响任何 UI |
| 容量超限 | 100% 有可见报错，无静默 catch |
| 跨端 | 同一 ZIP 双向导入成功 |
