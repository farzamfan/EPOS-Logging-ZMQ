package Communication;

import data.Plan;
import data.Vector;
import protopeer.network.Message;
import java.io.Serializable;
import java.util.List;

public class ChangeGFCMessage extends Message implements Serializable {
    public String status;

    public ChangeGFCMessage(String func){
        status = func;
    }
}
