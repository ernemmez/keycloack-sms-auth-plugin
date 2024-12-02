package netzbegruenung.keycloak.authenticator;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class PhoneVerifiedActionFactory implements RequiredActionFactory {
    private static final PhoneVerifiedAction SINGLETON = new PhoneVerifiedAction();

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public String getId() {
        return PhoneVerifiedAction.PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Phone Number Verified Details";
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}