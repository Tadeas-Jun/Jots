package com.eledris.jots;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    public SettingsFragment() {
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
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SetupDateFormatDropdown(view);
        SetupSwitches(view);

    }

    private void SetupDateFormatDropdown(View attachedView) {

        // Get all the date formats from date_formats.xml, such as 'dd-MM-yyyy hh:mm', and load them into the dropdown.
        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(getContext(), R.array.date_formats, R.layout.dropdown_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        AutoCompleteTextView menu = attachedView.findViewById(R.id.settings_dateformat_textview);
        menu.setAdapter(arrayAdapter);

        // Set the selected dropdown item to the current selected date format.
        menu.setText(MainActivity.selectedDateFormat, false);

        // Set up onClickListener for when an option is selected from the dropdown.
        menu.setOnItemClickListener((adapterView, view, i, l) -> {

            // Get the selected date.
            String newDateFormat = adapterView.getItemAtPosition(i).toString();

            // Set the selected date and save it in sharedPreferences.
            MainActivity.sharedPreferences.edit().putString("selectedDateFormat", newDateFormat).apply();
            MainActivity.selectedDateFormat = newDateFormat;

        });
    }

    // Set up all the individual switch buttons.
    private void SetupSwitches(View attachedView) {

        // Set up switch button for turning on/off hints in empty notes.
        SwitchMaterial hintSwitch = attachedView.findViewById(R.id.settings_switch_hints);
        SetupSwitch(hintSwitch, "settings_hints", getResources().getBoolean(R.bool.settings_hints_defaultValue));

        // Set up switch button for turning on/off the divider below the note title.
        SwitchMaterial dividerSwitch = attachedView.findViewById(R.id.settings_switch_divider);
        SetupSwitch(dividerSwitch, "settings_titledivider", getResources().getBoolean(R.bool.settings_titleDivider_defaultValue));

        // Set up switch button for turning on/off toolbar title in note.
        SwitchMaterial toolbarSwitch = attachedView.findViewById(R.id.settings_switch_toolbar);
        SetupSwitch(toolbarSwitch, "settings_hidetoolbartitle", getResources().getBoolean(R.bool.settings_hideToolbarTitle_defaultValue));

    }

    // Set up individual switch but the button, the named of the setting sharedPreference, and the default value boolean.
    private void SetupSwitch(SwitchMaterial switchButton, String sharedPreferenceName, boolean defaultValue) {

        // Load the sharedPreference boolean and set up the switch to reflect it.
        boolean sharedPreferencesBoolean = MainActivity.sharedPreferences.getBoolean(sharedPreferenceName, defaultValue);
        switchButton.setChecked(sharedPreferencesBoolean);

        // Save the new boolean value to the sharedPreferences.
        switchButton.setOnCheckedChangeListener((compoundButton, b) ->
                MainActivity.sharedPreferences.edit().putBoolean(sharedPreferenceName, b).apply()
        );

    }
}