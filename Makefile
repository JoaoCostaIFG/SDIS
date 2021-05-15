<<<<<<< HEAD
.PHONY: build
OUT_DIR = build/

build: clean
	@javac -cp src/ -d $(OUT_DIR) src/*/*.java src/*.java

clean:
	@rm -rf $(OUT_DIR)/*
=======
OUT_DIR = build

build: clean
	@javac -cp src/ -d $(OUT_DIR) src/*.java src/*/*.java

clean:
	@rm -rf $(OUT_DIR)/*

.PHONY: clean build
>>>>>>> master
