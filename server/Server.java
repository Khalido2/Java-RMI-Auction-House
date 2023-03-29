import java.io.Serializable;
import java.math.BigInteger;
import java.nio.file.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.crypto.*;
import java.security.*;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class Server implements Serializable { 

    LinkedList<AuctionItem> items; //list of active auaction listings
    Cipher cipher;
    SecretKey cipherKey; //cipher key for AES encryption
    Path path; //path of parent directory
    HashMap<String, PublicKey> clientKeyPairs; //hashmap of client ids and corresponding publick keys
    HashMap<String, Integer> clientsToBeVerified; //holds list of client ids and the challenge being used to authenticate them
    PrivateKey serverKey;
    LinkedList<String> clientsConnected; // a list of connected clients

    public Server() 
    {
        super();
        path = Paths.get("").toAbsolutePath().getParent(); //get parent directory so that files containing keys can be accessed

        items = new LinkedList<>();
        clientsConnected = new LinkedList<>();
        clientKeyPairs = new HashMap<String, PublicKey>();
        clientsToBeVerified = new HashMap<String, Integer>();

        try 
        {
           cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); //get instance of cipher with AES algorithm
           cipherKey = (SecretKey) readObjectFromFile("key"); //read cipher key for AES encryption from file
           serverKey = (PrivateKey) readObjectFromFile("ServerPrivateKey");
        } catch (Exception e) {
           e.printStackTrace();
        }
      
    }

    //generates unique auction id
    int generateAuctionID(){
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

    //Closes an active auction
    //if reserve price is not met, return null meaning no winner, otherwise return client details of winner
    //verifies client is creator using their signature and public key
    public synchronized SealedObject closeAuction(int auctionID, SealedObject clientID, byte[] clientSig){ 

      AuctionItem item = getAuctionItem(auctionID);
      int id = (int) decryptSealedObject(clientID);

      //if auction does not exist, given id not equal to id of listing creator
      //or if this client's digital signature does not match the creators
      if(item == null || !item.isCreator(id)|| !clientSignatureValid(item.getCreatorID(), clientSig, auctionID))
         return encryptObject(null);

      if(item.getCurrentPrice() < item.getReservePrice()){
         items.remove(item); //remove auction from list of auctions
         return encryptObject(new ClientDetails("No Winner", ""));
      }

      items.remove(item); //remove auction from list of auctions
      return encryptObject(item.getCurrentWinner());
    }

    //creates a new auction item
    //ordering handled by synchronised to prevent duplicate IDs
    public synchronized int createAuction(SealedObject auctionDetails, SealedObject clientID) {
        
      AuctionItem auctionItem = (AuctionItem) decryptSealedObject(auctionDetails); //set creator id and auction id
      int id = generateAuctionID();
      auctionItem.setAuctionID(id);
      auctionItem.setAuctionCreator((int) decryptSealedObject(clientID)); 

      items.add(auctionItem);

      return id;
    }
    
    //Builds a string with details of all active auctions
    public String viewAuctions(){
       String result = "Current Auctions:\n";
       int count = 1;

       if(items.size() == 0)
         return "There are currently no active bids";

       for (AuctionItem auctionItem : items) {
          result += count + ". " + auctionItem.getTitle() + "\nID: " + auctionItem.getID()
           + "\nDescription: " + auctionItem.getDescription()
          + "\nCurrent highest bid: " + auctionItem.getCurrentPrice() + "\n\n";
          
          count++;
       }

       return result;
    }

    //Allows client to bid on auction listing
    //returns -1 or 1 if the bid was succesfully made
    //synchronised to handle ordering if two bids come at the same time
    public synchronized int bidOnAuction(int price, int auctionID, SealedObject clientInfo){

      AuctionItem item = getAuctionItem(auctionID);

      if(item != null){ //get item if it exists

         ClientDetails clientDetails = (ClientDetails) decryptSealedObject(clientInfo);
         return item.bid(price, clientDetails);
      }

      return -1; //bid unsuccesful
    }

   //generate random key for AES encryption
   public SecretKey generateAESKey() {

      try {

         KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
         keyGenerator.init(128);
         return keyGenerator.generateKey();
         
      } catch (NoSuchAlgorithmException e) {
         System.out.println("Couldn't generate key, no such algorithm");
      }

      return null;
      
  }

  //Read object from file
  Object readObjectFromFile(String fileName){ 
   try {

      FileInputStream fileIn = new FileInputStream(path.toString() + "//" +fileName+".txt");
      ObjectInputStream objectIn = new ObjectInputStream(fileIn);
      Object result = objectIn.readObject();

      objectIn.close();

      return result; //return object

     } catch (Exception e) {
         System.out.println("Couldn't read file");
     }

     return null;
  }

  //write object to a file
  void writeObjectToFile(String fileName, Object o){
     try {

      FileOutputStream fileOut = new FileOutputStream(path.toString() + "//" +fileName+".txt");
      ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
      objectOut.writeObject(o);
      objectOut.close();

     } catch (Exception e) {
      System.out.println("Couldn't write object");
     } 

  }

  //Generate all public and private keys for server and valid clients, and writes them to files
  void writePrivatePublicKeys(){

     try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA", "SUN"); //get instance of key generator
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN"); //generates strong random value
      keyGen.initialize(1024, random);
      KeyPair[] pair = new KeyPair[5];
      int[] clientIDs = {52, 14, 68, 23}; //array of valid client ids 

      for(int i = 0; i < 5; i++){
         pair[i] = keyGen.generateKeyPair(); //generate key pair

         if(i < 4) //generate 4 client key pairs
         {
            writeObjectToFile("Client" + clientIDs[i], pair[i].getPrivate()); //write private key of client 
            writeObjectToFile("Client" + clientIDs[i] + "publicKey", pair[i].getPublic()); //write public key
         }else{ //write server key pair
            writeObjectToFile("ServerPublicKey", pair[i].getPublic()); //write public key of server
            writeObjectToFile("ServerPrivateKey", pair[i].getPrivate()); //write private key of server
         }
      }
        
     } catch (Exception e) {
        e.printStackTrace();
     }
   }

   //iterate through item list and return item
   AuctionItem getAuctionItem(int itemId){

      for (AuctionItem auctionItem : items) {

         if(itemId == auctionItem.getID())
            return auctionItem;
      }

      return null;
   }

   //Return specification of requested item (encrypted)
   public SealedObject getSpec(int itemId, SealedObject clientReq)
   {
      ClientRequest clientRequest = (ClientRequest) decryptSealedObject(clientReq); //get client request
      AuctionItem item = getAuctionItem(itemId); //get auction item
      return encryptObject(item);
   }

     //Decrypts given sealed object
     Object decryptSealedObject(SealedObject so){
         try {
            
            cipher.init(Cipher.DECRYPT_MODE, cipherKey); 

            return so.getObject(cipher); //return decrypted object

         } catch (Exception e) {
            System.out.println("Couldn't decrypt object");
            return null;
         }
     }

     //Encrypts given sealed object
     SealedObject encryptObject(Serializable object){
         try {
            cipher.init(Cipher.ENCRYPT_MODE, cipherKey); 
            return new SealedObject(object, cipher); //encrypt item

         } catch (Exception e) {
            System.out.println("Couldn't encrypt object");
            return null;
         }
     }

     //Returns challenge to client for authentification
     public int generateChallenge(int clientID){
        int minVal = 10000;
        int maxVal = 99999;
        int challenge = (int) Math.floor(Math.random()*(maxVal-minVal+1)+minVal); //generate random 5 digit number
        clientsToBeVerified.put(clientID+"", challenge); //store challenge with client ID
        return challenge;
     }

     //Authenticate client using challenge and asymmetric keys
     public boolean authenticateClient(int clientID, byte[] clientSig){

      Integer challenge = clientsToBeVerified.get(clientID+""); //get corresponding challenge for client id

         if(challenge == null) //if no challenge matching this client id, return false
            return false;

         if(clientsConnected.contains(clientID +"")) //if client with this id is already connected client, fail authentification
            return false;
    
         if(!clientKeyPairs.containsKey(clientID+"")){ //if client public key not in hashmap read it from file
            File f = new File(path + "//Client" +clientID+"publicKey.txt");
        
            if(!f.exists()) //if public key for this client doesn't exist, return false
            {
              return false;
            }
        
            PublicKey key = (PublicKey) readObjectFromFile("Client" +clientID + "publicKey");
            clientKeyPairs.put(clientID+"", key);
         }

         if(clientSignatureValid(clientID, clientSig, challenge))
         {
            clientsConnected.add(clientID+""); //add client to list of connected clients
            return true;
         }

         return false;
     }

     //Authenticate server with Client, by signing a given challenge with server private key
     public byte[] authenticateServer(int challenge) {

      try 
      {
         Signature dsa = Signature.getInstance("SHA1withDSA", "SUN");
         dsa.initSign(serverKey);//intialise signature with private key
         BigInteger bigInt = BigInteger.valueOf(challenge); //convert challenge int to big int so can be converted to byte array     
         dsa.update(bigInt.toByteArray()); //give signature the data to be signed

         return dsa.sign(); //digital signature then generated    

      } catch (Exception e) {
         e.printStackTrace();
      }

      return null;
   }

   //Returns true if a client's signature is valid (succesfully verified with corresponding public key)
   private boolean clientSignatureValid(int clientID, byte[] clientSig, int dataToSign){
      try 
      {
         Signature dsa = Signature.getInstance("SHA1withDSA", "SUN");
         PublicKey clientKey = clientKeyPairs.get(clientID+"");
         dsa.initVerify(clientKey);//intialise signature with private key
         BigInteger bigInt = BigInteger.valueOf(dataToSign); //convert int to big int so can be converted to byte array     
         dsa.update(bigInt.toByteArray()); //give signature the signed data
         
         return dsa.verify(clientSig); //verify signature    

      } catch (Exception e) {
         e.printStackTrace();
      }

      return false;
   }

   //Called when client disconnects with server i.e. quits
   //allows server to keep track of active connected clients
   public synchronized void disconnectedClient(int clientID, byte[] clientSig){

      if(clientSignatureValid(clientID, clientSig, clientID) && clientsConnected.contains(clientID+"")) //only remove client if they are that client and exist
         clientsConnected.remove(clientID+"");
   }
  
   /*  public static void main(String[] args) {
        try {
         Server s = new Server();
         String name = "myserver";
         Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
         Registry registry = LocateRegistry.getRegistry();
         registry.rebind(name, stub);
         System.out.println("Server ready");
        } catch (Exception e) {
         System.err.println("Exception:");
         e.printStackTrace();
        }
      }
*/
  }