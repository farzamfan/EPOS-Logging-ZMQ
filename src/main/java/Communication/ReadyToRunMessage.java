package Communication;

import protopeer.network.Message;
import java.io.Serializable;

public class ReadyToRunMessage extends Message implements Serializable {
    public int index;
    public int run;
    public ReadyToRunMessage(int id, int currentRun){
        index = id;
        run = currentRun;
    }
}
