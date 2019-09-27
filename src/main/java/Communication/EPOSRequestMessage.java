package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class EPOSRequestMessage extends Message implements Serializable {
    int run;
    int numPeers;
    String status;
    public EPOSRequestMessage(int currentRun, int numNodes, String stat){
        run = currentRun;
        numPeers = numNodes;
        status = stat;
    }
}
