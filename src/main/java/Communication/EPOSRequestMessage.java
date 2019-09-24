package Communication;

import protopeer.network.Message;

import java.io.Serializable;

public class EPOSRequestMessage extends Message implements Serializable {
    int numNodes;
}
