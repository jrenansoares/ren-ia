set -eu
export DEBIAN_FRONTEND=noninteractive
export LC_ALL=C
printf '%s\n' 'PROJETO_IA_SETUP:REPAIR'
dpkg --configure -a
printf '%s\n' 'PROJETO_IA_SETUP:INDEX'
apt-get -o APT::Status-Fd=1 -o APT::Update::Error-Mode=any update
printf '%s\n' 'PROJETO_IA_SETUP:PACKAGES'
# Upgrade packages without replacing the rootfs, changing distribution, removing
# packages, or touching project directories. Preserve modified configuration.
apt-get -o APT::Status-Fd=1 -o Dpkg::Use-Pty=0 -o Dpkg::Options::=--force-confold \
    --with-new-pkgs upgrade -y --no-install-recommends
printf '%s\n' 'PROJETO_IA_SETUP:DONE'
