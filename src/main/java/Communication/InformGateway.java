package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformGateway extends Message implements Serializable {
    int peerID;
    String status;
    boolean isLeaf;
    public InformGateway(int index, String stat, boolean leaf){
        peerID = index;
        status =stat;
        isLeaf = leaf;
    }
}
