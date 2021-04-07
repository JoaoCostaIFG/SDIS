#!/bin/sh

[ $# -ne 1 ] &&
  echo "Usage: $0 <peer_id>" &&
  exit 1

cd build || exit 1
rm -rf "files-${1}"

exit 0
