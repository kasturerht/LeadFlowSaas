package com.nexaleads.app.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.getCreatedAtString
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.Customer360ViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ─── Design Tokens (Silicon Valley Light Mode) ────────────────────────────────────────────────────────────
private val SheetBg        = Color(0xFFFFFFFF)
private val CardBg         = Color(0xFFF9FAFB)
private val CardBorder     = Color(0xFFE5E7EB)
private val TextPrimary    = Color(0xFF111827)
private val TextSecondary  = Color(0xFF6B7280)
private val Muted          = Color(0xFF9CA3AF)
private val Subtle         = Color(0xFFF3F4F6)
private val GreenAccent    = Color(0xFF10B981)
private val AmberAccent    = Color(0xFFF59E0B)
private val GradientStart  = Color(0xFF6366F1)
private val GradientEnd    = Color(0xFFA855F7)

private val HeaderGradient = Brush.verticalGradient(
    listOf(Color(0xFFF9FAFB), Color(0xFFFFFFFF))
)
private val VioletGradient = Brush.horizontalGradient(
    listOf(GradientStart, GradientEnd)
)

// ─── Bottom Sheet ─────────────────────────────────────────────────────────────
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
    val leads         by viewModel.leads.collectAsStateWithLifecycle()
    val interactions  by viewModel.interactions.collectAsStateWithLifecycle()
    val orders        by viewModel.orders.collectAsStateWithLifecycle()
    val ltv           by viewModel.lifetimeValue.collectAsStateWithLifecycle()
    val myLtv         by viewModel.myLtv.collectAsStateWithLifecycle()
    val teamLtv       by viewModel.teamLtv.collectAsStateWithLifecycle()
    val myOrdersCount by viewModel.myOrdersCount.collectAsStateWithLifecycle()
    val teamOrdersCount by viewModel.teamOrdersCount.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()
    val activeLead    by viewModel.activeLeadContext.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId

    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(initialLead.phone) {
        viewModel.fetchCustomerData(initialLead.phone, initialLead.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Muted.copy(alpha = 0.5f))
            )
        },
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = ModernViolet, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    Text("Loading customer data…", color = Muted, fontSize = 14.sp)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {

                // ── Hero Header ────────────────────────────────────────────
                CustomerHeroHeader(
                    lead       = activeLead ?: initialLead,
                    ltv        = ltv,
                    totalOrders = orders.size,
                    myLtv      = myLtv,
                    teamLtv    = teamLtv,
                    myOrders   = myOrdersCount,
                    teamOrders = teamOrdersCount,
                    onDiagnose = {
                        // Diagnose button stays accessible via long-press debug
                    },
                    viewModel  = viewModel,
                    initialLead = initialLead
                )

                // ── Segmented Tab Bar ──────────────────────────────────────
                PremiumTabBar(selectedTab = selectedTab, onTabChange = { selectedTab = it })

                // ── Tab Content ────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                ) {
                    if (selectedTab == 0) {
                        TimelineTab(interactions = interactions)
                    } else {
                        OrdersTab(orders = orders, currentUserId = currentUserId)
                    }
                }

                // ── Action Bar ─────────────────────────────────────────────
                PremiumActionBar(
                    onLogActivity = { onLogActivityClick(activeLead ?: initialLead) },
                    onPlaceOrder  = { onPlaceOrderClick(activeLead ?: initialLead) }
                )
            }
        }
    }
}

// ─── Hero Header & Integrated Stats ───────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CustomerHeroHeader(
    lead: Lead,
    ltv: Long,
    totalOrders: Int,
    myLtv: Long,
    teamLtv: Long,
    myOrders: Int,
    teamOrders: Int,
    onDiagnose: () -> Unit,
    viewModel: Customer360ViewModel,
    initialLead: Lead
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope   = rememberCoroutineScope()
    var showDiagnose by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Horizontal Profile Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(VioletGradient)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showDiagnose = !showDiagnose }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lead.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = GradientStart
                        )
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                // Name & Phone
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lead.name.ifEmpty { "Unknown Customer" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = lead.phone,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                // Diagnose Button (hidden by default)
                if (showDiagnose) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val rawData = viewModel.diagnoseRawData(initialLead.phone.trim())
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("diagnostics", rawData))
                                    android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("🔍", fontSize = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Integrated Stats Card ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                // Top Row: LTV and Orders
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lifetime Value", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold)
                        Text("₹$ltv", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(CardBorder))
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Total Orders", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold)
                        Text("$totalOrders", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                }

                // Revenue Split Section (if applicable)
                if (myLtv > 0 || teamLtv > 0) {
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    Spacer(Modifier.height(12.dp))
                    
                    val myRatio = if (ltv > 0) myLtv.toFloat() / ltv.toFloat() else 0f
                    val animRatio by animateFloatAsState(targetValue = myRatio, animationSpec = tween(durationMillis = 900), label = "revenueSplit")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Revenue Split", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
                        if (myOrders > 0 && teamOrders > 0) {
                            Text("${(myRatio * 100).toInt()}% yours", fontSize = 11.sp, color = GradientStart, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Animated Split Bar
                    Box(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CardBorder)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(animRatio).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(VioletGradient)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Mini Split Rows
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (myLtv > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(GradientStart))
                                Text("You (₹$myLtv)", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (teamLtv > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(TextSecondary))
                                Text("Team (₹$teamLtv)", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // ── Slim Pending Inquiries Banner ──
            val closedStatuses = listOf(
                "Order Placed", "Cancelled", "RTO", "Delivered",
                com.nexaleads.app.Constants.STATUS_ORDER_PLACED, 
                com.nexaleads.app.Constants.STATUS_ORDER_CANCELLED, 
                com.nexaleads.app.Constants.STATUS_RTO, 
                com.nexaleads.app.Constants.STATUS_DELIVERED
            )
            val pendingLeads = viewModel.leads.value.filter { 
                it.assignedTo == viewModel.currentUserId && 
                !closedStatuses.contains(it.status) && 
                !it.archived &&
                it.id != initialLead.id
            }
            if (pendingLeads.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (pendingLeads.size > 1) "${pendingLeads.size} pending inquiries found" else "1 pending inquiry found",
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitRow(
    dot: Color,
    label: String,
    orders: Int,
    amount: Long,
    isHighlight: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(dot.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$orders order${if (orders != 1) "s" else ""}", fontSize = 10.sp, color = dot, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text  = "₹$amount",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = if (isHighlight) GradientStart else TextPrimary
        )
    }
}

// ─── Segmented Tab Bar ────────────────────────────────────────────────────────
@Composable
private fun PremiumTabBar(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf("Timeline", "Orders")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) VioletGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onTabChange(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = label,
                    fontSize   = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color      = if (isSelected) Color.White else Muted
                )
            }
        }
    }
}

// ─── Timeline Tab ─────────────────────────────────────────────────────────────
@Composable
fun TimelineTab(interactions: List<Interaction>) {
    if (interactions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📭", fontSize = 32.sp)
                Text("No activity yet", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Log a call to start tracking", color = Subtle, fontSize = 12.sp)
            }
        }
        return
    }

    val isoFmt     = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val displayFmt = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.US) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(interactions) { item ->
            val dateStr = try { isoFmt.parse(item.timestamp)?.let { displayFmt.format(it) } ?: "" } catch (e: Exception) { "" }
            val isOrder = item.statusAfter.contains("Order", ignoreCase = true)
            val dotColor = when {
                isOrder -> GreenAccent
                item.statusAfter.contains("Shared Inquiry", ignoreCase = true) -> AmberAccent
                else -> GradientStart
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Vertical timeline track
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(72.dp)
                            .background(
                                Brush.verticalGradient(listOf(dotColor.copy(alpha = 0.3f), Color.Transparent))
                            )
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Content card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text(
                            text       = item.statusAfter.ifEmpty { "Note" },
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary,
                            fontSize   = 14.sp,
                            modifier   = Modifier.weight(1f)
                        )
                        Text(
                            text     = dateStr,
                            color    = Muted,
                            fontSize = 11.sp
                        )
                    }
                    if (item.notes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(item.notes, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (!item.product.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GreenAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🛍", fontSize = 12.sp)
                            Text(item.product!!, color = GreenAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!item.callerName.isNullOrEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("by ${item.callerName}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ─── Orders Tab ───────────────────────────────────────────────────────────────
@Composable
fun OrdersTab(orders: List<com.nexaleads.app.data.model.Order>, currentUserId: String) {
    if (orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🛒", fontSize = 32.sp)
                Text("No orders yet", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Place your first order to start tracking", color = Subtle, fontSize = 12.sp)
            }
        }
        return
    }

    val isoFmt     = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val displayFmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(orders) { order ->
            val isMyOrder  = order.assignedTo == currentUserId
            val statusColor = when (order.status) {
                "Delivered"      -> GreenAccent
                "Dispatched"     -> Color(0xFF3B82F6)
                "Order Placed"   -> AmberAccent
                "Order Cancelled", "Cancelled", "RTO" -> Color(0xFFEF4444)
                else             -> Muted
            }

            val rawAt  = order.getCreatedAtString()
            val dateStr = try {
                if (rawAt.length >= 10) isoFmt.parse(rawAt)?.let { displayFmt.format(it) } ?: rawAt.take(10)
                else rawAt
            } catch (e: Exception) { rawAt.take(10) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(
                        width = 1.dp,
                        color = if (isMyOrder) GradientStart.copy(alpha = 0.35f) else CardBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                // Top row: product + amount
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.product,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(dateStr, fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text       = "₹${order.orderAmountNum}",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Black,
                        color      = TextPrimary
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Bottom row: status chip + ownership badge
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Status chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(order.status, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
                    }

                    // Ownership badge
                    val (badgeColor, badgeText, badgeIcon) = if (isMyOrder) {
                        Triple(GradientStart, "Placed by You", "👤")
                    } else {
                        Triple(Muted, "Team Order", "👥")
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(badgeIcon, fontSize = 10.sp)
                        Text(badgeText, fontSize = 10.sp, color = badgeColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Action Bar ───────────────────────────────────────────────────────────────
@Composable
private fun PremiumActionBar(
    onLogActivity: () -> Unit,
    onPlaceOrder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SheetBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick  = onLogActivity,
            modifier = Modifier.weight(1f).height(50.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            border   = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log Call", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Button(
            onClick  = onPlaceOrder,
            modifier = Modifier
                .weight(1.6f)
                .height(50.dp)
                .background(VioletGradient, RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape  = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Order", fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

// ─── Legacy stubs (keep for backward compat with any external references) ─────
@Composable
fun Customer360Header(lead: Lead, ltv: Long, totalOrders: Int, myLtv: Long, teamLtv: Long, myOrdersCount: Int, teamOrdersCount: Int) {
    // Delegated to CustomerHeroHeader via Customer360BottomSheet
}

@Composable
fun StatBox(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    // Replaced by MetricChip
}

@Composable
fun Customer360ActionBar(onLogActivity: () -> Unit, onPlaceOrder: () -> Unit) {
    PremiumActionBar(onLogActivity = onLogActivity, onPlaceOrder = onPlaceOrder)
}
