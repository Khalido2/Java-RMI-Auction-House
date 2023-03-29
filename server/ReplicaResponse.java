import java.io.Serializable;
import java.util.LinkedList;

//Holds the replica state (active auction items) and a value (object that is the result of the function called)
public class ReplicaResponse implements Serializable{

    private LinkedList<AuctionItem> state;
    private Object value;

    public ReplicaResponse(LinkedList<AuctionItem> auctionItems, Object val){
        state = auctionItems;
        value = val;
    }

    public LinkedList<AuctionItem> getState(){
        return state;
    }

    public Object getValue(){
        return value;
    }
    
}
