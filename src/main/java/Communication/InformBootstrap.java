package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformBootstrap extends Message implements Serializable {
    public int currentRun;
    public int numPeers;
    public String status;

    public InformBootstrap(int run, String stat, int newNumPeers){
        currentRun = run;
        status = stat;
        numPeers = newNumPeers;
    }

    public InformBootstrap(int run, String stat){
        currentRun = run;
        status = stat;
    }
}
