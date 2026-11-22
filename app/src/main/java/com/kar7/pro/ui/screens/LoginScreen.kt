package com.kar7.pro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.kar7.pro.device.DeviceIdentity
import com.kar7.pro.ui.components.Kar7ErrorBanner
import com.kar7.pro.ui.components.Kar7GlassCard
import com.kar7.pro.ui.components.Kar7GoldButton
import com.kar7.pro.ui.components.Kar7LogoEmblem
import com.kar7.pro.ui.components.Kar7OutlinedGoldButton
import com.kar7.pro.ui.components.Kar7ResponsiveContainer
import com.kar7.pro.ui.theme.JetBrainsMonoFontFamily
import com.kar7.pro.ui.theme.Kar7Colors

@Composable
fun LoginScreen(
    deviceIdentity: DeviceIdentity?,
    isLoggingIn: Boolean,
    errorMessage: String?,
    onKeyChanged: () -> Unit,
    onLoginClick: (String) -> Unit,
    onBuyKeyClick: () -> Unit,
    onSupportClick: () -> Unit
) {
    var keyText by rememberSaveable { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Kar7Colors.DeepBlack)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Kar7ResponsiveContainer(maxWidth = 480.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Kar7LogoEmblem(size = 84.dp)

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Kar7Colors.MetallicGold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(id = R.string.login_welcome),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Kar7Colors.PureWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(id = R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kar7Colors.LightGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Kar7GlassCard {
                        Text(
                            text = stringResource(id = R.string.login_key_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = Kar7Colors.SoftGold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = keyText,
                            onValueChange = {
                                keyText = it
                                if (errorMessage != null) onKeyChanged()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_key_input"),
                            enabled = !isLoggingIn,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.login_key_placeholder),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = Kar7Colors.DimGray
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = Kar7Colors.MetallicGold
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { keyVisible = !keyVisible },
                                    modifier = Modifier.testTag("toggle_key_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (keyVisible) {
                                            stringResource(id = R.string.cd_hide_key)
                                        } else {
                                            stringResource(id = R.string.cd_show_key)
                                        },
                                        tint = Kar7Colors.LightGray
                                    )
                                }
                            },
                            visualTransformation = if (keyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Kar7Colors.PureWhite
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onLoginClick(keyText) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Kar7Colors.MetallicGold,
                                unfocusedBorderColor = Kar7Colors.SubtleGoldBorder,
                                focusedContainerColor = Kar7Colors.DarkCharcoal,
                                unfocusedContainerColor = Kar7Colors.DarkCharcoal,
                                cursorColor = Kar7Colors.MetallicGold
                            )
                        )

                        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                            Column {
                                Spacer(modifier = Modifier.height(14.dp))
                                Kar7ErrorBanner(
                                    message = errorMessage.orEmpty(),
                                    modifier = Modifier.testTag("login_error_banner")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Kar7GoldButton(
                            text = if (isLoggingIn) {
                                stringResource(id = R.string.login_authenticating)
                            } else {
                                stringResource(id = R.string.login_button)
                            },
                            icon = Icons.Outlined.LockOpen,
                            isLoading = isLoggingIn,
                            onClick = { onLoginClick(keyText) },
                            modifier = Modifier.testTag("login_submit_button")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (deviceIdentity != null) {
                        Kar7GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Kar7Colors.MetallicGold
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Device Registration",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Kar7Colors.SoftGold
                                    )
                                    Text(
                                        text = deviceIdentity.deviceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Kar7Colors.PureWhite
                                    )
                                    Text(
                                        text = "ID: ${deviceIdentity.maskedDeviceId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Kar7Colors.LightGray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Kar7OutlinedGoldButton(
                            text = stringResource(id = R.string.action_buy_key),
                            icon = Icons.Outlined.ShoppingCart,
                            onClick = onBuyKeyClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("login_buy_key_button")
                        )
                        Kar7OutlinedGoldButton(
                            text = stringResource(id = R.string.action_support),
                            icon = Icons.Outlined.SupportAgent,
                            onClick = onSupportClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("login_support_button")
                        )
                    }
                }
            }
        }
    }
}
