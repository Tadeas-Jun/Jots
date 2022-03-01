package com.eledris.jots;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;

public class NotesFragment extends Fragment {

    JotsRecyclerViewAdapter adapter;

    public NotesFragment() {
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
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find all the user's notes and put them in the database.
        MainActivity ma = ((MainActivity) requireActivity());
        ma.FindAllNotes();

        // Create the list to populate the RecyclerView with.
        ArrayList<Note> notes = MainActivity.allNotes;

        // Sort the notes in order of date last edited, in descending order (using the Note.compareTo function).
        Collections.sort(notes, Collections.reverseOrder());

        // Unlock drawer menu if it's already instantiated - mainly when coming back from another fragment.
        if(ma.drawerLayout != null) ma.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        // Clear the options menu, if it's already instantiated - mainly when coming back from another fragment.
        if(ma.toolbar != null) ma.clearOptionsMenu();

        // Add the onClickListener to the 'Create new note...' button.
        view.findViewById(R.id.notes_addNew).setOnClickListener(this::AddNewNote);

        // Set up the RecyclerView with all the notes.
        RecyclerView recyclerView = view.findViewById(R.id.rv_notes);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new JotsRecyclerViewAdapter(requireContext(), notes);
        adapter.setClickListener(this::onItemClick);

        recyclerView.setAdapter(adapter);
        registerForContextMenu(recyclerView);

        // Hide the keyboard (mainly used when coming back from a Note fragment).
        MainActivity.hideKeyboard(requireActivity());

    }

    // When creating the context menu that shows up when the user taps and holds over a note.
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {

        // Android 7+ displays the context menu as a small pop-up, not as a full modal. Because of this, it's useless to set up the header title view on Android versions 7+.
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {

            // Setup the header TextView.
            TextView headerView = new TextView(getContext());
            String noteTitle = adapter.getSelectedItem().title;
            String title = noteTitle.length() == 0 ? getString(R.string.note_untitled) : noteTitle;
            headerView.setText(title);
            headerView.setTextAppearance(R.style.Jots_Text_ContextualHeading);

            // Setting the font.
            TypedValue typedValue = new TypedValue();
            requireActivity().getTheme().resolveAttribute(R.attr.headingFontFamily, typedValue, true);
            Typeface typeface = ResourcesCompat.getFont(requireContext(), typedValue.resourceId);
            headerView.setTypeface(typeface);

            headerView.setPadding(45, 40, 0, 0);

            menu.setHeaderView(headerView);

        }

        MenuInflater inflater = requireActivity().getMenuInflater();
        inflater.inflate(R.menu.note_contextual, menu);

    }

    // Fires when a user taps on one of the contextItems, such as Share note and Delete.
    @Override
    public boolean onContextItemSelected(MenuItem item) {

        // Find the item that was tapped on.
        Note selectedItem = adapter.getSelectedItem();

        // Execute the appropriate action for the clicked item.
        int itemId = item.getItemId();
        if(itemId == R.id.contextual_delete) {

            // Delete the selected note.
            DeleteNoteFromList(adapter.getCurrentPos());

        } else if (itemId == R.id.contextual_share) {

            // Share the selected note.
            ShareNote(selectedItem);

        }

        return super.onContextItemSelected(item);

    }

    // Fires when a user clicks a note.
    public void onItemClick(View view, int position) {

        // Navigate to the note fragment.
        MainActivity ma = ((MainActivity)requireActivity());
        ma.CustomNavigate(R.id.action_notesFragment_to_noteFragment, MainActivity.NoteToBundle(adapter.getItem(position)));

    }

    // Delete a note from the list and from the user's device.
    public void DeleteNoteFromList(int position) {

        // Delete the note.
        MainActivity ma = ((MainActivity)requireActivity());
        ma.DeleteNote(adapter.getItem(position).id);

        // Notify the list that the note has been deleted.
        adapter.notifyItemRemoved(position);

        // Notify the user that the note has been deleted.
        Toast.makeText(getContext(), getString(R.string.notes_deleted), Toast.LENGTH_SHORT).show();

    }

    // Share the note's title and contents.
    public void ShareNote(Note note) {

        // Get the contents of the note.
        String noteContent = Html.fromHtml(note.content).toString();

        // Create a new sendIntent with the note's title, a line break, and its content.
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT,
                note.title + "\n" + noteContent);
        sendIntent.setType("text/plain");

        // Start a shareIntent with the text.
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);

    }

    // Add a new note to the list.
    public void AddNewNote(View view) {

        ((MainActivity)requireActivity()).CreateNote();

    }

}
