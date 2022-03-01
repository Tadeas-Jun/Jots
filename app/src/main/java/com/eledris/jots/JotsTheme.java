package com.eledris.jots;

import android.content.res.Resources;
import android.content.res.TypedArray;

import com.android.billingclient.api.SkuDetails;

public class JotsTheme implements Comparable<JotsTheme> {

    // Can later be expanded to include other ways of unlocking Themes, such as via a referral program or rewarded ad.
    // Public enums are implicitly static.
    public enum UnlockedBy {
        DEFAULT,
        PURCHASED
    }

    int resourceId;
    int marketOrder;
    Resources.Theme themeResource;
    boolean unlocked = false;
    UnlockedBy unlockedBy;

    SkuDetails skuDetails;

    // Constructor for the required parameters.
    public JotsTheme(int id, int order, UnlockedBy unlocked) {
        resourceId = id;
        marketOrder = order;
        unlockedBy = unlocked;
    }

    // Compare to another Theme by the order in the Theme Market.
    @Override
    public int compareTo(JotsTheme jotsTheme) {

        int compareOrder = jotsTheme.marketOrder;

        return marketOrder - compareOrder;

    }

    // Return the resourceTheme's attribute passed in the argument.
    private String GetThemeAttribute(int attribute) {

        TypedArray attrs = themeResource.obtainStyledAttributes(new int[]{attribute});
        String attr = attrs.getString(0);
        attrs.recycle();

        return attr;

    }

    // Return the themeResource's themeTitle attribute.
    public String GetThemeTitle() {

        return GetThemeAttribute(R.attr.themeTitle);

    }

    // Return the themeResource's themeIdAttr attribute.
    public String GetThemeId() {

        return GetThemeAttribute(R.attr.themeIdAttr);

    }

    // Return the themeResource's themeIdSku attribute.
    public String GetThemeSkuId() {

        return GetThemeAttribute(R.attr.themeIdSku);

    }

    // Check if this themeResource has a specific ID - return true if the given themeResource's ID and the given ID equal.
    public boolean CompareId(String idToCompare) {

        String attr = GetThemeId();

        return attr.equals(idToCompare);

    }

}
