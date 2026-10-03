package com.weightdiary.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.home.components.RecordRowItem
import com.weightdiary.app.ui.home.components.SwipeToDeleteRow
import com.weightdiary.app.ui.sheet.components.SheetTitle
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import java.time.Instant

/**
 * 「全部记录」弹窗。75% 屏高。
 *
 * 一次性加载全量，**不引入 Paging 3**（设计规范 §5.2）。1000 条量级下 `LazyColumn`
 * 只组合可见项，滚动不会掉帧；分页带来的复杂度在这个数据规模上不划算。
 *
 * [snackbarHostState] 由外部传入并**在这个弹窗内部渲染** —— `ModalBottomSheet` 是一个
 * 独立的窗口，主窗口里的 Snackbar 会被它完全盖住，删除后的「撤销」就永远点不到。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRecordsSheet(
    rows: List<RecordRow>,
    /** 非空时**弹窗内容换成编辑表单**，而不是关掉再弹一个 —— 后者会「收回又弹出」，很乱 */
    editing: RecordRow?,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onRowClick: (RecordRow) -> Unit,
    onRowDelete: (RecordRow) -> Unit,
    onEditSave: (weightKg: Double, measuredAt: Instant, bodyFatPercent: Double?, note: String?) -> Unit,
    onEditCancel: () -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 展开状态由弹窗持有，而不是每行自己记 —— 同一时刻最多只有一行是展开的，
    // 划开第二行时第一行会自动弹回
    var revealedId by remember { mutableStateOf<Long?>(null) }

    // 进编辑表单前把划开的那行收回去，免得返回列表时它还是敞开状态
    LaunchedEffect(editing) { if (editing != null) revealedId = null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = dimen.radiusSheetTop, topEnd = dimen.radiusSheetTop),
    ) {
        if (editing != null) {
            AddRecordForm(
                initial = editing,
                onSave = onEditSave,
                onDelete = { onRowDelete(editing) },
                onBack = onEditCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = dimen.pageHorizontal),
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = dimen.pageHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetTitle(stringResource(R.string.sheet_all_records_title))
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.history_count, rows.size),
                    style = typo.cardLabel,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(items = rows, key = { it.id }) { row ->
                    SwipeToDeleteRow(
                        row = row,
                        revealedId = revealedId,
                        onRevealChange = { revealedId = it },
                        onClick = { onRowClick(row) },
                        onDelete = {
                            revealedId = null
                            onRowDelete(row)
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(colors.divider),
                    )
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
