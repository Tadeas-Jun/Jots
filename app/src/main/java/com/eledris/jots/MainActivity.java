package com.eledris.jots;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    public NavHostFragment navHostFragment;
    public NavController navController;
    public AppBarConfiguration appBarConfiguration;
    public Toolbar toolbar;
    public DrawerLayout drawerLayout;

    public static ArrayList<Note> allNotes;
    public static ArrayList<JotsTheme> allThemes;

    public static SharedPreferences sharedPreferences;

    public static String selectedDateFormat;

    private final String keysetFilename = "jots_keyset.json";
    public static KeysetHandle keyset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Load sharedPreferences into the static variable.
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Tink is used for encryption and decryption of the notes.
        SetupTink();

        // Load and set the current Theme from shared preferences.
        int currentTheme = sharedPreferences.getInt("currentTheme", R.style.Theme_Jots);
        setTheme(currentTheme);

        // Load all themes and unlock those purchased / unlocked by default.
        GetAllThemes();

        // Load the layout.
        setContentView(R.layout.activity_main);

        // Set up all the navigation components.
        navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navhost);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        drawerLayout = findViewById(R.id.drawer_layout);

        appBarConfiguration =
                new AppBarConfiguration.Builder(navController.getGraph())
                        .setOpenableLayout(drawerLayout)
                        .build();

        toolbar = findViewById(R.id.toolbar);

        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);

        toolbar.setNavigationOnClickListener(view -> {

            // Get the active fragment
            Fragment fragment = navHostFragment.getChildFragmentManager().getFragments().get(0);
            if (fragment instanceof NotesFragment) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else {
                onBackPressed();
            }

        });

        // Unlock the drawer to be able to slide open.
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        // Load the selected date format from sharedPreferences.
        selectedDateFormat = sharedPreferences.getString("selectedDateFormat", getResources().getStringArray(R.array.date_formats)[0]);

    }

    // Setting up Tink, the encryption service.
    public void SetupTink() {

        try {

            // Register key types at runtime.
            AeadConfig.register();

            // Load the keyset from its file, if it exists.
            keyset = LoadKeyset();

            // If the keyset wasn't loaded, create and save a new one.
            if (keyset == null) {
                keyset = CreateAndSaveKeyset();
            }

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

    }

    // Load the keyset from its file, if it exists.
    private KeysetHandle LoadKeyset() {

        try {

            // Find the keyset file.
            File file = new File(getFilesDir(), keysetFilename);

            // If the file doesn't exist, return null, it will be created later.
            if (!file.exists()) {
                return null;
            }

            // Read the keyset.
            KeysetHandle keysetHandle = CleartextKeysetHandle.read(
                    JsonKeysetReader.withFile(file));

            return keysetHandle;

        } catch (GeneralSecurityException | IOException e) {

            e.printStackTrace();
            return null;

        }

    }

    // If the keyset doesn't already exists, create it and save it into its file.
    private KeysetHandle CreateAndSaveKeyset() {

        KeysetHandle keysetHandle;
        try {

            // Generate a new keyset.
            keysetHandle = KeysetHandle.generateNew(
                    KeyTemplates.get("AES128_GCM"));

            // Create the keyset file.
            File file = new File(getFilesDir(), keysetFilename);

            if (!file.exists()) {
                try {

                    // Doesn't need a ResultOfMethodCallIgnored - the result is only needed if we're not sure if the file exists or not.
                    // noinspection ResultOfMethodCallIgnored
                    file.createNewFile();

                } catch (IOException e) {

                    e.printStackTrace();
                    return null;

                }
            }

            // Write (save) the keyset into the file.
            CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withFile(
                    file));

            // Return the keyset to Tink.
            return keysetHandle;

        } catch (GeneralSecurityException | IOException e) {

            e.printStackTrace();
            return null;

        }

    }

    // Navigate to fragment.
    public void CustomNavigate(int actionID, Bundle args) {

        hideKeyboard(this);
        clearOptionsMenu();
        hideMenu();
        navController.navigate(actionID, args);

    }

    // Hide and lock the side menu.
    public void hideMenu() {

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

    }

    // Hide the pop-up menu.
    public void clearOptionsMenu() {
        toolbar.getMenu().clear();
    }

    // Hide the keyboard - required to not carry open keyboard from the note fragment.
    public static void hideKeyboard(Activity activity) {

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);

        // Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();

        // If no view currently has focus, create a new one, just so we can grab a window token from it.
        if (view == null) {
            view = new View(activity);
        }

        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // Find all defined notes in the file system.
    public void FindAllNotes() {

        allNotes = new ArrayList<>();

        // Loop through all the app's files and select them as Notes if they include the ID template ("jot_id").
        String[] files = fileList();
        for (String s : files) {

            if (s.contains("jot_id")) {
                LoadNote(s);
            }
        }

    }

    // Load a Note by ID.
    public void LoadNote(String id) {

        String content;
        String title;

        // Get the Note file.
        File file = new File(this.getFilesDir(), id);

        if (file.exists()) {

            byte[] output = new byte[(int) file.length()];
            FileInputStream fis;
            try {

                fis = new FileInputStream(file);

                // Doesn't need a ResultOfMethodCallIgnored - the result (number of bytes read) is irrelevant at this point.
                // noinspection ResultOfMethodCallIgnored
                fis.read(output);

                // Decrypt output.
                String decryptedOutput = new String(DecryptNote(output, id), StandardCharsets.UTF_8);

                // Return if the note is empty (= the file contains only the note's ID).
                if (decryptedOutput.equals(id)) return;

                // Split the loaded note into title and content, by the note's ID.
                String[] split = decryptedOutput.split(id);
                title = split.length > 0 ? split[0] : " ";
                content = split.length > 1 ? split[1] : " ";

                // Create a new Note object and load the data into it.
                Note n = new Note();
                n.title = title;
                n.content = content;
                n.id = id;
                n.lastEdited = new Date(file.lastModified());
                allNotes.add(n);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    // Decrypt the note.
    private byte[] DecryptNote(byte[] fileContent, String id) {

        Aead aead;
        byte[] outputText = new byte[0];
        try {

            aead = keyset.getPrimitive(Aead.class);

            // Get the bytes from the file and decrypt it.
            outputText = aead.decrypt(fileContent, id.getBytes(StandardCharsets.UTF_8));

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

        return outputText;

    }

    // Encrypt the note using Google's tink. When encrypting a note, the associated data is the note's ID.
    public byte[] EncryptString(String fileContent, String associatedData) {

        Aead aead;
        byte[] cipherText = new byte[0];
        try {

            // Getting the AEAD (Authenticated Encryption with Associated Data) primitive from tink to use its API.
            aead = keyset.getPrimitive(Aead.class);

            // Encrypt the note's full content (title + ID + content) with the note's ID as the associated data.
            cipherText = aead.encrypt(fileContent.getBytes(StandardCharsets.UTF_8), associatedData.getBytes(StandardCharsets.UTF_8));

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

        // Return the encrypted byte array.
        return cipherText;

    }

    // Create a new empty note and navigate into it.
    public void CreateNote() {

        // Create the ID by searching for the lowest unused ID number.
        String id = "jot_id" + FindUnusedID();

        Note n = new Note();
        n.id = id;

        // Navigate to the empty note fragment.
        CustomNavigate(R.id.action_notesFragment_to_noteFragment, MainActivity.NoteToBundle(n));

    }

    // Find the lowest unused ID number.
    public int FindUnusedID() {

        int i = 0;
        ArrayList<String> ids = new ArrayList<>();

        // Find all the used IDs.
        for (Note n : allNotes) {
            ids.add(n.id);
        }

        // Loop through the IDs to find the lowest unused number.
        while (ids.contains("jot_id" + i)) {
            i++;
        }

        return i;

    }

    // Convert a Note object into a Bundle to be passed while navigating.
    public static Bundle NoteToBundle(Note note) {

        Bundle bundle = new Bundle();

        bundle.putString("id", note.id);
        bundle.putString("title", note.title);
        bundle.putString("content", note.content);

        return bundle;

    }

    // Delete a note from the allNotes ArrayList and delete the file.
    public void DeleteNote(String id) {

        // Find the index of the note in the allNotes ArrayList.
        int index = -1;
        for (Note n : allNotes) {
            if (n.id.equals(id)) {
                index = allNotes.indexOf(n);
                break;
            }
        }

        if (index == -1) return;

        // Remove the note from the allNotes ArrayList.
        allNotes.remove(index);

        // Find and remove the note file from the app's file system.
        File file = new File(getFilesDir(), id);
        if (!file.exists()) return;

        // Doesn't need a ResultOfMethodCallIgnored - the result is not relevant at this point.
        // noinspection ResultOfMethodCallIgnored
        file.delete();

    }

    // Handle a click on an item in the side menu or the top bar menu (e.g. Save note).
    public void HandleMenuClick(MenuItem item) {

        int itemId = item.getItemId();

        if (itemId == R.id.menu_about) {

            // Navigate to the About fragment.
            CustomNavigate(R.id.action_notesFragment_to_aboutFragment, null);

        } else if (itemId == R.id.menu_themes) {

            // Navigate to Theme Store fragment.
            CustomNavigate(R.id.action_notesFragment_to_themeStoreFragment, null);

        } else if (itemId == R.id.menu_settings) {

            // Navigate to the Settings fragment.
            CustomNavigate(R.id.action_notesFragment_to_settingsFragment, null);

        } else if (itemId == R.id.menu_code) {

            // Open the GitHub repo.
            Uri uri = Uri.parse("https://github.com/Tadeas-Jun/Jots");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);

        } else if (itemId == R.id.save_note) {

            // Save the note and leave the fragment.
            NoteFragment noteFragment = (NoteFragment) navHostFragment.getChildFragmentManager().getFragments().get(0);
            noteFragment.SaveAndLeave();

        }
    }

    // Load all themes from the DefinedThemes class.
    public void GetAllThemes() {

        allThemes = new ArrayList<>();

        JotsTheme[] themes = DefinedThemes.definedThemes;

        // Loop through all the themes and load their resources from themes.xml.
        for (JotsTheme theme : themes) {

            Resources.Theme themeResource = getResources().newTheme();
            themeResource.applyStyle(theme.resourceId, true);
            theme.themeResource = themeResource;

        }

        Collections.addAll(allThemes, themes);

        // Load the unlocked themes.
        LoadUnlockedThemes();

        // Unlock the themes that are supposed to be unlocked by default.
        UnlockedByDefault();

        // Lock all themes for the user - only used for testing purposes.
        //LockAllThemes();

    }

    // Reloads the activity - used when switching themes.
    public void reloadActivity() {
        finish();
        overridePendingTransition(0, 0);
        startActivity(getIntent());
        overridePendingTransition(0, 0);
    }

    // Loop through all the themes and unlock those that are supposed to be unlocked by default.
    public static void UnlockedByDefault() {

        for (JotsTheme theme : allThemes) {
            if (theme.unlockedBy == JotsTheme.UnlockedBy.DEFAULT && !theme.unlocked) {
                UnlockTheme(theme);
            }
        }

    }

    // Load all themes that the user has unlocked.
    public static void LoadUnlockedThemes() {

        char[] finalBinaryArray = new char[allThemes.size()];

        // Load the saved unlockedThemes value and convert it to binary.
        String binaryValue = sharedPreferences.getString("unlockedThemes", "0");
        char[] binaryArray = binaryValue.toCharArray();

        // Needs to be there for when the size of the saved value is different than the total theme count.
        System.arraycopy(binaryArray, 0, finalBinaryArray, 0, binaryArray.length);

        // Loop through all the binary values and lock / unlock the appropriate themes.
        for (int i = 0; i < finalBinaryArray.length; i++) {

            if (finalBinaryArray[i] == '0') {
                allThemes.get(i).unlocked = false;
            } else if (finalBinaryArray[i] == '1') {
                allThemes.get(i).unlocked = true;
            }

        }
    }

    // These two methods are unused in the production - they are used for testing purposes.
    @SuppressWarnings("unused")
    public static void UnlockAllThemes() {

        for (JotsTheme theme : allThemes) {

            if (!theme.unlocked) {
                theme.unlocked = true;
            }

        }

    }

    @SuppressWarnings("unused")
    public static void LockAllThemes() {

        for (JotsTheme theme : allThemes) {

            if (theme.unlocked) {
                theme.unlocked = false;
            }

        }

        sharedPreferences.edit().putInt("unlockedThemes", 0).apply();

    }

    // Unlock a theme and save the new data.
    public static void UnlockTheme(JotsTheme theme) {

        theme.unlocked = true;
        SaveUnlockedThemes();
        LoadUnlockedThemes();

    }

    // Saves the unlocked Themes in order as a binary string - 1 if the theme is unlocked, 0 if it's not - into Shared Preferences (as "unlockedThemes").
    public static void SaveUnlockedThemes() {

        StringBuilder binaryValue = new StringBuilder();

        for (JotsTheme theme : allThemes) {

            if (!theme.unlocked) {
                binaryValue.append('0');
            } else {
                binaryValue.append('1');
            }

        }

        sharedPreferences.edit().putString("unlockedThemes", binaryValue.toString()).apply();

        // This was the old system of saving the binary through a decimal value in an int. However, this was limited by the size of the int (32 themes). It's better to just save the binary values as a string.
        // int decimalValue = Integer.parseInt(binaryValue.toString(), 2);
        // sharedPreferences.edit().putInt("unlockedThemes", decimalValue).apply();

    }

    // Converting DP into normal pixels. Used in calculating the paddings of the exit dialog in Note.
    public static int ConvertDpToPx(int dp, Context context) {

        return Math.round(dp * (context.getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));

    }

    // TODO: Implement tags.

    // TODO: Implement search bar - search based on text, title, tag, date.

    // TODO: Write unit tests.

    // TODO: Implement a feature to export all notes at once into one file (could be given selection between plain text and HTML text).

}
