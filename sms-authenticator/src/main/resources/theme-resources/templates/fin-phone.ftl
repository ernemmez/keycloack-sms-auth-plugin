<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("phoneLoginTitle")}
    <#elseif section = "form">
        <form id="kc-phone-login-form" action="${url.loginAction}" method="post">
            <input type="hidden" name="redirect_uri" value="${redirectUri}">
            <div class="form-group">
                <label for="phone_number">${msg("phoneNumber")}</label>
                <input type="tel" id="phone_number" name="phone_number" class="form-control" required />
            </div>
            <div class="form-group">
                <label for="password">${msg("password")}</label>
                <input type="password" id="password" name="password" class="form-control" required />
            </div>
            <div class="form-group">
                <button type="submit" class="btn btn-primary btn-block btn-lg">
                    ${msg("doSubmit")}
                </button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>