#!/bin/bash
# 从上游 Mangi-11/Eta 同步更新到 ETA-K，保留所有本地功能。
#
# 策略说明（重要）：
#   ETA-K 与上游同源（共同祖先见 `git merge-base`），但两侧都已经分叉很远：
#   上游约 54 个提交、本地约 134 个提交，双方改过 55 个相同文件。
#   直接 `git merge upstream/main` 会产生 ~65 个冲突块，跨 30 个文件，
#   其中 MiuixScaffoldPage / TopBarBackdrop 属于架构级重构，硬合会静默破坏
#   本地自定义壁纸。
#
#   因此本脚本采用「可干净应用的逐个 cherry-pick」：
#   只吸收那些不触碰本地改动区域的提交，冲突多的大特性（Miuix 0.9.5 重构、
#   定时任务引擎、AI 生图、三语文案外置等）留待人工逐个评估后再合。
#
# 用法:
#   ./sync-upstream.sh            # 预演，只报告不落地
#   ./sync-upstream.sh --apply    # 实际应用可干净的提交
set -u
cd "$(dirname "$0")" || exit 1

APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

echo "== 拉取上游 =="
git fetch upstream || { echo "拉取失败"; exit 1; }

BASE=$(git merge-base HEAD upstream/main)
AHEAD=$(git rev-list --count upstream/main..HEAD)
BEHIND=$(git rev-list --count HEAD..upstream/main)
echo "共同祖先: ${BASE:0:8}"
echo "本地领先: $AHEAD   上游领先: $BEHIND"

if [ "$BEHIND" -eq 0 ]; then
  echo "已是最新，无需同步。"
  exit 0
fi

echo
echo "== 逐个评估上游提交能否干净应用 =="
CLEAN=()
for sha in $(git log --format=%h --no-merges HEAD..upstream/main); do
  msg=$(git log -1 --format='%s' "$sha")
  if git cherry-pick --no-commit "$sha" >/dev/null 2>&1; then
    echo "  CLEAN    $sha  $msg"
    CLEAN+=("$sha")
  else
    cf=$(git diff --name-only --diff-filter=U 2>/dev/null | wc -l)
    echo "  CONFLICT $sha  conflict=$cf  $msg"
  fi
  git cherry-pick --abort >/dev/null 2>&1
  git reset -q --hard HEAD
done

echo
echo "可干净应用: ${#CLEAN[@]} 个"

if [ "$APPLY" -eq 0 ]; then
  echo "(预演模式，未改动仓库。加 --apply 实际应用)"
  exit 0
fi

if [ "${#CLEAN[@]}" -eq 0 ]; then
  echo "没有可自动应用的提交，需要人工介入。"
  exit 0
fi

echo
echo "== 开始应用 =="
git tag -f "backup-before-sync-$(date +%Y%m%d-%H%M)" >/dev/null 2>&1
OK=0
for sha in "${CLEAN[@]}"; do
  if git cherry-pick -x "$sha" >/dev/null 2>&1; then
    echo "  已应用 $sha"
    OK=$((OK + 1))
  else
    echo "  失败 $sha，已回滚该提交"
    git cherry-pick --abort >/dev/null 2>&1 || git reset -q --hard
  fi
done
echo "应用完成: $OK / ${#CLEAN[@]}"
echo
echo "下一步: 推送后由 CI 编译验证；本地设备不建议跑全量构建。"
