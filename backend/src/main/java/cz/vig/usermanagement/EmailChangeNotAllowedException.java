package cz.vig.usermanagement;

public class EmailChangeNotAllowedException extends RuntimeException {

    public EmailChangeNotAllowedException() {
        super("Email cannot be changed after the user is created");
    }
}
