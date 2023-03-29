import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Auction extends Remote{
    //get auction item
    public AuctionItem getSpec(int itemId, ClientRequest clientReq) throws RemoteException;

    //create auction listing
    public int createAuction(AuctionItem auctionItem, int clientID) throws RemoteException;

    //close auction listing
    public ClientDetails closeAuction(int auctionID, int clientID) throws RemoteException;

    //get string detailing all active actions
    public String viewAuctions() throws RemoteException;

    //bid on an auction listing
    public int bidOnAuction(int price, int auctionID, ClientDetails clientInfo) throws RemoteException;
}