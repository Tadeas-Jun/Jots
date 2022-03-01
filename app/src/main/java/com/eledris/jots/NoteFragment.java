package com.eledris.jots;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class NoteFragment extends Fragment {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String CONTENT = "content";

    private Note displayedNote;

    private boolean hadChanges = false;
    /* On back arrow:
        When note empty with changes -> discard changes and leave.
        When note empty without changes -> delete note and leave.
        When note not empty with changes -> Save/Discard/Cancel dialog.
        When note not empty without changes -> leave.
     */

    // Is the SAVE button inflated into the toolbar?
    private boolean toolbarInflated = false;

    private EditText titleEditText;
    private TextInputLayout titleTextLayout;
    private EditText contentEditText;

    // Is the bar with the formatting options expanded?
    private boolean formattingBarExpanded;

    public NoteFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        displayedNote = new Note();

        // Load the arguments into the displayedNote.
        if (getArguments() != null) {
            displayedNote.id = getArguments().getString(ID); // The ID should always be set.
            displayedNote.title = getArguments().getString(TITLE) == null ? null : getArguments().getString(TITLE); // The title can be empty.
            displayedNote.content = getArguments().getString(CONTENT) == null ? null : getArguments().getString(CONTENT); // The note can be empty.
        }


        // Setup the function that fires when the user presses the back button on their device or in the toolbar.
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                OnExit();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);

    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    // The local variable v is needed for better code readability and possible future editing.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_note, container, false);

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide the keyboard, if it was open before.
        MainActivity.hideKeyboard(requireActivity());

        // Set the editTexts.
        titleEditText = view.findViewById(R.id.note_title_editText);
        contentEditText = view.findViewById(R.id.note_content_editText);
        titleTextLayout = view.findViewById(R.id.note_title_inputLayout);

        // Set the note title and content.
        UpdateTexts();

        // Hide the hints if they are not allowed in the Settings - otherwise they're set by default.
        SetupHints();

        // Hide the title divider if the user has it turned off in the Settings - otherwise it's visible by default.
        SetupTitleDivider();

        // Hide the toolbar if the user has it set to hide in the Settings - otherwise it's visible by default.
        SetupToolbar();

        // Add text change listeners to the title and the content text fields.
        AddChangeListeners();

        // Set up the formatting bar (if the user is on API 24+).
        SetupFormattingBar();

    }

    // Fired every time any one of the texts changes.
    public void OnTextChange() {

        if (!hadChanges) {
            hadChanges = true;
        }

        // Inflate the toolbar's SAVE button.
        if (!toolbarInflated) {
            InflateToolbar();
        }
    }

    // Fired directly after any one of the texts changes.
    public void AfterTextChange() {

        // If the note is completely empty, the SAVE button from the toolbar should disappear - one cannot save an empty note.
        if (displayedNote.isEmpty()) {
            DeflateToolbar();
        }

    }

    // Display the SAVE button.
    public void InflateToolbar() {

        toolbarInflated = true;
        MainActivity ma = ((MainActivity) requireActivity());
        ma.toolbar.inflateMenu(R.menu.note_menu);

    }

    // Hide the SAVE button.
    public void DeflateToolbar() {

        MainActivity ma = ((MainActivity) requireActivity());
        ma.toolbar.getMenu().clear();
        toolbarInflated = false;

    }

    // Hide the text field hints and the max title length counter, if the user has them disabled in the Settings.
    private void SetupHints() {

        // If the user doesn't have the hints allowed in the Settings, hide them. Otherwise, they're set by default.
        if (!MainActivity.sharedPreferences.getBoolean("settings_hints", getResources().getBoolean(R.bool.settings_hints_defaultValue))) {
            titleEditText.setHint(null);
            contentEditText.setHint(null);
            titleTextLayout.setCounterEnabled(false);
        }

    }

    // Hide the title divider line, if the user has it disabled in the Settings.
    private void SetupTitleDivider() {

        // Hide the title divider if the user has it turned off in the Settings. Otherwise, it is enabled by default.
        if (!MainActivity.sharedPreferences.getBoolean("settings_titledivider", getResources().getBoolean(R.bool.settings_titleDivider_defaultValue))) {

            titleTextLayout.setBoxStrokeWidth(0);
            titleTextLayout.setBoxStrokeWidthFocused(0);

        }
    }

    // Hide the "Jots" title in the toolbar, if the user has it disabled in the Settings.
    private void SetupToolbar() {

        // Hide the toolbar title, if the user has it set to hide in the Settings. Otherwise, it is shown by default.
        if (MainActivity.sharedPreferences.getBoolean("settings_hidetoolbartitle", getResources().getBoolean(R.bool.settings_hideToolbarTitle_defaultValue))) {

            MainActivity mainActivity = (MainActivity) requireActivity();
            mainActivity.toolbar.setTitle(null);

        }

    }

    // Updates the title and content edit texts' displayed content.
    public void UpdateTexts() {

        // Display the title text.
        if (displayedNote.title != null && displayedNote.title.length() > 0)
            titleEditText.setText(displayedNote.title);

        // Display the content text.
        if (displayedNote.content != null && displayedNote.content.length() > 0)

            // If the API is below 24, no formatting is present. However, we still need to use the fromHtml() function to deal with <br> tags and similar.
            contentEditText.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Html.fromHtml(displayedNote.content, Html.FROM_HTML_MODE_COMPACT) : displayedNote.content);

    }

    // Add text change listeners to the title and the content text fields.
    public void AddChangeListeners() {

        // Adding listeners to the content text edit.
        ((EditText) requireView().findViewById(R.id.note_content_editText)).addTextChangedListener(new TextWatcher() {

            @Override
            // Nothing needs to happen before the text changes, but the method needs to be implemented.
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                OnTextChange();
            }

            @Override
            // Transform the text to the HTML version and save it in the Note.
            public void afterTextChanged(Editable editable) {
                displayedNote.content = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? JotsHtmlParser.toHtml(editable) : String.valueOf(editable);
                AfterTextChange();
            }
        });

        // Adding listeners to the title text edit.
        ((EditText) requireView().findViewById(R.id.note_title_editText)).addTextChangedListener(new TextWatcher() {

            @Override
            // Nothing needs to happen before the text changes, but the method needs to be implemented.
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                OnTextChange();
            }

            @Override
            // The title text doesn't include any formatting, so it's possible to just save the pure text into the Note.
            public void afterTextChanged(Editable editable) {
                displayedNote.title = editable.toString();
                AfterTextChange();
            }
        });

    }

    // Save the note and leave back to the NotesFragment page.
    public void SaveAndLeave() {

        SaveNote();
        Leave();

    }

    // Leave the fragment back to the list of notes (NotesFragment).
    public void Leave() {

        Navigation.findNavController(requireView()).navigateUp();

    }

    // Fired when the user attempts to exit the fragment using the back arrow on the toolbar or on their phone.
    public void OnExit() {

        // If the note is empty, do not save any changes and leave - one can not save an empty note.
        if (displayedNote.isEmpty()) {
            Leave();
            return;
        }

        // If there weren't any changes, leave directly - there is nothing to be saved.
        if (!hadChanges) {
            Leave();
            return;
        }

        // Creating the dialog asking the user whether to save changes, discard them, or leave.

        // Create the description text view.
        TextView messageView = new TextView(getContext());

        // Set the dialog's text.
        String message = getString(R.string.note_exit_description);
        messageView.setText(message);

        // Set the dialog's text's design.
        messageView.setTextAppearance(R.style.Jots_Text);

        // Set the dialog's text's and buttons' font.
        TypedValue typedValue = new TypedValue();
        requireActivity().getTheme().resolveAttribute(R.attr.fontFamily, typedValue, true);
        Typeface typeface = ResourcesCompat.getFont(requireContext(), typedValue.resourceId);
        messageView.setTypeface(typeface);

        // Set the dialog's text's padding.
        // A left padding of 27dp looks aligned with the title.
        int paddingLeft = MainActivity.ConvertDpToPx(27, requireContext());
        int paddingTop = MainActivity.ConvertDpToPx(20, requireContext());
        messageView.setPadding(paddingLeft, paddingTop, 0, 0);

        // Build the actual content of the dialog.
        new MaterialAlertDialogBuilder(requireContext())

                // Set the dialog's title.
                .setTitle(getString(R.string.note_exit_title))

                // Set the dialog's description to display the previously created TextView.
                .setView(messageView)

                // Create a Cancel button with a null listener that allows the button to dismiss the dialog and take no further action.
                .setNeutralButton(getString(R.string.note_exit_cancel), null)

                // Create a Save button that saves the note and leaves back to the NotesFragment page.
                .setPositiveButton(getString(R.string.note_exit_save), (dialog, which) -> {

                    // Save the note and leave to homepage.
                    SaveAndLeave();

                })

                // Create a Discard button that leaves to the NotesFragment page without saving changes.
                .setNegativeButton(getString(R.string.note_exit_discard), (dialog, which) -> {

                    // Leave to homepage without saving.
                    Leave();

                })

                // Display the dialog.
                .show();

    }

    // Check if this Note is already saved in the allNotes list in MainActivity.
    public boolean NoteSavedInList() {

        for (Note note : MainActivity.allNotes) {

            // Checks if the Note exists in the list based on the ID.
            if (note.id.equals(displayedNote.id)) {
                return true;
            }

        }

        return false;

    }

    // Encrypts the note and saves it into a file.
    // Encrypting done using: https://github.com/google/tink/
    public void SaveNote() {

        // If the note doesn't have a title, put an empty string into it.
        if (displayedNote.title == null) {
            displayedNote.title = "";
        }

        // If the note doesn't have any content, put an empty string into it.
        if (displayedNote.content == null) {
            displayedNote.content = "";
        }

        // Create a File reference.
        File file = new File(requireContext().getFilesDir(), displayedNote.id);

        // Add the note to MainActivity's allNotes list, if it's not already there.
        if (!NoteSavedInList()) {
            MainActivity.allNotes.add(displayedNote);
        }

        // If the file doesn't already exist, create it.
        if (!file.exists()) {
            try {

                // Doesn't need a ResultOfMethodCallIgnored - the result is only needed if we're not sure if the file exists or not.
                // noinspection ResultOfMethodCallIgnored
                file.createNewFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Create the content to be encrypted and saved into the file - the Note's title, id, and content together.
        String string = displayedNote.title + displayedNote.id + displayedNote.content;

        // Encrypt the note.
        MainActivity ma = ((MainActivity) requireActivity());
        byte[] encryptedBytes = ma.EncryptString(string, displayedNote.id);

        try {

            // Save the encrypted content into the file.
            FileOutputStream fos = requireContext().openFileOutput(displayedNote.id, Context.MODE_PRIVATE);
            fos.write(encryptedBytes);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // An enum of all the formatting options the user has. More options can be added by adding their name here and handling them in ToggleFormatting().
    enum FORMATTING_OPTION {
        BOLD,
        ITALICS,
        UNDERLINE,
        STRIKETHROUGH
    }

    // Expands or collapses the formatting bar when the user clicks on it.
    public void ToggleFormattingBar(View view) {

        // Get the duration of the animation from the durations.xml file.
        int formattingBarAnimDuration = getResources().getInteger(R.integer.toggle_formatting_bar_duration);

        // If the formatting bar is collapsed, expand it.
        if (!formattingBarExpanded) {

            formattingBarExpanded = true;

            // Execute the animation of the collapsed bar sliding up.
            slide_up(getContext(), view);

            // After the animation ends...
            view.postDelayed(() -> {

                // Hide the collapsed version of the bar.
                view.setVisibility(View.GONE);

                // Show the expanded version of the bar.
                View expandedView = requireView().findViewById(R.id.note_tools_expanded);
                expandedView.setVisibility(View.VISIBLE);

                // Execute the animation of the expanded bar sliding down.
                slide_down(getContext(), expandedView);

            }, formattingBarAnimDuration);

        // If the formatting bar is expanded, collapse it.
        } else {

            formattingBarExpanded = false;

            // Execute the animation of the expanded bar sliding up.
            slide_up(getContext(), view);

            // After the animation ends...
            view.postDelayed(() -> {

                // Hide the expanded version of the bar.
                view.setVisibility(View.GONE);

                // Show the collapsed version of the bar.
                View formattingView = requireView().findViewById(R.id.note_tools);
                formattingView.setVisibility(View.VISIBLE);

                // Execute the animation of the collapsed bar sliding down.
                slide_down(getContext(), formattingView);

            }, formattingBarAnimDuration);
        }

    }

    // Sets up the formatting bar (if the user is on API 24+).
    public void SetupFormattingBar() {

        // Finds the collapsed and expanded versions of the formatting bar.
        View toolsView = requireView().findViewById(R.id.note_tools);
        View toolsExpandedView = requireView().findViewById(R.id.note_tools_expanded);

        // The Html functions have major issues in API < 24. For this reason, the formatting is entirely forbidden before Android 7.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            toolsView.setVisibility(View.GONE);
            toolsExpandedView.setVisibility(View.GONE);
            return;
        }

        // Adds the click listeners to fire ToggleFormattingBar() when both the expanded and collapsed versions of the bar are clicked.
        toolsView.setOnClickListener(this::ToggleFormattingBar);
        toolsExpandedView.setOnClickListener(this::ToggleFormattingBar);

        // Get the duration of the animation from the durations.xml file.
        int formattingBarAnimDuration = getResources().getInteger(R.integer.toggle_formatting_bar_duration);

        // Show or hide the formatting bar when the user clicks on/off the note's main content edit text field.
        contentEditText.setOnFocusChangeListener((view, hasFocus) -> {

            // Choose either the expanded or collapsed version of the bar, depending on which one is active.
            View formattingBar = formattingBarExpanded ? toolsExpandedView : toolsView;

            // If the user just clicked on the content edit text, execute the animation to show the toolbar, and vice versa.
            if (hasFocus) {
                slide_down(getContext(), formattingBar);
            } else {
                slide_up(getContext(), formattingBar);
            }

            // After the animation is done, show/hide the bar.
            requireView().postDelayed(() -> formattingBar.setVisibility(hasFocus ? View.VISIBLE : View.GONE), formattingBarAnimDuration);

        });

        // Set up the Bold button.
        Button buttonBold = requireView().findViewById(R.id.formatting_button_bold);
        buttonBold.setOnClickListener(this::BoldButtonClick);

        // Set up the Italics button.
        Button buttonItalics = requireView().findViewById(R.id.formatting_button_italics);
        buttonItalics.setOnClickListener(this::ItalicsButtonClick);

        // Set up the Underline button.
        Button buttonUnderline = requireView().findViewById(R.id.formatting_button_underline);
        buttonUnderline.setOnClickListener(this::UnderlineButtonClick);

        // Set up the Strikethrough button.
        Button buttonStrikethrough = requireView().findViewById(R.id.formatting_button_strikethrough);
        buttonStrikethrough.setOnClickListener(this::StrikethroughButtonClick);

    }

    // Fired when the Bold formatting button is clicked.
    public void BoldButtonClick(View view) {

        ToggleFormatting(FORMATTING_OPTION.BOLD);

    }

    // Fired when the Italics formatting button is clicked.
    public void ItalicsButtonClick(View view) {

        ToggleFormatting(FORMATTING_OPTION.ITALICS);

    }

    // Fired when the Underline formatting button is clicked.
    public void UnderlineButtonClick(View view) {

        ToggleFormatting(FORMATTING_OPTION.UNDERLINE);

    }

    // Fired when the Strikethrough formatting button is clicked.
    public void StrikethroughButtonClick(View view) {

        ToggleFormatting(FORMATTING_OPTION.STRIKETHROUGH);

    }

    // Get the positions of the user's selection in the text. Returned as a size 2 int array.
    public int[] GetSelection() {

        // Get the selection start and end.
        int startSelection = contentEditText.getSelectionStart();
        int endSelection = contentEditText.getSelectionEnd();

        // Switch the start and ending selection, in case they are in the wrong order.
        if (startSelection > endSelection) {
            endSelection = contentEditText.getSelectionStart();
            startSelection = contentEditText.getSelectionEnd();
        }

        return new int[]{startSelection, endSelection};

    }

    // Toggles the formatting of the text (specific formatting option based on what's passed in the parameter).
    public void ToggleFormatting(FORMATTING_OPTION option) {

        // The formatting is not present on API <= 23.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        // Get the positions (start and end) of the user's selection.
        int[] selection = GetSelection();

        // Get the full text of the note.
        Editable fullText = contentEditText.getText();

        // Split the full text into the text before the user's selection, the selection itself, and the text after.
        SpannableString[] spannableStrings = new SpannableString[]{
                new SpannableString(fullText.subSequence(0, selection[0])),
                new SpannableString(fullText.subSequence(selection[0], selection[1])),
                new SpannableString(fullText.subSequence(selection[1], fullText.length()))
        };   

        // Get the HTML versions of the texts.
        String textBefore = JotsHtmlParser.toHtml(spannableStrings[0]);
        String selectedText = JotsHtmlParser.toHtml(spannableStrings[1]);
        String textAfter = JotsHtmlParser.toHtml(spannableStrings[2]);

        // Loading the HTML tags based on which formatting button was clicked.
        String[] htmlTags;
        switch (option) {

            case BOLD:
                htmlTags = JotsHtmlParser.boldTags;
                break;

            case ITALICS:
                htmlTags = JotsHtmlParser.italicsTags;
                break;

            case UNDERLINE:
                htmlTags = JotsHtmlParser.underlineTags;
                break;

            case STRIKETHROUGH:
                htmlTags = JotsHtmlParser.strikethroughTags;
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + option);
        }

        // If the selection actually contains any text, format it.
        if (!selectedText.equals("") || selection[0] != selection[1]) {

            Spannable formattedSelection;

            @SuppressWarnings("UnnecessaryLocalVariable") // The local variable selectedString is needed for better code readability and possible future editing.
            String selectedString = selectedText;

            // Split the selected string into individual lines - it's easier to work with HTML formatting on a line-by-line basis.
            String[] selectedStringLines = selectedString.split("[\\r\\n]");

            // If both of the tags are found in the text at least once, the formatting should remove the tags from all lines. If not, it should add them to all lines.
            boolean textContainsFormatting = selectedString.contains(htmlTags[0]) && selectedString.contains(htmlTags[1]);

            SpannableStringBuilder lines = new SpannableStringBuilder();

            // For each of the individual lines the user selected:
            for (String selectedLine : selectedStringLines) {

                Spannable formattedLine;

                // If the line needs to be formatted, add the tags.:
                if (!textContainsFormatting) {

                    // Add the HTML tags to the beginning and end of the line.
                    formattedLine = new SpannableStringBuilder()
                            .append(htmlTags[0])
                            .append(selectedLine)
                            .append(htmlTags[1]);

                // If the text already has the formatting, it needs to be removed:
                } else {

                    // Remove the HTML tags from the line.
                    formattedLine = new SpannableString(selectedLine.replace(htmlTags[0], "").replace(htmlTags[1], ""));

                }

                // Add the current line into the final list of lines.
                lines.append(formattedLine);

            }

            formattedSelection = new SpannableStringBuilder(lines);

            // Get the text out of the formatted selection.
            String finalString = formattedSelection.toString();

            // Add the text that was present before the user's selection, if there is any.
            if (textBefore.length() > 0) finalString = textBefore + finalString;

            // Add the text that was present after the user's selection, if there is any.
            if (textAfter.length() > 0) finalString = finalString + textAfter;

            // Get the formatted spanned.
            Spanned finalSpanned = Html.fromHtml(finalString, Html.FROM_HTML_MODE_COMPACT);

            // Set the text of the content to the formatted spanned.
            contentEditText.setText(finalSpanned);

            // Make the cursor stay after the selected text; the Math.min() ensures the selection doesn't go beyond the bounds of the text.
            contentEditText.setSelection(Math.min(selection[1], contentEditText.length()));

        // TODO: Implement a formatting solution for when no text is selected.
        // If the user's selection is empty, let them know to select text to format it.
        } else {

            // Let the user know that they have to select some text in order to format it.
            Toast.makeText(getContext(), getString(R.string.note_formatting_noselection), Toast.LENGTH_SHORT).show();

        }

    }

    // Execute the animation of the formatting bar sliding down.
    public static void slide_down(Context ctx, View v) {

        Animation a = AnimationUtils.loadAnimation(ctx, R.anim.slide_down);
        if (a != null) {
            a.reset();
            if (v != null) {
                v.clearAnimation();
                v.startAnimation(a);
            }
        }
    }

    // Execute the animation of the formatting bar sliding back up.
    public static void slide_up(Context ctx, View v) {

        Animation a = AnimationUtils.loadAnimation(ctx, R.anim.slide_up);
        if (a != null) {
            a.reset();
            if (v != null) {
                v.clearAnimation();
                v.startAnimation(a);
            }
        }
    }

}