package com.nexaleads.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.getCreatedAtString
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.Customer360ViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Customer360BottomSheet(
    initialLead: Lead,
    viewModel: Customer360ViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onLogActivityClick: (Lead) -> Unit,
    onPlaceOrderClick: (Lead) -> Unit,
    onOrderCardClick: (Lead) -> Unit
) {
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val interactions by viewModel.interactions.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val ltv by viewModel.lifetimeValue.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val activeLeadContext by viewModel.activeLeadContext.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(initialLead.phone) {
        viewModel.fetchCustomerData(initialLead.phone, initialLead.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E2E), // Match dark background
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ModernViolet)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // HEADER
                Box(modifier = Modifier.fillMaxWidth()) {
                    Customer360Header(
                        lead = activeLeadContext ?: initialLead,
                        ltv = ltv,
                        totalOrders = orders.size
                    )
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val targetPhone = initialLead.phone.trim()
                                    val rawData = viewModel.diagnoseRawData(targetPhone)
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("diagnostics", rawData)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Copied! Paste it to the chat now.", android.widget.Toast.LENGTH_LONG).show()
                                } catch(e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Diagnose", color = Color.White)
                    }
                }

                // TABS
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = ModernViolet,
                    divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Timeline", fontWeight = FontWeight.Bold, color = if (selectedTab == 0) ModernViolet else Color.White.copy(alpha = 0.5f)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Past Orders", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) ModernViolet else Color.White.copy(alpha = 0.5f)) }
                    )
                }

                // CONTENT
                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    if (selectedTab == 0) {
                        TimelineTab(interactions = interactions)
                    } else {
                        OrdersTab(orders = orders)
                    }
                }

                // ACTION BAR
                Customer360ActionBar(
                    onLogActivity = { (activeLeadContext ?: initialLead).let { onLogActivityClick(it) } },
                    onPlaceOrder = { (activeLeadContext ?: initialLead).let { onPlaceOrderClick(it) } }
                )
            }
        }
    }
}

@Composable
fun Customer360Header(lead: Lead, ltv: Long, totalOrders: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ModernViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = ModernViolet, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(lead.name.ifEmpty { "Unknown Customer" }, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(lead.phone, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBox(title = "LTV", value = "₹$ltv", icon = Icons.Rounded.Storefront)
            StatBox(title = "Orders", value = "$totalOrders", icon = Icons.Rounded.CheckCircle)
        }
    }
}

@Composable
fun StatBox(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = ModernViolet, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun TimelineTab(interactions: List<Interaction>) {
    if (interactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No history available.", color = Color.White.copy(alpha = 0.5f))
        }
        return
    }
    
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val displayFormat = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.US) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(interactions) { interaction ->
            val date = try {
                isoFormat.parse(interaction.timestamp)?.let { displayFormat.format(it) } ?: "Unknown Date"
            } catch(e: Exception) { "Unknown Date" }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Timeline Line & Dot
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(ModernViolet))
                    Box(modifier = Modifier.width(2.dp).height(80.dp).background(Color.White.copy(alpha = 0.1f)))
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Content Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(interaction.statusAfter.ifEmpty { "Note Added" }, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(date, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                    if (interaction.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(interaction.notes, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    if (!interaction.product.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("🛒 ${interaction.product}", color = ModernViolet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun OrdersTab(orders: List<com.nexaleads.app.data.model.Order>) {
    if (orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No orders placed yet.", color = Color.White.copy(alpha = 0.5f))
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(orders) { order ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(ModernViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📦", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.product, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Status: ${order.status}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    val createdAtStr = order.getCreatedAtString()
                    if (createdAtStr.isNotEmpty()) {
                        val displayDate = if (createdAtStr.length >= 10) createdAtStr.substring(0, 10) else createdAtStr
                        Text(displayDate, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
                Text("₹${order.orderAmountNum}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ModernViolet)
            }
        }
    }
}

@Composable
fun Customer360ActionBar(
    onLogActivity: () -> Unit,
    onPlaceOrder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E2E)) // Dark background to match sheet
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onLogActivity,
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Call", fontWeight = FontWeight.Bold)
        }
        
        Button(
            onClick = onPlaceOrder,
            modifier = Modifier.weight(1.5f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ModernViolet),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Order", fontWeight = FontWeight.Bold)
        }
    }
}
