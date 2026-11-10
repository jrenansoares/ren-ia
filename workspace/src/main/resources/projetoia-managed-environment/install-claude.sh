set -eu
export DEBIAN_FRONTEND=noninteractive
export LC_ALL=C
if [ "$(dpkg-query -W -f='${Status}' claude-code 2>/dev/null || true)" = "install ok installed" ]; then
    printf '%s\n' 'Claude Code package is already installed.'
    exit 0
fi
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
trap 'exit 130' HUP INT TERM
mkdir -m 700 "$scratch/gnupg"
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
    --connect-timeout 15 --max-time 120 --retry 2 \
    https://downloads.claude.ai/keys/claude-code.asc -o "$scratch/key.asc"
fingerprint=$(gpg --no-options --homedir "$scratch/gnupg" --batch --with-colons --show-keys "$scratch/key.asc" \
    | awk -F: '$1 == "pub" { primary = 1 } $1 == "fpr" && primary { print $10; primary = 0 }')
if [ "$fingerprint" != '31DDDE24DDFAB679F42D7BD2BAA929FF1A7ECACE' ]; then
    printf '%s\n' 'Claude Code signing key fingerprint mismatch.' >&2
    exit 1
fi
install -d -m 0755 /etc/apt/keyrings
install -m 0644 "$scratch/key.asc" /etc/apt/keyrings/claude-code.asc.new
mv /etc/apt/keyrings/claude-code.asc.new /etc/apt/keyrings/claude-code.asc
printf '%s\n' 'deb [signed-by=/etc/apt/keyrings/claude-code.asc] https://downloads.claude.ai/claude-code/apt/stable stable main' \
    > /etc/apt/sources.list.d/claude-code.list.new
mv /etc/apt/sources.list.d/claude-code.list.new /etc/apt/sources.list.d/claude-code.list
printf '%s\n' 'PROJETO_IA_SETUP:REPAIR'
dpkg --configure -a
printf '%s\n' 'PROJETO_IA_SETUP:INDEX'
apt-get -o APT::Status-Fd=1 -o APT::Update::Error-Mode=any update
printf '%s\n' 'PROJETO_IA_SETUP:PACKAGES'
apt-get -o APT::Status-Fd=1 -o Dpkg::Use-Pty=0 -o Dpkg::Options::=--force-confold \
    install -y --no-install-recommends claude-code
printf '%s\n' 'PROJETO_IA_SETUP:DONE'
