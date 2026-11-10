set -eu
export DEBIAN_FRONTEND=noninteractive
export LC_ALL=C
printf '%s\n' 'PROJETO_IA_SETUP:INDEX'
apt-get -o APT::Status-Fd=1 -o APT::Update::Error-Mode=any update
printf '%s\n' 'PROJETO_IA_SETUP:DONE'
