#!/bin/env bash

sudo sh -c 'cat << EOF > /etc/udev/rules.d/88-nuand.rules
# Nuand bladeRF 2.0 micro
ATTR{idVendor}=="2cf0", ATTR{idProduct}=="5250", MODE="660", GROUP="plugdev"
EOF'

echo "Wrote UDEV rules, now resetting. Please unplug and replug device"

sudo udevadm control --reload-rules
