<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("registerDetailsTitle")}
    <#elseif section = "form">
        <form id="kc-register-details-form" action="${url.loginAction}" method="post">
            <div class="form-group">
                <label for="firstName">${msg("firstName")}</label>
                <input type="text" id="firstName" name="firstName" class="form-control" required />
            </div>
            <div class="form-group">
                <label for="lastName">${msg("lastName")}</label>
                <input type="text" id="lastName" name="lastName" class="form-control" required />
            </div>
            <div class="form-group">
                <label for="email">${msg("email")}</label>
                <input type="email" id="email" name="email" class="form-control" required />
            </div>
            <div class="form-group">
                <button type="submit" class="btn btn-primary btn-block btn-lg">
                    ${msg("doSubmit")}
                </button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>