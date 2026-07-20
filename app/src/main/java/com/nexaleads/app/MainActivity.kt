package com.nexaleads.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexaleads.app.ui.theme.LeadFlowSaaSTheme
import com.nexaleads.app.ui.viewmodel.AuthState
import com.nexaleads.app.ui.viewmodel.AuthViewModel
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LeadFlowSaaSTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                
                val callingViewModel: CallingViewModel = hiltViewModel()
                val pendingInvoiceLead by callingViewModel.pendingInvoiceLead.collectAsStateWithLifecycle()
                val telecallerContact by callingViewModel.telecallerContact.collectAsStateWithLifecycle()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    pendingInvoiceLead?.let { lead ->
                        com.nexaleads.app.components.InvoiceDialog(
                            lead = lead,
                            supportNumber = telecallerContact,
                            onLogAction = { action, notes ->
                                callingViewModel.logAction(lead.id, action, notes)
                            },
                            onDismiss = {
                                callingViewModel.clearPendingInvoice()
                            }
                        )
                    }

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(
                                authState = authState,
                                onNavigate = { dest ->
                                    navController.navigate(dest) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            val authStateFlow = authState
                            if (authStateFlow is AuthState.Authenticated) {
                                LaunchedEffect(authStateFlow.userId) {
                                    callingViewModel.initialize(authStateFlow.userId, authStateFlow.userName, authStateFlow.contactNumber, authStateFlow.orgId)
                                }
                                val leads by callingViewModel.leads.collectAsStateWithLifecycle()
                                
                                DashboardScreen(
                                    callerName = authStateFlow.userName,
                                    viewModel = callingViewModel,
                                    leads = leads,
                                    onSelectCategory = { filter ->
                                        navController.navigate("callingList/$filter")
                                    },
                                    onHistoryClick = {
                                        navController.navigate("history")
                                    },
                                    onLogout = {
                                        authViewModel.logout()
                                        navController.navigate("login") {
                                            popUpTo("dashboard") { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }

                        composable("callingList/{filter}") { backStackEntry ->
                            val filter = backStackEntry.arguments?.getString("filter") ?: "PENDING"
                            val authStateFlow = authState
                            if (authStateFlow is AuthState.Authenticated) {
                                LaunchedEffect(authStateFlow.userId) {
                                    callingViewModel.initialize(authStateFlow.userId, authStateFlow.userName, authStateFlow.contactNumber, authStateFlow.orgId)
                                }
                                val leads by callingViewModel.leads.collectAsStateWithLifecycle()
                                
                                TodayCallingListScreen(
                                    currentUserId = authStateFlow.userId,
                                    callerName = authStateFlow.userName,
                                    filter = filter,
                                    fullLeadsList = leads,
                                    onBack = { navController.popBackStack() },
                                    onLogout = {
                                        authViewModel.logout()
                                        navController.navigate("login") {
                                            popUpTo("callingList") { inclusive = true }
                                        }
                                    },
                                    viewModel = callingViewModel
                                )
                            }
                        }

                        composable("history") {
                            val authStateFlow = authState
                            if (authStateFlow is AuthState.Authenticated) {
                                LaunchedEffect(authStateFlow.userId) {
                                    callingViewModel.initialize(authStateFlow.userId, authStateFlow.userName, authStateFlow.contactNumber, authStateFlow.orgId)
                                }
                                val leads by callingViewModel.leads.collectAsStateWithLifecycle()
                                
                                HistoryScreen(
                                    currentUserId = authStateFlow.userId,
                                    orgId = authStateFlow.orgId,
                                    fullLeadsList = leads,
                                    onBack = { navController.popBackStack() },
                                    onLogout = {
                                        authViewModel.logout()
                                        navController.navigate("login") {
                                            popUpTo("history") { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(authState: AuthState, onNavigate: (String) -> Unit) {
    val logoAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val logoOffsetY = remember { androidx.compose.animation.core.Animatable(20f) }
    val dotScale = remember { androidx.compose.animation.core.Animatable(0f) }
    val subtitleAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val subtitleOffsetY = remember { androidx.compose.animation.core.Animatable(15f) }
    
    var animationFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        
        launch {
            logoAlpha.animateTo(1f, androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.EaseOutExpo))
        }
        launch {
            logoOffsetY.animateTo(0f, androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.EaseOutExpo))
        }

        kotlinx.coroutines.delay(500)

        launch {
            dotScale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f))
        }

        kotlinx.coroutines.delay(300)

        launch {
            subtitleAlpha.animateTo(1f, androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.EaseOutQuart))
        }
        launch {
            subtitleOffsetY.animateTo(0f, androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.EaseOutQuart))
        }

        kotlinx.coroutines.delay(1200)

        launch {
            logoAlpha.animateTo(0f, androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.EaseInCubic))
            logoOffsetY.animateTo(-10f, androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.EaseInCubic))
            dotScale.animateTo(0f, androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.EaseInCubic))
            subtitleAlpha.animateTo(0f, androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.EaseInCubic))
            subtitleOffsetY.animateTo(-5f, androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.EaseInCubic))
        }

        kotlinx.coroutines.delay(400)
        animationFinished = true
    }

    LaunchedEffect(animationFinished, authState) {
        if (animationFinished) {
            when (authState) {
                is AuthState.Authenticated -> onNavigate("dashboard")
                is AuthState.Unauthenticated -> onNavigate("login")
                else -> {} 
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(com.nexaleads.app.ui.theme.BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "LEADFLOW",
                    modifier = Modifier.offset(y = logoOffsetY.value.dp).alpha(logoAlpha.value),
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = com.nexaleads.app.ui.theme.TextPrimary)
                )
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp, start = 2.dp)
                        .size(6.dp)
                        .scale(dotScale.value)
                        .background(com.nexaleads.app.ui.theme.ModernViolet, CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "CRM STATION",
                modifier = Modifier.offset(y = subtitleOffsetY.value.dp).alpha(subtitleAlpha.value),
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = com.nexaleads.app.ui.theme.TextSecondary)
            )
        }
        
        // Developer Credit
        Text(
            text = "Developed by Rohit Kasture",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(subtitleAlpha.value),
            style = TextStyle(
                fontSize = 11.sp, 
                fontWeight = FontWeight.SemiBold, 
                letterSpacing = 1.sp, 
                color = com.nexaleads.app.ui.theme.TextSecondary.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
fun LoginScreen(authViewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            isSubmitting = false
            onLoginSuccess()
        }
        if (authState is AuthState.Unauthenticated && isSubmitting) {
            isSubmitting = false
            Toast.makeText(context, (authState as AuthState.Unauthenticated).error ?: "Login failed", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(com.nexaleads.app.ui.theme.BackgroundLight), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.05f))
                .background(com.nexaleads.app.ui.theme.SurfaceLight, RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("LEADFLOW", fontSize = 22.sp, fontWeight = FontWeight.Black, color = com.nexaleads.app.ui.theme.TextPrimary, letterSpacing = 4.sp)
                Box(modifier = Modifier.padding(bottom = 5.dp, start = 2.dp).size(5.dp).background(com.nexaleads.app.ui.theme.ModernViolet, CircleShape))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Staff Workspace Login", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = com.nexaleads.app.ui.theme.TextSecondary, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(36.dp))

            OutlinedTextField(
                value = emailInput, onValueChange = { emailInput = it }, label = { Text("Workspace Email") },
                singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = com.nexaleads.app.ui.theme.TextPrimary, unfocusedTextColor = com.nexaleads.app.ui.theme.TextPrimary,
                    focusedBorderColor = com.nexaleads.app.ui.theme.ModernViolet, unfocusedBorderColor = com.nexaleads.app.ui.theme.BorderSubtle,
                    focusedLabelColor = com.nexaleads.app.ui.theme.ModernViolet, unfocusedLabelColor = com.nexaleads.app.ui.theme.TextSecondary,
                    focusedContainerColor = com.nexaleads.app.ui.theme.AccentSurface, unfocusedContainerColor = com.nexaleads.app.ui.theme.SurfaceLight
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = passwordInput, onValueChange = { passwordInput = it }, label = { Text("Access Key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = com.nexaleads.app.ui.theme.TextPrimary, unfocusedTextColor = com.nexaleads.app.ui.theme.TextPrimary,
                    focusedBorderColor = com.nexaleads.app.ui.theme.ModernViolet, unfocusedBorderColor = com.nexaleads.app.ui.theme.BorderSubtle,
                    focusedLabelColor = com.nexaleads.app.ui.theme.ModernViolet, unfocusedLabelColor = com.nexaleads.app.ui.theme.TextSecondary,
                    focusedContainerColor = com.nexaleads.app.ui.theme.AccentSurface, unfocusedContainerColor = com.nexaleads.app.ui.theme.SurfaceLight
                )
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (emailInput.isEmpty() || passwordInput.isEmpty()) {
                        Toast.makeText(context, "Please enter details!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSubmitting = true
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(emailInput.trim(), passwordInput)
                        .addOnSuccessListener {
                            authViewModel.checkCurrentUser()
                        }.addOnFailureListener {
                            isSubmitting = false
                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.nexaleads.app.ui.theme.ModernViolet),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = com.nexaleads.app.ui.theme.CleanWhite, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign In", color = com.nexaleads.app.ui.theme.CleanWhite, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}





