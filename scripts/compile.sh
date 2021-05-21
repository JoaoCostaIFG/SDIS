#!/bin/sh

build_dir="build"

mkdir -p "$build_dir"
javac -d "$build_dir" \
  file/*.java \
  message/*.java \
  sender/*.java \
  state/*.java \
  utils/*.java \
  Peer.java TestApp.java TestInterface.java

exit 0
