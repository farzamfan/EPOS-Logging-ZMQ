package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformGatewayMessage extends Message implements Serializable {
    int peerID;
    int run;
    String status;
    boolean isLeaf;
    public InformGatewayMessage(int index, int currentRun, String stat, boolean leaf){
        peerID = index;
        run = currentRun;
        status =stat;
        isLeaf = leaf;
    }
}
