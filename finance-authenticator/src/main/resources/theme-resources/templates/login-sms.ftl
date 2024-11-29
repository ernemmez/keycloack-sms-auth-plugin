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
			<div class="${properties.kcFormGroupClass!}">
				<form id="kc-sms-resend-form" action="${url.loginAction}" method="post">
					<input type="hidden" name="resend" value="true"/>
					<button id="resendButton" 
							class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!}" 
							type="submit" 
							disabled>
						${msg("smsAuthResend")}
					</button>
				</form>
				<div id="timer" class="timer-text">
					${msg("smsAuthTimeRemaining")}: <span id="countdown"></span>
				</div>
			</div>
		</div>

		<script>
			let timerInterval;
			
			function startTimer(duration) {
				clearInterval(timerInterval);
				
				let timer = duration;
				const countdown = document.getElementById('countdown');
				const resendButton = document.getElementById('resendButton');
				
				function updateTimer() {
					const minutes = Math.floor(timer / 60);
					const seconds = timer % 60;
					
					const minutesStr = minutes < 10 ? '0' + minutes : minutes;
					const secondsStr = seconds < 10 ? '0' + seconds : seconds;
					
					countdown.textContent = minutesStr + ':' + secondsStr;

					if (--timer < 0) {
						clearInterval(timerInterval);
						countdown.textContent = "${msg("smsAuthTimeExpired")}";
						resendButton.disabled = false;
					}
				}
				
				updateTimer();
				timerInterval = setInterval(updateTimer, 1000);
			}

			// Sayfa yüklendiğinde sayacı başlat
			window.onload = function () {
				const minutes = parseInt('${minutes!"2"}');
				const seconds = parseInt('${seconds!"0"}');
				const totalSeconds = (minutes * 60) + seconds;
				startTimer(totalSeconds);
				
				document.getElementById('kc-sms-resend-form').addEventListener('submit', function() {
					startTimer(totalSeconds);
					document.getElementById('resendButton').disabled = true;
				});
			}
		</script>
	<#elseif section = "info" >
		${msg("smsAuthInstruction")}
	</#if>
</@layout.registrationLayout>
