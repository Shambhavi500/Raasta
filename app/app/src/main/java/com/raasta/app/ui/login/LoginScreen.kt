package com.raasta.app.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.auth.DemoAuth
import com.raasta.app.ui.theme.RaastaTheme

@Composable
fun LoginScreen(onSignedIn: (phone: String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    var otpSent by rememberSaveable { mutableStateOf(false) }
    var otp by rememberSaveable { mutableStateOf("") }
    var otpWrong by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            DemoBanner()

            if (!otpSent) {
                PhoneStep(
                    phone = phone,
                    onPhoneChange = { phone = it.filter(Char::isDigit).take(DemoAuth.PHONE_LENGTH) },
                    onSendOtp = { otpSent = true },
                )
            } else {
                OtpStep(
                    phone = phone,
                    otp = otp,
                    otpWrong = otpWrong,
                    onOtpChange = {
                        otp = it.filter(Char::isDigit).take(DemoAuth.OTP_LENGTH)
                        otpWrong = false
                    },
                    onVerify = {
                        if (DemoAuth.verifyOtp(otp)) onSignedIn(phone) else otpWrong = true
                    },
                    onChangeNumber = {
                        otpSent = false
                        otp = ""
                        otpWrong = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DemoBanner() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                text = stringResource(R.string.demo_banner, DemoAuth.DEMO_OTP),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PhoneStep(phone: String, onPhoneChange: (String) -> Unit, onSendOtp: () -> Unit) {
    val valid = DemoAuth.isValidMobile(phone)
    val showError = phone.length == DemoAuth.PHONE_LENGTH && !valid

    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text(stringResource(R.string.login_phone_label)) },
        prefix = { Text(stringResource(R.string.login_phone_prefix) + " ") },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(R.string.login_phone_invalid)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onSendOtp,
        enabled = valid,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(stringResource(R.string.login_send_otp))
    }
}

@Composable
private fun OtpStep(
    phone: String,
    otp: String,
    otpWrong: Boolean,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    Text(
        text = stringResource(R.string.login_otp_sent_to, DemoAuth.maskPhone(phone)),
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = otp,
        onValueChange = onOtpChange,
        label = { Text(stringResource(R.string.login_otp_label)) },
        singleLine = true,
        isError = otpWrong,
        supportingText = if (otpWrong) {
            { Text(stringResource(R.string.login_otp_wrong)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onVerify,
        enabled = otp.length == DemoAuth.OTP_LENGTH,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(stringResource(R.string.login_verify))
    }
    TextButton(onClick = onChangeNumber) {
        Text(stringResource(R.string.login_change_number))
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    RaastaTheme { LoginScreen(onSignedIn = {}) }
}
