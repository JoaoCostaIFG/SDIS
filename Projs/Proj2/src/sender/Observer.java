package sender;

import message.Message;

public interface Observer {
    void handle(Message notification);
}
