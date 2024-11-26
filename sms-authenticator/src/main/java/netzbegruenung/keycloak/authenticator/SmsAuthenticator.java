/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 * @author Netzbegruenung e.V.
 * @author verdigado eG
 */

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
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;
import org.keycloak.util.JsonSerialization;

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
		if (context.getHttpRequest().getDecodedFormParameters().containsKey("code")) {
			String enteredCode = context.getHttpRequest().getDecodedFormParameters().getFirst("code");
			AuthenticationSessionModel authSession = context.getAuthenticationSession();
			String expectedCode = authSession.getAuthNote("code");
			String ttl = authSession.getAuthNote("ttl");
			
			if (expectedCode == null || ttl == null) {
				context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
					context.form().createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
				return;
			}

			if (enteredCode.equals(expectedCode) && Long.parseLong(ttl) > System.currentTimeMillis()) {
				context.success();
				return;
			} else {
				context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
					context.form()
						.setError("smsAuthCodeInvalid")
						.createForm("login-sms.ftl"));
				return;
			}
		}

		String enteredPhone = context.getHttpRequest().getDecodedFormParameters().getFirst("phone_number");
		String enteredPassword = context.getHttpRequest().getDecodedFormParameters().getFirst("password");

		if (enteredPhone == null || enteredPhone.isEmpty() || enteredPassword == null || enteredPassword.isEmpty()) {
			context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
				context.form()
					.setAttribute("realm", context.getRealm())
					.setError("smsAuthInvalidInput")
					.createForm(TPL_CODE));
			return;
		}

		try {
			AuthenticatorConfigModel config = context.getAuthenticatorConfig();
			int ttl = Integer.parseInt(config.getConfig().get("ttl"));
			int length = Integer.parseInt(config.getConfig().get("length"));
			String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);

			KeycloakSession session = context.getSession();
			Theme theme = session.theme().getTheme(Theme.Type.LOGIN);
			Locale locale = session.getContext().resolveLocale(null);
			String smsAuthText = theme.getEnhancedMessages(context.getRealm(), locale).getProperty("smsAuthText");
			String smsText = String.format(smsAuthText, code, Math.floorDiv(ttl, 60));

			SmsServiceFactory.get(config.getConfig()).send(enteredPhone, smsText);

			AuthenticationSessionModel authSession = context.getAuthenticationSession();
			authSession.setAuthNote("code", code);
			authSession.setAuthNote("ttl", Long.toString(System.currentTimeMillis() + (ttl * 1000L)));
			authSession.setAuthNote("phone_number", enteredPhone);
			
			String redirectUri = authSession.getRedirectUri();
			if (redirectUri == null || redirectUri.isEmpty()) {
				context.failureChallenge(AuthenticationFlowError.INVALID_CLIENT_SESSION,
					context.form()
						.setError("invalidRedirectUri")
						.createErrorPage(Response.Status.BAD_REQUEST));
				return;
			}
			
			context.challenge(context.form()
				.setAttribute("realm", context.getRealm())
				.createForm("login-sms.ftl"));

		} catch (Exception e) {
			logger.error("SMS gönderme hatası", e);
			context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
				context.form()
					.setError("smsAuthSmsNotSent")
					.createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
		}
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
		return (SmsAuthCredentialProvider)session.getProvider(CredentialProvider.class, SmsAuthCredentialProviderFactory.PROVIDER_ID);
	}
}