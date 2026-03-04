#!/usr/bin/env bash
set -euo pipefail

sudo apt-get update -qq
sudo apt-get install -y --no-install-recommends ripgrep fd-find unzip wget
sudo ln -sf /usr/bin/fdfind /usr/local/bin/fd 2>/dev/null || true

# ── Android SDK ───────────────────────────────────────────────────────────────
ANDROID_HOME=/opt/android-sdk
if [[ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]]; then
  echo '▸ Installing Android cmdline-tools…'
  sudo mkdir -p "$ANDROID_HOME/cmdline-tools"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    -O /tmp/cmdline-tools.zip
  sudo unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools/"
  sudo mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  sudo chown -R "$(id -u):$(id -g)" "$ANDROID_HOME"
  rm /tmp/cmdline-tools.zip

  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
  yes | sdkmanager --licenses >/dev/null 2>&1
  sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.1"
  echo '✓ Android SDK installed'
else
  echo '✓ Android SDK already present'
fi

if [[ ! -e "$HOME/.claude" ]]; then
  for _d in /home/*/.claude; do
    [[ -d "$_d" ]] || continue
    ln -sf "$_d" "$HOME/.claude"
    ln -sf "${_d}.json" "$HOME/.claude.json" 2>/dev/null || true
    break
  done
fi

if [[ -f pnpm-lock.yaml ]]; then
  sudo corepack enable pnpm 2>/dev/null || sudo npm i -g pnpm
  pnpm install
elif [[ -f package-lock.json ]]; then
  npm ci
elif [[ -f yarn.lock ]]; then
  yarn install
elif [[ -f Cargo.toml ]]; then
  cargo fetch
elif [[ -f go.mod ]]; then
  go mod download
elif [[ -f Gemfile ]]; then
  bundle install
elif [[ -f pyproject.toml ]]; then
  uv sync
elif [[ -f Pipfile ]]; then
  pip install pipenv && pipenv install
elif [[ -f requirements.txt ]]; then
  uv venv && uv pip install -r requirements.txt
else
  echo 'No package manager detected'
fi

mkdir -p "$HOME/.local/bin"
claude_bin=$(command -v claude 2>/dev/null || true)
if [[ -n "$claude_bin" && "$claude_bin" != "$HOME/.local/bin/claude" ]]; then
  ln -sf "$claude_bin" "$HOME/.local/bin/claude"
fi

echo '✓ deps installed'
