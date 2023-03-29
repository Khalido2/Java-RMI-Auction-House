# Java-RMI-Auction-House

This is an auctioning system built using Java RMI and JGroups. Clients are able to host their own items on the auction house (seller client) as well as bid on other peoples items (seller client).
All auction data is stored in the backend servers which consist of a front end server and several replicas. The auction data is managed and kept consisten using active server replication. You can create as many back end replicas as you want by creating new instances of the BackEnd class.

Clients connect to the front end server via Java RMI.

In a previous version AES encryption and digital signatures were used to ensure no data sent between client and server could be easily deciphered as well as to allow the client and server authenticate each other's identity. This will be added again later

HOW TO RUN:
(THIS PROJECT USES JGROUPS VERSION 3.6.20 TO RUN)
1.  Run an instance of the FrontEnd and at least one BackEnd.
2.  Run as many instances of BuyerClient and SellerClient as you wish and use the console interface to create, close and bid on auctions

[311 Architecture.pdf](https://github.com/Khalido2/Java-RMI-Auction-House/files/11102549/311.Architecture.pdf)
