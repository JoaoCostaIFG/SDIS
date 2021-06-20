all:
	javac *.java

server: all
	java Server 8080 1099

register: all
	java Client localhost 1099 REGISTER ola 123

lookup: all
	java Client localhost 1099 LOOKUP ola

close: all
	java Client localhost 1099 CLOSE

hello: all
	java Client localhost 1099 HELLO

.PHONY: all server register close hello
