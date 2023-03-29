import java.io.Serializable;

//Stores details of a bidding client
public class ClientDetails implements Serializable{

    private String name;
    private String emailAddress;

    public ClientDetails(String n, String email){
        name = n ;
        emailAddress = email;
    }
    
    public String getName(){
        return name;
    }

    public String getEmailAddress(){
        return emailAddress;
    }
}
