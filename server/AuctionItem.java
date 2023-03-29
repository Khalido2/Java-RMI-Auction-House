import java.io.Serializable;

public class AuctionItem implements Serializable{

    private int creatorID; //id of creator of the auction

    private int auctionId;
    private String itemTitle;
    private String itemDescription;

    private ClientDetails currentWinner; //client details of current highest bidder 
    private int currentPrice; //current price the auction is at

    private int reservePrice;

    public AuctionItem(String title, String desc, int startingPrice, int reserve){

        auctionId = -1;
        itemTitle = title;
        itemDescription = desc;

        reservePrice = reserve;

        currentPrice = startingPrice;
        currentWinner = null;
    }

    public void setAuctionID(int id){
        auctionId = id;
    }

    public void setAuctionCreator(int id){
        creatorID = id;
    }

    public void setCurrentWinner(ClientDetails client){
        currentWinner = client;
    }

    //synchronised to handle ordering if two bids come at the same time
    public synchronized void setCurrentPrice(int price){
        currentPrice = price;
    }

    public boolean isCreator(int id){
        return (creatorID == id);
    }

    public int getCurrentPrice(){
        return currentPrice;
    }
    
    public ClientDetails getCurrentWinner(){
        return currentWinner;
    }

    public int getReservePrice(){
        return reservePrice;
    }

    public int getID(){
        return auctionId;
    }

    public String getTitle(){
        return itemTitle;
    }
    
    public String getDescription(){
        return itemDescription;
    }

    public int getCreatorID(){
        return creatorID;
    }

    public int bid(int price, ClientDetails clientDetails){

        if(price > currentPrice){ //if price is valid
            setCurrentPrice(price);
            setCurrentWinner(clientDetails);
            return 1; //bid succesful
        }

        return -1; //bid was unsuccesful
    }

    @Override
    public String toString() {
        return auctionId + "," + itemTitle + ", Current Price" + currentPrice;
    }
}
