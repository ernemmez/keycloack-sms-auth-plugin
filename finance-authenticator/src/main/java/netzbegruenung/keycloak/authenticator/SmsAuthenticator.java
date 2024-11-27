package netzbegruenung.keycloak.authenticator;

import netzbegruenung.keycloak.authenticator.gateway.SmsServiceFactory;

import org.jboss.logging.Logger;
import org.keycloak.authentication.CredentialValidator;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.UserCredentialModel;

import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SmsAuthenticator implements Authenticator, CredentialValidator<SmsAuthCredentialProvider> {

	private static final Logger logger = Logger.getLogger(SmsAuthenticator.class);
	private static final String TPL_CODE = "fin-phone.ftl";

	@Override
	public void authenticate(AuthenticationFlowContext context) {
		AuthenticatorConfigModel config = context.getAuthenticatorConfig();
		RealmModel realm = context.getRealm();

		String redirectUri = context.getAuthenticationSession().getRedirectUri();

		boolean enableCaptcha = Boolean.parseBoolean(config.getConfig().getOrDefault("enableCaptcha", "false"));
		String recaptchaSiteKey = config.getConfig().get("recaptchaSiteKey");

		context.challenge(context.form()
				.setAttribute("realm", realm)
				.setAttribute("redirectUri", redirectUri)
				.setAttribute("enableCaptcha", enableCaptcha)
				.setAttribute("recaptchaSiteKey", recaptchaSiteKey)
				.createForm(TPL_CODE));
	}

	@Override
	public void action(AuthenticationFlowContext context) {
		// Form verilerini al
		String formFirstName = context.getHttpRequest().getDecodedFormParameters().getFirst("firstName");
		String formLastName = context.getHttpRequest().getDecodedFormParameters().getFirst("lastName");
		String formEmail = context.getHttpRequest().getDecodedFormParameters().getFirst("email");

		// Eğer form detayları gönderildiyse
		if (formFirstName != null && formLastName != null && formEmail != null) {
			// Email kontrolü
			if (!isValidEmail(formEmail, context)) {
				return;
			}

			// Kullanıcı detaylarını güncelle
			UserModel user = context.getUser();
			user.setFirstName(formFirstName);
			user.setLastName(formLastName);
			user.setEmail(formEmail);

			// auth et akış biter
			context.success();
			return;
		}

		// OTP doğrulama kontrolü
		if (context.getHttpRequest().getDecodedFormParameters().containsKey("code")) {
			handleOtpVerification(context);
			return;
		}

		// İlk kayıt formu işleme
		String phoneNumber = context.getHttpRequest().getDecodedFormParameters().getFirst("phone_number");
		String password = context.getHttpRequest().getDecodedFormParameters().getFirst("password");

		if (phoneNumber == null || phoneNumber.isEmpty() || password == null || password.isEmpty()) {
			String redirectUri = context.getAuthenticationSession() != null
					? context.getAuthenticationSession().getRedirectUri()
					: null;

			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
					context.form()
							.setAttribute("realm", context.getRealm())
							.setAttribute("redirectUri", redirectUri)
							.setError("smsAuthInvalidInput")
							.createForm(TPL_CODE));
			return;
		}

		try {
			String normalizedPhone = normalizePhoneNumber(phoneNumber);

			// Kullanıcı var mı kontrol et
			UserModel existingUser = context.getSession().users().getUserByUsername(context.getRealm(),
					normalizedPhone);
			if (existingUser != null) {
				// Kullanıcının zorunlu bilgileri var mı kontrol et
				String firstName = existingUser.getFirstName();
				String lastName = existingUser.getLastName();
				String email = existingUser.getEmail();

				if (firstName != null && !firstName.isEmpty() &&
						lastName != null && !lastName.isEmpty() &&
						email != null && !email.isEmpty()) {
					// Kullanıcı zaten kayıtlı ve tüm bilgileri var
					context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
							context.form()
									.setAttribute("realm", context.getRealm())
									.setAttribute("auth", true)
									.setAttribute("login", new LoginBean(phoneNumber)) // Telefon numarasını username
																						// olarak set et
									.setAttribute("social", Boolean.FALSE)
									.setAttribute("registrationDisabled", Boolean.FALSE)
									.setError("userAlreadyExistsWithDetails")
									.createForm("login.ftl"));
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

		} catch (IllegalArgumentException e) {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
					context.form()
							.setAttribute("realm", context.getRealm())
							.setError("numberFormatNumberInvalid")
							.createForm(TPL_CODE));
			return;
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
			// OTP doğrulaması başarılı, kullanıcıyı bul ve detay formuna yönlendir
			UserModel user = context.getSession().users().getUserByUsername(context.getRealm(), username);
			if (user != null) {
				context.setUser(user);
				user.addRequiredAction(PhoneVerifiedAction.PROVIDER_ID);
				context.challenge(context.form()
						.createForm("register-detail.ftl"));
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
		// Telefon numarasından boşluk, tire gibi karakterleri temizle
		String normalized = phoneNumber.replaceAll("[^0-9+]", "");

		// Eğer +90 ile başlamıyorsa ekle
		if (!normalized.startsWith("+90")) {
			normalized = "+90" + normalized;
		}

		// +90 dahil toplam uzunluk kontrolü (13 karakter olmalı)
		if (normalized.length() != 13) {
			throw new IllegalArgumentException("Geçersiz telefon numarası uzunluğu. Örnek format: +905321234567");
		}

		// +90'dan sonraki kısmın uzunluk kontrolü (10 karakter olmalı)
		String numberWithoutPrefix = normalized.substring(3); // +90 kısmını çıkar
		if (numberWithoutPrefix.length() != 10) {
			throw new IllegalArgumentException("Telefon numarası +90 hariç 10 haneli olmalıdır");
		}

		// İlk rakam 5 ile başlamalı (Türkiye mobil numaraları için)
		if (!numberWithoutPrefix.startsWith("5")) {
			throw new IllegalArgumentException("Geçersiz mobil numara formatı. 5 ile başlamalıdır");
		}

		return normalized;
	}

	private void sendOtpAndRedirect(AuthenticationFlowContext context, UserModel user, String phoneNumber)
			throws Exception {
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
		return Collections.singletonList((PhoneNumberRequiredActionFactory) session.getKeycloakSessionFactory()
				.getProviderFactory(RequiredActionProvider.class, PhoneNumberRequiredAction.PROVIDER_ID));
	}

	@Override
	public void close() {
	}

	@Override
	public SmsAuthCredentialProvider getCredentialProvider(KeycloakSession session) {
		return (SmsAuthCredentialProvider) session.getProvider(CredentialProvider.class,
				SmsAuthCredentialProviderFactory.PROVIDER_ID);
	}

	private boolean isValidEmail(String email, AuthenticationFlowContext context) {
		// Email format kontrolü
		String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
		Pattern pattern = Pattern.compile(emailRegex);
		Matcher matcher = pattern.matcher(email);

		if (!matcher.matches()) {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
					context.form()
							.setError("invalidEmailFormat")
							.createForm("register-detail.ftl"));
			return false;
		}

		// Email'in başka bir kullanıcı tarafından kullanılıp kullanılmadığını kontrol
		// et
		UserModel existingUserWithEmail = context.getSession().users().getUserByEmail(context.getRealm(), email);

		if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(context.getUser().getId())) {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
					context.form()
							.setError("emailAlreadyExists")
							.createForm("register-detail.ftl"));
			return false;
		}

		return true;
	}
}