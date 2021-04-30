package message;

public class NoSuchMessage extends Exception {
    NoSuchMessage(String type) {
        super("No such message with type " + type + " exists");
    }
}
