package netzbegruenung.keycloak.authenticator;

public class LoginBean {
    private String username;
    private boolean rememberMe;

    public LoginBean(String username) {
        this.username = username;
        this.rememberMe = false;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
