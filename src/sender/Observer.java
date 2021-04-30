package sender;

import message.Message;

public interface Observer {
    void notify(Message notification);
}
