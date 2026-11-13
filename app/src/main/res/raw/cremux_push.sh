set -e
SRC="$1"; WORK="$2"; REPO="$3"; BRANCH="$4"; MODE="$5"; MSG="$6"; MIRROR="$7"; GNAME="$8"; GMAIL="$9"
export GIT_TERMINAL_PROMPT=0
rm -rf "$SRC/.git"
if [ -z "$(ls -A "$SRC" 2>/dev/null)" ]; then echo "Source is empty."; exit 1; fi
if ! command -v git >/dev/null 2>&1; then
  echo "==> git not found, installing it"
  pkg install -y git
fi
git --version
URL="https://github.com/$REPO.git"
rm -rf "$WORK"; mkdir -p "$WORK"
if [ "$MODE" = "fresh" ]; then
  echo "==> Fresh repository from your files"
  cp -a "$SRC"/. "$WORK"/
  cd "$WORK"
  rm -rf .git
  git init -q
  git checkout -q -b "$BRANCH"
  git remote add origin "$URL"
else
  echo "==> Cloning $REPO"
  git clone --progress "$URL" "$WORK"
  cd "$WORK"
  if [ "$MIRROR" = "1" ]; then
    echo "==> Removing repo files that are not in your source"
    find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
  fi
  echo "==> Copying your files over the repo"
  cp -a "$SRC"/. "$WORK"/
fi
git config user.name "$GNAME"
git config user.email "$GMAIL"
echo "==> Staging changes"
git add -A
if git diff --cached --quiet && git rev-parse -q --verify HEAD >/dev/null 2>&1; then
  echo "Nothing to commit: the repo already matches your source."
  exit 0
fi
git commit -q -m "$MSG"
git log --oneline -1
echo "==> Pushing"
if [ "$MODE" = "fresh" ]; then
  git push --progress -u --force origin HEAD
else
  git push --progress -u origin HEAD
fi
echo "==> Done: https://github.com/$REPO"
