package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformUser extends Message implements Serializable {
    int peerID;
    String status;
    int selectedPlanID;
    public InformUser(int index, String stat, int planID){
        peerID = index;
        status = stat;
        selectedPlanID = planID;
    }
}
