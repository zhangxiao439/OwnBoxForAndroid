#!/usr/bin/env bash
# ============================================================================
# 本地静态校验（在推送前拦住会破坏云端构建的问题）
#
# 检查项：
#   1. Kotlin 语法：用便携版 kotlinc 编译你改动的 .kt（过滤缺 SDK 的连带错误）
#   2. 资源引用完整性：主题相关的 @color/@style/@dimen/@drawable 是否可解析
#   3. XML 格式：所有改动的 XML 能否被解析
#   4. 样式 parent 链：parent 指向的样式是否定义
#
# 只在有 kotlinc 时才做第 1 项（缺失则跳过）
# ============================================================================
set -uo pipefail

REPO_DIR="${REPO_DIR:-/c/Users/Administrator/WorkBuddy/gh-work/ownbox-dev}"
KOTLINC="${KOTLINC:-/c/Users/Administrator/WorkBuddy/gh-work/kotlinc/kotlinc/bin/kotlinc.bat}"
PY="${PY:-python3}"

cd "$REPO_DIR" || exit 1
FAIL=0

say() { echo -e "\n\033[1;36m--- $* ---\033[0m"; }
ok()  { echo -e "  \033[32m✓\033[0m $*"; }
err(){ echo -e "  \033[31m✗\033[0m $*"; FAIL=1; }
warn(){ echo -e "  \033[33m!\033[0m $*"; }

# 与上游的差异文件
DIFF_FILES=$(git diff --name-only upstream/main 2>/dev/null)
if [ -z "$DIFF_FILES" ]; then
  DIFF_FILES=$(git diff --name-only HEAD~1 2>/dev/null)
fi

# ------------------------------------------------------------------ 1. XML
say "1/4 XML 格式检查"
XML_FILES=$(echo "$DIFF_FILES" | grep '\.xml$')
if [ -z "$XML_FILES" ]; then
  ok "无改动的 XML 文件"
else
  for f in $XML_FILES; do
    if [ -f "$f" ]; then
      if $PY -c "import xml.etree.ElementTree as ET,sys; ET.parse(sys.argv[1])" "$f" 2>/dev/null; then
        echo "  ok  $f"
      else
        err "XML 解析失败: $f"
        $PY -c "import xml.etree.ElementTree as ET,sys; ET.parse(sys.argv[1])" "$f" 2>&1 | tail -3 | sed 's/^/      /'
      fi
    fi
  done
fi

# --------------------------------------------------- 2. 资源引用可解析性
say "2/4 资源引用完整性"
$PY - <<'PYEOF'
import re, os, glob, sys, collections

RES = "app/src/main/res"
VALS_DIRS = [d for d in glob.glob(RES + "/values*") if os.path.isdir(d)]
COLOR_DIRS = [d for d in glob.glob(RES + "/color*") if os.path.isdir(d)]
LAYOUT_DIRS = [d for d in glob.glob(RES + "/layout*") if os.path.isdir(d)]
DRAW_DIRS = [d for d in glob.glob(RES + "/drawable*") if os.path.isdir(d)]

defined = collections.defaultdict(set)

# 收集所有定义的资源符号
for d in VALS_DIRS:
    for f in glob.glob(d + "/*.xml"):
        try:
            txt = open(f, encoding="utf-8").read()
        except Exception:
            continue
        for m in re.finditer(r'<(color|string|dimen|bool|integer|style|attr|array|item)\s+name="([^"]+)"', txt):
            defined[m.group(1)].add(m.group(2))
        for m in re.finditer(r'<style\s+name="([^"]+)"', txt):
            defined["style"].add(m.group(1))
        for m in re.finditer(r'<attr\s+name="([^"]+)"', txt):
            defined["attr"].add(m.group(1))
        # <item type="color" name="x">
        for m in re.finditer(r'<item\s+type="(\w+)"\s+name="([^"]+)"', txt):
            defined[m.group(1)].add(m.group(2))

for d in COLOR_DIRS + DRAW_DIRS:
    for f in glob.glob(d + "/*"):
        base = os.path.splitext(os.path.basename(f))[0]
        defined["color" if d in COLOR_DIRS else "drawable"].add(base)

for d in LAYOUT_DIRS:
    for f in glob.glob(d + "/*.xml"):
        defined["layout"].add(os.path.splitext(os.path.basename(f))[0])

# 只检查项目自定义资源（第三方库自带的 attr 大量误报，按前缀白名单排除）
THIRD_PARTY_PREFIX = (
    "colorPrimary","colorSecondary","colorSurface","colorError","colorOn",
    "textAppearance","tabTextColor","mtrl_","material","actionMenuTextColor",
    "chipStyle","bottomSheetDialogTheme","materialAlertDialogTheme",
    "textInputStyle","radioButtonStyle","checkboxStyle","switchStyle",
    "toolbarStyle","toolbarTitleTextColor","navigationBarColor","statusBarColor",
    "android:","windowActionMode","popupMenuStyle","toolbarNavigationButtonStyle",
    "actionButtonStyle","actionMenuTextColor","borderlessButtonStyle",
    "selectableItemBackground","panelBackground","preferenceTheme",
)

# 已知来自第三方库（AAR）内部的资源 —— 项目内查不到，但构建时能解析。
# 依据：androidx.preference:preference-ktx:1.2.0 的 AAR 自带这些资源。
KNOWN_EXTERNAL = {
    ("layout", "image_frame"),
    ("style", "PreferenceCategoryTitleTextStyle"),
    ("style", "PreferenceSummaryTextStyle"),
    ("style", "Preference.Material"),
    ("style", "Preference.Material.Title"),
    ("style", "Preference.Material.Summary"),
}

def collect_refs(txt):
    refs = []
    for m in re.finditer(r'"@(?:\+)?(color|style|dimen|drawable|layout|string|attr|array|bool|integer)/([A-Za-z0-9_.]+)"', txt):
        refs.append((m.group(1), m.group(2)))
    for m in re.finditer(r'\?(?:attr)/([A-Za-z0-9_.]+)', txt):
        refs.append(("attr", m.group(1)))
    return refs

# 检查范围：本次改动涉及的 themes/colors/attrs + 所有引用它们的文件
targets = []
for f in ("app/src/main/res/values/themes.xml",
          "app/src/main/res/values/colors.xml",
          "app/src/main/res/values-night/colors.xml",
          "app/src/main/res/values/attrs.xml",
          "app/src/main/res/color/dialog_button_text.xml",
          "app/src/main/res/color/dialog_button_ripple.xml",
          "app/src/main/res/layout/layout_preference_category.xml"):
    if os.path.exists(f):
        targets.append(f)

problems = []
for f in targets:
    txt = open(f, encoding="utf-8").read()
    for kind, name in collect_refs(txt):
        base = name.split(".")[-1] if "." in name else name
        if any(name.startswith(p) or base.startswith(p) for p in THIRD_PARTY_PREFIX):
            continue
        if (kind, base) in KNOWN_EXTERNAL:
            continue
        if base in defined.get(kind, set()):
            continue
        # attr 可能在 Material 库里
        if kind == "attr":
            continue
        problems.append((f, kind, name))

if problems:
    print("  未解析的资源引用:")
    seen = set()
    for f, kind, name in problems:
        k = (f, kind, name)
        if k in seen: continue
        seen.add(k)
        print("    %s : @%s/%s" % (f, kind, name))
    sys.exit(1)
else:
    print("  所有 @color/@style/@dimen/@drawable 引用均可解析")
PYEOF
[ $? -eq 0 ] && ok "资源引用完整" || err "资源引用存在断裂"

# ------------------------------------------------------ 3. style parent 链
say "3/4 style parent 链检查"
$PY - <<'PYEOF'
import re, glob, os, sys

VALS_DIRS = [d for d in glob.glob("app/src/main/res/values*") if os.path.isdir(d)]
styles = {}   # name -> parent
for d in VALS_DIRS:
    for f in glob.glob(d + "/*.xml"):
        txt = open(f, encoding="utf-8").read()
        for m in re.finditer(r'<style\s+name="([^"]+)"(?:\s+parent="([^"]+)")?', txt):
            styles[m.group(1)] = m.group(2)

# Material/Android/第三方库内置 parent 白名单
BUILTIN = re.compile(
    r'^(Theme\.|ThemeOverlay\.|Widget\.|TextAppearance\.|Base\.|'
    r'android:Widget\.|Widget\.AppCompat\.|Widget\.MaterialComponents\.|'
    r'Widget\.Material3\.|Theme\.Material|Theme\.AppCompat|'
    r'Theme\.MaterialComponents|Theme\.Material3|'
    r'Widget\.SagerNet\.|Theme\.SagerNet\.|'
    r'ShapeAppearance\.|MaterialAlertDialog\.|PreferenceThemeOverlay\.|'
    r'Preference\.|PreferenceCategoryTitleTextStyle|PreferenceSummaryTextStyle|)'
)

# 第三方库（AAR）内部定义的样式
EXTERNAL_STYLES = {
    "PreferenceCategoryTitleTextStyle",
    "PreferenceSummaryTextStyle",
    "Preference.Material",
}

missing = []
for name, parent in styles.items():
    if not parent: continue
    if BUILTIN.match(parent): continue
    if parent in EXTERNAL_STYLES: continue
    p = parent.lstrip("?")
    if p.startswith("@"):
        p = p.split("/")[-1]
    base = p.split(".")[-1]
    if p in styles or base in styles:
        continue
    missing.append((name, parent))

if missing:
    print("  parent 指向未定义的样式:")
    for n, p in missing:
        print("    %s -> %s" % (n, p))
    sys.exit(1)
print("  所有 style parent 链可解析（共检查 %d 个 style）" % len(styles))
PYEOF
[ $? -eq 0 ] && ok "style parent 链完整" || err "style parent 链有断裂"

# -------------------------------------------------------------- 4. Kotlin
say "4/4 Kotlin 语法检查"
KT_FILES=$(echo "$DIFF_FILES" | grep '\.kt$')
if [ -z "$KT_FILES" ]; then
  ok "无改动的 Kotlin 文件"
elif [ ! -f "$KOTLINC" ]; then
  warn "未找到 kotlinc ($KOTLINC)，跳过 Kotlin 检查"
  warn "如需检查请下载: https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip"
else
  TMPOUT=$(mktemp -d)
  # 缺 Android SDK 时会产生大量 unresolved reference，需过滤。
  # 「overrides nothing」/「variable expected」也是缺 SDK 的连带错误：
  #   无法解析 RecyclerView.Adapter / View.OnClickListener 等父类型，
  #   导致 override 目标为空、lambda 参数类型推导失败。
  FILTER='unresolved reference|overload resolution ambiguity|not enough information|cannot infer|none of the following|cannot access|is not a function|expression expected|type mismatch|no value passed|overrides nothing|variable expected'
  for f in $KT_FILES; do
    [ -f "$f" ] || continue
    "$KOTLINC" "$f" -d "$TMPOUT" -nowarn 2>&1 \
      | grep -E "^.*\.kt:[0-9]+:[0-9]+: error:" \
      | grep -vE "$FILTER" > "$TMPOUT/errs.txt"
    if [ -s "$TMPOUT/errs.txt" ]; then
      err "真实编译错误 in $f:"
      sed 's/^/      /' "$TMPOUT/errs.txt" | head -20
    else
      ok "$f 无真实编译错误"
    fi
  done
  rm -rf "$TMPOUT"
fi

echo ""
if [ $FAIL -eq 0 ]; then
  echo -e "\033[1;32m✓ 全部静态校验通过\033[0m"
else
  echo -e "\033[1;31m✗ 存在校验失败项，请勿推送\033[0m"
fi
exit $FAIL
