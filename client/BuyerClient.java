import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.PrivateKey;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

import java.io.Serializable;

import java.nio.file.*;
import java.io.*; 

public class BuyerClient {

    private static Auction server;

    static void init()
    {
      server = Client.getServer();
    }

    //Bid on an auction
    static void bidOnAuction(ClientDetails details, Scanner kb){

        System.out.println("\nEnter the ID of the auction you want to bid on: "); //get auction id and bidding price
        int auctionID = Integer.parseInt(kb.nextLine());

        System.out.println("\nHow much will you bid: ");
        int price = Integer.parseInt(kb.nextLine());

        try 
        {
            int result = server.bidOnAuction(price, auctionID, details);

            if(result == 1){ //indicate if bid was succesfully made
                System.out.println("Bid has been succesfully made.");
            }else{
                System.out.println("Bid was unsuccesful. Ensure you entered a valid auction id and bidding amount.");
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
       Scanner kb = new Scanner(System.in);
       String input = "";

       System.out.println("What's your name: ");
       String name = kb.nextLine();

       System.out.println("\nWhat's your email address: ");
       String address = kb.nextLine();

       ClientDetails details = new ClientDetails(name,address);

       //Below provides a user interface of sorts so user can bid and view auctions multiple times 
       //without needing to repeatedly create a new client
       System.out.println("\n\nEnter 1 to view bids, 2 to bid on an item or 3 to quit");
       input = kb.nextLine();

       while(!input.equals("3")){

        if(input.equals("1"))
            Client.viewAuctions();

        if(input.equals("2"))
            bidOnAuction(details,kb);

        System.out.println("\n\nEnter 1 to view bids, 2 to bid on an item or 3 to quit");
        input = kb.nextLine();
       }

      }
}

