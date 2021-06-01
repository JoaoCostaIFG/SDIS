#!/bin/sh

usage() {
	echo "Usage: $0 <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<opnd_2>]]"
	exit 1
}

[ $# -lt 2 ] && usage

case "$2" in
backup | BACKUP)
	if [ $# -ne 4 ]; then
		echo "Usage: $0 <peer_ap> BACKUP <filename> <rep degree>"
		exit 1
	fi
	;;
restore | RESTORE)
	if [ $# -ne 3 ]; then
		echo "Usage: $0 <peer_app> RESTORE <filename>"
	fi
	;;
delete | DELETE)
	if [ $# -ne 3 ]; then
		echo "Usage: $0 <peer_app> DELETE <filename>"
		exit 1
	fi
	;;
reclaim | RECLAIM)
	if [ $# -ne 3 ]; then
		echo "Usage: $0 <peer_app> RECLAIM <max space>"
		exit 1
	fi
	;;
state | STATE)
	if [ $# -ne 2 ]; then
		echo "Usage: $0 <peer_app> STATE"
		exit 1
	fi
	;;
*)
  usage
	;;
esac

java TestApp "$@"

exit 0
