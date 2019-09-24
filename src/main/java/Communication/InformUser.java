package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class InformUser extends Message implements Serializable {
    int peerID;
    public InformUser(int index){
        peerID = index;
    }
}
