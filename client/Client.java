import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.math.BigInteger;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import java.security.*;

import java.io.Serializable;

import java.nio.file.*;
import java.io.*; 

public class Client{

  private static final String serverName = "myserver";
  private static Registry registry;
  private static Auction server;

  //locate registry and server
  private static void initClient(){
    try {

      if(registry == null){
        registry = LocateRegistry.getRegistry("localhost");
        server = (Auction) registry.lookup(serverName);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  //returns a reference to the rmi registry
  public static Registry getRegistry(){
    initClient();
    return registry;
  }

  //returns a reference to the server
  public static Auction getServer(){
    initClient();
    return server;
  }   

    //Display all active auctions
    public static void viewAuctions(){
      initClient();

      try 
      {
          String result = server.viewAuctions(); //get string with details of all active auctions
          System.out.println(result);

      } catch (Exception e) {
          e.printStackTrace();
      }
    }

     public static void main(String[] args) {
       if (args.length < 1) {
       System.out.println("Usage: java Client n");
       return;
       }

       initClient();
       int n = Integer.parseInt(args[0]);
      }
}