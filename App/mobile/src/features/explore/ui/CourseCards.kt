package com.shikkhasathi.features.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.components.SsCard
import com.shikkhasathi.core.designsystem.components.SsChip
import com.shikkhasathi.core.designsystem.hexColor
import com.shikkhasathi.core.designsystem.numeric
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.durationText
import com.shikkhasathi.core.i18n.takaText
import com.shikkhasathi.core.model.Compatibility
import com.shikkhasathi.core.model.CourseCard
import com.shikkhasathi.core.model.StatusTone

/**
 * Course Card, list anatomy (shared with web): cover colour rail · code + exam type · 2-line title ·
 * teacher (verified) · rating · lessons/length · ৳ price with strikethrough · compatibility badge.
 */
@Composable
fun CourseListCard(
    course: CourseCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SsTheme.palette
    val examType = Str.fromKey("examType.${course.examType}")?.t() ?: course.examType
    val price = if (course.price == 0) Str.CourseFree.t() else takaText(course.price).t()
    val description = listOfNotNull(course.title, course.courseCode, examType, course.sellerName, price).joinToString(", ")
    SsCard(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(hexColor(course.coverColor, palette.primary)))
            Column(Modifier.weight(1f).padding(SsSpace.s3), verticalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s1), verticalAlignment = Alignment.CenterVertically) {
                    course.courseCode?.let { SsChip(text = it, tone = StatusTone.Primary) }
                    SsChip(text = examType)
                    course.institutionShort?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = palette.muted) }
                }
                Text(
                    course.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TeacherLine(course.sellerName, course.sellerVerified)
                Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3), verticalAlignment = Alignment.CenterVertically) {
                    if (course.ratingCount > 0) RatingText(course.ratingAvg, course.ratingCount)
                    if (course.lessonCount > 0) {
                        Text(
                            Str.CourseLessonsLength.t("n" to course.lessonCount, "length" to durationText(course.totalDurationSec)),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriceText(course.price, course.compareAtPrice)
                    CompatibilityBadge(course.compatibility)
                }
            }
        }
    }
}

@Composable
fun TeacherLine(
    name: String,
    verified: Boolean,
) {
    val palette = SsTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s1)) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
        if (verified) {
            Icon(
                Lucide.BadgeCheck,
                contentDescription = Str.CourseVerified.t(),
                tint = palette.success,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun RatingText(
    avg: Double,
    count: Int,
) {
    val palette = SsTheme.palette
    val avgText = UiText.Digits(((avg * 10).toInt() / 10.0).toString()).t()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Lucide.Star, contentDescription = null, tint = palette.warning, modifier = Modifier.size(14.dp))
        Text(
            Str.CourseRating.t("avg" to avgText, "n" to count),
            style = MaterialTheme.typography.bodySmall.numeric,
            color = palette.text,
        )
    }
}

/** ৳ price with the old price struck through (never a fake discount: compareAt must be higher). */
@Composable
fun PriceText(
    price: Int,
    compareAt: Int?,
) {
    val palette = SsTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SsSpace.s2)) {
        Text(
            if (price == 0) Str.CourseFree.t() else takaText(price).t(),
            style = MaterialTheme.typography.titleSmall.numeric,
            color = if (price == 0) palette.successFg else palette.text,
        )
        compareAt?.takeIf { it > price }?.let {
            Text(
                takaText(it).t(),
                style = MaterialTheme.typography.bodySmall.numeric,
                color = palette.muted,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

/** "আপনার ইউনিভার্সিটির সিলেবাস" (match) / "ভিন্ন সিলেবাস হতে পারে" (mismatch); nothing for general courses. */
@Composable
fun CompatibilityBadge(compatibility: Compatibility) {
    when (compatibility) {
        Compatibility.Match -> SsChip(text = Str.CompatMatch.t(), tone = StatusTone.Success)
        Compatibility.Mismatch -> SsChip(text = Str.CompatMismatch.t(), tone = StatusTone.Warning)
        Compatibility.General -> Unit
    }
}
