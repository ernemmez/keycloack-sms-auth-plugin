package netzbegruenung.keycloak.authenticator;

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;

public class PhoneVerifiedAction implements RequiredActionProvider {

    public static final String PROVIDER_ID = "phone_verified_action";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        // Kullanıcıyı register-detail.ftl formuna yönlendir
        context.challenge(context.form().createForm("register-detail.ftl"));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        // Form verilerini al
        String firstName = context.getHttpRequest().getDecodedFormParameters().getFirst("firstName");
        String lastName = context.getHttpRequest().getDecodedFormParameters().getFirst("lastName");
        String email = context.getHttpRequest().getDecodedFormParameters().getFirst("email");

        // Kullanıcı bilgilerini güncelle
        if (firstName != null && lastName != null && email != null) {
            context.getUser().setFirstName(firstName);
            context.getUser().setLastName(lastName);
            context.getUser().setEmail(email);

            // Required action'ı kaldır
            context.getUser().removeRequiredAction(PROVIDER_ID);
            context.success();
        }
    }

    @Override
    public void close() {
    }
}
