#!/bin/sh

[ $# -ne 1 ] &&
  echo "Usage: $0 <peer_id>" &&
  exit 1

rm -rf -- "peer-${1}"

exit 0
