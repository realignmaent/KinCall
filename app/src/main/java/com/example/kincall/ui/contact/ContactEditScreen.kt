package com.example.kincall.ui.contact

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kincall.KinCallApp
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact
import kotlinx.coroutines.launch

/**
 * 添加/编辑联系人页
 * 子女用来配置亲情通讯录的界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactEditScreen(
    contactId: Long? = null,  // null = 新建，非 null = 编辑
    onBack: () -> Unit,
    app: KinCallApp
) {
    val scope = rememberCoroutineScope()
    val isEditing = contactId != null && contactId > 0

    // 表单状态
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf<List<String>>(emptyList()) }
    var aliasInput by remember { mutableStateOf("") }
    var useCustomRelation by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    // 常用关系选项
    val commonRelations = listOf("大儿子", "二儿子", "大闺女", "二闺女", "老伴", "邻居", "医生", "其他")

    // 验证状态
    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    // 如果是编辑模式，加载已有数据
    LaunchedEffect(contactId) {
        if (isEditing && !isLoaded) {
            val contact = app.contactRepository.getContactById(contactId!!)
            if (contact != null) {
                name = contact.name
                relation = contact.relation ?: ""
                phone = contact.phone
                // 判断是否为自定义关系
                useCustomRelation = relation.isNotBlank() && relation !in commonRelations.filter { it != "其他" }
                // 加载别名
                val existingAliases = app.contactRepository.getAliases(contact.id)
                aliases = existingAliases.map { it.text }
            }
            isLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑联系人" else "添加联系人") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 姓名
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("姓名 *") },
                isError = nameError,
                supportingText = if (nameError) {{ Text("请输入姓名") }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 关系
            Text("关系", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 关系快速选择 Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                commonRelations.forEach { option ->
                    val isSelected = if (option == "其他") {
                        useCustomRelation
                    } else {
                        relation == option && !useCustomRelation
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (option == "其他") {
                                useCustomRelation = true
                                relation = ""
                            } else {
                                useCustomRelation = false
                                relation = option
                            }
                        },
                        label = { Text(option) }
                    )
                }
            }

            // 自定义关系输入框（选"其他"时显示）
            if (useCustomRelation) {
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("请输入关系") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 手机号
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; phoneError = false },
                label = { Text("手机号 *") },
                isError = phoneError,
                supportingText = if (phoneError) {{ Text("请输入手机号") }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 别名管理
            Text("别名（老人怎么叫这个人？）", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 已添加的别名 Chips
            if (aliases.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    aliases.forEach { alias ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(alias) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { aliases = aliases - alias },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除别名",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 添加别名输入框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("输入别名...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmed = aliasInput.trim()
                        if (trimmed.isNotBlank() && trimmed !in aliases) {
                            aliases = aliases + trimmed
                            aliasInput = ""
                        }
                    },
                    enabled = aliasInput.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            // 提示文案
            Text(
                text = "💡 老人平时怎么叫这个人？比如老大、大宝、儿子，都填进来。多填几个匹配更准。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 保存和取消按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        // 验证
                        nameError = name.isBlank()
                        phoneError = phone.isBlank()
                        if (nameError || phoneError) return@Button

                        scope.launch {
                            if (isEditing) {
                                // 更新已有联系人
                                val existing = app.contactRepository.getContactById(contactId!!)
                                if (existing != null) {
                                    val updated = existing.copy(
                                        name = name.trim(),
                                        relation = relation.trim().ifBlank { null },
                                        phone = phone.trim(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    app.contactRepository.updateContact(updated)
                                    // 删除旧别名，重新添加
                                    val oldAliases = app.contactRepository.getAliases(existing.id)
                                    oldAliases.forEach { app.contactRepository.deleteAlias(it) }
                                    aliases.forEach { aliasText ->
                                        app.contactRepository.insertAlias(
                                            Alias(
                                                contactId = existing.id,
                                                text = aliasText,
                                                pinyin = aliasText  // MVP 直接用原文，不做拼音转换
                                            )
                                        )
                                    }
                                }
                            } else {
                                // 新建联系人
                                val contact = Contact(
                                    name = name.trim(),
                                    relation = relation.trim().ifBlank { null },
                                    phone = phone.trim()
                                )
                                val newId = app.contactRepository.insertContact(contact)
                                aliases.forEach { aliasText ->
                                    app.contactRepository.insertAlias(
                                        Alias(
                                            contactId = newId,
                                            text = aliasText,
                                            pinyin = aliasText  // MVP 直接用原文
                                        )
                                    )
                                }
                            }
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
