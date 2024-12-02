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
			<div class="${properties.kcFormGroupClass!}" id="resendButtonContainer" style="display: none;">
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
			const countdownScript = `
				let minutes = ${minutes!'2'};
				let seconds = ${seconds!'0'};

				function updateCountdown() {
					countdown.textContent = minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
				}

				const countdown = document.getElementById('countdown');
				const resendButtonContainer = document.getElementById('resendButtonContainer');
				
				updateCountdown();
				const countdownTimer = setInterval(() => {
					if (seconds > 0) {
							seconds--;
					} else if (minutes > 0) {
							minutes--;
							seconds = 59;
					} else {
							clearInterval(countdownTimer);
							// Süre bittiğinde butonu göster
							resendButtonContainer.style.display = 'block';
					}
					updateCountdown();
				}, 1000);
			`;

			// Script'i eval ile çalıştır
			eval(countdownScript);
		</script>
	<#elseif section = "info" >
		${msg("smsAuthInstruction")}
	</#if>
</@layout.registrationLayout>
