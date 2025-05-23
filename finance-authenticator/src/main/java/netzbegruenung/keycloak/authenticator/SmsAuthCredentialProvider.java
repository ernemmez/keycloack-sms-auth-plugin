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
 * @author Netzbegruenung e.V.
 * @author verdigado eG
 */

package netzbegruenung.keycloak.authenticator;

import netzbegruenung.keycloak.authenticator.credentials.SmsAuthCredentialModel;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialTypeMetadata;
import org.keycloak.credential.CredentialTypeMetadataContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import java.util.*;
import java.util.stream.Stream;

public class SmsAuthCredentialProvider implements CredentialProvider<SmsAuthCredentialModel>, CredentialInputValidator, CredentialInputUpdater {

    protected final KeycloakSession session;

    public SmsAuthCredentialProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!(input instanceof UserCredentialModel)) {
            return false;
        }
        if (!input.getType().equals(getType())) {
            return false;
        }
        String challengeResponse = input.getChallengeResponse();
        if (challengeResponse == null) {
            return false;
        }
        CredentialModel credentialModel = user.credentialManager().getStoredCredentialById(input.getCredentialId());
        SmsAuthCredentialModel sqcm = getCredentialFromModel(credentialModel);
        return sqcm.getSmsAuthenticatorData().getMobileNumber().equals(challengeResponse);
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return getType().equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        if (!supportsCredentialType(credentialType)) return false;
        
        // Hem credential hem de attribute kontrolü yap
        boolean hasCredential = user.credentialManager().getStoredCredentialsByTypeStream(credentialType).findAny().isPresent();
        String mobileNumber = user.getFirstAttribute("mobile_number");
        
        return hasCredential && mobileNumber != null && !mobileNumber.isEmpty();
    }

    @Override
    public CredentialModel createCredential(RealmModel realm, UserModel user, SmsAuthCredentialModel credentialModel) {
        credentialModel.setCreatedDate(Time.currentTimeMillis());
        return user.credentialManager().createStoredCredential(credentialModel);
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        String mobileNumber = input.getChallengeResponse();
        Optional<CredentialModel> model = user.credentialManager().getStoredCredentialsByTypeStream(input.getType()).findFirst();
        if (model.isPresent()) {
            CredentialModel credentialModel = model.get();
            deleteCredential(realm, user, credentialModel.getId());
            createCredential(realm, user, SmsAuthCredentialModel.createSmsAuthenticator(mobileNumber));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean deleteCredential(RealmModel realm, UserModel user, String credentialId) {
        return user.credentialManager().removeStoredCredentialById(credentialId);
    }

    @Override
    public SmsAuthCredentialModel getCredentialFromModel(CredentialModel model) {
        return SmsAuthCredentialModel.createFromModel(model);
    }

    @Override
    public CredentialTypeMetadata getCredentialTypeMetadata(CredentialTypeMetadataContext metadataContext) {
        return CredentialTypeMetadata.builder()
                .type(getType())
                .category(CredentialTypeMetadata.Category.TWO_FACTOR)
                .displayName("smsAuthenticator")
                .helpText("smsPhoneUpdate")
                .createAction(PhoneNumberRequiredAction.PROVIDER_ID)
                .removeable(true)
                .build(session);
    }

    @Override
    public String getType() {
        return SmsAuthCredentialModel.TYPE;
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return Stream.empty();
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {}
}
