# OwnBox 上游同步指南

> 目标：跟随 `Own716/OwnBoxForAndroid` 上游更新，同时**完整保留**你在 fork 上的
> 主题移植改动（24 主题系统）。

---

## 一、为什么原来的做法不可持续

之前的工作方式是「**patch 快照 + 整文件覆盖**」：
`gh-work/patch/` 里存着改好的完整文件副本，用 GitHub Contents API 逐个 PUT 覆盖到
`feature/throne-theme` 分支。

这个模型的致命问题是：

| 问题 | 后果 |
|---|---|
| patch 里存的是**旧快照** | 上游改了 `themes.xml` 后，你再 PUT 一次就把上游的新内容**整体覆盖回旧版** |
| 覆盖是**整文件级** | 上游的 8 个新提交（新增 `AppearanceSettingsFragment`、工具栏重构等）全部丢失 |
| 无版本历史 | 每次推送都产生一个新 commit，无法追溯、无法 diff、无法回滚 |

**实测数据**：你的 fork 停在 `v3.2.42`，上游已到 `v3.2.47`，中间 8 个提交、
约 4000 行改动，全部没有进入你的分支。

---

## 二、现在的模型：标准 git 三分支

```
upstream/main            官方上游，永不改动
      │
      │  git reset --hard（镜像）
      ▼
origin/main              你的 fork 的 main = 上游的镜像
      │
      │  git rebase upstream/main（你的改动叠在上面）
      ▼
origin/feature/throne-theme   上游最新 + 你的主题改动
```

**分支职责**

| 分支 | 内容 | 更新方式 |
|---|---|---|
| `upstream/main` | 官方原版 | `git fetch upstream` |
| `origin/main` | 与上游完全一致 | `git reset --hard upstream/main` |
| `origin/feature/throne-theme` | 上游最新 + 你的 10 个文件改动 | `git rebase upstream/main` |

这样做的关键好处：**上游更新是「底座」，你的改动是「补丁层」**。
rebase 时 git 做三方合并，只要上游和你改的不是同一处代码，就自动合上；
即使冲突，也只在**具体文件的具体行**，而不是整文件被覆盖。

### 本仓库的实际结构

```
C:\Users\Administrator\WorkBuddy\gh-work\ownbox-dev\   # 主开发仓库
├── tools/
│   ├── sync.sh      # 一键同步
│   └── verify.sh    # 推送前静态校验
```

你的改动只涉及 **10 个文件**（与上游的差异）：

```
.github/workflows/theme-build.yml                      自签构建工作流
app/src/main/java/io/nekohasekai/sagernet/utils/Theme.kt
app/src/main/java/moe/matsuri/nb4a/ui/ColorPickerPreference.kt
app/src/main/res/color/dialog_button_ripple.xml
app/src/main/res/color/dialog_button_text.xml
app/src/main/res/layout/layout_preference_category.xml
app/src/main/res/values-night/colors.xml
app/src/main/res/values/attrs.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/themes.xml
```

---

## 三、日常使用

### 同步上游（一条命令）

```bash
cd /c/Users/Administrator/WorkBuddy/gh-work/ownbox-dev
export GITHUB_TOKEN=<你的 token>     # 推送必需
./tools/sync.sh
```

脚本依次执行：

1. `git fetch upstream` — 拉取上游
2. `git reset --hard upstream/main` — 让 `main` 镜像上游
3. `git rebase upstream/main` — 把你的分支挪到最新上游之上
4. `./tools/verify.sh` — 本地静态校验（不通过就中止，不推送）
5. `git push` — 推送两个分支
6. 云端构建 → 下载 APK 到 `gh-work/apk_out/`

其他用法：

```bash
./tools/sync.sh --no-push    # 只同步+校验，不推送
./tools/sync.sh --no-build   # 同步但不触发云端构建
```

### 出现冲突时

脚本会停下并打印冲突文件。解决流程：

```bash
git status                          # 看哪些文件冲突
# 编辑文件：删掉 <<<<<<< ======= >>>>>>> 标记，
# 保留「上游的新内容」+「你自己的改动」两部分
git add <冲突文件>
git rebase --continue                # 重复直到完成
# 想放弃重来：git rebase --abort
```

### 同步后重新构建

冲突解决完、校验通过后：

```bash
./tools/sync.sh                      # 会推送并构建
```

或者手动触发：

```bash
export GITHUB_TOKEN=<token>
python3 tools/fetch_apk.py feature/throne-theme
```

---

## 四、静态校验拦截什么

`tools/verify.sh` 在推送前检查 4 类问题，全部是**云端构建失败的常见原因**：

| 检查 | 拦截的问题 | 举例 |
|---|---|---|
| XML 格式 | 合并时写坏的 XML | 未闭合标签 |
| 资源引用完整性 | `@color/xxx` / `@style/xxx` 不存在 | AAPT: `resource color/xxx not found` |
| style parent 链 | `parent=` 指向不存在的样式 | AAPT: `style attribute 'attr/xxx' not found` |
| Kotlin 语法 | 移植时的语义错误 | `Missing return statement`、`must be initialized` |

校验器已内建两条经验规则（来自之前的踩坑）：

1. **第三方库自带资源**（如 `androidx.preference` AAR 里的 `image_frame.xml`、
   `PreferenceCategoryTitleTextStyle`）在项目内查不到，但构建时能解析 → 列入白名单，
   不误报。
2. **缺 Android SDK 时 kotlinc 的大量连带错误**（`unresolved reference`、
   `overrides nothing`、`variable expected` 等）→ 过滤掉，只报真实错误。

判据：排除连带错误后剩下的才是真问题。

---

## 五、已验证的上游兼容性

针对当前上游 `e1957e1`（v3.2.47）的实测结果：

- ✅ `git merge upstream/main` — **零冲突自动合并**
- ✅ `git rebase upstream/main` — **零冲突**
- ✅ 合并后双方改动全部完好：
  - 你的 `COLOR_THEMES` / 24 主题常量 → 在
  - 你的 `Theme.SagerNet.*` 55 个样式 → 在
  - 上游新增的 `AppearanceSettingsFragment.kt` → 在
  - 上游新增的 `actionButtonStyle` / `toolbarNavigationButtonStyle` → 在
- ✅ 静态校验 4 项全过
- ⚠️ 上游把外观设置重构到了独立的 `AppearanceSettingsFragment`，
  但你的 `ColorPickerPreference` 改动挂的仍是同一个 `appTheme` 入口 → **不受影响**

上游这 8 个提交只改了 `themes.xml` 一处，且改动区域（`ThemeOverlay.SagerNet.Toolbar.*`）
与你新增的样式**不重叠**，所以能干净合并。

---

## 六、注意事项

1. **token 权限**：需要 `Contents: Read and write` + `Workflows: Read and write`
   + `Actions: Read and write`（读 artifact 用）。
2. **签名冲突**：自签 keystore（`CN=OwnBox`）与官方签名不同，**不能覆盖安装**官方版，
   需先卸载。
3. **不要用旧的 `pipeline.py` / `push_patch.py`**：那是整文件覆盖模型，
   会破坏上游更新。改用 `tools/sync.sh`。
4. **fork 的 Actions 需在 Settings → Actions 手动启用**（已启用）。
5. **构建耗时**：`libcore`（Go+NDK 编译）约 5-8 分钟，`build` job 约 10-15 分钟，
   整体约 20 分钟。
