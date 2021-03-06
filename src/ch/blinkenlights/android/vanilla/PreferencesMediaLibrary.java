/*
 * Copyright (C) 2017 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ch.blinkenlights.android.vanilla;

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class PreferencesMediaLibrary extends Fragment implements View.OnClickListener
{
	/**
	 * The ugly timer which fires every 200ms
	 */
	private Timer mTimer;
	/**
	 * Our start button
	 */
	private View mStartButton;
	/**
	 * The cancel button
	 */
	private View mCancelButton;
	/**
	 * The debug / progress text describing the scan status
	 */
	private TextView mProgress;
	/**
	 * The number of tracks on this device
	 */;
	private TextView mStatsTracks;
	/**
	 * The number of hours of music we have
	 */
	private TextView mStatsPlaytime;
	/**
	 * Checkbox for full scan
	 */
	private CheckBox mFullScanCheck;
	/**
	 * Checkbox for drop
	 */
	private CheckBox mDropDbCheck;
	/**
	 * Checkbox for album group option
	 */
	private CheckBox mGroupAlbumsCheck;
	/**
	 * Checkbox for targreader flavor
	 */
	private CheckBox mForceBastpCheck;
	/**
	 * Set if we should start a full scan due to option changes
	 */
	private boolean mFullScanPending;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.medialibrary_preferences, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mStartButton = (View)view.findViewById(R.id.start_button);
		mCancelButton = (View)view.findViewById(R.id.cancel_button);
		mProgress = (TextView)view.findViewById(R.id.media_stats_progress);
		mStatsTracks = (TextView)view.findViewById(R.id.media_stats_tracks);
		mStatsPlaytime = (TextView)view.findViewById(R.id.media_stats_playtime);
		mFullScanCheck = (CheckBox)view.findViewById(R.id.media_scan_full);
		mDropDbCheck = (CheckBox)view.findViewById(R.id.media_scan_drop_db);
		mGroupAlbumsCheck = (CheckBox)view.findViewById(R.id.media_scan_group_albums);
		mForceBastpCheck = (CheckBox)view.findViewById(R.id.media_scan_force_bastp);

		// Bind onClickListener to some elements
		mStartButton.setOnClickListener(this);
		mCancelButton.setOnClickListener(this);
		mGroupAlbumsCheck.setOnClickListener(this);
		mForceBastpCheck.setOnClickListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		mTimer = new Timer();
		// Yep: its as ugly as it seems: we are POLLING
		// the database.
		mTimer.scheduleAtFixedRate((new TimerTask() {
			@Override
			public void run() {
				getActivity().runOnUiThread(new Runnable(){
					public void run() {
						updateProgress();
					}
				});
			}}), 0, 200);

		updatePreferences(null);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}

		if (mFullScanPending) {
			MediaLibrary.startLibraryScan(getActivity(), true, true);
			mFullScanPending = false;
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.start_button:
				startButtonPressed(view);
				break;
			case R.id.cancel_button:
				cancelButtonPressed(view);
				break;
			case R.id.media_scan_group_albums:
			case R.id.media_scan_force_bastp:
				confirmUpdatePreferences((CheckBox)view);
				break;
		}
	}

	/**
	 * Wrapper for updatePreferences() which warns the user about
	 * possible consequences.
	 *
	 * @param checkbox the checkbox which was changed
	 */
	private void confirmUpdatePreferences(final CheckBox checkbox) {
		if (mFullScanPending) {
			// User was already warned, so we can just dispatch this
			// without nagging again
			updatePreferences(checkbox);
			return;
		}

		new AlertDialog.Builder(getActivity())
			.setTitle(R.string.media_scan_preferences_change_title)
			.setMessage(R.string.media_scan_preferences_change_message)
			.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					mFullScanPending = true;
					updatePreferences(checkbox);
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// restore old condition if use does not want to proceed
					checkbox.setChecked(!checkbox.isChecked());
				}
			})
			.show();
	}

	/**
	 * Initializes and updates the scanner preferences
	 *
	 * @param checkbox the item to update, may be null
	 * @return void but sets the checkboxes to their correct state
	 */
	private void updatePreferences(CheckBox checkbox) {
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(getActivity());

		if (checkbox == mGroupAlbumsCheck)
			prefs.groupAlbumsByFolder = mGroupAlbumsCheck.isChecked();
		if (checkbox == mForceBastpCheck)
			prefs.forceBastp = mForceBastpCheck.isChecked();

		MediaLibrary.setPreferences(getActivity(), prefs);

		mGroupAlbumsCheck.setChecked(prefs.groupAlbumsByFolder);
		mForceBastpCheck.setChecked(prefs.forceBastp);
	}

	/**
	 * Updates the view of this fragment with current information
	 */
	private void updateProgress() {
		Context context = getActivity();
		String scanText = MediaLibrary.describeScanProgress(getActivity());
		boolean scanIdle = scanText == null;

		mProgress.setText(scanText);
		mStartButton.setEnabled(scanIdle);
		mDropDbCheck.setEnabled(scanIdle);
		mFullScanCheck.setEnabled(scanIdle);
		mForceBastpCheck.setEnabled(scanIdle);
		mGroupAlbumsCheck.setEnabled(scanIdle);
		mCancelButton.setVisibility(scanIdle ? View.GONE : View.VISIBLE);

		Integer songCount = MediaLibrary.getLibrarySize(context);
		mStatsTracks.setText(songCount.toString());

		float playtime = calculateDuration(context) / 3600000F;
		mStatsPlaytime.setText(String.format("%.1f", playtime));
	}

	/**
	 * Queries the media library and calculates the total amount of playtime in ms
	 *
	 * @param context the context to use
	 * @return the play time of the library in ms
	 */
	public long calculateDuration(Context context) {
		long duration = 0;
		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_SONGS, new String[]{"SUM("+MediaLibrary.SongColumns.DURATION+")"}, null, null, null);
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				duration = cursor.getLong(0);
			}
			cursor.close();
		}
		return duration;
	}

	/**
	 * Called when the user hits the start button
	 *
	 * @param view the view which was pressed
	 */
	public void startButtonPressed(View view) {
		MediaLibrary.startLibraryScan(getActivity(), mFullScanCheck.isChecked(), mDropDbCheck.isChecked());
		updateProgress();
	}

	/**
	 * Called when the user hits the cancel button
	 *
	 * @param view the view which was pressed
	 */
	public void cancelButtonPressed(View view) {
		MediaLibrary.abortLibraryScan(getActivity());
		updateProgress();
	}

}
