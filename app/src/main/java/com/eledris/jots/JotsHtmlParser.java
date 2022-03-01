package com.eledris.jots;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import java.util.regex.Pattern;

// The HTML parser created to handle HTML in Notes on API >= 24.
// Heavily inspired by: https://stackoverflow.com/a/35837723
public class JotsHtmlParser {

    // Define all the used HTML tags.
    public static String[] boldTags = {"<b>", "</b>"};
    public static String[] italicsTags = {"<i>", "</i>"};
    public static String[] underlineTags = {"<u>", "</u>"};
    public static String[] strikethroughTags = {"<del>", "</del>"};
    public static String newlineTag = "<br/>";

    public static String toHtml(Spannable text) {

        final SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        int[] position = new int[2];

        // Replace Style spans with <b> </b> or <i> </i>:
        // region STYLE SPANS
        // Get all the StyleSpans from the full text.
        StyleSpan[] styleSpans = stringBuilder.getSpans(0, text.length(), StyleSpan.class);

        // Move through all the style spans.
        for (int i = styleSpans.length - 1; i >= 0; i--) {

            // Get the individual span.
            StyleSpan span = styleSpans[i];

            // Get the start and end positions of the span.
            position[0] = stringBuilder.getSpanStart(span);
            position[1] = stringBuilder.getSpanEnd(span);

            // Remove the formatting of the text.
            stringBuilder.removeSpan(span);

            // If the text is bold...
            if (span.getStyle() == Typeface.BOLD) {

                // Insert the bold HTML tags.
                stringBuilder.insert(position[0], boldTags[0]);
                stringBuilder.insert(position[1] + boldTags[0].length(), boldTags[1]);

            // If the text is italics...
            } else if (span.getStyle() == Typeface.ITALIC) {

                // Insert the italics HTML tags.
                stringBuilder.insert(position[0], italicsTags[0]);
                stringBuilder.insert(position[1] + italicsTags[0].length(), italicsTags[1]);

            }
        }
        // endregion STYLE SPANS

        // Replace underline spans with <u> </u>:
        // region UNDERLINE SPANS
        // Get all the UnderlineSpans from the full text.
        UnderlineSpan[] underSpans = stringBuilder.getSpans(0, stringBuilder.length(), UnderlineSpan.class);

        // Move through all the underline spans.
        for (int i = underSpans.length - 1; i >= 0; i--) {

            // Get the individual span.
            UnderlineSpan span = underSpans[i];

            // Get the start and end positions of the span.
            position[0] = stringBuilder.getSpanStart(span);
            position[1] = stringBuilder.getSpanEnd(span);

            // Remove the formatting of the text.
            stringBuilder.removeSpan(span);

            // Insert the underline HTML tags.
            stringBuilder.insert(position[0], underlineTags[0]);
            stringBuilder.insert(position[1] + underlineTags[0].length(), underlineTags[1]);

        }
        // endregion UNDERLINE SPANS

        // Replace strikethrough spans with <del> </del>:
        // region STRIKETHROUGH SPANS
        // Get all the StrikethroughSpans from the full text.
        StrikethroughSpan[] strikeSpans = stringBuilder.getSpans(0, stringBuilder.length(), StrikethroughSpan.class);

        // Move through all the strikethrough spans.
        for (int i = strikeSpans.length - 1; i >= 0; i--) {

            // Get the individual span.
            StrikethroughSpan span = strikeSpans[i];

            // Get the start and end positions of the span.
            position[0] = stringBuilder.getSpanStart(span);
            position[1] = stringBuilder.getSpanEnd(span);

            // Remove the formatting of the text.
            stringBuilder.removeSpan(span);

            // Insert the strikethrough HTML tags.
            stringBuilder.insert(position[0], strikethroughTags[0]);
            stringBuilder.insert(position[1] + strikethroughTags[0].length(), strikethroughTags[1]);
        }
        // endregion STRIKETHROUGH SPANS

        // Replace the newline characters with <br/>.
        ReplaceInStringBuilder(stringBuilder, Pattern.compile("[\\r\\n]"), newlineTag);

        return stringBuilder.toString();
    }

    // Replace a single character matching a regex patter with a new string; used to replace newline characters with the newline HTML tag.
    private static void ReplaceInStringBuilder(SpannableStringBuilder b, Pattern oldPattern, String newStr) {

        // Go through the individual characters of the text.
        for (int i = b.length() - 1; i >= 0; i--) {

            // If this character matches the provided regex pattern...
            if (oldPattern.matcher(Character.toString(b.charAt(i))).matches()) {

                // Replace the character with the new string.
                b.replace(i, i + 1, newStr);

            }

        }

    }

}
