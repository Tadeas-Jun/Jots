package com.eledris.jots;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Base64;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ThemeStoreFragment extends Fragment implements PurchasesUpdatedListener {

    ArrayList<JotsTheme> themes;

    public ThemeStoreFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_theme_store, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // data to populate the RecyclerView with
        LoadThemesFromMain();
        Collections.sort(themes);

        LinearLayout themesLayout = requireView().findViewById(R.id.themes_layout);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 25, 0, 25);

        // Get the currently used theme.
        TypedArray currentIdArray = requireActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.themeIdAttr});
        String currentId = currentIdArray.getString(0);
        currentIdArray.recycle();

        // Set up each Theme preview.
        for (JotsTheme theme : themes) {

            // Find the theme attribute and save its ID into the themeIdArray.
            Resources.Theme themeResource = theme.themeResource;
            TypedArray themeIdArray = themeResource.obtainStyledAttributes(new int[]{R.attr.themeIdAttr});

            // Create and style the theme preview.
            LayoutInflater inflater = LayoutInflater.from(new ContextThemeWrapper(getContext(), themeResource));
            View themePreview = inflater.inflate(R.layout.theme_preview, null);
            String themeId = themeIdArray.getString(0);
            themePreview.setTag(themeId);
            themeIdArray.recycle();

            // Set the theme preview's onClick function.
            themePreview.setOnClickListener(this::HandleThemeClick);

            // Show the checkmark on currently selected theme - hide if it this theme is not currently selected.
            if (currentId.equals(themeId)) {
                themePreview.findViewById(R.id.theme_selected).setVisibility(View.VISIBLE);
            } else {
                themePreview.findViewById(R.id.theme_selected).setVisibility(View.GONE);
            }

            // Hide the lock icon if this theme is unlocked.
            if (theme.unlocked) {
                themePreview.findViewById(R.id.theme_locked).setVisibility(View.GONE);
            }

            // Add the theme preview to the view layout.
            themesLayout.addView(themePreview, layoutParams);
        }

        // Set up in-app purchases for the themes.
        SetupBillingClient();

    }

    // Get all the themes from MainActivity.
    private void LoadThemesFromMain() {

        themes = new ArrayList<>(MainActivity.allThemes);

    }

    // Fired when a Theme preview button is clicked.
    public void HandleThemeClick(View view) {

        // Get the Theme's tag (= ID).
        String themeId = view.getTag().toString();

        // Find the selected Theme.
        List<JotsTheme> jotsThemes = themes.stream().filter(theme -> theme.CompareId(themeId)).collect(Collectors.toList());
        JotsTheme selectedTheme = jotsThemes.get(0);

        // If the theme is not unlocked, run the code for unlocking it appropriately based on its unlockedBy value.
        if (!selectedTheme.unlocked) {

            switch (selectedTheme.unlockedBy) {

                // Themes unlockedBy DEFAULT should never be locked; if for some reason a theme like this is locked, unlock it as soon as the user clicks on it.
                case DEFAULT:
                    MainActivity.UnlockTheme(selectedTheme);
                    return;

                // Themes unlockedBy PURCHASED have to open the in-app purchase flow when clicked.
                case PURCHASED:
                    ClickToPurchaseTheme(selectedTheme.skuDetails, view);
                    return;

            }

        }

        // The following code is only reached if the clicked theme is unlocked.

        // Set the currentTheme in sharedPreferences to the selected theme.
        MainActivity.sharedPreferences.edit().putInt("currentTheme", requireContext().getResources().getIdentifier(themeId, "style", requireContext().getPackageName())).apply();

        // Reload the activity so the change takes effect.
        ReloadMainActivity();

    }

    // Reload the MainActivity so theme changes can take effect.
    private void ReloadMainActivity() {
        ((MainActivity) requireActivity()).reloadActivity();
    }

    // region BILLING

    // Load the app's public key from the hidden local.properties file.
    private static final String publicKey = BuildConfig.BASE64_ENCODED_PUBLIC_KEY;
    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    private BillingClient billingClient;
    private Context mContext;
    private List<Purchase> queriedPurchases;
    private final List<String> skuList = new ArrayList<>(); // Can be 'final', because the variable is never going to be pointing to a different collection instance. This doesn't prevent inserting and removing elements to/from the ArrayList.
    private View purchasingThemePreview = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;

    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    // Called when the user clicks on a unlockedBy PURCHASED locked Theme preview to unlock it.
    private void ClickToPurchaseTheme(SkuDetails skuDetails, View themePreview) {

        // The selected Theme doesn't have the skuDetails set (= it cannot be purchased).
        if (skuDetails == null) return;

        // Run through all the queriedPurchases, find the purchase with these skuDetails, and check if it's PENDING. Assign to boolean pending.
        boolean purchaseIsPending = false;
        for (Purchase p : queriedPurchases) {
            if (p.getSkus().get(0).equals(skuDetails.getSku())) {
                if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    purchaseIsPending = true;
                }
            }
        }

        // If the purchase is not already pending, the user can proceed to purchase it.
        if (!purchaseIsPending) {

            // Launch the in-app purchase billing flow.
            LaunchPurchaseFlow(skuDetails, themePreview);

        } else { // The purchase is already pending, so we need to let the user know via a Toast.

            if (isAdded()) { // isAdded() is needed to check if the fragment is added to an Activity.

                // Toast with a text letting the user know this purchase is already pending.
                Toast.makeText(getContext(), getResources().getString(R.string.purchase_pending), Toast.LENGTH_LONG).show();

            }

        }

    }

    // Run through the list of Themes, find all the ones that are unlockedBy PURCHASED, and add their skuIds to the skuList.
    private void FillSkuList() {

        // Run through all defined Themes.
        for (JotsTheme theme : themes) {

            // Find the Themes that are unlockedBy PURCHASED.
            if (theme.unlockedBy == JotsTheme.UnlockedBy.PURCHASED) {

                // Get the Theme's skuId (ID used to define the in-app purchased item).
                String skuId = theme.GetThemeSkuId();

                // If the Theme has a defined skuId, add it to the skuList.
                if (skuId != null) {

                    skuList.add(skuId);

                }

            }

        }

    }

    // The initial set up for the in-app purchases billing client.
    private void SetupBillingClient() {

        // Find all Themes that are unlockedBy PURCHASED and add their skuIds to the skuList.
        FillSkuList();

        // Create the billing client.
        billingClient = BillingClient.newBuilder(requireContext())
                .enablePendingPurchases()
                .setListener(this)
                .build();

        // Start the billingClient set up process (asynchronous).
        billingClient.startConnection(new BillingClientStateListener() {

            // Event fired when the billingClient setup is finished.
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {

                // If the billingClient setup was successful...
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {

                    // Find all purchases (non-consumed one-time and active subscriptions).
                    billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, (billingResult1, list) -> {

                        // Save the purchases in the queriedPurchases list.
                        queriedPurchases = list;

                        // Load all purchasable products from Google Play.
                        LoadAllSkus();

                        // Re-verify all owned purchases to make sure none of them are illegitimate.
                        for (Purchase purchase : queriedPurchases) {

                            HandlePurchase(purchase);

                        }

                    });

                }

            }

            @Override
            public void onBillingServiceDisconnected() {

                if (mContext != null) {
                    Toast.makeText(getContext(), getResources().getString(R.string.purchase_billingdisconnects), Toast.LENGTH_LONG).show();
                }

            }
        });

    }

    // Load all purchasable products from Google Play.
    public void LoadAllSkus() {

        if (billingClient.isReady()) {

            // Prepare the SKU parameters to be queried from Google Play.
            SkuDetailsParams.Builder skuParams = SkuDetailsParams.newBuilder();
            skuParams.setSkusList(skuList)
                    .setType(BillingClient.SkuType.INAPP);

            // Query Google Play to receive details about all the possible products to purchase.
            billingClient.querySkuDetailsAsync(skuParams.build(),
                    (billingResult, list) -> {

                        // If the SKU Details were loaded without any issues...
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {

                            // For each product that's available from Google Play...
                            for (Object skuDetailsObject : Objects.requireNonNull(list)) {

                                // Retrieve and save the SKU Details.
                                final SkuDetails skuDetails = (SkuDetails) skuDetailsObject;

                                // Find the theme whose SKU ID (defined in strings.xml and set in themes.xml) matches the SKU ID of the SKU Details we are currently looking at.
                                JotsTheme focusedTheme = themes.stream().filter(theme -> theme.GetThemeSkuId() != null && theme.GetThemeSkuId().equals(skuDetails.getSku())).collect(Collectors.toList()).get(0);

                                // Set the SKU Details of the Theme in question to the SKU Details loaded from Google Play.
                                focusedTheme.skuDetails = skuDetails;

                            }

                        }

                    });
        }
    }

    // Fired when a user attempts to purchase a product.
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> list) {

        // Get the update's response code.
        int responseCode = billingResult.getResponseCode();

        // The user successfully purchased an item - the purchase(s) is either complete or pending.
        if (responseCode == BillingClient.BillingResponseCode.OK && list != null) {

            // For each product the user attempted to purchase...
            for (Purchase purchase : list) {

                HandlePurchase(purchase);

            }

        // The user attempted to purchase an item they already own. This means that there has been a bug preventing the user from accessing their owned item - correct this mistake and allow access to the user.
        } else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED && list != null) {

            // Verifying all the purchases the user attempted to purchase while they already owned them.
            for (Purchase purchase : list) {

                HandlePurchase(purchase);

            }

        // The user cancelled the purchase flow before completing the purchase.
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {

            // Let the user know the Theme was not unlocked, as they canceled the purchase.
            if (isAdded()) {
                Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.purchase_canceled), Toast.LENGTH_LONG).show();
            }

        // There has been a miscellaneous error.
        } else {

            // Inform the user about the error.
            if (isAdded()) {
                Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.purchase_error), Toast.LENGTH_LONG).show();
            }

        }

    }

    // The user made a valid purchase which is currently either pending, or complete (and has to be verified).
    private void HandlePurchase(Purchase purchase) {

        // If the purchase is already completed, verify it was legitimate.
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {

            VerifyPurchase(purchase);

        // If the purchase is still pending, inform the user that they might have to wait.
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {

            if (isAdded()) {
                Toast.makeText(getContext(), getResources().getString(R.string.purchase_pending), Toast.LENGTH_LONG).show();
            }

        }

    }

    // Verify that the purchase is legitimate using the app's public key and a signature.
    private void VerifyPurchase(Purchase purchase) {

        Signature signature;

        try {

            signature = Signature.getInstance(SIGNATURE_ALGORITHM);

            // Decode the public key.
            PublicKey key = generateKey();

            // Verify the purchase.
            signature.initVerify(key);
            signature.update(purchase.getOriginalJson().getBytes());
            byte[] decodedSignature = Base64.decode(purchase.getSignature(), Base64.DEFAULT);

            // The verification failed and the purchase is illegitimate.
            if (!signature.verify(decodedSignature)) return;

            // If the purchase was verified, acknowledge it and give the user their purchased product.
            AcknowledgePurchase(purchase);

        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
        }

    }

    // Generate a decoded public key from the encoded one.
    private static PublicKey generateKey() {

        try {

            byte[] decodedKey = Base64.decode(ThemeStoreFragment.publicKey, Base64.DEFAULT);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }

    }

    // Acknowledge the user's purchase and unlock their product.
    private void AcknowledgePurchase(Purchase purchase) {

        // The purchase wasn't acknowledged yet - acknowledge it, give the user the appropriate theme, and inform them about the success.
        if (!purchase.isAcknowledged()) {

            // A listener determining what should happen once the purchase is acknowledged; if everything went okay, give user the Theme and let them know there were no issues.
            AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = billingResult -> {

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {

                    // Give the user access to the unlocked Theme.
                    AwardPurchasedItem(purchase);

                    // Let the user know the purchase was a success (via Toast) and hide the lock icon on the purchased Theme.
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getResources().getString(R.string.purchase_success), Toast.LENGTH_LONG).show();
                        purchasingThemePreview.findViewById(R.id.theme_locked).setVisibility(View.GONE);
                    });

                }

            };

            // Set the options for the acknowledge purchase method to include the appropriate purchase details.
            AcknowledgePurchaseParams acknowledgePurchaseParams =
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();

            // Acknowledge the purchase.
            billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);

        // The purchase has already been acknowledged, just give the user the appropriate Theme.
        } else {

            AwardPurchasedItem(purchase);

        }

    }

    @SuppressWarnings("CommentedOutCode")
    // The commented out code in this method is there purely for testing purposes, and should never make it into production.
    private void AwardPurchasedItem(Purchase purchase) {

        // Find the appropriate Theme based on the Theme's SKU ID.
        JotsTheme purchasedTheme = themes.stream().filter(theme -> theme.GetThemeSkuId() != null && theme.GetThemeSkuId().equals(purchase.getSkus().get(0))).collect(Collectors.toList()).get(0);

        // Unlock the Theme for the user.
        MainActivity.UnlockTheme(purchasedTheme);

        // These two lines are there purely for testing purposes, and should never make it into production. They effectively block all of the Themes for a user.
        // ConsumePurchase(purchase);
        // MainActivity.LockAllThemes();

    }

    // Launch the in-app purchase billing flow.
    private void LaunchPurchaseFlow(SkuDetails skuDetails, View themePreview) {

        // Set up the billing flow.
        BillingFlowParams flowParams = BillingFlowParams
                .newBuilder()
                .setSkuDetails(skuDetails)
                .build();

        // Set the theme being purchased.
        purchasingThemePreview = themePreview;

        // Launch the flow.
        billingClient.launchBillingFlow(requireActivity(), flowParams);

    }

    @SuppressWarnings("unused")
    // This is a method used purely for testing purposes and has no use in production. It clears a user's purchased Theme so they have to purchase it again.
    private void ConsumePurchase(Purchase purchase) {

        // Set the Purchase to be consumed.
        ConsumeParams consumeParams =
                ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();

        // Log the purchase being successfully consumed.
        ConsumeResponseListener listener = (billingResult, outToken) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Log.d("JOTS", "Purchase consumed!");
            }
        };

        // Consume the purchase.
        billingClient.consumeAsync(consumeParams, listener);
    }

    // endregion BILLING
}
