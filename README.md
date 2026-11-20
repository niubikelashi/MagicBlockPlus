# MagicBlockPlus

魔法方块（Magic Block）插件的重写版：借鉴 [MoonWeSif/MagicBlock](https://github.com/MoonWeSif/MagicBlock) 与
[chengzhimeow/MagicBlock](https://github.com/chengzhimeow/MagicBlock) 的玩法，把它们的已知 bug 修掉，
并明确兼容 **Paper / Spigot 1.20.1 及以上**（Java 17+）。

一个「魔法方块物品」可以放置成方块，每放置一次消耗 1 次使用次数；次数用尽后物品变回普通方块（或直接删除，可配置）。
方块可以绑定到玩家（别人不能破坏）、可以收藏（GUI 列表）、可以从世界里取回（变成物品，按剩余次数返还）。

## 下载 / 安装

- 从 [Releases](https://github.com/niubikelashi/MagicBlockPlus/releases) 下载 zip，解压出 jar，放进服务端 `plugins/`。
- 或从 Actions 的 `MagicBlockPlus-jar` 工件下载。
- 首次启动会自动生成 `plugins/MagicBlockPlus/config.yml` 与 `food.yml`。

## 已修复的原插件 bug

下面是通读两个仓库（`moonwesif` 3.2.1.1 重构版、`chengzhimeow` 3.2.0.1 旧版）后列出的缺陷，以及本插件的处理方式。

### 一、「方块莫名其妙消失」类

| # | 原插件问题 | 根因 | MagicBlockPlus 的做法 |
|---|---|---|---|
| 1 | 绑定数据重启后全丢（`BlockBindManager.loadBindConfig`） | 写的是 `bindings.json`，读的是 `bindings.yml`：JSON 文件打开后**从未 `gson.fromJson`**，每次启动用（可能为空的）YAML 覆盖 JSON | 只有一份存储：`world-blocks.yml`，读写对称（读同一份、写同一份），原子写入（先写 `.tmp` 再 `Files.move(ATOMIC_MOVE)`），不再存在「写一个文件、读另一个文件」 |
| 2 | 每 5 分钟的全量清理把未加载区块当空气删记录（`MagicBlockIndexManager.cleanupInvalidEntries`） | `CraftBlock.getType()` 对未加载区块返回 `AIR`，视距变化后条目被永久删除，且异常分支也 `remove()` | 不做定时全量扫描；记录只在「破坏 / 取回 / 区块加载」时清理。可选的启动校验默认关闭（`validate-on-startup: false`），且只校验真正存在的世界 |
| 3 | `ChunkLoadEvent` 恢复时 `if (chunkMagicBlocks.containsKey(chunkKey)) return;`，并且 `chunkKey.startsWith(worldName + "_")` 会把 `world` 误匹配成 `world_nether` | 同区块还剩 1 条就跳过恢复；前缀匹配跨世界误删 | 没有区块级缓存与前缀匹配：记录按 `world;x;y;z` 精确分组，恢复就是「按世界读取记录」，不存在前缀误匹配 |
| 4 | 破坏魔法方块只 `setDropItems(false)`，之后没有任何掉落 | 用一次就什么都不掉 | 破坏时按记录里的**剩余次数**重新生成物品并掉落；先检查 `event.isCancelled()`，别人取消破坏时不会误删记录 |
| 5 | 液体 / 活塞 / 生长 / 雪块成形会破坏魔法方块（`moonwesif` 缺 `BlockFromTo`、活塞、生长等监听） | 缺事件保护 | 补齐 `BlockFromToEvent`、`BlockPistonExtendEvent`/`BlockPistonRetractEvent`、`BlockGrowEvent`、`BlockFormEvent`、`EntityBlockFormEvent`、`BlockSpreadEvent`、`BlockMoistureChange`、`BlockFade`、`LeavesDecay`、`BlockBurn`、`TNTPrime`，全部按开关可配置 |
| 6 | 爆炸分支直接 `setType(AIR)` + 延迟注销，方块被销毁且不返还 | 绕过掉落 | 爆炸走 `BlockExplodeEvent.blockList()` / `EntityExplodeEvent.blockList()`：`drop-on-explosion: true` 时让方块正常被炸掉、删记录并掉落按剩余次数生成的物品；`false` 时 `Iterator.remove()` 保方块 |
| 7 | 魔法识别靠 lore 字符串（`magic-lore: "§7MagicBlock"`，还有 `contains("MagicBlock")` 兜底） | 改配置后旧物品全部失去保护；别人插件写含 `MagicBlock` 的 lore 会被误认 | 身份全部用 `PersistentDataContainer`（`mbp_marker` / `mbp_block_id` / `mbp_owner` / `mbp_uses` / `mbp_max_uses`），lore 只做展示 |
| 8 | 位置 key 有两套（`LocationUtil` 位打包 `x26/y12/z26` 与 `world,x,y,z`），y>±2048 或 x/z>±33,554,432 溢出撞 key | 两个方块撞同一 key → 一个失去保护 | 只有一套字符串 key：`world;x;y;z`，无位打包，无溢出 |

### 二、「刷方块 / 刷物品」类

| # | 原插件问题 | 根因 | MagicBlockPlus 的做法 |
|---|---|---|---|
| 9 | GUI 展示槽位是**真实魔法物品**，且没有 `InventoryDragEvent` 处理 | drag 能把槽位物品拖进背包，每次打开 GUI 都能无限拿方块且不扣次数 | GUI 槽位是「普通物品 + 魔法样式」，**没有 PDC**；`InventoryClickEvent`/`InventoryDragEvent`/`InventoryMoveItemEvent` 全部 cancel；左键领取时才现场生成真实物品 |
| 10 | `retrieveBlock` 可无限重复取回，并按「材质 + owner」删别人的同材质物品 | 取回后不删绑定也不扣次数；扫描不含末影箱/未加载区块 | 取回 = 一次性消费：删记录 → 方块 `setType(AIR)` → 给物品（按记录剩余次数）。记录已不存在 / 方块被替换时只提示，不给物品 |
| 11 | 破坏后按**默认次数**重新生成物品（次数膨胀） | 掉落逻辑与记录脱钩 | 次数守恒：物品 `uses` = 剩余可放置次数，放置 `decrement`，记录存递减后的剩余，破坏按记录剩余掉落 → 总量守恒。自检里有一条专门验证「3 次物品只能放 3 次，第 4 次被拒绝」 |
| 12 | 放置监听在 `NORMAL` 优先级、先写索引再扣次数、不判 `isCancelled`，`BlockMultiPlaceEvent` 只处理主方块 | 区域保护取消放置时次数照扣、索引写入不存在的方块；床/门/活塞另一半无保护 | 放置用 `HIGHEST` 并先 `isCancelled()`；**单独注册 `BlockMultiPlaceEvent`**（`BlockPlaceEvent` 的 handler 收不到多块事件），逐个方块注册 |
| 13 | 红石强制 `getState().update(true, true)`（含漏斗/发射器） | 容器被重复搬运/多吐 | 不强制刷新方块状态，保持原版红石；发射器/漏斗主动投放魔法物品时直接 `BlockDispenseEvent` cancel |
| 14 | `DuplicateBlockDetector` 在 `runAsync` 里改**别的玩家**背包，按 block_id 全局删物品 | 非主线程改他人 Inventory；同 id 合法物品被误删 | 没有跨玩家清理逻辑；每次放置分配新 blockId，天然不会出现重复 |
| 15 | `AddTimes` 整数溢出（`1e9 + 2e9` 变负）；`get/getfood/give` 的 `amount` 无上限 | 次数变负导致方块失效；`amount` 无上限卡服/OOM | 次数收敛（负数→0，上限 `max-uses-limit`，`-1` 表示无限）；命令里的数量强制 `Math.min` 到堆叠上限，材质强制校验 `isBlock()` / `isEdible()` |
| 16 | 熔炉只 cancel `FurnaceSmeltEvent`，没处理燃料事件 | 魔法方块当燃料会被烧掉并产出普通产物（绕过次数、等于复制） | `BlockCookEvent`（三种熔炉的产物）与 `FurnaceBurnEvent`（高炉/烟熏炉是它的子类）都 cancel；合成侧 `PrepareItemCraftEvent` 结果置空 + `CraftItemEvent` cancel |
| 17 | 每次新建 `NamespacedKey` | 重复创建开销与不一致 | PDC key 在 `ItemFactory` 里一次性建好，作为常量使用 |

### 三、1.20.1+ 兼容性

| # | 原插件问题 | 修复 |
|---|---|---|
| 18 | `api-version: '1.18'`、`compileOnly spigot-api 1.18.2` | `api-version: '1.20'`，构建用 `io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT`（`provided`），Java 17 字节码，实测在 1.20.1 与 1.21.11 服务端都能加载 |
| 19 | 靠字符串拼 Material 名：`valueOf("STRIPPED_" + name)`、`EXPOSED_COPPER → COPPER`（铜锭不是方块）→ `setType` 抛 `IllegalArgumentException` | 不做字符串拼名；所有材料走 `Material.matchMaterial` 并校验 `isBlock()`/`isAir()`，无效名字直接跳过（`debug-mode` 会打印） |
| 20 | 1.19/1.20 新方块的按钮/压力板被注释掉，不被当红石组件 | 不做硬编码红石组件集合，交给原版行为；本插件只保护魔法方块本身 |
| 21 | `Material.valueOf` 遇不存在材料抛异常、`getfood` 的 tab 补全在 `foods` 节缺失时 NPE | 全部走 `matchMaterial` 判空；tab 补全在数据缺失时返回空列表 |
| 22 | `softdepend` 只有 PlaceholderAPI（代码却反射 ItemsAdder） | 只保留 `softdepend: [PlaceholderAPI]`；PlaceholderAPI 用反射调用，没装就原样返回变量文本，不会报错 |
| 23 | `folia-supported: true` 却到处用 `getServer().getScheduler()` / 异步改世界与背包 | `folia-supported: false`；IO（存盘、更新检查）走 `runTaskAsynchronously`，其余都在主线程 |

### 四、可用性 / 内存

| # | 原插件问题 | 修复 |
|---|---|---|
| 24 | GUI 缓存（`lastGuiOpenTime`、`searchPlayers`、`originalItems`、`chunkLocationCache`）没有 `PlayerQuitEvent` 清理 | GUI 会话按 `Inventory` 归属存储，`InventoryCloseEvent` 即清理；插件 `onDisable` 全清；不再有全局静态缓存 |
| 25 | GUI 点击延迟到下一 tick（快速双击重复执行），冷却时间戳在 close 时被删 → 冷却失效 | 同一 tick 内同步处理 + 每人 250ms 操作冷却 |
| 26 | 功能容器监听把**玩家背包槽**也一起 cancel | 只拦截属于自家 GUI 的容器（按 `sessionOf(inventory)` 判定），不干扰玩家背包 |
| 27 | `/mb reload` 语义不完整（食物监听、绑定管理器、清理任务、存盘周期都不重建） | `reloadAll()` 重建配置、消息、物品样式、食物表、材料表并重启存盘任务 |
| 28 | 聊天搜索方块会劫持聊天（两处注册 `AsyncPlayerChatEvent`） | 没有聊天搜索功能；GUI 用关键字参数搜索（`/mb gui <关键字>`），不会吞聊天 |
| 29 | `config.yml` 注释示例写了不存在的 `CHERRY_BLOSSOM_BLOCK` | 材料表只列真实存在的 1.20 材料名，无效名字会被跳过并在 `debug-mode` 下打印 |

## 建筑方块目录 + 全量汉化

GUI 只卖「建筑方块」，不会把箱子、熔炉、作物、液体、红石机械这类奇奇怪怪的方块塞进界面。

- **内置常见建筑方块**：`config.yml` 的 `building-blocks.categories` 按类别分组列好（石头与地形 /
  木板与木质 / 砖瓦与装饰 / 金属与矿物块 / 陶瓦·玻璃·羊毛·混凝土 / 下界与末地 / 植物类建材），
  GUI 就按这个顺序排。
- **随版本自动发现新方块**：`building-blocks.auto-add` 打开后，插件按**命名规律**（正则）扫描
  `Material.values()`：`*_PLANKS`/`*_STAIRS`/`*_SLAB`/`*_WALL`/`*_FENCE`/`*_BRICKS`/`*_TILES`/`*_PILLAR`/
  `*_LOG`/`*_BLOCK`/`*_GLASS`/`*_WOOL`/`*_CARPET`/`*_CONCRETE`/`*_TERRACOTTA` 等建材家族，
  新木种、新砖瓦、新陶瓦颜色会自动进目录，不用改代码。自动进来的归到「自动发现」类别。
  上限 `auto-max`（默认 600），怕界面太长就调小。
- **奇怪的方块一律不收**：`building-blocks.exclude` 是显式黑名单（容器、机器、红石、告示牌、门、床、
  火把、液体、作物、树叶/树苗、珊瑚、蘑菇、矿石与粗金属、刷怪笼、结构方块、`LEGACY_*` 废弃材料…），
  命中任意一条就进不了目录。门/床/告示牌被排除是因为它们是双格或实体掉落结构，走原版掉落路径
  会绕过「只掉落一次」的保护，容易刷物品。
- **候选还要满足**：`isBlock()`、不是空气、并且**有物品形态**（能进背包；新版本里某些「只有方块形态」
  的材料会被跳过，不会抛异常拖垮插件）。
- **全量汉化**：方块显示名由词根表拼装（`木种 + 制品`、`颜色 + 材质`、`磨制/裂纹/雕纹/涂蜡` 等修饰词），
  所以新版本的新方块也能自动得到中文名；词根没收录的会退回英文可读名并在启用日志里报数量，
  用 `custom-block-translations` 补一条即可（本轮实测 1.20.1 与 1.21.11 目录里未汉化数量为 0）。
  GUI 标题、翻页按钮、lore、命令帮助、权限描述、`plugin.yml`、配置注释全是中文；
  `language: en` 保留一份英文文案。`/mb search 玻璃` 能按中文名搜，`/mb get|give` 的 tab 补全也认中文名。
- **管理员自选仍然生效**：`gui.materials` / `gui.group` 里的材料照旧加入（归到「手动追加」类别），
  目录只管 GUI 与搜索；`/mb get|give` 可以给任意合法方块。

## 玩法与命令

| 命令 | 作用 | 权限 |
|---|---|---|
| `/mb help` | 用法一览 | `magicblock.use` |
| `/mb gui [页码] [关键字]` | 打开材料选择 GUI | `magicblock.gui` |
| `/mb search <关键字>` | 按名字搜材料 | `magicblock.gui` |
| `/mb list` | 列出世界里的魔法方块 | `magicblock.list` |
| `/mb get <材料> [数量]` | 给自己魔法方块 | `magicblock.get` |
| `/mb give <玩家> <材料> [数量] [次数]` | 给别人魔法方块 | `magicblock.give` |
| `/mb food <食物> [数量]` | 魔法食物（吃一口消耗一次） | `magicblock.get` |
| `/mb retrieve <世界> <x> <y> <z>` | 从世界里取回方块 | `magicblock.retrieve` |
| `/mb settimes <世界> <x> <y> <z> <次数>` | 改方块剩余次数 | `magicblock.admin` |
| `/mb addtimes <世界> <x> <y> <z> <次数>` | 加减方块剩余次数 | `magicblock.admin` |
| `/mb stats` | 统计计数 | `magicblock.stats` |
| `/mb reload` | 重载配置 | `magicblock.admin` |
| `/mb version` | 版本信息 | `magicblock.use` |

权限树：`magicblock.use` / `magicblock.break` / `magicblock.breakprotected` / `magicblock.gui` /
`magicblock.get` / `magicblock.give` / `magicblock.food` / `magicblock.list` / `magicblock.retrieve` /
`magicblock.stats` / `magicblock.reload` / `magicblock.admin`（children）。

## 关键配置（`config.yml`）

- `building-blocks.enabled|auto-add|auto-max|show-category`：建筑方块目录开关（见上一节）
- `building-blocks.include|exclude|categories`：建材命名规则、黑名单、分组清单，管理员可以直接改
- `custom-block-translations`：给没翻出来的材料补中文名（优先级最高）
- `default-uses` / `max-uses-limit`：默认次数与上限（非法值会被收敛，不会抛异常）
- `enable-binding-system` / `allow-use-bound-blocks` / `allow-break-bound-blocks`：绑定保护
- `blacklisted-worlds`：禁用世界
- `depleted-action`：`convert`（变回普通方块）或 `delete`
- `return-item-on-break`：破坏是否返还物品
- `drop-on-explosion`：爆炸是炸掉并掉落，还是保住方块
- `piston-behavior`：`cancel`（活塞不能推魔法方块）或 `forget`（删记录，按原版处理）
- `protect-from-fluid|growth|burn|physics`：各类被动变化是否拦截
- `storage.save-interval-seconds` / `save-on-disable` / `validate-on-startup`
- `self-test`：启动时跑一遍自检（见下方验证）
- `debug-mode`：打印被跳过的材料、被拦截的事件
- `messages.zh_CN` / `messages.en`：两套文案，占位符写成 `%count%` 这种形式

## 构建

GitHub Actions：`mvn package` → 工件 `MagicBlockPlus-jar`；打 tag（`v*`）时额外产出 zip 并创建 Release。

本仓库也提供 `build.ps1`（Windows 本机没有 Maven 时的离线构建：`javac` + `jar`，依赖 jar 放 `libs/`，
该目录不入库）。它会先把 class 文件摊到 jar 根目录再打包，所以产出的 jar 在原生 CraftBukkit/Spigot 上也能加载。

## 验证（真实服务端）

- 离线编译：JDK 17，18 个源文件编译通过，jar 89 KB，字节码 major 61（Java 17）。
- **CraftBukkit 1.20.1**（`3871-Bukkit-3f9263b`，Java 17）：加载 + 启用成功，日志出现
  `启用完成：建筑方块 428 个（内置常见 125，自动发现 303，手动追加 0），已知魔法方块记录 0 条，语言 zh_CN`
  与 `自检通过：全部检查通过`，插件相关异常 0 条；`/mb version|list|stats|get|retrieve|未知子命令` 的回复全是中文。
- **Leaves 1.21.11-156**（MC 1.21.11，Java 21）：同样加载 + 启用成功，
  `建筑方块 467 个（内置常见 127，自动发现 340）`（1.21 新增的树脂块之类被自动收录），自检通过，插件相关异常 0 条。
  日志末尾提示 `未收录中文名 2 个`，就是只在 1.21 才存在的那两种方块，用 `custom-block-translations` 补即可。
  （服务端自带的 `Failed to request yggdrasil public key / PKIX` 报错是本机网络拦截造成的噪音，与插件无关。）
- 自检覆盖：PDC 身份、次数守恒（3 次物品只能放 3 次、第 4 次拒绝）、负数收敛、
  `toPlain`、记录/快照往返、配置非法值收敛、GUI 材料有效、文案存在、食物可食用、无限次数不递减，
  建筑方块目录（必备建材在列、箱子/熔炉/作物/液体/告示牌等禁入、未汉化名不超过 40）、
  目录配置容错（坏正则被忽略、`auto-max` 收敛到 2000、关掉目录后只认手写材料），
  以及命令接线（控制台执行 `mb version/list/stats/get/retrieve/food/未知子命令` 均不抛异常）。
- 注意：class 文件必须在 jar 根目录（`io/github/...`），不能放在 `classes/` 子目录下——
  原生 CraftBukkit/Spigot 会报 `InvalidPluginException: Cannot find main class`（Paper 系的加载器看起来能容忍，所以这个坑很容易被漏掉）。

## 与原版的行为差异（刻意取舍）

- 没有数据库、没有 Folia、没有影子依赖（原版的 MySQL/HikariCP/FoliaLib/gson 都去掉了），
  持久化是一份 YAML，读写对称、原子替换。
- 「取回」只针对世界里的方块，且是一次性消费（这是防刷的关键）。
- 没有「聊天搜方块」这类会劫持聊天输入的功能。
