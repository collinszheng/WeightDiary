package com.weightdiary.app.ui.sheet

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 「添加数据」底部弹窗。占约 3/4 屏高。
 *
 * 只是给 [AddRecordForm] 套一层弹窗外壳。表单本身是独立组件 ——
 * 「全部记录」弹窗要在不关闭自己的前提下直接嵌它。
 *
 * 传了 [initial] 就进入编辑模式：预填原值、标题换成「编辑数据」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordSheet(
    onDismiss: () -> Unit,
    onSubmit: (weightKg: Double, measuredAt: Instant, bodyFatPercent: Double?, note: String?) -> Unit,
    initial: RecordRow? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(
            topStart = dimen.radiusSheetTop,
            topEnd = dimen.radiusSheetTop,
        ),
    ) {
        AddRecordForm(
            initial = initial,
            // 先播完退场动画再落库，否则弹窗会在数据变化时「跳」一下
            onSave = { weightKg, measuredAt, bodyFatPercent, note ->
                scope.launch {
                    sheetState.hide()
                    onSubmit(weightKg, measuredAt, bodyFatPercent, note)
                }
            },
            onDelete = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = dimen.pageHorizontal),
        )
    }
}
