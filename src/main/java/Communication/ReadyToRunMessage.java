package Communication;

import protopeer.network.Message;
import java.io.Serializable;

public class ReadyToRunMessage extends Message implements Serializable {
    int index;
    public ReadyToRunMessage(int id){
        index = id;
    }
}
