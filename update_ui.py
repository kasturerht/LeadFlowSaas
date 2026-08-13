import re

with open('app/src/main/java/com/nexaleads/app/components/Customer360BottomSheet.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add orders to Customer360BottomSheet signature and collection
content = content.replace(
    'val interactions by viewModel.interactions.collectAsStateWithLifecycle()',
    'val interactions by viewModel.interactions.collectAsStateWithLifecycle()\n    val orders by viewModel.orders.collectAsStateWithLifecycle()'
)

# 2. Update OrdersTab call
content = content.replace(
    'OrdersTab(leads = leads, onOrderClick = onOrderCardClick)',
    'OrdersTab(orders = orders)'
)

# 3. Rewrite OrdersTab definition
old_tab_regex = r'fun OrdersTab\(leads: List<Lead>, onOrderClick: \(Lead\) -> Unit\) \{.*?\}'
new_tab = """fun OrdersTab(orders: List<com.nexaleads.app.data.model.Order>) {
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
                    if (order.createdAt.isNotEmpty()) {
                        val displayDate = if (order.createdAt.length >= 10) order.createdAt.substring(0, 10) else order.createdAt
                        Text(displayDate, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
                Text("₹${order.orderAmountNum}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ModernViolet)
            }
        }
    }
}"""
content = re.sub(old_tab_regex, new_tab, content, flags=re.DOTALL)

with open('app/src/main/java/com/nexaleads/app/components/Customer360BottomSheet.kt', 'w', encoding='utf-8') as f:
    f.write(content)
