OUT_DIR = build

build:
	@javac -cp src/ -d $(OUT_DIR) src/*.java src/*/*.java

clean:
	@rm -rf $(OUT_DIR)/*

cleanpeers:
	@rm -rf $(OUT_DIR)/peer-*

.PHONY: clean build
