package com.example.kincall.ui.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kincall.KinCallApp
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact
import kotlinx.coroutines.launch

/**
 * 联系人列表页
 *
 * 优化点：
 * - 新增左侧首字母头像圆圈
 * - 关系标签改为药丸形 Surface
 * - 手机号字号放大
 * - 编辑/删除改为 IconButton
 * - 底部提示去掉 emoji
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (Long) -> Unit,
    app: KinCallApp
) {
    val contacts by app.contactRepository.getAllContacts().collectAsState(initial = emptyList())
    var aliasMap by remember { mutableStateOf<Map<Long, List<Alias>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    LaunchedEffect(contacts) {
        val map = mutableMapOf<Long, List<Alias>>()
        contacts.forEach { contact ->
            map[contact.id] = app.contactRepository.getAliases(contact.id)
        }
        aliasMap = map
    }

    // 删除确认弹窗
    if (showDeleteDialog && contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; contactToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除 ${contactToDelete!!.name} 吗？相关别名也会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    contactToDelete?.let { contact ->
                        scope.launch {
                            app.contactRepository.deleteContact(contact)
                        }
                    }
                    showDeleteDialog = false
                    contactToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; contactToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("亲情通讯录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onAddContact) {
                        Icon(Icons.Default.Add, contentDescription = "添加联系人")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有联系人",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击右上角 + 添加第一个联系人",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        aliases = aliasMap[contact.id] ?: emptyList(),
                        onEdit = { onEditContact(contact.id) },
                        onDelete = {
                            contactToDelete = contact
                            showDeleteDialog = true
                        }
                    )
                }

                // 底部统计 - 去掉 emoji
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "共 ${contacts.size} 个联系人 · 多填别名，匹配更准",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个联系人卡片 - 优化版
 *
 * 新增：首字母头像、关系药丸标签、IconButton 操作
 */
@Composable
private fun ContactCard(
    contact: Contact,
    aliases: List<Alias>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：首字母头像圆圈
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.firstOrNull()?.toString() ?: "?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 中间：信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 关系标签 - 药丸形
                    if (!contact.relation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = contact.relation!!,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = contact.phone,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 别名
                if (aliases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "别名：${aliases.joinToString("、") { it.text }}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }

            // 右侧：操作按钮
            Column {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
