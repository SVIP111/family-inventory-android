package com.family.inventory.data

import kotlinx.serialization.Serializable

/** 位置：与后端 store.go 的 Location 一一对应，parentId 为空表示根位置。 */
@Serializable
data class Location(
    val id: String = "",
    val parentId: String = "",
    val name: String = "",
    val imageFile: String = "",
    val createdAt: String = ""
)

/** 物品：与后端 store.go 的 Item 一一对应。 */
@Serializable
data class Item(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val locationId: String = "",
    val locationName: String = "",
    val quantity: Int = 1,
    val barcode: String = "",
    val tags: List<String> = emptyList(),
    val imageFile: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class AuthStatus(
    val registered: Boolean = false,
    val authenticated: Boolean = false
)

@Serializable
data class AuthResult(
    val token: String = "",
    val username: String = ""
)

@Serializable
data class Stats(
    val items: Int = 0,
    val locations: Int = 0,
    val withImage: Int = 0
)

@Serializable
data class ImportError(
    val row: Int = 0,
    val name: String = "",
    val reason: String = ""
)

@Serializable
data class ImportResult(
    val added: Int = 0,
    val failed: Int = 0,
    val errors: List<ImportError> = emptyList()
)

@Serializable
data class ApiError(val error: String = "")

/** 新增/编辑物品时提交的表单字段（文件名与后端 FormValue 对齐）。 */
data class ItemForm(
    val name: String,
    val description: String,
    val locationId: String,
    val locationName: String,
    val barcode: String,
    val tags: String,
    val quantity: Int
)

/** 汇总分片：饼图/柱状图一段。count = 物品件数，quantity = 数量合计。 */
@Serializable
data class SummarySegment(
    val name: String = "",
    val count: Int = 0,
    val quantity: Int = 0
)

/** 数量排行里的一件物品（后端只回 id/name/quantity/location，不带整条物品）。 */
@Serializable
data class TopItem(
    val id: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val location: String = ""
)

/** GET /api/summary：仪表盘数据（位置/标签分布 + 数量 Top10）。 */
@Serializable
data class Summary(
    val byLocation: List<SummarySegment> = emptyList(),
    val byTag: List<SummarySegment> = emptyList(),
    val topItems: List<TopItem> = emptyList()
)

/** PUT /api/tags/rename 的返回：ok + 改名后的全部标签。 */
@Serializable
data class TagRenameResult(
    val ok: Boolean = false,
    val tags: List<String> = emptyList()
)

/** GET /api/version（免鉴权）：服务端在 main.go 里以 const Version 为准。 */
@Serializable
data class ServerVersion(
    val version: String = ""
)
