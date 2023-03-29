import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.LinkedList;
import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

public class FrontEnd implements Auction, MembershipListener {

    private final int DISPATCHER_TIMEOUT = 2000;

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private LinkedList<AuctionItem> items; //list of active auction listings, this is the cluster state
    private RequestOptions opts=new RequestOptions(ResponseMode.GET_ALL, DISPATCHER_TIMEOUT); //default request options for rpc calls
    private int numViews = 1; //holds number of members on channel

    public FrontEnd(){

        items = new LinkedList<>(); 

        //create/join jgroup channel
        try {
            channel=new JChannel(); // use the default config, udp.xml
            channel.connect("ServerCluster");
            dispatcher = new RpcDispatcher(channel, this); //create RPC dispatcher on top of this channel
            dispatcher.setMembershipListener(this); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        FrontEnd frontEnd = new FrontEnd();
        
        try { //List front end on rmiregistry as server
            String name = "myserver";
            Auction stub = (Auction) UnicastRemoteObject.exportObject(frontEnd, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("Front End started");
           } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
         
    }

    //Returns true if both states are identical to each other
    boolean isIdenticalState(LinkedList<AuctionItem> state1, LinkedList<AuctionItem> state2){
        if(state1.size() != state2.size())
            return false;

        for(int i = 0; i < state1.size(); i++){ //check that id and current price of every auction is the same
            if(state1.get(i).getID() != state2.get(i).getID())
                return false;

            if(state1.get(i).getCurrentPrice() != state2.get(i).getCurrentPrice())
                return false;
        }

        return true;
    }

    //Get replicas whose responses were different to majority voted response and update their state
    private void updateErronousReplicas (RspList<ReplicaResponse> responses, Rsp<ReplicaResponse> correctResponse){
        for (Rsp<ReplicaResponse> response : responses) {

            //Find replicas whos responses were different to the majority voted response
            if(response.getValue() != null && !isIdenticalState(correctResponse.getValue().getState(), response.getValue().getState())){
                try { //update this replicas state
                    dispatcher.callRemoteMethod(response.getSender(),"updateState",new Object[]{correctResponse.getValue()},new Class[]{ReplicaResponse.class}, new RequestOptions(ResponseMode.GET_ALL, DISPATCHER_TIMEOUT)); 
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }
    }

    //Goes through responses and returns the highest voted response aka the response which matches the majority
    //of responses recieved from the cluster
    private Rsp<ReplicaResponse> getMostVotedResponse(RspList<ReplicaResponse> responses){

        HashMap<Rsp<ReplicaResponse>, Integer> responseVotesMap = new HashMap<>(); //stores votes of each response and the response itself
        int mostVotes = -1;
        Rsp<ReplicaResponse> mostVoted = null;

        for (Rsp<ReplicaResponse> response1 : responses) { //goes through responses and picks result with most votes, for now only checking size of auction items array

            if(response1.getValue() != null){ //if valid response received from replica
                int responseVotes = 0;
                LinkedList<AuctionItem> itemsList1 = response1.getValue().getState();

                for (Rsp<ReplicaResponse> response2 : responses) {

                    if(response2.getValue() != null && isIdenticalState(itemsList1, response2.getValue().getState())){ //if responses are identical are not null 
                            responseVotes++; //calculate votes for each response, aka number of identical responses
                    }
                
                }

                responseVotesMap.put(response1, responseVotes); //make entry into hashmap

                if(responseVotes >= responses.size()/2){  //if response votes already a majority, quit early
                    return response1;
                }
            } 

        }          

        //Get the response with the highest number of votes
        for (HashMap.Entry<Rsp<ReplicaResponse>, Integer> entry : responseVotesMap.entrySet()) {
            if(entry.getValue() > mostVotes){
                mostVotes = entry.getValue();
                mostVoted = entry.getKey();
            }
        }

        return (mostVotes != -1 ? mostVoted : null);
    }

   //Return specification of requested item
   //auction items list is buffered in front end so get value from buffer
   public AuctionItem getSpec(int itemId, ClientRequest clientReq)
   {
        for (AuctionItem auctionItem : items) {

            if(itemId == auctionItem.getID())
                return auctionItem;
        }

        return null;
   }

    //creates a new auction item
    //returns auction id of newly created auction
    public int createAuction(AuctionItem auctionItem, int clientID) throws RemoteException {
        try {
            RspList<ReplicaResponse> responses = dispatcher.callRemoteMethods(null,"createAuctionListing",new Object[]{auctionItem, clientID},new Class[]{AuctionItem.class, int.class},opts);
            Rsp<ReplicaResponse> response = null;
            for (Rsp<ReplicaResponse> rsp : responses) { //response = 1st response that isn't null
                if(rsp.getValue() != null){
                    response = rsp;
                    break;
                }
            }

            if(response == null) //if no backend replicas have given a valid response, return unsuccseful creation flag
                return -1;

            //update state of all replicas to match this response
            dispatcher.callRemoteMethods(null,"updateState",new Object[]{response.getValue()},new Class[]{ReplicaResponse.class}, new RequestOptions(ResponseMode.GET_ALL, DISPATCHER_TIMEOUT)); 
            updateState(response.getValue()); //update front end buffer of active auctions to match this response
            return (int) response.getValue().getValue(); //return id of created auction

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1; //unsuccesful creation flag returned
    }

    //Closes an active auction
    //if reserve price is not met, return null meaning no winner, otherwise return client details of winner
    public ClientDetails closeAuction(int auctionID, int clientID) throws RemoteException {
        try {
            RspList<ReplicaResponse> responses = dispatcher.callRemoteMethods(null,"endAuction",new Object[]{auctionID, clientID},new Class[]{int.class, int.class},opts);
            Rsp<ReplicaResponse> majorityResult = getMostVotedResponse(responses);
            updateErronousReplicas(responses, majorityResult); //correct state of any replicas with a differing state
            updateState(majorityResult.getValue()); //update front end buffer of active auctions 
            return (ClientDetails) majorityResult.getValue().getValue(); //return details of winner/no details if no winner

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    //Returns string detailing each active auction listing
    public String viewAuctions() throws RemoteException {
        try {
            RspList<ReplicaResponse> responses = dispatcher.callRemoteMethods(null,"getActiveAuctions",new Object[]{},new Class[]{},opts);
            Rsp<ReplicaResponse> majorityResult = getMostVotedResponse(responses);
            updateErronousReplicas(responses, majorityResult); //correct state of any replicas with a differing state
            updateState(majorityResult.getValue()); //update front end buffer of active auctions 
            return (String) majorityResult.getValue().getValue(); //return string of active auction details

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "ERROR";
    }

    //Update replica state to match new state
    public void updateState(ReplicaResponse mostVotedReponse){
        synchronized(items)
        {
            items.clear();
            items.addAll(mostVotedReponse.getState());
        }
    }

    //Returns state of replica
    public ReplicaResponse getState(){
        return new ReplicaResponse(items, 0); 
    }

    //Allows client to bid on auction listing
    //returns 1 if bid succesful or -1 if unsuccesful
    public int bidOnAuction(int price, int auctionID, ClientDetails clientInfo) throws RemoteException {
        try {
            RspList<ReplicaResponse> responses = dispatcher.callRemoteMethods(null,"bid",new Object[]{price, auctionID, clientInfo},new Class[]{int.class, int.class, ClientDetails.class},opts);
            Rsp<ReplicaResponse> majorityResult = getMostVotedResponse(responses);
            updateErronousReplicas(responses, majorityResult); //correct state of any replicas with a differing state
            updateState(majorityResult.getValue()); //update front end buffer of active auctions 
            return (int) majorityResult.getValue().getValue(); //return succesful/unsuccesful bid flag value

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1; //return unsuccseful bid flag value
    }

    public void viewAccepted(View newView) {

        if(newView.size() > numViews){ //if a new view addded
            System.out.printf("\nNew View ;)\n" + newView.toString());  
            Address newestReplica = newView.get(newView.size()-1); //get newest replica thats just been added
            numViews++;
        }

        if(newView.size() < numViews){ //if a view disconnected/crashed etc, reduce count to new value
            System.out.printf("\nLost View :(\n" + newView.toString());  
            numViews = newView.size();
        }
        
      }
    
      public void suspect(Address suspectedMember) {
        //When a member crashes
        System.out.printf("\nSuspected member crash\n", suspectedMember.toString());
      }
    
      public void block() {
          //implementation not needed
      }
    
      public void unblock() {
          //implementation not needed
      }
    
}
