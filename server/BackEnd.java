import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.jgroups.util.RspList;
import org.jgroups.util.Util;

import org.jgroups.*;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;

public class BackEnd  {

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private LinkedList<AuctionItem> items; //list of active auction listings, this is the cluster state

    public BackEnd(){

        items = new LinkedList<>(); 
        try {
            channel=new JChannel();
            channel.connect("ServerCluster"); //join channel
            dispatcher=new RpcDispatcher(channel, this); //create RPC dispatcher on top of this channel

            //Get the state of one of the cluster from one of the replicas
            RspList<ReplicaResponse> responses= dispatcher.callRemoteMethods(null,"getState",new Object[]{},new Class[]{}, new RequestOptions(ResponseMode.GET_FIRST, 3000)); 
            updateState(responses.getFirst()); //update state with first response

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //generates unique auction id
    //ordering handled by synchronised to prevent duplicate IDs
    synchronized int generateAuctionID(){
        boolean idExists = true;
        int auctionID = 0;
 
        while(idExists){
           auctionID = 10000 + new Random().nextInt(90000); //generates random 5 digit id
           idExists = auctionIDExists(auctionID);
        }
 
        return auctionID;
    }

    //ensures auction id has not already been used
    boolean auctionIDExists(int auctionID){

        for (AuctionItem auctionItem : items) {
           if(auctionItem.getID() == auctionID)
              return true;
        }
  
        return false;
    }

    //Builds a string with details of all active auctions
    public ReplicaResponse getActiveAuctions(){
       String result = "Current Auctions:\n";
       int count = 1;

       if(items.isEmpty())
         return new ReplicaResponse(items, "There are currently no active bids");

       for (AuctionItem auctionItem : items) {
          result += count + ". " + auctionItem.getTitle() + "\nID: " + auctionItem.getID()
           + "\nDescription: " + auctionItem.getDescription()
          + "\nCurrent highest bid: " + auctionItem.getCurrentPrice() + "\n\n";
          
          count++;
       }

        return new ReplicaResponse(items, result); //return state and string of active auctions;
    }

    //creates a new auction item
    //returns cluster state after creation
    public ReplicaResponse createAuctionListing(AuctionItem auctionItem, int clientID) {
        
        int id = generateAuctionID();
        auctionItem.setAuctionID(id);
        auctionItem.setAuctionCreator(clientID); 
  
        synchronized(items){
            items.add(auctionItem); 
        }
  
        return new ReplicaResponse(items, id); //return state and id of created auction
    }

    //Allows client to bid on auction listing
    //returns cluster state after every bid as well as 1 if bid succesful or -1 if unsuccesful
    public ReplicaResponse bid(int price, int auctionID, ClientDetails clientDetails){

        AuctionItem item = getAuctionItem(auctionID);
  
        if(item != null){ //get item if it exists
           int result = item.bid(price, clientDetails);
           return new ReplicaResponse(items, result);
        }
  
        return new ReplicaResponse(items, -1); //return cluster state and 1 or -1 to indicate if bid succesful
    }

    //Closes an active auction
    //if reserve price is not met, return null meaning no winner, otherwise return client details of winner as well as state
    public ReplicaResponse endAuction(int auctionID, int clientID){ 

        AuctionItem item = getAuctionItem(auctionID);
  
        //if auction does not exist, given id not equal to id of listing creator
        if(item == null || !item.isCreator(clientID))
           return new ReplicaResponse(items, null);
  
        if(item.getCurrentPrice() < item.getReservePrice()){

            synchronized(items){
                items.remove(item); //remove auction from list of auctions
            }
           return new ReplicaResponse(items, new ClientDetails("No Winner", ""));
        }
  
        synchronized(items){
            items.remove(item); //remove auction from list of auctions
        }
        return new ReplicaResponse(items, item.getCurrentWinner());
    }

    //iterate through item list and return item
    private AuctionItem getAuctionItem(int itemId){

      for (AuctionItem auctionItem : items) {

         if(itemId == auctionItem.getID())
            return auctionItem;
      }

      return null;
    }

    //Update replica state to match new state
    public void updateState(ReplicaResponse mostVotedReponse){

        synchronized(items)
        {
            items.clear();
            items.addAll(mostVotedReponse.getState());
        }

        System.out.println("State updated");
    }

    //Returns state of replica
    public ReplicaResponse getState(){
        return new ReplicaResponse(items, 0); 
    }

    public static void main(String[] args) {
        new BackEnd();    
    }
}
