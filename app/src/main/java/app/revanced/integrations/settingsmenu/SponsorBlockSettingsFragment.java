package app.revanced.integrations.settingsmenu;

import static app.revanced.integrations.utils.StringRef.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.sponsorblock.SponsorBlockSettings;
import app.revanced.integrations.sponsorblock.SponsorBlockUtils;
import app.revanced.integrations.sponsorblock.objects.EditTextListPreference;
import app.revanced.integrations.sponsorblock.requests.SBRequester;
import app.revanced.integrations.utils.SharedPrefHelper;
import app.revanced.integrations.whitelist.WhitelistType;

public class SponsorBlockSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final DecimalFormat FORMATTER = new DecimalFormat("#,###,###");
    public static final String SAVED_TEMPLATE = "%dh %.1f %s";
    private static final APIURLChangeListener API_URL_CHANGE_LISTENER = new APIURLChangeListener();
    private static final APIMIRRORURLChangeListener API_MIRROR_URL_CHANGE_LISTENER = new APIMIRRORURLChangeListener();
    private final ArrayList<Preference> preferencesToDisableWhenSBDisabled = new ArrayList<>();
    private final ArrayList<Preference> preferencesToDisableWhenSBAPIDisabled = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(SharedPrefHelper.SharedPrefNames.SPONSOR_BLOCK.getName());

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        final Activity context = this.getActivity();

        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(preferenceScreen);

        SponsorBlockSettings.update(context);

        {
            SwitchPreference preference = new SwitchPreference(context);
            preferenceScreen.addPreference(preference);
            preference.setKey(SettingsEnum.SB_ENABLED.getPath());
            preference.setDefaultValue(SettingsEnum.SB_ENABLED.getDefaultValue());
            preference.setChecked(SettingsEnum.SB_ENABLED.getBoolean());
            preference.setTitle(str("enable_sb"));
            preference.setSummary(str("enable_sb_sum"));
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                final boolean value = (Boolean) newValue;
                enableCategoriesIfNeeded(value);
                SettingsEnum.SB_ENABLED.saveValue(value);
                return true;
            });
        }

        {
            SwitchPreference preference = new SwitchPreference(context);
            preferenceScreen.addPreference(preference);
            preference.setKey(SettingsEnum.SB_MIRROR_ENABLED.getPath());
            preference.setDefaultValue(SettingsEnum.SB_MIRROR_ENABLED.getDefaultValue());
            preference.setChecked(SettingsEnum.SB_MIRROR_ENABLED.getBoolean());
            preference.setTitle(str("enable_sb_mirror"));
            preference.setSummary(str("enable_sb_mirror_sum"));
            preferencesToDisableWhenSBDisabled.add(preference);
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                final boolean value = (Boolean) newValue;
                enablePreferenceIfNeeded(value);
                SettingsEnum.SB_MIRROR_ENABLED.saveValue(value);
                return true;
            });
        }

        {
            SwitchPreference preference = new SwitchPreference(context);
            preferenceScreen.addPreference(preference);
            preference.setKey(SettingsEnum.SB_NEW_SEGMENT_ENABLED.getPath());
            preference.setDefaultValue(SettingsEnum.SB_NEW_SEGMENT_ENABLED.getDefaultValue());
            preference.setChecked(SettingsEnum.SB_NEW_SEGMENT_ENABLED.getBoolean());
            preference.setTitle(str("enable_segmadding"));
            preference.setSummary(str("enable_segmadding_sum"));
            preferencesToDisableWhenSBDisabled.add(preference);
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                final boolean value = (Boolean) newValue;
                if (value && !SettingsEnum.SB_SEEN_GUIDELINES.getBoolean()) {
                    new AlertDialog.Builder(pref.getContext())
                            .setTitle(str("sb_guidelines_popup_title"))
                            .setMessage(str("sb_guidelines_popup_content"))
                            .setNegativeButton(str("sb_guidelines_popup_already_read"), null)
                            .setPositiveButton(str("sb_guidelines_popup_open"), (dialogInterface, i) -> openGuidelines())
                            .show();
                            SettingsEnum.SB_SEEN_GUIDELINES.saveValue(true);
                }
                SettingsEnum.SB_NEW_SEGMENT_ENABLED.saveValue(value);
                return true;
            });
        }

        {
            SwitchPreference preference = new SwitchPreference(context);
            preferenceScreen.addPreference(preference);
            preference.setTitle(str("enable_voting"));
            preference.setSummary(str("enable_voting_sum"));
            preference.setKey(SettingsEnum.SB_VOTING_ENABLED.getPath());
            preference.setDefaultValue(SettingsEnum.SB_VOTING_ENABLED.getDefaultValue());
            preference.setChecked(SettingsEnum.SB_VOTING_ENABLED.getBoolean());
            preferencesToDisableWhenSBDisabled.add(preference);
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                final boolean value = (Boolean) newValue;
                SettingsEnum.SB_VOTING_ENABLED.saveValue(value);
                return true;
            });
        }

        addGeneralCategory(context, preferenceScreen);
        addSegmentsCategory(context, preferenceScreen);
        addStatsCategory(context, preferenceScreen);
        addAboutCategory(context, preferenceScreen);

        enableCategoriesIfNeeded(SettingsEnum.SB_ENABLED.getBoolean());
        enablePreferenceIfNeeded(SettingsEnum.SB_MIRROR_ENABLED.getBoolean());
    }

    private void openGuidelines() {
        final Context context = getActivity();
        SettingsEnum.SB_SEEN_GUIDELINES.saveValue(true);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://wiki.sponsor.ajay.app/w/Guidelines"));
        context.startActivity(intent);
    }

    private void enableCategoriesIfNeeded(boolean value) {
        for (Preference preference : preferencesToDisableWhenSBDisabled)
            preference.setEnabled(value);
    }

    private void enablePreferenceIfNeeded(boolean value) {
        for (Preference preference : preferencesToDisableWhenSBAPIDisabled)
            preference.setEnabled(value);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    private void addSegmentsCategory(Context context, PreferenceScreen screen) {
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        preferencesToDisableWhenSBDisabled.add(category);
        category.setTitle(str("diff_segments"));

        SponsorBlockSettings.SegmentBehaviour[] segmentBehaviours = SponsorBlockSettings.SegmentBehaviour.values();
        String[] entries = new String[segmentBehaviours.length];
        String[] entryValues = new String[segmentBehaviours.length];
        for (int i = 0, segmentBehavioursLength = segmentBehaviours.length; i < segmentBehavioursLength; i++) {
            SponsorBlockSettings.SegmentBehaviour behaviour = segmentBehaviours[i];
            entries[i] = behaviour.getRef().toString();
            entryValues[i] = behaviour.getKey();
        }

        SponsorBlockSettings.SegmentInfo[] categories = SponsorBlockSettings.SegmentInfo.values();

        for (SponsorBlockSettings.SegmentInfo segmentInfo : categories) {
            EditTextListPreference preference = new EditTextListPreference(context);
            preference.setTitle(segmentInfo.getTitleWithDot());
            preference.setSummary(segmentInfo.getDescription().toString());
            preference.setKey(segmentInfo.getKey());
            preference.setDefaultValue(segmentInfo.getBehaviour().getKey());
            preference.setEntries(entries);
            preference.setEntryValues(entryValues);

            if (segmentInfo.getKey().equals("unsubmitted")) {
                category.removePreference(preference);
            } else {
                category.addPreference(preference);
            }
        }

        Preference colorPreference = new Preference(context); // TODO remove this after the next major update
        screen.addPreference(colorPreference);
        colorPreference.setTitle(str("color_change"));
        colorPreference.setSummary(str("color_change_sum"));
        colorPreference.setSelectable(false);
        preferencesToDisableWhenSBDisabled.add(colorPreference);
    }

    private void addStatsCategory(Context context, PreferenceScreen screen) {
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        category.setTitle(str("stats"));
        preferencesToDisableWhenSBDisabled.add(category);

        {
            Preference preference = new Preference(context);
            category.addPreference(preference);
            preference.setTitle(str("stats_loading"));
            preference.setSelectable(false);

            SBRequester.retrieveUserStats(category, preference);
        }
    }

    private void addAboutCategory(Context context, PreferenceScreen screen) {
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        category.setTitle(str("about"));

        {
            Preference preference = new Preference(context);
            screen.addPreference(preference);
            preference.setTitle(str("about_api"));
            preference.setSummary(str("about_api_sum"));
            preference.setOnPreferenceClickListener(pref -> {
                var intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://sponsor.ajay.app"));
                pref.getContext().startActivity(intent);
                return false;
            });
        }

        {
            Preference preference = new Preference(context);
            screen.addPreference(preference);
            preference.setTitle(str("about_madeby"));
            preference.setSelectable(false);
        }

    }

    private void addGeneralCategory(final Context context, PreferenceScreen screen) {
        final PreferenceCategory category = new PreferenceCategory(context);
        preferencesToDisableWhenSBDisabled.add(category);
        screen.addPreference(category);
        category.setTitle(str("general"));

        {
            Preference preference = new Preference(context);
            preference.setTitle(str("sb_guidelines_preference_title"));
            preference.setSummary(str("sb_guidelines_preference_sum"));
            preference.setOnPreferenceClickListener(pref -> {
                openGuidelines();
                return false;
            });
            screen.addPreference(preference);
        }

        {
            SwitchPreference preference = new SwitchPreference(context);
            preference.setTitle(str("general_skiptoast"));
            preference.setChecked(SettingsEnum.SB_SHOW_TOAST_WHEN_SKIP.getBoolean());
            preference.setSummary(str("general_skiptoast_sum"));
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                Toast.makeText(pref.getContext(), str("skipped_sponsor"), Toast.LENGTH_SHORT).show();
                SettingsEnum.SB_SHOW_TOAST_WHEN_SKIP.saveValue(newValue);
                return true;
            });
            preferencesToDisableWhenSBDisabled.add(preference);
            screen.addPreference(preference);
        }

        {
            Preference preference = new SwitchPreference(context);
            preference.setTitle(str("general_skipcount"));
            preference.setSummary(str("general_skipcount_sum"));
            preference.setKey(SettingsEnum.SB_COUNT_SKIPS.getPath());
            preference.setDefaultValue(SettingsEnum.SB_COUNT_SKIPS.getDefaultValue());
            preferencesToDisableWhenSBDisabled.add(preference);
            screen.addPreference(preference);
        }

        {
            SwitchPreference preference = new SwitchPreference(context);
            preference.setTitle(str("general_time_without_sb"));
            preference.setSummary(str("general_time_without_sb_sum"));
            preference.setChecked(SettingsEnum.SB_SHOW_TIME_WITHOUT_SEGMENTS.getBoolean());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                SettingsEnum.SB_SHOW_TIME_WITHOUT_SEGMENTS.saveValue(newValue);
                return true;
            });
            preferencesToDisableWhenSBDisabled.add(preference);
            screen.addPreference(preference);
        }

        {
            EditTextPreference preference = new EditTextPreference(context);
            preference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
            preference.setTitle(str("general_adjusting"));
            preference.setText(String.valueOf(SettingsEnum.SB_ADJUST_NEW_SEGMENT_STEP.getInt()));
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                SettingsEnum.SB_ADJUST_NEW_SEGMENT_STEP.saveValue(Integer.valueOf(newValue.toString()));
                return true;
            });
            preference.setDefaultValue(SettingsEnum.SB_ADJUST_NEW_SEGMENT_STEP.getDefaultValue() + "");
            screen.addPreference(preference);
            preferencesToDisableWhenSBDisabled.add(preference);
        }

        {
            EditTextPreference preference = new EditTextPreference(context);
            preference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            preference.setTitle(str("general_min_duration"));
            preference.setSummary(str("general_min_duration_sum"));
            preference.setText(String.valueOf(SettingsEnum.SB_MIN_DURATION.getFloat()));
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                SettingsEnum.SB_MIN_DURATION.saveValue(Float.valueOf(newValue.toString()));
                return true;
            });
            screen.addPreference(preference);
            preferencesToDisableWhenSBDisabled.add(preference);
        }

        {
            EditTextPreference preference = new EditTextPreference(context);
            preference.setTitle(str("general_uuid"));
            preference.setSummary(str("general_uuid_sum"));
            preference.setText(SettingsEnum.SB_UUID.getString());
            preference.setOnPreferenceChangeListener((preference1, newValue) -> {
                SettingsEnum.SB_UUID.saveValue(newValue.toString());
                return true;
            });
            screen.addPreference(preference);
            preferencesToDisableWhenSBDisabled.add(preference);
        }

        {
            Preference preference = new Preference(context);
            String title = str("general_api_url");
            preference.setTitle(title);
            preference.setSummary(Html.fromHtml(str("general_api_url_sum")));
            preference.setOnPreferenceClickListener(pref -> {
                EditText editText = new EditText(context);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                editText.setText(SettingsEnum.SB_API_URL.getString());

                API_URL_CHANGE_LISTENER.setEditTextRef(editText);
                new AlertDialog.Builder(context)
                        .setTitle(title)
                        .setView(editText)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(str("reset"), API_URL_CHANGE_LISTENER)
                        .setPositiveButton(android.R.string.ok, API_URL_CHANGE_LISTENER)
                        .show();
                return true;
            });

            screen.addPreference(preference);
            preferencesToDisableWhenSBDisabled.add(preference);
        }

        {
            Preference preference = new Preference(context);
            String title = str("general_api_mirror_url");
            preference.setTitle(title);
            preference.setSummary(Html.fromHtml(str("general_api_mirror_url_sum")));
            preference.setOnPreferenceClickListener(pref -> {
                EditText editText = new EditText(context);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                editText.setText(SettingsEnum.SB_API_MIRROR_URL.getString());

                API_MIRROR_URL_CHANGE_LISTENER.setEditTextRef(editText);
                new AlertDialog.Builder(context)
                        .setTitle(title)
                        .setView(editText)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(str("reset"), API_MIRROR_URL_CHANGE_LISTENER)
                        .setPositiveButton(android.R.string.ok, API_MIRROR_URL_CHANGE_LISTENER)
                        .show();
                return true;
            });

            screen.addPreference(preference);
            preferencesToDisableWhenSBAPIDisabled.add(preference);
            preferencesToDisableWhenSBDisabled.add(preference);
        }

        {
            EditTextPreference preference = new EditTextPreference(context);
            Context applicationContext = context.getApplicationContext();

            preference.setTitle(str("settings_ie"));
            preference.setSummary(str("settings_ie_sum"));
            preference.setText(SponsorBlockUtils.exportSettings(applicationContext));
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                SponsorBlockUtils.importSettings((String) newValue, applicationContext);
                return false;
            });
            screen.addPreference(preference);
            preferencesToDisableWhenSBDisabled.add(preference);
        }
    }

    private static class APIURLChangeListener implements DialogInterface.OnClickListener {
        private WeakReference<EditText> editTextRef;

        @Override
        public void onClick(DialogInterface dialog, int which) {
            EditText editText = editTextRef.get();
            if (editText == null)
                return;
            Context context = ((AlertDialog) dialog).getContext();
            Context applicationContext = context.getApplicationContext();

            switch (which) {
                case DialogInterface.BUTTON_NEUTRAL:
                    SettingsEnum.SB_API_URL.saveValue(SettingsEnum.SB_API_URL.getDefaultValue());
                    Toast.makeText(applicationContext, str("api_url_reset"), Toast.LENGTH_SHORT).show();
                    break;
                case DialogInterface.BUTTON_POSITIVE:
                    Editable text = editText.getText();
                    Toast invalidToast = Toast.makeText(applicationContext, str("api_url_invalid"), Toast.LENGTH_SHORT);
                    if (text == null) {
                        invalidToast.show();
                    } else {
                        String textAsString = text.toString();
                        if (textAsString.isEmpty() || !Patterns.WEB_URL.matcher(textAsString).matches()) {
                            invalidToast.show();
                        } else {
                            SettingsEnum.SB_API_URL.saveValue(textAsString);
                            Toast.makeText(applicationContext, str("api_url_changed"), Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
            }
        }

        public void setEditTextRef(EditText editText) {
            editTextRef = new WeakReference<>(editText);
        }
    }

    private static class APIMIRRORURLChangeListener implements DialogInterface.OnClickListener {
        private WeakReference<EditText> editTextRef;

        @Override
        public void onClick(DialogInterface dialog, int which) {
            EditText editText = editTextRef.get();
            if (editText == null)
                return;
            Context context = ((AlertDialog) dialog).getContext();
            Context applicationContext = context.getApplicationContext();

            switch (which) {
                case DialogInterface.BUTTON_NEUTRAL:
                    SettingsEnum.SB_API_MIRROR_URL.saveValue(SettingsEnum.SB_API_MIRROR_URL.getDefaultValue());
                    Toast.makeText(applicationContext, str("api_mirror_url_reset"), Toast.LENGTH_SHORT).show();
                    break;
                case DialogInterface.BUTTON_POSITIVE:
                    Editable text = editText.getText();
                    Toast invalidToast = Toast.makeText(applicationContext, str("api_mirror_url_invalid"), Toast.LENGTH_SHORT);
                    if (text == null) {
                        invalidToast.show();
                    } else {
                        String textAsString = text.toString();
                        if (textAsString.isEmpty() || !Patterns.WEB_URL.matcher(textAsString).matches()) {
                            invalidToast.show();
                        } else {
                            SettingsEnum.SB_API_MIRROR_URL.saveValue(textAsString);
                            Toast.makeText(applicationContext, str("api_mirror_url_changed"), Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
            }
        }

        public void setEditTextRef(EditText editText) {
            editTextRef = new WeakReference<>(editText);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SponsorBlockSettings.update(getActivity());
    }
}
