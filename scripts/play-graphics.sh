#!/usr/bin/env bash
# Renders the Play Store graphics in play/ from their SVGs, with the app's own bundled fonts
# (Montserrat and Barlow) rather than whatever ImageMagick would fall back to.
#
# Usage: scripts/play-graphics.sh
set -euo pipefail
cd "$(dirname "$0")/.."

fonts=app/src/main/res/font
magick -background none play/icon-512.svg play/icon-512.png

magick -background none play/feature-graphic.svg \
    -font "$fonts/montserrat_800.ttf" -pointsize 110 -fill '#F4F5F7' \
    -annotate +440+290 'Rivals' \
    -font "$fonts/barlow_400.ttf" -pointsize 32 -fill '#ABA6BA' \
    -annotate +444+342 'Pool scores between friends' \
    play/feature-graphic.png

echo "Wrote play/icon-512.png and play/feature-graphic.png"
