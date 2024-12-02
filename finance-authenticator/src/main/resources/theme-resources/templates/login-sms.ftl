<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
	<#if section = "header">
		${msg("smsAuthTitle",realm.displayName)}
	<#elseif section = "form">
		<div class="${properties.kcFormGroupClass!}">
			<!-- Ana Form -->
			<form id="kc-sms-code-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
				<div class="${properties.kcFormGroupClass!}">
					<div class="${properties.kcLabelWrapperClass!}">
						<label for="code" class="${properties.kcLabelClass!}">${msg("smsAuthLabel")}</label>
					</div>
					<div class="${properties.kcInputWrapperClass!}">
						<input type="text" id="code" name="code" class="${properties.kcInputClass!}" autofocus required/>
						<div id="countdown"></div>
					</div>
				</div>

				<div class="${properties.kcFormGroupClass!}">
					<div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
						<input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
							   type="submit" value="${msg("doSubmit")}"/>
					</div>
				</div>
			</form>

			<!-- Yeniden Gönder Butonu -->
			<div class="${properties.kcFormGroupClass!}" id="resendButtonContainer">
				<form id="kc-sms-resend-form" action="${url.loginAction}" method="post">
					<input type="hidden" name="resend" value="true"/>
					<button id="resendButton" 
							class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!}" 
							type="submit">
						${msg("smsAuthResend")}
					</button>
				</form>
			</div>
		</div>

		<script>
			document.addEventListener('DOMContentLoaded', function() {
				const resendButtonContainer = document.getElementById('resendButtonContainer');
				const countdown = document.getElementById('countdown');
				const expirationTime = new Date().getTime() + (${ttl!"120"} * 1000);

				resendButtonContainer.style.display = 'none';

				function updateCountdown() {
					const now = new Date().getTime();
					const timeLeft = expirationTime - now;

					if (timeLeft <= 0) {
						countdown.textContent = "${msg("smsAuthTimeExpired")}";
						resendButtonContainer.style.display = 'block';
						return;
					}

					const minutes = Math.floor((timeLeft % (1000 * 60 * 60)) / (1000 * 60));
					const seconds = Math.floor((timeLeft % (1000 * 60)) / 1000);
					countdown.textContent = `${minutes}:${seconds < 10 ? '0' : ''}${seconds}`;
				}

				updateCountdown();
				const interval = setInterval(updateCountdown, 1000);
			});
		</script>
	<#elseif section = "info" >
		${msg("smsAuthInstruction")}
	</#if>
</@layout.registrationLayout>
