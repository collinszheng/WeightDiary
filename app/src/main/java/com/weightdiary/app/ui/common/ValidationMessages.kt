package com.weightdiary.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.weightdiary.app.R
import com.weightdiary.app.domain.validation.ValidationError

/**
 * 把 [ValidationError] 翻成给用户看的提示。
 *
 * 错误对象本身不带文案（domain 层不该管本地化），这里才组装。
 */
@Composable
fun ValidationError.message(): String = when (this) {
    is ValidationError.Required -> stringResource(R.string.error_weight_required)
    is ValidationError.FutureDate -> stringResource(R.string.error_future_datetime)
    is ValidationError.OutOfRange -> stringResource(
        R.string.error_out_of_range,
        range.start.formatTrimmed(),
        range.endInclusive.formatTrimmed(),
    )
}
