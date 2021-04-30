#!/bin/sh

if [ "$1" ]; then
  peer_id="$1"
else
  peer_id="1"
fi

if [ "$2" ]; then
  version="$2"
else
  version="2.0"
fi

../../scripts/peer.sh "$version" "$peer_id" "rmithing${peer_id}" 224.0.0.1 8081 224.0.0.2 8082 224.0.0.3 8083
