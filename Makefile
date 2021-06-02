OUT_DIR = build

build:
	@javac -cp src/ -d $(OUT_DIR) src/*.java src/*/*.java

clean:
	@rm -rf $(OUT_DIR)/*

.PHONY: clean build
