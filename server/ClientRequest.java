import java.io.Serializable;

//Serializable class containing client id, as per level 2
public class ClientRequest implements Serializable{

    private int clientID;

    public ClientRequest(int ID){
        clientID = ID;
    }

    public int getID(){
        return clientID;
    }
    
}
