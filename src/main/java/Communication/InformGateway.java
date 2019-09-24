package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformGateway extends Message implements Serializable {
    int peerID;
    String status;
    public InformGateway(int index, String stat){
        peerID = index;
        status =stat;
    }
}
