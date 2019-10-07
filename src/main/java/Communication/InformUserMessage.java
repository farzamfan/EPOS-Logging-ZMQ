package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformUserMessage extends Message implements Serializable {
    int peerID;
    int run;
    String status;
    int selectedPlanID;

    public InformUserMessage(int index, int currentRun, String stat, int planID){
        peerID = index;
        run = currentRun;
        status = stat;
        selectedPlanID = planID;
    }

    public InformUserMessage(int index, int currentRun, String stat) {
        peerID = index;
        run = currentRun;
        status = stat;
    }
}
