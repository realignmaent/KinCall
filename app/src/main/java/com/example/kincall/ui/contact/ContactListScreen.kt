package com.example.kincall.ui.contact

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kincall.KinCallApp
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact
import kotlinx.coroutines.launch

/**
 * 联系人列表页
 * 给子女配置亲情通讯录用的界面
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
    // 存储每个联系人的别名列表
    var aliasMap by remember { mutableStateOf<Map<Long, List<Alias>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    // 删除确认弹窗
    var showDeleteDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    // 加载所有联系人的别名
    LaunchedEffect(contacts) {
        val map = mutableMapOf<Long, List<Alias>>()
        contacts.forEach { contact ->
            map[contact.id] = app.contactRepository.getAliases(contact.id)
        }
        aliasMap = map
    }

    // 删除联系人
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
            // 空状态提示
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

                // 底部统计
                item {
                    Text(
                        text = "共 ${contacts.size} 个联系人 · 💡 多填别名，匹配更准",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 单个联系人卡片
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 姓名 + 关系
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!contact.relation.isNullOrBlank()) {
                    Text(
                        text = "（${contact.relation}）",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 手机号
            Text(
                text = contact.phone,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 别名列表
            if (aliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "别名：${aliases.joinToString("、") { it.text }}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
