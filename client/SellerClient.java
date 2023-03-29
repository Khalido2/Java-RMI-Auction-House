import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import java.security.*;

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.file.*;
import java.io.*; 

public class SellerClient {

    private static Auction server;

    static void init()
    {
      server = Client.getServer();
    }

    private static void createAuctionItem(Scanner kb, int clientID){

        boolean validValues = false;
        String name = "";
        String desc = "";
        int startPrice = 0;
        int reservePrice = 0;

        while(!validValues) //keep looping till valid auction details given
        {

            System.out.println("Give the name of the Item: ");
            name = kb.nextLine();

            System.out.println("Give a description of the Item: ");
            desc = kb.nextLine();

            System.out.println("Give the starting price of the Item: ");
            startPrice = Integer.parseInt(kb.nextLine());

            System.out.println("Give the reserve price of the Item: ");
            reservePrice = Integer.parseInt(kb.nextLine());

            if(startPrice > 0 && reservePrice > startPrice && !name.equals(""))
            {
              validValues = true;
            }else{
              System.out.println("Invalid values, try again\n");
            }
        }
        
        AuctionItem item = new AuctionItem(name, desc, startPrice, reservePrice); //create auction item without id

        try 
        { 
            int auctionID = server.createAuction(item, clientID); //send details to client and get auction id
            
            if(auctionID == -1){
              System.out.println("\nAuction creation unsuccesful");
            }else{
              System.out.println("\nThe Auction id for this auction is " + auctionID);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    //Takes client through process of closing an auction
    private static void closeAuction(Scanner kb, int clientID){

      System.out.println("Give the auction ID of the auction you wish to close: ");
      int auctionID = Integer.parseInt(kb.nextLine()); //get auction id

        try 
        {
            ClientDetails winnerDetails = server.closeAuction(auctionID, clientID); //get winner of auction if there was one

            if(winnerDetails == null){ //if nothing return then auction does not exist
                System.out.println("There is no auction under that name or you are not the creator.");

            }else{ //otherwise check if there was a winner or not

                if(winnerDetails.getName().equals("No Winner")){
                  System.out.println("No winner, reserve price not met");
                }else{
                  System.out.println("\nWinner Details\nName: " + winnerDetails.getName() + "\nEmail: " + winnerDetails.getEmailAddress());
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

     public static void main(String[] args) {
       if (args.length < 1) {
       System.out.println("Usage: java Client n");
       return;
       }

       int clientID = Integer.parseInt(args[0]);
 
       init();

       //Below provides a user interface of sorts so user can create or close an auction multiple times 
       //without needing to repeatedly create a new client
       Scanner kb = new Scanner(System.in);
       String input = "";

       System.out.println("\n\nEnter 1 to view active auctions, 2 to " +
       "create an auction, 3 to close an auction or 4 to quit");
       input = kb.nextLine();

       while(!input.equals("4")){

        if(input.equals("1"))
          Client.viewAuctions();

        if(input.equals("2"))
          createAuctionItem(kb, clientID);

        if(input.equals("3"))
          closeAuction(kb, clientID);

        System.out.println("\n\nEnter 1 to view active auctions, 2 to " +
        "create an auction, 3 to close an auction or 4 to quit");
        input = kb.nextLine();
       }

      }
}

