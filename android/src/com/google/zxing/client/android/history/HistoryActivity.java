/*
 * Copyright 2012 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android.history;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.zxing.client.android.CaptureActivity;
import com.google.zxing.client.android.Intents;
import com.google.zxing.client.android.R;

import java.util.HashMap;
import java.util.Map;

public final class HistoryActivity extends ListActivity {

    private static final String TAG = HistoryActivity.class.getSimpleName();

    private HistoryManager historyManager;
    private ArrayAdapter<HistoryItem> adapter;
    private CharSequence originalTitle;

    private final Map<HistoryItem, Integer> selectedHistoryItems = new HashMap<HistoryItem, Integer>();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.historyManager = new HistoryManager(this);

        adapter = new HistoryItemAdapter(this);
        setListAdapter(adapter);

        final ListView listview = getListView();
        listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listview.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                  long id, boolean checked) {
                // Here you can do something when items are selected/de-selected,
                // such as update the title in the CAB
                if (checked) {
                    selectedHistoryItems.put(adapter.getItem(position), position);
                } else {
                    selectedHistoryItems.remove(adapter.getItem(position));
                }
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // Respond to clicks on the actions in the CAB
                switch (item.getItemId()) {
                    case R.id.menu_history_contextual_delete:
                        for (Integer historyItemPosition : selectedHistoryItems.values()) {
                            historyManager.deleteHistoryItem(historyItemPosition);
                        }
                        mode.finish(); // Action picked, so close the CAB
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate the menu for the CAB
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.history_context, menu);
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Here you can make any necessary updates to the activity when
                // the CAB is removed. By default, selected items are deselected/unchecked.
                reloadHistoryItems();
                selectedHistoryItems.clear();
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // Here you can perform updates to the CAB due to
                // an invalidate() request
                return false;
            }
        });
        originalTitle = getTitle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadHistoryItems();
    }

    private void reloadHistoryItems() {
        Iterable<HistoryItem> items = historyManager.buildHistoryItems();
        adapter.clear();
        for (HistoryItem item : items) {
            adapter.add(item);
        }
        setTitle(originalTitle + " (" + adapter.getCount() + ')');
        if (adapter.isEmpty()) {
            adapter.add(new HistoryItem(null, null, null));
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (adapter.getItem(position).getResult() != null) {
            Intent intent = new Intent(this, CaptureActivity.class);
            intent.putExtra(Intents.History.ITEM_NUMBER, position);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
                                    View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        if (position >= adapter.getCount() || adapter.getItem(position).getResult() != null) {
            menu.add(Menu.NONE, position, position, R.string.history_clear_one_history_text);
        } // else it's just that dummy "Empty" message
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = item.getItemId();
        historyManager.deleteHistoryItem(position);
        reloadHistoryItems();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (historyManager.hasHistoryItems()) {
            MenuInflater menuInflater = getMenuInflater();
            menuInflater.inflate(R.menu.history, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_history_send:
                CharSequence history = historyManager.buildHistory();
                Parcelable historyFile = HistoryManager.saveHistory(history.toString());
                if (historyFile == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.msg_unmount_usb);
                    builder.setPositiveButton(R.string.button_ok, null);
                    builder.show();
                } else {
                    Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse("mailto:"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    String subject = getResources().getString(R.string.history_email_title);
                    intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                    intent.putExtra(Intent.EXTRA_TEXT, subject);
                    intent.putExtra(Intent.EXTRA_STREAM, historyFile);
                    intent.setType("text/csv");
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException anfe) {
                        Log.w(TAG, anfe.toString());
                    }
                }
                break;
            case R.id.menu_history_clear_text:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.msg_sure);
                builder.setCancelable(true);
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i2) {
                        historyManager.clearHistory();
                        dialog.dismiss();
                        finish();
                    }
                });
                builder.setNegativeButton(R.string.button_cancel, null);
                builder.show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

}
