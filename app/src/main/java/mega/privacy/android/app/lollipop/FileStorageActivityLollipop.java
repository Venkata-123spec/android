package mega.privacy.android.app.lollipop;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.FileDocument;
import mega.privacy.android.app.MegaPreferences;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.SimpleDividerItemDecoration;
import mega.privacy.android.app.lollipop.adapters.FileStorageLollipopAdapter;
import mega.privacy.android.app.utils.SDCardOperator;

import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.FileUtils.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;


public class FileStorageActivityLollipop extends PinActivityLollipop implements OnClickListener, RecyclerView.OnItemTouchListener, GestureDetector.OnGestureListener {

	public static final String EXTRA_URL = "fileurl";
	public static final String EXTRA_SIZE = "filesize";
	public static final String EXTRA_SERIALIZED_NODES = "serialized_nodes";
	public static final String EXTRA_DOCUMENT_HASHES = "document_hash";
	public static final String EXTRA_FROM_SETTINGS = "from_settings";
	public static final String EXTRA_SAVE_RECOVERY_KEY = "save_recovery_key";
	public static final String EXTRA_CAMERA_FOLDER = "camera_folder";
	public static final String EXTRA_BUTTON_PREFIX = "button_prefix";
	public static final String EXTRA_SD_ROOT = "sd_root";
	public static final String EXTRA_PATH = "filepath";
	public static final String EXTRA_FILES = "fileslist";
    public static final String EXTRA_PROMPT = "prompt";

	// Pick modes
	public enum Mode {
		// Select single folder
		PICK_FOLDER("ACTION_PICK_FOLDER"),
		// Pick one or multiple files or folders
		PICK_FILE("ACTION_PICK_FILE");

		private String action;

		Mode(String action) {
			this.action = action;
		}

		public String getAction() {
			return action;
		}

		public static Mode getFromIntent(Intent intent) {
			if (intent.getAction().equals(PICK_FILE.getAction())) {
				return PICK_FILE;
			} else {
				return PICK_FOLDER;
			}
		}
	}
	MegaPreferences prefs;
	DatabaseHandler dbH;
	Mode mode;
	
	private MenuItem newFolderMenuItem;
	
	private File path;
	private String camSyncLocalPath;
	private File root;
	private RelativeLayout viewContainer;
	private Button button;
	private TextView contentText;
	private RecyclerView listView;
	LinearLayoutManager mLayoutManager;
	private Button cancelButton;
	GestureDetectorCompat detector;
	ImageView emptyImageView;
	TextView emptyTextView;
	
	private Boolean fromSettings, fromSaveRecoveryKey;
	private Boolean cameraFolderSettings;
	private String sdRoot;
	private boolean hasSDCard;
    private String prompt;

	Stack<Integer> lastPositionStack;
	
	private String url;
	private long size;
	private long[] documentHashes;
	private ArrayList<String> serializedNodes;

	FileStorageLollipopAdapter adapter;
	Toolbar tB;
	ActionBar aB;
	
	float scaleH, scaleW;
	float density;
	DisplayMetrics outMetrics;
	Display display;
	
	private ActionMode actionMode;
	
	private AlertDialog newFolderDialog;

	String regex = "[*|\\?:\"<>\\\\\\\\/]";

	Handler handler;

	public class RecyclerViewOnGestureListener extends SimpleOnGestureListener{

	    public void onLongPress(MotionEvent e) {
			logDebug("onLongPress");
	    	
			if (mode == Mode.PICK_FILE) {
				logDebug("Mode.PICK_FILE");
				// handle long press
				if (!adapter.isMultipleSelect()){
					adapter.setMultipleSelect(true);

					actionMode = startSupportActionMode(new ActionBarCallBack());
				}
				super.onLongPress(e);
			}
	    }
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logDebug("onOptionsItemSelected");

		// Handle presses on the action bar items
	    switch (item.getItemId()) {
		    case android.R.id.home:{
		    	onBackPressed();
		    	return true;
		    }
		    case R.id.cab_menu_create_folder:{
		    	showNewFolderDialog();
		    	return true;
		    }
		    case R.id.cab_menu_select_all:{
		    	selectAll();
		    	return true;
		    }
		    case R.id.cab_menu_unselect_all:{
		    	clearSelections();
		    	return true;
		    }
		    default:{
	            return super.onOptionsItemSelected(item);
	        }
	    }
	}
	
	private class ActionBarCallBack implements ActionMode.Callback {
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			
			switch(item.getItemId()){
				case R.id.cab_menu_select_all:{
					selectAll();
					break;
				}
				case R.id.cab_menu_unselect_all:{
					clearSelections();
					break;
				}
			}
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.file_storage_action, menu);
			MenuItem newFolderItem = menu.findItem(R.id.cab_menu_create_folder);
			newFolderItem.setIcon(mutateIconSecondary(getApplicationContext(), R.drawable.ic_b_new_folder, R.color.white));
			changeStatusBarColorActionMode(getApplicationContext(), getWindow(), handler, 1);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode arg0) {
			clearSelections();
			adapter.setMultipleSelect(false);
			changeStatusBarColorActionMode(getApplicationContext(), getWindow(), handler, 0);
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			List<FileDocument> selected = adapter.getSelectedDocuments();
			
			if (selected.size() != 0) {				
				
				if(selected.size()==adapter.getItemCount()){
					menu.findItem(R.id.cab_menu_select_all).setVisible(false);
					menu.findItem(R.id.cab_menu_unselect_all).setVisible(true);			
				}
				else{
					menu.findItem(R.id.cab_menu_select_all).setVisible(true);
					menu.findItem(R.id.cab_menu_unselect_all).setVisible(true);	
				}	
			}
			else{
				menu.findItem(R.id.cab_menu_select_all).setVisible(true);
				menu.findItem(R.id.cab_menu_unselect_all).setVisible(false);
			}
			
			if (!(mode.equals(Mode.PICK_FOLDER))) {
				logDebug("Not Mode.PICK_FOLDER");
				menu.findItem(R.id.cab_menu_create_folder).setVisible(false);
			}
			
			return false;
		}
	}
	
	public void selectAll(){
		logDebug("selectAll");
		if (adapter != null){
			if(adapter.isMultipleSelect()){
				adapter.selectAll();
			}
			else{			
				adapter.setMultipleSelect(true);
				adapter.selectAll();
				
				actionMode = startSupportActionMode(new ActionBarCallBack());
			}
			
			updateActionModeTitle();
		}
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		logDebug("onCreateOptionsMenuLollipop");
		
		
		// Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.file_storage_action, menu);
	    getSupportActionBar().setDisplayShowCustomEnabled(true);
	    
	    newFolderMenuItem = menu.findItem(R.id.cab_menu_create_folder);
		newFolderMenuItem.setIcon(mutateIconSecondary(this, R.drawable.ic_b_new_folder, R.color.white));
		
		if (mode == Mode.PICK_FOLDER) {
            newFolderMenuItem.setVisible(true);
		}
		else{
			newFolderMenuItem.setVisible(false);
		}
	    
	    return super.onCreateOptionsMenu(menu);
	}
	
	@Override
    public boolean onPrepareOptionsMenu(Menu menu) {
		logDebug("onPrepareOptionsMenu");
		if (mode == Mode.PICK_FOLDER) {
			menu.findItem(R.id.cab_menu_select_all).setVisible(false);
			menu.findItem(R.id.cab_menu_unselect_all).setVisible(false);
            newFolderMenuItem.setVisible(true);
		}else{
			newFolderMenuItem.setVisible(false);
			menu.findItem(R.id.cab_menu_select_all).setVisible(true);
			menu.findItem(R.id.cab_menu_unselect_all).setVisible(false);

		}
		return super.onPrepareOptionsMenu(menu);
	}
	
	@SuppressLint("NewApi") @Override
	protected void onCreate(Bundle savedInstanceState) {
		logDebug("onCreate");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			boolean hasStoragePermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
			if (!hasStoragePermission) {
				ActivityCompat.requestPermissions(this,
		                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
						REQUEST_WRITE_STORAGE);
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Window window = getWindow();
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_primary_color_secondary));
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		
		display = getWindowManager().getDefaultDisplay();
		outMetrics = new DisplayMetrics ();
	    display.getMetrics(outMetrics);
	    density  = getResources().getDisplayMetrics().density;
		
	    scaleW = getScaleW(outMetrics, density);
	    scaleH = getScaleH(outMetrics, density);

	    handler = new Handler();

		setContentView(R.layout.activity_filestorage);
		
		detector = new GestureDetectorCompat(this, new RecyclerViewOnGestureListener());
		
		//Set toolbar
		tB = (Toolbar) findViewById(R.id.toolbar_filestorage);
		setSupportActionBar(tB);
		aB = getSupportActionBar();
		aB.setDisplayHomeAsUpEnabled(true);
		aB.setDisplayShowHomeEnabled(true);
		
		Intent intent = getIntent();
		prompt = intent.getStringExtra(EXTRA_PROMPT);
		if (prompt != null) {
			showSnackbar(viewContainer, prompt);
		}
		fromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, true);
		fromSaveRecoveryKey = intent.getBooleanExtra(EXTRA_SAVE_RECOVERY_KEY, false);
		cameraFolderSettings = intent.getBooleanExtra(EXTRA_CAMERA_FOLDER, false);
		sdRoot = intent.getStringExtra(EXTRA_SD_ROOT);
		hasSDCard = (sdRoot != null);
		
		mode = Mode.getFromIntent(intent);
		if (mode == Mode.PICK_FOLDER) {
			documentHashes = intent.getExtras().getLongArray(EXTRA_DOCUMENT_HASHES);
			serializedNodes = intent.getStringArrayListExtra(EXTRA_SERIALIZED_NODES);
			url = intent.getExtras().getString(EXTRA_URL);
			size = intent.getExtras().getLong(EXTRA_SIZE);
			aB.setTitle(getString(R.string.general_select_to_download));
		}
		else{
			aB.setTitle(getString(R.string.general_select_to_upload));
		}
		
		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey("path")) {
				path = new File(savedInstanceState.getString("path"));
			}

			fromSaveRecoveryKey = savedInstanceState.getBoolean(EXTRA_SAVE_RECOVERY_KEY, false);
		}
		
        viewContainer = (RelativeLayout) findViewById(R.id.file_storage_container);
		contentText = (TextView) findViewById(R.id.file_storage_content_text);
		listView = (RecyclerView) findViewById(R.id.file_storage_list_view);

		cancelButton = (Button) findViewById(R.id.file_storage_cancel_button);
		cancelButton.setOnClickListener(this);
		cancelButton.setText(getString(R.string.general_cancel).toUpperCase(Locale.getDefault()));

		button = (Button) findViewById(R.id.file_storage_button);
		button.setOnClickListener(this);

		if (fromSaveRecoveryKey) {
			button.setText(getString(R.string.save_action).toUpperCase(Locale.getDefault()));
		} else if (fromSettings) {
			button.setText(getString(R.string.general_select).toUpperCase(Locale.getDefault()));
		} else {
			if (mode == Mode.PICK_FOLDER) {
				button.setText(getString(R.string.general_save_to_device).toUpperCase(Locale.getDefault()));
			} else {
				button.setText(getString(R.string.context_upload).toUpperCase(Locale.getDefault()));
			}
		}
		emptyImageView = (ImageView) findViewById(R.id.file_storage_empty_image);
		emptyTextView = (TextView) findViewById(R.id.file_storage_empty_text);
		if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
			emptyImageView.setImageResource(R.drawable.ic_zero_landscape_empty_folder);
		}else{
			emptyImageView.setImageResource(R.drawable.ic_zero_portrait_empty_folder);
		}
		String textToShow = String.format(getString(R.string.file_browser_empty_folder_new));
		try{
			textToShow = textToShow.replace("[A]", "<font color=\'#000000\'>");
			textToShow = textToShow.replace("[/A]", "</font>");
			textToShow = textToShow.replace("[B]", "<font color=\'#7a7a7a\'>");
			textToShow = textToShow.replace("[/B]", "</font>");
		}
		catch (Exception e){}
		Spanned result = null;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
			result = Html.fromHtml(textToShow,Html.FROM_HTML_MODE_LEGACY);
		} else {
			result = Html.fromHtml(textToShow);
		}
		emptyTextView.setText(result);

		listView = (RecyclerView) findViewById(R.id.file_storage_list_view);
		listView.addItemDecoration(new SimpleDividerItemDecoration(this, outMetrics));
		mLayoutManager = new LinearLayoutManager(this);
		listView.addOnItemTouchListener(this);
		listView.setLayoutManager(mLayoutManager);
		listView.setItemAnimator(new DefaultItemAnimator()); 
		
		if (adapter == null){
			
			adapter = new FileStorageLollipopAdapter(this, listView, mode);
			listView.setAdapter(adapter);
			
		}

		dbH = DatabaseHandler.getDbHandler(getApplicationContext());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			root = buildExternalStorageFile("");
			if (root == null){
				root = new File(File.separator);
			}
		}
		else{
			root = new File(File.separator);
		}
		//pick file from SD card
		if(hasSDCard) {
		    root = new File(sdRoot);
        }

		lastPositionStack = new Stack<>();

		if(!hasSDCard) {
            prefs = dbH.getPreferences();
            if (prefs == null){
                path = buildExternalStorageFile(DOWNLOAD_DIR);
            }
            else{
                String lastFolder = prefs.getLastFolderUpload();
                if(lastFolder!=null){
                    path = new File(prefs.getLastFolderUpload());
                    if(!path.exists())
                    {
                        path = null;
                    }
                }
                else{
                    path = buildExternalStorageFile(DOWNLOAD_DIR);
                }
                if (cameraFolderSettings){
                    camSyncLocalPath = prefs.getCamSyncLocalPath();
                }
            }
            if (path == null) {
                path = buildExternalStorageFile(DOWNLOAD_DIR);
            }
        } else {
		    //always pick from SD card root
		    path = new File(sdRoot);
        }

		if (cameraFolderSettings) {
			if (Environment.getExternalStorageDirectory() != null) {
				path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
			}
		}
		
		if (path == null){
			finish();
			return;
		}
		
		path.mkdirs();
		changeFolder(path);
		logDebug("Path to show: " + path);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeCallbacksAndMessages(null);
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		state.putString("path", path.getAbsolutePath());
		state.putBoolean(EXTRA_SAVE_RECOVERY_KEY, fromSaveRecoveryKey);
		super.onSaveInstanceState(state);
	}
	
	/*
	 * Open new folder
	 * @param newPath New folder path
	 */
	@SuppressLint("NewApi")
	private void changeFolder(File newPath) {
		logDebug("New path: " + newPath);
		
		setFiles(newPath);
		path = newPath;
		contentText.setText(makeBold(path.getAbsolutePath(), path.getName()));
//		windowTitle.setText(makeBold(path.getAbsolutePath(), path.getName()));
		invalidateOptionsMenu();
        if (mode == Mode.PICK_FILE) {
			clearSelections();
		}
	}
	
	/*
	 * Update file list for new folder
	 */
	private void setFiles(File path) {
		logDebug("setFiles");
		List<FileDocument> documents = new ArrayList<FileDocument>();
		if (!path.canRead()) {
			showErrorAlertDialog(getString(R.string.error_io_problem),
					true, this);
			return;
		}
		File[] files = path.listFiles();

		if(files != null)
		{
			logDebug("Number of files: " + files.length);
			for (File file : files) {
				FileDocument document = new FileDocument(file);
				if (document.isHidden()) {
					continue;
				}
				documents.add(document);
			}
			Collections.sort(documents, new CustomComparator());
		}
		if(documents.size()==0){
			logDebug("Documents SIZE 0");
			listView.setVisibility(View.GONE);
			emptyImageView.setVisibility(View.VISIBLE);
			emptyTextView.setVisibility(View.VISIBLE);
		}
		else{
			logDebug("Documents: " + documents.size());
			adapter.setFiles(documents);
			listView.setVisibility(View.VISIBLE);
			emptyImageView.setVisibility(View.GONE);
			emptyTextView.setVisibility(View.GONE);
		}
	}

	private void updateActionModeTitle() {
		logDebug("updateActionModeTitle");
		if (actionMode == null) {
			logWarning("RETURN");
			return;
		}
		
		List<FileDocument> documents = adapter.getSelectedDocuments();
		int files = 0;
		int folders = 0;
		for (FileDocument document : documents) {
			if (document.isFolder()) {
				folders++;
			}
			else{
				files++;
			}
		}
		
		Resources res = this.getResources();
		String format = "%d %s";
		String filesStr = String.format(format, files,
				res.getQuantityString(R.plurals.general_num_files, files));
		String foldersStr = String.format(format, folders,
				res.getQuantityString(R.plurals.general_num_folders, folders));
		String title;
		if (files == 0 && folders == 0) {
			title = foldersStr + ", " + filesStr;
		} else if (files == 0) {
			title = foldersStr;
		} else if (folders == 0) {
			title = filesStr;
		} else {
			title = foldersStr + ", " + filesStr;
		}
		actionMode.setTitle(title);
		try {
			actionMode.invalidate();
		} catch (NullPointerException e) {
			logError("Invalidate error", e);
			e.printStackTrace();
		}
	}

	/*
	 * Clear all selected items
	 */
	private void clearSelections() {
		logDebug("clearSelections");
		if(adapter.isMultipleSelect()){
			adapter.clearSelections();
		}
	}

	/*
	 * Comparator to sort the files
	 */
	public class CustomComparator implements Comparator<FileDocument> {
		@Override
		public int compare(FileDocument o1, FileDocument o2) {
			if (o1.isFolder() != o2.isFolder()) {
				return o1.isFolder() ? -1 : 1;
			}
			return o1.getName().compareToIgnoreCase(o2.getName());
		}
	}

	@Override
	public void onClick(View v) {
		logDebug("onClick");

		switch (v.getId()) {
			case R.id.file_storage_button:{
                //don't record last upload folder for SD card upload
                if(!hasSDCard) {
                    dbH.setLastUploadFolder(path.getAbsolutePath());
                }
				if (mode == Mode.PICK_FOLDER) {
					logDebug("Mode.PICK_FOLDER");
					Intent intent = new Intent();
					intent.putExtra(EXTRA_PATH, path.getAbsolutePath());
					intent.putExtra(EXTRA_DOCUMENT_HASHES, documentHashes);
					intent.putStringArrayListExtra(EXTRA_SERIALIZED_NODES, serializedNodes);
					intent.putExtra(EXTRA_URL, url);
					intent.putExtra(EXTRA_SIZE, size);
					setResult(RESULT_OK, intent);
					finish();
				}
				else {
					logDebug("Mode.PICK_FILE");
					if(adapter.getSelectedCount()<=0){
						showSnackbar(viewContainer, getString(R.string.error_no_selection));
						break;
					}
					new AsyncTask<Void, Void, Void>()
					{
						ArrayList<String> files = new ArrayList<String>();

						@Override
						protected Void doInBackground(Void... params) {
							List<FileDocument> selectedDocuments= adapter.getSelectedDocuments();
							for (int i = 0; i < selectedDocuments.size(); i++) {
								FileDocument document = selectedDocuments.get(i);
								if(document != null)
								{
									File file = document.getFile();
									logDebug("Add to files selected: " + file.getAbsolutePath());
									files.add(file.getAbsolutePath());
								}
								
							}
							return null;	
						}

						@Override
						public void onPostExecute(Void a)
						{

							setResultFiles(files);
						}
					}.execute();			
				}
				break;
			}
			case R.id.file_storage_cancel_button:{
				finish();
				break;
			}
		}
		
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ( keyCode == KeyEvent.KEYCODE_MENU ) {
	        // do nothing
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}

	public void itemClick(int position) {
		logDebug("Position: " + position);

		FileDocument document = adapter.getDocumentAt(position);
		if(document == null) {
			return;
		}
		
		if (adapter.isMultipleSelect()){
			logDebug("MULTISELECT ON");
			adapter.toggleSelection(position);
			List<FileDocument> selected = adapter.getSelectedDocuments();
			if (selected.size() > 0){
				updateActionModeTitle();
			}
		}
		else{
			if (document.isFolder()) {

				int lastFirstVisiblePosition = 0;

				lastFirstVisiblePosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();

				logDebug("Push to stack " + lastFirstVisiblePosition + " position");
				lastPositionStack.push(lastFirstVisiblePosition);

				changeFolder(document.getFile());
			}
			else if (mode == Mode.PICK_FILE) {
				//Multiselect on to select several files if desired
				adapter.setMultipleSelect(true);				
				actionMode = startSupportActionMode(new ActionBarCallBack());
				adapter.toggleSelection(position);
				updateActionModeTitle();
				adapter.notifyDataSetChanged();
			}
		}		
	}
	
	/*
	 * Set selected files to pass to the caller activity and finish this
	 * activity
	 */
	private void setResultFiles(ArrayList<String> files) {
		logDebug(files.size() + "files selected");
		Intent intent = new Intent();
		intent.putStringArrayListExtra(EXTRA_FILES, files);
		intent.putExtra(EXTRA_PATH, path.getAbsolutePath());
		setResult(RESULT_OK, intent);
		finish();
	}
	
	/*
	 * Count all selected items
	 */
	public int getItemCount(){
		if(adapter!=null){
			return adapter.getItemCount();
		}
		return 0;
	}
	
	/*
	 * Disable selection
	 */
	public void hideMultipleSelect() {
		logDebug("hideMultipleSelect");
		adapter.setMultipleSelect(false);
		if (actionMode != null) {
			actionMode.finish();
		}
	}
	
	@Override
	public void onBackPressed() {
		logDebug("onBackPressed");
		retryConnectionsAndSignalPresence();

		// Finish activity if at the root
		if (path.equals(root)) {
			super.onBackPressed();
		// Go one level higher otherwise
		} else {
			changeFolder(path.getParentFile());
			int lastVisiblePosition = 0;
			if(!lastPositionStack.empty()){
				lastVisiblePosition = lastPositionStack.pop();
				logDebug("Pop of the stack " + lastVisiblePosition + " position");
			}
			logDebug("Scroll to " + lastVisiblePosition + " position");

			if(lastVisiblePosition>=0){
				mLayoutManager.scrollToPositionWithOffset(lastVisiblePosition, 0);
			}
		}
	}

	
	public void showNewFolderDialog(){
		logDebug("showNewFolderDialog");
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.setMargins(scaleWidthPx(20, outMetrics), scaleWidthPx(20, outMetrics), scaleWidthPx(17, outMetrics), 0);

		final EditText input = new EditText(this);
		layout.addView(input, params);

		LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params1.setMargins(scaleWidthPx(20, outMetrics), 0, scaleWidthPx(17, outMetrics), 0);

		final RelativeLayout error_layout = new RelativeLayout(FileStorageActivityLollipop.this);
		layout.addView(error_layout, params1);

		final ImageView error_icon = new ImageView(FileStorageActivityLollipop.this);
		error_icon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_input_warning));
		error_layout.addView(error_icon);
		RelativeLayout.LayoutParams params_icon = (RelativeLayout.LayoutParams) error_icon.getLayoutParams();


		params_icon.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		error_icon.setLayoutParams(params_icon);

		error_icon.setColorFilter(ContextCompat.getColor(FileStorageActivityLollipop.this, R.color.login_warning));

		final TextView textError = new TextView(FileStorageActivityLollipop.this);
		error_layout.addView(textError);
		RelativeLayout.LayoutParams params_text_error = (RelativeLayout.LayoutParams) textError.getLayoutParams();
		params_text_error.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		params_text_error.width = ViewGroup.LayoutParams.WRAP_CONTENT;
		params_text_error.addRule(RelativeLayout.CENTER_VERTICAL);
		params_text_error.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		params_text_error.setMargins(scaleWidthPx(3, outMetrics), 0,0,0);
		textError.setLayoutParams(params_text_error);

		textError.setTextColor(ContextCompat.getColor(FileStorageActivityLollipop.this, R.color.login_warning));
		error_layout.setVisibility(View.GONE);

		input.getBackground().mutate().clearColorFilter();
		input.getBackground().mutate().setColorFilter(ContextCompat.getColor(this, R.color.accentColor), PorterDuff.Mode.SRC_ATOP);
		input.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				if(error_layout.getVisibility() == View.VISIBLE){
					error_layout.setVisibility(View.GONE);
					input.getBackground().mutate().clearColorFilter();
					input.getBackground().mutate().setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.accentColor), PorterDuff.Mode.SRC_ATOP);
				}
			}
		});

		input.setSingleLine();
		input.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
		input.setHint(getString(R.string.context_new_folder_name));
		input.setImeOptions(EditorInfo.IME_ACTION_DONE);
		input.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					String value = v.getText().toString().trim();

					if (value.length() == 0) {
						input.getBackground().mutate().setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.login_warning), PorterDuff.Mode.SRC_ATOP);
						textError.setText(getString(R.string.invalid_string));
						error_layout.setVisibility(View.VISIBLE);
						input.requestFocus();

					}else{
						boolean result=matches(regex, value);
						if(result){
							input.getBackground().mutate().setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.login_warning), PorterDuff.Mode.SRC_ATOP);
							textError.setText(getString(R.string.invalid_characters));
							error_layout.setVisibility(View.VISIBLE);
							input.requestFocus();

						}else{
							createFolder(value);
							newFolderDialog.dismiss();
						}
					}
					return true;
				}
				return false;
			}
		});



		input.setImeActionLabel(getString(R.string.general_create),KeyEvent.KEYCODE_ENTER);
		input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					showKeyboardDelayed(v);
				}
			}
		});
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
		builder.setTitle(getString(R.string.menu_new_folder));
		builder.setPositiveButton(getString(R.string.general_create),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String value = input.getText().toString().trim();
						if (value.length() == 0) {
							return;
						}
						createFolder(value);
					}
				});
		builder.setNegativeButton(getString(android.R.string.cancel), null);
		builder.setView(layout);
		newFolderDialog = builder.create();
		newFolderDialog.show();

		newFolderDialog.getButton(android.support.v7.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new   View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				String value = input.getText().toString().trim();
				if (value.length() == 0) {
					input.getBackground().mutate().setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.login_warning), PorterDuff.Mode.SRC_ATOP);
					textError.setText(getString(R.string.invalid_string));
					error_layout.setVisibility(View.VISIBLE);
					input.requestFocus();

				}else{
					boolean result=matches(regex, value);
					if(result){
						input.getBackground().mutate().setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.login_warning), PorterDuff.Mode.SRC_ATOP);
						textError.setText(getString(R.string.invalid_characters));
						error_layout.setVisibility(View.VISIBLE);
						input.requestFocus();

					}else{
						createFolder(value);
						newFolderDialog.dismiss();
					}
				}


			}
		});
	}
	
	/*
	 * Display keyboard
	 */
	private void showKeyboardDelayed(final View view) {
		view.postDelayed(new Runnable() {
			@Override
			public void run() {
				InputMethodManager imm = (InputMethodManager) FileStorageActivityLollipop.this.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
			}
		}, 50);
	}
	
	/*
	 * Create new folder and reload file list
	 */
	private void createFolder(String value) {
		logDebug(value + " Of value");
        SDCardOperator sdCardOperator = null;
        try {
            sdCardOperator = new SDCardOperator(this);
        } catch (SDCardOperator.SDCardException e) {
            e.printStackTrace();
            logError("Initialize SDCardOperator failed", e);
        }
        if (sdCardOperator != null && SDCardOperator.isSDCardPath(path.getAbsolutePath()) && !path.canWrite()) {
            try {
                sdCardOperator.initDocumentFileRoot(dbH.getSDCardUri());
                sdCardOperator.createFolder(path.getAbsolutePath(), value);
            } catch (SDCardOperator.SDCardException e) {
                e.printStackTrace();
                logError("SDCardOperator initDocumentFileRoot failed", e);
                showErrorAlertDialog(getString(R.string.error_io_problem), true, this);
            }
        } else {
            createFolderWithFile(value);
        }
        setFiles(path);
    }

    private void createFolderWithFile(String value) {
        File newFolder = new File(path, value);
        newFolder.mkdir();
        newFolder.setReadable(true, false);
        newFolder.setExecutable(true, false);
    }

	public static boolean matches(String regex, CharSequence input) {
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(input);
		return m.find();
	}

	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(RecyclerView rV, MotionEvent e) {
		detector.onTouchEvent(e);
		return false;
	}

	@Override
	public void onRequestDisallowInterceptTouchEvent(boolean arg0) {
	}

	@Override
	public void onTouchEvent(RecyclerView arg0, MotionEvent arg1) {
	}
}
