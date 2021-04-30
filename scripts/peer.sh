#!/bin/sh

echo "$@"
[ $# -ne 9 ] && 
  echo "Usage: $0 <version> <peer_id> <svc_access_point> <mc_addr> <mc_port> <mdb_addr> <mdb_port> <mdr_addr> <mdr_port>" &&
  exit 1

java Peer "$@"

exit 0
