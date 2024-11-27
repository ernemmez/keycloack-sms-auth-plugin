package netzbegruenung.keycloak.authenticator;

import netzbegruenung.keycloak.authenticator.credentials.SmsAuthCredentialData;
import netzbegruenung.keycloak.authenticator.credentials.SmsAuthCredentialModel;
import netzbegruenung.keycloak.authenticator.gateway.SmsServiceFactory;

import org.jboss.logging.Logger;
import org.keycloak.authentication.CredentialValidator;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;
import org.keycloak.util.JsonSerialization;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.credential.dto.PasswordCredentialData;
import org.keycloak.models.credential.dto.PasswordSecretData;
import org.keycloak.models.UserCredentialModel;

import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SmsAuthenticator implements Authenticator, CredentialValidator<SmsAuthCredentialProvider> {

	private static final Logger logger = Logger.getLogger(SmsAuthenticator.class);
	private static final String TPL_CODE = "fin-phone.ftl";

	@Override
	public void authenticate(AuthenticationFlowContext context) {
		AuthenticatorConfigModel config = context.getAuthenticatorConfig();
		RealmModel realm = context.getRealm();
		
		String redirectUri = context.getAuthenticationSession().getRedirectUri();
		context.challenge(context.form()
			.setAttribute("realm", realm)
			.setAttribute("redirectUri", redirectUri)
			.createForm(TPL_CODE));
	}

	@Override
	public void action(AuthenticationFlowContext context) {
		// OTP doğrulama kontrolü
		if (context.getHttpRequest().getDecodedFormParameters().containsKey("code")) {
			handleOtpVerification(context);
			return;
		}

		// İlk kayıt formu işleme
		String phoneNumber = context.getHttpRequest().getDecodedFormParameters().getFirst("phone_number");
		String password = context.getHttpRequest().getDecodedFormParameters().getFirst("password");

		if (phoneNumber == null || phoneNumber.isEmpty() || password == null || password.isEmpty()) {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
				context.form()
					.setAttribute("realm", context.getRealm())
					.setError("smsAuthInvalidInput")
					.createForm(TPL_CODE));
			return;
		}

		// Telefon numarasını normalize et
		String normalizedPhone = normalizePhoneNumber(phoneNumber);
		
		try {
			// Kullanıcı var mı kontrol et
			UserModel existingUser = context.getSession().users().getUserByUsername(context.getRealm(), normalizedPhone);
			if (existingUser != null) {
				// Kullanıcının zorunlu bilgileri var mı kontrol et
				String firstName = existingUser.getFirstName();
				String lastName = existingUser.getLastName();
				String email = existingUser.getEmail();
				
				if (firstName != null && !firstName.isEmpty() && 
					lastName != null && !lastName.isEmpty() && 
					email != null && !email.isEmpty()) {
					// Tüm bilgiler varsa login sayfasına yönlendir
					context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
						context.form()
							.setError("userAlreadyExistsWithDetails")
							.createForm("login.ftl")); // Keycloak'un standart login formu
					return;
				} else {
					// Eksik bilgiler varsa OTP gönder
					sendOtpAndRedirect(context, existingUser, normalizedPhone);
					return;
				}
			}

			// Yeni kullanıcı oluştur
			UserModel newUser = context.getSession().users().addUser(context.getRealm(), normalizedPhone);
			newUser.setEnabled(true);
			
			// Şifre oluşturma işlemini bu şekilde güncelle
			UserCredentialModel credentialModel = UserCredentialModel.password(password);
			newUser.credentialManager().updateCredential(credentialModel);
			
			newUser.setSingleAttribute("phoneNumber", normalizedPhone);
			
			// Yeni kullanıcı için OTP gönder
			sendOtpAndRedirect(context, newUser, normalizedPhone);

		} catch (Exception e) {
			logger.error("Kullanıcı kaydı veya SMS gönderme hatası", e);
			context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
				context.form()
					.setError("registrationError")
					.createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
		}
	}

	private void handleOtpVerification(AuthenticationFlowContext context) {
		String enteredCode = context.getHttpRequest().getDecodedFormParameters().getFirst("code");
		AuthenticationSessionModel authSession = context.getAuthenticationSession();
		String expectedCode = authSession.getAuthNote("code");
		String ttl = authSession.getAuthNote("ttl");
		String username = authSession.getAuthNote("username");

		if (expectedCode == null || ttl == null || username == null) {
			context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
				context.form().createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
			return;
		}

		if (enteredCode.equals(expectedCode) && Long.parseLong(ttl) > System.currentTimeMillis()) {
			// OTP doğrulaması başarılı, kullanıcıyı bul ve oturumu başlat
			UserModel user = context.getSession().users().getUserByUsername(context.getRealm(), username);
			if (user != null) {
				context.setUser(user);
				context.success();
			} else {
				context.failureChallenge(AuthenticationFlowError.INVALID_USER,
					context.form().setError("userNotFound").createErrorPage(Response.Status.BAD_REQUEST));
			}
		} else {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
				context.form()
					.setError("smsAuthCodeInvalid")
					.createForm("login-sms.ftl"));
		}
	}

	private String normalizePhoneNumber(String phoneNumber) {
		// Telefon numarasını temizle ve normalize et
		String normalized = phoneNumber.replaceAll("[^0-9+]", "");
		if (!normalized.startsWith("+")) {
			normalized = "+90" + normalized; // Türkiye için varsayılan
		}
		return normalized;
	}

	private void sendOtpAndRedirect(AuthenticationFlowContext context, UserModel user, String phoneNumber) throws Exception {
		AuthenticatorConfigModel config = context.getAuthenticatorConfig();
		int ttl = Integer.parseInt(config.getConfig().get("ttl"));
		int length = Integer.parseInt(config.getConfig().get("length"));
		String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);

		AuthenticationSessionModel authSession = context.getAuthenticationSession();
		authSession.setAuthNote("code", code);
		authSession.setAuthNote("ttl", Long.toString(System.currentTimeMillis() + (ttl * 1000L)));
		authSession.setAuthNote("username", phoneNumber);
		
		// SMS gönder
		KeycloakSession session = context.getSession();
		Theme theme = session.theme().getTheme(Theme.Type.LOGIN);
		Locale locale = session.getContext().resolveLocale(null);
		String smsAuthText = theme.getMessages(locale).getProperty("smsAuthText");
		String smsText = String.format(smsAuthText, code, Math.floorDiv(ttl, 60));

		SmsServiceFactory.get(config.getConfig()).send(phoneNumber, smsText);

		// OTP giriş formuna yönlendir
		context.challenge(context.form()
			.setAttribute("realm", context.getRealm())
			.createForm("login-sms.ftl"));
	}

	@Override
	public boolean requiresUser() {
		return false;
	}

	@Override
	public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
		return getCredentialProvider(session).isConfiguredFor(realm, user, getType(session));
	}

	@Override
	public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
		user.addRequiredAction(PhoneNumberRequiredAction.PROVIDER_ID);
	}

	public List<RequiredActionFactory> getRequiredActions(KeycloakSession session) {
		return Collections.singletonList((PhoneNumberRequiredActionFactory)session.getKeycloakSessionFactory().getProviderFactory(RequiredActionProvider.class, PhoneNumberRequiredAction.PROVIDER_ID));
	}

	@Override
	public void close() {
	}

	@Override
	public SmsAuthCredentialProvider getCredentialProvider(KeycloakSession session) {
		return (SmsAuthCredentialProvider) session.getProvider(CredentialProvider.class, SmsAuthCredentialProviderFactory.PROVIDER_ID);
	}
}