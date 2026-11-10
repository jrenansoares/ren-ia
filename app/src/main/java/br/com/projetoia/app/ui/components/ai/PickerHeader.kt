package br.com.projetoia.app.ui.components.ai

import br.com.projetoia.app.ui.modifier.ImmediateContent as MaterialAnimatedContent
import br.com.projetoia.app.ui.modifier.ImmediateContent as AnimatedContent
import br.com.projetoia.app.ui.modifier.ImmediateColorAsState as animateColorAsState
import br.com.projetoia.app.ui.modifier.ImmediateFloatAsState as animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// sheet 的头部：左侧标题与说明，右侧放操作
@Composable
internal fun PickerHeader(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                // 各语言标题长短差别很大，放不下两行时自动缩小字号
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 28.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                    fontWeight = FontWeight.Black,
                    lineHeight = 1.2.em,
                    lineBreak = LineBreak.Heading,
                ),
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hero()
    }
}

// 选择器 sheet 的紧凑头部：左侧依次是小标题（可省略）、当前选项和说明，右侧放随选项变形的形状
@Composable
internal fun <T : Comparable<T>> PickerValueHeader(
    title: String?,
    value: T,
    hint: String,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
    hero: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PickerValueLabel(value = value, label = label)
            Text(
                text = hint,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hero()
    }
}

// 右侧显示固定的矩形状态块
@Composable
internal fun PickerHero(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .background(containerColor, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = contentColor,
        )
    }
}

// 当前选项的名称，切换时上下滚动
@Composable
private fun <T : Comparable<T>> PickerValueLabel(
    value: T,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    Text(
        text = label(value),
        modifier = modifier,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 24.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineSmallEmphasized.copy(
            fontWeight = FontWeight.Black,
            lineHeight = 1.2.em,
            lineBreak = LineBreak.Heading,
        ),
    )
}

// sheet 内子页面的头部：返回按钮加标题
@Composable
internal fun SheetHeader(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (navigationIcon == null) 8.dp else 4.dp),
        )
        actions()
    }
}
